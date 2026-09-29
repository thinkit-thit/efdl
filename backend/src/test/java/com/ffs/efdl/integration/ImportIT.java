package com.ffs.efdl.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.ffs.efdl.model.UserType;
import com.ffs.efdl.security.EfdlUserPrincipal;
import com.jayway.jsonpath.JsonPath;

/**
 * Imports Excel (prestataires, factures, fiche de suivi des sous-projets), sur la base de TEST jetable
 * {@code efdl_it}. Les classeurs sont fabriqués avec les en-têtes des modèles réels.
 * Lancement : {@code mvn test -Defdl.it=true -Dtest=ImportIT}.
 */
@SpringBootTest(properties = "spring.mongodb.database=efdl_it")
@ActiveProfiles("local")
@EnabledIfSystemProperty(named = "efdl.it", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ImportIT {

    static final String SCHOOL = "sdi-fdl";
    static final String P = "com.ffs.kiwi.model.project.";
    static final String E = "com.ffs.kiwi.model.dashboard.election.";

    @Autowired WebApplicationContext context;
    @Autowired MongoTemplate mongo;
    MockMvc mvc;

    @BeforeAll
    void seed() {
        assertThat(mongo.getDb().getName()).isEqualTo("efdl_it"); // garde-fou : jamais la base réelle
        assertThat(context.getEnvironment().getProperty("spring.mail.host")).isNull(); // garde-fou : jamais le vrai SMTP
        mongo.getDb().drop();
        insert("school", "com.ffs.kiwi.model.School", SCHOOL, new Document("name", "sdi-fdl"), false);
        insert("Programme", P + "Programme", "prog", new Document("title", "FNP"), true);
        insert("SubProgramme", P + "SubProgramme", "sub", new Document("title", "FNP 2025").append("programmeExternalId", "prog"), true);
        insert("SubProgramme", P + "SubProgramme", "sub2", new Document("title", "FNP 2026").append("programmeExternalId", "prog"), true);
        insert("Region", E + "Region", "r1", new Document("name", "BOENY").append("code", "41"), true);
        insert("District", E + "District", "d1", new Document("name", "MAJUNGA I").append("code", "4101").append("regionId", "r1"), true);
        insert("Commune", E + "Commune", "c1", new Document("name", "MAHAJANGA").append("code", "410101").append("regionId", "r1")
                .append("districtId", "d1"), true);
        insert("Fokontany", E + "Fokontany", "f1", new Document("name", "TSARAMANDROSO").append("communeId", "c1")
                .append("districtId", "d1").append("regionId", "r1"), true);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private void insert(String collection, String className, String id, Document fields, boolean schoolScoped) {
        Document d = new Document("_id", new ObjectId()).append("className", className);
        d.putAll(fields);
        if (schoolScoped) d.append("schoolExternalId", SCHOOL).append("orderPlace", 0);
        d.append("externalId", id).append("creationDate", new Date()).append("lastUpdateDate", new Date());
        mongo.getCollection(collection).insertOne(d);
    }

    private static RequestPostProcessor admin() {
        EfdlUserPrincipal p = new EfdlUserPrincipal("it-admin", "admin", "IT", UserType.ADMIN, SCHOOL, Set.of(), null);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(p, null, p.authorities()));
    }

    /** Classeur : en-têtes (index → libellé) et lignes (index → valeur ; Date = cellule date Excel). */
    private static byte[] xlsx(Map<Integer, String> headers, List<Map<Integer, Object>> rows) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Sheet1");
            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy"));
            Row h = sheet.createRow(0);
            headers.forEach((i, v) -> h.createCell(i).setCellValue(v));
            int n = 1;
            for (Map<Integer, Object> values : rows) {
                Row r = sheet.createRow(n++);
                values.forEach((i, v) -> {
                    if (v instanceof Number num) r.createCell(i).setCellValue(num.doubleValue());
                    else if (v instanceof Date d) {
                        r.createCell(i).setCellValue(d);
                        r.getCell(i).setCellStyle(dateStyle);
                    } else r.createCell(i).setCellValue(String.valueOf(v));
                });
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    private String upload(String url, byte[] content, boolean apply, String extra, int expected) throws Exception {
        return upload(url, content, apply, extra, expected, admin());
    }

    private String upload(String url, byte[] content, boolean apply, String extra, int expected, RequestPostProcessor as)
            throws Exception {
        var req = multipart(url + "?apply=" + apply + (extra == null ? "" : "&" + extra))
                .file(new MockMultipartFile("file", "import.xlsx", MediaType.APPLICATION_OCTET_STREAM_VALUE, content));
        return mvc.perform(req.with(as).with(csrf())).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
    }

    private long count(String collection, Document filter) {
        return mongo.getCollection(collection).countDocuments(filter.append("schoolExternalId", SCHOOL));
    }

    @Test @Order(1)
    void prestataires() throws Exception {
        Map<Integer, String> headers = Map.of(0, "N°", 1, "Dénomination", 2, "NIF", 3, "STAT", 4, "Adresse", 5, "RCS", 6, "Contact");
        byte[] file = xlsx(headers, List.of(Map.of(0, 10, 1, "BTP SUD", 2, "NIF-1", 6, "034 00"), Map.of(0, 11, 1, "BE CONSEIL"),
                Map.of(0, "abc", 1, "X")));
        String dry = upload("/api/imports/prestataires", file, false, null, 200);
        assertThat((Integer) JsonPath.read(dry, "$.creations")).isEqualTo(2);
        assertThat((Integer) JsonPath.read(dry, "$.erreurs")).isEqualTo(1);
        assertThat(count("provider", new Document())).isZero(); // analyse : rien d'écrit

        upload("/api/imports/prestataires", file, true, null, 200);
        Document btp = mongo.getCollection("provider").find(new Document("name", "BTP SUD")).first();
        assertThat(btp.get("number")).isEqualTo(10);
        assertThat(btp.getString("className")).isEqualTo("com.ffs.kiwi.model.dashboard.Provider");
        byte[] update = xlsx(headers, List.of(Map.of(0, 10, 1, "BTP SUD SARL")));
        assertThat((Integer) JsonPath.read(upload("/api/imports/prestataires", update, true, null, 200), "$.misesAJour")).isEqualTo(1);
        assertThat(mongo.getCollection("provider").find(new Document("number", 10)).first().getString("name")).isEqualTo("BTP SUD SARL");
        assertThat(upload("/api/imports/prestataires", "pas un excel".getBytes(), false, null, 400)).contains("illisible");
    }

    @Test @Order(2)
    void factures() throws Exception {
        String provider = mongo.getCollection("provider").find(new Document("number", 10)).first().getString("externalId");
        EfdlUserPrincipal p = new EfdlUserPrincipal("it-admin", "admin", "IT", UserType.ADMIN, SCHOOL, Set.of(), null);
        mvc.perform(post("/api/params/contrats").with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"C-100\",\"typePrestataire\":\"ENTREPRISE\",\"providerExternalId\":\"" + provider + "\"}"))
                .andExpect(status().isCreated());
        Map<Integer, String> headers = Map.of(0, "N° contrat", 1, "N° facture", 2, "Intitulé", 3, "N° attachement", 4, "Intitulé attachement", 5, "Montant");
        byte[] file = xlsx(headers, List.of(Map.of(0, "C-100", 1, "F-1", 2, "Décompte 1", 5, 250000), Map.of(0, "C-999", 1, "F-2")));
        String dry = upload("/api/imports/paiements", file, false, null, 200);
        assertThat((Integer) JsonPath.read(dry, "$.creations")).isEqualTo(1);
        assertThat(JsonPath.<List<String>>read(dry, "$.details[?(@.action=='ERREUR')].messages[0]")).containsExactly("contrat « C-999 » introuvable");

        upload("/api/imports/paiements", file, true, "historique=true", 200);
        Document f = mongo.getCollection("Paiement").find(new Document("numeroFacture", "F-1")).first();
        assertThat(f.getString("status")).isEqualTo("VALIDATE");
        assertThat(f.get("montant")).isEqualTo(250000.0);
        assertThat(f.getString("typePrestataire")).isEqualTo("ENTREPRISE");
        // Réimport : pas de doublon ; hors historique : dans le circuit de validation.
        assertThat((Integer) JsonPath.read(upload("/api/imports/paiements", file, true, null, 200), "$.ignorees")).isEqualTo(1);
        byte[] nouvelle = xlsx(headers, List.of(Map.of(0, "C-100", 1, "F-3", 5, 10)));
        upload("/api/imports/paiements", nouvelle, true, "historique=false", 200);
        assertThat(mongo.getCollection("Paiement").find(new Document("numeroFacture", "F-3")).first().getString("status")).isEqualTo("PENDING");
        assertThat(p).isNotNull();
    }

    /** En-têtes de la fiche complète (111 colonnes) utilisés par l'import. */
    private static Map<Integer, String> ficheComplete() {
        Map<Integer, String> h = new java.util.HashMap<>();
        h.put(0, "N°Dossier"); h.put(1, "Code Commune"); h.put(2, "SOUS-PROJET"); h.put(4, "INFRASTRUCTURE"); h.put(5, "TYPE D'AMENAGEMENT");
        h.put(9, "Vague/CYCLE"); h.put(10, "Tranche"); h.put(12, "Indicateurs 1"); h.put(13, "Unité"); h.put(14, "Quantité");
        h.put(15, "Indicateurs 2"); h.put(16, "Unité de dimension"); h.put(17, "Dimension"); h.put(21, "Montant prévisionnel BE");
        h.put(22, "Montant prévisionnel Ese"); h.put(24, "Année de financement"); h.put(25, "Coordonnées GPS"); h.put(26, "FOKONTANY");
        h.put(27, "COMMUNE"); h.put(30, "DISTRICT"); h.put(35, "Dénomination"); h.put(36, "RCS"); h.put(37, "NIF"); h.put(38, "STAT");
        h.put(39, "Date Contrat"); h.put(40, "Délai du contrat"); h.put(41, "Date de début (OS)"); h.put(42, "Montant contrat");
        h.put(43, "Avancement prévue (%)"); h.put(54, "Dénomination"); h.put(58, "Date Contrat"); h.put(61, "Montantcontrat");
        h.put(79, "Dénomination"); h.put(110, "Photo \"réalisée\"");
        return h;
    }

    @Test @Order(3)
    void ficheDeSuiviComplete() throws Exception {
        Date signature = new Date(1_750_000_000_000L);
        Map<Integer, Object> row = new java.util.HashMap<>();
        row.put(0, "21-900"); row.put(1, "410101"); row.put(2, "EPP Tsaramandroso"); row.put(4, "École primaire"); row.put(5, "Construction");
        row.put(9, "V1"); row.put(10, "T2"); row.put(14, 3); row.put(17, 120); row.put(21, 1000); row.put(22, 2500.5); row.put(24, 2025);
        row.put(25, "-15.7,46.3"); row.put(26, "Tsaramandroso"); row.put(27, "MAHAJANGA"); row.put(30, "MAJUNGA I");
        row.put(35, "BE ALPHA"); row.put(36, "RCS-A"); row.put(39, signature); row.put(40, 3); row.put(41, "15/07/2025"); row.put(42, 800);
        row.put(43, 20); row.put(54, "ESE BETA"); row.put(58, signature); row.put(61, 2000);
        row.put(111, "CBE-01"); row.put(112, "CESE-01"); row.put(114, "CONV-7");
        byte[] file = xlsx(ficheComplete(), List.of(row));
        String url = "/api/imports/programme/prog/subProgramme/sub/projects";

        String dry = upload(url, file, false, null, 200);
        assertThat((Integer) JsonPath.read(dry, "$.creations")).isEqualTo(1);
        assertThat(count("Project", new Document())).isZero();
        assertThat(JsonPath.<List<String>>read(dry, "$.details[0].messages").toString())
                .contains("Infrastructure « École primaire » créé(e)").contains("contrat BE « CBE-01 » créé");

        upload(url, file, true, null, 200);
        Document p = mongo.getCollection("Project").find(new Document("numero", "21-900")).first();
        assertThat(p.getString("subProgrammeExternalId")).isEqualTo("sub");
        assertThat(p.getString("programmeExternalId")).isEqualTo("prog");
        assertThat(p.getString("communeId")).isEqualTo("c1");
        assertThat(p.getString("regionId")).isEqualTo("r1"); // dérivé de la commune
        assertThat(p.getString("fokontanyId")).isEqualTo("f1");
        assertThat(p.get("montantPrevEse")).isEqualTo(2500.5);
        assertThat(p.get("anneeFinancement")).isEqualTo(2025);
        assertThat(p.get("indicateurProduit1")).isEqualTo(3.0);
        assertThat(p.get("indicateurProduit2")).isEqualTo(120.0);
        assertThat(p.getString("nConvention")).isEqualTo("CONV-7");
        assertThat(count("Infrastructure", new Document("title", "École primaire"))).isEqualTo(1);
        Document be = mongo.getCollection("Contrat").find(new Document("title", "CBE-01")).first();
        assertThat(be.getString("typePrestataire")).isEqualTo("BE");
        assertThat(be.get("montantContrat")).isEqualTo(800.0);
        assertThat(be.get("dateDebut")).isNotNull();
        assertThat(be.getString("subProgrammeExternalId")).isEqualTo("sub");
        assertThat(mongo.getCollection("GroupeContrat").find(new Document("externalId", be.getString("externalId"))).first()).isNotNull();
        assertThat(count("provider", new Document("name", "ESE BETA"))).isEqualTo(1);

        // Réimport : sous-projet et contrats mis à jour, rien n'est dupliqué.
        row.put(42, 900);
        String again = upload(url, xlsx(ficheComplete(), List.of(row)), true, null, 200);
        assertThat((Integer) JsonPath.read(again, "$.misesAJour")).isEqualTo(1);
        assertThat(count("Project", new Document("numero", "21-900"))).isEqualTo(1);
        assertThat(count("Contrat", new Document("projectExternalId", p.getString("externalId")))).isEqualTo(2);
        assertThat(mongo.getCollection("Contrat").find(new Document("title", "CBE-01")).first().get("montantContrat")).isEqualTo(900.0);
        assertThat(count("Infrastructure", new Document())).isEqualTo(1);

        // Même dossier depuis un autre sous-programme : refusé (pas de déplacement silencieux).
        String other = upload("/api/imports/programme/prog/subProgramme/sub2/projects", file, true, null, 200);
        assertThat(JsonPath.<String>read(other, "$.details[0].messages[0]")).contains("autre sous-programme");
    }

    @Test @Order(4)
    void ficheCourte() throws Exception {
        Map<Integer, String> h = Map.ofEntries(Map.entry(0, "N°Dossier"), Map.entry(1, "Code Commune"), Map.entry(2, "Sous-projet"),
                Map.entry(4, "Infrastructure"), Map.entry(5, "Type de Travaux"), Map.entry(6, "Type d'Infrastructure"), Map.entry(7, "Secteur"),
                Map.entry(23, "Montantprévisionnel BE"), Map.entry(24, "Montantprévisionnel Ese"), Map.entry(26, "Année de financement"),
                Map.entry(27, "Latitude,Longitude"), Map.entry(29, "Fokontany"), Map.entry(30, "Commune"), Map.entry(33, "District"),
                Map.entry(38, "Dénomination"), Map.entry(42, "Date Contrat"), Map.entry(45, "Montantcontrat"));
        byte[] file = xlsx(h, List.of(Map.of(0, "22-001", 2, "CSB Mahajanga", 6, "Santé de base", 7, "SANTE", 23, 500, 24, 4500,
                26, 2026, 30, "Mahajanga", 33, "Majunga I", 38, "BE GAMMA")));
        String report = upload("/api/imports/programme/prog/subProgramme/sub/projects", file, true, null, 200);
        assertThat((Integer) JsonPath.read(report, "$.creations")).isEqualTo(1);
        assertThat(JsonPath.<List<String>>read(report, "$.details[0].messages").toString()).contains("N° de contrat absent");
        Document p = mongo.getCollection("Project").find(new Document("numero", "22-001")).first();
        assertThat(p.get("montantPrevBE")).isEqualTo(500.0);
        assertThat(p.getString("communeId")).isEqualTo("c1"); // nom + district, sans tenir compte de la casse
        assertThat(p.getString("typeInfrastructureExternalId")).isNotNull();
        assertThat(p.getString("secteurExternalId")).isNotNull();
        String sansDossier = upload("/api/imports/programme/prog/subProgramme/sub/projects", xlsx(Map.of(0, "Autre"), List.of()), false, null, 400);
        assertThat(sansDossier).contains("N°Dossier");
    }

    private static RequestPostProcessor administration(String userId) {
        EfdlUserPrincipal p = new EfdlUserPrincipal(userId, userId, userId, UserType.ADMINISTRATION, SCHOOL, Set.of("g-import"), null);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(p, null, p.authorities()));
    }

    /** Imports réservés à l'administrateur et au « Validateur info » de l'application (school.validatorId). */
    @Test @Order(5)
    void importReserveAuValidateurInfoEtAdmin() throws Exception {
        insert("user", "com.ffs.kiwi.model.user.User", "u-info", new Document("username", "info").append("type", "ADMINISTRATION"), true);
        insert("user", "com.ffs.kiwi.model.user.User", "u-autre", new Document("username", "autre").append("type", "ADMINISTRATION"), true);
        // Les deux ont le droit sur la route de l'écran : seul le validateur info doit pouvoir importer.
        mongo.getCollection("user_group").insertOne(new Document("_id", new ObjectId())
                .append("className", "com.ffs.kiwi.model.user.group.UserGroup").append("name", "Import")
                .append("allowedPaths", List.of("/admin/project/prestataires")).append("schoolExternalId", SCHOOL)
                .append("orderPlace", 0).append("externalId", "g-import"));
        byte[] file = xlsx(Map.of(0, "N°", 1, "Dénomination"), List.of(Map.of(0, 50, 1, "BE NORD")));
        upload("/api/imports/prestataires", file, false, null, 403, administration("u-info"));

        // Fiche application : le validateur info doit être un utilisateur ADMINISTRATION de l'application.
        String url = "/api/admin/schools/" + SCHOOL;
        mvc.perform(put(url).with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"sdi-fdl\",\"validatorId\":\"inconnu\"}")).andExpect(status().isBadRequest());
        String school = mvc.perform(put(url).with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"sdi-fdl\",\"validatorId\":\"u-info\"}")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(school, "$.validatorId")).isEqualTo("u-info");
        assertThat(JsonPath.<List<String>>read(mvc.perform(get(url + "/validators").with(admin())).andReturn().getResponse()
                .getContentAsString(), "$[*].externalId")).contains("u-info", "u-autre");

        upload("/api/imports/prestataires", file, false, null, 200, administration("u-info"));
        assertThat(upload("/api/imports/prestataires", file, false, null, 403, administration("u-autre"))).contains("validateur info");
        upload("/api/imports/prestataires", file, false, null, 200); // administrateur

        // Affichage : canImport de /api/auth/me.
        assertThat((Boolean) JsonPath.read(mvc.perform(get("/api/auth/me").with(administration("u-info"))).andReturn()
                .getResponse().getContentAsString(), "$.canImport")).isTrue();
        assertThat((Boolean) JsonPath.read(mvc.perform(get("/api/auth/me").with(administration("u-autre"))).andReturn()
                .getResponse().getContentAsString(), "$.canImport")).isFalse();
    }
}
