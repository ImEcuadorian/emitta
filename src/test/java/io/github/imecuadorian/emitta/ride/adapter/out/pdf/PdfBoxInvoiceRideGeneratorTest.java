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

        java.nio.file.Files.createDirectories(java.nio.file.Path.of("build/ride-samples"));
        java.nio.file.Files.write(java.nio.file.Path.of("build/ride-samples/invoice-unicode.pdf"), pdf);
        assertEquals("%PDF", new String(pdf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
        try (PDDocument document = Loader.loadPDF(pdf)) {
            renderSample(document,"unicode");
            assertEquals(1, document.getNumberOfPages());
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("FACTURA ELECTRONICA - RIDE"));
            assertTrue(text.contains("José Pérez"));
            assertTrue(text.contains("1234567890001"));
            assertTrue(text.contains(ACCESS_KEY));
            assertTrue(text.contains("RUC Proveedor"));
            assertTrue(text.contains("IMPORTE TOTAL"));
        }
    }

    @Test
    void paginatesManyItemsWithoutDroppingTotals() throws Exception {
        byte[] pdf = generator.generate(new InvoiceRideRequest(
                sampleInvoice(120, "2"), ACCESS_KEY,
                Instant.parse("2026-10-09T13:00:00Z")));

        try (PDDocument document = Loader.loadPDF(pdf)) {
            java.nio.file.Files.createDirectories(java.nio.file.Path.of("build/ride-samples"));
            java.nio.file.Files.write(java.nio.file.Path.of("build/ride-samples/invoice-120-items.pdf"), pdf);
            renderSample(document,"120-items");
            assertTrue(document.getNumberOfPages() > 1);
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("SKU-119"));
            assertTrue(text.contains("IMPORTE TOTAL"));
            assertTrue(text.contains("Pagina 1 de "));
        }
    }

    @Test
    void regenerationProducesIdenticalBytesAfterRestart() {
        var request=new InvoiceRideRequest(sampleInvoice(2,"1"),ACCESS_KEY,Instant.parse("2026-10-09T13:00:00Z"));
        assertArrayEquals(new PdfBoxInvoiceRideGenerator().generate(request),new PdfBoxInvoiceRideGenerator().generate(request));
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

    @Test
    void barcodeDecodesToExactAccessKeyAndFontIsEmbedded() throws Exception {
        try (PDDocument pdf = Loader.loadPDF(generator.generate(new InvoiceRideRequest(
                sampleInvoice(1, "1"), ACCESS_KEY, Instant.parse("2026-10-09T13:00:00Z"))))) {
            var page = new org.apache.pdfbox.rendering.PDFRenderer(pdf).renderImageWithDPI(0, 200);
            int[] pixels = page.getRGB(0, 0, page.getWidth(), page.getHeight(), null, 0, page.getWidth());
            var bitmap = new com.google.zxing.BinaryBitmap(new com.google.zxing.common.HybridBinarizer(
                    new com.google.zxing.RGBLuminanceSource(page.getWidth(), page.getHeight(), pixels)));
            var result = new com.google.zxing.MultiFormatReader().decode(bitmap,
                    java.util.Map.of(com.google.zxing.DecodeHintType.POSSIBLE_FORMATS,
                            List.of(com.google.zxing.BarcodeFormat.CODE_128)));
            assertEquals(ACCESS_KEY, result.getText());
            for (var name : pdf.getPage(0).getResources().getFontNames())
                assertTrue(pdf.getPage(0).getResources().getFont(name).isEmbedded());
        }
    }

    @Test
    void splitsSingleVeryLongRowAcrossPagesWithoutLosingText() throws Exception {
        InvoiceXmlData base = sampleInvoice(1, "1");
        String description = "Descripción española: niño, pingüino, Ω. ".repeat(300) + "FIN_DE_FILA";
        var row = new InvoiceXmlData.Item("CODIGO-LARGO-".repeat(20), description,
                new BigDecimal("123456789.123456"), new BigDecimal("123456789.123456"),
                new BigDecimal("123456789.12"), new BigDecimal("123456789012.12"), List.of());
        var invoice = new InvoiceXmlData(base.environmentCode(), base.legalName(), base.tradeName(), base.ruc(),
                base.accessKey(), base.establishmentCode(), base.pointOfIssueCode(), base.sequential(),
                base.mainAddress(), base.issueDate(), base.establishmentAddress(), base.buyer(),
                base.totalWithoutTaxes(), base.discountTotal(), base.taxTotals(), base.tip(), base.total(),
                base.currency(), List.of(row), base.payments(), base.providerRuc());
        try (PDDocument pdf = Loader.loadPDF(generator.generate(new InvoiceRideRequest(invoice,
                ACCESS_KEY, Instant.parse("2026-10-09T13:00:00Z"))))) {
            assertTrue(pdf.getNumberOfPages() > 2);
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("FIN_DE_FILA"));
            assertTrue(text.contains("Ω"));
            assertTrue(text.contains("IMPORTE TOTAL"));
        }
    }

    @Test
    void authorizedXmlIsTheExactFiscalSource() {
        var invoice=sampleInvoice(2,"1");
        var xml=new io.github.imecuadorian.emitta.invoicexml.adapter.out.xml.SriInvoiceXmlGenerator().generate(invoice);
        var request=new io.github.imecuadorian.emitta.ride.adapter.out.xml.AuthorizedInvoiceRideReader()
                .read(xml.bytes(),ACCESS_KEY,Instant.parse("2026-10-09T13:00:00Z"));
        assertEquals(invoice,request.invoice());
        assertThrows(IllegalArgumentException.class, () ->
                new io.github.imecuadorian.emitta.ride.adapter.out.xml.AuthorizedInvoiceRideReader()
                        .read("<!DOCTYPE factura [<!ENTITY x SYSTEM 'file:///not-allowed'>]><factura/>".getBytes(),
                                ACCESS_KEY,Instant.now()));
    }

    private static void renderSample(PDDocument document,String name) throws Exception {
        var renderer=new org.apache.pdfbox.rendering.PDFRenderer(document);
        for(int i=0;i<document.getNumberOfPages();i++) {
            javax.imageio.ImageIO.write(renderer.renderImageWithDPI(i,110),"png",
                    java.nio.file.Path.of("build/ride-samples/"+name+"-page-"+(i+1)+".png").toFile());
        }
    }

    private static InvoiceXmlData sampleInvoice(int count, String environment) {
        List<InvoiceXmlData.Item> items = IntStream.range(0, count)
                .mapToObj(i -> new InvoiceXmlData.Item(
                        "SKU-" + String.format("%03d", i),
                        "Servicio profesional - descripcion de prueba " + i,
                        new BigDecimal("1"),
                        new BigDecimal("5"),
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
