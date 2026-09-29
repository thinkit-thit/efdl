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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.ffs.efdl.model.UserType;
import com.ffs.efdl.security.EfdlUserPrincipal;
import com.jayway.jsonpath.JsonPath;

/**
 * Historique des modifications (audit_log) : qui a créé, modifié, supprimé, validé quoi et quand, valeurs avant / après,
 * consultation réservée à l'administrateur et au validateur info. Base de TEST jetable {@code efdl_it}.
 */
@SpringBootTest(properties = { "spring.mongodb.database=efdl_it", "efdl.files.root=target/it-files" })
@ActiveProfiles("local")
@EnabledIfSystemProperty(named = "efdl.it", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuditIT {

    static final String SCHOOL = "sdi-fdl";
    static final String P = "com.ffs.kiwi.model.project.";

    @Autowired WebApplicationContext context;
    @Autowired MongoTemplate mongo;
    MockMvc mvc;

    @BeforeAll
    void seed() {
        assertThat(mongo.getDb().getName()).isEqualTo("efdl_it"); // garde-fou : jamais la base réelle
        mongo.getDb().drop();
        insert("school", "com.ffs.kiwi.model.School", SCHOOL, new Document("name", "sdi-fdl").append("validatorId", "u-info"), false);
        insert("user", "com.ffs.kiwi.model.user.User", "it-admin", new Document("username", "admin").append("lastName", "ADMIN")
                .append("type", "ADMIN"), true);
        insert("user", "com.ffs.kiwi.model.user.User", "u-info", new Document("username", "info").append("lastName", "RAKOTO")
                .append("type", "ADMINISTRATION"), true);
        insert("user", "com.ffs.kiwi.model.user.User", "u-autre", new Document("username", "autre").append("type", "ADMINISTRATION"), true);
        insert("provider", "com.ffs.kiwi.model.dashboard.Provider", "prov", new Document("name", "BTP SUD").append("number", 1), true);
        insert("provider", "com.ffs.kiwi.model.dashboard.Provider", "prov2", new Document("name", "BE CONSEIL").append("number", 2), true);
        insert("GroupeContrat", P + "GroupeContrat", "g1", new Document("title", "C-01").append("providerExternalId", "prov"), true);
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

    private String send(MockHttpServletRequestBuilder req, String json, int expected, RequestPostProcessor who) throws Exception {
        return mvc.perform(req.with(who).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
    }

    @Test
    void quiAFaitQuoiEtQuand() throws Exception {
        RequestPostProcessor admin = as("it-admin", UserType.ADMIN);
        String secteur = JsonPath.read(send(post("/api/params/secteurs"), "{\"title\":\"Santé\"}", 201, admin), "$.externalId");
        send(put("/api/params/secteurs/" + secteur), "{\"title\":\"Santé publique\"}", 200, admin);
        send(put("/api/params/secteurs/" + secteur), "{\"title\":\"Santé publique\"}", 200, admin); // sans changement : pas de trace
        send(delete("/api/params/secteurs/" + secteur), "", 204, admin);
        // Référence : libellé enregistré (prestataire), pas l'identifiant.
        String facture = JsonPath.read(send(post("/api/params/paiements"), "{\"providerExternalId\":\"prov\",\"groupeContratExternalId\":\"g1\","
                + "\"numeroFacture\":\"F-9\",\"montant\":1000}", 201, admin), "$.externalId");
        send(put("/api/params/paiements/" + facture), "{\"providerExternalId\":\"prov2\"}", 200, admin);

        String page = send(get("/api/audit?collection=Secteur"), "", 200, as("u-info", UserType.ADMINISTRATION));
        assertThat(JsonPath.<List<String>>read(page, "$.items[*].action")).containsExactly("SUPPRESSION", "MODIFICATION", "CREATION");
        assertThat(JsonPath.<List<String>>read(page, "$.items[*].userName")).containsOnly("ADMIN");
        assertThat(JsonPath.<List<String>>read(page, "$.items[1].changes[?(@.field=='title')].before")).containsExactly("Santé");
        assertThat(JsonPath.<List<String>>read(page, "$.items[1].changes[?(@.field=='title')].after")).containsExactly("Santé publique");
        assertThat(JsonPath.<String>read(page, "$.items[0].targetLabel")).isEqualTo("Santé publique");

        String factures = send(get("/api/audit?collection=Paiement&action=MODIFICATION"), "", 200, admin);
        assertThat(JsonPath.<List<String>>read(factures, "$.items[0].changes[?(@.field=='providerExternalId')].before")).containsExactly("BTP SUD");
        assertThat(JsonPath.<List<String>>read(factures, "$.items[0].changes[?(@.field=='providerExternalId')].after")).containsExactly("BE CONSEIL");
        assertThat(JsonPath.<String>read(factures, "$.items[0].entityLabel")).isEqualTo("facture");
        // Recherche texte, filtres proposés.
        assertThat((Integer) JsonPath.read(send(get("/api/audit?q=conseil"), "", 200, admin), "$.total")).isEqualTo(1);
        assertThat(JsonPath.<List<String>>read(page, "$.users[*].label")).contains("ADMIN");

        // Réservé à l'administrateur et au validateur info.
        send(get("/api/audit"), "", 403, as("u-autre", UserType.ADMINISTRATION));
        assertThat((Boolean) JsonPath.read(send(get("/api/auth/me"), "", 200, as("u-info", UserType.ADMINISTRATION)), "$.canViewAudit")).isTrue();
        assertThat((Boolean) JsonPath.read(send(get("/api/auth/me"), "", 200, as("u-autre", UserType.ADMINISTRATION)), "$.canViewAudit")).isFalse();
        // Jamais de valeur sensible dans l'historique.
        assertThat(mongo.getCollection("audit_log").find(new Document("changes.field", new Document("$regex", "(?i)password"))).first()).isNull();

        // Suppression en masse (administrateur seul) : archive Excel, puis suppression, trace de la purge conservée.
        send(post("/api/audit/purge?collection=Secteur&expected=3"), "", 403, as("u-info", UserType.ADMINISTRATION));
        send(post("/api/audit/purge?collection=Secteur&expected=2"), "", 409, admin); // l'historique a changé
        var archive = mvc.perform(post("/api/audit/purge?collection=Secteur&expected=3").with(admin).with(csrf()))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(archive.getContentType()).contains("spreadsheetml");
        assertThat(archive.getContentAsByteArray()).startsWith(new byte[] { 0x50, 0x4B }); // fichier xlsx (zip)
        assertThat((Integer) JsonPath.read(send(get("/api/audit?collection=Secteur"), "", 200, admin), "$.total")).isZero();
        // Suppression définitive : aucune ligne ne reste dans l'historique ; l'archive est listée.
        assertThat(mongo.getCollection("audit_log").countDocuments(new Document("action", "PURGE"))).isZero();
        String name = JsonPath.<List<String>>read(send(get("/api/audit/archives"), "", 200, admin), "$[*].name").get(0);
        send(get("/api/audit/archives"), "", 403, as("u-info", UserType.ADMINISTRATION));
        mvc.perform(get("/api/audit/archives/" + name).with(admin)).andExpect(status().isOk());
        mvc.perform(get("/api/audit/archives/" + name).with(as("u-info", UserType.ADMINISTRATION))).andExpect(status().isForbidden());
        // Tout supprimer : la collection est vide, rien ne reste en base.
        long rest = mongo.getCollection("audit_log").countDocuments(new Document("schoolExternalId", SCHOOL));
        mvc.perform(post("/api/audit/purge?expected=" + rest).with(admin).with(csrf())).andExpect(status().isOk());
        assertThat(mongo.getCollection("audit_log").countDocuments(new Document("schoolExternalId", SCHOOL))).isZero();
    }
}
