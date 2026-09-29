package com.ffs.efdl.tools;

import java.io.Console;
import java.io.IOException;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Génère le hash BCrypt du mot de passe maître
 * pour la propriété {@code efdl.security.admin-master-password-hash}.
 * <p>
 * Le mot de passe est saisi deux fois dans le terminal, sans écho ; il n'est jamais passé en argument,
 * écrit sur disque ni affiché. Seul le hash est affiché, et écrit dans application-local.yml si
 * {@code --write=<chemin>} est fourni.
 * <p>
 * Lancement : {@code backend/tools/hash-master-password.cmd} (Windows) ou {@code .sh} (Git Bash).
 */
public final class MasterPasswordHashTool {

    static final String PROPERTY_KEY = "admin-master-password-hash";
    private static final Pattern PROPERTY_LINE =
            Pattern.compile("^(\\s*" + PROPERTY_KEY + ":).*$", Pattern.MULTILINE);

    private MasterPasswordHashTool() {
    }

    public static void main(String[] args) throws IOException {
        Console console = System.console();
        if (console == null) {
            System.err.println("Terminal interactif requis (cmd, PowerShell, ou Git Bash via winpty) : "
                    + "le mot de passe doit être saisi sans écho.");
            System.exit(2);
        }
        char[] first = console.readPassword("Mot de passe maître (admin.pass) : ");
        char[] second = console.readPassword("Confirmer le mot de passe : ");
        try {
            if (first == null || first.length == 0) {
                System.err.println("Mot de passe vide : abandon.");
                System.exit(1);
            }
            if (!Arrays.equals(first, second)) {
                System.err.println("Les deux saisies sont différentes : abandon.");
                System.exit(1);
            }
            String hash = hash(first);
            System.out.println();
            System.out.println("Hash BCrypt généré :");
            System.out.println("  " + hash);
            System.out.println();

            Path target = writeTarget(args);
            if (target != null && Files.exists(target)) {
                String answer = console.readLine("Écrire ce hash dans %s ? (o/N) : ", target);
                if (answer != null && answer.trim().equalsIgnoreCase("o")) {
                    boolean updated = writeHash(target, hash);
                    System.out.println(updated
                            ? "application-local.yml mis à jour. Redémarrez le backend pour prendre en compte le hash."
                            : "Ligne '" + PROPERTY_KEY + ":' introuvable : ajoutez-la manuellement (voir ci-dessous).");
                    if (updated) {
                        return;
                    }
                }
            }
            System.out.println("À placer dans application-local.yml (non versionné) :");
            System.out.println("  efdl:");
            System.out.println("    security:");
            System.out.println("      " + PROPERTY_KEY + ": \"" + hash + "\"");
            System.out.println("ou dans la variable d'environnement EFDL_ADMIN_MASTER_PASSWORD_HASH.");
        } finally {
            if (first != null) Arrays.fill(first, '\0');
            if (second != null) Arrays.fill(second, '\0');
        }
    }

    /** Hash BCrypt (coût par défaut de Spring Security) — même encodeur que le backend. */
    static String hash(char[] password) {
        return new BCryptPasswordEncoder().encode(CharBuffer.wrap(password));
    }

    /** Remplace la valeur de {@code admin-master-password-hash:} dans le fichier YAML ; faux si la ligne est absente. */
    static boolean writeHash(Path yaml, String hash) throws IOException {
        String content = Files.readString(yaml, StandardCharsets.UTF_8);
        Matcher m = PROPERTY_LINE.matcher(content);
        if (!m.find()) {
            return false;
        }
        String updated = m.replaceFirst(Matcher.quoteReplacement(m.group(1) + " \"" + hash + "\""));
        Files.writeString(yaml, updated, StandardCharsets.UTF_8);
        return true;
    }

    private static Path writeTarget(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--write=")) {
                return Path.of(arg.substring("--write=".length()));
            }
        }
        return null;
    }
}
