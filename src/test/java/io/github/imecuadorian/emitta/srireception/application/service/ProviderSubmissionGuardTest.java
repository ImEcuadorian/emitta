package io.github.imecuadorian.emitta.srireception.application.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class ProviderSubmissionGuardTest {
    private static byte[] xml(String fields) {
        return ("<factura><infoTributaria><ruc>1790012345001</ruc></infoTributaria><infoAdicional>"
                + fields + "</infoAdicional></factura>").getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void shouldRequireExactExistingProviderAndNeverRewriteSignedBytes() {
        String field = "<campoAdicional nombre=\"RUC Proveedor\">1799999999001</campoAdicional>";
        byte[] original = xml(field), before = Arrays.copyOf(original, original.length);
        assertDoesNotThrow(() -> ProviderSubmissionGuard.verifyXml(original, "1799999999001"));
        assertArrayEquals(before, original);
        assertThrows(IllegalStateException.class, () -> ProviderSubmissionGuard.verifyXml(original, "1790012345001"));
        assertThrows(IllegalStateException.class, () -> ProviderSubmissionGuard.verifyXml(original, null));
        assertThrows(IllegalStateException.class, () -> ProviderSubmissionGuard.verifyXml(xml(field + field), "1799999999001"));
        assertThrows(IllegalStateException.class, () -> ProviderSubmissionGuard.verifyXml(xml(""), "1799999999001"));
        assertDoesNotThrow(() -> ProviderSubmissionGuard.verifyXml(xml(""), null));
    }
}
