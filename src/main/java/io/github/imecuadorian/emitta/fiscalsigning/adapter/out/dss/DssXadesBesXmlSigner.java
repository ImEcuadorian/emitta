package io.github.imecuadorian.emitta.fiscalsigning.adapter.out.dss;

import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.EncryptionAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.enumerations.SignaturePackaging;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.spi.DSSUtils;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.token.DSSPrivateKeyEntry;
import eu.europa.esig.dss.token.Pkcs12SignatureToken;
import eu.europa.esig.dss.xades.XAdESSignatureParameters;
import eu.europa.esig.dss.xades.signature.XAdESService;
import eu.europa.esig.dss.xades.reference.DSSReference;
import eu.europa.esig.dss.xades.reference.EnvelopedSignatureTransform;

import io.github.imecuadorian.emitta.fiscalsigning.application.exception.XmlSigningException;
import io.github.imecuadorian.emitta.fiscalsigning.application.model.SignedXml;
import io.github.imecuadorian.emitta.fiscalsigning.application.model.SigningKeyMaterial;
import io.github.imecuadorian.emitta.fiscalsigning.application.port.out.XmlSignerPort;

import java.security.KeyStore;
import java.time.Clock;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;

public final class DssXadesBesXmlSigner
        implements XmlSignerPort {

    private final Clock clock;

    public DssXadesBesXmlSigner(
            Clock clock
    ) {

        this.clock =
                Objects.requireNonNull(
                        clock
                );
    }

    @Override
    public SignedXml sign(
            byte[] unsignedXml,
            SigningKeyMaterial keyMaterial
    ) {

        Objects.requireNonNull(
                unsignedXml,
                "Unsigned XML cannot be null"
        );

        if (unsignedXml.length == 0) {
            throw new IllegalArgumentException(
                    "Unsigned XML cannot be empty"
            );
        }

        Objects.requireNonNull(
                keyMaterial,
                "Signing key material cannot be null"
        );

        byte[] pkcs12Content =
                keyMaterial.pkcs12Content();

        char[] password =
                keyMaterial.password();

        try (
                Pkcs12SignatureToken token =
                        new Pkcs12SignatureToken(
                                pkcs12Content,
                                new KeyStore.PasswordProtection(
                                        password
                                )
                        )
        ) {

            DSSPrivateKeyEntry privateKey =
                    selectPrivateKey(
                            token,
                            keyMaterial.alias()
                    );

            requireRsaKey(
                    privateKey
            );

            DSSDocument document =
                    new InMemoryDocument(
                            unsignedXml.clone(),
                            "invoice.xml"
                    );

            XAdESSignatureParameters parameters =
                    createSignatureParameters(
                            privateKey
                    );

            // Override DSS's whole-document URI and XPath Filter2 defaults before digesting.
            DSSReference comprobante = new DSSReference();
            comprobante.setId("Reference-comprobante");
            comprobante.setUri("#comprobante");
            comprobante.setContents(document);
            comprobante.setDigestMethodAlgorithm(DigestAlgorithm.SHA1);
            comprobante.setTransforms(List.of(new EnvelopedSignatureTransform()));
            parameters.setReferences(List.of(comprobante));

            CommonCertificateVerifier certificateVerifier =
                    new CommonCertificateVerifier();

            XAdESService service =
                    new XAdESService(
                            certificateVerifier
                    );

            ToBeSigned dataToSign =
                    service.getDataToSign(
                            document,
                            parameters
                    );

            SignatureValue signatureValue =
                    token.sign(
                            dataToSign,
                            DigestAlgorithm.SHA1,
                            privateKey
                    );

            DSSDocument signedDocument =
                    service.signDocument(
                            document,
                            parameters,
                            signatureValue
                    );

            byte[] signedBytes = DSSUtils.toByteArray(signedDocument);
            new io.github.imecuadorian.emitta.fiscalsigning.adapter.out.xml.JdkSriXadesSignatureVerifier()
                    .verify(signedBytes, privateKey.getCertificate().getCertificate());
            return new SignedXml(signedBytes);

        } catch (XmlSigningException exception) {

            throw exception;

        } catch (Exception exception) {

            throw new XmlSigningException(
                    "Unable to create SRI XAdES-BES signature",
                    exception
            );

        } finally {

            Arrays.fill(
                    password,
                    '\0'
            );

            Arrays.fill(
                    pkcs12Content,
                    (byte) 0
            );
        }
    }

    private XAdESSignatureParameters
    createSignatureParameters(
            DSSPrivateKeyEntry privateKey
    ) {

        XAdESSignatureParameters parameters =
                new XAdESSignatureParameters();

        /*
         * DSS 6.5 creates the initial XAdES signature through its
         * Baseline-B profile. XAdES_BES is not a supported signing
         * extension target in this DSS version.
         *
         * SRI requires the legacy XAdES-BES 1.3.2 structure, so the
         * modern EN 319 132 profile is explicitly disabled below.
         */
        parameters.setSignatureLevel(
                SignatureLevel.XAdES_BASELINE_B
        );

        parameters.setSignaturePackaging(
                SignaturePackaging.ENVELOPED
        );

        /*
         * SRI Technical Specification v2.34 requires RSA-SHA1.
         */
        parameters.setDigestAlgorithm(
                DigestAlgorithm.SHA1
        );

        /*
         * Document and XAdES references must use SHA-1 for the
         * SRI compatibility profile.
         */
        parameters.setReferenceDigestAlgorithm(
                DigestAlgorithm.SHA1
        );

        /*
         * SRI's XAdES-BES profile also uses SHA-1 for the digest
         * of the signing certificate.
         */
        parameters.setSigningCertificateDigestMethod(
                DigestAlgorithm.SHA1
        );

        /*
         * Generate the signature according to the legacy XAdES
         * standard expected by SRI instead of ETSI EN 319 132.
         */
        parameters.setEn319132(
                false
        );

        /*
         * SRI requires KeyInfo, including the signing certificate,
         * to be protected by the signature.
         */
        parameters.setSignKeyInfo(
                true
        );

        parameters.setSigningCertificate(
                privateKey.getCertificate()
        );

        parameters.setCertificateChain(
                privateKey.getCertificateChain()
        );

        parameters.bLevel()
                .setSigningDate(
                        Date.from(
                                clock.instant()
                        )
                );

        return parameters;
    }

    private static DSSPrivateKeyEntry selectPrivateKey(
            Pkcs12SignatureToken token,
            String alias
    ) {

        if (alias != null) {

            DSSPrivateKeyEntry privateKey =
                    token.getKey(
                            alias
                    );

            if (privateKey == null) {
                throw new XmlSigningException(
                        "PKCS#12 does not contain private key alias: "
                                + alias
                );
            }

            return privateKey;
        }

        List<DSSPrivateKeyEntry> keys =
                token.getKeys();

        if (keys.isEmpty()) {
            throw new XmlSigningException(
                    "PKCS#12 does not contain a private key"
            );
        }

        if (keys.size() != 1) {
            throw new XmlSigningException(
                    "PKCS#12 contains "
                            + keys.size()
                            + " private keys; an explicit alias is required"
            );
        }

        return keys.get(
                0
        );
    }

    private static void requireRsaKey(
            DSSPrivateKeyEntry privateKey
    ) {

        if (
                privateKey.getEncryptionAlgorithm()
                        != EncryptionAlgorithm.RSA
        ) {

            throw new XmlSigningException(
                    "SRI signing requires an RSA private key, but certificate uses: "
                            + privateKey.getEncryptionAlgorithm()
            );
        }
    }
}
