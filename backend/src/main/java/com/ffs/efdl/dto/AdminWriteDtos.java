package com.ffs.efdl.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Corps des requêtes d'écriture du socle administration. Jamais de mot de passe. */
public final class AdminWriteDtos {

    private AdminWriteDtos() {
    }

    /** Groupe : nom + chemins autorisés (expressions régulières). */
    public record GroupForm(
            @NotBlank @Size(max = 200) String name,
            List<@Size(max = 300) String> allowedPaths) {
    }

    /** Menu : Libellé, Parent, Icon, Ordre, Lien, Path. */
    public record MenuForm(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 100) String icon,
            @Size(max = 100) String parentId,
            int order,
            @Size(max = 500) String url,
            @Size(max = 200) String path) {
    }

    /** Application : Nom du compte, Nom de domaine, Année, Adresse, Description, Validateur info. */
    public record SchoolForm(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 200) String domainName,
            @Size(max = 50) String years,
            @Size(max = 500) String address,
            @Size(max = 2000) String description,
            @Size(max = 100) String validatorId) {
    }

    /** Utilisateur ADMINISTRATION : champs de la « Fiche de renseignements », hors mot de passe et photo. */
    public record UserForm(
            @NotBlank @Size(max = 100) String username,
            @Size(max = 200) String lastName,
            @Size(max = 200) String firstName,
            @Pattern(regexp = "^(MALE|FEMALE)?$") String gender,
            @Size(max = 50) String phone,
            @Email @Size(max = 200) String email,
            @Size(max = 500) String address,
            LocalDate birthDate,
            @Size(max = 200) String birthPlace,
            @Size(max = 50) String cinNumber,
            LocalDate cinDate,
            @Size(max = 200) String cinPlace,
            @Size(max = 50) String numeroCnaps,
            LocalDate inDate,
            List<@NotBlank String> userGroupIds) {
    }

    /** Réponse d'une création : identifiant métier du nouveau document. */
    /** Mot de passe provisoire saisi par l'administrateur (règles dans PasswordService). */
    public record PasswordResetForm(@NotBlank @Size(max = 200) String password) {
    }

    public record Created(String externalId) {
    }
}
