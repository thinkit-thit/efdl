package com.ffs.efdl.dto;

import java.util.Date;
import java.util.List;

/** Objets exposés par l'API du socle administration (lecture). Jamais de mot de passe. */
public final class AdminDtos {

    private AdminDtos() {
    }

    // ---- Groupes d'accès (/admin/user/group/*) ----
    public record GroupDto(String externalId, String name, List<String> allowedPaths) {
    }

    // ---- Menus (/admin/menus, /admin/menu/*) ----
    public record MenuRowDto(String externalId, String title, String url, String urlExterne, String icon, String path,
                             int order, String parentId, String parentTitle) {
    }

    // ---- Applications (/admin/school/*) ----
    public record SchoolRowDto(String externalId, String name, String domainName, String years, String address,
                               String description, String validatorId) {
    }

    /** Choix du « Validateur info » : utilisateurs ADMINISTRATION de l'application (« username - prénom nom »). */
    public record ValidatorChoiceDto(String externalId, String label) {
    }

    // ---- Utilisateurs (/admin/user/administration/*) ----
    public record UserRowDto(String externalId, String displayName, String username, String phone, String address,
                             Date inDate, String gender, boolean hasPhoto, Date lastLogin) {
    }

    /** Liste + droit d'afficher les actions (route /admin/user/administration/edit/). */
    public record UserListDto(List<UserRowDto> users, boolean canEdit) {
    }

    public record GroupChoiceDto(String externalId, String name, boolean member) {
    }

    public record UserDetailDto(String externalId, String username, String lastName, String firstName, String gender,
                                String phone, String email, String address, Date birthDate, String birthPlace,
                                String cinNumber, Date cinDate, String cinPlace, String numeroCnaps, Date inDate,
                                List<GroupChoiceDto> groups) {
    }
}
