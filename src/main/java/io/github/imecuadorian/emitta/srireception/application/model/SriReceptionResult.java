package io.github.imecuadorian.emitta.srireception.application.model;

import io.github.imecuadorian.emitta.srireception.domain.SriReceptionStatus;

import java.util.List;
import java.util.Objects;

public record SriReceptionResult(
        SriReceptionStatus status,
        List<SriReceptionMessage> messages
) {

    public SriReceptionResult {

        Objects.requireNonNull(
                status,
                "SRI reception status cannot be null"
        );

        Objects.requireNonNull(
                messages,
                "SRI reception messages cannot be null"
        );

        messages =
                List.copyOf(
                        messages
                );
    }
}