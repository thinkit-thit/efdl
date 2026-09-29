package com.ffs.efdl.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.ffs.efdl.config.EfdlProperties;
import com.ffs.efdl.model.User;
import com.ffs.efdl.model.UserCredential;
import com.ffs.efdl.model.UserType;
import com.ffs.efdl.repository.UserCredentialRepository;
import com.ffs.efdl.repository.UserRepository;

/**
 * Authentification :
 * <ol>
 *   <li>identifiant de moins de 4 caractères → refus ;</li>
 *   <li>identifiant {@code admin} → uniquement par le mot de passe maître (hash BCrypt de admin.pass),
 *       sur le document user {@code username=admin, password="admin", type=ADMIN} ;</li>
 *   <li>sinon, dans l'école courante : nom d'utilisateur, puis e-mail, puis téléphone (identifiant flexible),
 *       toujours avec le vrai mot de passe ;</li>
 *   <li>repli : utilisateur ADMIN d'une autre école (même nom d'utilisateur et mot de passe).</li>
 * </ol>
 * Mots de passe : uniquement le hash BCrypt de la collection {@code user_credential} ;
 * le champ {@code user.password} n'est pas utilisé. Un utilisateur sans hash doit être réinitialisé
 * par un administrateur. Les utilisateurs supprimés ({@code status=DELETED}) sont refusés.
 * Aucun document {@code user} n'est modifié, aucun mot de passe n'est journalisé.
 */
@Service
public class LoginService {

    static final String ADMIN_USERNAME = "admin";

    private final UserRepository userRepository;
    private final UserCredentialRepository credentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final EfdlProperties properties;
    /** Hash factice : même coût de calcul quand l'utilisateur n'a pas de hash (ne révèle pas son absence). */
    private final String dummyHash;

    public LoginService(UserRepository userRepository, UserCredentialRepository credentialRepository,
                        PasswordEncoder passwordEncoder, EfdlProperties properties) {
        this.userRepository = userRepository;
        this.credentialRepository = credentialRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public Optional<User> authenticate(String identifier, String password, String schoolExternalId) {
        if (!StringUtils.hasText(identifier) || identifier.length() < 4 || password == null) {
            return Optional.empty();
        }
        if (ADMIN_USERNAME.equals(identifier)) {
            return authenticateMasterAdmin(password);
        }
        if (StringUtils.hasText(schoolExternalId)) {
            List<User> candidates = new ArrayList<>();
            candidates.addAll(userRepository.findByUsernameAndSchoolExternalId(identifier, schoolExternalId));
            candidates.addAll(userRepository.findByEmailAndSchoolExternalId(identifier, schoolExternalId));
            candidates.addAll(userRepository.findByPhoneAndSchoolExternalId(identifier, schoolExternalId));
            for (User user : candidates) {
                if (!user.isDeleted() && verifyPassword(user, password)) {
                    return Optional.of(user);
                }
            }
        }
        for (User user : userRepository.findByUsername(identifier)) {
            if (user.getType() == UserType.ADMIN && !user.isDeleted() && verifyPassword(user, password)) {
                return Optional.of(user);
            }
        }
        return Optional.empty();
    }

    private Optional<User> authenticateMasterAdmin(String password) {
        String masterHash = properties.security() == null ? null : properties.security().adminMasterPasswordHash();
        if (!StringUtils.hasText(masterHash) || !passwordEncoder.matches(password, masterHash)) {
            return Optional.empty();
        }
        return userRepository.findByUsername(ADMIN_USERNAME).stream()
                .filter(u -> u.getType() == UserType.ADMIN && !u.isDeleted() && "admin".equals(u.getStoredPassword()))
                .findFirst();
    }

    private boolean verifyPassword(User user, String password) {
        String hash = credentialRepository.findFirstByUserExternalId(user.getExternalId())
                .map(UserCredential::getPasswordHash).orElse(null);
        if (hash == null) {
            passwordEncoder.matches(password, dummyHash);
            return false;
        }
        return passwordEncoder.matches(password, hash);
    }
}
