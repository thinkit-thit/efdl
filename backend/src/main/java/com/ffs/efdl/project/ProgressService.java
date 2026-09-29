package com.ffs.efdl.project;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.ffs.efdl.service.AuditService;
import com.ffs.efdl.model.BaseEntity;
import com.ffs.efdl.params.RowEnricher;

/**
 * Suivi d'avancement des programmes, sous-programmes et sous-projets : jalons du type
 * correspondant (Avancement.typeAvancement), dates prévisionnelle / réelle et « evidence » stockées dans
 * {@code avancementMap} (clé = externalId du jalon, valeur = AvancementProjet embarqué).
 * <p>
 * Les valeurs enregistrées sont relues à l'affichage
 * et seules les lignes envoyées sont modifiées.
 * Statut de chaque jalon : TERMINE, TERMINE_EN_RETARD, EN_RETARD, EN_COURS.
 */
@Service
public class ProgressService implements RowEnricher {

    /** Niveaux : collection, type de jalon, parent obligatoire. */
    public enum Level {
        PROGRAMME("Programme", "PROGRAMME"), PROJET("SubProgramme", "PROJET"), SOUS_PROJET("Project", "SOUS_PROJET");

        final String collection;
        final String type;

        Level(String collection, String type) {
            this.collection = collection;
            this.type = type;
        }
    }

    public record Milestone(String avancementId, int rang, String title, double pourcentage, Date prevision, Date reel,
                            Date debutPrevision, Date debutReel, String evidence, String statut) {
    }

    public record Progress(String title, List<Milestone> jalons, double pourcentage) {
    }

    /** Ligne envoyée par l'écran : dates au format yyyy-MM-dd (vides = effacées). */
    public record MilestoneInput(String avancementId, String prevision, String reel, String evidence) {
    }

    private final MongoTemplate mongo;

    private final AuditService audit;

    public ProgressService(MongoTemplate mongo, AuditService audit) {
        this.audit = audit;
        this.mongo = mongo;
    }

    public Progress get(String school, Level level, String id, Map<String, String> parents) {
        Document entity = entity(school, level, id, parents);
        Document map = entity.get("avancementMap", Document.class);
        List<Milestone> jalons = new ArrayList<>();
        for (Document a : milestones(school, level)) {
            Document v = map == null ? null : map.get(a.getString("externalId"), Document.class);
            jalons.add(new Milestone(a.getString("externalId"), intOf(a.get("rang")), a.getString("title"), dbl(a.get("pourcentage")),
                    v == null ? null : v.getDate("prevision"), v == null ? null : v.getDate("reel"),
                    v == null ? null : v.getDate("debutPrevision"), v == null ? null : v.getDate("debutReel"),
                    v == null ? null : v.getString("evidence"), v == null ? null : statut(v.getDate("prevision"), v.getDate("reel"))));
        }
        return new Progress(entity.getString("title"), jalons, pourcentage(map, percentages(school)));
    }

    public void save(String school, Level level, String id, Map<String, String> parents, List<MilestoneInput> rows,
                     String userExternalId) {
        Document entity = entity(school, level, id, parents);
        Map<String, Document> milestones = milestones(school, level).stream()
                .collect(Collectors.toMap(a -> a.getString("externalId"), a -> a));
        Document map = entity.get("avancementMap", Document.class);
        Date now = new Date();
        Update update = new Update().set("lastUpdateDate", now).set("lastUpdateUserId", userExternalId);
        List<AuditService.Change> changes = new java.util.ArrayList<>();
        for (MilestoneInput row : rows == null ? List.<MilestoneInput>of() : rows) {
            Document milestone = milestones.get(row.avancementId());
            if (milestone == null) throw bad("Jalon inconnu pour ce niveau");
            Date prevision = day(row.prevision());
            Date reel = day(row.reel());
            String evidence = row.evidence() == null || row.evidence().isBlank() ? null : row.evidence().trim();
            Document existing = map == null ? null : map.get(row.avancementId(), Document.class);
            // Entrée AvancementProjet embarquée : identifiant et date de création conservés.
            Document value = existing == null
                    ? new Document("orderPlace", 0).append("externalId", UUID.randomUUID().toString()).append("creationDate", now)
                    : new Document(existing);
            // Historique : valeurs du jalon avant / après.
            String jalon = milestone.getString("title");
            for (String[] f : new String[][] { { "prevision", "prévision" }, { "reel", "réel" }, { "evidence", "evidence" } }) {
                Object before = existing == null ? null : existing.get(f[0]);
                Object after = "prevision".equals(f[0]) ? prevision : "reel".equals(f[0]) ? reel : evidence;
                AuditService.Change c = audit.change(level.collection, f[0], before, after);
                if (!java.util.Objects.equals(c.before(), c.after())) {
                    changes.add(new AuditService.Change(f[0], jalon + " — " + f[1], c.before(), c.after()));
                }
            }
            put(value, "prevision", prevision);
            put(value, "reel", reel);
            put(value, "evidence", evidence);
            value.put("titre", milestone.getString("title"));
            value.put("statutAvancement", statut(prevision, reel));
            value.put("userId", userExternalId);
            value.put("lastUpdateDate", now);
            update.set("avancementMap." + row.avancementId(), value);
        }
        mongo.updateFirst(Query.query(Criteria.where("schoolExternalId").is(school).and("externalId").is(id)), update, level.collection);
        if (!changes.isEmpty()) {
            String label = entity.getString("numero") != null ? entity.getString("numero") + " " + entity.getString("title") : entity.getString("title");
            audit.record(school, userExternalId, AuditService.MODIFICATION, level.collection, id, "Avancement : " + label, changes);
        }
    }

