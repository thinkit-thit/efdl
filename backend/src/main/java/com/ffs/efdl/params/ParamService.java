package com.ffs.efdl.params;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.ffs.efdl.model.BaseEntity;
import com.ffs.efdl.params.ParamModel.Derivation;
import com.ffs.efdl.params.ParamModel.FieldDef;
import com.ffs.efdl.params.ParamModel.FieldType;
import com.ffs.efdl.params.ParamModel.Hierarchy;
import com.ffs.efdl.params.ParamModel.LockDef;
import com.ffs.efdl.params.ParamModel.MirrorDef;
import com.ffs.efdl.params.ParamModel.SumDef;
import com.ffs.efdl.params.ParamModel.UniqueDef;
import com.ffs.efdl.params.ParamModel.Virtual;
import com.ffs.efdl.params.ParamModel.ParamDef;
import com.ffs.efdl.params.ParamModel.RefDef;
import com.ffs.efdl.params.ParamModel.ScopeDef;
import com.ffs.efdl.service.DocumentWriter;

/**
 * Lecture et écriture génériques des écrans de paramétrage, selon {@link ParamDefinitions}.
 * Toujours limité à l'école courante ; éléments {@code status=DELETED} ignorés ;
 * seuls les champs déclarés sont lus ou écrits.
 */
@Service
public class ParamService {

    /** @param attributes champs de la référence exposés au formulaire (ex. textes de l'indicateur KPI), sinon null */
    public record Choice(String id, String label, String parent, boolean deleted, Map<String, Object> attributes) {
    }

    private final ParamDefinitions definitions;
    private final MongoTemplate mongo;
    private final DocumentWriter writer;
    private final List<RowEnricher> enrichers;

    public ParamService(ParamDefinitions definitions, MongoTemplate mongo, DocumentWriter writer,
                        List<RowEnricher> enrichers) {
        this.definitions = definitions;
        this.mongo = mongo;
        this.writer = writer;
        this.enrichers = enrichers;
    }

    public ParamDef definition(String key) {
        return definitions.param(key).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paramètre inconnu"));
    }

    // ---------- lecture ----------

    public List<Map<String, Object>> list(ParamDef def, String school, Map<String, String> scope) {
        Query query = Query.query(scoped(def, school, scope));
        query.fields().include(fieldNames(def));
        if (def.sort().isEmpty()) {
            query.with(Sort.by("_id")); // ordre naturel (insertion), comme asList() sans order()
        } else {
            // « -creationDate » = tri décroissant sur creationDate.
            query.with(Sort.by(def.sort().stream().map(s -> s.startsWith("-")
                    ? Sort.Order.desc(s.substring(1)) : Sort.Order.asc(s)).toList()));
        }
        List<Map<String, Object>> rows = mongo.find(query, Document.class, def.collection()).stream().map(d -> toRow(def, d)).toList();
        addVirtuals(def, school, rows);
        enrichers.stream().filter(e -> e.supports(def.key())).forEach(e -> e.enrich(school, rows));
        return rows;
    }

    /** Valeurs calculées (sommes, valeurs liées), en une requête par colonne pour toute la liste. */
    private void addVirtuals(ParamDef def, String school, List<Map<String, Object>> rows) {
        for (Virtual v : definitions.virtuals(def.key())) {
            Set<Object> keys = rows.stream().map(r -> r.get(v.localField())).filter(java.util.Objects::nonNull)
                    .collect(Collectors.toSet());
            Criteria criteria = Criteria.where("schoolExternalId").is(school).and(v.foreignField()).in(keys);
            if (v.filter() != null) v.filter().forEach((k, value) -> criteria.and(k).is(value));
            if (v.filter() == null || !v.filter().containsKey("status")) criteria.and("status").ne(BaseEntity.STATUS_DELETED);
            Query query = Query.query(criteria);
            query.fields().include(v.foreignField(), v.valueField());
            Map<Object, Object> values = new java.util.HashMap<>();
            for (Document d : mongo.find(query, Document.class, v.collection())) {
                Object key = d.get(v.foreignField());
                Object value = d.get(v.valueField());
                if ("sum".equals(v.op())) {
                    double add = value instanceof Number n ? n.doubleValue() : 0;
                    values.merge(key, add, (a, b) -> ((Double) a) + ((Double) b));
                } else {
                    values.putIfAbsent(key, value);
                }
            }
            for (Map<String, Object> row : rows) {
                Object key = row.get(v.localField());
                Object value = key == null ? null : values.get(key);
                row.put(v.name(), value == null && "sum".equals(v.op()) ? 0.0 : value);
            }
        }
    }

