package com.ffs.efdl.service;

import static com.ffs.efdl.service.DocumentWriter.fields;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.ffs.efdl.dto.AdminWriteDtos.GroupForm;
import com.ffs.efdl.dto.AdminWriteDtos.MenuForm;
import com.ffs.efdl.dto.AdminWriteDtos.SchoolForm;
import com.ffs.efdl.dto.AdminWriteDtos.UserForm;
import com.ffs.efdl.model.User;
import com.ffs.efdl.model.UserGroup;
import com.ffs.efdl.model.UserType;
import com.ffs.efdl.repository.MenuRepository;
import com.ffs.efdl.repository.SchoolRepository;
import com.ffs.efdl.repository.UserGroupRepository;
import com.ffs.efdl.repository.UserRepository;

/**
 * Écritures du socle administration,
 * avec des modifications limitées aux champs de l'écran (voir {@link DocumentWriter}).
 * Suppressions logiques uniquement ({@code status=DELETED}) ; aucun mot de passe.
 */
@Service
public class AdminWriteService {

    static final String CLASS_USER_GROUP = "com.ffs.kiwi.model.user.group.UserGroup";
    static final String CLASS_MENU = "com.ffs.kiwi.model.menu.Menu";
    static final String CLASS_SCHOOL = "com.ffs.kiwi.model.School";
    static final String CLASS_USER = "com.ffs.kiwi.model.user.User";

    private final DocumentWriter writer;
    private final UserGroupRepository groupRepository;
    private final MenuRepository menuRepository;
    private final SchoolRepository schoolRepository;
    private final UserRepository userRepository;

    public AdminWriteService(DocumentWriter writer, UserGroupRepository groupRepository,
                             MenuRepository menuRepository, SchoolRepository schoolRepository,
                             UserRepository userRepository) {
        this.writer = writer;
        this.groupRepository = groupRepository;
        this.menuRepository = menuRepository;
        this.schoolRepository = schoolRepository;
        this.userRepository = userRepository;
    }

    // ---------- Groupes (admin/user/group) ----------

    public String createGroup(String school, GroupForm form, String by) {
        return writer.insert("user_group", CLASS_USER_GROUP, DocumentWriter.newExternalId(), school,
                groupFields(form), by);
    }

    /** Modification limitée aux groupes de l'école courante. */
    public void updateGroup(String school, String id, GroupForm form, String by) {
        if (!writer.update("user_group", id, school, groupFields(form), by)) throw notFound();
    }

    private static Map<String, Object> groupFields(GroupForm form) {
        // Équivalent de SetUtils.convertToSet(paths, ";") : ensemble sans éléments vides (chemins nettoyés des espaces).
        Set<String> paths = new LinkedHashSet<>();
        if (form.allowedPaths() != null) {
            form.allowedPaths().stream().filter(p -> p != null).map(String::trim).filter(StringUtils::hasText).forEach(paths::add);
        }
        return fields("name", form.name().trim(), "allowedPaths", new ArrayList<>(paths));
    }

    // ---------- Menus (admin/menu) ----------

    public String createMenu(String school, MenuForm form, String by) {
        return writer.insert("Menu", CLASS_MENU, DocumentWriter.newExternalId(), school,
                menuFields(school, null, form), by);
    }

    public void updateMenu(String school, String id, MenuForm form, String by) {
        if (!writer.update("Menu", id, school, menuFields(school, id, form), by)) throw notFound();
    }

    private Map<String, Object> menuFields(String school, String selfId, MenuForm form) {
        String parentId = blankToNull(form.parentId());
        if (parentId != null) {
            if (parentId.equals(selfId)) throw badRequest("Un menu ne peut pas être son propre parent");
            menuRepository.findFirstBySchoolExternalIdAndExternalId(school, parentId).filter(m -> !m.isDeleted())
                    .orElseThrow(() -> badRequest("Menu parent introuvable"));
        }
        return fields("title", form.title().trim(), "icon", blankToNull(form.icon()), "parentId", parentId,
                "order", form.order(), "url", blankToNull(form.url()), "path", blankToNull(form.path()));
    }

    // ---------- Applications (admin/school, ADMIN) ----------

