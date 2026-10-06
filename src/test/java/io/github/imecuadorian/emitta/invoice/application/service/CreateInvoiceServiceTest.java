package io.github.imecuadorian.emitta.invoice.application.service;

import io.github.imecuadorian.emitta.document.application.model.CreateDocumentResult;
import io.github.imecuadorian.emitta.document.application.port.in.CreateDocumentUseCase;
import io.github.imecuadorian.emitta.document.domain.Document;
import io.github.imecuadorian.emitta.document.domain.IdempotencyKey;
import io.github.imecuadorian.emitta.invoice.application.command.CreateInvoiceCommand;
import io.github.imecuadorian.emitta.invoice.application.command.InvoiceBuyerCommand;
import io.github.imecuadorian.emitta.invoice.application.command.InvoiceItemCommand;
import io.github.imecuadorian.emitta.invoice.application.command.InvoicePaymentCommand;
import io.github.imecuadorian.emitta.invoice.application.command.InvoiceTaxCommand;
import io.github.imecuadorian.emitta.invoice.application.exception.ExpectedTotalMismatchException;
import io.github.imecuadorian.emitta.invoice.application.model.CreateInvoiceResult;
import io.github.imecuadorian.emitta.invoice.application.port.out.InvoiceRepository;
import io.github.imecuadorian.emitta.invoice.domain.Invoice;
import io.github.imecuadorian.emitta.shared.fiscal.DocumentType;
import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateInvoiceServiceTest {

    private static final UUID TENANT_ID =
            UUID.randomUUID();

    private static final UUID TAXPAYER_ID =
            UUID.randomUUID();

    private static final UUID POINT_OF_ISSUE_ID =
            UUID.randomUUID();

    private static final UUID DOCUMENT_ID =
            UUID.randomUUID();

    private static final Instant NOW =
            Instant.parse(
                    "2026-10-06T05:00:00Z"
            );

    @Mock
    private CreateDocumentUseCase
            createDocumentUseCase;

    @Mock
    private InvoiceRepository
            invoiceRepository;

    private CreateInvoiceService service;

    @BeforeEach
    void setUp() {

        AtomicLong sequence =
                new AtomicLong();

        service =
                new CreateInvoiceService(
                        createDocumentUseCase,
                        invoiceRepository,
                        () ->
                                new UUID(
                                        0,
                                        sequence.incrementAndGet()
                                )
                );
    }

    @Test
    void shouldCreateAuthoritativeInvoiceSnapshot() {

        when(
                createDocumentUseCase.create(
                        any()
                )
        ).thenReturn(
                new CreateDocumentResult(
                        queuedDocument(),
                        true
                )
        );

        CreateInvoiceResult result =
                service.create(
                        validCommand(
                                new BigDecimal(
                                        "20.70"
                                )
                        )
                );

        assertTrue(
                result.created()
        );

        assertEquals(
                new BigDecimal(
                        "18.00"
                ),
                result.invoice()
                        .getSubtotal()
        );

        assertEquals(
                new BigDecimal(
                        "2.00"
                ),
                result.invoice()
                        .getDiscountTotal()
        );

        assertEquals(
                new BigDecimal(
                        "2.70"
                ),
                result.invoice()
                        .getTaxTotal()
        );

        assertEquals(
                new BigDecimal(
                        "20.70"
                ),
                result.invoice()
                        .getTotal()
        );

        ArgumentCaptor<Invoice> captor =
                ArgumentCaptor.forClass(
                        Invoice.class
                );

        verify(
                invoiceRepository
        ).insert(
                captor.capture()
        );

        assertEquals(
                DOCUMENT_ID,
                captor.getValue()
                        .getDocumentId()
        );
    }

    @Test
    void shouldRejectExpectedTotalMismatch() {

        when(
                createDocumentUseCase.create(
                        any()
                )
        ).thenReturn(
                new CreateDocumentResult(
                        queuedDocument(),
                        true
                )
        );

        assertThrows(
                ExpectedTotalMismatchException.class,
                () ->
                        service.create(
                                validCommand(
                                        new BigDecimal(
                                                "99.99"
                                        )
                                )
                        )
        );

        verify(
                invoiceRepository,
                never()
        ).insert(
                any()
        );
    }

    private CreateInvoiceCommand validCommand(
            BigDecimal expectedTotal
    ) {

        return new CreateInvoiceCommand(
                TENANT_ID,
                POINT_OF_ISSUE_ID,
                FiscalEnvironment.TEST,
                "invoice-create-test",
                NOW,
                null,
                new InvoiceBuyerCommand(
                        "07",
                        "9999999999999",
                        "CONSUMIDOR FINAL",
                        null,
                        null
                ),
                List.of(
                        new InvoiceItemCommand(
                                "P001",
                                "Test product",
                                new BigDecimal(
                                        "2"
                                ),
                                new BigDecimal(
                                        "10.00"
                                ),
                                new BigDecimal(
                                        "2.00"
                                ),
                                List.of(
                                        new InvoiceTaxCommand(
                                                "2",
                                                "4",
                                                new BigDecimal(
                                                        "15"
                                                )
                                        )
                                )
                        )
                ),
                List.of(
                        new InvoicePaymentCommand(
                                "01",
                                new BigDecimal(
                                        "20.70"
                                ),
                                null,
                                null
                        )
                ),
                expectedTotal
        );
    }

    private Document queuedDocument() {

        Document document =
                Document.create(
                        DOCUMENT_ID,
                        TENANT_ID,
                        TAXPAYER_ID,
                        POINT_OF_ISSUE_ID,
                        DocumentType.INVOICE,
                        FiscalEnvironment.TEST,
                        new IdempotencyKey(
                                "invoice-create-test"
                        ),
                        NOW,
                        NOW
                );

        document.queue(
                NOW
        );

        return document;
    }
}