    public Map<String, Object> get(ParamDef def, String school, Map<String, String> scope, String id) {
        Query query = Query.query(scoped(def, school, scope).and("externalId").is(id));
        query.fields().include(fieldNames(def));
        Document doc = mongo.findOne(query, Document.class, def.collection());
        if (doc == null) throw notFound();
        Map<String, Object> row = toRow(def, doc);
        addVirtuals(def, school, List.of(row));
        enrichers.stream().filter(e -> e.supports(def.key())).forEach(e -> e.enrich(school, List.of(row)));
        return row;
    }

    /**
     * Fiche de création préremplie avec les valeurs qui découlent du parent de l'URL (ex. nouveau lot : n° de contrat,
     * prestataire, type et dates du contrat multiple) ; {@code null} si aucune valeur n'en découle.
     */
    public Map<String, Object> blank(ParamDef def, String school, Map<String, String> scope) {
        Map<String, Object> fields = new LinkedHashMap<>();
        def.scope().forEach(sc -> fields.put(sc.field(), scope.get(sc.var())));
        if (fields.isEmpty()) return null;
        Map<String, Object> before = new LinkedHashMap<>(fields);
        derive(def, school, fields);
        return fields.equals(before) ? null : toRow(def, new Document(fields));
    }

    /**
     * Vérifie les parents portés par l'URL (existants, actifs, de l'école, et rattachés aux parents précédents)
     * et renvoie leurs libellés pour le fil d'Ariane.
     */
    public List<ScopeLabel> checkScope(ParamDef def, String school, Map<String, String> scope) {
        List<ScopeLabel> labels = new java.util.ArrayList<>();
        for (ScopeDef s : def.scope()) {
            String value = scope.get(s.var());
            if (value == null || value.isBlank()) throw invalid("Paramètre manquant : " + s.var());
            Criteria criteria = active(school).and(s.matchField()).is(value);
            for (ScopeDef before : def.scope()) {
                if (before == s) break;
                criteria = criteria.and(before.field()).is(scope.get(before.var()));
            }
            Document parent = mongo.findOne(Query.query(criteria), Document.class, s.collection());
            if (parent == null) throw notFound();
            labels.add(new ScopeLabel(s.var(), value, parent.getString(s.labelField())));
        }
        return labels;
    }

    public record ScopeLabel(String var, String id, String label) {
    }

    /** Choix et libellés de toutes les références de l'écran (éléments supprimés marqués, pour l'affichage seul). */
    public Map<String, List<Choice>> refs(ParamDef def, String school) {
        Map<String, List<Choice>> result = new LinkedHashMap<>();
        for (FieldDef field : def.fields()) {
            if (field.ref() != null && !result.containsKey(field.ref())) {
                result.put(field.ref(), choices(definitions.refDef(field.ref()), school));
            }
        }
        return result;
    }

