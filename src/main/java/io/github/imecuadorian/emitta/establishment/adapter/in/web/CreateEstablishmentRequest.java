package io.github.imecuadorian.emitta.establishment.adapter.in.web;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateEstablishmentRequest(

        @NotBlank
        @Pattern(
                regexp = "\\d{3}",
                message = "Establishment code must contain exactly 3 numeric digits"
        )
        String code,

        @Size(
                max = 200,
                message = "Establishment name cannot exceed 200 characters"
        )
        String name,

        @Size(
                max = 500,
                message = "Establishment address cannot exceed 500 characters"
        )
        String address
) {
}