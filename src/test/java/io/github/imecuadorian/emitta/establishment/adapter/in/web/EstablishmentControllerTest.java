package io.github.imecuadorian.emitta.establishment.adapter.in.web;

import io.github.imecuadorian.emitta.establishment.application.exception.EstablishmentAlreadyExistsException;
import io.github.imecuadorian.emitta.establishment.application.exception.TaxpayerNotFoundException;
import io.github.imecuadorian.emitta.establishment.application.port.in.CreateEstablishmentUseCase;
import io.github.imecuadorian.emitta.establishment.domain.Establishment;
import io.github.imecuadorian.emitta.establishment.domain.EstablishmentCode;
import io.github.imecuadorian.emitta.shared.error.GlobalExceptionHandler;
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

@WebMvcTest(EstablishmentController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class EstablishmentControllerTest {

    private static final UUID TAXPAYER_ID =
            UUID.fromString(
                    "5068da12-fc37-4e12-a50e-05adc629d7c7"
            );

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateEstablishmentUseCase createEstablishmentUseCase;

    @Test
    void shouldCreateEstablishment() throws Exception {

        Establishment establishment =
                Establishment.create(
                        UUID.fromString(
                                "d41de43c-eb87-445f-b02c-baeae6593375"
                        ),
                        TAXPAYER_ID,
                        new EstablishmentCode("001"),
                        "Matriz Quito",
                        "Av. Principal 123",
                        Instant.parse(
                                "2026-10-05T23:30:00Z"
                        )
                );

        when(
                createEstablishmentUseCase.create(any())
        ).thenReturn(establishment);

        mockMvc.perform(
                        post(
                                "/api/v1/taxpayers/{taxpayerId}/establishments",
                                TAXPAYER_ID
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(validRequest())
                )
                .andExpect(
                        status().isCreated()
                )
                .andExpect(
                        jsonPath("$.taxpayerId")
                                .value(
                                        TAXPAYER_ID.toString()
                                )
                )
                .andExpect(
                        jsonPath("$.code")
                                .value("001")
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("ACTIVE")
                );
    }

    @Test
    void shouldRejectInvalidCode() throws Exception {

        mockMvc.perform(
                        post(
                                "/api/v1/taxpayers/{taxpayerId}/establishments",
                                TAXPAYER_ID
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "code": "1",
                                          "name": "Matriz Quito",
                                          "address": "Quito"
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
    void shouldReturnNotFoundWhenTaxpayerDoesNotExist()
            throws Exception {

        when(
                createEstablishmentUseCase.create(any())
        ).thenThrow(
                new TaxpayerNotFoundException(
                        TAXPAYER_ID
                )
        );

        mockMvc.perform(
                        post(
                                "/api/v1/taxpayers/{taxpayerId}/establishments",
                                TAXPAYER_ID
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
                        jsonPath("$.taxpayerId")
                                .value(
                                        TAXPAYER_ID.toString()
                                )
                );
    }

    @Test
    void shouldReturnConflictForDuplicatedCode()
            throws Exception {

        when(
                createEstablishmentUseCase.create(any())
        ).thenThrow(
                new EstablishmentAlreadyExistsException(
                        "001"
                )
        );

        mockMvc.perform(
                        post(
                                "/api/v1/taxpayers/{taxpayerId}/establishments",
                                TAXPAYER_ID
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
                        jsonPath("$.code")
                                .value("001")
                );
    }

    private String validRequest() {

        return """
                {
                  "code": "001",
                  "name": "Matriz Quito",
                  "address": "Av. Principal 123"
                }
                """;
    }
}