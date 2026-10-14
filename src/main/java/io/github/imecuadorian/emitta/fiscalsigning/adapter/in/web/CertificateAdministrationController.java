package io.github.imecuadorian.emitta.fiscalsigning.adapter.in.web;

import io.github.imecuadorian.emitta.fiscalsigning.adapter.out.crypto.Pkcs12CertificateValidator;
import io.github.imecuadorian.emitta.fiscalsigning.adapter.out.persistence.PostgreSqlSigningKeyMaterialAdapter;
import io.github.imecuadorian.emitta.fiscalsigning.application.model.SigningKeyMaterial;
import io.github.imecuadorian.emitta.fiscalsigning.application.exception.SigningCertificateException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.*;
import java.security.cert.*;
import java.time.Clock;
import java.util.*;

/** Server trust configuration is never supplied by the certificate uploader. */
@RestController
@RequestMapping("/api/v1/taxpayers/{taxpayerId}/certificates")
@ConditionalOnProperty(name="emitta.certificates.administration.enabled",havingValue="true")
public final class CertificateAdministrationController {
    private final PostgreSqlSigningKeyMaterialAdapter keys;
    private final JdbcClient jdbc;
    private final Clock clock;
    private final String authorities,revocations;
    public CertificateAdministrationController(PostgreSqlSigningKeyMaterialAdapter keys,JdbcClient jdbc,Clock clock,
            @Value("${emitta.certificates.administration.trusted-ca-file:}") String authorities,
            @Value("${emitta.certificates.administration.crl-file:}") String revocations) {
        this.keys=keys;this.jdbc=jdbc;this.clock=clock;this.authorities=authorities;this.revocations=revocations;
    }
    public record RegisteredCertificate(UUID id,String fingerprint,String validUntil) {}
    public record CertificateMetadata(UUID id,String fingerprint,String status,String validUntil) {}
    @GetMapping
    public ResponseEntity<List<CertificateMetadata>> metadata(@PathVariable UUID taxpayerId,JwtAuthenticationToken authentication) {
        UUID tenant=targetTenant(authentication,taxpayerId);
        var result=jdbc.sql("SELECT c.id,c.fingerprint,c.status,c.valid_until FROM emitta.certificates c JOIN emitta.taxpayers t ON t.id=c.taxpayer_id WHERE t.id=:id AND t.tenant_id=:tenant ORDER BY c.valid_until DESC")
                .param("id",taxpayerId).param("tenant",tenant)
                .query((rs,row)->new CertificateMetadata(rs.getObject("id",UUID.class),rs.getString("fingerprint"),
                        rs.getString("status"),rs.getTimestamp("valid_until").toInstant().toString())).list();
        if(authentication.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("SCOPE_platform:admin")))
            io.github.imecuadorian.emitta.auth.adapter.out.persistence.AdministrationAuditRecorder.record(jdbc,tenant,taxpayerId,"CERTIFICATES_READ");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RegisteredCertificate> register(@PathVariable UUID taxpayerId,
            JwtAuthenticationToken authentication,@RequestParam MultipartFile certificate,
            @RequestParam String password,@RequestParam(required=false) String alias,
            @RequestParam MultipartFile authorizationEvidence,@RequestParam boolean authorizationReviewed) {
        if(!authorizationReviewed || certificate.isEmpty() || certificate.getSize()>1048576
                || authorizationEvidence.isEmpty() || authorizationEvidence.getSize()>262144
                || password.length()>1024 || (alias!=null && alias.length()>255))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid certificate onboarding request");
        UUID tenant=targetTenant(authentication,taxpayerId);
        String ruc=jdbc.sql("SELECT ruc FROM emitta.taxpayers WHERE id=:id AND tenant_id=:tenant AND status='ACTIVE' AND test_enabled=true")
                .param("id",taxpayerId).param("tenant",tenant).query(String.class).optional()
                .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Active TEST taxpayer not found"));
        if(authorities.isBlank() || revocations.isBlank()) throw unavailable();
        Collection<X509Certificate> cas; Collection<X509CRL> crls;
        try {
            CertificateFactory factory=CertificateFactory.getInstance("X.509");
            try(var input=Files.newInputStream(Path.of(authorities))) {
                cas=factory.generateCertificates(input).stream().map(c->(X509Certificate)c).toList();
            }
            try(var input=Files.newInputStream(Path.of(revocations))) {
                crls=factory.generateCRLs(input).stream().map(c->(X509CRL)c).toList();
            }
            if(cas.isEmpty() || crls.isEmpty()) throw unavailable();
        } catch(ResponseStatusException e) { throw e; }
        catch(Exception e) { throw unavailable(); }
        byte[] bytes=null,evidence=null; char[] chars=password.toCharArray();
        try {
            bytes=certificate.getBytes(); evidence=authorizationEvidence.getBytes();
            try(var material=new SigningKeyMaterial(bytes,chars,alias)) {
                var validated=Pkcs12CertificateValidator.validate(material,clock.instant(),cas,crls);
                Pkcs12CertificateValidator.requireCertifiedRuc(validated.certificate(),ruc);
                UUID id=keys.registerReviewedCertificate(tenant,taxpayerId,ruc,material,validated,
                        Pkcs12CertificateValidator.sha256(evidence),clock.instant());
                return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                        .body(new RegisteredCertificate(id,Pkcs12CertificateValidator.sha256(validated.certificate().getEncoded()),
                                validated.certificate().getNotAfter().toInstant().toString()));
            }
        } catch(SigningCertificateException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Certificate validity, trust or holder authorization could not be established");
        } catch(java.io.IOException | CertificateEncodingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid certificate upload");
        } finally {
            if(bytes!=null) Arrays.fill(bytes,(byte)0);
            if(evidence!=null) Arrays.fill(evidence,(byte)0);
            Arrays.fill(chars,'\0');
        }
    }
    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Trusted CA and current CRL configuration required");
    }
    private UUID targetTenant(JwtAuthenticationToken authentication,UUID taxpayer) {
        if(authentication.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("SCOPE_platform:admin")))
            return jdbc.sql("SELECT t.tenant_id FROM emitta.taxpayers t JOIN emitta.tenants n ON n.id=t.tenant_id WHERE t.id=:id AND n.status='ACTIVE'")
                    .param("id",taxpayer).query(UUID.class).optional().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Taxpayer unavailable"));
        return UUID.fromString(authentication.getToken().getClaimAsString("tenant_id"));
    }
}
