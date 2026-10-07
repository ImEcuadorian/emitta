package io.github.imecuadorian.emitta.invoicexml.adapter.out.xml;

import io.github.imecuadorian.emitta.invoicexml.application.model.InvoiceXmlData;
import io.github.imecuadorian.emitta.invoicexml.domain.GeneratedInvoiceXml;
import io.github.imecuadorian.emitta.invoicexml.domain.SriInvoiceSchema;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import java.io.StringReader;
import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SriInvoiceXmlGeneratorTest {

    private static final String ACCESS_KEY =
            "0610202601179001234500110010010000000028055561612";

    /*
     * Test fixture only.
     * This is not the real Emitta provider RUC.
     */
    private static final String TEST_PROVIDER_RUC =
            "1799999999001";

    private final SriInvoiceXmlGenerator generator =
            new SriInvoiceXmlGenerator();

    @Test
    void shouldGenerateExpectedSriInvoiceXml()
            throws Exception {

        GeneratedInvoiceXml generated =
                generator.generate(
                        invoiceData()
                );

        Document document =
                parse(
                        generated.content()
                );

        Element root =
                document.getDocumentElement();

        assertEquals(
                "factura",
                root.getTagName()
        );

        assertEquals(
                SriInvoiceSchema.ROOT_ID,
                root.getAttribute(
                        "id"
                )
        );

        assertEquals(
                SriInvoiceSchema.VERSION,
                root.getAttribute(
                        "version"
                )
        );

        assertEquals(
                "1",
                text(
                        document,
                        "ambiente"
                )
        );

        assertEquals(
                "1",
                text(
                        document,
                        "tipoEmision"
                )
        );

        assertEquals(
                "1790012345001",
                text(
                        document,
                        "ruc"
                )
        );

        assertEquals(
                ACCESS_KEY,
                text(
                        document,
                        "claveAcceso"
                )
        );

        assertEquals(
                "01",
                text(
                        document,
                        "codDoc"
                )
        );

        assertEquals(
                "001",
                text(
                        document,
                        "estab"
                )
        );

        assertEquals(
                "001",
                text(
                        document,
                        "ptoEmi"
                )
        );

        assertEquals(
                "000000002",
                text(
                        document,
                        "secuencial"
                )
        );

        assertEquals(
                "06/10/2026",
                text(
                        document,
                        "fechaEmision"
                )
        );

        assertEquals(
                "07",
                text(
                        document,
                        "tipoIdentificacionComprador"
                )
        );

        assertEquals(
                "9999999999999",
                text(
                        document,
                        "identificacionComprador"
                )
        );

        assertEquals(
                "18.00",
                text(
                        document,
                        "totalSinImpuestos"
                )
        );

        assertEquals(
                "2.00",
                text(
                        document,
                        "totalDescuento"
                )
        );

        assertEquals(
                "20.70",
                text(
                        document,
                        "importeTotal"
                )
        );

        assertEquals(
                "P001",
                text(
                        document,
                        "codigoPrincipal"
                )
        );

        assertEquals(
                "01",
                text(
                        document,
                        "formaPago"
                )
        );

        assertEquals(
                TEST_PROVIDER_RUC,
                providerRuc(
                        document
                )
        );
    }

    @Test
    void shouldValidateGeneratedInvoiceAgainstSriXsd()
            throws Exception {

        GeneratedInvoiceXml generated =
                generator.generate(
                        invoiceData()
                );

        URL schemaUrl =
                Objects.requireNonNull(
                        getClass()
                                .getResource(
                                        "/sri/xsd/factura_V2.1.0.xsd"
                                ),
                        "SRI invoice XSD not found"
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
                                                generated.content()
                                        )
                                )
                        )
        );
    }

    private static InvoiceXmlData invoiceData() {

        return new InvoiceXmlData(

                "1",

                "FAXORF LOCAL DEVELOPMENT S.A.S.",
                "FAXORF",
                "1790012345001",

                ACCESS_KEY,

                "001",
                "001",
                "000000002",

                "Quito, Ecuador",

                LocalDate.of(
                        2026,
                        10,
                        6
                ),

                "Quito, Ecuador",

                new InvoiceXmlData.Buyer(
                        "07",
                        "9999999999999",
                        "CONSUMIDOR FINAL",
                        null
                ),

                money(
                        "18.00"
                ),

                money(
                        "2.00"
                ),

                List.of(
                        new InvoiceXmlData.TaxTotal(
                                "2",
                                "4",
                                money(
                                        "18.00"
                                ),
                                money(
                                        "2.70"
                                )
                        )
                ),

                money(
                        "0.00"
                ),

                money(
                        "20.70"
                ),

                "DOLAR",

                List.of(
                        new InvoiceXmlData.Item(
                                "P001",
                                "Producto de prueba",
                                decimal(
                                        "2"
                                ),
                                decimal(
                                        "10.00"
                                ),
                                money(
                                        "2.00"
                                ),
                                money(
                                        "18.00"
                                ),
                                List.of(
                                        new InvoiceXmlData.ItemTax(
                                                "2",
                                                "4",
                                                decimal(
                                                        "15.00"
                                                ),
                                                money(
                                                        "18.00"
                                                ),
                                                money(
                                                        "2.70"
                                                )
                                        )
                                )
                        )
                ),

                List.of(
                        new InvoiceXmlData.Payment(
                                "01",
                                money(
                                        "20.70"
                                ),
                                null,
                                null
                        )
                ),

                TEST_PROVIDER_RUC
        );
    }

    private static Document parse(
            String xml
    ) throws Exception {

        DocumentBuilderFactory factory =
                DocumentBuilderFactory
                        .newInstance();

        factory.setFeature(
                XMLConstants.FEATURE_SECURE_PROCESSING,
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

    private static String text(
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

    private static String providerRuc(
            Document document
    ) {

        var nodes =
                document.getElementsByTagName(
                        "campoAdicional"
                );

        for (int index = 0;
             index < nodes.getLength();
             index++) {

            Element element =
                    (Element) nodes.item(
                            index
                    );

            if ("RUC Proveedor".equals(
                    element.getAttribute(
                            "nombre"
                    )
            )) {

                return element
                        .getTextContent();
            }
        }

        throw new AssertionError(
                "RUC Proveedor was not generated"
        );
    }

    private static BigDecimal money(
            String value
    ) {

        return new BigDecimal(
                value
        );
    }

    private static BigDecimal decimal(
            String value
    ) {

        return new BigDecimal(
                value
        );
    }
}