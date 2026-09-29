package com.ffs.efdl.files;

import java.io.IOException;
import java.nio.file.Files;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.ffs.efdl.model.School;
import com.ffs.efdl.model.UserType;
import com.ffs.efdl.repository.UserRepository;
import com.ffs.efdl.security.EfdlUserPrincipal;
import com.ffs.efdl.security.RoutePath;
import com.ffs.efdl.security.RoutePathInterceptor;
import com.ffs.efdl.security.TenantResolver;
import com.ffs.efdl.service.AdminWriteService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Photos de profil (/image/user/{id}?size=…) : affichage pour les utilisateurs de la même application,
 * envoi par la fiche utilisateur (droit de la route de la fiche) ou par l'utilisateur lui-même.
 */
@RestController
@RequestMapping("/api")
public class UserPhotoController {

    private final UserPhotoService photos;
    private final UserRepository users;
    private final AdminWriteService adminWriteService;
    private final TenantResolver tenantResolver;

    public UserPhotoController(UserPhotoService photos, UserRepository users, AdminWriteService adminWriteService,
                               TenantResolver tenantResolver) {
        this.photos = photos;
        this.users = users;
        this.adminWriteService = adminWriteService;
        this.tenantResolver = tenantResolver;
    }

    @GetMapping("/users/{id}/photo")
    public ResponseEntity<Resource> photo(@PathVariable String id, @RequestParam(defaultValue = "0") int size,
                                          WebRequest webRequest) throws IOException {
        EfdlUserPrincipal me = RoutePathInterceptor.currentUser();
        boolean visible = users.findFirstByExternalId(id)
                .filter(u -> !u.isDeleted())
                .filter(u -> me.type() == UserType.ADMIN || me.externalId().equals(u.getExternalId())
                        || me.schoolExternalId() != null && me.schoolExternalId().equals(u.getSchoolExternalId()))
                .isPresent();
        if (!visible) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pas de photo");
        UserPhotoService.Photo photo = photos.photo(id, size);
        long modified = Files.getLastModifiedTime(photo.file()).toMillis();
        if (webRequest.checkNotModified(modified)) return null;
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache().cachePrivate())
                .lastModified(modified)
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(photo.contentType()))
                .body(new FileSystemResource(photo.file()));
    }

    /** Fiche utilisateur ADMINISTRATION (« Ajouter / Modifier l'image »). */
    @PostMapping("/admin/users/{id}/photo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RoutePath("/admin/user/administration/edit/{id}")
    public void uploadForUser(@PathVariable String id, @RequestParam("file") MultipartFile file,
                              HttpServletRequest request) throws IOException {
        adminWriteService.editableUser(school(request), id);
        photos.upload(id, file, RoutePathInterceptor.currentUser().externalId());
    }

    /** Sa propre photo (page « Mon profil »). */
    @PostMapping("/auth/me/photo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void uploadOwn(@RequestParam("file") MultipartFile file) throws IOException {
        EfdlUserPrincipal me = RoutePathInterceptor.currentUser();
        photos.upload(me.externalId(), file, me.externalId());
    }

    private String school(HttpServletRequest request) {
        EfdlUserPrincipal user = RoutePathInterceptor.currentUser();
        return tenantResolver.resolve(request).map(School::getExternalId).orElse(user.schoolExternalId());
    }
}
