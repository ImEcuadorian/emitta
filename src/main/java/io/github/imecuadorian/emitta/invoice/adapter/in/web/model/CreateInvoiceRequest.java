package io.github.imecuadorian.emitta.invoice.adapter.in.web.model;

import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreateInvoiceRequest(

        @NotNull
        UUID pointOfIssueId,

        @NotNull
        FiscalEnvironment environment,

        @NotNull
        Instant issuedAt,

        UUID customerId,

        @NotNull
        @Valid
        Buyer buyer,

        @NotEmpty
        List<@Valid Item> items,

        @NotEmpty
        List<@Valid Payment> payments,

        @DecimalMin("0.00")
        @Digits(
                integer = 12,
                fraction = 2
        )
        BigDecimal expectedTotal
) {

    public record Buyer(

            @NotBlank
            @Pattern(
                    regexp = "\\d{2}"
            )
            String identificationType,

            @NotBlank
            @Size(
                    max = 30
            )
            String identification,

            @NotBlank
            @Size(
                    max = 300
            )
            String name,

            @Email
            @Size(
                    max = 320
            )
            String email,

            @Size(
                    max = 500
            )
            String address
    ) {
    }

    public record Item(

            @Size(
                    max = 25
            )
            String sku,

            @NotBlank
            @Size(
                    max = 300
            )
            String description,

            @NotNull
            @DecimalMin(
                    value = "0",
                    inclusive = false
            )
            @Digits(
                    integer = 12,
                    fraction = 6
            )
            BigDecimal quantity,

            @NotNull
            @DecimalMin("0")
            @Digits(
                    integer = 12,
                    fraction = 6
            )
            BigDecimal unitPrice,

            @NotNull
            @DecimalMin("0")
            @Digits(
                    integer = 12,
                    fraction = 2
            )
            BigDecimal discount,

            @NotEmpty
            List<@Valid Tax> taxes
    ) {
    }

    public record Tax(

            @NotBlank
            @Pattern(
                    regexp = "\\d{1,4}"
            )
            String taxCode,

            @NotBlank
            @Pattern(
                    regexp = "\\d{1,4}"
            )
            String percentageCode,

            @NotNull
            @DecimalMin("0")
            @Digits(
                    integer = 3,
                    fraction = 4
            )
            BigDecimal rate
    ) {
    }

    public record Payment(

            @NotBlank
            @Pattern(
                    regexp = "\\d{2}"
            )
            String paymentMethod,

            @NotNull
            @DecimalMin(
                    value = "0",
                    inclusive = false
            )
            @Digits(
                    integer = 12,
                    fraction = 2
            )
            BigDecimal total,

            @DecimalMin("0")
            @Digits(
                    integer = 12,
                    fraction = 2
            )
            BigDecimal term,

            @Size(
                    min = 1,
                    max = 10
            )
            String unitTime
    ) {
    }
}