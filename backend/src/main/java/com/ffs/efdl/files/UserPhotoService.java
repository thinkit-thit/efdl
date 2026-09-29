package com.ffs.efdl.files;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Date;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.ffs.efdl.model.BaseEntity;
import com.ffs.efdl.service.DocumentWriter;

/**
 * Photo de profil : collection {@code user_image} (UserImage : userExternalId, filename, filePath),
 * fichier dans le dossier user.image.folder.
 * Remplacement : le document existant est mis à jour ; le fichier précédent reste sur disque.
 */
@Service
public class UserPhotoService {

    private static final Logger log = LoggerFactory.getLogger(UserPhotoService.class);

    static final String COLLECTION = "user_image";
    static final String CLASS_NAME = "com.ffs.kiwi.model.image.UserImage";
    static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Set<String> EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif");

    /** Photo servie : fichier et type. */
    public record Photo(Path file, String contentType) {
    }

    private final MongoTemplate mongo;
    private final FileStorage storage;
    private final DocumentWriter writer;

    public UserPhotoService(MongoTemplate mongo, FileStorage storage, DocumentWriter writer) {
        this.mongo = mongo;
        this.storage = storage;
        this.writer = writer;
    }

    /** Photo de l'utilisateur (réduite si {@code size} > 0), ou 404 s'il n'en a pas. */
    public Photo photo(String userExternalId, int size) throws IOException {
        Document image = current(userExternalId);
        if (image == null || image.getString("filename") == null) throw notFound();
        String filename = image.getString("filename");
        Path folder = storage.userImageFolder();
        Path file = size > 0 ? storage.resized(folder, filename, Math.min(size, 600)) : storage.existing(folder, filename);
        String type = image.getString("contentType");
        return new Photo(file, type != null && type.startsWith("image/") ? type : contentType(filename));
    }

    /** Enregistre (ou remplace) la photo : JPG, PNG ou GIF de 5 Mo au plus, vérifiée comme image. */
    public void upload(String userExternalId, MultipartFile file, String by) throws IOException {
        String extension = FileStorage.extension(file.getOriginalFilename());
        if (file.isEmpty() || !EXTENSIONS.contains(extension)) throw badRequest("Choisissez une image JPG, PNG ou GIF");
        if (file.getSize() > MAX_BYTES) throw badRequest("Image trop lourde (5 Mo au plus)");
        byte[] bytes = file.getBytes();
        if (ImageIO.read(new ByteArrayInputStream(bytes)) == null) throw badRequest("Ce fichier n'est pas une image lisible");

        Path folder = storage.userImageFolder();
        String filename = storage.save(folder, userExternalId, extension, new ByteArrayInputStream(bytes));
        Map<String, Object> fields = DocumentWriter.fields("userExternalId", userExternalId,
                "filePath", folder.resolve(filename).toString(), "filename", filename,
                "contentType", contentType(filename), "dateImage", new Date());
        Document existing = current(userExternalId);
        if (existing != null && existing.getString("externalId") != null) {
            writer.update(COLLECTION, existing.getString("externalId"), null, fields, by);
        } else if (existing != null) {
            Update update = new Update().set("lastUpdateDate", new Date()).set("lastUpdateUserId", by);
            fields.forEach(update::set);
            mongo.updateFirst(Query.query(Criteria.where("_id").is(existing.get("_id"))), update, COLLECTION);
        } else {
            writer.insert(COLLECTION, CLASS_NAME, DocumentWriter.newExternalId(), null, false, fields, by);
        }
        log.info("Photo de profil enregistrée pour l'utilisateur {} par {}", userExternalId, by);
    }

    /** Utilisateurs (parmi {@code userIds}) ayant une photo : une seule requête pour une liste. */
    public Set<String> withPhoto(java.util.Collection<String> userIds) {
        Query query = Query.query(Criteria.where("userExternalId").in(userIds).and("status").ne(BaseEntity.STATUS_DELETED)
                .and("filename").exists(true));
        return new java.util.HashSet<>(mongo.findDistinct(query, "userExternalId", COLLECTION, String.class));
    }

    /** Dernière photo active de l'utilisateur. */
    private Document current(String userExternalId) {
        Query query = Query.query(Criteria.where("userExternalId").is(userExternalId).and("status").ne(BaseEntity.STATUS_DELETED))
                .with(Sort.by(Sort.Direction.DESC, "lastUpdateDate"));
        return mongo.findOne(query, Document.class, COLLECTION);
    }

    static String contentType(String filename) {
        return switch (FileStorage.extension(filename)) {
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            default -> "image/jpeg";
        };
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Pas de photo");
    }
}
