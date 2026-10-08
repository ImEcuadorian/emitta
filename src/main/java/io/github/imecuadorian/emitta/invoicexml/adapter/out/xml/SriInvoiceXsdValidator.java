package io.github.imecuadorian.emitta.invoicexml.adapter.out.xml;

import io.github.imecuadorian.emitta.invoicexml.application.exception.InvalidInvoiceXmlException;
import io.github.imecuadorian.emitta.invoicexml.application.port.out.InvoiceXmlValidatorPort;

import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URL;
import java.util.Objects;

public final class SriInvoiceXsdValidator
        implements InvoiceXmlValidatorPort {

    private static final String XSD_RESOURCE =
            "/sri/xsd/factura_V2.1.0.xsd";

    private static final String XMLDSIG_XSD_RESOURCE =
            "/sri/xsd/xmldsig-core-schema.xsd";

    private static final String XMLDSIG_NAMESPACE =
            "http://www.w3.org/2000/09/xmldsig#";

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
                    ""
            );

            factory.setResourceResolver(
                    new ClasspathSchemaResolver()
            );

            InputStream schemaStream =
                    requiredResource(
                            XSD_RESOURCE
                    );

            try (schemaStream) {

                StreamSource source =
                        new StreamSource(
                                schemaStream
                        );

                source.setSystemId(
                        "classpath:"
                                + XSD_RESOURCE
                );

                return factory.newSchema(
                        source
                );
            }

        } catch (
                SAXException
                | IOException exception
        ) {

            throw new IllegalStateException(
                    "Unable to load SRI invoice XSD",
                    exception
            );
        }
    }

    private static InputStream requiredResource(
            String resource
    ) {

        InputStream inputStream =
                SriInvoiceXsdValidator.class
                        .getResourceAsStream(
                                resource
                        );

        if (inputStream == null) {

            throw new IllegalStateException(
                    "Schema resource not found: "
                            + resource
            );
        }

        return inputStream;
    }

    private static final class ClasspathSchemaResolver
            implements LSResourceResolver {

        @Override
        public LSInput resolveResource(
                String type,
                String namespaceUri,
                String publicId,
                String systemId,
                String baseUri
        ) {

            boolean xmlDsigSchema =
                    XMLDSIG_NAMESPACE.equals(
                            namespaceUri
                    )
                            || "xmldsig-core-schema.xsd"
                            .equals(
                                    systemId
                            );

            if (!xmlDsigSchema) {
                return null;
            }

            return new ClasspathLsInput(
                    publicId,
                    "classpath:"
                            + XMLDSIG_XSD_RESOURCE,
                    requiredResource(
                            XMLDSIG_XSD_RESOURCE
                    )
            );
        }
    }

    private static final class ClasspathLsInput
            implements LSInput {

        private Reader characterStream;
        private InputStream byteStream;
        private String stringData;
        private String systemId;
        private String publicId;
        private String baseUri;
        private String encoding;
        private boolean certifiedText;

        private ClasspathLsInput(
                String publicId,
                String systemId,
                InputStream byteStream
        ) {

            this.publicId = publicId;
            this.systemId = systemId;
            this.byteStream = byteStream;
        }

        @Override
        public Reader getCharacterStream() {
            return characterStream;
        }

        @Override
        public void setCharacterStream(
                Reader characterStream
        ) {
            this.characterStream = characterStream;
        }

        @Override
        public InputStream getByteStream() {
            return byteStream;
        }

        @Override
        public void setByteStream(
                InputStream byteStream
        ) {
            this.byteStream = byteStream;
        }

        @Override
        public String getStringData() {
            return stringData;
        }

        @Override
        public void setStringData(
                String stringData
        ) {
            this.stringData = stringData;
        }

        @Override
        public String getSystemId() {
            return systemId;
        }

        @Override
        public void setSystemId(
                String systemId
        ) {
            this.systemId = systemId;
        }

        @Override
        public String getPublicId() {
            return publicId;
        }

        @Override
        public void setPublicId(
                String publicId
        ) {
            this.publicId = publicId;
        }

        @Override
        public String getBaseURI() {
            return baseUri;
        }

        @Override
        public void setBaseURI(
                String baseUri
        ) {
            this.baseUri = baseUri;
        }

        @Override
        public String getEncoding() {
            return encoding;
        }

        @Override
        public void setEncoding(
                String encoding
        ) {
            this.encoding = encoding;
        }

        @Override
        public boolean getCertifiedText() {
            return certifiedText;
        }

        @Override
        public void setCertifiedText(
                boolean certifiedText
        ) {
            this.certifiedText = certifiedText;
        }
    }
}