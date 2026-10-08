package io.github.imecuadorian.emitta.document.domain;

import io.github.imecuadorian.emitta.shared.fiscal.DocumentType;
import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentTest {

    private static final Instant NOW =
            Instant.parse(
                    "2026-10-06T01:00:00Z"
            );

    @Test
    void shouldCreateReceivedDocumentWithoutFiscalIdentity() {

        Document document =
                createDocument();

        assertEquals(
                DocumentStatus.RECEIVED,
                document.getStatus()
        );

        assertNull(
                document.getSequential()
        );

        assertNull(
                document.getAccessKey()
        );

        assertEquals(
                NOW,
                document.getReceivedAt()
        );
    }

    @Test
    void shouldAssignFiscalIdentityOnlyOnce() {

        Document document =
                createDocument();

        document.assignFiscalIdentity(
                124L,
                validAccessKey(),
                NOW.plusSeconds(1)
        );

        assertEquals(
                124L,
                document.getSequential()
        );

        assertEquals(
                validAccessKey(),
                document.getAccessKey()
        );

        assertThrows(
                IllegalStateException.class,
                () -> document.assignFiscalIdentity(
                        125L,
                        validAccessKey(),
                        NOW.plusSeconds(2)
                )
        );
    }

    @Test
    void shouldAdvanceThroughHappyPath() {

        Document document =
                createDocument();

        document.queue(
                NOW.plusSeconds(1)
        );

        document.startGenerating(
                NOW.plusSeconds(2)
        );

        document.markSigned(
                NOW.plusSeconds(3)
        );

        document.markSubmitted(
                NOW.plusSeconds(4)
        );

        document.markAuthorized(
                NOW.plusSeconds(5)
        );

        assertEquals(
                DocumentStatus.AUTHORIZED,
                document.getStatus()
        );

        assertEquals(
                NOW.plusSeconds(5),
                document.getAuthorizedAt()
        );
    }

    @Test
    void shouldRejectInvalidTransition() {

        Document document =
                createDocument();

        assertThrows(
                IllegalStateException.class,
                () -> document.markAuthorized(
                        NOW.plusSeconds(1)
                )
        );
    }

    @Test
    void shouldScheduleRetryAfterSubmittedFailure() {

        Document document =
                createDocument();

        document.queue(
                NOW.plusSeconds(
                        1
                )
        );

        document.startGenerating(
                NOW.plusSeconds(
                        2
                )
        );

        document.markSigned(
                NOW.plusSeconds(
                        3
                )
        );

        document.markSubmitted(
                NOW.plusSeconds(
                        4
                )
        );

        document.scheduleRetry(
                NOW.plusSeconds(
                        5
                )
        );

        assertEquals(
                DocumentStatus.RETRY_PENDING,
                document.getStatus()
        );
    }

    @Test
    void shouldResubmitRetryPendingDocument() {

        Document document =
                createDocument();

        document.queue(
                NOW.plusSeconds(
                        1
                )
        );

        document.startGenerating(
                NOW.plusSeconds(
                        2
                )
        );

        document.markSigned(
                NOW.plusSeconds(
                        3
                )
        );

        document.markSubmitted(
                NOW.plusSeconds(
                        4
                )
        );

        document.scheduleRetry(
                NOW.plusSeconds(
                        5
                )
        );

        document.markSubmitted(
                NOW.plusSeconds(
                        6
                )
        );

        assertEquals(
                DocumentStatus.SUBMITTED,
                document.getStatus()
        );

        assertEquals(
                NOW.plusSeconds(
                        6
                ),
                document.getSubmittedAt()
        );
    }

    @Test
    void shouldNotRetryRejectedDocument() {

        Document document =
                createDocument();

        document.queue(
                NOW.plusSeconds(
                        1
                )
        );

        document.startGenerating(
                NOW.plusSeconds(
                        2
                )
        );

        document.markSigned(
                NOW.plusSeconds(
                        3
                )
        );

        document.markSubmitted(
                NOW.plusSeconds(
                        4
                )
        );

        document.markRejected(
                NOW.plusSeconds(
                        5
                )
        );

        assertThrows(
                IllegalStateException.class,
                () ->
                        document.scheduleRetry(
                                NOW.plusSeconds(
                                        6
                                )
                        )
        );

        assertEquals(
                DocumentStatus.REJECTED,
                document.getStatus()
        );
    }

    @Test
    void shouldAuthorizeDocumentAfterRetryPending() {

        Document document =
                createDocument();

        document.queue(NOW.plusSeconds(1));

        document.startGenerating(NOW.plusSeconds(2));

        document.markSigned(NOW.plusSeconds(3));

        document.markSubmitted(NOW.plusSeconds(4));

        document.scheduleRetry(NOW.plusSeconds(5));

        document.markAuthorized(NOW.plusSeconds(6));

        assertEquals(
                DocumentStatus.AUTHORIZED,
                document.getStatus()
        );

        assertEquals(
                NOW.plusSeconds(6),
                document.getAuthorizedAt()
        );
    }

    private Document createDocument() {

        return Document.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                DocumentType.INVOICE,
                FiscalEnvironment.TEST,
                new IdempotencyKey(
                        "order-2026-001"
                ),
                NOW,
                NOW
        );
    }

    private String validAccessKey() {

        return "2111202405176001321000110010010000001241234567810";
    }
}