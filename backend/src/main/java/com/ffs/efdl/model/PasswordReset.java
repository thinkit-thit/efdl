package com.ffs.efdl.model;

import java.time.Instant;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Collection {@code password_reset} : demande de réinitialisation par e-mail.
 * Seul le hash BCrypt du code est conservé ; code à usage unique, durée limitée, essais limités.
 */
@Document(collection = "password_reset")
public class PasswordReset {

    @Id
    private ObjectId id;
    private String userExternalId;
    private String schoolExternalId;
    private String codeHash;
    private Instant createdAt;
    private Instant expiresAt;
    private int attempts;
    private boolean used;

    protected PasswordReset() {
    }

    public PasswordReset(String userExternalId, String schoolExternalId, String codeHash, Instant expiresAt) {
        this.userExternalId = userExternalId;
        this.schoolExternalId = schoolExternalId;
        this.codeHash = codeHash;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
    }

    public boolean isUsable(Instant now, int maxAttempts) {
        return !used && attempts < maxAttempts && now.isBefore(expiresAt);
    }

    public void failedAttempt() {
        attempts++;
    }

    public void markUsed() {
        used = true;
    }

    public String getUserExternalId() { return userExternalId; }
    public String getSchoolExternalId() { return schoolExternalId; }
    public String getCodeHash() { return codeHash; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public int getAttempts() { return attempts; }
    public boolean isUsed() { return used; }
}
