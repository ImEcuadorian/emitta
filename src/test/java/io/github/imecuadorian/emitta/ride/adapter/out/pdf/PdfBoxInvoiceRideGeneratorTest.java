package io.github.imecuadorian.emitta.ride.adapter.out.pdf;

import io.github.imecuadorian.emitta.invoicexml.application.model.InvoiceXmlData;
import io.github.imecuadorian.emitta.ride.application.model.InvoiceRideRequest;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class PdfBoxInvoiceRideGeneratorTest {

    private static final String ACCESS_KEY = "1".repeat(49);
    private final PdfBoxInvoiceRideGenerator generator = new PdfBoxInvoiceRideGenerator();

    @Test
    void rendersAuthorizedInvoiceWithSpanishCharacters() throws Exception {
        byte[] pdf = generator.generate(
                new InvoiceRideRequest(sampleInvoice(2, "2"), ACCESS_KEY,
                        Instant.parse("2026-10-09T13:00:00Z")));

        assertEquals("%PDF", new String(pdf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertEquals(1, document.getNumberOfPages());
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("FACTURA ELECTRONICA - RIDE"));
            assertTrue(text.contains("José Pérez"));
            assertTrue(text.contains("1234567890001"));
            assertTrue(text.contains(ACCESS_KEY));
            assertTrue(text.contains("RUC proveedor"));
            assertTrue(text.contains("IMPORTE TOTAL"));
        }
    }

    @Test
    void paginatesManyItemsWithoutDroppingTotals() throws Exception {
        byte[] pdf = generator.generate(new InvoiceRideRequest(
                sampleInvoice(90, "2"), ACCESS_KEY,
                Instant.parse("2026-10-09T13:00:00Z")));

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertTrue(document.getNumberOfPages() > 1);
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("SKU-089"));
            assertTrue(text.contains("IMPORTE TOTAL"));
            assertTrue(text.contains("Pagina 1 de "));
        }
    }

    @Test
    void testEnvironmentIsVisiblyMarkedInvalidForTaxPurposes() throws Exception {
        byte[] pdf = generator.generate(new InvoiceRideRequest(
                sampleInvoice(1, "1"), ACCESS_KEY,
                Instant.parse("2026-10-09T13:00:00Z")));
        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("SIN VALIDEZ TRIBUTARIA"));
        }
    }

    @Test
    void rejectsAuthorizationNumberNotMatchingFiscalAccessKey() {
        assertThrows(IllegalArgumentException.class, () -> new InvoiceRideRequest(
                sampleInvoice(1, "2"),
                "9".repeat(49),
                Instant.parse("2026-10-09T13:00:00Z")));
    }

    private static InvoiceXmlData sampleInvoice(int count, String environment) {
        List<InvoiceXmlData.Item> items = IntStream.range(0, count)
                .mapToObj(i -> new InvoiceXmlData.Item(
                        "SKU-" + String.format("%03d", i),
                        "Servicio profesional - descripcion de prueba " + i,
                        new BigDecimal("1"),
                        new BigDecimal("5.00"),
                        BigDecimal.ZERO.setScale(2),
                        new BigDecimal("5.00"),
                        List.of()))
                .toList();
        BigDecimal total = new BigDecimal("5.00").multiply(BigDecimal.valueOf(count));

        return new InvoiceXmlData(
                environment,
                "EMISOR DE PRUEBA S.A.", "Emisor de prueba", "1234567890001",
                ACCESS_KEY, "001", "001", "000000001",
                "Quito, Ecuador", LocalDate.of(2026, 10, 9), "Quito Norte",
                new InvoiceXmlData.Buyer("05", "1712345678", "José Pérez", "Av. Amazonas"),
                total, BigDecimal.ZERO.setScale(2),
                List.of(),
                BigDecimal.ZERO.setScale(2), total, "DOLAR",
                items,
                List.of(new InvoiceXmlData.Payment("01", total, null, null)),
                "1790012345001");
    }
}
