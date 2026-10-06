package io.github.imecuadorian.emitta.document.application.service;

import io.github.imecuadorian.emitta.document.application.command.CreateDocumentCommand;
import io.github.imecuadorian.emitta.document.application.exception.DocumentIdempotencyConflictException;
import io.github.imecuadorian.emitta.document.application.exception.DocumentTenantMismatchException;
import io.github.imecuadorian.emitta.document.application.model.CreateDocumentResult;
import io.github.imecuadorian.emitta.document.application.port.out.DocumentRepository;
import io.github.imecuadorian.emitta.document.domain.Document;
import io.github.imecuadorian.emitta.document.domain.IdempotencyKey;
import io.github.imecuadorian.emitta.establishment.application.port.in.EstablishmentFiscalData;
import io.github.imecuadorian.emitta.establishment.application.port.in.EstablishmentFiscalLookupUseCase;
import io.github.imecuadorian.emitta.pointofissue.application.port.in.PointOfIssueFiscalData;
import io.github.imecuadorian.emitta.pointofissue.application.port.in.PointOfIssueFiscalLookupUseCase;
import io.github.imecuadorian.emitta.shared.fiscal.DocumentType;
import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;
import io.github.imecuadorian.emitta.taxpayer.application.port.in.TaxpayerFiscalData;
import io.github.imecuadorian.emitta.taxpayer.application.port.in.TaxpayerFiscalLookupUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateDocumentServiceTest {

    private static final UUID TENANT_ID =
            UUID.randomUUID();

    private static final UUID TAXPAYER_ID =
            UUID.randomUUID();

    private static final UUID ESTABLISHMENT_ID =
            UUID.randomUUID();

    private static final UUID POINT_OF_ISSUE_ID =
            UUID.randomUUID();

    private static final UUID DOCUMENT_ID =
            UUID.randomUUID();

    private static final Instant NOW =
            Instant.parse(
                    "2026-10-06T02:30:00Z"
            );

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private PointOfIssueFiscalLookupUseCase
            pointOfIssueLookup;

    @Mock
    private EstablishmentFiscalLookupUseCase
            establishmentLookup;

    @Mock
    private TaxpayerFiscalLookupUseCase
            taxpayerLookup;

    private CreateDocumentService service;

    @BeforeEach
    void setUp() {

        Clock clock =
                Clock.fixed(
                        NOW,
                        ZoneOffset.UTC
                );

        service =
                new CreateDocumentService(
                        documentRepository,
                        pointOfIssueLookup,
                        establishmentLookup,
                        taxpayerLookup,
                        clock,
                        () -> DOCUMENT_ID
                );
    }

    @Test
    void shouldCreateReceivedDocument() {

        configureFiscalHierarchy(
                TENANT_ID
        );

        when(
                documentRepository
                        .findByTenantIdAndIdempotencyKey(
                                any(),
                                any()
                        )
        ).thenReturn(
                Optional.empty()
        );

        when(
                documentRepository.insertIfAbsent(
                        any()
                )
        ).thenReturn(
                true
        );

        CreateDocumentResult result =
                service.create(
                        validCommand()
                );

        assertTrue(
                result.created()
        );

        assertEquals(
                DOCUMENT_ID,
                result.document().getId()
        );

        assertEquals(
                TENANT_ID,
                result.document().getTenantId()
        );

        assertEquals(
                TAXPAYER_ID,
                result.document().getTaxpayerId()
        );

        assertEquals(
                POINT_OF_ISSUE_ID,
                result.document().getPointOfIssueId()
        );

        assertEquals(
                "RECEIVED",
                result.document()
                        .getStatus()
                        .name()
        );
    }

    @Test
    void shouldReplayExistingDocumentForSameRequest() {

        Document existing =
                existingDocument();

        when(
                documentRepository
                        .findByTenantIdAndIdempotencyKey(
                                any(),
                                any()
                        )
        ).thenReturn(
                Optional.of(existing)
        );

        CreateDocumentResult result =
                service.create(
                        validCommand()
                );

        assertFalse(
                result.created()
        );

        assertEquals(
                existing.getId(),
                result.document().getId()
        );

        verify(
                documentRepository,
                never()
        ).insertIfAbsent(
                any()
        );
    }

    @Test
    void shouldRejectReuseOfIdempotencyKeyForDifferentRequest() {

        Document existing =
                existingDocument();

        when(
                documentRepository
                        .findByTenantIdAndIdempotencyKey(
                                any(),
                                any()
                        )
        ).thenReturn(
                Optional.of(existing)
        );

        CreateDocumentCommand different =
                new CreateDocumentCommand(
                        TENANT_ID,
                        POINT_OF_ISSUE_ID,
                        DocumentType.CREDIT_NOTE,
                        FiscalEnvironment.TEST,
                        "invoice-001",
                        NOW
                );

        assertThrows(
                DocumentIdempotencyConflictException.class,
                () -> service.create(
                        different
                )
        );
    }

    @Test
    void shouldRejectPointOfIssueOwnedByAnotherTenant() {

        UUID anotherTenant =
                UUID.randomUUID();

        configureFiscalHierarchy(
                anotherTenant
        );

        when(
                documentRepository
                        .findByTenantIdAndIdempotencyKey(
                                any(),
                                any()
                        )
        ).thenReturn(
                Optional.empty()
        );

        assertThrows(
                DocumentTenantMismatchException.class,
                () -> service.create(
                        validCommand()
                )
        );

        verify(
                documentRepository,
                never()
        ).insertIfAbsent(
                any()
        );
    }

    private void configureFiscalHierarchy(
            UUID taxpayerTenantId
    ) {

        when(
                pointOfIssueLookup
                        .findFiscalDataById(
                                POINT_OF_ISSUE_ID
                        )
        ).thenReturn(
                Optional.of(
                        new PointOfIssueFiscalData(
                                POINT_OF_ISSUE_ID,
                                ESTABLISHMENT_ID,
                                "001",
                                true
                        )
                )
        );

        when(
                establishmentLookup
                        .findFiscalDataById(
                                ESTABLISHMENT_ID
                        )
        ).thenReturn(
                Optional.of(
                        new EstablishmentFiscalData(
                                ESTABLISHMENT_ID,
                                TAXPAYER_ID,
                                "001",
                                true
                        )
                )
        );

        when(
                taxpayerLookup
                        .findFiscalDataById(
                                TAXPAYER_ID
                        )
        ).thenReturn(
                Optional.of(
                        new TaxpayerFiscalData(
                                TAXPAYER_ID,
                                taxpayerTenantId,
                                "1790012345001",
                                true,
                                true,
                                true
                        )
                )
        );
    }

    private CreateDocumentCommand validCommand() {

        return new CreateDocumentCommand(
                TENANT_ID,
                POINT_OF_ISSUE_ID,
                DocumentType.INVOICE,
                FiscalEnvironment.TEST,
                "invoice-001",
                NOW
        );
    }

    private Document existingDocument() {

        return Document.create(
                UUID.randomUUID(),
                TENANT_ID,
                TAXPAYER_ID,
                POINT_OF_ISSUE_ID,
                DocumentType.INVOICE,
                FiscalEnvironment.TEST,
                new IdempotencyKey(
                        "invoice-001"
                ),
                NOW,
                NOW
        );
    }
}