package io.github.imecuadorian.emitta.invoicexml.adapter.out.persistence;

import io.github.imecuadorian.emitta.invoicexml.application.model.InvoiceXmlSourceData;
import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import io.github.imecuadorian.emitta.invoicexml.application.service.GenerateInvoiceXmlService;
import io.github.imecuadorian.emitta.invoicexml.domain.GeneratedInvoiceXml;
import io.github.imecuadorian.emitta.invoicexml.adapter.out.xml.SriInvoiceXmlGenerator;

import org.w3c.dom.Document;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import java.io.StringReader;
import java.net.URL;
import java.time.ZoneId;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class PostgreSqlInvoiceXmlSourceAdapterIntegrationTest {

    private static final UUID TENANT_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    private static final UUID TAXPAYER_ID =
            UUID.fromString(
                    "22222222-2222-2222-2222-222222222222"
            );

    private static final UUID ESTABLISHMENT_ID =
            UUID.fromString(
                    "33333333-3333-3333-3333-333333333333"
            );

    private static final UUID POINT_OF_ISSUE_ID =
            UUID.fromString(
                    "44444444-4444-4444-4444-444444444444"
            );

    private static final UUID DOCUMENT_ID =
            UUID.fromString(
                    "55555555-5555-5555-5555-555555555555"
            );

    private static final UUID ITEM_ID =
            UUID.fromString(
                    "66666666-6666-6666-6666-666666666666"
            );

    private static final String ACCESS_KEY =
            "0610202601179001234500110010010000000028055561612";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(
                    "postgres:18"
            );

    private static JdbcClient jdbcClient;

    private static PostgreSqlInvoiceXmlSourceAdapter adapter;

    @BeforeAll
    static void setUpDatabase() {

        Flyway.configure()
                .dataSource(
                        POSTGRES.getJdbcUrl(),
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword()
                )
                .schemas(
                        "emitta"
                )
                .defaultSchema(
                        "emitta"
                )
                .createSchemas(
                        true
                )
                .locations(
                        "classpath:db/migration"
                )
                .load()
                .migrate();

        String jdbcUrl =
                POSTGRES.getJdbcUrl();

        String separator =
                jdbcUrl.contains("?")
                        ? "&"
                        : "?";

        String emittaJdbcUrl =
                jdbcUrl
                        + separator
                        + "currentSchema=emitta";

        DataSource dataSource =
                new DriverManagerDataSource(
                        emittaJdbcUrl,
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword()
                );

        jdbcClient =
                JdbcClient.create(
                        dataSource
                );

        adapter =
                new PostgreSqlInvoiceXmlSourceAdapter(
                        jdbcClient
                );

        seedInvoice();
    }

    @Test
    void shouldLoadCompleteInvoiceXmlSourceFromPostgreSql() {

        Optional<InvoiceXmlSourceData> result =
                adapter.findByDocumentId(
                        DOCUMENT_ID
                );

        assertTrue(
                result.isPresent()
        );

        InvoiceXmlSourceData source =
                result.orElseThrow();

        assertEquals(
                FiscalEnvironment.TEST,
                source.environment()
        );

        assertEquals(
                "FAXORF LOCAL DEVELOPMENT S.A.S.",
                source.legalName()
        );

        assertEquals(
                "FAXORF",
                source.tradeName()
        );

        assertEquals(
                "1790012345001",
                source.ruc()
        );

        assertEquals(
                "Quito, Ecuador",
                source.mainAddress()
        );

        assertEquals(
                ACCESS_KEY,
                source.accessKey()
        );

        assertEquals(
                2L,
                source.sequential()
        );

        assertEquals(
                Instant.parse(
                        "2026-10-06T23:00:00Z"
                ),
                source.issuedAt()
        );

        assertEquals(
                "001",
                source.establishmentCode()
        );

        assertEquals(
                "001",
                source.pointOfIssueCode()
        );

        assertEquals(
                "07",
                source.buyer()
                        .identificationType()
        );

        assertEquals(
                "9999999999999",
                source.buyer()
                        .identification()
        );

        assertEquals(
                "CONSUMIDOR FINAL",
                source.buyer()
                        .name()
        );

        assertMoney(
                "18.00",
                source.subtotal()
        );

        assertMoney(
                "2.00",
                source.discountTotal()
        );

        assertMoney(
                "20.70",
                source.total()
        );

        assertEquals(
                "DOLAR",
                source.currency()
        );

        assertEquals(
                1,
                source.items()
                        .size()
        );

        InvoiceXmlSourceData.Item item =
                source.items()
                        .getFirst();

        assertEquals(
                "P001",
                item.sku()
        );

        assertEquals(
                "Producto de prueba",
                item.description()
        );

        assertDecimal(
                "2.000000",
                item.quantity()
        );

        assertDecimal(
                "10.000000",
                item.unitPrice()
        );

        assertMoney(
                "2.00",
                item.discount()
        );

        assertMoney(
                "18.00",
                item.subtotal()
        );

        assertEquals(
                1,
                item.taxes()
                        .size()
        );

        InvoiceXmlSourceData.ItemTax tax =
                item.taxes()
                        .getFirst();

        assertEquals(
                "2",
                tax.taxCode()
        );

        assertEquals(
                "4",
                tax.percentageCode()
        );

        assertDecimal(
                "15.0000",
                tax.rate()
        );

        assertMoney(
                "18.00",
                tax.taxableBase()
        );

        assertMoney(
                "2.70",
                tax.amount()
        );

        assertEquals(
                1,
                source.payments()
                        .size()
        );

        InvoiceXmlSourceData.Payment payment =
                source.payments()
                        .getFirst();

        assertEquals(
                "01",
                payment.method()
        );

        assertMoney(
                "20.70",
                payment.total()
        );

        assertEquals(
                null,
                payment.term()
        );

        assertEquals(
                null,
                payment.unitTime()
        );
    }

    @Test
    void shouldReturnEmptyWhenDocumentDoesNotExist() {

        Optional<InvoiceXmlSourceData> result =
                adapter.findByDocumentId(
                        UUID.randomUUID()
                );

        assertTrue(
                result.isEmpty()
        );
    }

    @Test
    void shouldGenerateValidSriInvoiceXmlFromPostgreSql()
            throws Exception {

        GenerateInvoiceXmlService service =
                new GenerateInvoiceXmlService(
                        adapter,
                        new SriInvoiceXmlGenerator(),
                        "1799999999001",
                        ZoneId.of(
                                "America/Guayaquil"
                        )
                );

        GeneratedInvoiceXml generated =
                service.generate(
                        DOCUMENT_ID
                );

        Document xmlDocument =
                parseXml(
                        generated.content()
                );

        assertEquals(
                "factura",
                xmlDocument
                        .getDocumentElement()
                        .getTagName()
        );

        assertEquals(
                "comprobante",
                xmlDocument
                        .getDocumentElement()
                        .getAttribute(
                                "id"
                        )
        );

        assertEquals(
                "2.1.0",
                xmlDocument
                        .getDocumentElement()
                        .getAttribute(
                                "version"
                        )
        );

        assertEquals(
                ACCESS_KEY,
                elementText(
                        xmlDocument,
                        "claveAcceso"
                )
        );

        assertEquals(
                "06/10/2026",
                elementText(
                        xmlDocument,
                        "fechaEmision"
                )
        );

        assertEquals(
                "000000002",
                elementText(
                        xmlDocument,
                        "secuencial"
                )
        );

        assertEquals(
                "CONSUMIDOR FINAL",
                elementText(
                        xmlDocument,
                        "razonSocialComprador"
                )
        );

        assertEquals(
                "18.00",
                elementText(
                        xmlDocument,
                        "totalSinImpuestos"
                )
        );

        assertEquals(
                "2.00",
                elementText(
                        xmlDocument,
                        "totalDescuento"
                )
        );

        assertEquals(
                "20.70",
                elementText(
                        xmlDocument,
                        "importeTotal"
                )
        );

        assertEquals(
                "P001",
                elementText(
                        xmlDocument,
                        "codigoPrincipal"
                )
        );

        assertEquals(
                "01",
                elementText(
                        xmlDocument,
                        "formaPago"
                )
        );

        validateAgainstSriXsd(
                generated.content()
        );
    }

    private static void seedInvoice() {

        jdbcClient.sql(
                        """
                        INSERT INTO tenants (
                            id,
                            name,
                            status
                        )
                        VALUES (
                            :id,
                            'Faxorf Local Development',
                            'ACTIVE'
                        )
                        """
                )
                .param(
                        "id",
                        TENANT_ID
                )
                .update();

        jdbcClient.sql(
                        """
                        INSERT INTO taxpayers (
                            id,
                            tenant_id,
                            ruc,
                            legal_name,
                            trade_name,
                            main_address,
                            status,
                            test_enabled,
                            production_enabled
                        )
                        VALUES (
                            :id,
                            :tenantId,
                            '1790012345001',
                            'FAXORF LOCAL DEVELOPMENT S.A.S.',
                            'FAXORF',
                            'Quito, Ecuador',
                            'ACTIVE',
                            TRUE,
                            FALSE
                        )
                        """
                )
                .param(
                        "id",
                        TAXPAYER_ID
                )
                .param(
                        "tenantId",
                        TENANT_ID
                )
                .update();

        jdbcClient.sql(
                        """
                        INSERT INTO establishments (
                            id,
                            taxpayer_id,
                            code,
                            name,
                            address,
                            status
                        )
                        VALUES (
                            :id,
                            :taxpayerId,
                            '001',
                            'Matriz',
                            'Quito, Ecuador',
                            'ACTIVE'
                        )
                        """
                )
                .param(
                        "id",
                        ESTABLISHMENT_ID
                )
                .param(
                        "taxpayerId",
                        TAXPAYER_ID
                )
                .update();

        jdbcClient.sql(
                        """
                        INSERT INTO points_of_issue (
                            id,
                            establishment_id,
                            code,
                            name,
                            status
                        )
                        VALUES (
                            :id,
                            :establishmentId,
                            '001',
                            'Caja Principal',
                            'ACTIVE'
                        )
                        """
                )
                .param(
                        "id",
                        POINT_OF_ISSUE_ID
                )
                .param(
                        "establishmentId",
                        ESTABLISHMENT_ID
                )
                .update();

        jdbcClient.sql(
                        """
                        INSERT INTO documents (
                            id,
                            tenant_id,
                            taxpayer_id,
                            point_of_issue_id,
                            document_type,
                            environment,
                            sequential,
                            access_key,
                            status,
                            idempotency_key,
                            issued_at
                        )
                        VALUES (
                            :id,
                            :tenantId,
                            :taxpayerId,
                            :pointOfIssueId,
                            'INVOICE',
                            'TEST',
                            2,
                            :accessKey,
                            'GENERATING',
                            'xml-source-integration-test',
                            TIMESTAMPTZ '2026-10-06 23:00:00+00'
                        )
                        """
                )
                .param(
                        "id",
                        DOCUMENT_ID
                )
                .param(
                        "tenantId",
                        TENANT_ID
                )
                .param(
                        "taxpayerId",
                        TAXPAYER_ID
                )
                .param(
                        "pointOfIssueId",
                        POINT_OF_ISSUE_ID
                )
                .param(
                        "accessKey",
                        ACCESS_KEY
                )
                .update();

        jdbcClient.sql(
                        """
                        INSERT INTO invoices (
                            document_id,
                            buyer_identification_type,
                            buyer_identification,
                            buyer_name,
                            buyer_address,
                            subtotal,
                            discount_total,
                            tax_total,
                            total,
                            currency
                        )
                        VALUES (
                            :documentId,
                            '07',
                            '9999999999999',
                            'CONSUMIDOR FINAL',
                            NULL,
                            18.00,
                            2.00,
                            2.70,
                            20.70,
                            'DOLAR'
                        )
                        """
                )
                .param(
                        "documentId",
                        DOCUMENT_ID
                )
                .update();

        jdbcClient.sql(
                        """
                        INSERT INTO invoice_items (
                            id,
                            invoice_id,
                            line_number,
                            sku,
                            description,
                            quantity,
                            unit_price,
                            discount,
                            subtotal,
                            tax_total,
                            total
                        )
                        VALUES (
                            :id,
                            :documentId,
                            1,
                            'P001',
                            'Producto de prueba',
                            2.000000,
                            10.000000,
                            2.00,
                            18.00,
                            2.70,
                            20.70
                        )
                        """
                )
                .param(
                        "id",
                        ITEM_ID
                )
                .param(
                        "documentId",
                        DOCUMENT_ID
                )
                .update();

        jdbcClient.sql(
                        """
                        INSERT INTO invoice_item_taxes (
                            invoice_item_id,
                            tax_code,
                            percentage_code,
                            rate,
                            taxable_base,
                            tax_amount
                        )
                        VALUES (
                            :itemId,
                            '2',
                            '4',
                            15.0000,
                            18.00,
                            2.70
                        )
                        """
                )
                .param(
                        "itemId",
                        ITEM_ID
                )
                .update();

        jdbcClient.sql(
                        """
                        INSERT INTO invoice_payments (
                            invoice_id,
                            line_number,
                            payment_method,
                            total,
                            term,
                            unit_time
                        )
                        VALUES (
                            :documentId,
                            1,
                            '01',
                            20.70,
                            NULL,
                            NULL
                        )
                        """
                )
                .param(
                        "documentId",
                        DOCUMENT_ID
                )
                .update();
    }


    private static void assertMoney(
            String expected,
            BigDecimal actual
    ) {

        assertEquals(
                0,
                new BigDecimal(
                        expected
                ).compareTo(
                        actual
                )
        );
    }

    private static void assertDecimal(
            String expected,
            BigDecimal actual
    ) {

        assertEquals(
                0,
                new BigDecimal(
                        expected
                ).compareTo(
                        actual
                )
        );
    }

    private static Document parseXml(
            String xml
    ) throws Exception {

        DocumentBuilderFactory factory =
                DocumentBuilderFactory
                        .newInstance();

        factory.setFeature(
                XMLConstants.FEATURE_SECURE_PROCESSING,
                true
        );

        factory.setFeature(
                "http://apache.org/xml/features/disallow-doctype-decl",
                true
        );

        factory.setNamespaceAware(
                true
        );

        return factory
                .newDocumentBuilder()
                .parse(
                        new org.xml.sax.InputSource(
                                new StringReader(
                                        xml
                                )
                        )
                );
    }

    private static String elementText(
            Document document,
            String elementName
    ) {

        return document
                .getElementsByTagName(
                        elementName
                )
                .item(
                        0
                )
                .getTextContent();
    }

    private static void validateAgainstSriXsd(
            String xml
    ) throws Exception {

        ClassLoader classLoader =
                Thread.currentThread()
                        .getContextClassLoader();

        URL schemaUrl =
                Objects.requireNonNull(
                        classLoader.getResource(
                                "sri/xsd/factura_V2.1.0.xsd"
                        ),
                        "SRI invoice XSD not found in classpath"
                );

        SchemaFactory schemaFactory =
                SchemaFactory.newInstance(
                        XMLConstants.W3C_XML_SCHEMA_NS_URI
                );

        schemaFactory.setProperty(
                XMLConstants.ACCESS_EXTERNAL_DTD,
                ""
        );

        schemaFactory.setProperty(
                XMLConstants.ACCESS_EXTERNAL_SCHEMA,
                "file"
        );

        Schema schema =
                schemaFactory.newSchema(
                        schemaUrl
                );

        Validator validator =
                schema.newValidator();

        assertDoesNotThrow(
                () ->
                        validator.validate(
                                new StreamSource(
                                        new StringReader(
                                                xml
                                        )
                                )
                        )
        );
    }
}