    // ---------- colonne « Avancement (%) » des listes de sous-projets ----------

    @Override
    public String key() {
        return "projects";
    }

    @Override
    public boolean supports(String screenKey) {
        return Set.of("projects", "allProjects", "regionProjects", "districtProjects").contains(screenKey);
    }

    @Override
    public void enrich(String school, List<Map<String, Object>> rows) {
        Map<String, Double> percentages = percentages(school);
        Set<Object> ids = rows.stream().map(r -> r.get("externalId")).collect(Collectors.toSet());
        Query query = Query.query(Criteria.where("schoolExternalId").is(school).and("externalId").in(ids));
        query.fields().include("externalId", "avancementMap");
        Map<String, Document> maps = new HashMap<>();
        mongo.find(query, Document.class, "Project").forEach(d -> maps.put(d.getString("externalId"), d.get("avancementMap", Document.class)));
        rows.forEach(r -> r.put("avancementPct", pourcentage(maps.get((String) r.get("externalId")), percentages)));
    }

    /** Comme calculerPourcentageAvancement : somme des pourcentages des jalons ayant une date réelle. */
    static double pourcentage(Document map, Map<String, Double> percentages) {
        if (map == null) return 0;
        double total = 0;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (e.getValue() instanceof Document v && v.getDate("reel") != null) total += percentages.getOrDefault(e.getKey(), 0.0);
        }
        return total;
    }

    /** Pourcentage de chaque jalon de l'école. */
    Map<String, Double> percentages(String school) {
        Map<String, Double> values = new HashMap<>();
        mongo.find(Query.query(Criteria.where("schoolExternalId").is(school)), Document.class, "Avancement")
                .forEach(a -> values.put(a.getString("externalId"), dbl(a.get("pourcentage"))));
        return values;
    }

    /** Statut d'un jalon d'après ses dates prévisionnelle et réelle. */
    static String statut(Date prevision, Date reel) {
        if (reel != null) return prevision != null && prevision.before(reel) ? "TERMINE_EN_RETARD" : "TERMINE";
        return prevision != null && prevision.before(new Date()) ? "EN_RETARD" : "EN_COURS";
    }

    List<Document> milestones(String school, Level level) {
        return mongo.find(Query.query(active(school).and("typeAvancement").is(level.type)).with(Sort.by("rang")),
                Document.class, "Avancement");
    }

    /** Élément de l'école, rattaché aux parents de l'URL (programme, sous-programme). */
    private Document entity(String school, Level level, String id, Map<String, String> parents) {
        Criteria criteria = active(school).and("externalId").is(id);
        String programme = parents.get("programmeId");
        String subProgramme = parents.get("subProgrammeId");
        if (level != Level.PROGRAMME) {
            if (programme == null) throw bad("Paramètre manquant : programmeId");
            criteria = criteria.and("programmeExternalId").is(programme);
        }
        if (level == Level.SOUS_PROJET) {
            if (subProgramme == null) throw bad("Paramètre manquant : subProgrammeId");
            criteria = criteria.and("subProgrammeExternalId").is(subProgramme);
        }
        Document entity = mongo.findOne(Query.query(criteria), Document.class, level.collection);
        if (entity == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Élément introuvable");
        return entity;
    }

    private static void put(Document d, String key, Object value) {
        if (value == null) d.remove(key); else d.put(key, value);
    }

    private static Date day(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Date.from(LocalDate.parse(value.trim().substring(0, 10)).atStartOfDay(ZoneId.systemDefault()).toInstant());
        } catch (DateTimeParseException | StringIndexOutOfBoundsException e) {
            throw bad("Date invalide : " + value);
        }
    }

    private static int intOf(Object v) {
        return v instanceof Number n ? n.intValue() : 0;
    }

    private static double dbl(Object v) {
        return v instanceof Number n ? n.doubleValue() : 0;
    }

    private static Criteria active(String school) {
        return Criteria.where("schoolExternalId").is(school).and("status").ne(BaseEntity.STATUS_DELETED);
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
