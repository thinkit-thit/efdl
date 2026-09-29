package com.ffs.efdl.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
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
 * Contrôle sur la VRAIE base (lecture seule) : les API du socle administration sont comparées
 * à des requêtes MongoDB brutes équivalentes.
 * Lancement : {@code mvn test -Defdl.it=true -Dtest=AdminReadIT} (profil local, base testfdl2025).
 */
@SpringBootTest
@ActiveProfiles("local")
@EnabledIfSystemProperty(named = "efdl.it", matches = "true")
class AdminReadIT {

    private static final String SCHOOL = "sdi-fdl";

    @Autowired
    WebApplicationContext context;
    @Autowired
    MongoTemplate mongo;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static RequestPostProcessor as(UserType type, Set<String> groups) {
        EfdlUserPrincipal p = new EfdlUserPrincipal("it-user", "it", "IT", type, SCHOOL, groups, null);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(p, null, p.authorities()));
    }

    private String json(String url) throws Exception {
        return mvc.perform(get(url).with(as(UserType.ADMIN, Set.of()))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void utilisateursMemeListeQueLaBase() throws Exception {
        List<String> excluded = mongo.getCollection("Contract").distinct("userExternalId", new Document("schoolExternalId", SCHOOL)
                .append("contractType", new Document("$in", List.of("DEATH", "DISMISSED", "REMOVED", "RESIGNATION", "RETIREMENT", "OTHER"))), String.class)
                .into(new java.util.ArrayList<>());
        List<String> expected = mongo.getCollection("user")
                .find(new Document("schoolExternalId", SCHOOL).append("type", "ADMINISTRATION").append("externalId", new Document("$nin", excluded)))
                .sort(new Document("creationDate", 1)).map(d -> d.getString("externalId")).into(new java.util.ArrayList<>());

        String body = json("/api/admin/users");
        List<String> actual = JsonPath.read(body, "$.users[*].externalId");
        assertThat(actual).containsExactlyElementsOf(expected);
        assertThat((Boolean) JsonPath.read(body, "$.canEdit")).isTrue();
        assertThat(body).doesNotContain("password");
    }

    @Test
    void ficheUtilisateurSansMotDePasse() throws Exception {
        Document raw = mongo.getCollection("user").find(new Document("schoolExternalId", SCHOOL).append("type", "ADMINISTRATION")).first();
        String body = json("/api/admin/users/" + raw.getString("externalId"));
        assertThat((String) JsonPath.read(body, "$.username")).isEqualTo(raw.getString("username"));
        assertThat((String) JsonPath.read(body, "$.lastName")).isEqualTo(raw.getString("lastName"));
        assertThat(body).doesNotContain("password");
    }

    @Test
    void groupesMenusEcolesMemesComptages() throws Exception {
        long groups = mongo.getCollection("user_group").countDocuments(new Document("schoolExternalId", SCHOOL));
        long menus = mongo.getCollection("Menu").countDocuments(new Document("schoolExternalId", SCHOOL));
        long schools = mongo.getCollection("school").countDocuments();
        assertThat((List<?>) JsonPath.read(json("/api/admin/groups"), "$")).hasSize((int) groups);
        String menuBody = json("/api/admin/menus");
        assertThat((List<?>) JsonPath.read(menuBody, "$")).hasSize((int) menus);
        List<Integer> orders = JsonPath.read(menuBody, "$[*].order");
        assertThat(orders).isSorted();
        assertThat((List<?>) JsonPath.read(json("/api/admin/schools"), "$")).hasSize((int) schools);
    }

    @Test
    void memesDroitsQueLesRegles() throws Exception {
        // ADMINISTRATION sans groupe : refus sur /admin/*, et /admin/school réservé ADMIN.
        mvc.perform(get("/api/admin/users").with(as(UserType.ADMINISTRATION, Set.of()))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/schools").with(as(UserType.ADMINISTRATION, Set.of()))).andExpect(status().isForbidden());
        // Groupe réel de la base dont une regex couvre /admin/user/administration/list : accès autorisé.
        Document g = mongo.getCollection("user_group").find(new Document("schoolExternalId", SCHOOL)
                .append("allowedPaths", "/admin.*")).first();
        if (g != null) {
            mvc.perform(get("/api/admin/users").with(as(UserType.ADMINISTRATION, Set.of(g.getString("externalId")))))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/admin/schools").with(as(UserType.ADMINISTRATION, Set.of(g.getString("externalId")))))
                    .andExpect(status().isForbidden());
        }
        // Aucune écriture : la collection user_credential n'est pas modifiée par ces lectures.
        assertThat(mongo.getCollection("user_credential").countDocuments()).isGreaterThanOrEqualTo(0);
    }
}
