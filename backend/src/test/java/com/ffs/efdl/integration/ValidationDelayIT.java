package com.ffs.efdl.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

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
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.ffs.efdl.model.UserType;
import com.ffs.efdl.security.EfdlUserPrincipal;
import com.ffs.efdl.validation.ValidationDelayNotifier;
import com.jayway.jsonpath.JsonPath;

/**
 * Retards de validation : attente depuis la dernière validation (ou la saisie), délai « Nb jour de traitement max »
 * de l'étape, affichage et e-mail unique au validateur. Base de TEST jetable {@code efdl_it}.
 */
@SpringBootTest(properties = { "spring.mongodb.database=efdl_it", "efdl.mail.from=efdl@test.mg" })
@ActiveProfiles("local")
@EnabledIfSystemProperty(named = "efdl.it", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ValidationDelayIT {

    static final String SCHOOL = "sdi-fdl";
    static final String P = "com.ffs.kiwi.model.project.";
    static final long DAY = 86_400_000L;

    @Autowired WebApplicationContext context;
    @Autowired MongoTemplate mongo;
    @Autowired ValidationDelayNotifier notifier;
    @MockitoBean JavaMailSender mailSender;
    MockMvc mvc;

    @BeforeAll
    void seed() {
        assertThat(mongo.getDb().getName()).isEqualTo("efdl_it"); // garde-fou : jamais la base réelle
        mongo.getDb().drop();
        Date now = new Date();
        insert("school", "com.ffs.kiwi.model.School", SCHOOL, new Document("name", "sdi-fdl"), false, now);
        insert("user", "com.ffs.kiwi.model.user.User", "u-v1", new Document("username", "val1").append("lastName", "RAKOTO")
                .append("email", "v1@fdl.mg").append("type", "ADMINISTRATION"), true, now);
        insert("user", "com.ffs.kiwi.model.user.User", "u-v2", new Document("username", "val2").append("lastName", "RABE")
                .append("email", "v2@fdl.mg").append("type", "ADMINISTRATION"), true, now);
        insert("Validateur", P + "Validateur", "val-1", new Document("programmeExternalId", "prog").append("ordre", 1)
                .append("userExternalId", "u-v1").append("maxJourTraitement", 2), true, now);
        insert("Validateur", P + "Validateur", "val-2", new Document("programmeExternalId", "prog").append("ordre", 2)
                .append("userExternalId", "u-v2").append("maxJourTraitement", 0), true, now);
        insert("Contrat", P + "Contrat", "c1", new Document("groupeContratExternalId", "g1").append("programmeExternalId", "prog"), true, now);
        // Au 1er validateur depuis 5 jours (délai 2) : en retard.
        insert("Paiement", P + "Paiement", "retard", new Document("numeroFacture", "F-1").append("status", "PENDING")
                .append("groupeContratExternalId", "g1").append("niveauValidation", 0).append("montant", 1500000.0), true,
                new Date(now.getTime() - 5 * DAY));
        // Au 1er validateur depuis 1 jour : dans les temps.
        insert("Paiement", P + "Paiement", "ok", new Document("numeroFacture", "F-2").append("status", "PENDING")
                .append("groupeContratExternalId", "g1").append("niveauValidation", 0), true, new Date(now.getTime() - DAY));
        // Validée par le 1er il y a 10 jours, au 2e (sans délai) : jamais en retard.
        insert("Paiement", P + "Paiement", "sans-delai", new Document("numeroFacture", "F-3").append("status", "PENDING")
                .append("groupeContratExternalId", "g1").append("niveauValidation", 1)
                .append("dateValidationId", new Document("u-v1", new Date(now.getTime() - 10 * DAY))), true,
                new Date(now.getTime() - 20 * DAY));
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private void insert(String collection, String className, String id, Document fields, boolean schoolScoped, Date created) {
        Document d = new Document("_id", new ObjectId()).append("className", className);
        d.putAll(fields);
        if (schoolScoped) d.append("schoolExternalId", SCHOOL).append("orderPlace", 0);
        d.append("externalId", id).append("creationDate", created).append("lastUpdateDate", created);
        mongo.getCollection(collection).insertOne(d);
    }

    private static RequestPostProcessor as(String id, UserType type) {
        EfdlUserPrincipal p = new EfdlUserPrincipal(id, id, id, type, SCHOOL, Set.of(), null);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(p, null, p.authorities()));
    }

    private String json(String url, RequestPostProcessor who) throws Exception {
        return mvc.perform(get(url).with(who)).andReturn().getResponse().getContentAsString();
    }

    @Test
    void retardsAfficheesEtNotifiesUneFois() throws Exception {
        // Liste des factures : attente, délai, retard.
        String list = json("/api/params/paiements", as("admin", UserType.ADMIN));
        assertThat(JsonPath.<List<Boolean>>read(list, "$.rows[?(@.externalId=='retard')].enRetard")).containsExactly(true);
        assertThat(JsonPath.<List<Integer>>read(list, "$.rows[?(@.externalId=='retard')].attenteJours")).containsExactly(5);
        assertThat(JsonPath.<List<Integer>>read(list, "$.rows[?(@.externalId=='retard')].delaiMax")).containsExactly(2);
        assertThat(JsonPath.<List<Boolean>>read(list, "$.rows[?(@.externalId=='ok')].enRetard")).containsExactly(false);
        // Attente comptée depuis la dernière validation (10 jours), étape sans délai.
        assertThat(JsonPath.<List<Integer>>read(list, "$.rows[?(@.externalId=='sans-delai')].attenteJours")).containsExactly(10);
        assertThat(JsonPath.<List<Boolean>>read(list, "$.rows[?(@.externalId=='sans-delai')].enRetard")).containsExactly(false);
        // Liste « Validation Paiement » du validateur.
        String mine = json("/api/validations", as("u-v1", UserType.ADMIN));
        assertThat(JsonPath.<List<Boolean>>read(mine, "$[?(@.externalId=='retard')].enRetard")).containsExactly(true);

        // Un seul e-mail, au validateur de l'étape en retard ; pas de second envoi.
        assertThat(notifier.notifyLate(new Date())).isEqualTo(1);
        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, timeout(5000).times(1)).send(sent.capture());
        assertThat(sent.getValue().getTo()).containsExactly("v1@fdl.mg");
        assertThat(sent.getValue().getSubject()).isEqualTo("VALIDATION DE PAIEMENT EN RETARD");
        assertThat(sent.getValue().getText()).contains("F-1", "depuis 5 jours", "est de 2 jours", "/admin/project/validationPaiement/edit/retard");
        assertThat(notifier.notifyLate(new Date())).isZero();
        verify(mailSender, after(500).times(1)).send(any(SimpleMailMessage.class));
    }
}
