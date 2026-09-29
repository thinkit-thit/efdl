package com.ffs.efdl.project;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.ffs.efdl.model.BaseEntity;
import com.ffs.efdl.project.ProgressService.Level;

/**
 * Tableaux de bord (programme, sous-programme et accueil) :
 * budget = somme des montants prévisionnels BE + Ese des sous-projets, avancement physique moyen = somme des
 * avancements / (nombre × 100), jalons achevés (date réelle) et échus (prévision dépassée sans date réelle) par jalon,
 * frise prévision / réel des jalons de l'élément, carte des districts colorés par programme.
 */
@Service
public class DashboardService {

    public record Bar(String label, int rang, int count) {
    }

    public record TimelineRow(String rang, String title, Date debutPrevision, Date prevision, Date debutReel, Date reel) {
    }

    /** {@code districts} / {@code communes} : bénéficiaires, distincts (district et commune des sous-projets actifs). */
    public record Dashboard(String title, double budget, int projets, double avancementMoyen, List<Bar> acheves,
                            List<Bar> echus, List<TimelineRow> frise, long districts, long communes, String color,
                            Map<String, String> districtMap, String programmeTitle, List<Indicator> indicateurs) {
    }

    /** Total d'un indicateur de produit (libellé de l'indicateur KPI → somme des valeurs des sous-projets). */
    public record Indicator(String label, double value) {
    }

    /** {@code communes} : communes bénéficiaires, distinctes (commune des sous-projets actifs du programme). */
    public record ProgrammeCard(String id, String title, String color, double budget, double avancementMoyen, int projets,
                                long communes) {
    }

    /** @param districts code du district → couleur du programme (dernier programme rencontré) */
    public record Home(List<ProgrammeCard> programmes, String programmeId, String programmeTitle, Map<String, String> districts,
                       Map<String, String> districtNames) {
    }

    private final MongoTemplate mongo;
    private final ProgressService progress;

    public DashboardService(MongoTemplate mongo, ProgressService progress) {
        this.mongo = mongo;
        this.progress = progress;
    }

    public Dashboard programme(String school, String id) {
        Document programme = find(school, "Programme", Criteria.where("externalId").is(id));
        List<Document> projects = projects(school, Criteria.where("programmeExternalId").is(id));
        return dashboard(school, programme, projects, Level.PROGRAMME);
    }

    public Dashboard subProgramme(String school, String programmeId, String id) {
        Document sub = find(school, "SubProgramme", Criteria.where("externalId").is(id).and("programmeExternalId").is(programmeId));
        List<Document> projects = projects(school, Criteria.where("subProgrammeExternalId").is(id));
        return dashboard(school, sub, projects, Level.PROJET);
    }

    public Home home(String school, String programmeId) {
        Map<String, Double> percentages = progress.percentages(school);
        List<Document> programmes = mongo.find(Query.query(active(school)).with(Sort.by("order")), Document.class, "Programme");
        List<Document> all = projects(school, new Criteria());
        List<ProgrammeCard> cards = new ArrayList<>();
        for (Document p : programmes) {
            List<Document> own = all.stream().filter(pr -> p.getString("externalId").equals(pr.getString("programmeExternalId"))).toList();
            cards.add(new ProgrammeCard(p.getString("externalId"), p.getString("title"), p.getString("color"), budget(own),
                    average(own, percentages), own.size(),
                    distinct(own, "communeId")));
        }
        Map<String, Document> districtsById = new HashMap<>();
        mongo.find(Query.query(Criteria.where("schoolExternalId").is(school)), Document.class, "District")
                .forEach(d -> districtsById.put(d.getString("externalId"), d));
        Map<String, String> colors = new HashMap<>();
        programmes.forEach(p -> colors.put(p.getString("externalId"), p.getString("color")));
        Map<String, String> districts = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        for (Document pr : all) {
            if (programmeId != null && !programmeId.equals(pr.getString("programmeExternalId"))) continue;
            Document d = districtsById.get(pr.getString("districtId"));
            if (d == null || d.getString("code") == null) continue;
            districts.put(d.getString("code"), colors.getOrDefault(pr.getString("programmeExternalId"), null));
            names.put(d.getString("code"), d.getString("name"));
        }
        String title = programmeId == null ? null
                : programmes.stream().filter(p -> programmeId.equals(p.getString("externalId"))).map(p -> p.getString("title"))
                .findFirst().orElse(null);
        return new Home(cards, programmeId, title, districts, names);
    }

