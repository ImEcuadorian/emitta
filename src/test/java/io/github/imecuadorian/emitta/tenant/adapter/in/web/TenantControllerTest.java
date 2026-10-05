package io.github.imecuadorian.emitta.tenant.adapter.in.web;

import io.github.imecuadorian.emitta.shared.error.GlobalExceptionHandler;
import io.github.imecuadorian.emitta.tenant.application.port.in.CreateTenantUseCase;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import io.github.imecuadorian.emitta.tenant.domain.Tenant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
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

@WebMvcTest(TenantController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class TenantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateTenantUseCase createTenantUseCase;

    @Test
    void shouldCreateTenant() throws Exception {

        UUID id =
                UUID.fromString(
                        "4d34da33-c6e4-4379-8ab0-19fef817cc67"
                );

        Instant now =
                Instant.parse(
                        "2026-10-05T03:00:00Z"
                );

        Tenant tenant =
                Tenant.create(
                        id,
                        "NEXORF",
                        now
                );

        when(
                createTenantUseCase.create(any())
        ).thenReturn(tenant);

        mockMvc.perform(
                        post("/api/v1/tenants")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": "NEXORF"
                                        }
                                        """)
                )
                .andExpect(
                        status().isCreated()
                )
                .andExpect(
                        jsonPath("$.id")
                                .value(id.toString())
                )
                .andExpect(
                        jsonPath("$.name")
                                .value("NEXORF")
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("ACTIVE")
                );
    }

    @Test
    void shouldRejectBlankTenantName() throws Exception {

        mockMvc.perform(
                        post("/api/v1/tenants")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "name": ""
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
                )
                .andExpect(
                        jsonPath("$.title")
                                .value(
                                        "Request validation failed"
                                )
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(400)
                )
                .andExpect(
                        jsonPath("$.detail")
                                .value(
                                        "One or more request fields are invalid."
                                )
                )
                .andExpect(
                        jsonPath("$.instance")
                                .value(
                                        "/api/v1/tenants"
                                )
                )
                .andExpect(
                        jsonPath("$.errors[0].field")
                                .value("name")
                )
                .andExpect(
                        jsonPath("$.errors[0].message")
                                .value(
                                        "Tenant name is required"
                                )
                );
    }
}