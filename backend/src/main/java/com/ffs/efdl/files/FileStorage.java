package com.ffs.efdl.files;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Fichiers sur disque (dossiers configurés : user.image.folder…) ; la base ne
 * garde que la description (nom du fichier, type…). Les noms sur disque sont générés : jamais le nom envoyé par
 * l'utilisateur, et tout chemin est vérifié pour rester dans son dossier.
 */
@Service
public class FileStorage {

    private final Path root;
    private final Path userImageFolder;

    public FileStorage(@Value("${efdl.files.root:./data/files}") String root,
                       @Value("${efdl.files.user-image-folder:}") String userImageFolder) {
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.userImageFolder = userImageFolder.isBlank() ? this.root.resolve("image/user")
                : Path.of(userImageFolder).toAbsolutePath().normalize();
    }

    /** Dossier des photos de profil (user.image.folder). */
    public Path userImageFolder() {
        return userImageFolder;
    }

    /** Dossier des pièces jointes d'une école (documents d'avancement, factures). */
    public Path attachmentFolder(String school) {
        return inside(root.resolve("attachments"), safeSegment(school));
    }

    /** Archives Excel des purges de l'historique d'une école. */
    public Path archiveFolder(String school) {
        return inside(root.resolve("audit-archives"), safeSegment(school));
    }

    /** Chemin d'un fichier (existant ou à créer) du dossier, vérifié pour y rester. */
    public Path existingOrNew(Path folder, String filename) {
        return inside(folder, filename);
    }

    /** Enregistre le flux sous un nom généré « préfixe-uuid.extension » ; renvoie ce nom. */
    public String save(Path folder, String prefix, String extension, InputStream in) throws IOException {
        Files.createDirectories(folder);
        String name = safeSegment(prefix) + "-" + UUID.randomUUID() + "." + extension.toLowerCase(Locale.ROOT);
        Files.copy(in, inside(folder, name), StandardCopyOption.REPLACE_EXISTING);
        return name;
    }

    /** Fichier existant du dossier (404 sinon). */
    public Path existing(Path folder, String filename) {
        Path file = inside(folder, filename);
        if (!Files.isRegularFile(file)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fichier introuvable");
        return file;
    }

    /**
     * Image réduite à {@code size} pixels au plus (côté le plus long), mise en cache dans « dossier/size/nom » ;
     * l'original si elle est déjà plus petite ou illisible.
     */
    public Path resized(Path folder, String filename, int size) throws IOException {
        Path original = existing(folder, filename);
        Path cached = inside(folder.resolve(String.valueOf(size)), filename);
        if (Files.isRegularFile(cached)) return cached;
        BufferedImage src = ImageIO.read(original.toFile());
        if (src == null || Math.max(src.getWidth(), src.getHeight()) <= size) return original;
        double ratio = (double) size / Math.max(src.getWidth(), src.getHeight());
        int w = Math.max(1, (int) Math.round(src.getWidth() * ratio));
        int h = Math.max(1, (int) Math.round(src.getHeight() * ratio));
        String format = extension(filename);
        boolean alpha = src.getColorModel().hasAlpha() && !"jpg".equals(format) && !"jpeg".equals(format);
        BufferedImage out = new BufferedImage(w, h, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        Files.createDirectories(cached.getParent());
        if (!ImageIO.write(out, "jpeg".equals(format) ? "jpg" : format, cached.toFile())) return original;
        return cached;
    }

    /** Extension en minuscules (sans le point), ou chaîne vide. */
    public static String extension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static Path inside(Path folder, String name) {
        Path resolved = folder.resolve(name).normalize();
        if (!resolved.startsWith(folder.normalize()) || resolved.equals(folder.normalize())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nom de fichier invalide");
        }
        return resolved;
    }

    private static String safeSegment(String value) {
        String s = value == null ? "" : value.replaceAll("[^A-Za-z0-9_-]", "_");
        return s.isEmpty() ? "x" : s;
    }
}
