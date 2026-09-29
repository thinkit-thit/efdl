package com.ffs.efdl.controller;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.ffs.efdl.model.School;
import com.ffs.efdl.security.EfdlUserPrincipal;
import com.ffs.efdl.security.ImportPermission;
import com.ffs.efdl.security.RoutePathInterceptor;
import com.ffs.efdl.security.TenantResolver;
import com.ffs.efdl.service.AuditPurgeService;
import com.ffs.efdl.service.AuditService;

import jakarta.servlet.http.HttpServletRequest;

/** Historique des modifications de l'application : administrateur et « Validateur info » seulement. */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService audit;
    private final ImportPermission permission;
    private final TenantResolver tenantResolver;
    private final AuditPurgeService purge;

    public AuditController(AuditService audit, ImportPermission permission, TenantResolver tenantResolver, AuditPurgeService purge) {
        this.purge = purge;
        this.audit = audit;
        this.permission = permission;
        this.tenantResolver = tenantResolver;
    }

    /** Filtres facultatifs ; {@code from} / {@code to} : jours (aaaa-mm-jj) inclus ; plus récentes d'abord. */
    @GetMapping
    public AuditService.Page search(@RequestParam(required = false) String userId, @RequestParam(required = false) String collection,
                                    @RequestParam(required = false) String action, @RequestParam(required = false) String from,
                                    @RequestParam(required = false) String to, @RequestParam(required = false) String q,
                                    @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size,
                                    HttpServletRequest request) {
        EfdlUserPrincipal user = RoutePathInterceptor.currentUser();
        String school = tenantResolver.resolve(request).map(School::getExternalId).orElse(user.schoolExternalId());
        if (!permission.canViewAudit(user, school)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Historique réservé au validateur info de l'application et à l'administrateur");
        }
        return audit.search(school, new AuditService.Filter(userId, collection, action, day(from, 0), day(to, 1), q),
                Math.max(page, 0), Math.min(Math.max(size, 1), 200));
    }

    /**
     * Suppression en masse des entrées filtrées (administrateur seul) : archive Excel enregistrée sur le serveur puis
     * renvoyée au navigateur ; {@code expected} = nombre affiché (refus si l'historique a changé).
     */
    @PostMapping("/purge")
    public ResponseEntity<Resource> purge(@RequestParam(required = false) String userId, @RequestParam(required = false) String collection,
                                          @RequestParam(required = false) String action, @RequestParam(required = false) String from,
                                          @RequestParam(required = false) String to, @RequestParam(required = false) String q,
                                          @RequestParam long expected, HttpServletRequest request) throws IOException {
        EfdlUserPrincipal user = RoutePathInterceptor.currentUser();
        String school = school(request, user);
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("Filtre utilisateur", userId);
        labels.put("Filtre élément", collection);
        labels.put("Filtre action", action);
        labels.put("Du", from);
        labels.put("Au", to);
        labels.put("Recherche", q);
        AuditPurgeService.Result result = purge.purge(user, school,
                new AuditService.Filter(userId, collection, action, day(from, 0), day(to, 1), q), expected, labels);
        return xlsx(purge.archive(user, school, result.archive()), result.archive());
    }

    /** Archives des suppressions en masse (administrateur seul). */
    @GetMapping("/archives")
    public java.util.List<AuditPurgeService.ArchiveFile> archives(HttpServletRequest request) throws IOException {
        EfdlUserPrincipal user = RoutePathInterceptor.currentUser();
        return purge.archives(user, school(request, user));
    }

    /** Une archive, administrateur seul. */
    @GetMapping("/archives/{name}")
    public ResponseEntity<Resource> archive(@PathVariable String name, HttpServletRequest request) {
        EfdlUserPrincipal user = RoutePathInterceptor.currentUser();
        return xlsx(purge.archive(user, school(request, user), name), name);
    }

    private static ResponseEntity<Resource> xlsx(Path file, String name) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new FileSystemResource(file));
    }

    private String school(HttpServletRequest request, EfdlUserPrincipal user) {
        return tenantResolver.resolve(request).map(School::getExternalId).orElse(user.schoolExternalId());
    }

    private static Date day(String value, int plusDays) {
        if (!StringUtils.hasText(value)) return null;
        try {
            return Date.from(LocalDate.parse(value).plusDays(plusDays).atStartOfDay(ZoneId.systemDefault()).toInstant());
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date invalide : " + value);
        }
    }
}
