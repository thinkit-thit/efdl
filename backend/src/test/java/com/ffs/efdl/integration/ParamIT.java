package com.ffs.efdl.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.ffs.efdl.model.UserType;
import com.ffs.efdl.security.EfdlUserPrincipal;
import com.jayway.jsonpath.JsonPath;

/**
 * Écrans « Paramètre » sur la base de TEST jetable {@code efdl_it} (jamais la base réelle).
 * Lancement : {@code mvn test -Defdl.it=true -Dtest=ParamIT}.
 */
@SpringBootTest(properties = "spring.mongodb.database=efdl_it")
@ActiveProfiles("local")
@EnabledIfSystemProperty(named = "efdl.it", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ParamIT {

    static final String SCHOOL = "sdi-fdl";
    static final String E = "com.ffs.kiwi.model.dashboard.election.";

    @Autowired WebApplicationContext context;
    @Autowired MongoTemplate mongo;
    MockMvc mvc;

    static String categorieId, questionId, validateurId, avancementId, indicateurId;

    @BeforeAll
    void seed() {
        assertThat(mongo.getDb().getName()).isEqualTo("efdl_it"); // garde-fou : jamais la base réelle
        assertThat(context.getEnvironment().getProperty("spring.mail.host")).isNull(); // garde-fou : jamais le vrai SMTP
        mongo.getDb().drop();
        insert("school", "com.ffs.kiwi.model.School", SCHOOL, new Document("name", "sdi-fdl").append("domainName", "localhost:8080"));
        insert("Programme", "com.ffs.kiwi.model.project.Programme", "prog-1", new Document("title", "PDCID II").append("order", 0));
        insert("user", "com.ffs.kiwi.model.user.User", "val-user", new Document("username", "valid01").append("lastName", "RAKOTO")
                .append("firstName", "Jean").append("type", "ADMINISTRATION").append("password", "x"));
        insert("Province", E + "Province", "p1", new Document("name", "ANTANANARIVO").append("code", "1"));
        insert("Region", E + "Region", "r1", new Document("name", "ANALAMANGA").append("code", "11").append("provinceId", "p1"));
        insert("District", E + "District", "d1", new Document("name", "AMBOHIDRATRIMO").append("code", "1101").append("provinceId", "p1")
                .append("regionId", "r1").append("status", "TO_CHECK").append("siege", 0).append("nbrBv", 0));
        insert("Commune", E + "Commune", "c1", new Document("name", "AMBATO").append("code", "110101").append("categorie", "CR 2")
                .append("provinceId", "p1").append("regionId", "r1").append("districtId", "d1").append("contactName", "Ancien maire")
                .append("contactNumber2", "0340000002").append("contactPassword", "secret-contact").append("partiePolitique", "X")
                .append("bv", new Document("inscrit", 120).append("votants", 80)).append("data", false));
        insert("Fokontany", E + "Fokontany", "f1", new Document("name", "AMBATO").append("code", "11010101").append("provinceId", "p1")
                .append("regionId", "r1").append("districtId", "d1").append("communeId", "c1").append("contactPassword", "secret-fkt"));
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private void insert(String collection, String className, String externalId, Document fields) {
        Document d = new Document("_id", new ObjectId()).append("className", className);
        d.putAll(fields);
        if (!"school".equals(collection)) d.append("schoolExternalId", SCHOOL).append("orderPlace", 0);
        d.append("externalId", externalId).append("creationDate", new Date()).append("lastUpdateDate", new Date());
        mongo.getCollection(collection).insertOne(d);
    }

    private static RequestPostProcessor as(UserType type, Set<String> groups) {
        EfdlUserPrincipal p = new EfdlUserPrincipal("it-" + type, "it", "IT", type, SCHOOL, groups, null);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(p, null, p.authorities()));
    }

    private String send(MockHttpServletRequestBuilder req, String json, int expected) throws Exception {
        return send(req, json, expected, as(UserType.ADMIN, Set.of()));
    }

    private String send(MockHttpServletRequestBuilder req, String json, int expected, RequestPostProcessor user) throws Exception {
        return mvc.perform(req.with(user).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
    }

    private Document raw(String collection, String externalId) {
        return mongo.getCollection(collection).find(new Document("externalId", externalId)).first();
    }

    @Test @Order(1)
    void libelleSimpleCreationModificationSuppression() throws Exception {
        send(post("/api/params/categories"), "{\"title\":\"  \"}", 400); // obligatoire
        categorieId = JsonPath.read(send(post("/api/params/categories"), "{\"title\":\" Infrastructures \",\"autre\":\"ignoré\"}", 201), "$.externalId");
        Document d = raw("Categorie", categorieId);
        assertThat(d.getString("className")).isEqualTo("com.ffs.kiwi.model.project.Categorie");
        assertThat(d.getString("title")).isEqualTo("Infrastructures");
        assertThat(d.getString("schoolExternalId")).isEqualTo(SCHOOL);
        assertThat(d.containsKey("autre")).isFalse(); // liste blanche
        send(put("/api/params/categories/" + categorieId), "{\"title\":\"Infrastructures publiques\"}", 200);
        assertThat(raw("Categorie", categorieId).getString("title")).isEqualTo("Infrastructures publiques");

        String list = send(get("/api/params/categories"), "", 200);
        assertThat(JsonPath.<List<String>>read(list, "$.rows[*].title")).containsExactly("Infrastructures publiques");
        assertThat((Boolean) JsonPath.read(list, "$.canCreate")).isTrue();
        assertThat(JsonPath.<String>read(list, "$.meta.columns[0].header")).isEqualTo("Nom de la categorie");
        send(get("/api/params/inconnu"), "", 404);
    }

    @Test @Order(2)
    void typesNumeriquesEtEnumerations() throws Exception {
        avancementId = JsonPath.read(send(post("/api/params/avancements"),
                "{\"typeAvancement\":\"PROJET\",\"title\":\"Démarrage\",\"pourcentage\":\"12,5\"}", 201), "$.externalId");
        Document d = raw("Avancement", avancementId);
        assertThat(d.get("rang")).isEqualTo(0); // numérique écrit (Int32)
        assertThat(d.get("pourcentage")).isEqualTo(12.5);
        assertThat(d.getString("typeAvancement")).isEqualTo("PROJET");
        send(post("/api/params/avancements"), "{\"typeAvancement\":\"AUTRE\",\"title\":\"x\"}", 400);
        send(post("/api/params/avancements"), "{\"title\":\"x\",\"rang\":\"1.5\"}", 400);
        questionId = JsonPath.read(send(post("/api/params/questions"), "{\"question\":\"PV de réception ?\",\"typeReponse\":\"OUI_NON\"}", 201), "$.externalId");
    }

    @Test @Order(3)
    void validateurAvecReferences() throws Exception {
        send(post("/api/params/validateurs"), "{\"title\":\"x\",\"programmeExternalId\":\"inconnu\"}", 400);
        validateurId = JsonPath.read(send(post("/api/params/validateurs"), "{\"ordre\":\"2\",\"title\":\"Responsable technique\","
                + "\"programmeExternalId\":\"prog-1\",\"userExternalId\":\"val-user\",\"maxJourTraitement\":5,"
                + "\"questionIds\":[\"" + questionId + "\",\"" + questionId + "\"]}", 201), "$.externalId");
        Document d = raw("Validateur", validateurId);
        assertThat(d.get("ordre")).isEqualTo(2);
        assertThat(d.get("maxJourTraitement")).isEqualTo(5);
        assertThat(d.getList("questionIds", String.class)).containsExactly(questionId);
        String list = send(get("/api/params/validateurs"), "", 200);
        assertThat(JsonPath.<List<String>>read(list, "$.refs.users[*].label")).containsExactly("RAKOTO Jean");
        assertThat(JsonPath.<List<String>>read(list, "$.refs.programmes[*].label")).containsExactly("PDCID II");
        // Check-list vidée → champ retiré (questionIds null).
        send(put("/api/params/validateurs/" + validateurId), "{\"ordre\":2,\"title\":\"Responsable technique\",\"questionIds\":[]}", 200);
        assertThat(raw("Validateur", validateurId).containsKey("questionIds")).isFalse();

        indicateurId = JsonPath.read(send(post("/api/params/indicateurs"), "{\"ordre\":1,\"categorieExternalId\":\"" + categorieId
                + "\",\"indicateurProduit1\":\"Nombre d'écoles\"}", 201), "$.externalId");
        assertThat(raw("Indicateur", indicateurId).getString("categorieExternalId")).isEqualTo(categorieId);
    }

    static String districtId, communeId, fokontanyId;

    @Test @Order(4)
    void localisationCreationEnCascade() throws Exception {
        insert("Region", E + "Region", "r2", new Document("name", "BONGOLAVA").append("code", "12").append("provinceId", "p1"));
        send(get("/api/params/communes/new"), "", 200);
        send(post("/api/params/communes"), "{\"name\":\"x\"}", 400); // district obligatoire

        // Province : premier niveau, code = maximum + 1.
        String provinceId = JsonPath.read(send(post("/api/params/provinces"), "{\"name\":\"NOUVELLE PROVINCE\"}", 201), "$.externalId");
        assertThat(raw("Province", provinceId).getString("code")).isEqualTo("2");

        // District dans la région r1 : province héritée de la région, code 11 + 02, valeurs par défaut.
        districtId = JsonPath.read(send(post("/api/params/districts"), "{\"provinceId\":\"p1\",\"regionId\":\"r1\",\"name\":\"NOUVEAU DISTRICT\"}", 201), "$.externalId");
        Document d = raw("District", districtId);
        assertThat(d.getString("code")).isEqualTo("1102");
        assertThat(d.getString("provinceId")).isEqualTo("p1");
        assertThat(d.getString("status")).isEqualTo("TO_CHECK");
        assertThat(d.get("nbrBv")).isEqualTo(0);
        assertThat(d.getString("className")).isEqualTo(E + "District");

        // Commune : chaîne incohérente refusée (district d'une autre région), code invalide ou déjà pris refusé.
        send(post("/api/params/communes"), "{\"provinceId\":\"p1\",\"regionId\":\"r2\",\"districtId\":\"" + districtId + "\",\"name\":\"X\"}", 400);
        send(post("/api/params/communes"), "{\"districtId\":\"" + districtId + "\",\"code\":\"999999\",\"name\":\"X\"}", 400);
        send(post("/api/params/communes"), "{\"districtId\":\"d1\",\"code\":\"110101\",\"name\":\"X\"}", 409);
        communeId = JsonPath.read(send(post("/api/params/communes"), "{\"provinceId\":\"p1\",\"regionId\":\"r1\",\"districtId\":\""
                + districtId + "\",\"name\":\"NOUVELLE COMMUNE\",\"categorie\":\"CU\"}", 201), "$.externalId");
        Document c = raw("Commune", communeId);
        assertThat(c.getString("code")).isEqualTo("110201");
        assertThat(c.getString("regionId")).isEqualTo("r1");
        assertThat(c.getString("categorie")).isEqualTo("CU");
        assertThat(c.getBoolean("data")).isFalse();

        // Fokontany : les trois ancêtres sont recopiés depuis la commune.
        fokontanyId = JsonPath.read(send(post("/api/params/fokontanies"), "{\"communeId\":\"" + communeId + "\",\"name\":\"NOUVEAU FKT\"}", 201), "$.externalId");
        Document f = raw("Fokontany", fokontanyId);
        assertThat(f.getString("code")).isEqualTo("11020101");
        assertThat(List.of(f.getString("provinceId"), f.getString("regionId"), f.getString("districtId"))).containsExactly("p1", "r1", districtId);

        // Parents et code ne changent plus ensuite.
        send(put("/api/params/communes/" + communeId), "{\"name\":\"COMMUNE RENOMMEE\",\"districtId\":\"d1\",\"code\":\"110199\"}", 200);
        assertThat(raw("Commune", communeId).getString("districtId")).isEqualTo(districtId);
        assertThat(raw("Commune", communeId).getString("code")).isEqualTo("110201");
        System.out.println("IDS-POUR-MORPHIA-LOC district=" + districtId + " commune=" + communeId + " fokontany=" + fokontanyId);
    }

    @Test @Order(5)
    void localisationModificationSansFuite() throws Exception {
        send(put("/api/params/communes/c1"), "{\"name\":\"AMBATO I\",\"code\":\"999\",\"districtId\":\"autre\",\"contactName\":\"Nouveau maire\"}", 200);
        Document c = raw("Commune", "c1");
        assertThat(c.getString("name")).isEqualTo("AMBATO I");
        assertThat(c.getString("code")).isEqualTo("110101"); // lecture seule
        assertThat(c.getString("districtId")).isEqualTo("d1");
        assertThat(c.getString("contactPassword")).isEqualTo("secret-contact"); // jamais touché
        assertThat(c.getString("contactNumber2")).isEqualTo("0340000002"); // hors écran : conservé
        assertThat(c.getString("partiePolitique")).isEqualTo("X");
        assertThat(((Document) c.get("bv")).getInteger("inscrit")).isEqualTo(120);

        String list = send(get("/api/params/communes"), "", 200);
        assertThat(list).doesNotContain("secret-contact").doesNotContain("contactPassword").doesNotContain("inscrit");
        assertThat((Boolean) JsonPath.read(list, "$.canCreate")).isTrue();
        assertThat(JsonPath.<List<String>>read(list, "$.refs.districts[*].parent")).containsOnly("r1");
        assertThat(send(get("/api/params/fokontanies/f1"), "", 200)).doesNotContain("secret-fkt");
    }

    @Test @Order(6)
    void suppressionsLogiques() throws Exception {
        send(delete("/api/params/districts/d1"), "", 409); // la commune c1 est rattachée
        send(delete("/api/params/communes/c1"), "", 409); // le fokontany f1 est rattaché
        send(delete("/api/params/fokontanies/f1"), "", 204);
        send(delete("/api/params/communes/c1"), "", 204);
        send(delete("/api/params/districts/d1"), "", 204);
        assertThat(raw("District", "d1").getString("status")).isEqualTo("DELETED");
        assertThat(raw("Commune", "c1").getString("name")).isEqualTo("AMBATO I"); // document conservé
        send(get("/api/params/communes/c1"), "", 404);
        send(put("/api/params/communes/c1"), "{\"name\":\"x\"}", 404);

        send(delete("/api/params/categories/" + categorieId), "", 204);
        assertThat(JsonPath.<List<Object>>read(send(get("/api/params/categories"), "", 200), "$.rows")).isEmpty();
        // L'indicateur garde son libellé (référence marquée supprimée) mais on ne peut plus la choisir.
        String indicateurs = send(get("/api/params/indicateurs"), "", 200);
        assertThat(JsonPath.<List<Boolean>>read(indicateurs, "$.refs.categories[*].deleted")).containsExactly(true);
        send(put("/api/params/indicateurs/" + indicateurId), "{\"ordre\":1,\"categorieExternalId\":\"" + categorieId + "\"}", 400);
    }

    @Test @Order(7)
    void droitsParRouteHistorique() throws Exception {
        mongo.getCollection("user_group").insertOne(new Document("_id", new ObjectId())
                .append("className", "com.ffs.kiwi.model.user.group.UserGroup").append("name", "Param projets")
                .append("allowedPaths", List.of("/admin/project/parametre/secteurs", "/admin/project/parametre/secteur/edit/.*"))
                .append("schoolExternalId", SCHOOL).append("orderPlace", 0).append("externalId", "g-param"));
        RequestPostProcessor limited = as(UserType.ADMINISTRATION, Set.of("g-param"));
        String list = send(get("/api/params/secteurs"), "", 200, limited);
        assertThat((Boolean) JsonPath.read(list, "$.canEdit")).isTrue();
        assertThat((Boolean) JsonPath.read(list, "$.canDelete")).isFalse();
        send(post("/api/params/secteurs"), "{\"title\":\"Santé\"}", 201, limited);
        String secteurId = JsonPath.read(send(get("/api/params/secteurs"), "", 200, limited), "$.rows[0].externalId");
        send(delete("/api/params/secteurs/" + secteurId), "", 403, limited);
        send(get("/api/params/validateurs"), "", 403, limited);
        send(get("/api/params/provinces"), "", 403, limited);
        send(get("/api/params/secteurs"), "", 403, as(UserType.CANDIDAT, Set.of("g-param")));

        System.out.println("IDS-POUR-MORPHIA-PARAM categorie=" + categorieId + " avancement=" + avancementId + " question=" + questionId
                + " validateur=" + validateurId + " indicateur=" + indicateurId);
        assertThat(Map.of("v", validateurId, "i", indicateurId)).doesNotContainValue(null);
    }

    @Test @Order(8)
    void ptaFinancementActivitesSousActivites() throws Exception {
        // Financement : dates (minuit, fuseau du serveur), montant décimal, statut.
        send(post("/api/params/ptaFinancements"), "{\"title\":\"x\",\"dateDemarrage\":\"31/12/2026\"}", 400);
        String financement = JsonPath.read(send(post("/api/params/ptaFinancements"), "{\"numero\":\"P1\",\"title\":\"Appui communes\","
                + "\"dateDemarrage\":\"2026-01-15\",\"dateFin\":\"2026-12-31\",\"montant\":\"150000000\",\"devise\":\"MGA\","
                + "\"statutAvancement\":\"EN_COURS\"}", 201), "$.externalId");
        Document f = raw("PtaFinancement", financement);
        assertThat(f.get("dateDemarrage")).isEqualTo(java.util.Date.from(java.time.LocalDate.of(2026, 1, 15)
                .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()));
        assertThat(f.get("montant")).isEqualTo(1.5E8);
        assertThat(f.getString("className")).isEqualTo("com.ffs.kiwi.model.project.PtaFinancement");

        // Activité : le financement vient de l'URL (paramètre), obligatoire et vérifié.
        send(post("/api/params/ptaActivites"), "{\"title\":\"A\"}", 400);
        send(post("/api/params/ptaActivites?ptaFinancementId=inconnu"), "{\"title\":\"A\"}", 404);
        String activite = JsonPath.read(send(post("/api/params/ptaActivites?ptaFinancementId=" + financement),
                "{\"numero\":\"A1\",\"title\":\"Formation\",\"ptaFinancementExternalId\":\"ignoré\"}", 201), "$.externalId");
        assertThat(raw("PtaActivite", activite).getString("ptaFinancementExternalId")).isEqualTo(financement);
        String list = send(get("/api/params/ptaActivites?ptaFinancementId=" + financement), "", 200);
        assertThat(JsonPath.<List<String>>read(list, "$.parents[*].label")).containsExactly("Appui communes");
        assertThat(JsonPath.<List<String>>read(list, "$.rows[*].title")).containsExactly("Formation");

        // Sous-activité : deux parents d'URL cohérents, et région → district → communes cohérents.
        String scope = "?ptaFinancementId=" + financement + "&ptaActiviteId=" + activite;
        send(get("/api/params/ptaSousActivites?ptaFinancementId=autre&ptaActiviteId=" + activite), "", 404);
        send(post("/api/params/ptaSousActivites" + scope), "{\"title\":\"S\",\"regionId\":\"r2\",\"districtId\":\"" + districtId + "\"}", 400);
        send(post("/api/params/ptaSousActivites" + scope), "{\"title\":\"S\",\"districtId\":\"" + districtId + "\",\"communeIds\":[\"c1\"]}", 400);
        String sousActivite = JsonPath.read(send(post("/api/params/ptaSousActivites" + scope), "{\"numero\":\"S1\",\"title\":\"Atelier\","
                + "\"regionId\":\"r1\",\"districtId\":\"" + districtId + "\",\"communeIds\":[\"" + communeId + "\"],\"dateDebut\":\"2026-03-01\"}", 201), "$.externalId");
        Document s = raw("PtaSousActivite", sousActivite);
        assertThat(s.getString("ptaActiviteExternalId")).isEqualTo(activite);
        assertThat(s.getString("ptaFinancementExternalId")).isEqualTo(financement);
        assertThat(s.getList("communeIds", String.class)).containsExactly(communeId);

        // Une modification ne déplace jamais l'élément ; une autre activité ne le voit pas.
        send(put("/api/params/ptaSousActivites/" + sousActivite + "?ptaFinancementId=" + financement + "&ptaActiviteId=autre"), "{\"title\":\"X\"}", 404);
        send(put("/api/params/ptaSousActivites/" + sousActivite + scope), "{\"numero\":\"S1\",\"title\":\"Atelier régional\",\"regionId\":\"r1\",\"communeIds\":[]}", 200);
        assertThat(raw("PtaSousActivite", sousActivite).getString("title")).isEqualTo("Atelier régional");
        assertThat(raw("PtaSousActivite", sousActivite).containsKey("communeIds")).isFalse(); // champ vidé à l'écran

        // Suppressions : pas tant qu'il reste des enfants.
        send(delete("/api/params/ptaFinancements/" + financement), "", 409);
        send(delete("/api/params/ptaActivites/" + activite + "?ptaFinancementId=" + financement), "", 409);
        send(delete("/api/params/ptaSousActivites/" + sousActivite + scope), "", 204);
        send(delete("/api/params/ptaActivites/" + activite + "?ptaFinancementId=" + financement), "", 204);
        send(delete("/api/params/ptaFinancements/" + financement), "", 204);
        System.out.println("IDS-POUR-MORPHIA-PTA financement=" + financement + " activite=" + activite + " sousActivite=" + sousActivite);
    }

    @Test @Order(9)
    void programmesSousProgrammesPrestataires() throws Exception {
        // Programme : champs numériques absents du formulaire écrits, liens vers les écrans liés.
        String programme = JsonPath.read(send(post("/api/params/programmes"), "{\"title\":\"PDCID III\",\"montantDescription\":\"12000000\","
                + "\"color\":\"blue\",\"montant\":999}", 201), "$.externalId");
        Document p = raw("Programme", programme);
        assertThat(p.get("montant")).isEqualTo(0.0); // pas un champ de l'écran
        assertThat(p.get("nbrValidateur")).isEqualTo(0);
        assertThat(p.getString("montantDescription")).isEqualTo("12000000");
        String list = send(get("/api/params/programmes"), "", 200);
        assertThat(JsonPath.<List<String>>read(list, "$.meta.children[*].label"))
                .containsExactly("Projets", "Validations Paiement", "Tableau de bord", "Suivi Avcmnt");

        // Sous-programme : rattaché au programme de l'URL.
        String sub = JsonPath.read(send(post("/api/params/subProgrammes?programmeId=" + programme),
                "{\"title\":\"Région BOENY\",\"montant\":\"2500000.5\",\"avancement\":\"40\"}", 201), "$.externalId");
        Document s = raw("SubProgramme", sub);
        assertThat(s.getString("programmeExternalId")).isEqualTo(programme);
        assertThat(s.get("montant")).isEqualTo(2500000.5);
        assertThat(s.get("order")).isEqualTo(0);
        send(delete("/api/params/programmes/" + programme), "", 409); // sous-programme rattaché

        // Prestataires : numéro = dernier + 1 à la création, jamais renuméroté ensuite.
        String p1 = JsonPath.read(send(post("/api/params/prestataires"), "{\"name\":\"ENTREPRISE A\",\"number\":77}", 201), "$.externalId");
        String p2 = JsonPath.read(send(post("/api/params/prestataires"), "{\"name\":\"ENTREPRISE B\",\"nif\":\"123\"}", 201), "$.externalId");
        assertThat(raw("provider", p1).get("number")).isEqualTo(1);
        assertThat(raw("provider", p2).get("number")).isEqualTo(2);
        assertThat(raw("provider", p2).getString("className")).isEqualTo("com.ffs.kiwi.model.dashboard.Provider");
        send(put("/api/params/prestataires/" + p1), "{\"name\":\"ENTREPRISE A SARL\",\"number\":50}", 200);
        assertThat(raw("provider", p1).get("number")).isEqualTo(1);
        assertThat(raw("provider", p1).getString("name")).isEqualTo("ENTREPRISE A SARL");
        System.out.println("IDS-POUR-MORPHIA-PROG programme=" + programme + " sub=" + sub + " provider=" + p1);
    }

    @Test @Order(10)
    void sousProjetsFicheEnEtapes() throws Exception {
        String programme = JsonPath.read(send(post("/api/params/programmes"), "{\"title\":\"FNP\"}", 201), "$.externalId");
        String sub = JsonPath.read(send(post("/api/params/subProgrammes?programmeId=" + programme), "{\"title\":\"FNP 2026\"}", 201), "$.externalId");
        String infra = JsonPath.read(send(post("/api/params/infrastructures"), "{\"title\":\"École\"}", 201), "$.externalId");
        String indicateur = JsonPath.read(send(post("/api/params/indicateurs"), "{\"ordre\":4,\"infrastructureExternalId\":\"" + infra
                + "\",\"indicateurProduit1\":\"Salles construites\"}", 201), "$.externalId");
        String scope = "?programmeId=" + programme + "&subProgrammeId=" + sub;

        // Présentation de la fiche : grille, titres, textes de l'indicateur pour les libellés produit / effet.
        String form = send(get("/api/params/projects/new" + scope), "", 200);
        assertThat(JsonPath.<String>read(form, "$.meta.form.layout")).isEqualTo("grid");
        assertThat(JsonPath.<String>read(form, "$.meta.form.newTitle")).isEqualTo("NOUVEAU SOUS PROJET");
        assertThat(JsonPath.<List<String>>read(form, "$.refs.indicateursKpi[*].attributes.indicateurProduit1")).contains("Salles construites");
        assertThat(JsonPath.<List<String>>read(form, "$.meta.fields[?(@.name=='indicateurProduit1')].ui.labelFrom"))
                .containsExactly("indicateurExternalId.indicateurProduit1");

        // Étape 1 (création) : commune puis fokontany ; région/district recopiés depuis la commune
        // (non saisis), indicateur depuis l'infrastructure.
        send(post("/api/params/projects" + scope), "{\"title\":\"EPP\",\"communeId\":\"inconnue\"}", 400);
        send(post("/api/params/projects" + scope), "{\"title\":\"EPP\",\"communeId\":\"" + communeId + "\",\"fokontanyId\":\"f-autre\"}", 400);
        mongo.getCollection("Fokontany").insertOne(new Document("_id", new ObjectId()).append("className", E + "Fokontany")
                .append("name", "AUTRE FKT").append("communeId", "c-autre").append("schoolExternalId", SCHOOL).append("externalId", "f-autre"));
        send(post("/api/params/projects" + scope), "{\"title\":\"EPP\",\"communeId\":\"" + communeId + "\",\"fokontanyId\":\"f-autre\"}", 400);
        send(post("/api/params/projects" + scope), "{\"title\":\"EPP\",\"regionId\":\"r2\",\"communeId\":\"inconnue\"}", 400);
        String project = JsonPath.read(send(post("/api/params/projects" + scope), "{\"numero\":\"FNP-01\",\"title\":\"EPP Ambato\","
                + "\"communeId\":\"" + communeId + "\",\"infrastructureExternalId\":\"" + infra + "\",\"montantPrevBE\":\"1000\","
                + "\"montantPrevEse\":2500.5,\"filtrationEnvironnementaleSociale\":true,\"dateSignatureConvention\":\"2026-02-01\"}", 201), "$.externalId");
        Document p = raw("Project", project);
        assertThat(p.getString("programmeExternalId")).isEqualTo(programme);
        assertThat(p.getString("subProgrammeExternalId")).isEqualTo(sub);
        assertThat(p.getString("regionId")).isEqualTo("r1");
        assertThat(p.getString("districtId")).isEqualTo(districtId);
        assertThat(p.getString("indicateurExternalId")).isEqualTo(indicateur);
        assertThat(p.getBoolean("filtrationEnvironnementaleSociale")).isTrue();
        assertThat(p.getBoolean("apsValide")).isFalse(); // booléen écrit
        assertThat(p.get("delaiExecution")).isEqualTo(0);
        assertThat(p.get("montantPaye")).isEqualTo(0.0);

        // Étape 3 : seuls ses champs changent, le reste de la fiche est conservé.
        send(put("/api/params/projects/" + project + scope), "{\"apsValide\":true,\"avancementControleSurveillance\":\"50 %\"}", 200);
        p = raw("Project", project);
        assertThat(p.getBoolean("apsValide")).isTrue();
        assertThat(p.getString("title")).isEqualTo("EPP Ambato");
        assertThat(p.get("montantPrevBE")).isEqualTo(1000.0);
        assertThat(p.getString("indicateurExternalId")).isEqualTo(indicateur);

        // Les trois listes le montrent ; la globale et la régionale ne créent pas.
        assertThat(JsonPath.<List<String>>read(send(get("/api/params/projects" + scope), "", 200), "$.rows[*].externalId")).containsExactly(project);
        assertThat(JsonPath.<List<String>>read(send(get("/api/params/allProjects"), "", 200), "$.rows[*].externalId")).contains(project);
        assertThat(JsonPath.<List<String>>read(send(get("/api/params/regionProjects?regionId=r1"), "", 200), "$.rows[*].externalId")).containsExactly(project);
        send(post("/api/params/allProjects"), "{\"title\":\"x\"}", 400);
        send(put("/api/params/regionProjects/" + project + "?regionId=r2"), "{\"title\":\"x\"}", 404);

        // Suppression refusée tant qu'un contrat le référence (pas de suppression en masse « deleteAllXyz »).
        mongo.getCollection("Contrat").insertOne(new Document("_id", new ObjectId()).append("className", "com.ffs.kiwi.model.project.Contrat")
                .append("projectExternalId", project).append("schoolExternalId", SCHOOL).append("externalId", "contrat-1"));
        send(delete("/api/params/projects/" + project + scope), "", 409);
        send(delete("/api/params/projects/deleteAllXyz" + scope), "", 404);
        System.out.println("IDS-POUR-MORPHIA-PROJECT project=" + project);
    }

    @Test @Order(11)
    void contratsPaiementsContratsMultiples() throws Exception {
        String project = mongo.getCollection("Project").find(new Document("numero", "FNP-01")).first().getString("externalId");
        String provider = JsonPath.read(send(post("/api/params/prestataires"), "{\"name\":\"BTP SUD\"}", 201), "$.externalId");
        String autre = JsonPath.read(send(post("/api/params/prestataires"), "{\"name\":\"AUTRE\"}", 201), "$.externalId");

        // Contrat simple : programme/sous-programme depuis le sous-projet, jumeau GroupeContrat de même identifiant.
        String contrat = JsonPath.read(send(post("/api/params/contrats"), "{\"projectExternalId\":\"" + project + "\",\"title\":\"C-001\","
                + "\"typePrestataire\":\"ENTREPRISE\",\"providerExternalId\":\"" + provider + "\",\"montantContrat\":1000000,"
                + "\"dateContrat\":\"2026-03-10\",\"delaiContrat\":6}", 201), "$.externalId");
        Document c = raw("Contrat", contrat);
        assertThat(c.getString("groupeContratExternalId")).isEqualTo(contrat);
        assertThat(c.getString("subProgrammeExternalId")).isNotNull();
        Document twin = raw("GroupeContrat", contrat);
        assertThat(twin.getBoolean("multiple")).isFalse();
        assertThat(twin.getString("title")).isEqualTo("C-001");
        assertThat(twin.get("montantPaye")).isEqualTo(0.0);
        assertThat(twin.getString("className")).isEqualTo("com.ffs.kiwi.model.project.GroupeContrat");
        send(put("/api/params/contrats/" + contrat), "{\"title\":\"C-001B\",\"montantContrat\":1200000}", 200);
        assertThat(raw("GroupeContrat", contrat).getString("title")).isEqualTo("C-001B");
        assertThat(raw("GroupeContrat", contrat).get("montantContrat")).isEqualTo(1200000.0);

        // Facture : contrat du prestataire choisi, statut PENDING, type repris du contrat.
        send(post("/api/params/paiements"), "{\"providerExternalId\":\"" + autre + "\",\"groupeContratExternalId\":\"" + contrat
                + "\",\"numeroFacture\":\"F1\"}", 400);
        String facture = JsonPath.read(send(post("/api/params/paiements"), "{\"providerExternalId\":\"" + provider
                + "\",\"groupeContratExternalId\":\"" + contrat + "\",\"numeroFacture\":\"F1\",\"montant\":300000}", 201), "$.externalId");
        Document f = raw("Paiement", facture);
        assertThat(f.getString("status")).isEqualTo("PENDING");
        assertThat(f.get("niveauValidation")).isEqualTo(0);
        assertThat(f.getString("typePrestataire")).isEqualTo("ENTREPRISE");

        // Validée : comptée dans le montant payé, plus modifiable ni supprimable.
        mongo.getCollection("Paiement").updateOne(new Document("externalId", facture), new Document("$set", new Document("status", "VALIDATE")));
        String contrats = send(get("/api/params/contrats"), "", 200);
        assertThat(JsonPath.<List<Double>>read(contrats, "$.rows[?(@.externalId=='" + contrat + "')].montantPayeValide")).containsExactly(300000.0);
        String detail = send(get("/api/params/paiements/" + facture), "", 200);
        assertThat((Double) JsonPath.read(detail, "$.row.montantContratGroupe")).isEqualTo(1200000.0);
        send(put("/api/params/paiements/" + facture), "{\"montant\":1}", 409);
        send(delete("/api/params/paiements/" + facture), "", 409);
        send(delete("/api/params/contrats/" + contrat), "", 409); // facture rattachée
        send(delete("/api/params/paiements/deleteAllXyz"), "", 404);

        // Contrat multiple : seuls les multiples sont listés ; ses lots héritent titre, type, prestataire et dates.
        String groupe = JsonPath.read(send(post("/api/params/groupeContrats"), "{\"providerExternalId\":\"" + provider
                + "\",\"title\":\"CM-01\",\"typePrestataire\":\"BE\",\"montantContrat\":5000000,\"dateContrat\":\"2026-04-01\"}", 201), "$.externalId");
        assertThat(raw("GroupeContrat", groupe).getBoolean("multiple")).isTrue();
        // Montant d'un contrat multiple non saisi : somme de ses lots (0 sans lot).
        assertThat(((Number) raw("GroupeContrat", groupe).get("montantContrat")).doubleValue()).isZero();
        assertThat(JsonPath.<List<String>>read(send(get("/api/params/groupeContrats"), "", 200), "$.rows[*].externalId")).containsExactly(groupe);
        // Nouveau lot : fiche préremplie avec le n°, le prestataire, le type et les dates du contrat multiple.
        String blank = send(get("/api/params/groupeContratLots/new?groupeContratId=" + groupe), "", 200);
        assertThat((String) JsonPath.read(blank, "$.row.title")).isEqualTo("CM-01");
        assertThat((String) JsonPath.read(blank, "$.row.providerExternalId")).isEqualTo(provider);
        assertThat((String) JsonPath.read(blank, "$.row.typePrestataire")).isEqualTo("BE");
        assertThat((Object) JsonPath.read(blank, "$.row.dateContrat")).isNotNull();
        // Sans parent dans l'URL, la fiche de création reste vide.
        assertThat((Object) JsonPath.read(send(get("/api/params/groupeContrats/new"), "", 200), "$.row")).isNull();
        String lot = JsonPath.read(send(post("/api/params/groupeContratLots?groupeContratId=" + groupe), "{\"lot\":\"Lot 1\","
                + "\"projectExternalId\":\"" + project + "\",\"montantContrat\":2000000,\"title\":\"ignoré\"}", 201), "$.externalId");
        assertThat(((Number) raw("GroupeContrat", groupe).get("montantContrat")).doubleValue()).isEqualTo(2000000.0);
        String lot2 = JsonPath.read(send(post("/api/params/groupeContratLots?groupeContratId=" + groupe), "{\"lot\":\"Lot 2\","
                + "\"projectExternalId\":\"" + project + "\",\"montantContrat\":500000}", 201), "$.externalId");
        assertThat(((Number) raw("GroupeContrat", groupe).get("montantContrat")).doubleValue()).isEqualTo(2500000.0);
        send(put("/api/params/groupeContratLots/" + lot2 + "?groupeContratId=" + groupe), "{\"montantContrat\":700000}", 200);
        assertThat(((Number) raw("GroupeContrat", groupe).get("montantContrat")).doubleValue()).isEqualTo(2700000.0);
        send(delete("/api/params/groupeContratLots/" + lot2 + "?groupeContratId=" + groupe), "", 204);
        assertThat(((Number) raw("GroupeContrat", groupe).get("montantContrat")).doubleValue()).isEqualTo(2000000.0);
        Document l = raw("Contrat", lot);
        assertThat(l.getString("title")).isEqualTo("CM-01");
        assertThat(l.getString("providerExternalId")).isEqualTo(provider);
        assertThat(l.getString("typePrestataire")).isEqualTo("BE");
        assertThat(l.getString("groupeContratExternalId")).isEqualTo(groupe);
        assertThat(l.get("dateContrat")).isNotNull();
        assertThat(raw("GroupeContrat", lot)).isNull(); // pas de jumeau pour un lot
        // Modifié depuis l'écran « Contrats », le lot reste dans son contrat multiple.
        send(put("/api/params/contrats/" + lot), "{\"observation\":\"révisé\",\"montantContrat\":2100000}", 200);
        assertThat(raw("Contrat", lot).getString("groupeContratExternalId")).isEqualTo(groupe);
        assertThat(raw("GroupeContrat", lot)).isNull();
        assertThat(((Number) raw("GroupeContrat", groupe).get("montantContrat")).doubleValue()).isEqualTo(2100000.0);
        assertThat(JsonPath.<List<String>>read(send(get("/api/params/groupeContratLots?groupeContratId=" + groupe), "", 200),
                "$.rows[*].lot")).containsExactly("Lot 1");

        send(delete("/api/params/groupeContrats/" + groupe), "", 409); // lot rattaché
        send(delete("/api/params/groupeContratLots/" + lot + "?groupeContratId=" + groupe), "", 204);
        assertThat(((Number) raw("GroupeContrat", groupe).get("montantContrat")).doubleValue()).isZero();
        send(delete("/api/params/groupeContrats/" + groupe), "", 204);
        System.out.println("IDS-POUR-MORPHIA-CONTRAT contrat=" + contrat + " facture=" + facture + " groupe=" + groupe + " lot=" + lot);
    }

    @Test @Order(12)
    void tableauDeBordDesLocalisations() throws Exception {
        // Comptes = éléments actifs de l'école (supprimés exclus), un élément supprimé et un d'une autre école ignorés.
        insert("Province", "com.ffs.kiwi.model.dashboard.election.Province", "p-supprimee",
                new Document("name", "SUPPRIMEE").append("status", "DELETED"));
        mongo.getCollection("Province").insertOne(new Document("_id", new ObjectId()).append("name", "AUTRE ECOLE")
                .append("schoolExternalId", "autre-ecole").append("externalId", "p-autre"));
        String json = send(get("/api/dashboards/localisation"), "", 200);
        for (String[] c : new String[][] { { "provinces", "Province" }, { "regions", "Region" }, { "districts", "District" },
                { "communes", "Commune" }, { "fokontany", "Fokontany" } }) {
            long expected = mongo.getCollection(c[1]).countDocuments(new Document("schoolExternalId", SCHOOL)
                    .append("status", new Document("$ne", "DELETED")));
            assertThat(((Number) JsonPath.read(json, "$." + c[0])).longValue()).as(c[0]).isEqualTo(expected);
        }
        assertThat(((Number) JsonPath.read(json, "$.provinces")).longValue()).isPositive();
        // Droits : route /admin/dashboard/election/tbs, refusée à un utilisateur sans groupe.
        send(get("/api/dashboards/localisation"), "", 403, as(UserType.ADMINISTRATION, Set.of()));

        // Par district : population = somme des fokontany actifs ; tri par communes bénéficiaires décroissantes.
        Document district = mongo.getCollection("District").find(new Document("schoolExternalId", SCHOOL)
                .append("status", new Document("$ne", "DELETED"))).first();
        String districtId = district.getString("externalId");
        insert("Fokontany", E + "Fokontany", "fkt-pop-1", new Document("name", "POP 1").append("districtId", districtId)
                .append("population", 1000).append("population18", 400));
        insert("Fokontany", E + "Fokontany", "fkt-pop-2", new Document("name", "POP 2").append("districtId", districtId)
                .append("population", 500).append("population18", 100));
        insert("Fokontany", E + "Fokontany", "fkt-pop-3", new Document("name", "POP 3 SUPPRIME").append("districtId", districtId)
                .append("population", 99999).append("status", "DELETED"));
        String rows = send(get("/api/dashboards/localisation/districts"), "", 200);
        List<Map<String, Object>> list = JsonPath.read(rows, "$");
        Map<String, Object> row = list.stream().filter(r -> district.get("code").toString().equals(r.get("code"))).findFirst().orElseThrow();
        long pop = mongo.getCollection("Fokontany").aggregate(List.of(new Document("$match", new Document("districtId", districtId)
                .append("status", new Document("$ne", "DELETED"))), new Document("$group", new Document("_id", null)
                .append("s", new Document("$sum", "$population"))))).first().get("s", Number.class).longValue();
        assertThat(((Number) row.get("population")).longValue()).isEqualTo(pop).isGreaterThanOrEqualTo(1500);
        for (int i = 1; i < list.size(); i++) {
            assertThat(((Number) list.get(i - 1).get("communes")).longValue())
                    .isGreaterThanOrEqualTo(((Number) list.get(i).get("communes")).longValue());
        }
    }
}
