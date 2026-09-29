package com.ffs.efdl.files;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.ffs.efdl.model.BaseEntity;
import com.ffs.efdl.security.EfdlUserPrincipal;
import com.ffs.efdl.security.PermissionService;
import com.ffs.efdl.service.DocumentWriter;

/**
 * Documents joints : factures ({@code Paiement}) et suivi d'avancement
 * des programmes, projets et sous-projets ({@code Programme}, {@code SubProgramme}, {@code Project} ; jalon facultatif).
 * Description dans la collection {@code attachment}, fichier sur disque. Droits = routes des écrans : fiche facture
 * (ajout) ou fiche de validation (consultation) ; page d'avancement du niveau. Suppression logique ; sur une facture,
 * seulement tant qu'elle est en attente (même verrou que la fiche).
 */
@Service
public class AttachmentService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);

    static final String COLLECTION = "attachment";
    static final String CLASS_NAME = "com.ffs.efdl.model.Attachment";
    static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry("pdf", "application/pdf"), Map.entry("jpg", "image/jpeg"), Map.entry("jpeg", "image/jpeg"),
            Map.entry("png", "image/png"), Map.entry("gif", "image/gif"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("ppt", "application/vnd.ms-powerpoint"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("odt", "application/vnd.oasis.opendocument.text"),
            Map.entry("ods", "application/vnd.oasis.opendocument.spreadsheet"),
            Map.entry("txt", "text/plain"), Map.entry("csv", "text/csv"), Map.entry("zip", "application/zip"));

    /** Élément porteur : collection et libellé « introuvable ». */
    public enum Target {
        PAIEMENT("Paiement", "Facture introuvable"), PROGRAMME("Programme", "Programme introuvable"),
        SUB_PROGRAMME("SubProgramme", "Projet introuvable"), PROJECT("Project", "Sous-projet introuvable");

        final String collection;
        final String notFound;

        Target(String collection, String notFound) {
            this.collection = collection;
            this.notFound = notFound;
        }

        boolean progress() {
            return this != PAIEMENT;
        }
    }

    public record Item(String externalId, String title, String originalFilename, String contentType, long size,
                       String avancementExternalId, String avancementTitle, Date creationDate, String createdBy) {
    }

    public record Listing(List<Item> items, boolean canAdd, boolean canDelete) {
    }

    public record Download(Path file, String contentType, String filename) {
    }

    private final MongoTemplate mongo;
    private final FileStorage storage;
    private final DocumentWriter writer;
    private final PermissionService permissions;

    public AttachmentService(MongoTemplate mongo, FileStorage storage, DocumentWriter writer, PermissionService permissions) {
        this.mongo = mongo;
        this.storage = storage;
        this.writer = writer;
        this.permissions = permissions;
    }

    // ---------- lecture ----------

    public Listing list(EfdlUserPrincipal user, String school, Target target, String targetId) {
        Document owner = owner(school, target, targetId);
        if (!canView(user, target, owner)) throw forbidden();
        boolean canAdd = canAdd(user, target, owner);
        Map<String, String> jalons = new HashMap<>();
        mongo.find(Query.query(Criteria.where("schoolExternalId").is(school)), Document.class, "Avancement")
                .forEach(a -> jalons.put(a.getString("externalId"), a.getString("title")));
        List<Item> items = mongo.find(Query.query(active(school).and("targetType").is(target.name()).and("targetExternalId").is(targetId))
                        .with(Sort.by("creationDate")), Document.class, COLLECTION).stream()
                .map(d -> new Item(d.getString("externalId"), d.getString("title"), d.getString("originalFilename"),
                        d.getString("contentType"), d.get("size") instanceof Number n ? n.longValue() : 0,
                        d.getString("avancementExternalId"), jalons.get(d.getString("avancementExternalId")),
                        d.getDate("creationDate"), userName(d.getString("creationUserId"))))
                .toList();
        return new Listing(items, canAdd, canAdd && deletable(target, owner));
    }

    public Download download(EfdlUserPrincipal user, String school, String id) {
        Document d = attachment(school, id);
        Target target = Target.valueOf(d.getString("targetType"));
        if (!canView(user, target, owner(school, target, d.getString("targetExternalId")))) throw forbidden();
        Path file = storage.existing(storage.attachmentFolder(school), d.getString("filename"));
        return new Download(file, d.getString("contentType"), d.getString("originalFilename"));
    }

    // ---------- écriture ----------

    public String add(EfdlUserPrincipal user, String school, Target target, String targetId, String title,
                      String avancementId, MultipartFile file) throws IOException {
        Document owner = owner(school, target, targetId);
        if (!canAdd(user, target, owner)) throw forbidden();
        String original = file.getOriginalFilename() == null ? "" : Path.of(file.getOriginalFilename()).getFileName().toString();
        String extension = FileStorage.extension(original);
        if (file.isEmpty() || !TYPES.containsKey(extension)) {
            throw badRequest("Type de fichier non accepté (PDF, images, Word, Excel, PowerPoint, texte, CSV ou ZIP)");
        }
        if (file.getSize() > MAX_BYTES) throw badRequest("Fichier trop lourd (10 Mo au plus)");
        String jalon = StringUtils.hasText(avancementId) && target.progress() ? avancementId : null;
        if (jalon != null && !mongo.exists(Query.query(active(school).and("externalId").is(jalon)), "Avancement")) {
            throw badRequest("Jalon d'avancement introuvable");
        }
        String filename;
        try (InputStream in = file.getInputStream()) {
            filename = storage.save(storage.attachmentFolder(school), target.name().toLowerCase(), extension, in);
        }
        String cleanTitle = StringUtils.hasText(title) ? title.trim() : original;
        String id = writer.insert(COLLECTION, CLASS_NAME, DocumentWriter.newExternalId(), school,
                DocumentWriter.fields("targetType", target.name(), "targetExternalId", targetId,
                        "avancementExternalId", jalon, "title", cleanTitle.length() > 200 ? cleanTitle.substring(0, 200) : cleanTitle,
                        "originalFilename", original, "filename", filename, "contentType", TYPES.get(extension),
                        "size", file.getSize(), "creationUserId", user.externalId()), user.externalId());
        log.info("Document {} ajouté à {} {} par {}", id, target, targetId, user.externalId());
        return id;
    }

    public void delete(EfdlUserPrincipal user, String school, String id) {
        Document d = attachment(school, id);
        Target target = Target.valueOf(d.getString("targetType"));
        Document owner = owner(school, target, d.getString("targetExternalId"));
        if (!canAdd(user, target, owner)) throw forbidden();
        if (!deletable(target, owner)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Facture déjà traitée : ses documents ne peuvent plus être supprimés");
        }
        writer.markDeleted(COLLECTION, id, school, user.externalId());
    }

    // ---------- droits ----------

    private boolean canView(EfdlUserPrincipal user, Target target, Document owner) {
        String id = owner.getString("externalId");
        if (target == Target.PAIEMENT) {
            return permissions.canAccessRoute(user, "/admin/project/paiement/edit/" + id)
                    || permissions.canAccessRoute(user, "/admin/project/validationPaiement/edit/" + id);
        }
        return canAdd(user, target, owner);
    }

    private boolean canAdd(EfdlUserPrincipal user, Target target, Document owner) {
        String id = owner.getString("externalId");
        // Routes des pages d'avancement (une par niveau).
        String route = switch (target) {
            case PAIEMENT -> "/admin/project/paiement/edit/" + id;
            case PROGRAMME -> "/admin/project/programme/avancement/" + id;
            case SUB_PROGRAMME -> "/admin/project/programme/" + owner.getString("programmeExternalId") + "/avancement/" + id;
            case PROJECT -> "/admin/project/programme/" + owner.getString("programmeExternalId")
                    + "/subProgramme/" + owner.getString("subProgrammeExternalId") + "/avancement/" + id;
        };
        return permissions.canAccessRoute(user, route);
    }

    private static boolean deletable(Target target, Document owner) {
        return target != Target.PAIEMENT || "PENDING".equals(owner.getString("status"));
    }

    // ---------- utilitaires ----------

    private Document owner(String school, Target target, String id) {
        Document d = mongo.findOne(Query.query(active(school).and("externalId").is(id)), Document.class, target.collection);
        if (d == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, target.notFound);
        return d;
    }

    private Document attachment(String school, String id) {
        Document d = mongo.findOne(Query.query(active(school).and("externalId").is(id)), Document.class, COLLECTION);
        if (d == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document introuvable");
        return d;
    }

    private String userName(String userExternalId) {
        if (userExternalId == null) return null;
        Document u = mongo.findOne(Query.query(Criteria.where("externalId").is(userExternalId)), Document.class, "user");
        if (u == null) return null;
        String name = ((u.getString("lastName") == null ? "" : u.getString("lastName"))
                + (u.getString("firstName") == null ? "" : " " + u.getString("firstName"))).trim();
        return name.isEmpty() ? u.getString("username") : name;
    }

    private static Criteria active(String school) {
        return Criteria.where("schoolExternalId").is(school).and("status").ne(BaseEntity.STATUS_DELETED);
    }

    private static ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès refusé");
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
