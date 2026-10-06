package io.github.imecuadorian.emitta.invoice.adapter.in.web;

import io.github.imecuadorian.emitta.invoice.adapter.in.web.model.CreateInvoiceRequest;
import io.github.imecuadorian.emitta.invoice.adapter.in.web.model.CreateInvoiceResponse;
import io.github.imecuadorian.emitta.invoice.application.command.CreateInvoiceCommand;
import io.github.imecuadorian.emitta.invoice.application.command.InvoiceBuyerCommand;
import io.github.imecuadorian.emitta.invoice.application.command.InvoiceItemCommand;
import io.github.imecuadorian.emitta.invoice.application.command.InvoicePaymentCommand;
import io.github.imecuadorian.emitta.invoice.application.command.InvoiceTaxCommand;
import io.github.imecuadorian.emitta.invoice.application.model.CreateInvoiceResult;
import io.github.imecuadorian.emitta.invoice.application.port.in.CreateInvoiceUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping(
        "/api/v1/invoices"
)
@Validated
public class InvoiceController {

    private final CreateInvoiceUseCase
            createInvoiceUseCase;

    public InvoiceController(
            CreateInvoiceUseCase createInvoiceUseCase
    ) {
        this.createInvoiceUseCase =
                Objects.requireNonNull(
                        createInvoiceUseCase
                );
    }

    @PostMapping
    public ResponseEntity<CreateInvoiceResponse> create(
            JwtAuthenticationToken authentication,

            @RequestHeader(
                    "Idempotency-Key"
            )
            @NotBlank
            @Size(
                    max = 255
            )
            String idempotencyKey,

            @Valid
            @RequestBody
            CreateInvoiceRequest request
    ) {

        UUID tenantId =
                tenantId(
                        authentication
                );

        CreateInvoiceResult result =
                createInvoiceUseCase.create(
                        toCommand(
                                tenantId,
                                idempotencyKey,
                                request
                        )
                );

        CreateInvoiceResponse response =
                new CreateInvoiceResponse(
                        result.document()
                                .getId(),
                        result.document()
                                .getStatus(),
                        result.invoice()
                                .getTotal(),
                        result.created()
                );

        if (result.created()) {
            return ResponseEntity
                    .accepted()
                    .body(
                            response
                    );
        }

        return ResponseEntity
                .ok(
                        response
                );
    }

    private static UUID tenantId(
            JwtAuthenticationToken authentication
    ) {

        if (authentication == null) {
            throw new InvalidTenantContextException(
                    "Authenticated JWT is required"
            );
        }

        String tenantClaim =
                authentication
                        .getToken()
                        .getClaimAsString(
                                "tenant_id"
                        );

        if (
                tenantClaim == null
                        || tenantClaim.isBlank()
        ) {
            throw new InvalidTenantContextException(
                    "JWT does not contain tenant_id"
            );
        }

        try {

            return UUID.fromString(
                    tenantClaim
            );

        } catch (
                IllegalArgumentException exception
        ) {

            throw new InvalidTenantContextException(
                    "JWT contains an invalid tenant_id"
            );
        }
    }

    private static CreateInvoiceCommand toCommand(
            UUID tenantId,
            String idempotencyKey,
            CreateInvoiceRequest request
    ) {

        return new CreateInvoiceCommand(
                tenantId,
                request.pointOfIssueId(),
                request.environment(),
                idempotencyKey,
                request.issuedAt(),
                request.customerId(),

                new InvoiceBuyerCommand(
                        request.buyer()
                                .identificationType(),
                        request.buyer()
                                .identification(),
                        request.buyer()
                                .name(),
                        request.buyer()
                                .email(),
                        request.buyer()
                                .address()
                ),

                request.items()
                        .stream()
                        .map(
                                item ->
                                        new InvoiceItemCommand(
                                                item.sku(),
                                                item.description(),
                                                item.quantity(),
                                                item.unitPrice(),
                                                item.discount(),
                                                item.taxes()
                                                        .stream()
                                                        .map(
                                                                tax ->
                                                                        new InvoiceTaxCommand(
                                                                                tax.taxCode(),
                                                                                tax.percentageCode(),
                                                                                tax.rate()
                                                                        )
                                                        )
                                                        .toList()
                                        )
                        )
                        .toList(),

                request.payments()
                        .stream()
                        .map(
                                payment ->
                                        new InvoicePaymentCommand(
                                                payment.paymentMethod(),
                                                payment.total(),
                                                payment.term(),
                                                payment.unitTime()
                                        )
                        )
                        .toList(),

                request.expectedTotal()
        );
    }
}