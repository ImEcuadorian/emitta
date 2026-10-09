package io.github.imecuadorian.emitta.fiscalsigning.adapter.out.crypto;

import io.github.imecuadorian.emitta.fiscalsigning.application.exception.SigningCertificateException;
import io.github.imecuadorian.emitta.fiscalsigning.application.model.SigningKeyMaterial;
import io.github.imecuadorian.emitta.support.TestSigningCertificates;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Pkcs12CertificateValidatorTest {
    private static final TestSigningCertificates.Fixture FIXTURE = TestSigningCertificates.create("test-password");
    private static final Instant TIME = Instant.parse("2026-10-08T12:00:00Z");

    @Test void acceptsActualKeyAndTrustedChain() {
        try (var material = new SigningKeyMaterial(FIXTURE.pkcs12(),"test-password".toCharArray(),null)) {
            var result = Pkcs12CertificateValidator.validate(material,TIME,List.of(FIXTURE.ca()));
            assertEquals(FIXTURE.leaf(),result.certificate());
            assertEquals(FIXTURE.ca(),result.trustAnchor());
            assertEquals("emitta-test",result.alias());
        }
    }

    @Test void rejectsWrongPasswordAndUntrustedChain() {
        try (var wrongPassword = new SigningKeyMaterial(FIXTURE.pkcs12(),"wrong".toCharArray(),null);
             var material = new SigningKeyMaterial(FIXTURE.pkcs12(),"test-password".toCharArray(),null)) {
            assertThrows(SigningCertificateException.class,()->Pkcs12CertificateValidator.validate(wrongPassword,TIME,List.of(FIXTURE.ca())));
            assertThrows(SigningCertificateException.class,()->Pkcs12CertificateValidator.validate(material,TIME,List.of()));
        }
    }

    @Test void rejectsActualExpiredCertificateAndMissingAlias() {
        try (var material = new SigningKeyMaterial(FIXTURE.pkcs12(),"test-password".toCharArray(),null);
             var missingAlias = new SigningKeyMaterial(FIXTURE.pkcs12(),"test-password".toCharArray(),"missing")) {
            assertThrows(SigningCertificateException.class,()->Pkcs12CertificateValidator.validate(material,Instant.parse("2040-01-01T00:00:00Z"),List.of(FIXTURE.ca())));
            assertThrows(SigningCertificateException.class,()->Pkcs12CertificateValidator.validate(missingAlias,TIME,List.of(FIXTURE.ca())));
        }
    }

    @Test void requiresCertifiedRucAndRejectsMismatchRegardlessOfName() {
        assertDoesNotThrow(()->Pkcs12CertificateValidator.requireCertifiedRuc(FIXTURE.leaf(),"1790012345001"));
        assertThrows(SigningCertificateException.class,()->Pkcs12CertificateValidator.requireCertifiedRuc(FIXTURE.leaf(),"1790012346001"));
    }

    @Test void validatesCurrentCrlAndRejectsMissingExpiredAndRevokedEvidence() {
        try (var material=new SigningKeyMaterial(FIXTURE.pkcs12(),"test-password".toCharArray(),null)) {
            assertDoesNotThrow(()->Pkcs12CertificateValidator.validate(material,Instant.now(),List.of(FIXTURE.ca()),FIXTURE.crls()));
            assertThrows(SigningCertificateException.class,()->Pkcs12CertificateValidator.validate(material,Instant.now(),List.of(FIXTURE.ca()),List.of()));
            assertThrows(SigningCertificateException.class,()->Pkcs12CertificateValidator.validate(material,
                    FIXTURE.crls().getFirst().getNextUpdate().toInstant().plusSeconds(1),List.of(FIXTURE.ca()),FIXTURE.crls()));
        }
        var revoked=TestSigningCertificates.create("test-password",true);
        try (var material=new SigningKeyMaterial(revoked.pkcs12(),"test-password".toCharArray(),null)) {
            assertThrows(SigningCertificateException.class,()->Pkcs12CertificateValidator.validate(material,Instant.now(),List.of(revoked.ca()),revoked.crls()));
        }
    }
}
