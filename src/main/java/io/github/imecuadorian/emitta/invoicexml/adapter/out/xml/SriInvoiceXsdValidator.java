package io.github.imecuadorian.emitta.invoicexml.adapter.out.xml;

import io.github.imecuadorian.emitta.invoicexml.application.exception.InvalidInvoiceXmlException;
import io.github.imecuadorian.emitta.invoicexml.application.port.out.InvoiceXmlValidatorPort;

import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Objects;

public final class SriInvoiceXsdValidator
        implements InvoiceXmlValidatorPort {

    private static final String XSD_RESOURCE =
            "/sri/xsd/factura_V2.1.0.xsd";

    private final Schema schema;

    public SriInvoiceXsdValidator() {

        this.schema =
                loadSchema();
    }

    @Override
    public void validate(
            byte[] xml
    ) {

        Objects.requireNonNull(
                xml,
                "XML cannot be null"
        );

        if (xml.length == 0) {

            throw new IllegalArgumentException(
                    "XML cannot be empty"
            );
        }

        try {

            Validator validator =
                    schema.newValidator();

            validator.setProperty(
                    XMLConstants.ACCESS_EXTERNAL_DTD,
                    ""
            );

            validator.setProperty(
                    XMLConstants.ACCESS_EXTERNAL_SCHEMA,
                    "file"
            );

            validator.validate(
                    new StreamSource(
                            new ByteArrayInputStream(
                                    xml
                            )
                    )
            );

        } catch (
                SAXException
                | IOException exception
        ) {

            throw new InvalidInvoiceXmlException(
                    "Generated invoice XML does not conform to SRI schema",
                    exception
            );
        }
    }

    private static Schema loadSchema() {

        URL resource =
                SriInvoiceXsdValidator.class
                        .getResource(
                                XSD_RESOURCE
                        );

        if (resource == null) {

            throw new IllegalStateException(
                    "SRI invoice XSD not found: "
                            + XSD_RESOURCE
            );
        }

        try {

            SchemaFactory factory =
                    SchemaFactory.newInstance(
                            XMLConstants.W3C_XML_SCHEMA_NS_URI
                    );

            factory.setProperty(
                    XMLConstants.ACCESS_EXTERNAL_DTD,
                    ""
            );

            factory.setProperty(
                    XMLConstants.ACCESS_EXTERNAL_SCHEMA,
                    "file"
            );

            return factory.newSchema(
                    resource
            );

        } catch (SAXException exception) {

            throw new IllegalStateException(
                    "Unable to load SRI invoice XSD",
                    exception
            );
        }
    }
}