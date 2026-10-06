package io.github.imecuadorian.emitta.auth.adapter.in.web.model;

public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
}