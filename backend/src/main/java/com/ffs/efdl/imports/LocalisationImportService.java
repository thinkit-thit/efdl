package com.ffs.efdl.imports;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.poi.ss.usermodel.Row;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.ffs.efdl.imports.ImportService.Action;
import com.ffs.efdl.imports.ImportService.Line;
import com.ffs.efdl.imports.ImportService.Report;
import com.ffs.efdl.model.BaseEntity;
import com.ffs.efdl.params.ParamDefinitions;
import com.ffs.efdl.params.ParamModel.Hierarchy;
import com.ffs.efdl.params.ParamModel.ParamDef;
import com.ffs.efdl.service.AuditService;
import com.ffs.efdl.service.DocumentWriter;

/**
 * Import Excel du découpage administratif : feuille « base » (CodeProv, Province, CodeReg,
 * REGION, CodeDist, DISTRICT, CodeCom, COMMUNE, CodeFKT, FOKONTANY) et feuille de population (ID Fokontany,
 * Population, Population +18ans).
 * <ul>
 *   <li>rapprochement <b>par code</b> : un élément existant est mis à jour sur place (même identifiant, donc liens des
 *       sous-projets conservés) ; seuls le code, les rattachements et la population peuvent changer, jamais le nom
 *       (noms de la base conservés) ni les autres champs (maire, contacts…) ;</li>
 *   <li>élément renuméroté (code absent de la base, même nom qu'un élément de la base absent du fichier) : code et
 *       rattachements mis à jour sur place ;</li>
 *   <li>nouvel élément : créé au format des documents existants ;</li>
 *   <li>aucune suppression : les éléments de la base absents du fichier sont conservés et signalés.</li>
 * </ul>
 * Créations enregistrées par lots (une ligne d'historique par niveau), modifications une par une (historique détaillé).
 */
@Service
public class LocalisationImportService {

    private static final Logger log = LoggerFactory.getLogger(LocalisationImportService.class);
    private static final int BATCH = 1000;

    /** Niveau : écran, collection, colonnes du fichier, champ du parent, rattachements stockés dans le document. */
    private record Level(String key, String label, String collection, String codeHeader, String nameHeader,
                         String parentField, List<String> ancestors, boolean nameOnlyMatch) {
    }

    private static final List<Level> LEVELS = List.of(
            new Level("provinces", "Province", "Province", "CodeProv", "Province", null, List.of(), true),
            new Level("regions", "Région", "Region", "CodeReg", "REGION", "provinceId", List.of("provinceId"), true),
            new Level("districts", "District", "District", "CodeDist", "DISTRICT", "regionId",
                    List.of("provinceId", "regionId"), true),
            new Level("communes", "Commune", "Commune", "CodeCom", "COMMUNE", "districtId",
                    List.of("provinceId", "regionId", "districtId"), false),
            new Level("fokontanies", "Fokontany", "Fokontany", "CodeFKT", "FOKONTANY", "communeId",
                    List.of("provinceId", "regionId", "districtId", "communeId"), false));

    /** Élément du fichier (première occurrence de son code). */
    private record Item(int line, String code, String name, String parentCode) {
    }

    /** Élément retenu (existant ou créé) : identifiant et rattachements, pour ses enfants. */
    private record Node(String externalId, Map<String, Object> ancestors) {
    }

    private record Population(int total, int adults) {
    }

    private final MongoTemplate mongo;
    private final DocumentWriter writer;
    private final ParamDefinitions definitions;
    private final AuditService audit;

    public LocalisationImportService(MongoTemplate mongo, DocumentWriter writer, ParamDefinitions definitions,
                                     AuditService audit) {
        this.mongo = mongo;
        this.writer = writer;
        this.definitions = definitions;
        this.audit = audit;
    }

    public Report importFile(InputStream in, String school, boolean apply, String by) throws IOException {
        List<ExcelReader> sheets = ExcelReader.allSheets(in);
        ExcelReader base = sheets.stream().filter(s -> s.column("CodeFKT") >= 0 && s.column("CodeCom") >= 0).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Feuille « base » introuvable (colonnes CodeProv, Province, CodeReg, REGION, CodeDist, DISTRICT, "
                                + "CodeCom, COMMUNE, CodeFKT, FOKONTANY)"));
        ExcelReader popSheet = sheets.stream().filter(s -> s != base && s.column("Population") >= 0).findFirst().orElse(null);

