package com.ffs.efdl.service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.ffs.efdl.params.ParamDefinitions;
import com.ffs.efdl.params.ParamModel.FieldDef;
import com.ffs.efdl.params.ParamModel.ParamDef;
import com.ffs.efdl.params.ParamModel.RefDef;

/**
 * Historique des modifications : qui a créé, modifié, supprimé, validé… quoi et quand, avec les
 * valeurs avant / après. Collection {@code audit_log}. Alimenté par
 * {@link DocumentWriter} (toutes les écritures des écrans) et par les écritures particulières (validation,
 * avancement, mots de passe). Les valeurs sensibles (mots de passe, codes) ne sont jamais enregistrées.
 * Consultation réservée à l'administrateur et au « Validateur info » de l'application.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    public static final String COLLECTION = "audit_log";
    static final String CLASS_NAME = "com.ffs.efdl.model.AuditLog";

    public static final String CREATION = "CREATION";
    public static final String MODIFICATION = "MODIFICATION";
    public static final String SUPPRESSION = "SUPPRESSION";
    public static final String VALIDATION = "VALIDATION";
    public static final String REJET = "REJET";
    public static final String MOT_DE_PASSE = "MOT_DE_PASSE";
    /** Ancienne trace de suppression en masse (plus créée depuis le 2026-09-26 ; les existantes se suppriment). */
    public static final String PURGE = "PURGE";

    /** Collections techniques non tracées (traces elles-mêmes, envois déjà journalisés). */
    private static final Set<String> IGNORED_COLLECTIONS = Set.of(COLLECTION, "history", "payment_delay_notice", "password_reset",
            "user_credential");
    /** Champs techniques ou sensibles jamais repris dans le détail. */
    private static final Set<String> IGNORED_FIELDS = Set.of("_id", "className", "externalId", "schoolExternalId", "orderPlace",
            "creationDate", "lastUpdateDate", "lastUpdateUserId", "creationUserId", "filePath", "filename", "password",
            "passwordHash", "codeHash", "uid");
    private static final Pattern SENSITIVE = Pattern.compile("(?i).*(password|secret|token|hash).*");
    private static final int MAX_VALUE = 300;

    /** Libellés des collections hors écrans génériques. */
    private static final Map<String, String> ENTITY_LABELS = Map.ofEntries(
            Map.entry("user", "utilisateur"), Map.entry("user_group", "groupe d'accès"), Map.entry("Menu", "menu"),
            Map.entry("school", "application"), Map.entry("attachment", "document"), Map.entry("user_image", "photo de profil"),
            Map.entry("Paiement", "facture"), Map.entry(COLLECTION, "historique"));

    public record Change(String field, String label, String before, String after) {
    }

    private final MongoTemplate mongo;
    private final ParamDefinitions definitions;

    public AuditService(MongoTemplate mongo, ParamDefinitions definitions) {
        this.mongo = mongo;
        this.definitions = definitions;
    }

    // ---------- enregistrement ----------

    /** Création : toutes les valeurs saisies. */
    void created(String collection, String externalId, String school, Map<String, Object> fields, String by) {
        if (ignored(collection, by)) return;
        List<Change> changes = new ArrayList<>();
        fields.forEach((k, v) -> {
            if (v != null && shown(k)) changes.add(change(collection, k, null, v));
        });
        record(school, by, CREATION, collection, externalId, label(new Document(fields)), changes);
    }

    /** Modification (ou suppression logique) : seulement les champs dont la valeur change. */
    void updated(String collection, String externalId, String school, Document before, Map<String, Object> fields, String by) {
        if (ignored(collection, by) || before == null) return;
        List<Change> changes = new ArrayList<>();
        fields.forEach((k, v) -> {
            if (shown(k) && !Objects.equals(text(before.get(k)), text(v))) changes.add(change(collection, k, before.get(k), v));
        });
        if (changes.isEmpty()) return;
        boolean deleted = "DELETED".equals(text(fields.get("status"))) && !"DELETED".equals(text(before.get("status")));
        Document after = new Document(before);
        after.putAll(fields);
        record(school != null ? school : before.getString("schoolExternalId"), by, deleted ? SUPPRESSION : MODIFICATION,
                collection, externalId, label(deleted ? before : after), deleted ? List.of() : changes);
    }

    /** Écriture particulière (validation, avancement, mot de passe…) avec son détail. */
    public void record(String school, String by, String action, String collection, String externalId, String targetLabel,
                       List<Change> changes) {
        if (ignored(collection, by)) return;
        try {
            String actorSchool = school;
            Document actor = mongo.findOne(Query.query(Criteria.where("externalId").is(by)), Document.class, "user");
            if (actorSchool == null) actorSchool = targetSchool(collection, externalId, actor);
            Document doc = new Document("className", CLASS_NAME)
                    .append("schoolExternalId", actorSchool)
                    .append("date", new Date())
                    .append("userExternalId", by)
                    .append("userName", actor == null ? by : displayName(actor))
                    .append("action", action)
                    .append("collection", collection)
                    .append("entityLabel", entityLabel(collection))
                    .append("targetExternalId", externalId)
                    .append("targetLabel", truncate(targetLabel))
                    .append("changes", changes.stream().map(c -> new Document("field", c.field()).append("label", c.label())
                            .append("before", c.before()).append("after", c.after())).toList());
            mongo.getCollection(COLLECTION).insertOne(doc);
        } catch (RuntimeException e) {
            // L'historique ne doit jamais empêcher l'enregistrement lui-même.
            log.error("Historique : trace impossible pour {} {}", collection, externalId, e);
        }
    }

    public Change change(String collection, String field, Object before, Object after) {
        return new Change(field, fieldLabel(collection, field), display(collection, field, before), display(collection, field, after));
    }

    // ---------- consultation ----------

    public record Entry(Date date, String userExternalId, String userName, String action, String collection, String entityLabel,
                        String targetExternalId, String targetLabel, List<Change> changes) {
    }

    /** {@code purgeable} : entrées qu'une suppression en masse effacerait (toutes les entrées filtrées). */
    public record Page(List<Entry> items, long total, long purgeable, int page, int size, List<Option> users, List<Option> types) {
    }

    public record Option(String id, String label) {
    }

    /** Filtres de la page Historique (tous facultatifs ; {@code to} exclu). */
    public record Filter(String userId, String collection, String action, Date from, Date to, String text) {
    }

    /** Entrées de l'application correspondant aux filtres. */
    public Criteria criteria(String school, Filter f) {
        Criteria criteria = Criteria.where("schoolExternalId").is(school);
        if (StringUtils.hasText(f.userId())) criteria = criteria.and("userExternalId").is(f.userId());
        if (StringUtils.hasText(f.collection())) criteria = criteria.and("collection").is(f.collection());
        if (StringUtils.hasText(f.action())) criteria = criteria.and("action").is(f.action());
        if (f.from() != null || f.to() != null) {
            Criteria date = Criteria.where("date");
            if (f.from() != null) date = date.gte(f.from());
            if (f.to() != null) date = date.lt(f.to());
            criteria = new Criteria().andOperator(criteria, date);
        }
        if (StringUtils.hasText(f.text())) {
            String q = Pattern.quote(f.text().trim());
            criteria = new Criteria().andOperator(criteria, new Criteria().orOperator(
                    Criteria.where("targetLabel").regex(q, "i"), Criteria.where("userName").regex(q, "i"),
                    Criteria.where("changes.after").regex(q, "i"), Criteria.where("changes.before").regex(q, "i")));
        }
        return criteria;
    }

    public Page search(String school, Filter filter, int page, int size) {
        Query query = Query.query(criteria(school, filter));
        long total = mongo.count(query, COLLECTION);
        long purgeable = total;
        query.with(Sort.by(Sort.Direction.DESC, "date")).skip((long) page * size).limit(size);
        List<Entry> items = mongo.find(query, Document.class, COLLECTION).stream().map(AuditService::entry).toList();

        // Choix des filtres : utilisateurs et types présents dans l'historique de l'application.
        Query all = Query.query(Criteria.where("schoolExternalId").is(school));
        Map<String, String> users = new LinkedHashMap<>();
        Map<String, String> types = new LinkedHashMap<>();
        mongo.find(all.with(Sort.by(Sort.Direction.DESC, "date")).limit(5000), Document.class, COLLECTION).forEach(d -> {
            users.putIfAbsent(d.getString("userExternalId"), d.getString("userName"));
            types.putIfAbsent(d.getString("collection"), d.getString("entityLabel"));
        });
        return new Page(items, total, purgeable, page, size, options(users), options(types));
    }

    private static List<Option> options(Map<String, String> values) {
        return values.entrySet().stream().filter(e -> e.getKey() != null)
                .map(e -> new Option(e.getKey(), e.getValue() == null ? e.getKey() : e.getValue()))
                .sorted((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.label(), b.label())).toList();
    }

    static Entry entry(Document d) {
        List<Change> changes = d.getList("changes", Document.class, List.of()).stream()
                .map(c -> new Change(c.getString("field"), c.getString("label"), c.getString("before"), c.getString("after"))).toList();
        return new Entry(d.getDate("date"), d.getString("userExternalId"), d.getString("userName"), d.getString("action"),
                d.getString("collection"), d.getString("entityLabel"), d.getString("targetExternalId"), d.getString("targetLabel"), changes);
    }

    // ---------- libellés ----------

    private boolean ignored(String collection, String by) {
        return by == null || IGNORED_COLLECTIONS.contains(collection);
    }

    private static boolean shown(String field) {
        return !IGNORED_FIELDS.contains(field) && !SENSITIVE.matcher(field).matches();
    }

    /** Écran générique de la collection (premier trouvé : libellés des champs et des références). */
    private ParamDef screen(String collection) {
        return definitions.all().stream().filter(d -> d.collection().equals(collection)).findFirst().orElse(null);
    }

    private FieldDef field(String collection, String name) {
        ParamDef def = screen(collection);
        return def == null ? null : def.fields().stream().filter(f -> f.name().equals(name)).findFirst().orElse(null);
    }

    String entityLabel(String collection) {
        String known = ENTITY_LABELS.get(collection);
        if (known != null) return known;
        ParamDef def = screen(collection);
        return def != null && def.entityLabel() != null ? def.entityLabel() : collection;
    }

    private String fieldLabel(String collection, String name) {
        FieldDef f = field(collection, name);
        return f != null && f.label() != null ? f.label() : name;
    }

    /** Valeur lisible : libellé d'une référence (au moment du changement), date, liste… */
    private String display(String collection, String name, Object value) {
        if (value == null) return null;
        FieldDef f = field(collection, name);
        if (f != null && f.ref() != null) {
            RefDef ref = definitions.refDef(f.ref());
            if (ref != null) {
                Collection<?> ids = value instanceof Collection<?> c ? c : List.of(value);
                Set<String> labels = new LinkedHashSet<>();
                for (Object id : ids) {
                    Document d = mongo.findOne(Query.query(Criteria.where("externalId").is(String.valueOf(id))), Document.class, ref.collection());
                    labels.add(d == null ? String.valueOf(id) : Objects.toString(ref.label().apply(d), String.valueOf(id)));
                }
                return truncate(String.join(", ", labels));
            }
        }
        return truncate(text(value));
    }

    private static String text(Object value) {
        if (value == null) return null;
        if (value instanceof Date d) {
            return new SimpleDateFormat(d.getTime() % 86_400_000L == 0 ? "dd/MM/yyyy" : "dd/MM/yyyy HH:mm").format(d);
        }
        if (value instanceof Collection<?> c) return String.join(", ", c.stream().map(AuditService::text).toList());
        if (value instanceof Document d) return d.toJson();
        if (value instanceof Double n && n == Math.rint(n) && Math.abs(n) < 1e15) return String.valueOf(n.longValue());
        return String.valueOf(value);
    }

    private static String truncate(String value) {
        return value == null || value.length() <= MAX_VALUE ? value : value.substring(0, MAX_VALUE) + "…";
    }

    /** Libellé de l'élément : titre, nom, numéro… */
    private static String label(Document d) {
        for (String key : List.of("title", "name", "numeroFacture", "numero", "username", "originalFilename", "lastName", "question")) {
            Object v = d.get(key);
            if (v != null && !String.valueOf(v).isBlank()) return String.valueOf(v);
        }
        return null;
    }

    /** École d'une trace sans école : l'application elle-même, l'utilisateur de la photo, sinon celle de l'auteur. */
    private String targetSchool(String collection, String externalId, Document actor) {
        if ("school".equals(collection)) return externalId;
        if ("user_image".equals(collection)) {
            Document image = mongo.findOne(Query.query(Criteria.where("externalId").is(externalId)), Document.class, collection);
            Document user = image == null ? null
                    : mongo.findOne(Query.query(Criteria.where("externalId").is(image.getString("userExternalId"))), Document.class, "user");
            if (user != null) return user.getString("schoolExternalId");
        }
        return actor == null ? null : actor.getString("schoolExternalId");
    }

    private static String displayName(Document u) {
        String name = ((u.getString("lastName") == null ? "" : u.getString("lastName"))
                + (u.getString("firstName") == null ? "" : " " + u.getString("firstName"))).replace("'", " ").trim();
        return name.isEmpty() ? u.getString("username") : name;
    }
}
