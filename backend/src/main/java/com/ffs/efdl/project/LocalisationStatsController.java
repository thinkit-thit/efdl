package com.ffs.efdl.project;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ffs.efdl.model.BaseEntity;
import com.ffs.efdl.model.School;
import com.ffs.efdl.security.RoutePath;
import com.ffs.efdl.security.RoutePathInterceptor;
import com.ffs.efdl.security.TenantResolver;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Tableau de bord des localisations (/admin/dashboard/election/tbs) : nombre de provinces, régions, districts,
 * communes et fokontany enregistrés dans l'application (éléments supprimés exclus).
 */
@RestController
public class LocalisationStatsController {

    public record Counts(long provinces, long regions, long districts, long communes, long fokontany) {
    }

    /**
     * Un district : communes bénéficiaires des projets FDL (communes distinctes des sous-projets actifs) et population
     * (somme de ses fokontany actifs).
     */
    public record DistrictRow(String code, String name, long communes, long population, long population18) {
    }

    private final MongoTemplate mongo;
    private final TenantResolver tenantResolver;

    public LocalisationStatsController(MongoTemplate mongo, TenantResolver tenantResolver) {
        this.mongo = mongo;
        this.tenantResolver = tenantResolver;
    }

    @GetMapping("/api/dashboards/localisation")
    @RoutePath("/admin/dashboard/election/tbs")
    public Counts counts(HttpServletRequest request) {
        String school = tenantResolver.resolve(request).map(School::getExternalId)
                .orElse(RoutePathInterceptor.currentUser().schoolExternalId());
        return new Counts(count("Province", school), count("Region", school), count("District", school),
                count("Commune", school), count("Fokontany", school));
    }

    /** Districts triés par nombre de communes bénéficiaires, puis par population (décroissants). */
    @GetMapping("/api/dashboards/localisation/districts")
    @RoutePath("/admin/dashboard/election/tbs")
    public List<DistrictRow> districts(HttpServletRequest request) {
        String school = tenantResolver.resolve(request).map(School::getExternalId)
                .orElse(RoutePathInterceptor.currentUser().schoolExternalId());
        // District de chaque commune active.
        Map<String, String> communeDistrict = new HashMap<>();
        for (Document c : find("Commune", school, "externalId", "districtId")) {
            communeDistrict.put(c.getString("externalId"), c.getString("districtId"));
        }
        // Communes bénéficiaires : communes distinctes des sous-projets actifs, rattachées au district de la commune.
        Map<String, Set<String>> beneficiaries = new HashMap<>();
        for (Document p : find("Project", school, "communeId", "districtId")) {
            String commune = p.getString("communeId");
            if (commune == null) continue;
            String district = communeDistrict.getOrDefault(commune, p.getString("districtId"));
            if (district != null) beneficiaries.computeIfAbsent(district, k -> new HashSet<>()).add(commune);
        }
        // Population : somme des fokontany actifs du district.
        Map<String, long[]> population = new HashMap<>();
        for (Document f : find("Fokontany", school, "districtId", "population", "population18")) {
            long[] sums = population.computeIfAbsent(f.getString("districtId"), k -> new long[2]);
            sums[0] += number(f.get("population"));
            sums[1] += number(f.get("population18"));
        }
        List<DistrictRow> rows = new ArrayList<>();
        for (Document d : find("District", school, "externalId", "code", "name")) {
            String id = d.getString("externalId");
            long[] pop = population.getOrDefault(id, new long[2]);
            rows.add(new DistrictRow(d.get("code") == null ? null : d.get("code").toString(), d.getString("name"),
                    beneficiaries.getOrDefault(id, Set.of()).size(), pop[0], pop[1]));
        }
        rows.sort(Comparator.comparingLong(DistrictRow::communes).reversed()
                .thenComparing(Comparator.comparingLong(DistrictRow::population).reversed())
                .thenComparing(r -> r.name() == null ? "" : r.name()));
        return rows;
    }

    private List<Document> find(String collection, String school, String... fields) {
        Query query = Query.query(Criteria.where("schoolExternalId").is(school).and("status").ne(BaseEntity.STATUS_DELETED));
        query.fields().include(fields);
        return mongo.find(query, Document.class, collection);
    }

    private static long number(Object value) {
        return value instanceof Number n ? n.longValue() : 0;
    }

    private long count(String collection, String school) {
        return mongo.count(Query.query(Criteria.where("schoolExternalId").is(school)
                .and("status").ne(BaseEntity.STATUS_DELETED)), collection);
    }
}
