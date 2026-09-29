package com.ffs.efdl.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class MasterPasswordHashToolTest {

    @Test
    void leHashEstAccepteParLEncodeurDuBackend() {
        String hash = MasterPasswordHashTool.hash("Maitre!2026".toCharArray());
        assertThat(hash).startsWith("$2a$");
        assertThat(new BCryptPasswordEncoder().matches("Maitre!2026", hash)).isTrue();
        assertThat(new BCryptPasswordEncoder().matches("autre", hash)).isFalse();
    }

    @Test
    void ecritLeHashDansLaLigneExistanteSansToucherLeReste(@TempDir Path dir) throws Exception {
        Path yaml = dir.resolve("application-local.yml");
        Files.writeString(yaml, """
                spring:
                  mongodb:
                    database: testfdl2025
                efdl:
                  security:
                    admin-master-password-hash: ""
                  tenant:
                    default-school-id: "sdi-fdl"
                """, StandardCharsets.UTF_8);
        String hash = "$2a$10$abcdefghijklmnopqrstuv0123456789ABCDEFGHIJKLMNOPQRSTU";

        assertThat(MasterPasswordHashTool.writeHash(yaml, hash)).isTrue();

        String content = Files.readString(yaml, StandardCharsets.UTF_8);
        assertThat(content).contains("    admin-master-password-hash: \"" + hash + "\"");
        assertThat(content).contains("database: testfdl2025").contains("default-school-id: \"sdi-fdl\"");
    }

    @Test
    void signaleUneLigneAbsente(@TempDir Path dir) throws Exception {
        Path yaml = dir.resolve("application-local.yml");
        Files.writeString(yaml, "efdl:\n  tenant:\n    default-school-id: x\n", StandardCharsets.UTF_8);
        assertThat(MasterPasswordHashTool.writeHash(yaml, "$2a$10$x")).isFalse();
    }
}
