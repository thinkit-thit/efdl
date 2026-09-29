package com.ffs.efdl.imports;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.ffs.efdl.imports.ImportService.Report;
import com.ffs.efdl.model.School;
import com.ffs.efdl.security.EfdlUserPrincipal;
import com.ffs.efdl.security.ImportPermission;
import com.ffs.efdl.security.RoutePath;
import com.ffs.efdl.security.RoutePathInterceptor;
import com.ffs.efdl.security.TenantResolver;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Imports Excel, contrôlés sur la route de l'écran qui les propose.
 * {@code apply=false} : analyse seule (rien n'est écrit) ; {@code apply=true} : import.
 * En plus de la route : réservés à l'administrateur et au « Validateur info » de l'application ({@link ImportPermission}).
 */
@RestController
@RequestMapping("/api/imports")
public class ImportController {

    private final ImportService service;
    private final LocalisationImportService localisations;
    private final TenantResolver tenantResolver;
    private final ImportPermission importPermission;

    public ImportController(ImportService service, LocalisationImportService localisations, TenantResolver tenantResolver,
                            ImportPermission importPermission) {
        this.service = service;
        this.localisations = localisations;
        this.tenantResolver = tenantResolver;
        this.importPermission = importPermission;
    }

    /** Découpage administratif et population (feuilles « base » et population), depuis le tableau de bord des localisations. */
    @PostMapping("/localisations")
    @RoutePath("/admin/dashboard/election/tbs")
    public Report localisations(@RequestParam("file") MultipartFile file, @RequestParam(defaultValue = "false") boolean apply,
                                HttpServletRequest request) throws IOException {
        try (InputStream in = open(file)) {
            return localisations.importFile(in, school(request), apply, by());
        }
    }

    @PostMapping("/prestataires")
    @RoutePath("/admin/project/prestataires")
    public Report prestataires(@RequestParam("file") MultipartFile file, @RequestParam(defaultValue = "false") boolean apply,
                               HttpServletRequest request) throws IOException {
        try (InputStream in = open(file)) {
            return service.prestataires(in, school(request), apply, by());
        }
    }

    @PostMapping("/paiements")
    @RoutePath("/admin/project/paiement/edit/new")
    public Report paiements(@RequestParam("file") MultipartFile file, @RequestParam(defaultValue = "false") boolean apply,
                            @RequestParam(defaultValue = "true") boolean historique, HttpServletRequest request) throws IOException {
        try (InputStream in = open(file)) {
            return service.paiements(in, school(request), historique, apply, by());
        }
    }

    @PostMapping("/programme/{programmeId}/subProgramme/{subProgrammeId}/projects")
    @RoutePath("/admin/project/programme/{programmeId}/subProgramme/{subProgrammeId}/upload")
    public Report projects(@PathVariable String programmeId, @PathVariable String subProgrammeId,
                           @RequestParam("file") MultipartFile file, @RequestParam(defaultValue = "false") boolean apply,
                           HttpServletRequest request) throws IOException {
        try (InputStream in = open(file)) {
            return service.projects(in, school(request), programmeId, subProgrammeId, apply, by());
        }
    }

    private static InputStream open(MultipartFile file) throws IOException {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (file.isEmpty() || !(name.endsWith(".xlsx") || name.endsWith(".xls"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choisissez un fichier Excel (.xlsx ou .xls)");
        }
        return file.getInputStream();
    }

    private static String by() {
        EfdlUserPrincipal user = RoutePathInterceptor.currentUser();
        return user == null ? null : user.externalId();
    }

    /** École de la requête, après contrôle du droit d'import. */
    private String school(HttpServletRequest request) {
        String school = tenantResolver.resolve(request).map(School::getExternalId)
                .orElse(RoutePathInterceptor.currentUser().schoolExternalId());
        if (!importPermission.canImport(RoutePathInterceptor.currentUser(), school)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Import réservé au validateur info de l'application et à l'administrateur");
        }
        return school;
    }
}
