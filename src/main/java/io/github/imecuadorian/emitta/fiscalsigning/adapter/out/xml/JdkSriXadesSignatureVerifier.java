package io.github.imecuadorian.emitta.fiscalsigning.adapter.out.xml;

import io.github.imecuadorian.emitta.fiscalsigning.application.exception.XmlSignatureVerificationException;
import io.github.imecuadorian.emitta.fiscalsigning.application.port.out.XmlSignatureVerifierPort;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.crypto.URIReferenceException;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.parsers.DocumentBuilderFactory;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class JdkSriXadesSignatureVerifier
        implements XmlSignatureVerifierPort {

    private static final String XMLDSIG =
            "http://www.w3.org/2000/09/xmldsig#";

    private static final String XADES =
            "http://uri.etsi.org/01903/v1.3.2#";

    private static final int MAX_XML_BYTES =
            10 * 1024 * 1024;

    private static final Set<String> ALLOWED_TRANSFORMS =
            Set.of(
                    Transform.ENVELOPED,
                    CanonicalizationMethod.INCLUSIVE,
                    CanonicalizationMethod.INCLUSIVE_WITH_COMMENTS,
                    CanonicalizationMethod.EXCLUSIVE,
                    CanonicalizationMethod.EXCLUSIVE_WITH_COMMENTS
            );

    @Override
    public void verify(byte[] signedXml) {
        verify(signedXml, null);
    }

    public void verify(byte[] signedXml, X509Certificate expectedCertificate) {

        Objects.requireNonNull(
                signedXml,
                "Signed XML cannot be null"
        );

        if (signedXml.length == 0
                || signedXml.length > MAX_XML_BYTES) {

            throw new XmlSignatureVerificationException(
                    "Invalid signed XML size"
            );
        }

        try {

            Document document = parseXml(signedXml);

            Element root = document.getDocumentElement();

            /*
             * Current profile: Ecuador SRI invoice.
             * Other document types can be added separately.
             */
            if (!"factura".equals(root.getLocalName())) {

                throw new XmlSignatureVerificationException(
                        "Expected signed SRI invoice"
                );
            }

            Element signatureElement =
                    requireSingleElement(
                            document,
                            XMLDSIG,
                            "Signature"
                    );

            /*
             * Require an enveloped signature directly under
             * the fiscal document.
             */
            if (signatureElement.getParentNode() != root) {

                throw new XmlSignatureVerificationException(
                        "XML signature is not enveloped in invoice"
                );
            }

            Element keyInfo =
                    requireSingleElement(
                            document,
                            XMLDSIG,
                            "KeyInfo"
                    );

            Element signedProperties =
                    requireSingleElement(
                            document,
                            XADES,
                            "SignedProperties"
                    );

            String keyInfoId = requireId(keyInfo);
            String signedPropertiesId =
                    requireId(signedProperties);

            X509Certificate certificate =
                    extractCertificate(keyInfo);

            if (expectedCertificate != null && !java.util.Arrays.equals(
                    expectedCertificate.getEncoded(), certificate.getEncoded())) {
                throw new XmlSignatureVerificationException("XML certificate differs from selected signing certificate");
            }

            if (!(certificate.getPublicKey()
                    instanceof RSAPublicKey)) {

                throw new XmlSignatureVerificationException(
                        "SRI XML signature must use an RSA key"
                );
            }

            /*
             * Register XML IDs and reject duplicate IDs
             * to reduce signature wrapping risks.
             */
            Set<String> registeredIds = new HashSet<>();

            registerIds(
                    root,
                    registeredIds
            );

            XMLSignatureFactory factory =
                    XMLSignatureFactory.getInstance("DOM");

            DOMValidateContext context =
                    new DOMValidateContext(
                            certificate.getPublicKey(),
                            signatureElement
                    );

            /*
             * The legacy SRI profile uses RSA-SHA1.
             * Secure validation is disabled only for
             * compatibility with this signature profile.
             *
             * External references and unsafe transforms
             * are explicitly rejected below.
             */
            context.setProperty(
                    "org.jcp.xml.dsig.secureValidation",
                    Boolean.FALSE
            );

            var defaultDereferencer =
                    factory.getURIDereferencer();

            context.setURIDereferencer(
                    (reference, cryptoContext) -> {

                        String uri = reference.getURI();

                        boolean allowed =
                                uri != null
                                        && (
                                        uri.isEmpty()
                                                || (
                                                uri.startsWith("#")
                                                        && registeredIds.contains(
                                                        uri.substring(1)
                                                )
                                        )
                                );

                        if (!allowed) {

                            throw new URIReferenceException(
                                    "External XML signature reference is forbidden"
                            );
                        }

                        return defaultDereferencer.dereference(
                                reference,
                                cryptoContext
                        );
                    }
            );

            XMLSignature signature =
                    factory.unmarshalXMLSignature(context);

            validateProfile(
                    signature,
                    root,
                    keyInfoId,
                    signedPropertiesId
            );

            if (!signature.validate(context)) {

                throw new XmlSignatureVerificationException(
                        "SRI XAdES cryptographic validation failed"
                );
            }

        } catch (XmlSignatureVerificationException exception) {

            throw exception;

        } catch (Exception exception) {

            throw new XmlSignatureVerificationException(
                    "Unable to verify SRI XAdES signature",
                    exception
            );
        }
    }

    private static Document parseXml(
            byte[] xml
    ) throws Exception {

        DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();

        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        factory.setFeature(
                XMLConstants.FEATURE_SECURE_PROCESSING,
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

        factory.setAttribute(
                XMLConstants.ACCESS_EXTERNAL_DTD,
                ""
        );

        factory.setAttribute(
                XMLConstants.ACCESS_EXTERNAL_SCHEMA,
                ""
        );

        return factory.newDocumentBuilder()
                .parse(
                        new ByteArrayInputStream(xml)
                );
    }

    private static Element requireSingleElement(
            Document document,
            String namespace,
            String localName
    ) {

        NodeList elements =
                document.getElementsByTagNameNS(
                        namespace,
                        localName
                );

        if (elements.getLength() != 1) {

            throw new XmlSignatureVerificationException(
                    "Expected exactly one XML element: "
                            + localName
            );
        }

        return (Element) elements.item(0);
    }

    private static String requireId(
            Element element
    ) {

        for (String name : new String[]{
                "Id", "ID", "id"
        }) {

            if (element.hasAttribute(name)) {

                String value = element.getAttribute(name);

                if (!value.isBlank()) {
                    return value;
                }
            }
        }

        throw new XmlSignatureVerificationException(
                "Missing signed XML element ID: "
                        + element.getLocalName()
        );
    }

    private static X509Certificate extractCertificate(
            Element keyInfo
    ) throws Exception {

        NodeList certificates =
                keyInfo.getElementsByTagNameNS(
                        XMLDSIG,
                        "X509Certificate"
                );

        if (certificates.getLength() == 0) {

            throw new XmlSignatureVerificationException(
                    "Signing certificate is missing"
            );
        }

        byte[] encoded =
                Base64.getMimeDecoder()
                        .decode(
                                certificates.item(0)
                                        .getTextContent()
                        );

        CertificateFactory factory =
                CertificateFactory.getInstance("X.509");

        return (X509Certificate)
                factory.generateCertificate(
                        new ByteArrayInputStream(encoded)
                );
    }

    private static void registerIds(
            Element element,
            Set<String> registeredIds
    ) {

        for (String name : new String[]{
                "Id", "ID", "id"
        }) {

            if (!element.hasAttribute(name)) {
                continue;
            }

            String value =
                    element.getAttribute(name);

            if (value.isBlank()
                    || !registeredIds.add(value)) {

                throw new XmlSignatureVerificationException(
                        "Duplicate or empty XML ID"
                );
            }

            element.setIdAttribute(
                    name,
                    true
            );
        }

        NodeList children = element.getChildNodes();

        for (int index = 0;
             index < children.getLength();
             index++) {

            Node child = children.item(index);

            if (child instanceof Element childElement) {

                registerIds(
                        childElement,
                        registeredIds
                );
            }
        }
    }

    private static void validateProfile(
            XMLSignature signature,
            Element root,
            String keyInfoId,
            String signedPropertiesId
    ) {

        if (!SignatureMethod.RSA_SHA1.equals(
                signature.getSignedInfo()
                        .getSignatureMethod()
                        .getAlgorithm()
        )) {

            throw new XmlSignatureVerificationException(
                    "Unsupported SRI signature algorithm"
            );
        }

        boolean documentSigned = false;
        boolean keyInfoSigned = false;
        boolean signedPropertiesProtected = false;

        String rootId = requireId(root);

        for (Object item :
                signature.getSignedInfo().getReferences()) {

            Reference reference = (Reference) item;

            String uri = reference.getURI();

            if (!DigestMethod.SHA1.equals(
                    reference.getDigestMethod()
                            .getAlgorithm()
            )) {

                throw new XmlSignatureVerificationException(
                        "Unsupported SRI reference digest algorithm"
                );
            }

            for (Transform transform :
                    reference.getTransforms()) {

                String algorithm = transform.getAlgorithm();

                if (!ALLOWED_TRANSFORMS.contains(algorithm)) {

                    throw new XmlSignatureVerificationException(
                            "Unsupported XML signature transform: "
                                    + algorithm
                    );
                }
            }

            if ("#comprobante".equals(uri)) {
                if (!"comprobante".equals(rootId)
                        || !"comprobante".equals(root.getAttribute("id"))
                        || documentSigned
                        || reference.getTransforms().isEmpty()
                        || !Transform.ENVELOPED.equals(reference.getTransforms().getFirst().getAlgorithm())
                        || reference.getTransforms().stream().skip(1)
                            .anyMatch(t -> Transform.ENVELOPED.equals(t.getAlgorithm()))) {
                    throw new XmlSignatureVerificationException("Invalid SRI comprobante reference or enveloped transform");
                }
                documentSigned = true;
            }

            if (("#" + keyInfoId).equals(uri)) {
                keyInfoSigned = true;
            }

            if (("#" + signedPropertiesId).equals(uri)) {
                if (!"http://uri.etsi.org/01903#SignedProperties".equals(reference.getType())) {
                    throw new XmlSignatureVerificationException("Invalid SignedProperties reference type");
                }
                signedPropertiesProtected = true;
            }
            if (!"#comprobante".equals(uri) && !("#" + keyInfoId).equals(uri)
                    && !("#" + signedPropertiesId).equals(uri)) {
                throw new XmlSignatureVerificationException("Unexpected SRI signed reference");
            }
        }

        if (!documentSigned
                || !keyInfoSigned
                || !signedPropertiesProtected) {

            throw new XmlSignatureVerificationException(
                    "Incomplete SRI XAdES signed references"
            );
        }
    }

}
