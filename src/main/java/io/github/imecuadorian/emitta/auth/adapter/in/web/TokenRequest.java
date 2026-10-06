package io.github.imecuadorian.emitta.auth.adapter.in.web.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TokenRequest(

        @NotBlank
        @Size(max = 100)
        String clientId,

        @NotBlank
        @Size(max = 500)
        String clientSecret
) {
}