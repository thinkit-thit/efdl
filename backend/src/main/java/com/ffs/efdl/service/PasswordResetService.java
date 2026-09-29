package com.ffs.efdl.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.ffs.efdl.model.PasswordReset;
import com.ffs.efdl.model.User;
import com.ffs.efdl.model.UserType;
import com.ffs.efdl.repository.PasswordResetRepository;
import com.ffs.efdl.repository.UserRepository;

/**
 * « Mot de passe oublié » (/forgotPwd, code par e-mail) :
 * <ol>
 *   <li>l'utilisateur donne son identifiant (nom d'utilisateur, e-mail ou téléphone, comme la connexion) ;
 *       si le compte existe et a une adresse e-mail, un code à 6 chiffres lui est envoyé ;</li>
 *   <li>il saisit le code et son nouveau mot de passe (règles habituelles).</li>
 * </ol>
 * Réponse identique que le compte existe ou non ; code à usage unique, conservé haché, valable 15 minutes,
 * 5 essais au plus ; une nouvelle demande annule la précédente. Le compte {@code admin} (mot de passe maître) est exclu.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    static final Duration VALIDITY = Duration.ofMinutes(15);
    static final int MAX_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final PasswordResetRepository resets;
    private final PasswordService passwords;
    private final PasswordEncoder encoder;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final String from;

    public PasswordResetService(UserRepository users, PasswordResetRepository resets, PasswordService passwords,
                                PasswordEncoder encoder, ObjectProvider<JavaMailSender> mailSender,
                                @Value("${efdl.mail.from:}") String from) {
        this.users = users;
        this.resets = resets;
        this.passwords = passwords;
        this.encoder = encoder;
        this.mailSender = mailSender;
        this.from = from;
    }

    /** Envoie un code si le compte existe et a une adresse e-mail ; ne révèle jamais le résultat à l'appelant. */
    public void request(String identifier, String school) {
        // Configuration vérifiée avant la recherche : la réponse ne dépend jamais de l'existence du compte.
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.warn("Mot de passe oublié : envoi d'e-mail non configuré (spring.mail.host)");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "L'envoi d'e-mails n'est pas encore configuré : contactez un administrateur");
        }
        Optional<User> user = find(identifier, school);
        if (user.isEmpty() || !StringUtils.hasText(user.get().getEmail())) {
            log.info("Mot de passe oublié : aucun compte avec e-mail pour l'identifiant demandé");
            return;
        }
        User u = user.get();
        // Une nouvelle demande annule les codes précédents.
        resets.findByUserExternalIdAndUsedFalse(u.getExternalId()).forEach(r -> {
            r.markUsed();
            resets.save(r);
        });
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        resets.save(new PasswordReset(u.getExternalId(), u.getSchoolExternalId(), encoder.encode(code), Instant.now().plus(VALIDITY)));
        SimpleMailMessage message = new SimpleMailMessage();
        if (StringUtils.hasText(from)) message.setFrom(from);
        message.setTo(u.getEmail());
        message.setSubject("eFDL — code de réinitialisation du mot de passe");
        message.setText("Bonjour " + u.getDisplayName().trim() + ",\n\n"
                + "Votre code de réinitialisation du mot de passe eFDL est : " + code + "\n"
                + "Il est valable " + VALIDITY.toMinutes() + " minutes et ne peut servir qu'une fois.\n\n"
                + "Si vous n'êtes pas à l'origine de cette demande, ignorez ce message.\n");
        try {
            sender.send(message);
            log.info("Code de réinitialisation envoyé à l'utilisateur {}", u.getExternalId());
        } catch (MailException e) {
            log.error("Échec d'envoi du code de réinitialisation à l'utilisateur {}", u.getExternalId(), e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "L'e-mail n'a pas pu être envoyé : réessayez plus tard");
        }
    }

    /** Vérifie le code et enregistre le nouveau mot de passe. */
    public void confirm(String identifier, String code, String newPassword, String school) {
        ResponseStatusException invalid = new ResponseStatusException(HttpStatus.BAD_REQUEST, "Code invalide ou expiré");
        User user = find(identifier, school).orElseThrow(() -> invalid);
        PasswordReset reset = resets.findFirstByUserExternalIdOrderByCreatedAtDesc(user.getExternalId()).orElseThrow(() -> invalid);
        if (!reset.isUsable(Instant.now(), MAX_ATTEMPTS)) throw invalid;
        if (code == null || !encoder.matches(code.trim(), reset.getCodeHash())) {
            reset.failedAttempt();
            resets.save(reset);
            throw invalid;
        }
        PasswordService.validate(newPassword);
        passwords.setBySelf(user, newPassword);
        reset.markUsed();
        resets.save(reset);
        log.info("Mot de passe réinitialisé par code e-mail pour l'utilisateur {}", user.getExternalId());
    }

    /** Même recherche que la connexion (nom d'utilisateur, e-mail, téléphone) dans l'école ; comptes supprimés et admin exclus. */
    private Optional<User> find(String identifier, String school) {
        if (!StringUtils.hasText(identifier) || !StringUtils.hasText(school)) return Optional.empty();
        String id = identifier.trim();
        List<User> candidates = new ArrayList<>();
        candidates.addAll(users.findByUsernameAndSchoolExternalId(id, school));
        candidates.addAll(users.findByEmailAndSchoolExternalId(id, school));
        candidates.addAll(users.findByPhoneAndSchoolExternalId(id, school));
        return candidates.stream()
                .filter(u -> !u.isDeleted())
                .filter(u -> !(LoginService.ADMIN_USERNAME.equals(u.getUsername()) && u.getType() == UserType.ADMIN))
                .findFirst();
    }
}
