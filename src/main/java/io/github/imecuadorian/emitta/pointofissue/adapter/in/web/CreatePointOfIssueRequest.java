package io.github.imecuadorian.emitta.pointofissue.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreatePointOfIssueRequest(

        @NotBlank(
                message = "Point of issue code is required"
        )
        @Pattern(
                regexp = "\\d{3}",
                message = "Point of issue code must contain exactly 3 numeric digits"
        )
        String code,

        @Size(
                max = 200,
                message = "Point of issue name cannot exceed 200 characters"
        )
        String name
) {
}