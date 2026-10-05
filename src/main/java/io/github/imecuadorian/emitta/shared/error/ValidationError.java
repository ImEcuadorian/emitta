package io.github.imecuadorian.emitta.shared.error;

public record ValidationError(
        String field,
        String message
) {
}