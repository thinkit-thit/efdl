package com.ffs.efdl.dto;

import java.util.List;

import com.ffs.efdl.model.School;
import com.ffs.efdl.model.UserType;
import com.ffs.efdl.security.EfdlUserPrincipal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Objets exposés par l'API d'authentification, de menu et de tenant (jamais de mot de passe). */
public final class ApiDtos {

    private ApiDtos() {
    }

    public record LoginRequest(
            @NotBlank @Size(max = 200) String identifier,
            @NotBlank @Size(max = 200) String password) {
    }

    public record SchoolDto(String externalId, String name, String domainName) {
        public static SchoolDto of(School s) {
            return s == null ? null : new SchoolDto(s.getExternalId(), s.getName(), s.getDomainName());
        }
    }

    public record PasswordResetRequest(@NotBlank @Size(max = 200) String identifier) {
    }

    public record PasswordResetConfirm(
            @NotBlank @Size(max = 200) String identifier,
            @NotBlank @Size(max = 20) String code,
            @NotBlank @Size(max = 200) String newPassword) {
    }

    public record PasswordChangeRequest(
            @NotBlank @Size(max = 200) String currentPassword,
            @NotBlank @Size(max = 200) String newPassword) {
    }

    /**
     * {@code mustChangePassword} : mot de passe provisoire à changer avant toute autre action ;
     * {@code canImport} : affichage des imports Excel (le serveur contrôle à nouveau chaque import).
     */
    public record CurrentUserDto(String externalId, String username, String displayName, UserType type,
                                 String language, SchoolDto school, boolean mustChangePassword, boolean canImport,
                                 boolean canViewAudit) {
        /** {@code privileged} : administrateur ou validateur info (imports, historique des modifications). */
        public static CurrentUserDto of(EfdlUserPrincipal p, SchoolDto school, boolean mustChangePassword, boolean privileged) {
            return new CurrentUserDto(p.externalId(), p.username(), p.displayName(), p.type(),
                    languageOf(p.nationality()), school, mustChangePassword, privileged, privileged);
        }

        /** Langue de l'interface : Anglais → en, Malagasy → mg, sinon fr. */
        static String languageOf(String nationality) {
            if ("Anglais".equals(nationality)) {
                return "en";
            }
            return "Malagasy".equals(nationality) ? "mg" : "fr";
        }
    }

    public record MenuItemDto(String externalId, String title, String icon, String url, String urlExterne,
                              String path, List<MenuItemDto> children) {
    }

    public record ErrorDto(int status, String error, String message) {
    }
}