    private List<Choice> choices(RefDef ref, String school) {
        Criteria criteria = Criteria.where("schoolExternalId").is(school);
        if (ref.extraFilter() != null) ref.extraFilter().forEach((k, v) -> criteria.and(k).is(v));
        Query query = Query.query(criteria);
        query.fields().include(ref.fields().toArray(String[]::new)).include("externalId", "status");
        return mongo.find(query, Document.class, ref.collection()).stream()
                .map(d -> new Choice(d.getString("externalId"), ref.label().apply(d),
                        ref.parentField() == null ? null : d.getString(ref.parentField()),
                        BaseEntity.STATUS_DELETED.equals(d.getString("status")),
                        ref.attributes() ? attributes(ref, d) : null))
                .sorted((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(nz(a.label()), nz(b.label())))
                .toList();
    }

    // ---------- écriture ----------

    public String create(ParamDef def, String school, Map<String, String> scope, Map<String, Object> body, String by) {
        if (!def.creatable()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Création non disponible pour cet écran");
        checkScope(def, school, scope);
        Map<String, Object> fields = convert(def, school, body, true);
        def.scope().forEach(sc -> fields.put(sc.field(), scope.get(sc.var())));
        derive(def, school, fields);
        Hierarchy hierarchy = definitions.hierarchy(def.key());
        if (hierarchy != null) applyHierarchy(def, hierarchy, school, fields);
        // Le parent vient de l'URL (ex. ptaFinancementExternalId).
        definitions.createDefaults(def.key()).forEach(fields::putIfAbsent);
        String autoNumber = definitions.autoNumber(def.key());
        if (autoNumber != null) fields.put(autoNumber, nextNumber(def.collection(), school, autoNumber));
        checkUnique(def, school, null, fields);
        String id = DocumentWriter.newExternalId();
        MirrorDef mirror = definitions.mirror(def.key());
        if (mirror != null) fields.put(mirror.selfField(), id);
        writer.insert(def.collection(), def.className(), id, school, fields, by);
        if (mirror != null) syncMirror(def, mirror, school, id, by);
        updateSum(def, school, id, by);
        return id;
    }

    /**
     * Recalcule le total du parent désigné par l'élément {@code id} (ex. montant d'un contrat multiple = somme des
     * montants de ses lots actifs). Élément supprimé compris (son parent perd sa part) ; contrat simple ignoré.
     */
    private void updateSum(ParamDef def, String school, String id, String by) {
        SumDef sum = definitions.sum(def.key());
        if (sum == null) return;
        Query self = Query.query(Criteria.where("schoolExternalId").is(school).and("externalId").is(id));
        self.fields().include(sum.linkField());
        Document element = mongo.findOne(self, Document.class, def.collection());
        String parentId = element == null ? null : element.getString(sum.linkField());
        if (parentId == null || parentId.equals(id)) return;
        Query query = Query.query(active(school).and(sum.linkField()).is(parentId));
        query.fields().include(sum.valueField());
        double total = mongo.find(query, Document.class, def.collection()).stream()
                .map(d -> d.get(sum.valueField()))
                .mapToDouble(v -> v instanceof Number n ? n.doubleValue() : 0).sum();
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(sum.parentField(), total);
        writer.update(sum.parentCollection(), parentId, school, fields, by);
    }

    /** Crée ou met à jour le jumeau (même externalId) à partir de l'élément enregistré. */
    private void syncMirror(ParamDef def, MirrorDef mirror, String school, String id, String by) {
        Document saved = mongo.findOne(Query.query(active(school).and("externalId").is(id)), Document.class, def.collection());
        // Seulement pour un contrat simple (son groupe porte son propre identifiant) : un lot de contrat multiple
        // reste rattaché à son groupe.
        if (saved == null || !id.equals(saved.getString(mirror.selfField()))) return;
        Map<String, Object> copies = new LinkedHashMap<>();
        mirror.copies().forEach((target, source) -> copies.put(target, saved.get(source)));
        if (!writer.update(mirror.collection(), id, school, copies, by)) {
            Map<String, Object> created = new LinkedHashMap<>(copies);
            mirror.constants().forEach(created::putIfAbsent);
            writer.insert(mirror.collection(), mirror.className(), id, school, created, by);
        }
    }

    /** Unicité d'une combinaison de champs parmi les éléments actifs (valeurs actuelles complétées par celles envoyées). */
    private void checkUnique(ParamDef def, String school, String id, Map<String, Object> fields) {
        UniqueDef unique = definitions.unique(def.key());
        if (unique == null) return;
        Document current = id == null ? new Document()
                : mongo.findOne(Query.query(active(school).and("externalId").is(id)), Document.class, def.collection());
        if (current == null) return;
        Criteria criteria = active(school);
        for (String f : unique.fields()) {
            Object value = fields.containsKey(f) ? fields.get(f) : current.get(f);
            if (value == null) return;
            criteria = criteria.and(f).is(value);
        }
        if (id != null) criteria = criteria.and("externalId").ne(id);
        if (mongo.exists(Query.query(criteria), def.collection())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, unique.message());
        }
    }

    /** Verrou (ex. paiement déjà validé) : modification et suppression refusées. */
    private void checkLock(ParamDef def, String school, String id) {
        LockDef lock = definitions.lock(def.key());
        if (lock == null) return;
        Document doc = mongo.findOne(Query.query(active(school).and("externalId").is(id)), Document.class, def.collection());
        if (doc == null) throw notFound();
        Object value = doc.get(lock.field());
        if (value != null && !lock.allowed().contains(value.toString())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, lock.message());
        }
    }

