package io.github.imecuadorian.emitta.invoice.adapter.in.web;

import io.github.imecuadorian.emitta.document.domain.Document;
import io.github.imecuadorian.emitta.document.domain.DocumentStatus;
import io.github.imecuadorian.emitta.invoice.application.command.CreateInvoiceCommand;
import io.github.imecuadorian.emitta.invoice.application.model.CreateInvoiceResult;
import io.github.imecuadorian.emitta.invoice.application.port.in.CreateInvoiceUseCase;
import io.github.imecuadorian.emitta.invoice.domain.Invoice;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers =
                InvoiceController.class
)
@AutoConfigureMockMvc(
        addFilters = false
)
class InvoiceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateInvoiceUseCase
            createInvoiceUseCase;

    @Test
    void shouldCreateInvoiceUsingTenantFromJwt()
            throws Exception {

        UUID tenantId =
                UUID.randomUUID();

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

        Jwt jwt =
                Jwt.withTokenValue(
                                "test-token"
                        )
                        .header(
                                "alg",
                                "RS256"
                        )
                        .claim(
                                "tenant_id",
                                tenantId.toString()
                        )
                        .subject(
                                "test-client"
                        )
                        .issuedAt(
                                Instant.now()
                        )
                        .expiresAt(
                                Instant.now()
                                        .plusSeconds(
                                                600
                                        )
                        )
                        .build();

        JwtAuthenticationToken authentication =
                new JwtAuthenticationToken(
                        jwt
                );

        mockMvc.perform(
                        post(
                                "/api/v1/invoices"
                        )
                                .principal(
                                        authentication
                                )
                                .header(
                                        "Idempotency-Key",
                                        "sale-001"
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        """
                                        {
                                          "pointOfIssueId":
                                            "00000000-0000-0000-0000-000000000001",
                                          "environment": "TEST",
                                          "issuedAt":
                                            "2026-10-05T20:00:00Z",
                                          "buyer": {
                                            "identificationType": "07",
                                            "identification":
                                              "9999999999999",
                                            "name":
                                              "CONSUMIDOR FINAL"
                                          },
                                          "items": [
                                            {
                                              "sku": "P001",
                                              "description":
                                                "Producto de prueba",
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
                                        """
                                )
                )
                .andExpect(
                        status()
                                .isAccepted()
                )
                .andExpect(
                        content()
                                .contentTypeCompatibleWith(
                                        MediaType.APPLICATION_JSON
                                )
                )
                .andExpect(
                        jsonPath(
                                "$.id"
                        ).value(
                                documentId.toString()
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.status"
                        ).value(
                                "QUEUED"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.total"
                        ).value(
                                20.70
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.created"
                        ).value(
                                true
                        )
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

        assertEquals(
                tenantId,
                captor.getValue()
                        .tenantId()
        );

        assertEquals(
                "sale-001",
                captor.getValue()
                        .idempotencyKey()
        );
    }

    @Test
    void shouldReturnOkForIdempotentReplay()
            throws Exception {

        UUID tenantId =
                UUID.randomUUID();

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
                DocumentStatus.GENERATING
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
                        false
                )
        );

        Jwt jwt =
                Jwt.withTokenValue(
                                "test-token"
                        )
                        .header(
                                "alg",
                                "RS256"
                        )
                        .claim(
                                "tenant_id",
                                tenantId.toString()
                        )
                        .build();

        mockMvc.perform(
                        post(
                                "/api/v1/invoices"
                        )
                                .principal(
                                        new JwtAuthenticationToken(
                                                jwt
                                        )
                                )
                                .header(
                                        "Idempotency-Key",
                                        "sale-replay-001"
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        validJson()
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath(
                                "$.status"
                        ).value(
                                "GENERATING"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.created"
                        ).value(
                                false
                        )
                );
    }

    private static String validJson() {

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
                  "description": "Producto de prueba",
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