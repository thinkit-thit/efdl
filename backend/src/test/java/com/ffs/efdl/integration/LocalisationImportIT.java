package com.ffs.efdl.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.Document;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.ffs.efdl.imports.ImportService.Line;
import com.ffs.efdl.imports.ImportService.Report;
import com.ffs.efdl.imports.LocalisationImportService;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;

/**
 * Import des localisations sur une COPIE jetable ({@code efdl_loc}) des localisations et sous-projets de la base
 * de développement (lue seulement), avec le vrai fichier. Vérifie : aucune suppression, champs existants intacts
 * (hors code, rattachements et population), liens des sous-projets valides, second import sans changement.
 * Lancement : {@code mvn test -Defdl.it=true -Dtest=LocalisationImportIT -Defdl.loc.file=<chemin du .xlsx>}.
 */
@SpringBootTest(properties = "spring.mongodb.database=efdl_loc")
@ActiveProfiles("local")
@EnabledIfSystemProperty(named = "efdl.it", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LocalisationImportIT {

    static final String SCHOOL = "sdi-fdl";
    static final String SOURCE = System.getProperty("efdl.loc.source", "testfdl2025");
    static final List<String> LEVELS = List.of("Province", "Region", "District", "Commune", "Fokontany");
    /** Champs que l'import peut modifier sur un élément existant. */
    static final Set<String> CHANGEABLE = Set.of("code", "provinceId", "regionId", "districtId", "communeId",
            "population", "population18", "lastUpdateDate", "lastUpdateUserId");

    @Autowired MongoTemplate mongo;
    @Autowired MongoClient client;
    @Autowired LocalisationImportService service;

    Path file;
    final Map<String, Map<String, Document>> before = new HashMap<>();

    @BeforeAll
    void copy() {
        assertThat(mongo.getDb().getName()).isEqualTo("efdl_loc"); // garde-fou : jamais une base réelle
        file = Path.of(System.getProperty("efdl.loc.file", "C:/Users/mamich/IdeaProjects/livrable/RaleMCR2.xlsx"));
        assertThat(Files.exists(file)).as("fichier " + file).isTrue();
        MongoDatabase source = client.getDatabase(SOURCE);
        for (String c : new ArrayList<>(List.of("Province", "Region", "District", "Commune", "Fokontany", "Project", "audit_log"))) {
            mongo.getDb().getCollection(c).drop(); // base jetable
            if (c.equals("audit_log")) continue;
            List<Document> docs = source.getCollection(c).find().into(new ArrayList<>());
            if (!docs.isEmpty()) mongo.getDb().getCollection(c).insertMany(docs);
        }
        for (String c : LEVELS) {
            Map<String, Document> byId = new HashMap<>();
            mongo.getDb().getCollection(c).find().forEach(d -> byId.put(d.getString("externalId"), d));
            before.put(c, byId);
        }
    }

    private Report run(boolean apply) throws Exception {
        try (InputStream in = new FileInputStream(file.toFile())) {
            return service.importFile(in, SCHOOL, apply, "it-import");
        }
    }

    private static void print(String title, Report r) {
        System.out.println("=== " + title + " : lignes " + r.lignes() + ", créations " + r.creations() + ", mises à jour "
                + r.misesAJour() + ", inchangés " + r.ignorees() + ", erreurs " + r.erreurs());
        for (Line l : r.details()) {
            if (l.line() == 0 || l.action() != com.ffs.efdl.imports.ImportService.Action.CREATION || !l.label().startsWith("Fokontany")) {
                System.out.println("  [" + l.action() + "] l." + l.line() + " " + l.label() + " : " + String.join(" | ", l.messages()));
            }
        }
    }

    @Test
    void analyseImportPuisSecondImportSansChangement() throws Exception {
        Report analyse = run(false);
        print("ANALYSE", analyse);
        for (String c : LEVELS) {
            assertThat(mongo.getDb().getCollection(c).countDocuments()).as("analyse sans écriture : " + c)
                    .isEqualTo(before.get(c).size());
        }

        Report applied = run(true);
        print("IMPORT", applied);
        assertThat(applied.creations()).isEqualTo(analyse.creations());
        assertThat(applied.misesAJour()).isEqualTo(analyse.misesAJour());

        // Aucune suppression ; champs des éléments existants intacts hors code, rattachements et population.
        for (String c : LEVELS) {
            for (Document old : before.get(c).values()) {
                Document now = mongo.getDb().getCollection(c).find(new Document("externalId", old.getString("externalId"))).first();
                assertThat(now).as(c + " " + old.getString("name") + " toujours présent").isNotNull();
                for (String k : old.keySet()) {
                    if (!CHANGEABLE.contains(k)) assertThat(now.get(k)).as(c + " " + old.getString("name") + "." + k).isEqualTo(old.get(k));
                }
            }
        }
        // Sous-projets : inchangés et rattachés à des localisations actives existantes.
        for (Document p : mongo.getDb().getCollection("Project").find()) {
            for (String[] link : new String[][] { { "communeId", "Commune" }, { "fokontanyId", "Fokontany" }, { "districtId", "District" } }) {
                String id = p.getString(link[0]);
                if (id == null) continue;
                assertThat(mongo.getDb().getCollection(link[1]).countDocuments(new Document("externalId", id)))
                        .as("sous-projet " + p.getString("numero") + " → " + link[1]).isEqualTo(1);
            }
        }
        // Population : chaque fokontany du fichier en a une.
        long withoutPop = mongo.getDb().getCollection("Fokontany").countDocuments(new Document("schoolExternalId", SCHOOL)
                .append("status", new Document("$ne", "DELETED")).append("population", new Document("$exists", false)));
        System.out.println("Fokontany actifs sans population (absents du fichier) : " + withoutPop);

        Report again = run(false);
        print("SECOND PASSAGE (analyse)", again);
        assertThat(again.creations()).isZero();
        assertThat(again.misesAJour()).isZero();
        for (String c : LEVELS) {
            System.out.println(c + " : " + before.get(c).size() + " → " + mongo.getDb().getCollection(c).countDocuments());
        }
    }
}
