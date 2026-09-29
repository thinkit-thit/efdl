package com.ffs.efdl.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ffs.efdl.dto.AdminDtos.GroupDto;
import com.ffs.efdl.dto.AdminDtos.MenuRowDto;
import com.ffs.efdl.dto.AdminDtos.SchoolRowDto;
import com.ffs.efdl.dto.AdminDtos.ValidatorChoiceDto;
import com.ffs.efdl.dto.AdminDtos.UserDetailDto;
import com.ffs.efdl.dto.AdminDtos.UserListDto;
import com.ffs.efdl.dto.AdminWriteDtos.Created;
import com.ffs.efdl.dto.AdminWriteDtos.GroupForm;
import com.ffs.efdl.dto.AdminWriteDtos.MenuForm;
import com.ffs.efdl.dto.AdminWriteDtos.PasswordResetForm;
import com.ffs.efdl.dto.AdminWriteDtos.SchoolForm;
import com.ffs.efdl.dto.AdminWriteDtos.UserForm;
import com.ffs.efdl.model.School;
import com.ffs.efdl.security.EfdlUserPrincipal;
import com.ffs.efdl.security.RoutePath;
import com.ffs.efdl.security.RoutePathInterceptor;
import com.ffs.efdl.security.PermissionService;
import com.ffs.efdl.security.TenantResolver;
import com.ffs.efdl.service.AdminReadService;
import com.ffs.efdl.service.AdminWriteService;
import com.ffs.efdl.service.PasswordService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Socle administration — lecture et écriture (suppressions logiques uniquement).
 * Chaque endpoint déclare la route d'écran qu'il sert : les droits de cette route s'appliquent.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminReadService service;
    private final AdminWriteService writeService;
    private final TenantResolver tenantResolver;
    private final PermissionService permissionService;
    private final PasswordService passwordService;

    public AdminController(AdminReadService service, AdminWriteService writeService, TenantResolver tenantResolver,
                           PermissionService permissionService, PasswordService passwordService) {
        this.passwordService = passwordService;
        this.service = service;
        this.writeService = writeService;
        this.tenantResolver = tenantResolver;
        this.permissionService = permissionService;
    }

    private String schoolId(HttpServletRequest request) {
        EfdlUserPrincipal user = RoutePathInterceptor.currentUser();
        return tenantResolver.resolve(request).map(School::getExternalId).orElse(user == null ? null : user.schoolExternalId());
    }

    // ---- Groupes d'accès ----
    @GetMapping("/groups")
    @RoutePath("/admin/user/group/list")
    public List<GroupDto> groups(HttpServletRequest request) {
        return service.groups(schoolId(request));
    }

    @GetMapping("/groups/{id}")
    @RoutePath("/admin/user/group/edit/{id}")
    public GroupDto group(@PathVariable String id) {
        return service.group(id).orElseThrow(NotFoundException::new);
    }

    // ---- Menus ----
    @GetMapping("/menus")
    @RoutePath("/admin/menus")
    public List<MenuRowDto> menus(HttpServletRequest request) {
        return service.menus(schoolId(request));
    }

    @GetMapping("/menus/{id}")
    @RoutePath("/admin/menu/edit/{id}")
    public MenuRowDto menu(@PathVariable String id, HttpServletRequest request) {
        return service.menu(schoolId(request), id).orElseThrow(NotFoundException::new);
    }

    /** Choix du menu parent sur l'écran d'édition (liste déroulante), sauf le menu lui-même. */
    @GetMapping("/menus/{id}/parents")
    @RoutePath("/admin/menu/edit/{id}")
    public List<MenuRowDto> menuParents(@PathVariable String id, HttpServletRequest request) {
        return service.menus(schoolId(request)).stream().filter(m -> !m.externalId().equals(id)).toList();
    }

    // ---- Applications (ADMIN uniquement : règle /admin/school/*) ----
    @GetMapping("/schools")
    @RoutePath("/admin/school/list")
    public List<SchoolRowDto> schools() {
        return service.schools();
    }

    @GetMapping("/schools/{id}")
    @RoutePath("/admin/school/edit/{id}")
    public SchoolRowDto school(@PathVariable String id) {
        return service.school(id).orElseThrow(NotFoundException::new);
    }

    @GetMapping("/schools/{id}/validators")
    @RoutePath("/admin/school/edit/{id}")
    public List<ValidatorChoiceDto> schoolValidators(@PathVariable String id) {
        service.school(id).orElseThrow(NotFoundException::new);
        return service.validatorChoices(id);
    }

    // ---- Utilisateurs (administration) ----
    @GetMapping("/users")
    @RoutePath("/admin/user/administration/list")
    public UserListDto users(HttpServletRequest request) {
        boolean canEdit = permissionService.isAuthorizedPath(RoutePathInterceptor.currentUser(),
                "/admin/user/administration/edit/");
        return new UserListDto(service.administrationUsers(schoolId(request)), canEdit);
    }

    @GetMapping("/users/new")
    @RoutePath("/admin/user/administration/edit/new")
    public UserDetailDto newUser(HttpServletRequest request) {
        return service.newAdministrationUser(schoolId(request));
    }

    @GetMapping("/users/{id}")
    @RoutePath("/admin/user/administration/edit/{id}")
    public UserDetailDto user(@PathVariable String id, HttpServletRequest request) {
        return service.administrationUser(id, schoolId(request)).orElseThrow(NotFoundException::new);
    }

    // =====================  Écritures  =====================
    // Route de l'écran d'édition : "edit/new" pour une création, "edit/{id}" sinon.

    private static String by() {
        EfdlUserPrincipal user = RoutePathInterceptor.currentUser();
        return user == null ? null : user.externalId();
    }

    @PostMapping("/groups")
    @ResponseStatus(HttpStatus.CREATED)
    @RoutePath("/admin/user/group/edit/new")
    public Created createGroup(@Valid @RequestBody GroupForm form, HttpServletRequest request) {
        return new Created(writeService.createGroup(schoolId(request), form, by()));
    }

    @PutMapping("/groups/{id}")
    @RoutePath("/admin/user/group/edit/{id}")
    public GroupDto updateGroup(@PathVariable String id, @Valid @RequestBody GroupForm form, HttpServletRequest request) {
        writeService.updateGroup(schoolId(request), id, form, by());
        return group(id);
    }

    @PostMapping("/menus")
    @ResponseStatus(HttpStatus.CREATED)
    @RoutePath("/admin/menu/edit/new")
    public Created createMenu(@Valid @RequestBody MenuForm form, HttpServletRequest request) {
        return new Created(writeService.createMenu(schoolId(request), form, by()));
    }

    @PutMapping("/menus/{id}")
    @RoutePath("/admin/menu/edit/{id}")
    public MenuRowDto updateMenu(@PathVariable String id, @Valid @RequestBody MenuForm form, HttpServletRequest request) {
        writeService.updateMenu(schoolId(request), id, form, by());
        return menu(id, request);
    }

    @PostMapping("/schools")
    @ResponseStatus(HttpStatus.CREATED)
    @RoutePath("/admin/school/edit/new")
    public Created createSchool(@Valid @RequestBody SchoolForm form) {
        return new Created(writeService.createSchool(form, by()));
    }

    @PutMapping("/schools/{id}")
    @RoutePath("/admin/school/edit/{id}")
    public SchoolRowDto updateSchool(@PathVariable String id, @Valid @RequestBody SchoolForm form) {
        writeService.updateSchool(id, form, by());
        return school(id);
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    @RoutePath("/admin/user/administration/edit/new")
    public Created createUser(@Valid @RequestBody UserForm form, HttpServletRequest request) {
        return new Created(writeService.createUser(schoolId(request), form, by()));
    }

    @PutMapping("/users/{id}")
    @RoutePath("/admin/user/administration/edit/{id}")
    public UserDetailDto updateUser(@PathVariable String id, @Valid @RequestBody UserForm form, HttpServletRequest request) {
        writeService.updateUser(schoolId(request), id, form, by());
        return user(id, request);
    }

    // =====================  Suppressions logiques et mot de passe  =====================

    @DeleteMapping("/menus/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RoutePath("/admin/menu/delete/{id}")
    public void deleteMenu(@PathVariable String id, HttpServletRequest request) {
        writeService.deleteMenu(schoolId(request), id, by());
    }

    @DeleteMapping("/schools/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RoutePath("/admin/school/delete/{id}")
    public void deleteSchool(@PathVariable String id, HttpServletRequest request) {
        EfdlUserPrincipal user = RoutePathInterceptor.currentUser();
        writeService.deleteSchool(id, schoolId(request), user == null ? null : user.schoolExternalId(), by());
    }

    /** Événement deleteUser de /admin/user/administration/edit/{id}. */
    @DeleteMapping("/users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RoutePath("/admin/user/administration/edit/{id}")
    public void deleteUser(@PathVariable String id, HttpServletRequest request) {
        writeService.deleteUser(schoolId(request), id, by());
    }

    /** Réinitialisation du mot de passe depuis la fiche : provisoire, à changer à la prochaine connexion. */
    @PostMapping("/users/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RoutePath("/admin/user/administration/edit/{id}")
    public void resetPassword(@PathVariable String id, @Valid @RequestBody PasswordResetForm form, HttpServletRequest request) {
        passwordService.resetByAdmin(writeService.editableUser(schoolId(request), id), form.password(), by());
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    static class NotFoundException extends RuntimeException {
        NotFoundException() {
            super("Élément introuvable");
        }
    }
}
