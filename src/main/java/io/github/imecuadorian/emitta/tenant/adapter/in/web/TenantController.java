package io.github.imecuadorian.emitta.tenant.adapter.in.web;

import io.github.imecuadorian.emitta.tenant.application.command.CreateTenantCommand;
import io.github.imecuadorian.emitta.tenant.application.port.in.CreateTenantUseCase;
import io.github.imecuadorian.emitta.tenant.domain.Tenant;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenants")
@Tag(
        name = "Tenants",
        description = "Tenant administration operations"
)
public class TenantController {

    private final CreateTenantUseCase createTenantUseCase;

    public TenantController(
            CreateTenantUseCase createTenantUseCase
    ) {
        this.createTenantUseCase = createTenantUseCase;
    }

    @PostMapping
    @Operation(
            summary = "Create a tenant",
            description = """
                    Creates a new Emitta tenant.

                    A tenant represents an organization or account
                    that consumes Emitta services.
                    """
    )
    public ResponseEntity<TenantResponse> create(
            @Valid @RequestBody CreateTenantRequest request
    ) {

        CreateTenantCommand command =
                new CreateTenantCommand(
                        request.name()
                );

        Tenant tenant =
                createTenantUseCase.create(command);

        TenantResponse response =
                TenantResponse.from(tenant);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}