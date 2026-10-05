package io.github.imecuadorian.emitta.taxpayer.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTaxpayerRequest(

        @NotBlank(message = "RUC is required")
        @Pattern(
                regexp = "\\d{13}",
                message = "RUC must contain exactly 13 numeric digits"
        )
        String ruc,

        @NotBlank(message = "Legal name is required")
        @Size(
                max = 300,
                message = "Legal name cannot exceed 300 characters"
        )
        String legalName,

        @Size(
                max = 300,
                message = "Trade name cannot exceed 300 characters"
        )
        String tradeName,

        @NotBlank(message = "Main address is required")
        @Size(
                max = 300,
                message = "Main address cannot exceed 300 characters"
        )
        String mainAddress
) {
}