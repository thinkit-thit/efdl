package com.ffs.efdl.security;

import static com.ffs.efdl.TestData.with;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ffs.efdl.model.UserGroup;
import com.ffs.efdl.model.UserType;
import com.ffs.efdl.repository.UserGroupRepository;

/** Règles d'accès : isAuthorizedPath, filtres /admin/* et /admin/school/*. */
class PermissionServiceTest {

    private PermissionService service;

    @BeforeEach
    void setUp() {
        UserGroupRepository repo = mock(UserGroupRepository.class);
        UserGroup projets = with(new UserGroup(), Map.of("externalId", "g1", "name", "Projets",
                "allowedPaths", Set.of("/admin/project/.*", "/home")));
        when(repo.findByExternalIdIn(any())).thenAnswer(inv -> {
            Set<?> ids = Set.copyOf(inv.getArgument(0));
            return ids.contains("g1") ? List.of(projets) : List.of();
        });
        service = new PermissionService(repo);
    }

    private static EfdlUserPrincipal user(UserType type, String... groups) {
        return new EfdlUserPrincipal("u1", "jdoe", "DOE John", type, "s1", Set.of(groups), null);
    }

    @Test
    void adminEstToujoursAutorise() {
        assertThat(service.isAuthorizedPath(user(UserType.ADMIN), "/admin/pta/x")).isTrue();
        assertThat(service.canAccessRoute(user(UserType.ADMIN), "/admin/school/list")).isTrue();
    }

    @Test
    void regexDoitCorrespondreALaRouteEntiere() {
        EfdlUserPrincipal u = user(UserType.ADMINISTRATION, "g1");
        assertThat(service.isAuthorizedPath(u, "/admin/project/programmes")).isTrue();
        assertThat(service.isAuthorizedPath(u, "/admin/pta/ptaFinancements")).isFalse();
        assertThat(service.isAuthorizedPath(u, "/home/x")).isFalse(); // matches() et non find()
    }

    @Test
    void sansGroupeAucunAcces() {
        assertThat(service.isAuthorizedPath(user(UserType.ADMINISTRATION), "/home")).isFalse();
    }

    @Test
    void reglesDesFiltresAdmin() {
        assertThat(service.canAccessRoute(user(UserType.ADMINISTRATION, "g1"), "/admin/project/programmes")).isTrue();
        assertThat(service.canAccessRoute(user(UserType.ADMINISTRATION, "g1"), "/admin/menus")).isFalse();
        assertThat(service.canAccessRoute(user(UserType.CANDIDAT, "g1"), "/admin/project/programmes")).isFalse();
        assertThat(service.canAccessRoute(user(UserType.ADMINISTRATION, "g1"), "/admin/school/list")).isFalse();
        assertThat(service.canAccessRoute(user(UserType.CANDIDAT), "/profile/x")).isTrue();
    }
}
