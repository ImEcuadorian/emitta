package io.github.imecuadorian.emitta.document.application.event;

import io.github.imecuadorian.emitta.document.domain.Document;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record DocumentReceivedEvent(
        Document document
) {

    public static final String AGGREGATE_TYPE =
            "DOCUMENT";

    public static final String EVENT_TYPE =
            "document.received.v1";

    public DocumentReceivedEvent {

        Objects.requireNonNull(
                document,
                "Document cannot be null"
        );
    }

    public Map<String, Object> payload() {

        Map<String, Object> payload =
                new LinkedHashMap<>();

        payload.put(
                "eventVersion",
                1
        );

        payload.put(
                "documentId",
                document.getId().toString()
        );

        payload.put(
                "tenantId",
                document.getTenantId().toString()
        );

        payload.put(
                "taxpayerId",
                document.getTaxpayerId().toString()
        );

        payload.put(
                "pointOfIssueId",
                document.getPointOfIssueId().toString()
        );

        payload.put(
                "documentType",
                document.getDocumentType().name()
        );

        payload.put(
                "environment",
                document.getEnvironment().name()
        );

        payload.put(
                "status",
                document.getStatus().name()
        );

        payload.put(
                "receivedAt",
                document.getReceivedAt().toString()
        );

        payload.put(
                "queuedAt",
                document.getQueuedAt().toString()
        );

        return Map.copyOf(payload);
    }
}