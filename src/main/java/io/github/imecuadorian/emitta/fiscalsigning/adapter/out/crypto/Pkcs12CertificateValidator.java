package io.github.imecuadorian.emitta.fiscalsigning.adapter.out.crypto;

import io.github.imecuadorian.emitta.fiscalsigning.application.exception.SigningCertificateException;
import io.github.imecuadorian.emitta.fiscalsigning.application.model.SigningKeyMaterial;
import java.io.ByteArrayInputStream;
import java.security.*;
import java.security.cert.*;
import java.time.Instant;
import java.util.*;

/** Offline cryptographic checks. Legal authority is separately reviewed and recorded. */
public final class Pkcs12CertificateValidator {
    private Pkcs12CertificateValidator() {}

    public record Validated(String alias, X509Certificate certificate, X509Certificate trustAnchor) {}
    public record Inspected(String alias, X509Certificate certificate, List<X509Certificate> chain) {}

    /** Inspect metadata with key/validity checks; this does NOT establish CA trust or RUC authority. */
    public static Inspected inspect(SigningKeyMaterial material, Instant time) {
        byte[] content = material.pkcs12Content();
        char[] password = material.password();
        try {
            KeyStore store = KeyStore.getInstance("PKCS12");
            store.load(new ByteArrayInputStream(content), password);
            List<String> aliases = Collections.list(store.aliases()).stream()
                    .filter(alias -> {
                        try { return store.isKeyEntry(alias); }
                        catch (KeyStoreException e) { throw new IllegalStateException(e); }
                    }).toList();
            String alias = material.alias();
            if (alias == null) {
                if (aliases.size() != 1) throw new GeneralSecurityException("Explicit key alias required");
                alias = aliases.getFirst();
            }
            if (!aliases.contains(alias)) throw new GeneralSecurityException("Private key alias missing");
            if (!(store.getKey(alias, password) instanceof PrivateKey key))
                throw new GeneralSecurityException("Private key missing");
            X509Certificate leaf = (X509Certificate) store.getCertificate(alias);
            leaf.checkValidity(Date.from(time));
            if (!"RSA".equals(key.getAlgorithm()) || !"RSA".equals(leaf.getPublicKey().getAlgorithm()))
                throw new GeneralSecurityException("RSA key required");
            if (((java.security.interfaces.RSAPublicKey)leaf.getPublicKey()).getModulus().bitLength() < 2048)
                throw new GeneralSecurityException("RSA key must contain at least 2048 bits");
            boolean[] usage = leaf.getKeyUsage();
            if (leaf.getBasicConstraints() >= 0 || (usage != null && !usage[0] && !usage[1]))
                throw new GeneralSecurityException("Certificate does not permit document signing");
            byte[] challenge = new byte[32];
            new SecureRandom().nextBytes(challenge);
            Signature proof = Signature.getInstance("SHA256withRSA");
            proof.initSign(key);
            proof.update(challenge);
            byte[] signature = proof.sign();
            proof.initVerify(leaf);
            proof.update(challenge);
            if (!proof.verify(signature)) throw new GeneralSecurityException("Key/certificate mismatch");
            return new Inspected(alias,leaf,Arrays.stream(store.getCertificateChain(alias))
                    .map(certificate -> (X509Certificate)certificate).toList());
        } catch (Exception e) {
            throw new SigningCertificateException("PKCS#12 inspection failed (key, password or validity)",e);
        } finally {
            Arrays.fill(content,(byte)0);
            Arrays.fill(password,'\0');
        }
    }

    public static Validated validate(SigningKeyMaterial material, Instant time,
                                     Collection<X509Certificate> trustedAuthorities) {
        return validate(material,time,trustedAuthorities,null);
    }

    public static Validated validate(SigningKeyMaterial material, Instant time,
                                     Collection<X509Certificate> trustedAuthorities, Collection<X509CRL> crls) {
        Inspected inspected = inspect(material,time);
        try {
            Set<TrustAnchor> anchors = new HashSet<>();
            for (X509Certificate authority : trustedAuthorities) {
                authority.checkValidity(Date.from(time));
                if (authority.getBasicConstraints() < 0)
                    throw new GeneralSecurityException("Trust anchor must be a CA");
                anchors.add(new TrustAnchor(authority, null));
            }
            X509CertSelector selector = new X509CertSelector();
            selector.setCertificate(inspected.certificate());
            PKIXBuilderParameters parameters = new PKIXBuilderParameters(anchors, selector);
            parameters.setDate(Date.from(time));
            parameters.setRevocationEnabled(crls != null);
            if (crls != null) {
                if (crls.isEmpty()) throw new GeneralSecurityException("Current CRLs required");
                for (X509CRL crl : crls) {
                    if (crl.getNextUpdate()==null || crl.getThisUpdate().toInstant().isAfter(time)
                            || !crl.getNextUpdate().toInstant().isAfter(time))
                        throw new GeneralSecurityException("CRL is not current at signing time");
                }
                PKIXRevocationChecker checker=(PKIXRevocationChecker)CertPathBuilder.getInstance("PKIX").getRevocationChecker();
                checker.setOptions(Set.of(PKIXRevocationChecker.Option.PREFER_CRLS,PKIXRevocationChecker.Option.NO_FALLBACK));
                parameters.addCertPathChecker(checker);
                parameters.addCertStore(CertStore.getInstance("Collection",new CollectionCertStoreParameters(crls)));
            }
            parameters.addCertStore(CertStore.getInstance("Collection", new CollectionCertStoreParameters(inspected.chain())));
            PKIXCertPathBuilderResult result = (PKIXCertPathBuilderResult)
                    CertPathBuilder.getInstance("PKIX").build(parameters);
            return new Validated(inspected.alias(), inspected.certificate(), result.getTrustAnchor().getTrustedCert());
        } catch (Exception e) {
            throw new SigningCertificateException("PKCS#12 validation failed (key, validity or trusted chain)", e);
        }
    }

    /** Direct holder profile: certified RUC and identity, never a comparison of names. */
    public static void requireCertifiedRuc(X509Certificate certificate, String ruc) {
        var subject=org.bouncycastle.asn1.x500.X500Name.getInstance(certificate.getSubjectX500Principal().getEncoded());
        String certifiedRuc=subjectAttribute(subject,"2.5.4.97");
        if (!ruc.equals(certifiedRuc)) throw new SigningCertificateException("Certificate does not certify intended RUC");
        if (ruc.charAt(2)<'6' && !ruc.substring(0,10).equals(subjectAttribute(subject,"2.5.4.5")))
            throw new SigningCertificateException("Certified holder identity differs from natural-person RUC");
    }

    private static String subjectAttribute(org.bouncycastle.asn1.x500.X500Name subject,String oid) {
        var rdns=subject.getRDNs(new org.bouncycastle.asn1.ASN1ObjectIdentifier(oid));
        if (rdns.length!=1 || rdns[0].isMultiValued()
                || !(rdns[0].getFirst().getValue() instanceof org.bouncycastle.asn1.ASN1String text))
            throw new SigningCertificateException("Certified identity field missing or ambiguous");
        return text.getString();
    }

    public static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
    }
}
