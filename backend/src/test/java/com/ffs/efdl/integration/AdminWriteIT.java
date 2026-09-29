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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.ffs.efdl.model.UserType;
import com.ffs.efdl.security.EfdlUserPrincipal;
import com.jayway.jsonpath.JsonPath;

/**
 * Écritures du socle administration sur une base de TEST dédiée ({@code efdl_it}), jamais sur les données réelles.
 * Vérifie la forme des documents créés et que les modifications ne touchent que les champs de l'écran.
 * Lancement : {@code mvn test -Defdl.it=true -Dtest=AdminWriteIT}.
 */
@SpringBootTest(properties = "spring.mongodb.database=efdl_it")
@ActiveProfiles("local")
@EnabledIfSystemProperty(named = "efdl.it", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdminWriteIT {

    static final String SCHOOL = "sdi-fdl";

    @Autowired WebApplicationContext context;
    @Autowired MongoTemplate mongo;
    MockMvc mvc;

    @BeforeAll
    void seed() {
        assertThat(mongo.getDb().getName()).isEqualTo("efdl_it"); // garde-fou : jamais la base réelle
        mongo.getDb().drop();
        mongo.getCollection("school").insertOne(new Document("_id", new ObjectId()).append("className", "com.ffs.kiwi.model.School")
                .append("name", "sdi-fdl").append("domainName", "localhost:8080").append("signupActive", false)
                .append("externalId", SCHOOL).append("creationDate", new Date()).append("lastUpdateDate", new Date()));
        // Utilisateur existant avec des champs NON gérés par l'écran : ils doivent survivre à une modification.
        mongo.getCollection("user").insertOne(new Document("_id", new ObjectId()).append("className", "com.ffs.kiwi.model.user.User")
                .append("username", "exist01").append("lastName", "EXISTANT").append("type", "ADMINISTRATION")
                .append("password", "secret-stocke").append("religion", "champ-hors-ecran").append("nomAgence1", "BOA")
                .append("siege", 0).append("voix", 0).append("order", 0).append("cadreSup", false).append("amount", 0.0)
                .append("schoolExternalId", SCHOOL).append("orderPlace", 0).append("externalId", "it-user-existant")
                .append("creationDate", new Date()).append("lastUpdateDate", new Date()));
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static RequestPostProcessor admin() {
        EfdlUserPrincipal p = new EfdlUserPrincipal("it-admin", "admin", "IT", UserType.ADMIN, SCHOOL, Set.of(), null);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(p, null, p.authorities()));
    }

    private String send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req, String json, int expected) throws Exception {
        return mvc.perform(req.with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
    }

    private Document raw(String collection, String externalId) {
        return mongo.getCollection(collection).find(new Document("externalId", externalId)).first();
    }

    static String groupId, menuParentId, menuChildId, schoolId, userId;

    @Test @Order(1)
    void creationGroupeFormeDocument() throws Exception {
        groupId = JsonPath.read(send(post("/api/admin/groups"), "{\"name\":\"IT groupe\",\"allowedPaths\":[\"/admin/menus\",\" /admin/menu/.* \",\"\",\"/admin/menus\"]}", 201), "$.externalId");
        Document d = raw("user_group", groupId);
        assertThat(d.getString("className")).isEqualTo("com.ffs.kiwi.model.user.group.UserGroup");
        assertThat(d.get("_id")).isInstanceOf(ObjectId.class);
        assertThat(d.getString("schoolExternalId")).isEqualTo(SCHOOL);
        assertThat(d.get("orderPlace")).isEqualTo(0);
        assertThat(d.getList("allowedPaths", String.class)).containsExactly("/admin/menus", "/admin/menu/.*");
        assertThat(d.get("creationDate")).isInstanceOf(Date.class);
        assertThat(d.containsKey("_class")).isFalse();
    }

    @Test @Order(2)
    void menusParentEtEnfant() throws Exception {
        menuParentId = JsonPath.read(send(post("/api/admin/menus"), "{\"title\":\"IT parent\",\"icon\":\"home\",\"order\":5,\"url\":\"/admin/menus\",\"path\":\"it\"}", 201), "$.externalId");
        menuChildId = JsonPath.read(send(post("/api/admin/menus"), "{\"title\":\"IT enfant\",\"parentId\":\"" + menuParentId + "\",\"order\":1,\"url\":\"/admin/menu/edit/x\"}", 201), "$.externalId");
        Document parent = raw("Menu", menuParentId);
        assertThat(parent.containsKey("parentId")).isFalse(); // null non écrit
        assertThat(parent.get("order")).isEqualTo(5);
        assertThat(raw("Menu", menuChildId).getString("parentId")).isEqualTo(menuParentId);
        send(post("/api/admin/menus"), "{\"title\":\"x\",\"parentId\":\"inconnu\",\"order\":1}", 400);
        // Modification : le parent est retiré (null → $unset), le titre change.
        send(put("/api/admin/menus/" + menuChildId), "{\"title\":\"IT enfant modifié\",\"parentId\":\"\",\"order\":2,\"url\":\"/admin/menu/edit/x\"}", 200);
        Document child = raw("Menu", menuChildId);
        assertThat(child.getString("title")).isEqualTo("IT enfant modifié");
        assertThat(child.containsKey("parentId")).isFalse();
    }

    @Test @Order(3)
    void applicationIdentifiantNormalise() throws Exception {
        schoolId = JsonPath.read(send(post("/api/admin/schools"), "{\"name\":\"Écolé Test  IT\",\"domainName\":\"it.local\",\"years\":\"2026\"}", 201), "$.externalId");
        assertThat(schoolId).isEqualTo("ecole-test-it"); // IdUtils.normalize
        Document d = raw("school", schoolId);
        assertThat(d.getString("className")).isEqualTo("com.ffs.kiwi.model.School");
        assertThat(d.containsKey("schoolExternalId")).isFalse();
        assertThat(d.containsKey("orderPlace")).isFalse();
        assertThat(d.getBoolean("signupActive")).isFalse();
        send(post("/api/admin/schools"), "{\"name\":\"Ecole test IT\"}", 409); // même identifiant
    }

    @Test @Order(4)
    void utilisateurCreationEtModificationSansPerteDeChamps() throws Exception {
        userId = JsonPath.read(send(post("/api/admin/users"), "{\"username\":\"it-nouveau\",\"lastName\":\"NOUVEAU\",\"firstName\":\"Test\",\"gender\":\"FEMALE\",\"birthDate\":\"1990-05-17\",\"userGroupIds\":[\"" + groupId + "\"]}", 201), "$.externalId");
        Document created = raw("user", userId);
        assertThat(created.getString("type")).isEqualTo("ADMINISTRATION");
        assertThat(created.getString("className")).isEqualTo("com.ffs.kiwi.model.user.User");
        assertThat(created.get("siege")).isEqualTo(0);
        assertThat(created.get("amount")).isEqualTo(0.0);
        assertThat(created.get("birthDate")).isInstanceOf(Date.class);
        assertThat(created.containsKey("password")).isFalse();
        assertThat(created.getList("userGroupIds", String.class)).containsExactly(groupId);

        send(post("/api/admin/users"), "{\"username\":\"exist01\"}", 409); // matricule déjà pris
        send(post("/api/admin/users"), "{\"username\":\"x-groupe\",\"userGroupIds\":[\"inconnu\"]}", 400);

        send(put("/api/admin/users/it-user-existant"), "{\"username\":\"exist01\",\"lastName\":\"MODIFIE\",\"userGroupIds\":[\"" + groupId + "\"]}", 200);
        Document updated = raw("user", "it-user-existant");
        assertThat(updated.getString("lastName")).isEqualTo("MODIFIE");
        assertThat(updated.getString("religion")).isEqualTo("champ-hors-ecran"); // champ hors écran conservé
        assertThat(updated.getString("nomAgence1")).isEqualTo("BOA");
        assertThat(updated.getString("password")).isEqualTo("secret-stocke"); // mot de passe jamais touché
        assertThat(updated.getString("lastUpdateUserId")).isEqualTo("it-admin");
        assertThat(updated.getList("userGroupIds", String.class)).containsExactly(groupId);
    }

    @Test @Order(5)
    void droitsEnEcriture() throws Exception {
        EfdlUserPrincipal p = new EfdlUserPrincipal("it-x", "x", "X", UserType.ADMINISTRATION, SCHOOL, Set.of(groupId), null);
        RequestPostProcessor limited = authentication(UsernamePasswordAuthenticationToken.authenticated(p, null, p.authorities()));
        // Le groupe IT n'autorise que /admin/menus et /admin/menu/.* : menus oui, groupes/utilisateurs/applications non.
        mvc.perform(post("/api/admin/menus").with(limited).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"ok\",\"order\":1}")).andExpect(status().isCreated());
        mvc.perform(post("/api/admin/groups").with(limited).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"refus\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/schools").with(limited).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"refus\"}")).andExpect(status().isForbidden());
        // Sans jeton CSRF : refus.
        mvc.perform(post("/api/admin/menus").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"x\",\"order\":1}")).andExpect(status().isForbidden());
        // Écrans d'édition : fiche vierge avec les groupes de l'école, choix du parent sans le menu lui-même.
        String blank = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/admin/users/new").with(admin()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(blank, "$.groups[*].externalId")).contains(groupId);
        String parents = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/admin/menus/" + menuParentId + "/parents").with(admin()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(parents, "$[*].externalId")).contains(menuChildId).doesNotContain(menuParentId);
        System.out.println("IDS-POUR-MORPHIA group=" + groupId + " menuParent=" + menuParentId + " menuChild=" + menuChildId
                + " school=" + schoolId + " user=" + userId + " userExistant=it-user-existant");
        assertThat(List.of(groupId, menuParentId, menuChildId, schoolId, userId)).doesNotContainNull();
    }

    @Test @Order(6)
    void reinitialisationPuisChangementObligatoire() throws Exception {
        send(post("/api/admin/users/it-user-existant/password"), "{\"password\":\"court1\"}", 400);
        send(post("/api/admin/users/it-user-existant/password"), "{\"password\":\"Provisoire1\"}", 204);
        Document credential = mongo.getCollection("user_credential").find(new Document("userExternalId", "it-user-existant")).first();
        assertThat(credential.getBoolean("mustChangePassword")).isTrue();
        assertThat(credential.getString("passwordHash")).startsWith("$2");

        // Le champ user.password en clair ne sert pas.
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"exist01\",\"password\":\"secret-stocke\"}")).andExpect(status().isUnauthorized());

        MvcResult login = mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"exist01\",\"password\":\"Provisoire1\"}")).andExpect(status().isOk()).andReturn();
        assertThat((Boolean) JsonPath.read(login.getResponse().getContentAsString(), "$.mustChangePassword")).isTrue();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        // Tant que le mot de passe n'est pas changé : tout le reste de l'API est refusé côté serveur.
        mvc.perform(get("/api/admin/menus").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/menus/me").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/password").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"faux\",\"newPassword\":\"Nouveau2026\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/password").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"Provisoire1\",\"newPassword\":\"Nouveau2026\"}")).andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/menus").session(session)).andExpect(status().isOk());
        String me = mvc.perform(get("/api/auth/me").session(session)).andReturn().getResponse().getContentAsString();
        assertThat((Boolean) JsonPath.read(me, "$.mustChangePassword")).isFalse();

        credential = mongo.getCollection("user_credential").find(new Document("userExternalId", "it-user-existant")).first();
        assertThat(credential.getBoolean("mustChangePassword")).isFalse();
        assertThat(raw("user", "it-user-existant").getString("password")).isEqualTo("secret-stocke"); // jamais modifié
    }

    @Test @Order(7)
    void suppressionsLogiques() throws Exception {
        // Menus : refus tant qu'il reste des sous-menus, puis status=DELETED (document conservé).
        send(put("/api/admin/menus/" + menuChildId), "{\"title\":\"IT enfant\",\"parentId\":\"" + menuParentId + "\",\"order\":1}", 200);
        send(delete("/api/admin/menus/" + menuParentId), "", 409);
        send(delete("/api/admin/menus/" + menuChildId), "", 204);
        assertThat(raw("Menu", menuChildId).getString("status")).isEqualTo("DELETED");
        assertThat(raw("Menu", menuChildId).getString("title")).isEqualTo("IT enfant");
        send(get("/api/admin/menus/" + menuChildId), "", 404);
        send(put("/api/admin/menus/" + menuChildId), "{\"title\":\"x\",\"order\":1}", 404);
        send(delete("/api/admin/menus/" + menuChildId), "", 404);
        assertThat(JsonPath.<List<String>>read(send(get("/api/admin/menus"), "", 200), "$[*].externalId")).doesNotContain(menuChildId);
        send(delete("/api/admin/menus/" + menuParentId), "", 204);

        // Utilisateurs : jamais son propre compte ; le supprimé disparaît de la liste et de la fiche.
        send(delete("/api/admin/users/it-admin"), "", 409);
        send(delete("/api/admin/users/" + userId), "", 204);
        assertThat(raw("user", userId).getString("status")).isEqualTo("DELETED");
        send(get("/api/admin/users/" + userId), "", 404);
        assertThat(JsonPath.<List<String>>read(send(get("/api/admin/users"), "", 200), "$.users[*].externalId")).doesNotContain(userId);
        send(post("/api/admin/users/" + userId + "/password"), "{\"password\":\"Provisoire1\"}", 404);

        // Applications : pas celle en cours d'utilisation ; l'identifiant reste réservé.
        send(delete("/api/admin/schools/" + SCHOOL), "", 409);
        send(delete("/api/admin/schools/" + schoolId), "", 204);
        assertThat(raw("school", schoolId).getString("status")).isEqualTo("DELETED");
        send(get("/api/admin/schools/" + schoolId), "", 404);
        send(post("/api/admin/schools"), "{\"name\":\"Ecole test IT\"}", 409);
    }
}
