package io.github.imecuadorian.emitta.srireception.application.model;

public record SriReceptionMessage(
        String identifier,
        String message,
        String additionalInformation,
        String type
) {

    public SriReceptionMessage {

        if (
                identifier == null
                        || identifier.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "SRI message identifier cannot be blank"
            );
        }

        if (
                message == null
                        || message.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "SRI message cannot be blank"
            );
        }

        if (
                type == null
                        || type.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "SRI message type cannot be blank"
            );
        }

        identifier =
                identifier.trim();

        message =
                message.trim();

        type =
                type.trim();

        if (additionalInformation != null) {

            additionalInformation =
                    additionalInformation.trim();

            if (additionalInformation.isEmpty()) {

                additionalInformation =
                        null;
            }
        }
    }
}