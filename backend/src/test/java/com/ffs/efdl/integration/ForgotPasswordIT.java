package com.ffs.efdl.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * « Mot de passe oublié » par code e-mail (serveur SMTP simulé), sur la base de TEST jetable {@code efdl_it}.
 * Lancement : {@code mvn test -Defdl.it=true -Dtest=ForgotPasswordIT}.
 */
@SpringBootTest(properties = { "spring.mongodb.database=efdl_it", "efdl.mail.from=efdl@test.mg" })
@ActiveProfiles("local")
@EnabledIfSystemProperty(named = "efdl.it", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ForgotPasswordIT {

    static final String SCHOOL = "sdi-fdl";

    @Autowired WebApplicationContext context;
    @Autowired MongoTemplate mongo;
    @MockitoBean JavaMailSender mailSender;
    MockMvc mvc;

    @BeforeAll
    void seed() {
        assertThat(mongo.getDb().getName()).isEqualTo("efdl_it"); // garde-fou : jamais la base réelle
        mongo.getDb().drop();
        insert("school", new Document("name", "sdi-fdl").append("domainName", "localhost:8080"), SCHOOL, false);
        insert("user", new Document("username", "rakoto01").append("lastName", "RAKOTO").append("firstName", "Aina")
                .append("email", "aina@fdl.mg").append("phone", "0340000001").append("type", "ADMINISTRATION"), "u1", true);
        insert("user", new Document("username", "sansmail").append("type", "ADMINISTRATION"), "u2", true);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private void insert(String collection, Document fields, String id, boolean schoolScoped) {
        Document d = new Document("_id", new ObjectId()).append("className", "com.ffs.kiwi.model." + (collection.equals("user") ? "user.User" : "School"));
        d.putAll(fields);
        if (schoolScoped) d.append("schoolExternalId", SCHOOL).append("orderPlace", 0);
        d.append("externalId", id).append("creationDate", new Date()).append("lastUpdateDate", new Date());
        mongo.getCollection(collection).insertOne(d);
    }

    private int call(String url, String json) throws Exception {
        return mvc.perform(post(url).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json))
                .andReturn().getResponse().getStatus();
    }

    /** Code à 6 chiffres du dernier e-mail envoyé. */
    private String lastCode() {
        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, org.mockito.Mockito.atLeastOnce()).send(sent.capture());
        SimpleMailMessage message = sent.getValue();
        assertThat(message.getTo()).containsExactly("aina@fdl.mg");
        assertThat(message.getFrom()).isEqualTo("efdl@test.mg");
        Matcher m = Pattern.compile("est : (\\d{6})").matcher(message.getText());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    @Test
    void parcoursComplet() throws Exception {
        String url = "/api/auth/password-reset/";
        // Réponse identique, aucun e-mail : compte inconnu ou sans adresse.
        assertThat(call(url + "request", "{\"identifier\":\"inconnu\"}")).isEqualTo(202);
        assertThat(call(url + "request", "{\"identifier\":\"sansmail\"}")).isEqualTo(202);
        verify(mailSender, never()).send(any(SimpleMailMessage.class));

        // Identifiant = téléphone (comme la connexion) ; le code n'est conservé que haché.
        assertThat(call(url + "request", "{\"identifier\":\"0340000001\"}")).isEqualTo(202);
        String code = lastCode();
        Document reset = mongo.getCollection("password_reset").find(new Document("userExternalId", "u1")).first();
        assertThat(reset.getString("codeHash")).startsWith("$2").doesNotContain(code);

        assertThat(call(url + "confirm", "{\"identifier\":\"rakoto01\",\"code\":\"000000\",\"newPassword\":\"Nouveau2026\"}")).isEqualTo(400);
        assertThat(call(url + "confirm", "{\"identifier\":\"rakoto01\",\"code\":\"" + code + "\",\"newPassword\":\"faible\"}")).isEqualTo(400);
        assertThat(call(url + "confirm", "{\"identifier\":\"rakoto01\",\"code\":\"" + code + "\",\"newPassword\":\"Nouveau2026\"}")).isEqualTo(204);
        assertThat(call("/api/auth/login", "{\"identifier\":\"rakoto01\",\"password\":\"Nouveau2026\"}")).isEqualTo(200);
        // Connexion enregistrée dans l'historique (collection history).
        Document login = mongo.getCollection("history").find(new Document("userId", "u1")).first();
        assertThat(login.getString("className")).isEqualTo("com.ffs.kiwi.model.notification.History");
        assertThat(login.getString("historyType")).isEqualTo("CONNECTION");
        assertThat(login.getString("connectionType")).isEqualTo("LOGIN");
        assertThat(login.getString("schoolExternalId")).isEqualTo(SCHOOL);
        assertThat(mongo.getCollection("user").find(new Document("externalId", "u1")).first().containsKey("password")).isFalse();
        // Code à usage unique.
        assertThat(call(url + "confirm", "{\"identifier\":\"rakoto01\",\"code\":\"" + code + "\",\"newPassword\":\"Autre2026x\"}")).isEqualTo(400);

        // Une nouvelle demande annule la précédente.
        clearInvocations(mailSender);
        assertThat(call(url + "request", "{\"identifier\":\"aina@fdl.mg\"}")).isEqualTo(202);
        String first = lastCode();
        clearInvocations(mailSender);
        assertThat(call(url + "request", "{\"identifier\":\"rakoto01\"}")).isEqualTo(202);
        String second = lastCode();
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
        if (!first.equals(second)) {
            assertThat(call(url + "confirm", "{\"identifier\":\"rakoto01\",\"code\":\"" + first + "\",\"newPassword\":\"Autre2026x\"}")).isEqualTo(400);
        }
        // Essais limités : au-delà, même le bon code est refusé (puis l'adresse est bloquée).
        for (int i = 0; i < 4; i++) {
            call(url + "confirm", "{\"identifier\":\"rakoto01\",\"code\":\"999999\",\"newPassword\":\"Autre2026x\"}");
        }
        int blocked = call(url + "confirm", "{\"identifier\":\"rakoto01\",\"code\":\"" + second + "\",\"newPassword\":\"Autre2026x\"}");
        assertThat(blocked).isIn(400, 429);
        assertThat(call("/api/auth/login", "{\"identifier\":\"rakoto01\",\"password\":\"Autre2026x\"}")).isEqualTo(401);
    }
}
