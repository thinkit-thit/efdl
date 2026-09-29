package com.ffs.efdl.service;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.ffs.efdl.model.User;
import com.ffs.efdl.model.UserCredential;
import com.ffs.efdl.model.UserType;
import com.ffs.efdl.repository.UserCredentialRepository;
import com.ffs.efdl.security.EfdlUserPrincipal;

/**
 * Mots de passe : hash BCrypt dans {@code user_credential} uniquement.
 * <ul>
 *   <li>réinitialisation par un administrateur → mot de passe provisoire, changement obligatoire à la connexion ;</li>
 *   <li>changement par l'utilisateur lui-même → vérification du mot de passe actuel.</li>
 * </ul>
 * Le compte {@code admin} n'a pas de mot de passe propre : il utilise le mot de passe maître (configuration).
 */
@Service
public class PasswordService {

    private static final Logger log = LoggerFactory.getLogger(PasswordService.class);

    static final int MIN_LENGTH = 8;
    /** BCrypt ignore au-delà de 72 octets : on refuse plutôt que de tronquer silencieusement. */
    static final int MAX_BYTES = 72;

    private final UserCredentialRepository credentialRepository;
    private final PasswordEncoder passwordEncoder;

    private final AuditService audit;

    public PasswordService(UserCredentialRepository credentialRepository, PasswordEncoder passwordEncoder, AuditService audit) {
        this.audit = audit;
        this.credentialRepository = credentialRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** Réinitialisation par un administrateur (fiche utilisateur) : l'utilisateur devra le changer. */
    public void resetByAdmin(User target, String newPassword, String by) {
        if (isMasterAdmin(target.getUsername(), target.getType())) {
            throw badRequest("Le compte admin utilise le mot de passe maître");
        }
        validate(newPassword);
        store(target.getExternalId(), target.getSchoolExternalId(), newPassword, true, by);
        log.info("Mot de passe réinitialisé pour l'utilisateur {} par {}", target.getExternalId(), by);
        audit.record(target.getSchoolExternalId(), by, AuditService.MOT_DE_PASSE, "user", target.getExternalId(),
                "Réinitialisation par un administrateur : " + target.getDisplayName(), java.util.List.of());
    }

    /** Changement par l'utilisateur connecté. */
    public void changeOwn(EfdlUserPrincipal user, String currentPassword, String newPassword) {
        if (isMasterAdmin(user.username(), user.type())) {
            throw badRequest("Le compte admin utilise le mot de passe maître : utilisez l'outil hash-master-password");
        }
        UserCredential credential = credentialRepository.findFirstByUserExternalId(user.externalId())
                .filter(c -> c.getPasswordHash() != null && passwordEncoder.matches(currentPassword, c.getPasswordHash()))
                .orElseThrow(() -> badRequest("Mot de passe actuel incorrect"));
        if (currentPassword.equals(newPassword)) {
            throw badRequest("Le nouveau mot de passe doit être différent de l'actuel");
        }
        validate(newPassword);
        credential.updateHash(passwordEncoder.encode(newPassword), false, user.externalId());
        credentialRepository.save(credential);
        log.info("Mot de passe changé par l'utilisateur {}", user.externalId());
        audit.record(user.schoolExternalId(), user.externalId(), AuditService.MOT_DE_PASSE, "user", user.externalId(),
                "Changement de son mot de passe", java.util.List.of());
    }

    /** Nouveau mot de passe choisi par l'utilisateur après vérification d'un code « mot de passe oublié ». */
    public void setBySelf(User user, String newPassword) {
        validate(newPassword);
        store(user.getExternalId(), user.getSchoolExternalId(), newPassword, false, user.getExternalId());
        audit.record(user.getSchoolExternalId(), user.getExternalId(), AuditService.MOT_DE_PASSE, "user", user.getExternalId(),
                "Mot de passe oublié : nouveau mot de passe", java.util.List.of());
    }

    public boolean mustChange(String userExternalId) {
        return credentialRepository.findFirstByUserExternalId(userExternalId)
                .map(UserCredential::isMustChangePassword).orElse(false);
    }

    private void store(String userExternalId, String schoolExternalId, String password, boolean mustChange, String by) {
        UserCredential credential = credentialRepository.findFirstByUserExternalId(userExternalId)
                .orElseGet(() -> new UserCredential(userExternalId, schoolExternalId));
        credential.updateHash(passwordEncoder.encode(password), mustChange, by);
        credentialRepository.save(credential);
    }

    /** Règles minimales : 8 caractères, au moins une lettre et un chiffre, 72 octets au plus. */
    static void validate(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw badRequest("Le mot de passe doit contenir au moins " + MIN_LENGTH + " caractères");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw badRequest("Le mot de passe est trop long");
        }
        if (password.chars().noneMatch(Character::isLetter) || password.chars().noneMatch(Character::isDigit)) {
            throw badRequest("Le mot de passe doit contenir au moins une lettre et un chiffre");
        }
    }

    private static boolean isMasterAdmin(String username, UserType type) {
        return LoginService.ADMIN_USERNAME.equals(username) && type == UserType.ADMIN;
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
