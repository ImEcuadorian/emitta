package io.github.imecuadorian.emitta.fiscalprocessing.application.service;

import io.github.imecuadorian.emitta.accesskey.application.command.GenerateAccessKeyCommand;
import io.github.imecuadorian.emitta.accesskey.application.model.GeneratedAccessKey;
import io.github.imecuadorian.emitta.accesskey.application.port.in.GenerateAccessKeyUseCase;
import io.github.imecuadorian.emitta.accesskey.domain.AccessKey;
import io.github.imecuadorian.emitta.document.application.port.out.DocumentRepository;
import io.github.imecuadorian.emitta.document.domain.Document;
import io.github.imecuadorian.emitta.document.domain.DocumentStatus;
import io.github.imecuadorian.emitta.document.domain.IdempotencyKey;
import io.github.imecuadorian.emitta.documentsequence.domain.SequentialNumber;
import io.github.imecuadorian.emitta.fiscalprocessing.application.command.ProcessFiscalDocumentCommand;
import io.github.imecuadorian.emitta.fiscalprocessing.application.model.ProcessFiscalDocumentResult;
import io.github.imecuadorian.emitta.shared.fiscal.DocumentType;
import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessFiscalDocumentServiceTest {

    private static final UUID DOCUMENT_ID =
            UUID.randomUUID();

    private static final Instant NOW =
            Instant.parse(
                    "2026-10-06T04:00:00Z"
            );

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private GenerateAccessKeyUseCase
            generateAccessKeyUseCase;

    private ProcessFiscalDocumentService service;

    @BeforeEach
    void setUp() {

        service =
                new ProcessFiscalDocumentService(
                        documentRepository,
                        generateAccessKeyUseCase,
                        Clock.fixed(
                                NOW,
                                ZoneOffset.UTC
                        ),
                        ZoneId.of(
                                "America/Guayaquil"
                        )
                );
    }

    @Test
    void shouldGenerateFiscalIdentityForQueuedDocument() {

        Document document =
                queuedDocument();

        when(
                documentRepository
                        .findByIdForUpdate(
                                DOCUMENT_ID
                        )
        ).thenReturn(
                Optional.of(
                        document
                )
        );

        GeneratedAccessKey generated =
                new GeneratedAccessKey(
                        new AccessKey(
                                "2111202405176001321000110010010000001241234567810"
                        ),
                        new SequentialNumber(
                                124
                        ),
                        "12345678"
                );

        when(
                generateAccessKeyUseCase.generate(
                        any(
                                GenerateAccessKeyCommand.class
                        )
                )
        ).thenReturn(
                generated
        );

        when(
                documentRepository.save(
                        any()
                )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(0)
        );

        ProcessFiscalDocumentResult result =
                service.process(
                        new ProcessFiscalDocumentCommand(
                                DOCUMENT_ID
                        )
                );

        assertTrue(
                result.processed()
        );

        assertEquals(
                DocumentStatus.GENERATING,
                result.status()
        );

        assertEquals(
                124L,
                document.getSequential()
        );

        assertEquals(
                generated.accessKey().value(),
                document.getAccessKey()
        );

        verify(
                generateAccessKeyUseCase,
                times(1)
        ).generate(
                any()
        );

        verify(
                documentRepository,
                times(1)
        ).save(
                document
        );
    }

    @Test
    void shouldIgnoreDuplicateDeliveryWhenDocumentIsAlreadyGenerating() {

        Document document =
                queuedDocument();

        document.startGenerating(
                NOW.minusSeconds(
                        10
                )
        );

        when(
                documentRepository
                        .findByIdForUpdate(
                                DOCUMENT_ID
                        )
        ).thenReturn(
                Optional.of(
                        document
                )
        );

        ProcessFiscalDocumentResult result =
                service.process(
                        new ProcessFiscalDocumentCommand(
                                DOCUMENT_ID
                        )
                );

        assertFalse(
                result.processed()
        );

        assertEquals(
                DocumentStatus.GENERATING,
                result.status()
        );

        verify(
                generateAccessKeyUseCase,
                never()
        ).generate(
                any()
        );

        verify(
                documentRepository,
                never()
        ).save(
                any()
        );
    }

    private Document queuedDocument() {

        Document document =
                Document.create(
                        DOCUMENT_ID,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        DocumentType.INVOICE,
                        FiscalEnvironment.TEST,
                        new IdempotencyKey(
                                "fiscal-worker-test"
                        ),
                        Instant.parse(
                                "2026-10-05T20:00:00Z"
                        ),
                        NOW.minusSeconds(
                                60
                        )
                );

        document.queue(
                NOW.minusSeconds(
                        30
                )
        );

        return document;
    }
}