    private Dashboard dashboard(String school, Document entity, List<Document> projects, Level level) {
        Map<String, Double> percentages = progress.percentages(school);
        Map<String, Document> milestones = new HashMap<>();
        mongo.find(Query.query(Criteria.where("schoolExternalId").is(school)), Document.class, "Avancement")
                .forEach(a -> milestones.put(a.getString("externalId"), a));
        Map<String, Integer> done = new LinkedHashMap<>();
        Map<String, Integer> late = new LinkedHashMap<>();
        Date today = new Date();
        for (Document project : projects) {
            Document map = project.get("avancementMap", Document.class);
            if (map == null) continue;
            for (Map.Entry<String, Object> e : map.entrySet()) {
                if (!(e.getValue() instanceof Document v)) continue;
                if (v.getDate("reel") != null) done.merge(e.getKey(), 1, Integer::sum);
                else if (v.getDate("prevision") != null && v.getDate("prevision").before(today)) late.merge(e.getKey(), 1, Integer::sum);
            }
        }
        Document own = entity.get("avancementMap", Document.class);
        List<TimelineRow> frise = new ArrayList<>();
        for (Document a : progress.milestones(school, level)) {
            Document v = own == null ? null : own.get(a.getString("externalId"), Document.class);
            if (v == null) continue;
            frise.add(new TimelineRow(String.valueOf(a.get("rang")), a.getString("title"), v.getDate("debutPrevision"),
                    v.getDate("prevision"), v.getDate("debutReel"), v.getDate("reel")));
        }
        return new Dashboard(entity.getString("title"), budget(projects), projects.size(), average(projects, percentages),
                bars(done, milestones), bars(late, milestones), frise, distinct(projects, "districtId"), distinct(projects, "communeId"),
                color(school, entity), districtMap(school, projects), programmeTitle(school, entity, level), indicators(school, projects));
    }

    private static List<Bar> bars(Map<String, Integer> counts, Map<String, Document> milestones) {
        List<Bar> bars = new ArrayList<>();
        counts.forEach((id, n) -> {
            Document a = milestones.get(id);
            bars.add(new Bar(a == null ? "Jalon supprimé" : a.getString("title"),
                    a != null && a.get("rang") instanceof Number r ? r.intValue() : Integer.MAX_VALUE, n));
        });
        bars.sort(Comparator.comparingInt(Bar::rang));
        return bars;
    }

    private static double budget(List<Document> projects) {
        return projects.stream().mapToDouble(p -> num(p.get("montantPrevBE")) + num(p.get("montantPrevEse"))).sum();
    }

    /** Somme des avancements / (nombre de sous-projets × 100). */
    private static double average(List<Document> projects, Map<String, Double> percentages) {
        if (projects.isEmpty()) return 0;
        double total = projects.stream().mapToDouble(p -> ProgressService.pourcentage(p.get("avancementMap", Document.class), percentages)).sum();
        return total / (projects.size() * 100.0);
    }

    private List<Document> projects(String school, Criteria criteria) {
        Criteria c = active(school);
        criteria.getCriteriaObject().forEach((k, v) -> c.and(k).is(v));
        return mongo.find(Query.query(c), Document.class, "Project");
    }

