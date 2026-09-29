package com.ffs.efdl.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Date;
import java.util.List;
import java.util.Set;

import javax.imageio.ImageIO;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.ffs.efdl.model.UserType;
import com.ffs.efdl.security.EfdlUserPrincipal;
import com.jayway.jsonpath.JsonPath;

/**
 * Photos de profil (collection user_image) et documents joints (factures, avancement des sous-projets),
 * sur la base de TEST jetable {@code efdl_it} et un dossier de fichiers temporaire.
 * Lancement : {@code mvn test -Defdl.it=true -Dtest=FilesIT}.
 */
@SpringBootTest(properties = { "spring.mongodb.database=efdl_it", "efdl.files.root=target/it-files" })
@ActiveProfiles("local")
@EnabledIfSystemProperty(named = "efdl.it", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FilesIT {

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
        insert("user", "com.ffs.kiwi.model.user.User", "u1", new Document("username", "rakoto").append("type", "ADMINISTRATION"), true);
        insert("user", "com.ffs.kiwi.model.user.User", "u-ailleurs", new Document("username", "autre").append("type", "ADMINISTRATION")
                .append("schoolExternalId", "autre-ecole"), false);
        insert("Project", P + "Project", "proj", new Document("numero", "24-001").append("programmeExternalId", "prog")
                .append("subProgrammeExternalId", "sub"), true);
        insert("Programme", P + "Programme", "prog", new Document("title", "PDCID I"), true);
        insert("SubProgramme", P + "SubProgramme", "sub", new Document("title", "MOD").append("programmeExternalId", "prog"), true);
        insert("Avancement", P + "Avancement", "jalon1", new Document("title", "Démarrage des travaux"), true);
        insert("Paiement", P + "Paiement", "fact", new Document("numeroFacture", "F-1").append("status", "PENDING"), true);
        insert("Paiement", P + "Paiement", "fact-ok", new Document("numeroFacture", "F-2").append("status", "VALIDATE"), true);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private void insert(String collection, String className, String id, Document fields, boolean schoolScoped) {
        Document d = new Document("_id", new ObjectId()).append("className", className);
        d.putAll(fields);
        if (schoolScoped) d.append("schoolExternalId", SCHOOL).append("orderPlace", 0);
        d.append("externalId", id).append("creationDate", new Date()).append("lastUpdateDate", new Date());
        mongo.getCollection(collection).insertOne(d);
    }

    private static RequestPostProcessor as(String id, UserType type) {
        EfdlUserPrincipal p = new EfdlUserPrincipal(id, id, id, type, SCHOOL, Set.of(), null);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(p, null, p.authorities()));
    }

    private static RequestPostProcessor admin() {
        return as("it-admin", UserType.ADMIN);
    }

    private static byte[] png(int w, int h) throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), "png", out);
            return out.toByteArray();
        }
    }

    private MvcResult upload(String url, String name, byte[] content, RequestPostProcessor who, int expected, String... params)
            throws Exception {
        MockMultipartHttpServletRequestBuilder req = multipart(url).file(new MockMultipartFile("file", name, "application/octet-stream", content));
        for (int i = 0; i < params.length; i += 2) req.param(params[i], params[i + 1]);
        return mvc.perform(req.with(who).with(csrf())).andExpect(status().is(expected)).andReturn();
    }

    @Test
    void photoDeProfil() throws Exception {
        mvc.perform(get("/api/users/u1/photo").with(admin())).andExpect(status().isNotFound());
        upload("/api/admin/users/u1/photo", "photo.png", png(400, 300), admin(), 204);
        upload("/api/admin/users/u1/photo", "photo.png", "pas une image".getBytes(), admin(), 400);
        upload("/api/admin/users/u1/photo", "photo.exe", png(10, 10), admin(), 400);

        // Collection user_image (UserImage) ; un remplacement met à jour le même document.
        upload("/api/auth/me/photo", "moi.png", png(500, 500), as("u1", UserType.ADMINISTRATION), 204);
        List<Document> images = mongo.getCollection("user_image").find(new Document("userExternalId", "u1")).into(new java.util.ArrayList<>());
        assertThat(images).hasSize(1);
        assertThat(images.get(0).getString("className")).isEqualTo("com.ffs.kiwi.model.image.UserImage");
        assertThat(images.get(0).getString("filename")).startsWith("u1-").endsWith(".png");

        MvcResult full = mvc.perform(get("/api/users/u1/photo").with(as("u1", UserType.ADMINISTRATION))).andExpect(status().isOk()).andReturn();
        assertThat(full.getResponse().getContentType()).isEqualTo("image/png");
        BufferedImage small = ImageIO.read(new java.io.ByteArrayInputStream(mvc.perform(get("/api/users/u1/photo?size=65").with(admin()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()));
        assertThat(Math.max(small.getWidth(), small.getHeight())).isEqualTo(65);
        // Dernière connexion (collection history) : la plus récente.
        for (int days : new int[] { 3, 1 }) {
            mongo.getCollection("history").insertOne(new Document("_id", new ObjectId()).append("userId", "u1")
                    .append("historyType", "CONNECTION").append("connectionType", "LOGIN").append("schoolExternalId", SCHOOL)
                    .append("creationDate", new Date(System.currentTimeMillis() - days * 86_400_000L)));
        }
        // Liste des utilisateurs : indique qui a une photo (pas de requête inutile pour les autres).
        String users = mvc.perform(get("/api/admin/users").with(admin())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Boolean>>read(users, "$..[?(@.externalId=='u1')].hasPhoto")).containsExactly(true);
        String last = JsonPath.<List<String>>read(users, "$..[?(@.externalId=='u1')].lastLogin").get(0);
        assertThat(java.time.OffsetDateTime.parse(last).toInstant()).isAfter(java.time.Instant.now().minusSeconds(2 * 86_400));
        // Utilisateur d'une autre application : photo invisible.
        mvc.perform(get("/api/users/u-ailleurs/photo").with(as("u1", UserType.ADMINISTRATION))).andExpect(status().isNotFound());
    }

    @Test
    void documentsDeFacture() throws Exception {
        String id = JsonPath.read(upload("/api/attachments/paiements/fact", "facture.pdf", "%PDF-1.4 test".getBytes(), admin(), 201,
                "title", "Facture signée").getResponse().getContentAsString(), "$.externalId");
        upload("/api/attachments/paiements/fact", "virus.exe", "x".getBytes(), admin(), 400);
        // Sans droit sur la fiche facture : refusé.
        upload("/api/attachments/paiements/fact", "f.pdf", "x".getBytes(), as("u1", UserType.ADMINISTRATION), 403);
        mvc.perform(get("/api/attachments/paiements/fact").with(as("u1", UserType.ADMINISTRATION))).andExpect(status().isForbidden());

        String list = mvc.perform(get("/api/attachments/paiements/fact").with(admin())).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(list, "$.items[*].title")).containsExactly("Facture signée");
        assertThat((Boolean) JsonPath.read(list, "$.canDelete")).isTrue();
        MvcResult file = mvc.perform(get("/api/attachments/" + id + "/file").with(admin())).andExpect(status().isOk()).andReturn();
        assertThat(file.getResponse().getContentAsString()).isEqualTo("%PDF-1.4 test");
        assertThat(file.getResponse().getHeader("Content-Disposition")).startsWith("inline").contains("facture.pdf");

        // Facture validée : ajout possible, suppression refusée (verrou) ; facture en attente : suppression logique.
        String locked = JsonPath.read(upload("/api/attachments/paiements/fact-ok", "preuve.jpg", png(5, 5), admin(), 201)
                .getResponse().getContentAsString(), "$.externalId");
        mvc.perform(delete("/api/attachments/" + locked).with(admin()).with(csrf())).andExpect(status().isConflict());
        mvc.perform(delete("/api/attachments/" + id).with(admin()).with(csrf())).andExpect(status().isNoContent());
        assertThat(mongo.getCollection("attachment").find(new Document("externalId", id)).first().getString("status")).isEqualTo("DELETED");
        assertThat(JsonPath.<List<?>>read(mvc.perform(get("/api/attachments/paiements/fact").with(admin())).andReturn()
                .getResponse().getContentAsString(), "$.items")).isEmpty();
        mvc.perform(get("/api/attachments/" + id + "/file").with(admin())).andExpect(status().isNotFound());
    }

    @Test
    void documentsAvancementSousProjet() throws Exception {
        upload("/api/attachments/projects/proj", "pv.docx", "doc".getBytes(), admin(), 201, "title", "PV de démarrage",
                "avancementId", "jalon1");
        upload("/api/attachments/projects/proj", "pv.docx", "doc".getBytes(), admin(), 400, "avancementId", "inconnu");
        upload("/api/attachments/projects/inconnu", "pv.docx", "doc".getBytes(), admin(), 404);
        String list = mvc.perform(get("/api/attachments/projects/proj").with(admin())).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(list, "$.items[*].avancementTitle")).containsExactly("Démarrage des travaux");
        assertThat(JsonPath.<List<String>>read(list, "$.items[*].originalFilename")).containsExactly("pv.docx");
        mvc.perform(get("/api/attachments/projects/proj").with(as("u1", UserType.ADMINISTRATION))).andExpect(status().isForbidden());
    }

    /** Suivi d'avancement des programmes et des projets : mêmes documents, droit de leur page d'avancement. */
    @Test
    void documentsAvancementProgrammeEtProjet() throws Exception {
        upload("/api/attachments/programmes/prog", "rapport.pdf", "%PDF".getBytes(), admin(), 201, "avancementId", "jalon1");
        upload("/api/attachments/subProgrammes/sub", "pv.xlsx", "x".getBytes(), admin(), 201, "title", "Suivi MOD");
        upload("/api/attachments/subProgrammes/inconnu", "pv.xlsx", "x".getBytes(), admin(), 404);
        assertThat(JsonPath.<List<String>>read(mvc.perform(get("/api/attachments/programmes/prog").with(admin())).andReturn()
                .getResponse().getContentAsString(), "$.items[*].avancementTitle")).containsExactly("Démarrage des travaux");
        assertThat(JsonPath.<List<String>>read(mvc.perform(get("/api/attachments/subProgrammes/sub").with(admin())).andReturn()
                .getResponse().getContentAsString(), "$.items[*].title")).containsExactly("Suivi MOD");
        // Sans droit sur la page d'avancement : refusé.
        mvc.perform(get("/api/attachments/programmes/prog").with(as("u1", UserType.ADMINISTRATION))).andExpect(status().isForbidden());
        upload("/api/attachments/subProgrammes/sub", "pv.xlsx", "x".getBytes(), as("u1", UserType.ADMINISTRATION), 403);
    }
}
