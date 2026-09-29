package com.ffs.efdl.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import com.ffs.efdl.repository.PasswordResetRepository;
import com.ffs.efdl.repository.UserRepository;

class PasswordResetServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void envoiNonConfigureMemeReponsePourToutIdentifiant() {
        UserRepository users = mock(UserRepository.class);
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        PasswordResetService service = new PasswordResetService(users, mock(PasswordResetRepository.class),
                mock(PasswordService.class), new BCryptPasswordEncoder(4), provider, "");
        // Vérifié avant toute recherche : la réponse ne révèle pas si le compte existe.
        assertThatThrownBy(() -> service.request("inconnu", "s1")).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("pas encore configuré");
        verifyNoInteractions(users);
    }
}
