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
        var reviewed = policy.resolve(documentId);
        boolean manual = reviewed.mode() == io.github.imecuadorian.emitta.shared.fiscal.ProviderPolicy.Mode.UNRESOLVED
                && io.github.imecuadorian.emitta.shared.fiscal.ManualTestXmlScope.hasApproval(documentId);
        // Normal unresolved policy fails before artifact reads, state changes or any external I/O.
        String expectedRuc = manual ? null : reviewed.xmlProviderRuc();
        byte[] signed = artifacts.load(documentId, DocumentArtifactType.SIGNED_XML).content();
        if (manual && !permitsManualOmission(documentId, signed)) reviewed.xmlProviderRuc();
        verifyXml(signed, expectedRuc);
    }

    static boolean permitsManualOmission(UUID documentId, byte[] signed) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var root = factory.newDocumentBuilder().parse(new ByteArrayInputStream(signed)).getDocumentElement();
            var infos = root.getElementsByTagName("infoTributaria");
            if (!"factura".equals(root.getTagName()) || infos.getLength() != 1) return false;
            var info = (Element) infos.item(0);
            var rucs = info.getElementsByTagName("ruc");
            var environments = info.getElementsByTagName("ambiente");
            return rucs.getLength() == 1 && environments.getLength() == 1
                    && "1".equals(environments.item(0).getTextContent())
                    && io.github.imecuadorian.emitta.shared.fiscal.ManualTestXmlScope.permitsOmission(documentId,
                    io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment.TEST, rucs.item(0).getTextContent());
        } catch (Exception invalid) { return false; }
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
