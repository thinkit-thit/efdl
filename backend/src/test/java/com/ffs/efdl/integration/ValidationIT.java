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
import org.mockito.ArgumentCaptor;
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
 * Circuit de validation des factures, sur la base de TEST jetable {@code efdl_it}.
 * Lancement : {@code mvn test -Defdl.it=true -Dtest=ValidationIT}.
 */
@SpringBootTest(properties = { "spring.mongodb.database=efdl_it", "efdl.mail.from=efdl@test.mg",
        "efdl.mail.app-url=https://efdl.test" })
@ActiveProfiles("local")
@EnabledIfSystemProperty(named = "efdl.it", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ValidationIT {

    static final String SCHOOL = "sdi-fdl";
    static final String P = "com.ffs.kiwi.model.project.";

    @Autowired WebApplicationContext context;
    @Autowired MongoTemplate mongo;
    @MockitoBean JavaMailSender mailSender; // SMTP simulé
    MockMvc mvc;

    @BeforeAll
    void seed() {
        assertThat(mongo.getDb().getName()).isEqualTo("efdl_it"); // garde-fou : jamais la base réelle
        mongo.getDb().drop();
        insert("school", "com.ffs.kiwi.model.School", SCHOOL, new Document("name", "sdi-fdl"), false);
        insert("user", "com.ffs.kiwi.model.user.User", "u-v1", new Document("username", "val1").append("lastName", "RAKOTO")
                .append("firstName", "Aina").append("email", "aina@fdl.mg").append("type", "ADMINISTRATION"), true);
        insert("user", "com.ffs.kiwi.model.user.User", "u-v2", new Document("username", "val2").append("lastName", "RABE")
                .append("firstName", "Hery").append("email", "ancienne@fdl.mg").append("type", "ADMINISTRATION"), true);
        insert("Programme", P + "Programme", "prog", new Document("title", "PDCID 4"), true);
        insert("Programme", P + "Programme", "prog-vide", new Document("title", "Sans validateur"), true);
        insert("Project", P + "Project", "proj", new Document("numero", "24-001").append("title", "EPP")
                .append("programmeExternalId", "prog").append("subProgrammeExternalId", "sub"), true);
        insert("Project", P + "Project", "proj-vide", new Document("numero", "24-002").append("title", "CSB")
                .append("programmeExternalId", "prog-vide").append("subProgrammeExternalId", "sub"), true);
        insert("Question", P + "Question", "q1", new Document("question", "PV de réception signé ?").append("typeReponse", "OUI_NON"), true);
        insert("provider", "com.ffs.kiwi.model.dashboard.Provider", "prov", new Document("name", "BTP SUD").append("number", 1), true);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private void insert(String collection, String className, String id, Document fields, boolean schoolScoped) {
        Document d = new Document("_id", new ObjectId()).append("className", className);
        d.putAll(fields);
        if (schoolScoped) d.append("schoolExternalId", SCHOOL).append("orderPlace", 0);
        d.append("externalId", id).append("creationDate", new Date()).append("lastUpdateDate", new Date());
        mongo.getCollection(collection).insertOne(d);
    }

    /** Utilisateur ADMIN (droits sur toutes les routes) : le contrôle testé est celui du validateur attendu. */
    private static RequestPostProcessor as(String userId) {
        EfdlUserPrincipal p = new EfdlUserPrincipal(userId, userId, userId, UserType.ADMIN, SCHOOL, Set.of(), null);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(p, null, p.authorities()));
    }

    private String send(MockHttpServletRequestBuilder req, String json, int expected, String userId) throws Exception {
        return mvc.perform(req.with(as(userId)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
    }

    private Document paiement(String id) {
        return mongo.getCollection("Paiement").find(new Document("externalId", id)).first();
    }

    /** Destinataires des e-mails envoyés jusqu'ici (envoi en arrière-plan : attente du n-ième). */
    private List<String> mails(int expected) {
        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, timeout(5000).times(expected)).send(sent.capture());
        sent.getAllValues().forEach(m -> {
            assertThat(m.getSubject()).isEqualTo("VALIDATION DE PAIEMENT");
            assertThat(m.getFrom()).isEqualTo("efdl@test.mg");
            assertThat(m.getText()).contains("paiements en attente de validation", "https://efdl.test/admin/project/validationPaiements");
        });
        return sent.getAllValues().stream().map(m -> m.getTo()[0]).toList();
    }

    @Test
    void circuitComplet() throws Exception {
        // Validateurs du programme : l'ordre fixe le circuit, il est unique dans le programme.
        send(post("/api/params/programmeValidateurs?programmeId=prog"), "{\"ordre\":1,\"title\":\"Vérification région\","
                + "\"userExternalId\":\"u-v1\",\"description\":\"Vérifier le PV\",\"questionIds\":[\"q1\"]}", 201, "admin");
        send(post("/api/params/programmeValidateurs?programmeId=prog"), "{\"ordre\":2,\"title\":\"Vérification technique\","
                + "\"userExternalId\":\"u-v2\"}", 201, "admin");
        send(post("/api/params/programmeValidateurs?programmeId=prog"), "{\"ordre\":2,\"title\":\"Doublon\",\"userExternalId\":\"u-v1\"}", 409, "admin");

        String contrat = JsonPath.read(send(post("/api/params/contrats"), "{\"projectExternalId\":\"proj\",\"title\":\"C-24-001\","
                + "\"typePrestataire\":\"ENTREPRISE\",\"providerExternalId\":\"prov\",\"montantContrat\":1000000}", 201, "admin"), "$.externalId");
        String facture = JsonPath.read(send(post("/api/params/paiements"), "{\"providerExternalId\":\"prov\",\"groupeContratExternalId\":\""
                + contrat + "\",\"numeroFacture\":\"F-1\",\"montant\":400000}", 201, "admin"), "$.externalId");
        // E-mail au 1er validateur du programme.
        assertThat(mails(1)).containsExactly("aina@fdl.mg");
        // L'adresse est relue dans la base à chaque envoi : la nouvelle adresse du validateur 2 sera utilisée.
        mongo.getCollection("user").updateOne(new Document("externalId", "u-v2"),
                new Document("$set", new Document("email", "hery@fdl.mg")));

        // Niveau 0 : seul le validateur d'ordre 1 la voit et peut la traiter.
        assertThat(JsonPath.<List<String>>read(send(get("/api/validations"), "", 200, "u-v2"), "$[*].externalId")).isEmpty();
        send(post("/api/validations/" + facture + "/validate"), "", 403, "u-v2");
        String liste = send(get("/api/validations"), "", 200, "u-v1");
        assertThat(JsonPath.<List<String>>read(liste, "$[*].externalId")).containsExactly(facture);
        assertThat(JsonPath.<List<String>>read(liste, "$[*].projet")).containsExactly("24-001");
        String fiche = send(get("/api/validations/" + facture), "", 200, "u-v1");
        assertThat((Boolean) JsonPath.read(fiche, "$.canValidate")).isTrue();
        assertThat(JsonPath.<List<String>>read(fiche, "$.questions")).containsExactly("PV de réception signé ?");
        assertThat(JsonPath.<String>read(fiche, "$.etape.description")).isEqualTo("Vérifier le PV");
        assertThat((Integer) JsonPath.read(fiche, "$.etapes")).isEqualTo(2);

        send(post("/api/validations/" + facture + "/validate"), "", 204, "u-v1");
        Document f = paiement(facture);
        assertThat(f.getString("status")).isEqualTo("PENDING");
        assertThat(f.get("niveauValidation")).isEqualTo(1);
        assertThat(f.get("statusUserId", Document.class).getString("u-v1")).isEqualTo("VALIDATE");
        assertThat(f.get("dateValidationId", Document.class).getDate("u-v1")).isNotNull();
        send(post("/api/validations/" + facture + "/validate"), "", 403, "u-v1"); // plus son tour
        assertThat(mails(2)).containsExactly("aina@fdl.mg", "hery@fdl.mg"); // validateur suivant prévenu
        String factures = send(get("/api/params/paiements"), "", 200, "admin");
        assertThat(JsonPath.<List<String>>read(factures, "$.rows[?(@.externalId=='" + facture + "')].prochainValidateur"))
                .containsExactly("RABE Hery");
        send(post("/api/params/paiements"), "{}", 400, "admin"); // (contrôle des champs obligatoires)

        // Dernier validateur : VALIDATE, comptée dans le montant payé, verrouillée, plus traitable.
        send(post("/api/validations/" + facture + "/validate"), "", 204, "u-v2");
        f = paiement(facture);
        assertThat(f.getString("status")).isEqualTo("VALIDATE");
        assertThat(f.get("niveauValidation")).isEqualTo(2);
        assertThat(JsonPath.<List<Double>>read(send(get("/api/params/contrats"), "", 200, "admin"),
                "$.rows[?(@.externalId=='" + contrat + "')].montantPayeValide")).containsExactly(400000.0);
        send(post("/api/validations/" + facture + "/validate"), "", 409, "u-v2");
        send(post("/api/validations/" + facture + "/reject"), "", 409, "u-v2");

        // Rejet au premier niveau ; le 1er validateur n'a plus d'adresse : aucun e-mail.
        mongo.getCollection("user").updateOne(new Document("externalId", "u-v1"),
                new Document("$unset", new Document("email", "")));
        String facture2 = JsonPath.read(send(post("/api/params/paiements"), "{\"providerExternalId\":\"prov\",\"groupeContratExternalId\":\""
                + contrat + "\",\"numeroFacture\":\"F-2\",\"montant\":100}", 201, "admin"), "$.externalId");
        send(post("/api/validations/" + facture2 + "/reject"), "", 204, "u-v1");
        assertThat(paiement(facture2).getString("status")).isEqualTo("REJECTED");
        assertThat(paiement(facture2).get("statusUserId", Document.class).getString("u-v1")).isEqualTo("REJECTED");
        assertThat(JsonPath.<List<String>>read(send(get("/api/validations"), "", 200, "u-v2"), "$[*].externalId")).isEmpty();

        // Programme sans validateur : signalé, impossible à valider.
        String contrat2 = JsonPath.read(send(post("/api/params/contrats"), "{\"projectExternalId\":\"proj-vide\",\"title\":\"C-24-002\","
                + "\"providerExternalId\":\"prov\"}", 201, "admin"), "$.externalId");
        String facture3 = JsonPath.read(send(post("/api/params/paiements"), "{\"providerExternalId\":\"prov\",\"groupeContratExternalId\":\""
                + contrat2 + "\",\"numeroFacture\":\"F-3\",\"montant\":5}", 201, "admin"), "$.externalId");
        assertThat(JsonPath.<List<String>>read(send(get("/api/params/paiements"), "", 200, "admin"),
                "$.rows[?(@.externalId=='" + facture3 + "')].prochainValidateur")).containsExactly("Aucun validateur");
        send(post("/api/validations/" + facture3 + "/validate"), "", 409, "u-v1");
        // Aucun autre e-mail : ni à la validation finale, ni au rejet, ni sans adresse, ni sans validateur.
        verify(mailSender, after(1000).times(2)).send(any(SimpleMailMessage.class));

        // Droits : route de l'écran /admin/project/validationPaiements.
        EfdlUserPrincipal sansDroit = new EfdlUserPrincipal("u-x", "x", "X", UserType.ADMINISTRATION, SCHOOL, Set.of(), null);
        mvc.perform(get("/api/validations").with(authentication(UsernamePasswordAuthenticationToken.authenticated(
                sansDroit, null, sansDroit.authorities())))).andExpect(status().isForbidden());
        System.out.println("IDS-POUR-MORPHIA-VALIDATION facture=" + facture + " rejetee=" + facture2);
    }
}