    /** externalId = normalize(nom). Refus si cet identifiant existe déjà. */
    public String createSchool(SchoolForm form, String by) {
        String externalId = normalize(form.name());
        if (!StringUtils.hasText(externalId)) throw badRequest("Nom du compte invalide");
        if (schoolRepository.findFirstByExternalId(externalId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Une application avec cet identifiant existe déjà");
        }
        Map<String, Object> f = schoolFields(externalId, form);
        f.put("signupActive", false); // toujours écrit
        return writer.insert("school", CLASS_SCHOOL, externalId, null, false, f, by);
    }

    public void updateSchool(String id, SchoolForm form, String by) {
        if (!writer.update("school", id, null, schoolFields(id, form), by)) throw notFound();
    }

    private Map<String, Object> schoolFields(String schoolId, SchoolForm form) {
        Map<String, Object> f = fields("name", form.name().trim(), "domainName", blankToNull(form.domainName()),
                "years", blankToNull(form.years()), "address", blankToNull(form.address()),
                "description", blankToNull(form.description()));
        // Validateur info (school.validatorId) : un utilisateur ADMINISTRATION actif de cette application.
        String validator = blankToNull(form.validatorId());
        if (validator != null && userRepository.findFirstByExternalId(validator)
                .filter(u -> u.getType() == UserType.ADMINISTRATION && !u.isDeleted() && schoolId.equals(u.getSchoolExternalId()))
                .isEmpty()) {
            throw badRequest("Validateur info : choisissez un utilisateur ADMINISTRATION de cette application");
        }
        f.put("validatorId", validator);
        return f;
    }

    /** Identifiant normalisé à partir d'un libellé. */
    static String normalize(String value) {
        String v = Normalizer.normalize(value, Normalizer.Form.NFD).toLowerCase();
        v = v.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        v = v.replaceAll("\\s+", "-").replaceAll("\\.", "").replaceAll("/", "-").replaceAll("[^a-zA-Z0-9]+", "-");
        while (v.contains("--")) v = v.replace("--", "-");
        return v.toLowerCase();
    }

    // ---------- Utilisateurs ADMINISTRATION (admin/user/administration) ----------

    public String createUser(String school, UserForm form, String by) {
        checkUsernameFree(school, form.username(), null);
        Map<String, Object> f = userFields(school, form);
        f.put("type", UserType.ADMINISTRATION.name());
        // Champs numériques toujours écrits pour un nouvel utilisateur.
        f.putAll(fields("siege", 0, "siegeObtenu", 0, "siegeObtenuCalcul", 0, "voix", 0, "order", 0,
                "cadreSup", false, "amount", 0.0));
        return writer.insert("user", CLASS_USER, DocumentWriter.newExternalId(), school, f, by);
    }

    /** Seuls les utilisateurs ADMINISTRATION de l'école courante sont modifiables. */
    public void updateUser(String school, String id, UserForm form, String by) {
        User existing = editableUser(school, id);
        checkUsernameFree(school, form.username(), existing.getExternalId());
        if (!writer.update("user", id, school, userFields(school, form), by)) throw notFound();
    }

    /** Utilisateur ADMINISTRATION actif de l'école courante (seuls ceux-là sont gérés par la fiche). */
    public User editableUser(String school, String id) {
        return userRepository.findFirstByExternalId(id)
                .filter(u -> u.getType() == UserType.ADMINISTRATION && !u.isDeleted() && school.equals(u.getSchoolExternalId()))
                .orElseThrow(AdminWriteService::notFound);
    }

    private Map<String, Object> userFields(String school, UserForm form) {
        Set<String> schoolGroups = groupRepository.findBySchoolExternalId(school).stream()
                .filter(g -> !g.isDeleted()).map(UserGroup::getExternalId).collect(Collectors.toSet());
        List<String> groups = form.userGroupIds() == null ? List.of() : form.userGroupIds().stream().distinct().toList();
        if (!schoolGroups.containsAll(groups)) throw badRequest("Groupe d'accès inconnu pour cette application");
        return fields(
                "username", form.username().trim(),
                "lastName", blankToNull(form.lastName()),
                "firstName", blankToNull(form.firstName()),
                "gender", blankToNull(form.gender()),
                "phone", blankToNull(form.phone()),
                "email", blankToNull(form.email()),
                "address", blankToNull(form.address()),
                "birthDate", toDate(form.birthDate()),
                "birthPlace", blankToNull(form.birthPlace()),
                "cinNumber", blankToNull(form.cinNumber()),
                "cinDate", toDate(form.cinDate()),
                "cinPlace", blankToNull(form.cinPlace()),
                "numeroCnaps", blankToNull(form.numeroCnaps()),
                "inDate", toDate(form.inDate()),
                "userGroupIds", groups.isEmpty() ? null : new ArrayList<>(groups));
    }

    /** Un identifiant ne doit pas être dupliqué dans l'école. */
    private void checkUsernameFree(String school, String username, String selfId) {
        boolean taken = userRepository.findByUsernameAndSchoolExternalId(username.trim(), school).stream()
                .anyMatch(u -> !u.isDeleted() && !u.getExternalId().equals(selfId));
        if (taken) throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce matricule est déjà utilisé");
    }

    // ---------- Suppressions logiques (status=DELETED) ----------

    /** Suppression d'un menu. Refus si le menu a encore des sous-menus (ils deviendraient inaccessibles). */
    public void deleteMenu(String school, String id, String by) {
        boolean hasChildren = menuRepository.findBySchoolExternalIdOrderByOrderAsc(school).stream()
                .anyMatch(m -> !m.isDeleted() && id.equals(m.getParentId()));
        if (hasChildren) throw conflict("Ce menu a des sous-menus : supprimez-les ou déplacez-les d'abord");
        if (!writer.markDeleted("Menu", id, school, by)) throw notFound();
    }

    /** Suppression d'une application (ADMIN). Refus pour l'application en cours ou celle de l'utilisateur connecté. */
    public void deleteSchool(String id, String currentSchool, String userSchool, String by) {
        if (id.equals(currentSchool) || id.equals(userSchool)) {
            throw conflict("Impossible de supprimer l'application en cours d'utilisation");
        }
        if (!writer.markDeleted("school", id, null, by)) throw notFound();
    }

    /** Événement deleteUser de la fiche utilisateur ; limité à l'école courante, jamais son propre compte. */
    public void deleteUser(String school, String id, String by) {
        if (id.equals(by)) throw conflict("Vous ne pouvez pas supprimer votre propre compte");
        editableUser(school, id);
        if (!writer.markDeleted("user", id, school, by)) throw notFound();
    }

    // ---------- utilitaires ----------

    /** Date au format yyyyMMdd : minuit, fuseau du serveur. */
    private static Date toDate(LocalDate d) {
        return d == null ? null : Date.from(d.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Élément introuvable");
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
