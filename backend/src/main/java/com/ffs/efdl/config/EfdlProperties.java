package com.ffs.efdl.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriétés applicatives (préfixe {@code efdl}). Aucune valeur secrète par défaut.
 */
@ConfigurationProperties(prefix = "efdl")
public record EfdlProperties(Security security, Tenant tenant) {

    public record Security(
            /* Hash BCrypt du mot de passe maître (admin.pass). Vide = désactivé. */
            String adminMasterPasswordHash,
            /* Origines autorisées pour CORS. */
            List<String> allowedOrigins) {
    }

    public record Tenant(
            /* externalId de l'école utilisée quand le Host ne correspond à aucun School.domainName. */
            String defaultSchoolId) {
    }
}
