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
                "spring.rabbitmq.publisher-confirm-type=correlated"
        }
)
@AutoConfigureMockMvc
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