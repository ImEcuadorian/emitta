package io.github.imecuadorian.emitta.fiscalprocessing.adapter.in.messaging;

// The outbox envelope includes metadata beyond the document ID consumed here.
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public record DocumentReceivedMessage(
        String documentId
) {
}