    private Document find(String school, String collection, Criteria criteria) {
        Criteria c = active(school);
        criteria.getCriteriaObject().forEach((k, v) -> c.and(k).is(v));
        Document d = mongo.findOne(Query.query(c), Document.class, collection);
        if (d == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Élément introuvable");
        return d;
    }

    private static double num(Object v) {
        return v instanceof Number n ? n.doubleValue() : 0;
    }

    private static Criteria active(String school) {
        return Criteria.where("schoolExternalId").is(school).and("status").ne(BaseEntity.STATUS_DELETED);
    }

    /**
     * Indicateurs de produit 1 et 2 : indicateur KPI du sous-projet (son {@code indicateurExternalId},
     * sinon celui de son infrastructure), valeurs saisies sur le sous-projet additionnées par libellé ; seuls les
     * totaux non nuls, dans l'ordre des indicateurs.
     */
    private List<Indicator> indicators(String school, List<Document> projects) {
        Map<String, Document> byId = new HashMap<>();
        Map<String, Document> byInfrastructure = new HashMap<>();
        mongo.find(Query.query(Criteria.where("schoolExternalId").is(school).and("status").ne("DELETED"))
                        .with(Sort.by("ordre")), Document.class, "Indicateur")
                .forEach(k -> {
                    byId.put(k.getString("externalId"), k);
                    if (k.getString("infrastructureExternalId") != null) byInfrastructure.putIfAbsent(k.getString("infrastructureExternalId"), k);
                });
        Map<String, Double> totals = new LinkedHashMap<>();
        // Rang : numéro de l'indicateur KPI, puis produit 1 avant produit 2.
        Map<String, Long> order = new HashMap<>();
        List<String> fields = List.of("indicateurProduit1", "indicateurProduit2");
        for (Document p : projects) {
            Document kpi = byId.get(p.getString("indicateurExternalId"));
            if (kpi == null) kpi = byInfrastructure.get(p.getString("infrastructureExternalId"));
            if (kpi == null) continue;
            long ordre = kpi.get("ordre") instanceof Number n ? n.longValue() : Integer.MAX_VALUE;
            for (int f = 0; f < fields.size(); f++) {
                String field = fields.get(f);
                String label = kpi.getString(field) == null ? "" : kpi.getString(field).trim().replaceAll("\\s+", " ");
                double value = p.get(field) instanceof Number n ? n.doubleValue() : 0;
                if (label.isEmpty() || value == 0) continue;
                totals.merge(label, value, Double::sum);
                order.merge(label, ordre * 2 + f, Math::min);
            }
        }
        return totals.entrySet().stream().filter(e -> e.getValue() != 0)
                .sorted(java.util.Comparator.comparingLong((Map.Entry<String, Double> e) -> order.get(e.getKey())).thenComparing(Map.Entry::getKey))
                .map(e -> new Indicator(e.getKey(), e.getValue())).toList();
    }

    /** Projet : nom de son programme (titres « PROGRAMME - projet ») ; null pour un programme. */
    private String programmeTitle(String school, Document entity, Level level) {
        if (level != Level.PROJET || entity.getString("programmeExternalId") == null) return null;
        Document p = mongo.findOne(Query.query(Criteria.where("schoolExternalId").is(school)
                .and("externalId").is(entity.getString("programmeExternalId"))), Document.class, "Programme");
        return p == null ? null : p.getString("title");
    }

    /** Couleur du programme (celle de son programme pour un projet), comme la carte de l'accueil. */
    private String color(String school, Document entity) {
        if (entity.getString("color") != null) return entity.getString("color");
        String programme = entity.getString("programmeExternalId");
        if (programme == null) return null;
        Document p = mongo.findOne(Query.query(Criteria.where("schoolExternalId").is(school).and("externalId").is(programme)),
                Document.class, "Programme");
        return p == null ? null : p.getString("color");
    }

    /** Districts bénéficiaires (code → nom) des sous-projets, pour la carte. */
    private Map<String, String> districtMap(String school, List<Document> projects) {
        java.util.Set<String> ids = new java.util.HashSet<>();
        projects.forEach(pr -> { if (pr.getString("districtId") != null) ids.add(pr.getString("districtId")); });
        Map<String, String> result = new java.util.TreeMap<>();
        if (ids.isEmpty()) return result;
        mongo.find(Query.query(Criteria.where("schoolExternalId").is(school).and("externalId").in(ids)), Document.class, "District")
                .forEach(d -> { if (d.getString("code") != null) result.put(d.getString("code"), d.getString("name")); });
        return result;
    }

    /** Valeurs distinctes et renseignées d'un champ des sous-projets (districts, communes bénéficiaires). */
    private static long distinct(List<Document> projects, String field) {
        return projects.stream().map(pr -> pr.getString(field)).filter(v -> v != null && !v.isBlank()).distinct().count();
    }
}
