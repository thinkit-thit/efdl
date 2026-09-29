package com.ffs.efdl.model;

import java.time.Instant;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Collection {@code user_credential} : hash BCrypt du mot de passe.
 * Seule source de vérité des mots de passe : le champ {@code user.password}
 * n'est ni lu ni écrit pour l'authentification.
 */
@Document(collection = "user_credential")
public class UserCredential {

    @Id
    private ObjectId id;
    private String userExternalId;
    private String schoolExternalId;
    private String passwordHash;
    private String algorithm;
    /** Vrai après une réinitialisation par un administrateur : changement obligatoire à la connexion. */
    private Boolean mustChangePassword;
    private String updatedBy;
    private Instant createdAt;
    private Instant updatedAt;

    protected UserCredential() {
    }

    public UserCredential(String userExternalId, String schoolExternalId) {
        this.userExternalId = userExternalId;
        this.schoolExternalId = schoolExternalId;
        this.createdAt = Instant.now();
    }

    public void updateHash(String passwordHash, boolean mustChangePassword, String updatedBy) {
        this.passwordHash = passwordHash;
        this.algorithm = "bcrypt";
        this.mustChangePassword = mustChangePassword;
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }

    public String getUserExternalId() { return userExternalId; }
    public String getSchoolExternalId() { return schoolExternalId; }
    public String getPasswordHash() { return passwordHash; }
    public String getAlgorithm() { return algorithm; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public boolean isMustChangePassword() { return Boolean.TRUE.equals(mustChangePassword); }
    public String getUpdatedBy() { return updatedBy; }
}
