package com.ffs.efdl.service;

import static com.ffs.efdl.TestData.with;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import com.ffs.efdl.model.User;
import com.ffs.efdl.model.UserCredential;
import com.ffs.efdl.model.UserType;
import com.ffs.efdl.repository.UserCredentialRepository;
import com.ffs.efdl.security.EfdlUserPrincipal;

class PasswordServiceTest {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private UserCredentialRepository credentials;
    private PasswordService service;

    private final User jdoe = with(new User(), Map.of("externalId", "u1", "schoolExternalId", "s1",
            "username", "jdoe", "type", UserType.ADMINISTRATION));
    private final EfdlUserPrincipal jdoePrincipal =
            new EfdlUserPrincipal("u1", "jdoe", "Doe", UserType.ADMINISTRATION, "s1", Set.of(), null);

    @BeforeEach
    void setUp() {
        credentials = mock(UserCredentialRepository.class);
        when(credentials.findFirstByUserExternalId(any())).thenReturn(Optional.empty());
        service = new PasswordService(credentials, encoder, org.mockito.Mockito.mock(AuditService.class));
    }

    @Test
    void reglesMinimales() {
        assertThatThrownBy(() -> PasswordService.validate("abc12")).hasMessageContaining("8 caractères");
        assertThatThrownBy(() -> PasswordService.validate("abcdefgh")).hasMessageContaining("chiffre");
        assertThatThrownBy(() -> PasswordService.validate("12345678")).hasMessageContaining("lettre");
        assertThatThrownBy(() -> PasswordService.validate("a1" + "x".repeat(71))).hasMessageContaining("trop long");
        PasswordService.validate("Provisoire1");
    }

    @Test
    void reinitialisationParAdminImposeLeChangement() {
        service.resetByAdmin(jdoe, "Provisoire1", "admin-id");
        ArgumentCaptor<UserCredential> saved = ArgumentCaptor.forClass(UserCredential.class);
        verify(credentials).save(saved.capture());
        assertThat(saved.getValue().getUserExternalId()).isEqualTo("u1");
        assertThat(saved.getValue().isMustChangePassword()).isTrue();
        assertThat(saved.getValue().getUpdatedBy()).isEqualTo("admin-id");
        assertThat(encoder.matches("Provisoire1", saved.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void changementPersonnelVerifieLeMotDePasseActuel() {
        UserCredential c = new UserCredential("u1", "s1");
        c.updateHash(encoder.encode("Provisoire1"), true, "admin-id");
        when(credentials.findFirstByUserExternalId("u1")).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.changeOwn(jdoePrincipal, "mauvais", "Nouveau2026"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("actuel incorrect");
        assertThatThrownBy(() -> service.changeOwn(jdoePrincipal, "Provisoire1", "Provisoire1"))
                .hasMessageContaining("différent");
        verify(credentials, never()).save(any());

        service.changeOwn(jdoePrincipal, "Provisoire1", "Nouveau2026");
        assertThat(encoder.matches("Nouveau2026", c.getPasswordHash())).isTrue();
        assertThat(c.isMustChangePassword()).isFalse();
    }

    @Test
    void compteAdminUtiliseLeMotDePasseMaitre() {
        User admin = with(new User(), Map.of("externalId", "a1", "username", "admin", "type", UserType.ADMIN));
        assertThatThrownBy(() -> service.resetByAdmin(admin, "Provisoire1", "x")).hasMessageContaining("maître");
        EfdlUserPrincipal p = new EfdlUserPrincipal("a1", "admin", "Admin", UserType.ADMIN, "s1", Set.of(), null);
        assertThatThrownBy(() -> service.changeOwn(p, "x", "Nouveau2026")).hasMessageContaining("maître");
    }
}
