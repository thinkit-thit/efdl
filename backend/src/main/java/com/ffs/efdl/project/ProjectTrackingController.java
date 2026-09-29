package com.ffs.efdl.project;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ffs.efdl.model.School;
import com.ffs.efdl.project.DashboardService.Dashboard;
import com.ffs.efdl.project.DashboardService.Home;
import com.ffs.efdl.project.ProgressService.Level;
import com.ffs.efdl.project.ProgressService.MilestoneInput;
import com.ffs.efdl.project.ProgressService.Progress;
import com.ffs.efdl.security.EfdlUserPrincipal;
import com.ffs.efdl.security.RoutePath;
import com.ffs.efdl.security.RoutePathInterceptor;
import com.ffs.efdl.security.TenantResolver;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Suivi d'avancement et tableaux de bord FDL PROJET ; chaque appel est contrôlé sur la route de l'écran
 * (…/avancement/…, …/dashboard/…, /home).
 */
@RestController
@RequestMapping("/api")
public class ProjectTrackingController {

    public record ProgressBody(List<MilestoneInput> jalons) {
    }

    private final ProgressService progress;
    private final DashboardService dashboards;
    private final TenantResolver tenantResolver;
    private final DashboardExportService exports;

    public ProjectTrackingController(ProgressService progress, DashboardService dashboards, TenantResolver tenantResolver,
                                     DashboardExportService exports) {
        this.exports = exports;
        this.progress = progress;
        this.dashboards = dashboards;
        this.tenantResolver = tenantResolver;
    }

    // ---------- avancement ----------

    @GetMapping("/progress/programme/{id}")
    @RoutePath("/admin/project/programme/avancement/{id}")
    public Progress programmeProgress(@PathVariable String id, HttpServletRequest request) {
        return progress.get(school(request), Level.PROGRAMME, id, Map.of());
    }