        List<Line> details = new ArrayList<>();
        List<Line> errors = new ArrayList<>();
        List<Line> summaries = new ArrayList<>();
        Map<String, Population> population = readPopulation(popSheet, errors);
        List<Map<String, Item>> file = readBase(base, errors);

        int created = 0, updated = 0, unchanged = 0;
        Map<String, Node> parents = Map.of();
        for (int li = 0; li < LEVELS.size(); li++) {
            Level level = LEVELS.get(li);
            Map<String, Item> items = file.get(li);
            ParamDef def = definitions.param(level.key()).orElseThrow();
            Hierarchy hierarchy = definitions.hierarchy(level.key());
            boolean isFokontany = "Fokontany".equals(level.collection());

            List<Document> existing = mongo.find(Query.query(Criteria.where("schoolExternalId").is(school)),
                    Document.class, level.collection());
            Map<String, Document> byCode = new HashMap<>();
            Set<String> deletedCodes = new HashSet<>();
            for (Document d : existing) {
                String code = code(d.get("code"));
                if (code == null) continue;
                if (BaseEntity.STATUS_DELETED.equals(d.getString("status"))) deletedCodes.add(code);
                else byCode.putIfAbsent(code, d);
            }
            // Éléments de la base absents du fichier : candidats à une renumérotation, sinon conservés tels quels.
            List<Document> orphans = new ArrayList<>(byCode.values().stream().filter(d -> !items.containsKey(code(d.get("code")))).toList());

            Map<String, Node> nodes = new HashMap<>();
            List<Document> toInsert = new ArrayList<>();
            int levelCreated = 0, levelUpdated = 0, levelPopulation = 0, levelUnchanged = 0;
            for (Item item : items.values()) {
                Map<String, Object> expected = new LinkedHashMap<>();
                if (level.parentField() != null) {
                    Node parent = parents.get(item.parentCode());
                    if (parent == null) {
                        errors.add(new Line(item.line(), Action.ERREUR, level.label() + " " + item.code() + " " + item.name(),
                                List.of("parent " + item.parentCode() + " introuvable")));
                        continue;
                    }
                    expected.putAll(parent.ancestors());
                    expected.put(level.parentField(), parent.externalId());
                }
                Document doc = byCode.get(item.code());
                boolean recoded = false;
                if (doc == null) {
                    doc = matchOrphan(orphans, item, level, expected);
                    recoded = doc != null;
                }
                if (doc != null) {
                    orphans.remove(doc);
                    Map<String, Object> changes = new LinkedHashMap<>();
                    List<String> messages = new ArrayList<>();
                    if (recoded) {
                        changes.put("code", item.code());
                        messages.add("code " + code(doc.get("code")) + " → " + item.code());
                    }
                    for (String f : level.ancestors()) {
                        if (!Objects.equals(doc.get(f), expected.get(f))) changes.put(f, expected.get(f));
                    }
                    if (changes.containsKey(level.parentField())) messages.add("rattaché à " + parentLabel(item));
                    else if (!changes.isEmpty() && !recoded) messages.add("rattachement corrigé ("
                            + String.join(", ", changes.keySet().stream().map(f -> f.replace("Id", "")).toList()) + ")");
                    boolean structural = !changes.isEmpty();
                    Population pop = isFokontany ? population.get(item.code()) : null;
                    if (pop != null) {
                        if (!Objects.equals(number(doc.get("population")), pop.total())) changes.put("population", pop.total());
                        if (!Objects.equals(number(doc.get("population18")), pop.adults())) changes.put("population18", pop.adults());
                    }
                    if (changes.isEmpty()) {
                        levelUnchanged++;
                    } else {
                        if (apply) writer.update(level.collection(), doc.getString("externalId"), school, changes, by);
                        if (structural) {
                            levelUpdated++;
                            details.add(new Line(item.line(), Action.MISE_A_JOUR, level.label() + " " + doc.getString("name"), messages));
                        } else {
                            levelPopulation++;
                        }
                    }
                    nodes.put(item.code(), new Node(doc.getString("externalId"), new LinkedHashMap<>(expected)));
                    continue;
                }
                if (deletedCodes.contains(item.code())) {
                    errors.add(new Line(item.line(), Action.ERREUR, level.label() + " " + item.code() + " " + item.name(),
                            List.of("code déjà utilisé par un élément supprimé : non créé")));
                    continue;
                }
                String id = DocumentWriter.newExternalId();
                Map<String, Object> fields = new LinkedHashMap<>(expected);
                fields.put("name", item.name());
                fields.put("code", item.code());
                if (hierarchy != null) hierarchy.defaults().forEach(fields::putIfAbsent);
                Population pop = isFokontany ? population.get(item.code()) : null;
                if (pop != null) {
                    fields.put("population", pop.total());
                    fields.put("population18", pop.adults());
                }
                toInsert.add(document(def.className(), id, school, fields, by));
                levelCreated++;
                if (!isFokontany) {
                    details.add(new Line(item.line(), Action.CREATION, level.label() + " " + item.code() + " " + item.name(),
                            List.of(parentLabel(item))));
                }
                nodes.put(item.code(), new Node(id, new LinkedHashMap<>(expected)));
            }
            if (apply && !toInsert.isEmpty()) {
                for (int i = 0; i < toInsert.size(); i += BATCH) {
                    mongo.getCollection(level.collection()).insertMany(toInsert.subList(i, Math.min(i + BATCH, toInsert.size())));
                }
                audit.record(school, by, AuditService.CREATION, level.collection(), null,
                        "Import Excel des localisations : " + toInsert.size() + " " + level.label().toLowerCase() + "(s) créé(s)",
                        List.of());
                log.info("Import localisations : {} {} créés par {}", toInsert.size(), level.collection(), by);
            }
            List<String> summary = new ArrayList<>();
            summary.add(items.size() + " dans le fichier : " + levelCreated + " à créer, " + levelUpdated
                    + " code ou rattachement mis à jour, " + (isFokontany ? levelPopulation + " population mise à jour, " : "")
                    + levelUnchanged + " inchangé(s)");
            if (!orphans.isEmpty()) {
                summary.add(orphans.size() + " élément(s) de la base absent(s) du fichier, conservé(s) sans changement"
                        + linkedProjects(level, orphans, school) + " : "
                        + String.join(", ", orphans.stream().limit(8).map(d -> code(d.get("code")) + " " + d.getString("name")).toList())
                        + (orphans.size() > 8 ? "…" : ""));
            }
            summaries.add(new Line(0, Action.IGNOREE, level.label(), summary));
            created += levelCreated;
            updated += levelUpdated + levelPopulation;
            unchanged += levelUnchanged;
            parents = nodes;
        }
        List<Line> lines = new ArrayList<>(summaries);
        lines.addAll(errors);
        lines.addAll(details);
        if (apply) log.info("Import localisations appliqué : {} créations, {} mises à jour, {} erreurs", created, updated, errors.size());
        return new Report(apply, base.rows.size(), created, updated, unchanged, errors.size(), base.headers, lines);
    }

    /** Élément de la base, absent du fichier, de même nom : même parent de préférence ; nom seul pour les niveaux réorganisés. */
    private static Document matchOrphan(List<Document> orphans, Item item, Level level, Map<String, Object> expected) {
        List<Document> sameName = orphans.stream().filter(d -> norm(d.getString("name")).equals(norm(item.name()))).toList();
        if (level.parentField() != null) {
            List<Document> sameParent = sameName.stream()
                    .filter(d -> Objects.equals(d.get(level.parentField()), expected.get(level.parentField()))).toList();
            if (sameParent.size() == 1) return sameParent.get(0);
        }
        return level.nameOnlyMatch() && sameName.size() == 1 ? sameName.get(0) : null;
    }

    private static String parentLabel(Item item) {
        return item.parentCode() == null ? "" : "parent " + item.parentCode();
    }

    /** Sous-projets rattachés aux éléments conservés hors fichier (communes et fokontany). */
    private String linkedProjects(Level level, List<Document> orphans, String school) {
        String field = switch (level.collection()) {
            case "Commune" -> "communeId";
            case "Fokontany" -> "fokontanyId";
            case "District" -> "districtId";
            default -> null;
        };
        if (field == null) return "";
        long n = mongo.count(Query.query(Criteria.where("schoolExternalId").is(school).and(field)
                .in(orphans.stream().map(d -> d.getString("externalId")).toList())), "Project");
        return n == 0 ? "" : " (" + n + " sous-projet(s) y restent rattachés)";
    }

    private List<Map<String, Item>> readBase(ExcelReader x, List<Line> errors) {
        List<Map<String, Item>> levels = new ArrayList<>();
        for (int i = 0; i < LEVELS.size(); i++) levels.add(new LinkedHashMap<>());
        int[] codeCols = LEVELS.stream().mapToInt(l -> x.column(l.codeHeader())).toArray();
        int[] nameCols = LEVELS.stream().mapToInt(l -> x.column(l.nameHeader())).toArray();
        for (int c : codeCols) if (c < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Colonne de code manquante dans « " + x.sheetName + " »");
        for (Row row : x.rows) {
            int line = row.getRowNum() + 1;
            String[] codes = new String[LEVELS.size()];
            for (int i = 0; i < LEVELS.size(); i++) codes[i] = code(ExcelReader.text(row, codeCols[i]));
            if (codes[LEVELS.size() - 1] == null) continue; // ligne vide
            for (int i = 0; i < LEVELS.size(); i++) {
                String name = ExcelReader.text(row, nameCols[i]);
                if (codes[i] == null || name == null) {
                    errors.add(new Line(line, Action.ERREUR, LEVELS.get(i).label(), List.of("code ou nom manquant")));
                    break;
                }
                String parent = i == 0 ? null : codes[i - 1];
                Item known = levels.get(i).get(codes[i]);
                if (known == null) {
                    levels.get(i).put(codes[i], new Item(line, codes[i], name.trim(), parent));
                } else if (!norm(known.name()).equals(norm(name)) || !Objects.equals(known.parentCode(), parent)) {
                    errors.add(new Line(line, Action.ERREUR, LEVELS.get(i).label() + " " + codes[i] + " " + name,
                            List.of("code déjà utilisé ligne " + known.line() + " par « " + known.name() + " » : ligne ignorée")));
                }
            }
        }
        return levels;
    }

    private static Map<String, Population> readPopulation(ExcelReader x, List<Line> errors) {
        Map<String, Population> map = new HashMap<>();
        if (x == null) return map;
        int codeCol = x.column("ID Fokontany", "CodeFKT", "Code Fokontany");
        int totalCol = x.column("Population");
        int adultCol = x.column("Population +18ans", "Population 18ans", "Population +18 ans");
        for (Row row : x.rows) {
            String code = code(ExcelReader.text(row, codeCol));
            if (code == null) continue;
            try {
                Double total = ExcelReader.number(row, totalCol), adults = ExcelReader.number(row, adultCol);
                if (total == null && adults == null) continue;
                map.putIfAbsent(code, new Population(total == null ? 0 : total.intValue(), adults == null ? 0 : adults.intValue()));
            } catch (IllegalArgumentException e) {
                errors.add(new Line(row.getRowNum() + 1, Action.ERREUR, "Population " + code, List.of(e.getMessage())));
            }
        }
        return map;
    }

    /** Document au format des documents existants (comme {@link DocumentWriter#insert}). */
    private static Document document(String className, String externalId, String school, Map<String, Object> fields, String by) {
        Date now = new Date();
        Document doc = new Document("_id", new ObjectId()).append("className", className);
        fields.forEach((k, v) -> {
            if (v != null) doc.append(k, v);
        });
        doc.append("schoolExternalId", school).append("orderPlace", 0).append("externalId", externalId)
                .append("creationDate", now).append("lastUpdateDate", now);
        if (by != null) doc.append("lastUpdateUserId", by);
        return doc;
    }

    /** Code texte sans « .0 » final (cellules numériques). */
    private static String code(Object value) {
        if (value == null) return null;
        String s = value instanceof Number n ? String.valueOf(n.longValue()) : value.toString().trim();
        if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
        return s.isEmpty() ? null : s;
    }

    private static Integer number(Object value) {
        return value instanceof Number n ? n.intValue() : null;
    }

    private static String norm(String s) {
        return ExcelReader.normalize(s);
    }
}
