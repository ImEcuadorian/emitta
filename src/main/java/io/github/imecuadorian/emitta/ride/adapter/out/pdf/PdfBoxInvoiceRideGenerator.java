package io.github.imecuadorian.emitta.ride.adapter.out.pdf;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import io.github.imecuadorian.emitta.invoicexml.application.model.InvoiceXmlData;
import io.github.imecuadorian.emitta.ride.application.model.InvoiceRideRequest;
import io.github.imecuadorian.emitta.ride.application.port.out.InvoiceRidePdfGeneratorPort;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A4, multi-page invoice RIDE renderer.
 *
 * Inputs must come from the immutable fiscal invoice snapshot and a verified
 * SRI authorization. This adapter does not submit documents or persist PDFs.
 *
 * No fiscal values are recalculated: amounts come from the existing XML model.
 */
public final class PdfBoxInvoiceRideGenerator implements InvoiceRidePdfGeneratorPort {

    private static final float LEFT = 42f;
    private static final float RIGHT = PDRectangle.A4.getWidth() - LEFT;
    private static final float BODY_WIDTH = RIGHT - LEFT;
    private static final float BOTTOM = 52f;
    private static final PDFont NORMAL =
            new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont BOLD =
            new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final ZoneId ECUADOR = ZoneId.of("America/Guayaquil");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    @Override
    public byte[] generate(InvoiceRideRequest request) {
        Objects.requireNonNull(request, "Invoice RIDE request is required");
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream result = new ByteArrayOutputStream()) {

            InvoiceXmlData invoice = request.invoice();
            try (PageWriter writer = new PageWriter(document)) {
                writer.newPage();
                renderHeader(writer, request);
                renderBuyer(writer, invoice);
                renderItems(writer, invoice.items());
                renderTotals(writer, invoice);
                renderPayments(writer, invoice.payments());
                writer.paragraph(
                        "Representacion impresa del documento electronico (RIDE). " +
                        "Los valores y datos fiscales corresponden al comprobante electronico.", NORMAL, 8f, BODY_WIDTH);
            }
            addPageNumbers(document);
            document.save(result);
            return result.toByteArray();

        } catch (Exception exception) {
            throw new IllegalStateException("Could not render authorized invoice RIDE", exception);
        }
    }

    private static void renderHeader(PageWriter w, InvoiceRideRequest request) throws IOException {
        InvoiceXmlData v = request.invoice();
        w.text("FACTURA ELECTRONICA - RIDE", LEFT, w.y, BOLD, 15f);
        w.y -= 22f;
        if ("1".equals(v.environmentCode())) {
            w.text("AMBIENTE DE PRUEBAS - SIN VALIDEZ TRIBUTARIA", LEFT, w.y, BOLD, 9f);
            w.y -= 16f;
        }
        w.paragraph(v.legalName(), BOLD, 12f, BODY_WIDTH);
        if (v.tradeName() != null && !v.tradeName().isBlank()) {
            w.paragraph(v.tradeName(), NORMAL, 9f, BODY_WIDTH);
        }
        w.field("RUC emisor", v.ruc());
        w.field("RUC proveedor", v.providerRuc());
        w.field("Direccion matriz", v.mainAddress());
        w.field("Direccion establecimiento", v.establishmentAddress());
        w.field("Ambiente", "1".equals(v.environmentCode()) ? "PRUEBAS" : "PRODUCCION");
        w.field("Tipo de emision", "NORMAL");
        w.field("Nro. comprobante",
                v.establishmentCode() + "-" + v.pointOfIssueCode() + "-" + v.sequential());
        w.field("Numero de autorizacion", request.authorizationNumber());
        w.field("Fecha y hora autorizacion", DATE_TIME.format(request.authorizedAt().atZone(ECUADOR)));
        w.field("Clave de acceso", v.accessKey());
        w.space(4f);
        barcode(w, v.accessKey());
        w.space(12f);
        w.rule();
    }

    private static void renderBuyer(PageWriter w, InvoiceXmlData invoice) throws IOException {
        w.heading("INFORMACION DEL COMPRADOR");
        w.field("Razon social / nombres", invoice.buyer().name());
        w.field("Identificacion", invoice.buyer().identification());
        w.field("Fecha de emision", DATE.format(invoice.issueDate()));
        w.field("Direccion", invoice.buyer().address());
        w.space(8f);
    }

    private static void renderItems(PageWriter w, List<InvoiceXmlData.Item> items) throws IOException {
        w.ensure(45f);
        itemHeader(w);
        for (InvoiceXmlData.Item item : items) {
            List<String> descriptions = w.wrap(item.description(), NORMAL, 8f, 193f);
            float height = 15f + (descriptions.size() - 1) * 11f;
            if (w.y - height < BOTTOM + 50f) {
                w.newPage();
                itemHeader(w);
            }
            w.text(item.code(), 43f, w.y, NORMAL, 7.5f);
            w.text(number(item.quantity()), 326f, w.y, NORMAL, 8f);
            w.text(money(item.unitPrice()), 385f, w.y, NORMAL, 8f);
            w.text(money(item.discount()), 442f, w.y, NORMAL, 8f);
            w.text(money(item.subtotal()), 502f, w.y, NORMAL, 8f);
            float descY = w.y;
            for (String description : descriptions) {
                w.text(description, 117f, descY, NORMAL, 8f);
                descY -= 11f;
            }
            w.y -= height;
        }
        w.space(10f);
        w.rule();
    }

    private static void itemHeader(PageWriter w) throws IOException {
        w.heading("DETALLE DE PRODUCTOS / SERVICIOS");
        w.text("CODIGO", 43f, w.y, BOLD, 7.7f);
        w.text("DESCRIPCION", 117f, w.y, BOLD, 7.7f);
        w.text("CANT.", 326f, w.y, BOLD, 7.7f);
        w.text("P. UNIT.", 385f, w.y, BOLD, 7.7f);
        w.text("DESC.", 442f, w.y, BOLD, 7.7f);
        w.text("TOTAL", 502f, w.y, BOLD, 7.7f);
        w.y -= 15f;
        w.rule();
    }

    private static void renderTotals(PageWriter w, InvoiceXmlData invoice) throws IOException {
        w.heading("RESUMEN DE VALORES");
        w.field("Total sin impuestos", money(invoice.totalWithoutTaxes()));
        w.field("Descuento total", money(invoice.discountTotal()));
        for (InvoiceXmlData.TaxTotal tax : invoice.taxTotals()) {
            w.field("Impuesto (" + tax.taxCode() + "/" + tax.percentageCode() +
                    "), base " + money(tax.taxableBase()), money(tax.amount()));
        }
        w.field("Propina", money(invoice.tip()));
        w.ensure(22f);
        w.text("IMPORTE TOTAL: " + money(invoice.total()) + " " + invoice.currency(),
                LEFT, w.y, BOLD, 12f);
        w.y -= 25f;
    }

    private static void renderPayments(PageWriter w, List<InvoiceXmlData.Payment> payments)
            throws IOException {
        if (payments.isEmpty()) {
            return;
        }
        w.heading("FORMAS DE PAGO");
        for (InvoiceXmlData.Payment payment : payments) {
            w.field("Forma SRI " + payment.method(),
                    money(payment.total()) +
                    (payment.term() == null ? "" : " / Plazo: " + number(payment.term()) +
                            " " + (payment.unitTime() == null ? "" : payment.unitTime())));
        }
        w.space(8f);
    }

    private static void barcode(PageWriter w, String accessKey) throws Exception {
        // Encode all 49 digits, including a quiet zone. Keep the original key visible above.
        BitMatrix matrix = new MultiFormatWriter().encode(
                accessKey, BarcodeFormat.CODE_128, 960, 48);
        w.ensure(43f);
        float width = BODY_WIDTH / matrix.getWidth();
        w.stream.setNonStrokingColor(0f, 0f, 0f);
        for (int x = 0; x < matrix.getWidth(); x++) {
            if (matrix.get(x, 0)) {
                w.stream.addRect(LEFT + x * width, w.y - 30f, width, 30f);
            }
        }
        w.stream.fill();
        w.y -= 33f;
    }

    private static void addPageNumbers(PDDocument document) throws IOException {
        for (int i = 0; i < document.getNumberOfPages(); i++) {
            PDPage page = document.getPage(i);
            try (PDPageContentStream stream = new PDPageContentStream(
                    document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                stream.beginText();
                stream.setFont(NORMAL, 8f);
                stream.newLineAtOffset(LEFT, 27f);
                stream.showText("EMITTA | Pagina " + (i + 1) +
                        " de " + document.getNumberOfPages());
                stream.endText();
            }
        }
    }

    private static String money(BigDecimal value) {
        return Objects.requireNonNull(value, "Money must not be null")
                .setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    private static String number(BigDecimal value) {
        return Objects.requireNonNull(value, "Numeric value must not be null")
                .stripTrailingZeros().toPlainString();
    }

    private static final class PageWriter implements AutoCloseable {
        private final PDDocument document;
        private PDPageContentStream stream;
        private float y;

        private PageWriter(PDDocument document) {
            this.document = document;
        }

        private void newPage() throws IOException {
            if (stream != null) {
                stream.close();
            }
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = PDRectangle.A4.getHeight() - 43f;
        }

        private void ensure(float height) throws IOException {
            if (y - height < BOTTOM) {
                newPage();
            }
        }

        private void space(float height) throws IOException {
            ensure(height);
            y -= height;
        }

        private void heading(String heading) throws IOException {
            ensure(30f);
            text(heading, LEFT, y, BOLD, 10f);
            y -= 20f;
        }

        private void rule() throws IOException {
            ensure(12f);
            stream.setStrokingColor(0.69f, 0.69f, 0.69f);
            stream.moveTo(LEFT, y);
            stream.lineTo(RIGHT, y);
            stream.stroke();
            y -= 12f;
        }

        private void field(String name, String value) throws IOException {
            if (value == null || value.isBlank()) {
                return;
            }
            paragraph(name + ": " + value, NORMAL, 9f, BODY_WIDTH);
        }

        private void paragraph(String value, PDFont font, float size, float width)
                throws IOException {
            for (String line : wrap(value, font, size, width)) {
                ensure(size + 5f);
                text(line, LEFT, y, font, size);
                y -= size + 5f;
            }
        }

        private List<String> wrap(String value, PDFont font, float size, float width)
                throws IOException {
            String plain = Objects.requireNonNullElse(value, "")
                    .replace('\n', ' ').replace('\r', ' ').trim();
            List<String> lines = new ArrayList<>();
            if (plain.isEmpty()) {
                return List.of("");
            }
            StringBuilder line = new StringBuilder();
            for (int offset = 0; offset < plain.length();) {
                int cp = plain.codePointAt(offset);
                String glyph = new String(Character.toChars(cp));
                String candidate = line + glyph;
                if (!line.isEmpty() &&
                        font.getStringWidth(candidate) * size / 1000f > width) {
                    lines.add(line.toString().stripTrailing());
                    line.setLength(0);
                }
                line.append(glyph);
                offset += Character.charCount(cp);
            }
            if (!line.isEmpty()) {
                lines.add(line.toString().stripTrailing());
            }
            return lines;
        }

        private void text(String value, float x, float atY, PDFont font, float size)
                throws IOException {
            if (value == null || value.isEmpty()) {
                return;
            }
            stream.setNonStrokingColor(0f, 0f, 0f);
            stream.beginText();
            stream.setFont(font, size);
            stream.newLineAtOffset(x, atY);
            stream.showText(value);
            stream.endText();
        }

        @Override
        public void close() throws IOException {
            if (stream != null) {
                stream.close();
                stream = null;
            }
        }
    }
}