    @PutMapping("/progress/programme/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RoutePath("/admin/project/programme/avancement/{id}")
    public void saveProgrammeProgress(@PathVariable String id, @RequestBody ProgressBody body, HttpServletRequest request) {
        progress.save(school(request), Level.PROGRAMME, id, Map.of(), body.jalons(), user().externalId());
    }

    @GetMapping("/progress/programme/{programmeId}/subProgramme/{id}")
    @RoutePath("/admin/project/programme/{programmeId}/avancement/{id}")
    public Progress subProgrammeProgress(@PathVariable String programmeId, @PathVariable String id, HttpServletRequest request) {
        return progress.get(school(request), Level.PROJET, id, Map.of("programmeId", programmeId));
    }

    @PutMapping("/progress/programme/{programmeId}/subProgramme/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RoutePath("/admin/project/programme/{programmeId}/avancement/{id}")
    public void saveSubProgrammeProgress(@PathVariable String programmeId, @PathVariable String id, @RequestBody ProgressBody body,
                                         HttpServletRequest request) {
        progress.save(school(request), Level.PROJET, id, Map.of("programmeId", programmeId), body.jalons(), user().externalId());
    }

    @GetMapping("/progress/programme/{programmeId}/subProgramme/{subProgrammeId}/project/{id}")
    @RoutePath("/admin/project/programme/{programmeId}/subProgramme/{subProgrammeId}/avancement/{id}")
    public Progress projectProgress(@PathVariable String programmeId, @PathVariable String subProgrammeId, @PathVariable String id,
                                    HttpServletRequest request) {
        return progress.get(school(request), Level.SOUS_PROJET, id, Map.of("programmeId", programmeId, "subProgrammeId", subProgrammeId));
    }

    @PutMapping("/progress/programme/{programmeId}/subProgramme/{subProgrammeId}/project/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RoutePath("/admin/project/programme/{programmeId}/subProgramme/{subProgrammeId}/avancement/{id}")
    public void saveProjectProgress(@PathVariable String programmeId, @PathVariable String subProgrammeId, @PathVariable String id,
                                    @RequestBody ProgressBody body, HttpServletRequest request) {
        progress.save(school(request), Level.SOUS_PROJET, id, Map.of("programmeId", programmeId, "subProgrammeId", subProgrammeId),
                body.jalons(), user().externalId());
    }

    // ---------- tableaux de bord ----------

    @GetMapping("/dashboards/programme/{id}")
    @RoutePath("/admin/project/programme/dashboard/{id}")
    public Dashboard programmeDashboard(@PathVariable String id, HttpServletRequest request) {
        return dashboards.programme(school(request), id);
    }

    /**
     * Export du tableau de bord d'un programme ({@code format} = pdf ou docx) ; {@code map} : image PNG de la carte
     * affichée (facultative).
     */
    @org.springframework.web.bind.annotation.PostMapping("/dashboards/programme/{id}/export")
    @RoutePath("/admin/project/programme/dashboard/{id}")
    public org.springframework.http.ResponseEntity<byte[]> exportProgrammeDashboard(@PathVariable String id,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "pdf") String format,
            @org.springframework.web.bind.annotation.RequestParam(required = false) org.springframework.web.multipart.MultipartFile map,
            HttpServletRequest request) throws java.io.IOException {
        boolean pdf = !"docx".equalsIgnoreCase(format);
        byte[] content = exports.programme(school(request), id, pdf ? DashboardExportService.Format.PDF : DashboardExportService.Format.DOCX,
                map == null ? null : map.getBytes());
        String title = dashboards.programme(school(request), id).title();
        String name = "tableau-de-bord-" + (title == null ? id : title.replaceAll("[^A-Za-z0-9_-]+", "_")) + (pdf ? ".pdf" : ".docx");
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, org.springframework.http.ContentDisposition.attachment()
                        .filename(name, java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .contentType(org.springframework.http.MediaType.parseMediaType(pdf ? "application/pdf"
                        : "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(content);
    }

    /** Export du tableau de bord d'un projet (sous-programme), comme celui d'un programme. */
    @org.springframework.web.bind.annotation.PostMapping("/dashboards/programme/{programmeId}/subProgramme/{id}/export")
    @RoutePath("/admin/project/programme/{programmeId}/dashboard/{id}")
    public org.springframework.http.ResponseEntity<byte[]> exportSubProgrammeDashboard(@PathVariable String programmeId, @PathVariable String id,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "pdf") String format,
            @org.springframework.web.bind.annotation.RequestParam(required = false) org.springframework.web.multipart.MultipartFile map,
            HttpServletRequest request) throws java.io.IOException {
        boolean pdf = !"docx".equalsIgnoreCase(format);
        byte[] content = exports.subProgramme(school(request), programmeId, id,
                pdf ? DashboardExportService.Format.PDF : DashboardExportService.Format.DOCX, map == null ? null : map.getBytes());
        DashboardService.Dashboard d = dashboards.subProgramme(school(request), programmeId, id);
        String title = (d.programmeTitle() == null ? "" : d.programmeTitle() + "-") + (d.title() == null ? id : d.title());
        return exportResponse("tableau-de-bord-projet-" + title.replaceAll("[^A-Za-z0-9_-]+", "_"), pdf, content);
    }

    private static org.springframework.http.ResponseEntity<byte[]> exportResponse(String base, boolean pdf, byte[] content) {
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, org.springframework.http.ContentDisposition.attachment()
                        .filename(base + (pdf ? ".pdf" : ".docx"), java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .contentType(org.springframework.http.MediaType.parseMediaType(pdf ? "application/pdf"
                        : "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(content);
    }

    @GetMapping("/dashboards/programme/{programmeId}/subProgramme/{id}")
    @RoutePath("/admin/project/programme/{programmeId}/dashboard/{id}")
    public Dashboard subProgrammeDashboard(@PathVariable String programmeId, @PathVariable String id, HttpServletRequest request) {
        return dashboards.subProgramme(school(request), programmeId, id);
    }

    @GetMapping("/dashboards/home")
    @RoutePath("/home")
    public Home home(@RequestParam(required = false) String programmeId, HttpServletRequest request) {
        return dashboards.home(school(request), programmeId == null || programmeId.isBlank() ? null : programmeId);
    }

    private static EfdlUserPrincipal user() {
        return RoutePathInterceptor.currentUser();
    }

    private String school(HttpServletRequest request) {
        return tenantResolver.resolve(request).map(School::getExternalId).orElse(user().schoolExternalId());
    }
}
