package com.ffs.efdl.service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
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
import org.springframework.web.server.ResponseStatusException;

import com.ffs.efdl.files.FileStorage;
import com.ffs.efdl.model.UserType;
import com.ffs.efdl.security.EfdlUserPrincipal;

/**
 * Suppression en masse de l'historique (« export Excel puis suppression physique », administrateur
 * seul). Exception limitée à la collection {@code audit_log} : les entrées filtrées sont d'abord
 * archivées dans un fichier Excel sur le serveur, puis effacées définitivement ; aucune ligne ne reste dans
 * l'historique : la purge est notée dans le journal du serveur, l'archive reste sur disque.
 */
@Service
public class AuditPurgeService {

    private static final Logger log = LoggerFactory.getLogger(AuditPurgeService.class);

    public record Result(String archive, long deleted) {
    }

    private final MongoTemplate mongo;
    private final AuditService audit;
    private final FileStorage storage;

    public AuditPurgeService(MongoTemplate mongo, AuditService audit, FileStorage storage) {
        this.mongo = mongo;
        this.audit = audit;
        this.storage = storage;
    }

    /**
     * Archive puis supprime les entrées filtrées. {@code expected} = nombre affiché à l'utilisateur : refus si des
     * entrées sont arrivées entre-temps (on ne supprime jamais plus que ce qui a été vu).
     */
    public Result purge(EfdlUserPrincipal user, String school, AuditService.Filter filter, long expected, Map<String, String> filterLabels)
            throws IOException {
        if (user == null || user.type() != UserType.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Suppression de l'historique réservée à l'administrateur");
        }
        Criteria criteria = audit.criteria(school, filter);
        Query query = Query.query(criteria);
        long count = mongo.count(query, AuditService.COLLECTION);
        if (count == 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aucune entrée à supprimer");
        if (count != expected) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "L'historique a changé (" + count + " entrées au lieu de " + expected + ") : rechargez la page avant de supprimer");
        }

        // 1. Archive Excel sur le serveur (avant toute suppression).
        Path folder = storage.archiveFolder(school);
        Files.createDirectories(folder);
        String name = "historique-" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + "-"
                + UUID.randomUUID().toString().substring(0, 8) + ".xlsx";
        Path file = storage.existingOrNew(folder, name);
        List<Object> ids = new ArrayList<>();
        try (SXSSFWorkbook wb = new SXSSFWorkbook(200); OutputStream out = Files.newOutputStream(file)) {
            Sheet sheet = wb.createSheet("Historique");
            CellStyle bold = wb.createCellStyle();
            Font font = wb.createFont();
            font.setBold(true);
            bold.setFont(font);
            String[] headers = { "Date", "Utilisateur", "Action", "Élément", "Libellé", "Détail (avant → après)" };
            Row h = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                h.createCell(i).setCellValue(headers[i]);
                h.getCell(i).setCellStyle(bold);
            }
            SimpleDateFormat fmt = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
            int n = 1;
            for (Document d : mongo.find(Query.query(criteria).with(Sort.by(Sort.Direction.DESC, "date")), Document.class,
                    AuditService.COLLECTION)) {
                ids.add(d.get("_id"));
                AuditService.Entry e = AuditService.entry(d);
                Row r = sheet.createRow(n++);
                r.createCell(0).setCellValue(e.date() == null ? "" : fmt.format(e.date()));
                r.createCell(1).setCellValue(nz(e.userName()));
                r.createCell(2).setCellValue(nz(e.action()));
                r.createCell(3).setCellValue(nz(e.entityLabel()));
                r.createCell(4).setCellValue(nz(e.targetLabel()));
                r.createCell(5).setCellValue(String.join("\n", e.changes().stream().map(c -> nz(c.label() == null ? c.field() : c.label())
                        + " : " + ("CREATION".equals(e.action()) ? nz(c.after()) : nz(c.before()) + " → " + nz(c.after()))).toList()));
            }
            wb.write(out);
            wb.dispose();
        }

        // 2. Suppression des seules entrées archivées.
        long deleted = mongo.remove(Query.query(Criteria.where("_id").in(ids)), AuditService.COLLECTION).getDeletedCount();

        // 3. Aucune trace en base : journal du serveur seulement (qui, école, filtres, nombre, archive).
        StringBuilder filters = new StringBuilder();
        filterLabels.forEach((label, value) -> {
            if (StringUtils.hasText(value)) filters.append(filters.isEmpty() ? "" : ", ").append(label).append("=").append(value);
        });
        log.info("Historique : {} entrée(s) supprimée(s) définitivement par {} (école {}, filtres [{}], archive {})", deleted,
                user.externalId(), school, filters, name);
        return new Result(name, deleted);
    }

    public record ArchiveFile(String name, Date date, long size) {
    }

    /** Archives des purges de l'école, plus récentes d'abord (administrateur seul). */
    public List<ArchiveFile> archives(EfdlUserPrincipal user, String school) throws IOException {
        checkAdmin(user);
        Path folder = storage.archiveFolder(school);
        if (!Files.isDirectory(folder)) return List.of();
        try (var files = Files.list(folder)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".xlsx"))
                    .map(p -> {
                        try {
                            return new ArchiveFile(p.getFileName().toString(), new Date(Files.getLastModifiedTime(p).toMillis()), Files.size(p));
                        } catch (IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    })
                    .sorted((a, b) -> b.date().compareTo(a.date())).toList();
        }
    }

    private static void checkAdmin(EfdlUserPrincipal user) {
        if (user == null || user.type() != UserType.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Archives de l'historique réservées à l'administrateur");
        }
    }

    /** Archive d'une purge (administrateur seul). */
    public Path archive(EfdlUserPrincipal user, String school, String name) {
        if (user == null || user.type() != UserType.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Archives de l'historique réservées à l'administrateur");
        }
        return storage.existing(storage.archiveFolder(school), name);
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}
