package io.github.imecuadorian.emitta.srireception.application.service;

import io.github.imecuadorian.emitta.documentartifact.application.port.in.LoadDocumentArtifactUseCase;
import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifactType;
import io.github.imecuadorian.emitta.shared.fiscal.ProviderPolicyPort;
import org.w3c.dom.Element;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.UUID;

/** Checks existing signed bytes without rewriting them, before state changes or SOAP. */
public final class ProviderSubmissionGuard {
    private final ProviderPolicyPort policy;
    private final LoadDocumentArtifactUseCase artifacts;

    public ProviderSubmissionGuard(ProviderPolicyPort policy, LoadDocumentArtifactUseCase artifacts) {
        this.policy = java.util.Objects.requireNonNull(policy);
        this.artifacts = java.util.Objects.requireNonNull(artifacts);
    }

    public void verify(UUID documentId) {
        String expectedRuc = policy.resolve(documentId).xmlProviderRuc();
        byte[] signed = artifacts.load(documentId, DocumentArtifactType.SIGNED_XML).content();
        verifyXml(signed, expectedRuc);
    }

    public static void verifyXml(byte[] signed, String expectedRuc) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var root = factory.newDocumentBuilder().parse(new ByteArrayInputStream(signed)).getDocumentElement();
            int count = 0;
            String actual = null;
            for (var node = root.getFirstChild(); node != null; node = node.getNextSibling()) {
                if (!(node instanceof Element info) || !"infoAdicional".equals(info.getTagName())) continue;
                for (var child = info.getFirstChild(); child != null; child = child.getNextSibling()) {
                    if (child instanceof Element field && "campoAdicional".equals(field.getTagName())
                            && "RUC Proveedor".equals(field.getAttribute("nombre"))) {
                        count++;
                        actual = field.getTextContent();
                    }
                }
            }
            if (expectedRuc == null ? count != 0 : count != 1 || !expectedRuc.equals(actual))
                throw new IllegalStateException("Signed XML provider identity differs from reviewed policy; submission blocked");
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot verify signed XML provider identity; submission blocked");
        }
    }
}
