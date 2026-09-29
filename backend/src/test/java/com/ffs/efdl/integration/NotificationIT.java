package com.ffs.efdl.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
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
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.ffs.efdl.model.UserType;
import com.ffs.efdl.security.EfdlUserPrincipal;
import com.jayway.jsonpath.JsonPath;

/**
 * Notifications personnalisées : facture à valider (validateur), facture validée (auteur de la saisie), lecture,
 * préférences par type (application / e-mail). Base de TEST jetable {@code efdl_it}, SMTP simulé.
 */
@SpringBootTest(properties = { "spring.mongodb.database=efdl_it", "efdl.mail.from=efdl@test.mg" })
@ActiveProfiles("local")
@EnabledIfSystemProperty(named = "efdl.it", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NotificationIT {

    static final String SCHOOL = "sdi-fdl";
    static final String P = "com.ffs.kiwi.model.project.";

    @Autowired WebApplicationContext context;
    @Autowired MongoTemplate mongo;
    @MockitoBean JavaMailSender mailSender;
    MockMvc mvc;

    @BeforeAll
    void seed() {
        assertThat(mongo.getDb().getName()).isEqualTo("efdl_it"); // garde-fou : jamais la base réelle
        mongo.getDb().drop();
        insert("school", "com.ffs.kiwi.model.School", SCHOOL, new Document("name", "sdi-fdl"), false);
        insert("user", "com.ffs.kiwi.model.user.User", "u-saisie", new Document("username", "saisie").append("lastName", "RASOA")
                .append("email", "saisie@fdl.mg").append("type", "ADMIN"), true);
        insert("user", "com.ffs.kiwi.model.user.User", "u-v1", new Document("username", "val1").append("lastName", "RAKOTO")
                .append("email", "v1@fdl.mg").append("type", "ADMIN"), true);
        insert("Validateur", P + "Validateur", "val-1", new Document("programmeExternalId", "prog").append("ordre", 1)
                .append("userExternalId", "u-v1"), true);
        insert("provider", "com.ffs.kiwi.model.dashboard.Provider", "prov", new Document("name", "BTP SUD").append("number", 1), true);
        insert("GroupeContrat", P + "GroupeContrat", "g1", new Document("title", "C-01").append("providerExternalId", "prov"), true);
        insert("Contrat", P + "Contrat", "c1", new Document("groupeContratExternalId", "g1").append("programmeExternalId", "prog"), true);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private void insert(String collection, String className, String id, Document fields, boolean schoolScoped) {
        Document d = new Document("_id", new ObjectId()).append("className", className);
        d.putAll(fields);
        if (schoolScoped) d.append("schoolExternalId", SCHOOL).append("orderPlace", 0);
        d.append("externalId", id).append("creationDate", new Date()).append("lastUpdateDate", new Date());
        mongo.getCollection(collection).insertOne(d);
    }

    private static RequestPostProcessor as(String id) {
        EfdlUserPrincipal p = new EfdlUserPrincipal(id, id, id, UserType.ADMIN, SCHOOL, Set.of(), null);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(p, null, p.authorities()));
    }

    private String send(MockHttpServletRequestBuilder req, String json, int expected, String who) throws Exception {
        return mvc.perform(req.with(as(who)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
    }

    private String facture(String numero) throws Exception {
        return JsonPath.read(send(post("/api/params/paiements"), "{\"providerExternalId\":\"prov\",\"groupeContratExternalId\":\"g1\","
                + "\"numeroFacture\":\"" + numero + "\",\"montant\":1500000}", 201, "u-saisie"), "$.externalId");
    }

    @Test
    void notificationsPersonnalisees() throws Exception {
        // Facture saisie : le validateur est notifié dans l'application (et par e-mail, réglage par défaut).
        String f1 = facture("F-1");
        String mine = send(get("/api/notifications"), "", 200, "u-v1");
        assertThat(JsonPath.<List<String>>read(mine, "$.items[*].type")).containsExactly("VALIDATION_ATTENTE");
        assertThat(JsonPath.<String>read(mine, "$.items[0].message")).contains("F-1", "1 500 000 MGA");
        assertThat(JsonPath.<String>read(mine, "$.items[0].link")).isEqualTo("/admin/project/validationPaiement/edit/" + f1);
        assertThat((Integer) JsonPath.read(send(get("/api/notifications/unread-count"), "", 200, "u-v1"), "$.unread")).isEqualTo(1);
        verify(mailSender, timeout(5000).times(1)).send(any(SimpleMailMessage.class));

        // Chacun ne voit que les siennes ; lecture.
        assertThat(JsonPath.<List<?>>read(send(get("/api/notifications"), "", 200, "u-saisie"), "$.items")).isEmpty();
        String id = JsonPath.read(mine, "$.items[0].id");
        send(post("/api/notifications/" + id + "/read"), "", 204, "u-saisie"); // pas la sienne : sans effet
        assertThat((Integer) JsonPath.read(send(get("/api/notifications/unread-count"), "", 200, "u-v1"), "$.unread")).isEqualTo(1);
        send(post("/api/notifications/" + id + "/read"), "", 204, "u-v1");
        assertThat((Integer) JsonPath.read(send(get("/api/notifications/unread-count"), "", 200, "u-v1"), "$.unread")).isZero();

        // Validation finale : l'auteur de la saisie est notifié dans l'application (e-mail désactivé par défaut).
        send(post("/api/validations/" + f1 + "/validate"), "", 204, "u-v1");
        String saisie = send(get("/api/notifications?unread=true"), "", 200, "u-saisie");
        assertThat(JsonPath.<List<String>>read(saisie, "$.items[*].type")).containsExactly("FACTURE_VALIDEE");
        assertThat(JsonPath.<String>read(saisie, "$.items[0].message")).contains("validée par RAKOTO");
        verify(mailSender, after(500).times(1)).send(any(SimpleMailMessage.class));

        // Préférences : le validateur ne veut plus rien pour les factures à valider.
        String prefs = send(get("/api/notifications/preferences"), "", 200, "u-v1");
        assertThat(JsonPath.<List<Boolean>>read(prefs, "$[?(@.type=='FACTURE_VALIDEE')].mail")).containsExactly(false);
        send(put("/api/notifications/preferences"), "[{\"type\":\"VALIDATION_ATTENTE\",\"app\":false,\"mail\":false}]", 200, "u-v1");
        facture("F-2");
        assertThat((Integer) JsonPath.read(send(get("/api/notifications/unread-count"), "", 200, "u-v1"), "$.unread")).isZero();
        verify(mailSender, after(500).times(1)).send(any(SimpleMailMessage.class));

        send(post("/api/notifications/read-all"), "", 204, "u-saisie");
        assertThat((Integer) JsonPath.read(send(get("/api/notifications/unread-count"), "", 200, "u-saisie"), "$.unread")).isZero();
    }
}
