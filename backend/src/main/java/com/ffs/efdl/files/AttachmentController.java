package com.ffs.efdl.files;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ffs.efdl.dto.AdminWriteDtos.Created;
import com.ffs.efdl.files.AttachmentService.Listing;
import com.ffs.efdl.files.AttachmentService.Target;
import com.ffs.efdl.model.School;
import com.ffs.efdl.security.EfdlUserPrincipal;
import com.ffs.efdl.security.RoutePathInterceptor;
import com.ffs.efdl.security.TenantResolver;

import jakarta.servlet.http.HttpServletRequest;

/** Documents joints aux factures et à l'avancement des sous-projets (droits contrôlés par {@link AttachmentService}). */
@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {

    private final AttachmentService service;
    private final TenantResolver tenantResolver;

    public AttachmentController(AttachmentService service, TenantResolver tenantResolver) {
        this.service = service;
        this.tenantResolver = tenantResolver;
    }

    @GetMapping("/paiements/{id}")
    public Listing paiement(@PathVariable String id, HttpServletRequest request) {
        return service.list(user(), school(request), Target.PAIEMENT, id);
    }

    @PostMapping("/paiements/{id}")
    @ResponseStatus(HttpStatus.CREATED)
    public Created addToPaiement(@PathVariable String id, @RequestParam("file") MultipartFile file,
                                 @RequestParam(required = false) String title, HttpServletRequest request) throws IOException {
        return new Created(service.add(user(), school(request), Target.PAIEMENT, id, title, null, file));
    }

    /** Suivi d'avancement : {@code programmes}, {@code subProgrammes} (projets) ou {@code projects} (sous-projets). */
    @GetMapping("/{level:programmes|subProgrammes|projects}/{id}")
    public Listing progress(@PathVariable String level, @PathVariable String id, HttpServletRequest request) {
        return service.list(user(), school(request), target(level), id);
    }

    @PostMapping("/{level:programmes|subProgrammes|projects}/{id}")
    @ResponseStatus(HttpStatus.CREATED)
    public Created addToProgress(@PathVariable String level, @PathVariable String id, @RequestParam("file") MultipartFile file,
                                 @RequestParam(required = false) String title,
                                 @RequestParam(required = false) String avancementId, HttpServletRequest request) throws IOException {
        return new Created(service.add(user(), school(request), target(level), id, title, avancementId, file));
    }

    private static Target target(String level) {
        return switch (level) {
            case "programmes" -> Target.PROGRAMME;
            case "subProgrammes" -> Target.SUB_PROGRAMME;
            default -> Target.PROJECT;
        };
    }

    /** Téléchargement : PDF et images s'ouvrent dans le navigateur, les autres fichiers sont enregistrés. */
    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> file(@PathVariable String id, HttpServletRequest request) {
        AttachmentService.Download d = service.download(user(), school(request), id);
        boolean inline = d.contentType() != null && (d.contentType().startsWith("image/") || d.contentType().equals("application/pdf"));
        ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(d.filename() == null ? "document" : d.filename(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(d.contentType() == null ? "application/octet-stream" : d.contentType()))
                .body(new FileSystemResource(d.file()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id, HttpServletRequest request) {
        service.delete(user(), school(request), id);
    }

    private static EfdlUserPrincipal user() {
        return RoutePathInterceptor.currentUser();
    }

    private String school(HttpServletRequest request) {
        return tenantResolver.resolve(request).map(School::getExternalId).orElse(user().schoolExternalId());
    }
}
