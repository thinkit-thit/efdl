package com.ffs.efdl.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.ffs.efdl.model.UserType;
import com.ffs.efdl.params.ParamDefinitions;
import com.ffs.efdl.params.ParamModel.ParamDef;
import com.ffs.efdl.params.ParamModel.ScopeDef;
import com.ffs.efdl.security.EfdlUserPrincipal;
import com.jayway.jsonpath.JsonPath;

/**
 * Contrôle en LECTURE SEULE sur la base réelle de développement (profil local, {@code testfdl2025}) :
 * chaque écran « Paramètre » renvoie tous les documents de l'école, sans champ sensible.
 * Lancement : {@code mvn test -Defdl.it=true -Dtest=ParamReadIT}.
 */
@SpringBootTest
@ActiveProfiles("local")
@EnabledIfSystemProperty(named = "efdl.it", matches = "true")
class ParamReadIT {

    static final String SCHOOL = "sdi-fdl";

    @Autowired WebApplicationContext context;
    @Autowired MongoTemplate mongo;
    @Autowired ParamDefinitions definitions;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void chaqueEcranRenvoieTousLesDocumentsSansChampSensible() throws Exception {
        assertThat(mongo.getDb().getName()).isNotEqualTo("efdl_it");
        EfdlUserPrincipal admin = new EfdlUserPrincipal("lecture", "lecture", "Lecture", UserType.ADMIN, SCHOOL, Set.of(), null);
        for (ParamDef def : definitions.all()) {
            for (Map<String, String> scope : scopes(def)) {
                check(def, scope, admin);
            }
        }
    }

    /**
     * Combinaisons de parents d'URL à parcourir : une par document du dernier parent (ex. chaque activité PTA),
     * les parents précédents étant lus dans ce document.
     */
    private List<Map<String, String>> scopes(ParamDef def) {
        if (def.scope().isEmpty()) return List.of(Map.of());
        ScopeDef last = def.scope().get(def.scope().size() - 1);
        List<Map<String, String>> result = new java.util.ArrayList<>();
        for (Document parent : mongo.getCollection(last.collection())
                .find(new Document("schoolExternalId", SCHOOL).append("status", new Document("$ne", "DELETED")))) {
            Map<String, String> scope = new java.util.LinkedHashMap<>();
            for (int i = 0; i < def.scope().size() - 1; i++) {
                // Le dernier parent porte les précédents (ex. l'activité a son ptaFinancementExternalId).
                scope.put(def.scope().get(i).var(), parent.getString(def.scope().get(i).field()));
            }
            scope.put(last.var(), parent.getString(last.matchField())); // externalId, ou code pour un district
            result.add(scope);
        }
        return result;
    }

    private void check(ParamDef def, Map<String, String> scope, EfdlUserPrincipal admin) throws Exception {
        {
            var request = get("/api/params/" + def.key());
            scope.forEach((k, v) -> request.param(k, v));
            String json = mvc.perform(request
                            .with(authentication(UsernamePasswordAuthenticationToken.authenticated(admin, null, admin.authorities()))))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            Document filter = new Document("schoolExternalId", SCHOOL).append("status", new Document("$ne", "DELETED"));
            for (ScopeDef s : def.scope()) {
                String value = scope.get(s.var());
                if (!"externalId".equals(s.matchField())) {
                    value = mongo.getCollection(s.collection()).find(new Document(s.matchField(), value)
                            .append("schoolExternalId", SCHOOL)).first().getString("externalId");
                }
                filter.append(s.field(), value);
            }
            if (definitions.listFilter(def.key()) != null) filter.putAll(definitions.listFilter(def.key()));
            long expected = mongo.getCollection(def.collection()).countDocuments(filter);
            List<Map<String, Object>> rows = JsonPath.read(json, "$.rows");
            System.out.printf("PARITE %-20s %-60s base=%5d api=%5d%n", def.key(), scope.values(), expected, rows.size());
            assertThat(rows).as(def.key()).hasSize((int) expected);
            assertThat(json).as(def.key()).doesNotContain("contactPassword").doesNotContain("\"bv\"").doesNotContain("\"password\"")
                    .doesNotContain("avancementMap");
            // Chaque référence non vide doit se résoudre en libellé (sinon la colonne serait vide à tort).
            Map<String, List<Map<String, Object>>> refs = JsonPath.read(json, "$.refs");
            def.fields().stream().filter(f -> f.ref() != null && f.type() == com.ffs.efdl.params.ParamModel.FieldType.REF).forEach(f -> {
                long unresolved = rows.stream().map(r -> r.get(f.name())).filter(v -> v != null && !"".equals(v))
                        .filter(v -> refs.get(f.ref()).stream().noneMatch(c -> v.equals(c.get("id")))).count();
                System.out.printf("         %-24s références non résolues : %d%n", f.name(), unresolved);
            });
        }
    }
}
