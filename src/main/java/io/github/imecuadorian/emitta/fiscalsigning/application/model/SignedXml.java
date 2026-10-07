package io.github.imecuadorian.emitta.fiscalsigning.application.model;

import java.util.Objects;

public record SignedXml(
        byte[] content
) {

    public SignedXml {

        Objects.requireNonNull(
                content,
                "Signed XML content cannot be null"
        );

        if (content.length == 0) {
            throw new IllegalArgumentException(
                    "Signed XML content cannot be empty"
            );
        }

        content =
                content.clone();
    }

    @Override
    public byte[] content() {

        return content.clone();
    }
}