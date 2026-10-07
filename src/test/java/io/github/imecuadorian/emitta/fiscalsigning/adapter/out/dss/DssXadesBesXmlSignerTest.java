package io.github.imecuadorian.emitta.fiscalsigning.adapter.out.dss;

import io.github.imecuadorian.emitta.fiscalsigning.application.model.SignedXml;
import io.github.imecuadorian.emitta.fiscalsigning.application.model.SigningKeyMaterial;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.parsers.DocumentBuilderFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class DssXadesBesXmlSignerTest {

    private static final String ALIAS =
            "emitta-test";

    private static final String PASSWORD =
            "emitta-test-password";

    private static final String XMLDSIG_NAMESPACE =
            "http://www.w3.org/2000/09/xmldsig#";

    private static final String XADES_NAMESPACE =
            "http://uri.etsi.org/01903/v1.3.2#";

    @TempDir
    Path tempDirectory;

    @Test
    void shouldCreateCryptographicallyValidSriXadesBesSignature()
            throws Exception {

        Path pkcs12Path =
                generatePkcs12();

        byte[] pkcs12 =
                Files.readAllBytes(
                        pkcs12Path
                );

        char[] password =
                PASSWORD.toCharArray();

        X509Certificate certificate =
                loadCertificate(
                        pkcs12Path
                );

        assertInstanceOf(
                RSAPublicKey.class,
                certificate.getPublicKey()
        );

        RSAPublicKey rsaPublicKey =
                (RSAPublicKey) certificate.getPublicKey();

        assertEquals(
                2048,
                rsaPublicKey
                        .getModulus()
                        .bitLength()
        );

        byte[] unsignedXml =
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <factura id="comprobante" version="2.1.0">
                    <infoTributaria>
                        <ambiente>1</ambiente>
                        <tipoEmision>1</tipoEmision>
                        <razonSocial>EMITTA TEST S.A.S.</razonSocial>
                        <ruc>1790012345001</ruc>
                        <claveAcceso>0610202601179001234500110010010000000011230619114</claveAcceso>
                        <codDoc>01</codDoc>
                        <estab>001</estab>
                        <ptoEmi>001</ptoEmi>
                        <secuencial>000000001</secuencial>
                        <dirMatriz>Quito</dirMatriz>
                    </infoTributaria>
                </factura>
                """
                        .getBytes(
                                StandardCharsets.UTF_8
                        );

        DssXadesBesXmlSigner signer =
                new DssXadesBesXmlSigner(
                        Clock.systemUTC()
                );

        SignedXml signed =
                signer.sign(
                        unsignedXml,
                        new SigningKeyMaterial(
                                pkcs12,
                                password,
                                null
                        )
                );

        byte[] signedXml =
                signed.content();

        assertNotNull(
                signedXml
        );

        assertTrue(
                signedXml.length
                        > unsignedXml.length
        );

        Document document =
                parseXml(
                        signedXml
                );

        /*
         * SRI requires an enveloped signature: the signature must
         * live inside the electronic document.
         */
        NodeList signatures =
                document.getElementsByTagNameNS(
                        XMLDSIG_NAMESPACE,
                        "Signature"
                );

        assertEquals(
                1,
                signatures.getLength()
        );

        /*
         * XAdES-BES must contain SignedProperties.
         */
        NodeList signedProperties =
                document.getElementsByTagNameNS(
                        XADES_NAMESPACE,
                        "SignedProperties"
                );

        assertEquals(
                1,
                signedProperties.getLength()
        );

        /*
         * KeyInfo must contain the signing X.509 certificate.
         */
        NodeList certificates =
                document.getElementsByTagNameNS(
                        XMLDSIG_NAMESPACE,
                        "X509Certificate"
                );

        assertTrue(
                certificates.getLength() >= 1
        );

        /*
         * SRI requires the legacy XAdES 1.3.2 namespace.
         */
        NodeList qualifyingProperties =
                document.getElementsByTagNameNS(
                        XADES_NAMESPACE,
                        "QualifyingProperties"
                );

        assertEquals(
                1,
                qualifyingProperties.getLength()
        );

        assertEquals(
                XADES_NAMESPACE,
                qualifyingProperties
                        .item(0)
                        .getNamespaceURI()
        );

        /*
         * SRI requires KeyInfo, including the signing certificate,
         * to be protected by the XML signature.
         */
        NodeList keyInfoNodes =
                document.getElementsByTagNameNS(
                        XMLDSIG_NAMESPACE,
                        "KeyInfo"
                );

        assertEquals(
                1,
                keyInfoNodes.getLength()
        );

        Element keyInfo =
                (Element) keyInfoNodes.item(
                        0
                );

        String keyInfoId =
                keyInfo.getAttribute(
                        "Id"
                );

        assertFalse(
                keyInfoId.isBlank(),
                "KeyInfo must have an Id so it can be signed"
        );

        NodeList references =
                document.getElementsByTagNameNS(
                        XMLDSIG_NAMESPACE,
                        "Reference"
                );

        boolean keyInfoIsSigned =
                false;

        for (
                int index = 0;
                index < references.getLength();
                index++
        ) {

            Element reference =
                    (Element) references.item(
                            index
                    );

            if (
                    ("#" + keyInfoId)
                            .equals(
                                    reference.getAttribute(
                                            "URI"
                                    )
                            )
            ) {

                keyInfoIsSigned =
                        true;

                break;
            }
        }

        assertTrue(
                keyInfoIsSigned,
                "KeyInfo must be referenced by SignedInfo"
        );

        NodeList signingCertificates =
                document.getElementsByTagNameNS(
                        XADES_NAMESPACE,
                        "SigningCertificate"
                );

        assertEquals(
                1,
                signingCertificates.getLength()
        );

        NodeList signingCertificatesV2 =
                document.getElementsByTagNameNS(
                        XADES_NAMESPACE,
                        "SigningCertificateV2"
                );

        assertEquals(
                0,
                signingCertificatesV2.getLength()
        );

        byte[] embeddedCertificate =
                Base64.getMimeDecoder()
                        .decode(
                                certificates
                                        .item(0)
                                        .getTextContent()
                        );

        assertArrayEquals(
                certificate.getEncoded(),
                embeddedCertificate
        );

        /*
         * SRI v2.34 requires RSA-SHA1 for the XML signature.
         */
        Element signatureMethod =
                (Element) document
                        .getElementsByTagNameNS(
                                XMLDSIG_NAMESPACE,
                                "SignatureMethod"
                        )
                        .item(0);

        assertEquals(
                SignatureMethod.RSA_SHA1,
                signatureMethod.getAttribute(
                        "Algorithm"
                )
        );

        /*
         * References must use SHA-1 as configured for the SRI
         * compatibility profile.
         */
        NodeList digestMethods =
                document.getElementsByTagNameNS(
                        XMLDSIG_NAMESPACE,
                        "DigestMethod"
                );

        assertTrue(
                digestMethods.getLength() >= 1
        );

        for (
                int index = 0;
                index < digestMethods.getLength();
                index++
        ) {

            Element digestMethod =
                    (Element) digestMethods.item(
                            index
                    );

            assertEquals(
                    DigestMethod.SHA1,
                    digestMethod.getAttribute(
                            "Algorithm"
                    )
            );
        }

        /*
         * Register XML Id attributes before JSR-105 validation so
         * references to SignedProperties / KeyInfo can be resolved.
         */
        registerIdAttributes(
                document.getDocumentElement()
        );

        validateCryptographically(
                document,
                certificate
        );
    }

    private Path generatePkcs12()
            throws Exception {

        Path pkcs12Path =
                tempDirectory.resolve(
                        "emitta-test.p12"
                );

        Path keytool =
                resolveKeytool();

        Process process =
                new ProcessBuilder(
                        keytool.toString(),
                        "-genkeypair",
                        "-alias",
                        ALIAS,
                        "-keyalg",
                        "RSA",
                        "-keysize",
                        "2048",
                        "-sigalg",
                        "SHA256withRSA",
                        "-dname",
                        "CN=Emitta Test, OU=QA, O=Emitta, L=Quito, C=EC",
                        "-validity",
                        "3650",
                        "-storetype",
                        "PKCS12",
                        "-keystore",
                        pkcs12Path.toString(),
                        "-storepass",
                        PASSWORD,
                        "-keypass",
                        PASSWORD,
                        "-noprompt"
                )
                        .redirectErrorStream(
                                true
                        )
                        .start();

        String output;

        try (
                InputStream input =
                        process.getInputStream()
        ) {

            output =
                    new String(
                            input.readAllBytes(),
                            StandardCharsets.UTF_8
                    );
        }

        int exitCode =
                process.waitFor();

        assertEquals(
                0,
                exitCode,
                () ->
                        "keytool failed:\n"
                                + output
        );

        assertTrue(
                Files.exists(
                        pkcs12Path
                )
        );

        return pkcs12Path;
    }

    private X509Certificate loadCertificate(
            Path pkcs12Path
    ) throws Exception {

        KeyStore keyStore =
                KeyStore.getInstance(
                        "PKCS12"
                );

        try (
                InputStream input =
                        Files.newInputStream(
                                pkcs12Path
                        )
        ) {

            keyStore.load(
                    input,
                    PASSWORD.toCharArray()
            );
        }

        return (X509Certificate)
                keyStore.getCertificate(
                        ALIAS
                );
    }

    private static Document parseXml(
            byte[] xml
    ) throws Exception {

        DocumentBuilderFactory factory =
                DocumentBuilderFactory
                        .newInstance();

        factory.setNamespaceAware(
                true
        );

        factory.setFeature(
                "http://apache.org/xml/features/disallow-doctype-decl",
                true
        );

        factory.setFeature(
                "http://xml.org/sax/features/external-general-entities",
                false
        );

        factory.setFeature(
                "http://xml.org/sax/features/external-parameter-entities",
                false
        );

        return factory
                .newDocumentBuilder()
                .parse(
                        new ByteArrayInputStream(
                                xml
                        )
                );
    }

    private static void registerIdAttributes(
            Element element
    ) {

        registerIdAttribute(
                element,
                "Id"
        );

        registerIdAttribute(
                element,
                "ID"
        );

        registerIdAttribute(
                element,
                "id"
        );

        NodeList children =
                element.getChildNodes();

        for (
                int index = 0;
                index < children.getLength();
                index++
        ) {

            Node child =
                    children.item(
                            index
                    );

            if (
                    child instanceof Element childElement
            ) {

                registerIdAttributes(
                        childElement
                );
            }
        }
    }

    private static void registerIdAttribute(
            Element element,
            String attributeName
    ) {

        if (
                element.hasAttribute(
                        attributeName
                )
        ) {

            element.setIdAttribute(
                    attributeName,
                    true
            );
        }
    }

    private static void validateCryptographically(
            Document document,
            X509Certificate certificate
    ) throws Exception {

        Node signatureNode =
                document
                        .getElementsByTagNameNS(
                                XMLDSIG_NAMESPACE,
                                "Signature"
                        )
                        .item(0);

        assertNotNull(
                signatureNode
        );

        DOMValidateContext context =
                new DOMValidateContext(
                        certificate.getPublicKey(),
                        signatureNode
                );

        /*
         * Modern JDKs reject SHA-1 under secure-validation rules.
         * We disable that policy only in this compatibility test
         * because SRI v2.34 explicitly requires RSA-SHA1.
         */
        context.setProperty(
                "org.jcp.xml.dsig.secureValidation",
                Boolean.FALSE
        );

        XMLSignatureFactory factory =
                XMLSignatureFactory
                        .getInstance(
                                "DOM"
                        );

        XMLSignature signature =
                factory.unmarshalXMLSignature(
                        context
                );

        assertTrue(
                signature
                        .getSignatureValue()
                        .validate(
                                context
                        ),
                "XML SignatureValue is cryptographically invalid"
        );

        @SuppressWarnings("unchecked")
        List<Reference> references =
                signature
                        .getSignedInfo()
                        .getReferences();

        assertFalse(
                references.isEmpty()
        );

        for (
                Reference reference :
                references
        ) {

            assertTrue(
                    reference.validate(
                            context
                    ),
                    () ->
                            "Invalid XML signature reference: "
                                    + reference.getURI()
            );
        }

        assertTrue(
                signature.validate(
                        context
                ),
                "Complete XML signature validation failed"
        );
    }

    private static Path resolveKeytool() {

        boolean windows =
                System.getProperty(
                                "os.name"
                        )
                        .toLowerCase(
                                Locale.ROOT
                        )
                        .contains(
                                "win"
                        );

        String executable =
                windows
                        ? "keytool.exe"
                        : "keytool";

        Path keytool =
                Path.of(
                        System.getProperty(
                                "java.home"
                        ),
                        "bin",
                        executable
                );

        if (
                !Files.isRegularFile(
                        keytool
                )
        ) {

            throw new IllegalStateException(
                    "keytool was not found at: "
                            + keytool
            );
        }

        return keytool;
    }
}