package io.github.imecuadorian.emitta.srireception.application.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class ProviderSubmissionGuardTest {
    @Test
    void manualOmissionIsBoundToDocumentIssuerAndTestAndEndsWithScope() {
        var id = java.util.UUID.randomUUID();
        byte[] test = "<factura><infoTributaria><ambiente>1</ambiente><ruc>1790012345001</ruc></infoTributaria></factura>".getBytes(StandardCharsets.UTF_8);
        assertFalse(ProviderSubmissionGuard.permitsManualOmission(id, test));
        io.github.imecuadorian.emitta.shared.fiscal.ManualTestXmlScope.run(id, "1790012345001", () -> {
            assertTrue(ProviderSubmissionGuard.permitsManualOmission(id, test));
            assertFalse(ProviderSubmissionGuard.permitsManualOmission(java.util.UUID.randomUUID(), test));
            assertFalse(ProviderSubmissionGuard.permitsManualOmission(id, new String(test, StandardCharsets.UTF_8).replace("<ambiente>1", "<ambiente>2").getBytes(StandardCharsets.UTF_8)));
            assertFalse(ProviderSubmissionGuard.permitsManualOmission(id, new String(test, StandardCharsets.UTF_8).replace("1790012345001", "1790012345002").getBytes(StandardCharsets.UTF_8)));
            return null;
        });
        assertFalse(ProviderSubmissionGuard.permitsManualOmission(id, test));
    }
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
