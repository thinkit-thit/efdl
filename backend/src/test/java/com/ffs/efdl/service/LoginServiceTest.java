package com.ffs.efdl.service;

import static com.ffs.efdl.TestData.with;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.ffs.efdl.config.EfdlProperties;
import com.ffs.efdl.model.User;
import com.ffs.efdl.model.UserCredential;
import com.ffs.efdl.model.UserType;
import com.ffs.efdl.repository.UserCredentialRepository;
import com.ffs.efdl.repository.UserRepository;

class LoginServiceTest {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private UserRepository users;
    private UserCredentialRepository credentials;
    private LoginService service;

    private final User jdoe = with(new User(), Map.of("externalId", "u1", "schoolExternalId", "s1",
            "username", "jdoe", "email", "jdoe@fdl.mg", "phone", "0340000000", "password", "secret", "type", UserType.ADMINISTRATION));

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        credentials = mock(UserCredentialRepository.class);
        when(users.findByUsernameAndSchoolExternalId("jdoe", "s1")).thenReturn(List.of(jdoe));
        when(users.findByEmailAndSchoolExternalId("jdoe@fdl.mg", "s1")).thenReturn(List.of(jdoe));
        when(users.findByPhoneAndSchoolExternalId("0340000000", "s1")).thenReturn(List.of(jdoe));
        when(credentials.findFirstByUserExternalId(any())).thenReturn(Optional.empty());
        hash("u1", "Secret123");
        service = newService(encoder.encode("master"));
    }

    private LoginService newService(String masterHash) {
        EfdlProperties props = new EfdlProperties(
                new EfdlProperties.Security(masterHash, List.of()), new EfdlProperties.Tenant(null));
        return new LoginService(users, credentials, encoder, props);
    }

    private void hash(String userId, String password) {
        UserCredential c = new UserCredential(userId, "s1");
        c.updateHash(encoder.encode(password), false, "test");
        when(credentials.findFirstByUserExternalId(userId)).thenReturn(Optional.of(c));
    }

    @Test
    void identifiantTropCourtRefuse() {
        assertThat(service.authenticate("abc", "Secret123", "s1")).isEmpty();
    }

    @Test
    void identifiantFlexibleNomEmailTelephone() {
        assertThat(service.authenticate("jdoe", "Secret123", "s1")).contains(jdoe);
        assertThat(service.authenticate("jdoe@fdl.mg", "Secret123", "s1")).contains(jdoe);
        assertThat(service.authenticate("0340000000", "Secret123", "s1")).contains(jdoe);
    }

    @Test
    void telephoneNeSertPlusDeMotDePasse() {
        assertThat(service.authenticate("jdoe@fdl.mg", "0340000000", "s1")).isEmpty();
    }

    @Test
    void motDePasseEnClairNeSertPas() {
        assertThat(service.authenticate("jdoe", "secret", "s1")).isEmpty(); // champ user.password ignoré
        verify(users, never()).save(any());
        verify(credentials, never()).save(any());
    }

    @Test
    void sansHashConnexionRefusee() {
        when(credentials.findFirstByUserExternalId("u1")).thenReturn(Optional.empty());
        assertThat(service.authenticate("jdoe", "secret", "s1")).isEmpty();
        assertThat(service.authenticate("jdoe", "Secret123", "s1")).isEmpty();
    }

    @Test
    void utilisateurSupprimeRefuse() {
        User supprime = with(new User(), Map.of("externalId", "u2", "schoolExternalId", "s1",
                "username", "gone01", "type", UserType.ADMINISTRATION, "status", "DELETED"));
        when(users.findByUsernameAndSchoolExternalId("gone01", "s1")).thenReturn(List.of(supprime));
        hash("u2", "Secret123");
        assertThat(service.authenticate("gone01", "Secret123", "s1")).isEmpty();
    }

    @Test
    void adminUniquementParMotDePasseMaitre() {
        User admin = with(new User(), Map.of("externalId", "a1", "username", "admin", "password", "admin", "type", UserType.ADMIN));
        when(users.findByUsername("admin")).thenReturn(List.of(admin));
        assertThat(service.authenticate("admin", "master", "s1")).contains(admin);
        assertThat(service.authenticate("admin", "admin", "s1")).isEmpty();
        assertThat(newService("").authenticate("admin", "master", "s1")).isEmpty(); // maître non configuré
    }

    @Test
    void repliAdminAutreEcoleSeulementPourLesAdmins() {
        User adminAilleurs = with(new User(), Map.of("externalId", "a2", "schoolExternalId", "s2",
                "username", "superuser", "type", UserType.ADMIN));
        User simpleAilleurs = with(new User(), Map.of("externalId", "u9", "schoolExternalId", "s2",
                "username", "someone", "type", UserType.ADMINISTRATION));
        when(users.findByUsername("superuser")).thenReturn(List.of(adminAilleurs));
        when(users.findByUsername("someone")).thenReturn(List.of(simpleAilleurs));
        hash("a2", "pwd12345");
        hash("u9", "pwd12345");
        assertThat(service.authenticate("superuser", "pwd12345", "s1")).contains(adminAilleurs);
        assertThat(service.authenticate("someone", "pwd12345", "s1")).isEmpty();
    }
}
