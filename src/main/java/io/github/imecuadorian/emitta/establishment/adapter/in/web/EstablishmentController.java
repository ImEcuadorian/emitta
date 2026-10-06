package io.github.imecuadorian.emitta.establishment.adapter.in.web;

import io.github.imecuadorian.emitta.establishment.application.command.CreateEstablishmentCommand;
import io.github.imecuadorian.emitta.establishment.application.port.in.CreateEstablishmentUseCase;
import io.github.imecuadorian.emitta.establishment.domain.Establishment;
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
@RequestMapping(
        "/api/v1/taxpayers/{taxpayerId}/establishments"
)
@Tag(
        name = "Establishments",
        description = "Fiscal establishment administration"
)
public class EstablishmentController {

    private final CreateEstablishmentUseCase createEstablishmentUseCase;

    public EstablishmentController(
            CreateEstablishmentUseCase createEstablishmentUseCase
    ) {
        this.createEstablishmentUseCase =
                createEstablishmentUseCase;
    }

    @PostMapping
    @Operation(
            summary = "Create an establishment",
            description = """
                    Creates a fiscal establishment under an
                    existing taxpayer.
                    """
    )
    public ResponseEntity<EstablishmentResponse> create(
            @PathVariable UUID taxpayerId,
            @Valid @RequestBody CreateEstablishmentRequest request
    ) {

        CreateEstablishmentCommand command =
                new CreateEstablishmentCommand(
                        taxpayerId,
                        request.code(),
                        request.name(),
                        request.address()
                );

        Establishment establishment =
                createEstablishmentUseCase.create(
                        command
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        EstablishmentResponse.from(
                                establishment
                        )
                );
    }
}