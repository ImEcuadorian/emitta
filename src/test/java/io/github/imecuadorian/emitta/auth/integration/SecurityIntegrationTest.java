package io.github.imecuadorian.emitta.auth.integration;

import io.github.imecuadorian.emitta.document.domain.Document;
import io.github.imecuadorian.emitta.document.domain.DocumentStatus;
import io.github.imecuadorian.emitta.invoice.application.command.CreateInvoiceCommand;
import io.github.imecuadorian.emitta.invoice.application.model.CreateInvoiceResult;
import io.github.imecuadorian.emitta.invoice.application.port.in.CreateInvoiceUseCase;
import io.github.imecuadorian.emitta.invoice.domain.Invoice;
import io.github.imecuadorian.emitta.support.JwtTestProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(
        properties = {
                "emitta.outbox.publisher.enabled=false",
                "emitta.fiscal.worker.enabled=false",
                "emitta.security.jwt.issuer=emitta-test",
                "emitta.security.jwt.audience=emitta-api",
                "emitta.security.jwt.ttl=PT15M",
                "emitta.security.allowed-web-origins=https://emitta.nexorf.dev",
                "spring.rabbitmq.publisher-confirm-type=correlated"
        }
)
@AutoConfigureMockMvc
@org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics(export=true)
@Testcontainers
class SecurityIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(
                    "postgres:18"
            )
                    .withDatabaseName(
                            "emitta_db"
                    )
                    .withUsername(
                            "emitta_test"
                    )
                    .withPassword(
                            "emitta_test"
                    );

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBITMQ =
            new RabbitMQContainer(
                    "rabbitmq:4-management-alpine"
            );

    @DynamicPropertySource
    static void jwtProperties(
            DynamicPropertyRegistry registry
    ) {
        JwtTestProperties.register(
                registry
        );
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private CreateInvoiceUseCase
            createInvoiceUseCase;

    @MockitoBean private io.github.imecuadorian.emitta.document.application.port.out.DocumentRepository queryDocuments;
    @MockitoBean private io.github.imecuadorian.emitta.documentartifact.application.port.in.LoadDocumentArtifactUseCase queryArtifacts;

    private UUID tenantId;

    @BeforeEach
    void setUp() {

        tenantId =
                UUID.randomUUID();

        jdbcTemplate.update(
                """
                INSERT INTO emitta.tenants (
                    id,
                    name,
                    status
                )
                VALUES (?, ?, ?)
                """,
                tenantId,
                "Security Test Tenant",
                "ACTIVE"
        );
    }

    @Test
    void customerCredentialsAreOneTimeScopedRotatableAndRevocable() throws Exception {
        var platform=org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_platform:admin"));
        String path="/api/v1/tenants/"+tenantId+"/api-clients";
        mockMvc.perform(post(path).with(platform).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Synthetic customer\",\"scopes\":[\"platform:admin\"]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(path).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Synthetic customer\",\"scopes\":[\"invoices:write\"]}"))
                .andExpect(status().isForbidden());
        var result=mockMvc.perform(post(path).with(platform).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Synthetic customer\",\"scopes\":[\"invoices:write\"]}"))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control","no-store"))
                .andReturn().getResponse().getContentAsString();
        var credentials=jsonMapper.readTree(result);
        String id=credentials.get("id").asText(),client=credentials.get("clientId").asText(),secret=credentials.get("clientSecret").asText();
        String hash=jdbcTemplate.queryForObject("SELECT client_secret_hash FROM emitta.api_clients WHERE client_id=?",String.class,client);
        org.junit.jupiter.api.Assertions.assertNotEquals(secret,hash);
        org.junit.jupiter.api.Assertions.assertTrue(passwordEncoder.matches(secret,hash));
        obtainToken(client,secret);
        mockMvc.perform(post("/api/v1/tenants/{tenant}/api-clients/{id}/rotate-secret",UUID.randomUUID(),id).with(platform))
                .andExpect(status().isNotFound());
        var rotated=jsonMapper.readTree(mockMvc.perform(post(path+"/"+id+"/rotate-secret").with(platform))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(java.util.Map.of("clientId",client,"clientSecret",secret))))
                .andExpect(status().isUnauthorized());
        String issuedBeforeRevoke=obtainToken(client,rotated.get("clientSecret").asText());
        mockMvc.perform(post(path+"/"+id+"/revoke").with(platform)).andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertEquals(
                java.util.List.of("CLIENT_CREATED", "CLIENT_REVOKED", "CLIENT_SECRET_ROTATED"),
                jdbcTemplate.queryForList("SELECT action FROM emitta.administrative_audit_events WHERE resource_id=? AND tenant_id=? ORDER BY action",
                        String.class, UUID.fromString(id), tenantId));
        org.junit.jupiter.api.Assertions.assertEquals(0,
                jdbcTemplate.queryForObject("SELECT count(*) FROM emitta.administrative_audit_events WHERE resource_id=? AND tenant_id<>?",
                        Integer.class, UUID.fromString(id), tenantId));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/documents/{id}",UUID.randomUUID())
                .header("Authorization","Bearer "+issuedBeforeRevoke)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(java.util.Map.of("clientId",client,"clientSecret",rotated.get("clientSecret").asText()))))
                .andExpect(status().isUnauthorized());
    }
    @Test
    void documentAndArtifactReadsAreScopedAndDoNotExposeOtherTenants() throws Exception {
        createApiClient("read-test","synthetic-secret","documents:read");
        jdbcTemplate.update("INSERT INTO emitta.api_client_scopes(api_client_id,scope) SELECT id,? FROM emitta.api_clients WHERE client_id=?", "artifacts:read","read-test");
        String token=obtainToken("read-test","synthetic-secret");
        UUID id=UUID.randomUUID();
        var document=mock(Document.class);
        when(queryDocuments.findById(id)).thenReturn(java.util.Optional.of(document));
        when(document.getTenantId()).thenReturn(UUID.randomUUID());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/documents/"+id)
                .header("Authorization","Bearer "+token)).andExpect(status().isNotFound());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/documents/"+id+"/artifacts/ride-pdf")
                .header("Authorization","Bearer "+token)).andExpect(status().isNotFound());
        verifyNoInteractions(queryArtifacts);
        when(document.getTenantId()).thenReturn(tenantId);
        when(document.getId()).thenReturn(id);
        when(document.getStatus()).thenReturn(DocumentStatus.AUTHORIZED);
        when(document.getEnvironment()).thenReturn(io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment.TEST);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/documents/"+id)
                .header("Authorization","Bearer "+token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHORIZED")).andExpect(jsonPath("$.storageKey").doesNotExist());
        byte[] pdf = "%PDF-synthetic-fixture".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String digest = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(pdf));
        var artifact = new io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifact(UUID.randomUUID(), id,
                io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifactType.RIDE_PDF,
                "application/pdf", "private-key-never-exposed", digest, pdf.length, java.time.Instant.now());
        when(queryArtifacts.load(id, artifact.type())).thenReturn(
                new io.github.imecuadorian.emitta.documentartifact.application.model.LoadedDocumentArtifact(artifact, pdf));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/documents/"+id+"/artifacts/ride-pdf")
                .header("Authorization","Bearer "+token)).andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf")).andExpect(content().bytes(pdf))
                .andExpect(header().string("Cache-Control","no-store"))
                .andExpect(header().string("ETag", "\"" + digest + "\""))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"" + id + ".pdf\""));
        createApiClient("write-no-read","synthetic-secret","invoices:write");
        String writer=obtainToken("write-no-read","synthetic-secret");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/documents/"+id)
                .header("Authorization","Bearer "+writer)).andExpect(status().isForbidden());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/documents/"+id+"/artifacts/ride-pdf"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldIssueJwtAndAuthorizeInvoiceCreation()
            throws Exception {

        createApiClient(
                "faxorf-test",
                "super-secret",
                "invoices:write"
        );

        String token =
                obtainToken(
                        "faxorf-test",
                        "super-secret"
                );

        UUID documentId =
                UUID.randomUUID();

        Document document =
                mock(
                        Document.class
                );

        Invoice invoice =
                mock(
                        Invoice.class
                );

        when(
                document.getId()
        ).thenReturn(
                documentId
        );

        when(
                document.getStatus()
        ).thenReturn(
                DocumentStatus.QUEUED
        );

        when(
                invoice.getTotal()
        ).thenReturn(
                new BigDecimal(
                        "20.70"
                )
        );

        when(
                createInvoiceUseCase.create(
                        any()
                )
        ).thenReturn(
                new CreateInvoiceResult(
                        document,
                        invoice,
                        true
                )
        );

        mockMvc.perform(
                        post(
                                "/api/v1/invoices"
                        )
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                                .header(
                                        "Idempotency-Key",
                                        "security-test-001"
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        validInvoiceJson()
                                )
                )
                .andExpect(
                        status().isAccepted()
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("QUEUED")
                );

        ArgumentCaptor<CreateInvoiceCommand> captor =
                ArgumentCaptor.forClass(
                        CreateInvoiceCommand.class
                );

        verify(
                createInvoiceUseCase
        ).create(
                captor.capture()
        );

        /*
         * Critical multitenancy assertion:
         * tenant came from the signed JWT.
         */
        assertEquals(
                tenantId,
                captor.getValue()
                        .tenantId()
        );
    }

    @Test
    void shouldRejectInvoiceWithoutAccessToken()
            throws Exception {

        mockMvc.perform(
                        post(
                                "/api/v1/invoices"
                        )
                                .header(
                                        "Idempotency-Key",
                                        "security-test-no-token"
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        validInvoiceJson()
                                )
                )
                .andExpect(
                        status().isUnauthorized()
                )
                .andExpect(
                        content()
                                .contentTypeCompatibleWith(
                                        MediaType.APPLICATION_PROBLEM_JSON
                                )
                )
                .andExpect(
                        jsonPath("$.code")
                                .value("UNAUTHORIZED")
                );

        verifyNoInteractions(
                createInvoiceUseCase
        );
    }

    @Test
    void shouldRejectClientWithoutInvoiceWriteScope()
            throws Exception {

        createApiClient(
                "readonly-client",
                "readonly-secret",
                "invoices:read"
        );

        String token =
                obtainToken(
                        "readonly-client",
                        "readonly-secret"
                );

        mockMvc.perform(
                        post(
                                "/api/v1/invoices"
                        )
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                                .header(
                                        "Idempotency-Key",
                                        "security-test-forbidden"
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        validInvoiceJson()
                                )
                )
                .andExpect(
                        status().isForbidden()
                )
                .andExpect(
                        jsonPath("$.code")
                                .value("FORBIDDEN")
                );

        verifyNoInteractions(
                createInvoiceUseCase
        );
    }

    @Test
    void shouldRejectInvalidClientSecret()
            throws Exception {

        createApiClient(
                "faxorf-invalid",
                "correct-secret",
                "invoices:write"
        );

        mockMvc.perform(
                        post(
                                "/api/v1/auth/token"
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        """
                                        {
                                          "clientId": "faxorf-invalid",
                                          "clientSecret": "wrong-secret"
                                        }
                                        """
                                )
                )
                .andExpect(
                        status().isUnauthorized()
                )
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "INVALID_CLIENT_CREDENTIALS"
                                )
                );
    }

    private String obtainToken(
            String clientId,
            String secret
    ) throws Exception {

        String body =
                mockMvc.perform(
                                post(
                                        "/api/v1/auth/token"
                                )
                                        .contentType(
                                                MediaType.APPLICATION_JSON
                                        )
                                        .content(
                                                """
                                                {
                                                  "clientId": "%s",
                                                  "clientSecret": "%s"
                                                }
                                                """.formatted(
                                                        clientId,
                                                        secret
                                                )
                                        )
                        )
                        .andExpect(
                                status().isOk()
                        )
                        .andExpect(
                                header().string(
                                        "Cache-Control",
                                        "no-store"
                                )
                        )
                        .andExpect(
                                jsonPath("$.tokenType")
                                        .value("Bearer")
                        )
                        .andExpect(
                                jsonPath("$.expiresIn")
                                        .value(900)
                        )
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        JsonNode json =
                jsonMapper.readTree(
                        body
                );

        return json
                .get("accessToken")
                .asText();
    }

    private void createApiClient(
            String clientId,
            String rawSecret,
            String scope
    ) {

        UUID clientPk =
                UUID.randomUUID();

        jdbcTemplate.update(
                """
                INSERT INTO emitta.api_clients (
                    id,
                    tenant_id,
                    client_id,
                    client_secret_hash,
                    name,
                    status
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                clientPk,
                tenantId,
                clientId,
                passwordEncoder.encode(
                        rawSecret
                ),
                clientId,
                "ACTIVE"
        );

        jdbcTemplate.update(
                """
                INSERT INTO emitta.api_client_scopes (
                    api_client_id,
                    scope
                )
                VALUES (?, ?)
                """,
                clientPk,
                scope
        );
    }

    @Test
    void shouldRequireAdministrativeScopesAndIsolateAllParentResources() throws Exception {
        createApiClient("admin-test-" + tenantId, "synthetic-secret", "taxpayers:write");
        UUID client = jdbcTemplate.queryForObject("SELECT id FROM emitta.api_clients WHERE tenant_id=?", UUID.class, tenantId);
        for (String scope : java.util.List.of("establishments:write", "points-of-issue:write", "certificates:write", "certificates:read"))
            jdbcTemplate.update("INSERT INTO emitta.api_client_scopes(api_client_id,scope) VALUES (?,?)", client, scope);
        String token = obtainToken("admin-test-" + tenantId, "synthetic-secret");
        String taxpayerBody = "{\"ruc\":\"1790012345001\",\"legalName\":\"SYNTHETIC ISSUER\",\"mainAddress\":\"Synthetic address\"}";
        String ownTaxpayer = mockMvc.perform(post("/api/v1/tenants/{tenant}/taxpayers", tenantId)
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(taxpayerBody))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID taxpayer = UUID.fromString(jsonMapper.readTree(ownTaxpayer).get("id").asText());
        String ownEstablishment = mockMvc.perform(post("/api/v1/taxpayers/{id}/establishments", taxpayer)
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"001\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID establishment = UUID.fromString(jsonMapper.readTree(ownEstablishment).get("id").asText());
        mockMvc.perform(post("/api/v1/establishments/{id}/points-of-issue", establishment)
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"001\"}"))
                .andExpect(status().isCreated());
        UUID otherTenant = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO emitta.tenants(id,name,status) VALUES (?,'Other synthetic tenant','ACTIVE')", otherTenant);
        UUID originalTenant = tenantId;
        tenantId = otherTenant;
        createApiClient("other-test-" + otherTenant, "synthetic-secret", "taxpayers:write");
        UUID otherClient = jdbcTemplate.queryForObject("SELECT id FROM emitta.api_clients WHERE tenant_id=?", UUID.class, otherTenant);
        for (String scope : java.util.List.of("establishments:write", "points-of-issue:write", "certificates:write", "certificates:read"))
            jdbcTemplate.update("INSERT INTO emitta.api_client_scopes(api_client_id,scope) VALUES (?,?)", otherClient, scope);
        String otherToken = obtainToken("other-test-" + otherTenant, "synthetic-secret");
        for (String path : java.util.List.of("/api/v1/tenants/"+originalTenant+"/taxpayers",
                "/api/v1/taxpayers/"+taxpayer+"/establishments", "/api/v1/taxpayers/"+taxpayer+"/certificates", "/api/v1/establishments/"+establishment+"/points-of-issue")) {
            mockMvc.perform(post(path).header("Authorization", "Bearer " + otherToken)
                    .contentType(MediaType.APPLICATION_JSON).content(path.endsWith("taxpayers") ? taxpayerBody : "{\"code\":\"002\"}"))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/taxpayers/{id}/certificates",taxpayer)
                .header("Authorization","Bearer "+otherToken)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/tenants").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Unauthorized tenant\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/tenants").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_platform:admin")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Platform-managed synthetic tenant\"}"))
                .andExpect(status().isCreated());
        createApiClient("limited-test-"+otherTenant, "synthetic-secret", "invoices:write");
        String limited = obtainToken("limited-test-"+otherTenant, "synthetic-secret");
        mockMvc.perform(post("/api/v1/tenants/{tenant}/taxpayers", otherTenant)
                .header("Authorization", "Bearer " + limited).contentType(MediaType.APPLICATION_JSON).content(taxpayerBody))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectAuthenticationForInactiveTenant() throws Exception {
        createApiClient("inactive-test-"+tenantId, "synthetic-secret", "invoices:write");
        String token = obtainToken("inactive-test-"+tenantId, "synthetic-secret");
        mockMvc.perform(post("/api/v1/invoices").header("Authorization", "Bearer "+token)
                .header("Idempotency-Key", "unknown-field-test").contentType(MediaType.APPLICATION_JSON)
                .content(validInvoiceJson().replaceFirst("\\{", "{\"tenantId\":\""+tenantId+"\",")))
                .andExpect(status().isBadRequest());
        jdbcTemplate.update("UPDATE emitta.tenants SET status='DISABLED' WHERE id=?", tenantId);
        mockMvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"inactive-test-"+tenantId+"\",\"clientSecret\":\"synthetic-secret\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/invoices").header("Authorization", "Bearer "+token)
                .header("Idempotency-Key", "inactive-test").contentType(MediaType.APPLICATION_JSON).content(validInvoiceJson()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(createInvoiceUseCase);
    }

    @Test
    void prometheusRequiresOperationsScopeAndNeverExposesCredentials() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/actuator/prometheus")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()))
                .andExpect(status().isForbidden());
        String text=mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/actuator/prometheus")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_operations:read"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(text.contains("jvm_memory"));
        org.junit.jupiter.api.Assertions.assertFalse(text.contains("client_secret"));
        org.junit.jupiter.api.Assertions.assertFalse(text.contains("Authorization:"));
    }

    @Test void administrativeListsRequirePlatformScopeAndNeverReturnSecrets() throws Exception {
        createApiClient("metadata-test", "synthetic-secret", "documents:read");
        String token=obtainToken("metadata-test","synthetic-secret");
        for(String path:java.util.List.of("/api/v1/tenants","/api/v1/tenants/"+tenantId+"/api-clients")) {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)).andExpect(status().isUnauthorized());
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path).header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
            String body=mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)
                    .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                    .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_platform:admin"))))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsString();
            org.junit.jupiter.api.Assertions.assertFalse(body.contains("synthetic-secret"));
            org.junit.jupiter.api.Assertions.assertFalse(body.contains("hash"));
        }
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/tenants?limit=101")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_platform:admin"))))
                .andExpect(status().isBadRequest());
    }

    @Test void corsAllowsOnlyConfiguredOriginAndNeverCredentialCookies() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/api/v1/tenants")
                .header("Origin","https://emitta.nexorf.dev").header("Access-Control-Request-Method","GET")
                .header("Access-Control-Request-Headers","authorization"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin","https://emitta.nexorf.dev"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/api/v1/tenants")
                .header("Origin","https://untrusted.example.invalid").header("Access-Control-Request-Method","GET"))
                .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    private static String validInvoiceJson() {

        return """
                {
                  "pointOfIssueId":
                    "00000000-0000-0000-0000-000000000001",
                  "environment": "TEST",
                  "issuedAt": "2026-10-05T20:00:00Z",
                  "buyer": {
                    "identificationType": "07",
                    "identification": "9999999999999",
                    "name": "CONSUMIDOR FINAL"
                  },
                  "items": [
                    {
                      "sku": "P001",
                      "description": "Security test product",
                      "quantity": 2,
                      "unitPrice": 10.00,
                      "discount": 2.00,
                      "taxes": [
                        {
                          "taxCode": "2",
                          "percentageCode": "4",
                          "rate": 15
                        }
                      ]
                    }
                  ],
                  "payments": [
                    {
                      "paymentMethod": "01",
                      "total": 20.70
                    }
                  ],
                  "expectedTotal": 20.70
                }
                """;
    }


}
