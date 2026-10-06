package io.github.imecuadorian.emitta.pointofissue.adapter.in.web;

import io.github.imecuadorian.emitta.pointofissue.application.exception.EstablishmentNotFoundException;
import io.github.imecuadorian.emitta.pointofissue.application.exception.PointOfIssueAlreadyExistsException;
import io.github.imecuadorian.emitta.pointofissue.application.port.in.CreatePointOfIssueUseCase;
import io.github.imecuadorian.emitta.pointofissue.domain.PointOfIssue;
import io.github.imecuadorian.emitta.pointofissue.domain.PointOfIssueCode;
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

@WebMvcTest(PointOfIssueController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class PointOfIssueControllerTest {

    private static final UUID ESTABLISHMENT_ID =
            UUID.fromString(
                    "d41de43c-eb87-445f-b02c-baeae6593375"
            );

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreatePointOfIssueUseCase createPointOfIssueUseCase;

    @Test
    void shouldCreatePointOfIssue() throws Exception {

        PointOfIssue pointOfIssue =
                PointOfIssue.create(
                        UUID.fromString(
                                "44ee5a49-334a-43c1-a57a-b1bb47ee60a4"
                        ),
                        ESTABLISHMENT_ID,
                        new PointOfIssueCode("001"),
                        "Caja Principal",
                        Instant.parse(
                                "2026-10-06T00:30:00Z"
                        )
                );

        when(
                createPointOfIssueUseCase.create(any())
        ).thenReturn(pointOfIssue);

        mockMvc.perform(
                        post(
                                "/api/v1/establishments/{establishmentId}/points-of-issue",
                                ESTABLISHMENT_ID
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
                        jsonPath("$.establishmentId")
                                .value(
                                        ESTABLISHMENT_ID.toString()
                                )
                )
                .andExpect(
                        jsonPath("$.code")
                                .value("001")
                )
                .andExpect(
                        jsonPath("$.name")
                                .value("Caja Principal")
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
                                "/api/v1/establishments/{establishmentId}/points-of-issue",
                                ESTABLISHMENT_ID
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "code": "1",
                                          "name": "Caja Principal"
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
    void shouldReturnNotFoundWhenEstablishmentDoesNotExist()
            throws Exception {

        when(
                createPointOfIssueUseCase.create(any())
        ).thenThrow(
                new EstablishmentNotFoundException(
                        ESTABLISHMENT_ID
                )
        );

        mockMvc.perform(
                        post(
                                "/api/v1/establishments/{establishmentId}/points-of-issue",
                                ESTABLISHMENT_ID
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
                        jsonPath("$.establishmentId")
                                .value(
                                        ESTABLISHMENT_ID.toString()
                                )
                );
    }

    @Test
    void shouldReturnConflictForDuplicatedCode()
            throws Exception {

        when(
                createPointOfIssueUseCase.create(any())
        ).thenThrow(
                new PointOfIssueAlreadyExistsException(
                        "001"
                )
        );

        mockMvc.perform(
                        post(
                                "/api/v1/establishments/{establishmentId}/points-of-issue",
                                ESTABLISHMENT_ID
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
                        jsonPath("$.code")
                                .value("001")
                );
    }

    private String validRequest() {

        return """
                {
                  "code": "001",
                  "name": "Caja Principal"
                }
                """;
    }
}