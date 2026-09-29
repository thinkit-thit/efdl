package com.ffs.efdl.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AdminWriteServiceTest {

    @Test
    void normalizeIdentifiant() {
        assertThat(AdminWriteService.normalize("Écolé Test  IT")).isEqualTo("ecole-test-it");
        assertThat(AdminWriteService.normalize("SDI FDL")).isEqualTo("sdi-fdl");
        assertThat(AdminWriteService.normalize("a.b/c d")).isEqualTo("ab-c-d");
        assertThat(AdminWriteService.normalize("x -- y")).isEqualTo("x-y");
    }
}
