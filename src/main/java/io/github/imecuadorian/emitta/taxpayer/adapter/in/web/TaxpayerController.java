package io.github.imecuadorian.emitta.taxpayer.adapter.in.web;

import io.github.imecuadorian.emitta.taxpayer.application.command.CreateTaxpayerCommand;
import io.github.imecuadorian.emitta.taxpayer.application.port.in.CreateTaxpayerUseCase;
import io.github.imecuadorian.emitta.taxpayer.domain.Taxpayer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/taxpayers")
@Tag(
        name = "Taxpayers",
        description = "Fiscal taxpayer administration"
)
public class TaxpayerController {

    private final CreateTaxpayerUseCase createTaxpayerUseCase;

    public TaxpayerController(
            CreateTaxpayerUseCase createTaxpayerUseCase
    ) {
        this.createTaxpayerUseCase =
                createTaxpayerUseCase;
    }

    @PostMapping
    @Operation(
            summary = "Create a taxpayer",
            description = """
                    Registers a fiscal taxpayer under an existing tenant.

                    New taxpayers start enabled for the SRI test environment
                    and disabled for production.
                    """
    )
    public ResponseEntity<TaxpayerResponse> create(
            @PathVariable UUID tenantId,
            @Valid @RequestBody CreateTaxpayerRequest request
    ) {

        CreateTaxpayerCommand command =
                new CreateTaxpayerCommand(
                        tenantId,
                        request.ruc(),
                        request.legalName(),
                        request.tradeName(),
                        request.mainAddress()
                );

        Taxpayer taxpayer =
                createTaxpayerUseCase.create(command);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        TaxpayerResponse.from(taxpayer)
                );
    }
}