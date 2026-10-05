package io.github.imecuadorian.emitta.taxpayer.adapter.in.web;

import io.github.imecuadorian.emitta.shared.error.GlobalExceptionHandler;
import io.github.imecuadorian.emitta.taxpayer.application.exception.TaxpayerAlreadyExistsException;
import io.github.imecuadorian.emitta.taxpayer.application.exception.TenantNotFoundException;
import io.github.imecuadorian.emitta.taxpayer.application.port.in.CreateTaxpayerUseCase;
import io.github.imecuadorian.emitta.taxpayer.domain.Ruc;
import io.github.imecuadorian.emitta.taxpayer.domain.Taxpayer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaxpayerController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class TaxpayerControllerTest {

    private static final UUID TENANT_ID =
            UUID.fromString(
                    "4d34da33-c6e4-4379-8ab0-19fef817cc67"
            );

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateTaxpayerUseCase createTaxpayerUseCase;

    @Test
    void shouldCreateTaxpayer() throws Exception {

        Taxpayer taxpayer =
                Taxpayer.create(
                        UUID.fromString(
                                "5068da12-fc37-4e12-a50e-05adc629d7c7"
                        ),
                        TENANT_ID,
                        new Ruc("1790012345001"),
                        "NEXORF S.A.S.",
                        "NEXORF",
                        "Quito",
                        Instant.parse(
                                "2026-10-05T20:00:00Z"
                        )
                );

        when(
                createTaxpayerUseCase.create(any())
        ).thenReturn(taxpayer);

        mockMvc.perform(
                        post(
                                "/api/v1/tenants/{tenantId}/taxpayers",
                                TENANT_ID
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "ruc": "1790012345001",
                                          "legalName": "NEXORF S.A.S.",
                                          "tradeName": "NEXORF",
                                          "mainAddress": "Quito"
                                        }
                                        """)
                )
                .andExpect(
                        status().isCreated()
                )
                .andExpect(
                        jsonPath("$.tenantId")
                                .value(TENANT_ID.toString())
                )
                .andExpect(
                        jsonPath("$.ruc")
                                .value("1790012345001")
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("ACTIVE")
                )
                .andExpect(
                        jsonPath("$.testEnabled")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.productionEnabled")
                                .value(false)
                );
    }

    @Test
    void shouldRejectInvalidRuc() throws Exception {

        mockMvc.perform(
                        post(
                                "/api/v1/tenants/{tenantId}/taxpayers",
                                TENANT_ID
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "ruc": "123",
                                          "legalName": "NEXORF S.A.S.",
                                          "tradeName": "NEXORF",
                                          "mainAddress": "Quito"
                                        }
                                        """)
                )
                .andExpect(
                        status().isBadRequest()
                )
                .andExpect(
                        jsonPath("$.type")
                                .value(
                                        "urn:emitta:problem:validation-error"
                                )
                );
    }

    @Test
    void shouldReturnNotFoundWhenTenantDoesNotExist()
            throws Exception {

        when(
                createTaxpayerUseCase.create(any())
        ).thenThrow(
                new TenantNotFoundException(
                        TENANT_ID
                )
        );

        mockMvc.perform(
                        post(
                                "/api/v1/tenants/{tenantId}/taxpayers",
                                TENANT_ID
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(validRequest())
                )
                .andExpect(
                        status().isNotFound()
                )
                .andExpect(
                        jsonPath("$.type")
                                .value(
                                        "urn:emitta:problem:resource-not-found"
                                )
                )
                .andExpect(
                        jsonPath("$.tenantId")
                                .value(TENANT_ID.toString())
                );
    }

    @Test
    void shouldReturnConflictForDuplicatedRuc()
            throws Exception {

        when(
                createTaxpayerUseCase.create(any())
        ).thenThrow(
                new TaxpayerAlreadyExistsException(
                        "1790012345001"
                )
        );

        mockMvc.perform(
                        post(
                                "/api/v1/tenants/{tenantId}/taxpayers",
                                TENANT_ID
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(validRequest())
                )
                .andExpect(
                        status().isConflict()
                )
                .andExpect(
                        jsonPath("$.type")
                                .value(
                                        "urn:emitta:problem:conflict"
                                )
                )
                .andExpect(
                        jsonPath("$.ruc")
                                .value(
                                        "1790012345001"
                                )
                );
    }

    private String validRequest() {
        return """
                {
                  "ruc": "1790012345001",
                  "legalName": "NEXORF S.A.S.",
                  "tradeName": "NEXORF",
                  "mainAddress": "Quito"
                }
                """;
    }
}