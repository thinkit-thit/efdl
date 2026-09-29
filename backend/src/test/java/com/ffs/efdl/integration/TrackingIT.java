package com.ffs.efdl.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Date;
import java.util.List;
import java.util.Set;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.ffs.efdl.model.UserType;
import com.ffs.efdl.security.EfdlUserPrincipal;
import com.jayway.jsonpath.JsonPath;

/**
 * Avancement et tableaux de bord FDL PROJET, sur la base de TEST jetable {@code efdl_it}.
 * Lancement : {@code mvn test -Defdl.it=true -Dtest=TrackingIT}.
 */
@SpringBootTest(properties = "spring.mongodb.database=efdl_it")
@ActiveProfiles("local")
@EnabledIfSystemProperty(named = "efdl.it", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrackingIT {

    static final String SCHOOL = "sdi-fdl";
    static final String P = "com.ffs.kiwi.model.project.";

    @Autowired WebApplicationContext context;
    @Autowired MongoTemplate mongo;
    MockMvc mvc;

    @BeforeAll
    void seed() {
        assertThat(mongo.getDb().getName()).isEqualTo("efdl_it"); // garde-fou : jamais la base réelle
        mongo.getDb().drop();
        insert("school", "com.ffs.kiwi.model.School", SCHOOL, new Document("name", "sdi-fdl"), false);
        insert("Programme", P + "Programme", "prog", new Document("title", "PDCID II").append("color", "green").append("order", 0)
                .append("financement", "Banque mondiale").append("objectifGlobal", "Améliorer les services de base"), true);
        insert("SubProgramme", P + "SubProgramme", "sub", new Document("title", "BOENY").append("programmeExternalId", "prog"), true);
        // Indicateur KPI de l'infrastructure « inf1 » : p1 y est lié par son infrastructure, p2 par indicateurExternalId.
        insert("Indicateur", P + "Indicateur", "kpi1", new Document("ordre", 1).append("infrastructureExternalId", "inf1")
                .append("indicateurProduit1", "Superficie du bâtiment EPP bâti (m²)").append("indicateurProduit2", "Nombre de salles de classe"), true);
        insert("District", "com.ffs.kiwi.model.dashboard.election.District", "d1", new Document("name", "MAJUNGA I").append("code", "4101"), true);
        insert("Project", P + "Project", "p1", new Document("numero", "21-001").append("title", "EPP").append("programmeExternalId", "prog")
                .append("subProgrammeExternalId", "sub").append("districtId", "d1").append("communeId", "c1").append("infrastructureExternalId", "inf1").append("indicateurProduit1", 800.0)
                .append("indicateurProduit2", 3.0).append("montantPrevBE", 100.0).append("montantPrevEse", 900.0), true);
        insert("Project", P + "Project", "p2", new Document("numero", "21-002").append("title", "CSB").append("programmeExternalId", "prog")
                .append("subProgrammeExternalId", "sub").append("districtId", "d1").append("communeId", "c1").append("indicateurExternalId", "kpi1").append("indicateurProduit1", 700.0)
                .append("indicateurProduit2", 0.0).append("montantPrevBE", 0.0).append("montantPrevEse", 1000.0), true);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private void insert(String collection, String className, String id, Document fields, boolean schoolScoped) {
        Document d = new Document("_id", new ObjectId()).append("className", className);
        d.putAll(fields);
        if (schoolScoped) d.append("schoolExternalId", SCHOOL).append("orderPlace", 0);
        d.append("externalId", id).append("creationDate", new Date()).append("lastUpdateDate", new Date());
        mongo.getCollection(collection).insertOne(d);
    }

    private String send(MockHttpServletRequestBuilder req, String json, int expected) throws Exception {
        EfdlUserPrincipal admin = new EfdlUserPrincipal("it-admin", "admin", "IT", UserType.ADMIN, SCHOOL, Set.of(), null);
        return mvc.perform(req.with(authentication(UsernamePasswordAuthenticationToken.authenticated(admin, null, admin.authorities())))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
    }

    private String jalon(String type, int rang, String title, double pct) throws Exception {
        return JsonPath.read(send(post("/api/params/avancements"), "{\"typeAvancement\":\"" + type + "\",\"rang\":" + rang
                + ",\"title\":\"" + title + "\",\"pourcentage\":" + pct + "}", 201), "$.externalId");
    }

    @Test
    void avancementEtTableauxDeBord() throws Exception {
        String sp1 = jalon("SOUS_PROJET", 1, "Transfert de fonds", 40);
        String sp2 = jalon("SOUS_PROJET", 2, "Démarrage local", 60);
        String pg1 = jalon("PROGRAMME", 1, "Signature convention", 10);
        jalon("PROJET", 1, "Convention", 10);

        // Avancement d'un sous-projet : jalons du bon type, valeurs relues, statut calculé, identifiant conservé.
        String base = "/api/progress/programme/prog/subProgramme/sub/project/p1";
        send(get("/api/progress/programme/prog/subProgramme/autre/project/p1"), "", 404);
        send(put(base), "{\"jalons\":[{\"avancementId\":\"" + pg1 + "\",\"prevision\":\"2026-01-01\"}]}", 400); // mauvais type
        send(put(base), "{\"jalons\":[{\"avancementId\":\"" + sp1 + "\",\"prevision\":\"2026-01-10\",\"reel\":\"2026-02-01\","
                + "\"evidence\":\"OV n°12\"},{\"avancementId\":\"" + sp2 + "\",\"prevision\":\"2020-01-01\"}]}", 204);
        String progress = send(get(base), "", 200);
        assertThat(JsonPath.<List<String>>read(progress, "$.jalons[*].title")).containsExactly("Transfert de fonds", "Démarrage local");
        assertThat(JsonPath.<List<String>>read(progress, "$.jalons[*].statut")).containsExactly("TERMINE_EN_RETARD", "EN_RETARD");
        assertThat(JsonPath.<List<String>>read(progress, "$.jalons[0].evidence") + "").contains("OV n°12");
        assertThat((Double) JsonPath.read(progress, "$.pourcentage")).isEqualTo(40.0);
        // Dates relues au format ISO (champ date de l'écran) : 10/01/2026 minuit, fuseau du serveur.
        String prevision = JsonPath.read(progress, "$.jalons[0].prevision");
        assertThat(java.time.OffsetDateTime.parse(prevision).atZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalDate())
                .isEqualTo(java.time.LocalDate.of(2026, 1, 10));
        Document entry = mongo.getCollection("Project").find(new Document("externalId", "p1")).first()
                .get("avancementMap", Document.class).get(sp1, Document.class);
        String entryId = entry.getString("externalId");
        assertThat(entry.getString("titre")).isEqualTo("Transfert de fonds");
        assertThat(entry.get("creationDate")).isNotNull();
        send(put(base), "{\"jalons\":[{\"avancementId\":\"" + sp1 + "\",\"prevision\":\"2026-01-10\",\"reel\":\"2026-01-05\"}]}", 204);
        entry = mongo.getCollection("Project").find(new Document("externalId", "p1")).first()
                .get("avancementMap", Document.class).get(sp1, Document.class);
        assertThat(entry.getString("externalId")).isEqualTo(entryId);
        assertThat(entry.getString("statutAvancement")).isEqualTo("TERMINE");
        assertThat(entry.containsKey("evidence")).isFalse(); // vidée à l'écran
        assertThat(mongo.getCollection("Project").find(new Document("externalId", "p1")).first()
                .get("avancementMap", Document.class).containsKey(sp2)).isTrue(); // autre jalon conservé

        // Colonne « Avancement (%) » des listes de sous-projets.
        String list = send(get("/api/params/projects?programmeId=prog&subProgrammeId=sub"), "", 200);
        assertThat(JsonPath.<List<Double>>read(list, "$.rows[?(@.externalId=='p1')].avancementPct")).containsExactly(40.0);
        assertThat(JsonPath.<List<Double>>read(list, "$.rows[?(@.externalId=='p2')].avancementPct")).containsExactly(0.0);

        // Avancement du programme : alimente la frise du tableau de bord.
        send(put("/api/progress/programme/prog"), "{\"jalons\":[{\"avancementId\":\"" + pg1 + "\",\"prevision\":\"2026-03-01\"}]}", 204);
        String tb = send(get("/api/dashboards/programme/prog"), "", 200);
        // Bénéficiaires distincts : 2 sous-projets dans le même district et la même commune.
        assertThat((Integer) JsonPath.read(tb, "$.districts")).isEqualTo(1);
        assertThat((Integer) JsonPath.read(tb, "$.communes")).isEqualTo(1);
        // Carte : districts bénéficiaires (code → nom), couleur du programme.
        assertThat(JsonPath.<String>read(tb, "$.districtMap.4101")).isEqualTo("MAJUNGA I");
        assertThat(JsonPath.<String>read(tb, "$.color")).isEqualTo("green");
        // Indicateurs de produit : totaux par libellé, seulement les non nuls.
        assertThat(JsonPath.<List<String>>read(tb, "$.indicateurs[*].label"))
                .containsExactly("Superficie du bâtiment EPP bâti (m²)", "Nombre de salles de classe");
        assertThat(JsonPath.<List<Double>>read(tb, "$.indicateurs[*].value")).containsExactly(1500.0, 3.0);
        exportDashboard();
        assertThat((Double) JsonPath.read(tb, "$.budget")).isEqualTo(2000.0);
        assertThat((Integer) JsonPath.read(tb, "$.projets")).isEqualTo(2);
        assertThat((Double) JsonPath.read(tb, "$.avancementMoyen")).isEqualTo(0.2); // (40 + 0) / (2 × 100)
        assertThat(JsonPath.<List<String>>read(tb, "$.acheves[*].label")).containsExactly("Transfert de fonds");
        assertThat(JsonPath.<List<String>>read(tb, "$.echus[*].label")).containsExactly("Démarrage local");
        assertThat(JsonPath.<List<String>>read(tb, "$.frise[*].title")).containsExactly("Signature convention");
        String tbSub = send(get("/api/dashboards/programme/prog/subProgramme/sub"), "", 200);
        assertThat((Integer) JsonPath.read(tbSub, "$.projets")).isEqualTo(2);
        assertThat(JsonPath.<String>read(tbSub, "$.programmeTitle")).isEqualTo("PDCID II");
        assertThat((Object) JsonPath.read(tb, "$.programmeTitle")).isNull();
        send(get("/api/dashboards/programme/autre/subProgramme/sub"), "", 404);

        // Accueil : cartes programme et carte des districts (couleur du programme).
        String home = send(get("/api/dashboards/home"), "", 200);
        assertThat(JsonPath.<List<Double>>read(home, "$.programmes[*].budget")).containsExactly(2000.0);
        assertThat(JsonPath.<String>read(home, "$.districts.4101")).isEqualTo("green");
        // Communes bénéficiaires : distinctes (2 sous-projets dans la même commune = 1).
        assertThat(JsonPath.<List<Integer>>read(home, "$.programmes[*].communes")).containsExactly(1);
        assertThat(JsonPath.<String>read(send(get("/api/dashboards/home?programmeId=prog"), "", 200), "$.programmeTitle")).isEqualTo("PDCID II");

        // Sous-projets d'un district, désigné par son code.
        assertThat(JsonPath.<List<String>>read(send(get("/api/params/districtProjects?districtCode=4101"), "", 200), "$.rows[*].externalId"))
                .containsExactlyInAnyOrder("p1", "p2");
        assertThat(JsonPath.<List<String>>read(send(get("/api/params/districtProjects?districtCode=4101"), "", 200), "$.parents[*].label"))
                .containsExactly("MAJUNGA I");
        send(get("/api/params/districtProjects?districtCode=9999"), "", 404);
        System.out.println("IDS-POUR-MORPHIA-TRACKING project=p1 programme=prog");
    }

    /** Export du tableau de bord : PDF et Word, avec l'en-tête du programme et l'image de la carte envoyée par l'écran. */
    private void exportDashboard() throws Exception {
        EfdlUserPrincipal admin = new EfdlUserPrincipal("it-admin", "admin", "IT", UserType.ADMIN, SCHOOL, Set.of(), null);
        java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(90, 160, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", png);
        var map = new org.springframework.mock.web.MockMultipartFile("map", "carte.png", "image/png", png.toByteArray());
        var auth = authentication(UsernamePasswordAuthenticationToken.authenticated(admin, null, admin.authorities()));
        var pdf = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart("/api/dashboards/programme/prog/export?format=pdf").file(map).with(auth).with(csrf()))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(pdf.getContentType()).isEqualTo("application/pdf");
        assertThat(new String(pdf.getContentAsByteArray(), 0, 5)).isEqualTo("%PDF-");
        assertThat(pdf.getHeader("Content-Disposition")).contains("tableau-de-bord-PDCID_II.pdf");
        var docx = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart("/api/dashboards/programme/prog/export?format=docx").file(map).with(auth).with(csrf()))
                .andExpect(status().isOk()).andReturn().getResponse();
        try (var doc = new org.apache.poi.xwpf.usermodel.XWPFDocument(new java.io.ByteArrayInputStream(docx.getContentAsByteArray()));
             var text = new org.apache.poi.xwpf.extractor.XWPFWordExtractor(doc)) {
            assertThat(text.getText()).contains("TABLEAU DE BORD - PDCID II", "Banque mondiale", "Améliorer les services de base",
                    "Nombre de Communes Bénéficiaires", "Les 1 district bénéficiaire de PDCID II",
                    "Indicateurs de produit", "Superficie du bâtiment EPP bâti (m²)", "1 500");
            assertThat(doc.getAllPictures()).hasSize(1);
        }
        // Tableau de bord d'un projet : « PROGRAMME - projet », financement et objectif de son programme.
        var sub = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart("/api/dashboards/programme/prog/subProgramme/sub/export?format=docx").file(map).with(auth).with(csrf()))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(sub.getHeader("Content-Disposition")).contains("tableau-de-bord-projet-PDCID_II-BOENY.docx");
        try (var doc = new org.apache.poi.xwpf.usermodel.XWPFDocument(new java.io.ByteArrayInputStream(sub.getContentAsByteArray()));
             var text = new org.apache.poi.xwpf.extractor.XWPFWordExtractor(doc)) {
            assertThat(text.getText()).contains("TABLEAU DE BORD PROJET : PDCID II - BOENY", "Nom du projet", "Banque mondiale",
                    "Budget total du projet", "du projet PDCID II - BOENY", "Situation du projet");
        }
    }
}
