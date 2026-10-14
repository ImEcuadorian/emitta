package io.github.imecuadorian.emitta.fiscalsigning.adapter.in.web;

import io.github.imecuadorian.emitta.fiscalsigning.adapter.out.persistence.PostgreSqlSigningKeyMaterialAdapter;
import io.github.imecuadorian.emitta.support.TestSigningCertificates;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CertificateAdministrationControllerTest {
    private static final TestSigningCertificates.Fixture FIXTURE=TestSigningCertificates.create("synthetic-password");
    @TempDir Path directory;
    private final UUID tenant=UUID.randomUUID(),taxpayer=UUID.randomUUID();
    private final PostgreSqlSigningKeyMaterialAdapter keys=mock(PostgreSqlSigningKeyMaterialAdapter.class);
    private final JdbcClient jdbc=mock(JdbcClient.class,RETURNS_DEEP_STUBS);
    private CertificateAdministrationController controller;
    @BeforeEach void configure() throws Exception {
        Path ca=directory.resolve("test-ca.der"),crl=directory.resolve("test-crl.der");
        Files.write(ca,FIXTURE.ca().getEncoded()); Files.write(crl,FIXTURE.crls().getFirst().getEncoded());
        when(jdbc.sql(anyString()).param("id",taxpayer).param("tenant",tenant).query(String.class).optional())
                .thenReturn(Optional.of("1790012345001"));
        controller=new CertificateAdministrationController(keys,jdbc,Clock.systemUTC(),ca.toString(),crl.toString());
    }
    private JwtAuthenticationToken authentication() {
        return new JwtAuthenticationToken(Jwt.withTokenValue("synthetic").header("alg","RS256")
                .claim("tenant_id",tenant.toString()).build());
    }
    private MockMultipartFile certificate() { return new MockMultipartFile("certificate","synthetic.p12","application/octet-stream",FIXTURE.pkcs12()); }
    private MockMultipartFile evidence() { return new MockMultipartFile("authorizationEvidence","review.txt","text/plain","Synthetic administrative review".getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    @Test void validatesRealCryptographyBeforeInvokingExistingEncryptedRegistration() {
        UUID id=UUID.randomUUID();
        when(keys.registerReviewedCertificate(eq(tenant),eq(taxpayer),eq("1790012345001"),any(),any(),anyString(),any())).thenReturn(id);
        var response=controller.register(taxpayer,authentication(),certificate(),"synthetic-password",null,evidence(),true);
        assertEquals(201,response.getStatusCode().value()); assertEquals(id,response.getBody().id());
        assertEquals("no-store",response.getHeaders().getCacheControl());
        verify(keys).registerReviewedCertificate(eq(tenant),eq(taxpayer),eq("1790012345001"),any(),any(),matches("[a-f0-9]{64}"),any());
    }
    @Test void platformOwnerUsesTargetTaxpayerTenantWithoutBorrowingCustomerCredentials() {
        when(jdbc.sql(anyString()).param("id",taxpayer).query(UUID.class).optional()).thenReturn(Optional.of(tenant));
        var owner=new JwtAuthenticationToken(Jwt.withTokenValue("synthetic").header("alg","RS256")
                .claim("tenant_id",UUID.randomUUID().toString()).build(),
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_platform:admin")));
        controller.register(taxpayer,owner,certificate(),"synthetic-password",null,evidence(),true);
        verify(keys).registerReviewedCertificate(eq(tenant),eq(taxpayer),eq("1790012345001"),any(),any(),anyString(),any());
    }
    @Test void rejectsWrongPasswordAndMismatchedRucWithoutPersistence() {
        assertEquals(422,assertThrows(ResponseStatusException.class,()->controller.register(taxpayer,authentication(),certificate(),"wrong",null,evidence(),true)).getStatusCode().value());
        when(jdbc.sql(anyString()).param("id",taxpayer).param("tenant",tenant).query(String.class).optional())
                .thenReturn(Optional.of("1790012346001"));
        assertEquals(422,assertThrows(ResponseStatusException.class,()->controller.register(taxpayer,authentication(),certificate(),"synthetic-password",null,evidence(),true)).getStatusCode().value());
        verifyNoInteractions(keys);
    }
    @Test void failsClosedWithoutServerTrustAndWithoutAdministrativeReview() {
        var unconfigured=new CertificateAdministrationController(keys,jdbc,Clock.systemUTC(),"","");
        assertEquals(503,assertThrows(ResponseStatusException.class,()->unconfigured.register(taxpayer,authentication(),certificate(),"synthetic-password",null,evidence(),true)).getStatusCode().value());
        assertEquals(400,assertThrows(ResponseStatusException.class,()->controller.register(taxpayer,authentication(),certificate(),"synthetic-password",null,evidence(),false)).getStatusCode().value());
        verifyNoInteractions(keys);
    }
    @Test void multipartHttpBindingReturnsSafeProblemDetailsWithoutEchoingPassword() throws Exception {
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new io.github.imecuadorian.emitta.shared.error.AdministrationExceptionHandler()).build();
        String secret="SYNTHETIC_DO_NOT_ECHO";
        var response=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart("/api/v1/taxpayers/{id}/certificates",taxpayer).file(certificate()).file(evidence())
                .principal(authentication()).param("password",secret).param("authorizationReviewed","true"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnprocessableEntity())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentTypeCompatibleWith("application/problem+json"))
                .andReturn().getResponse();
        assertFalse(response.getContentAsString().contains(secret));
        verifyNoInteractions(keys);
    }
}