    /** Modification : les champs « création seulement » (parents, code) et le parent de l'URL ne changent jamais. */
    public void update(ParamDef def, String school, Map<String, String> scope, String id, Map<String, Object> body, String by) {
        requireInScope(def, school, scope, id);
        checkLock(def, school, id);
        Map<String, Object> fields = convert(def, school, body, false);
        derive(def, school, fields);
        checkUnique(def, school, id, fields);
        if (!writer.update(def.collection(), id, school, fields, by)) throw notFound();
        MirrorDef mirror = definitions.mirror(def.key());
        if (mirror != null) syncMirror(def, mirror, school, id, by);
        updateSum(def, school, id, by);
    }

    /** Recopie les valeurs des documents liés (commune → région/district, infrastructure → indicateur…). */
    private void derive(ParamDef def, String school, Map<String, Object> fields) {
        for (Derivation d : definitions.derivations(def.key())) {
            Object source = fields.get(d.source());
            if (source == null) continue; // champ absent ou vidé : les valeurs dérivées ne sont pas modifiées
            Document linked = mongo.findOne(Query.query(active(school).and(d.matchField()).is(source)), Document.class,
                    d.collection());
            if (linked == null) continue;
            d.copies().forEach((target, from) -> fields.put(target, linked.get(from)));
        }
    }

    private void requireInScope(ParamDef def, String school, Map<String, String> scope, String id) {
        if (def.scope().isEmpty()) return;
        checkScope(def, school, scope);
        if (!mongo.exists(Query.query(scoped(def, school, scope).and("externalId").is(id)), def.collection())) throw notFound();
    }

    /**
     * Localisation : ancêtres recopiés depuis le parent direct (la chaîne choisie doit être cohérente),
     * code calculé (code du parent + numéro suivant) ou vérifié, valeurs par défaut.
     */
    private void applyHierarchy(ParamDef def, Hierarchy h, String school, Map<String, Object> fields) {
        String parentCode = "";
        if (h.parentField() != null) {
            FieldDef parentDef = def.field(h.parentField());
            String parentId = (String) fields.get(h.parentField());
            if (parentId == null) throw invalid(parentDef.label() + " est obligatoire");
            Document parent = mongo.findOne(Query.query(active(school).and("externalId").is(parentId)), Document.class,
                    h.parentCollection());
            if (parent == null) throw invalid("Choix inconnu : " + parentDef.label());
            for (String inherited : h.inheritedFields()) {
                Object chosen = fields.get(inherited);
                Object actual = parent.get(inherited);
                if (chosen != null && !chosen.equals(actual)) {
                    throw invalid("Choix incohérents : " + parentDef.label() + " n'appartient pas à " + def.field(inherited).label());
                }
                fields.put(inherited, actual);
            }
            parentCode = parent.getString("code");
            if (parentCode == null || !parentCode.matches("\\d+")) {
                throw invalid("Le parent choisi n'a pas de code numérique : saisissez le code");
            }
        }
        String code = (String) fields.get("code");
        int length = parentCode.length() + h.codeWidth();
        if (code == null) {
            code = nextCode(def.collection(), school, parentCode, h.codeWidth());
        } else if (!code.matches("\\d{" + length + "}") || !code.startsWith(parentCode)) {
            throw invalid("Le code doit comporter " + length + " chiffres"
                    + (parentCode.isEmpty() ? "" : " et commencer par " + parentCode));
        }
        // Codes uniques par école, y compris ceux des éléments supprimés (réservés).
        if (mongo.exists(Query.query(Criteria.where("schoolExternalId").is(school).and("code").is(code)), def.collection())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Le code " + code + " est déjà utilisé");
        }
        fields.put("code", code);
        h.defaults().forEach(fields::putIfAbsent);
    }

    /** Dernier numéro de l'école + 1 (comme getProvidersByLast : order("-number")), éléments supprimés compris. */
    private int nextNumber(String collection, String school, String field) {
        Query query = Query.query(Criteria.where("schoolExternalId").is(school)).with(Sort.by(Sort.Order.desc(field))).limit(1);
        query.fields().include(field);
        Document last = mongo.findOne(query, Document.class, collection);
        Object value = last == null ? null : last.get(field);
        return (value instanceof Number n ? n.intValue() : 0) + 1;
    }

    private String nextCode(String collection, String school, String parentCode, int width) {
        Query query = Query.query(Criteria.where("schoolExternalId").is(school)
                .and("code").regex("^" + parentCode + "\\d{" + width + "}$"));
        query.fields().include("code");
        int max = mongo.find(query, Document.class, collection).stream()
                .mapToInt(d -> Integer.parseInt(d.getString("code").substring(parentCode.length()))).max().orElse(0);
        int next = max + 1;
        if (String.valueOf(next).length() > width) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Plus de code automatique disponible : saisissez le code");
        }
        return parentCode + String.format("%0" + width + "d", next);
    }

    /** Suppression logique ; refusée si des éléments actifs en dépendent (localisation). */
    public void delete(ParamDef def, String school, Map<String, String> scope, String id, String by) {
        requireInScope(def, school, scope, id);
        if (def.childCollection() != null) {
            boolean hasChildren = mongo.exists(Query.query(active(school).and(def.childField()).is(id)), def.childCollection());
            if (hasChildren) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Des éléments rattachés existent encore : supprimez-les d'abord");
            }
        }
        checkLock(def, school, id);
        if (!writer.markDeleted(def.collection(), id, school, by)) throw notFound();
        MirrorDef mirror = definitions.mirror(def.key());
        if (mirror != null) writer.markDeleted(mirror.collection(), id, school, by);
        updateSum(def, school, id, by);
    }

    /** Valide et convertit les champs modifiables ; les valeurs vides deviennent null (champ retiré). */
    Map<String, Object> convert(ParamDef def, String school, Map<String, Object> body, boolean creating) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (FieldDef f : def.fields()) {
            if (!f.editable() && !(creating && f.createOnly())) continue;
            // Modification : seuls les champs envoyés (une étape de la fiche n'efface pas les autres étapes).
            if (!creating && (body == null || !body.containsKey(f.name()))) continue;
            Object raw = body == null ? null : body.get(f.name());
            Object value = switch (f.type()) {
                case TEXT, TEXTAREA -> text(f, raw);
                case INT -> { // numérique : toujours écrit, 0 par défaut
                    Number n = number(f, raw);
                    yield n == null ? 0 : n.intValue();
                }
                case DOUBLE -> {
                    Number n = number(f, raw);
                    yield n == null ? 0.0 : n.doubleValue();
                }
                case DATE -> date(f, raw);
                case BOOLEAN -> raw instanceof Boolean b ? b : raw != null && "true".equalsIgnoreCase(raw.toString());
                case ENUM -> enumValue(f, raw);
                case REF -> refValue(f, school, raw);
                case REF_SET -> refSetValue(f, school, raw);
            };
            if (f.required() && value == null) throw invalid(f.label() + " est obligatoire");
            out.put(f.name(), value);
        }
        checkCascade(def, school, out);
        return out;
    }

    /** Chaque choix d'une cascade doit appartenir au choix précédent (ex. district de la région choisie). */
    private void checkCascade(ParamDef def, String school, Map<String, Object> values) {
        for (int i = 1; i < def.cascade().size(); i++) {
            Object parent = values.get(def.cascade().get(i - 1));
            FieldDef f = def.field(def.cascade().get(i));
            if (!f.editable() && !f.createOnly()) continue;
            Object value = values.get(f.name());
            if (parent == null || value == null) continue;
            List<?> chosen = value instanceof List<?> l ? l : List.of(value);
            Map<String, String> parents = choices(definitions.refDef(f.ref()), school).stream()
                    .filter(c -> c.parent() != null).collect(Collectors.toMap(Choice::id, Choice::parent, (a, b) -> a));
            for (Object id : chosen) {
                if (!parent.equals(parents.get(id.toString()))) {
                    throw invalid("Choix incohérents : " + f.label() + " n'appartient pas à " + def.field(def.cascade().get(i - 1)).label());
                }
            }
        }
    }

    private static Date date(FieldDef f, Object raw) {
        if (raw == null || (raw instanceof String s && s.isBlank())) return null;
        try {
            String v = raw.toString().trim();
            // yyyy-MM-dd (champ date) ; une date-heure ISO est ramenée à son jour.
            LocalDate day = v.length() > 10 ? LocalDate.parse(v.substring(0, 10)) : LocalDate.parse(v);
            return Date.from(day.atStartOfDay(ZoneId.systemDefault()).toInstant());
        } catch (DateTimeParseException e) {
            throw invalid("Date invalide : " + f.label());
        }
    }

    private static String text(FieldDef f, Object raw) {
        if (raw == null) return null;
        if (!(raw instanceof String s)) throw invalid("Champ invalide : " + f.label());
        String v = s.trim();
        if (v.isEmpty()) return null;
        if (v.length() > f.maxLength()) throw invalid("Champ trop long : " + f.label());
        return v;
    }

    private static Number number(FieldDef f, Object raw) {
        if (raw == null || (raw instanceof String s && s.isBlank())) return null;
        try {
            double d = raw instanceof Number n ? n.doubleValue() : Double.parseDouble(raw.toString().trim().replace(',', '.'));
            if (Double.isNaN(d) || Double.isInfinite(d)) throw new NumberFormatException();
            if (f.type() == FieldType.INT && d != Math.rint(d)) throw new NumberFormatException();
            return d;
        } catch (NumberFormatException e) {
            throw invalid("Nombre invalide : " + f.label());
        }
    }

    private static String enumValue(FieldDef f, Object raw) {
        if (raw == null || (raw instanceof String s && s.isBlank())) return null;
        String v = raw.toString();
        if (f.options().stream().noneMatch(o -> o.value().equals(v))) throw invalid("Valeur invalide : " + f.label());
        return v;
    }

    private String refValue(FieldDef f, String school, Object raw) {
        if (raw == null || (raw instanceof String s && s.isBlank())) return null;
        String v = raw.toString();
        if (!activeIds(f, school).contains(v)) throw invalid("Choix inconnu : " + f.label());
        return v;
    }

    private List<String> refSetValue(FieldDef f, String school, Object raw) {
        if (raw == null) return null;
        if (!(raw instanceof List<?> list)) throw invalid("Champ invalide : " + f.label());
        Set<String> values = list.stream().filter(x -> x != null && !x.toString().isBlank()).map(Object::toString)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (values.isEmpty()) return null;
        if (!activeIds(f, school).containsAll(values)) throw invalid("Choix inconnu : " + f.label());
        return new ArrayList<>(values);
    }

    private Set<String> activeIds(FieldDef f, String school) {
        return choices(definitions.refDef(f.ref()), school).stream().filter(c -> !c.deleted())
                .map(Choice::id).collect(Collectors.toSet());
    }

    // ---------- utilitaires ----------

    private Criteria scoped(ParamDef def, String school, Map<String, String> scope) {
        Criteria criteria = active(school);
        Document fixed = definitions.listFilter(def.key());
        if (fixed != null) for (Map.Entry<String, Object> e : fixed.entrySet()) criteria = criteria.and(e.getKey()).is(e.getValue());
        for (ScopeDef s : def.scope()) {
            String value = scope.get(s.var());
            if (value == null || value.isBlank()) throw invalid("Paramètre manquant : " + s.var());
            if (!"externalId".equals(s.matchField())) {
                Document parent = mongo.findOne(Query.query(active(school).and(s.matchField()).is(value)), Document.class, s.collection());
                if (parent == null) throw notFound();
                value = parent.getString("externalId");
            }
            criteria = criteria.and(s.field()).is(value);
        }
        return criteria;
    }

    private static Criteria active(String school) {
        return Criteria.where("schoolExternalId").is(school).and("status").ne(BaseEntity.STATUS_DELETED);
    }

    private static String[] fieldNames(ParamDef def) {
        List<String> names = new ArrayList<>(def.fields().stream().map(FieldDef::name).toList());
        names.add("externalId");
        return names.toArray(String[]::new);
    }

    private static Map<String, Object> toRow(ParamDef def, Document d) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("externalId", d.getString("externalId"));
        for (FieldDef f : def.fields()) {
            row.put(f.name(), d.get(f.name()));
        }
        return row;
    }

    private static Map<String, Object> attributes(RefDef ref, Document d) {
        Map<String, Object> values = new LinkedHashMap<>();
        ref.fields().forEach(f -> values.put(f, d.get(f)));
        return values;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Élément introuvable");
    }

    private static ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
