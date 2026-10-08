package io.github.imecuadorian.emitta.srireception.application.service;

import io.github.imecuadorian.emitta.document.application.port.out.DocumentRepository;
import io.github.imecuadorian.emitta.document.domain.Document;
import io.github.imecuadorian.emitta.document.domain.DocumentStatus;

import io.github.imecuadorian.emitta.documentartifact.application.model.LoadedDocumentArtifact;
import io.github.imecuadorian.emitta.documentartifact.application.port.in.LoadDocumentArtifactUseCase;
import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifact;
import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifactType;

import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;

import io.github.imecuadorian.emitta.srireception.application.model.SriReceptionRequest;
import io.github.imecuadorian.emitta.srireception.application.model.SriReceptionResult;
import io.github.imecuadorian.emitta.srireception.application.port.out.SriReceptionPort;
import io.github.imecuadorian.emitta.srireception.domain.SriReceptionStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmitSignedDocumentToSriServiceTest {

    private static final UUID DOCUMENT_ID =
            UUID.fromString(
                    "11111111-2222-3333-4444-555555555555"
            );

    private static final Instant NOW =
            Instant.parse(
                    "2026-10-07T20:00:00Z"
            );

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private LoadDocumentArtifactUseCase
            loadDocumentArtifactUseCase;

    @Mock
    private SriReceptionPort sriReceptionPort;

    @Mock
    private Document document;

    private SubmitSignedDocumentToSriService service;

    @BeforeEach
    void setUp() {

        service =
                new SubmitSignedDocumentToSriService(
                        documentRepository,
                        loadDocumentArtifactUseCase,
                        sriReceptionPort
                );
    }

    @Test
    void shouldSubmitSignedXmlToSriUsingDocumentEnvironment() {

        byte[] signedXml =
                "<factura id=\"comprobante\"/>"
                        .getBytes(
                                StandardCharsets.UTF_8
                        );

        when(
                documentRepository.findById(
                        DOCUMENT_ID
                )
        ).thenReturn(
                Optional.of(
                        document
                )
        );

        when(
                document.getStatus()
        ).thenReturn(
                DocumentStatus.SIGNED
        );

        when(
                document.getEnvironment()
        ).thenReturn(
                FiscalEnvironment.TEST
        );

        DocumentArtifact metadata =
                new DocumentArtifact(
                        UUID.randomUUID(),
                        DOCUMENT_ID,
                        DocumentArtifactType.SIGNED_XML,
                        "application/xml",
                        "documents/"
                                + DOCUMENT_ID
                                + "/signed.xml",
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                                + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        signedXml.length,
                        NOW
                );

        when(
                loadDocumentArtifactUseCase.load(
                        DOCUMENT_ID,
                        DocumentArtifactType.SIGNED_XML
                )
        ).thenReturn(
                new LoadedDocumentArtifact(
                        metadata,
                        signedXml
                )
        );

        SriReceptionResult sriResult =
                new SriReceptionResult(
                        SriReceptionStatus.RECEIVED,
                        List.of()
                );

        when(
                sriReceptionPort.submit(
                        org.mockito.ArgumentMatchers.any()
                )
        ).thenReturn(
                sriResult
        );

        SriReceptionResult result =
                service.submit(
                        DOCUMENT_ID
                );

        assertSame(
                sriResult,
                result
        );

        ArgumentCaptor<SriReceptionRequest> requestCaptor =
                ArgumentCaptor.forClass(
                        SriReceptionRequest.class
                );

        verify(
                sriReceptionPort
        ).submit(
                requestCaptor.capture()
        );

        SriReceptionRequest request =
                requestCaptor.getValue();

        assertEquals(
                FiscalEnvironment.TEST,
                request.environment()
        );

        org.junit.jupiter.api.Assertions.assertTrue(
                Arrays.equals(
                        signedXml,
                        request.signedXml()
                )
        );
    }

    @Test
    void shouldReturnSriReturnedOutcomeWithoutChangingIt() {

        when(
                documentRepository.findById(
                        DOCUMENT_ID
                )
        ).thenReturn(
                Optional.of(
                        document
                )
        );

        when(
                document.getStatus()
        ).thenReturn(
                DocumentStatus.SIGNED
        );

        when(
                document.getEnvironment()
        ).thenReturn(
                FiscalEnvironment.TEST
        );

        byte[] signedXml =
                "<factura/>"
                        .getBytes(
                                StandardCharsets.UTF_8
                        );

        DocumentArtifact metadata =
                new DocumentArtifact(
                        UUID.randomUUID(),
                        DOCUMENT_ID,
                        DocumentArtifactType.SIGNED_XML,
                        "application/xml",
                        "documents/"
                                + DOCUMENT_ID
                                + "/signed.xml",
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                                + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        signedXml.length,
                        NOW
                );

        when(
                loadDocumentArtifactUseCase.load(
                        DOCUMENT_ID,
                        DocumentArtifactType.SIGNED_XML
                )
        ).thenReturn(
                new LoadedDocumentArtifact(
                        metadata,
                        signedXml
                )
        );

        SriReceptionResult returned =
                new SriReceptionResult(
                        SriReceptionStatus.RETURNED,
                        List.of()
                );

        when(
                sriReceptionPort.submit(
                        org.mockito.ArgumentMatchers.any()
                )
        ).thenReturn(
                returned
        );

        SriReceptionResult result =
                service.submit(
                        DOCUMENT_ID
                );

        assertSame(
                returned,
                result
        );
    }

    @Test
    void shouldRejectDocumentOutsideSignedState() {

        when(
                documentRepository.findById(
                        DOCUMENT_ID
                )
        ).thenReturn(
                Optional.of(
                        document
                )
        );

        when(
                document.getStatus()
        ).thenReturn(
                DocumentStatus.GENERATING
        );

        assertThrows(
                IllegalStateException.class,
                () ->
                        service.submit(
                                DOCUMENT_ID
                        )
        );

        verify(
                loadDocumentArtifactUseCase,
                never()
        ).load(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );

        verify(
                sriReceptionPort,
                never()
        ).submit(
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void shouldFailWhenDocumentDoesNotExist() {

        when(
                documentRepository.findById(
                        DOCUMENT_ID
                )
        ).thenReturn(
                Optional.empty()
        );

        assertThrows(
                IllegalStateException.class,
                () ->
                        service.submit(
                                DOCUMENT_ID
                        )
        );

        verify(
                sriReceptionPort,
                never()
        ).submit(
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void shouldSubmitAlreadySubmittedDocument() {

        byte[] signedXml =
                "<factura/>"
                        .getBytes(
                                StandardCharsets.UTF_8
                        );

        when(
                documentRepository.findById(
                        DOCUMENT_ID
                )
        ).thenReturn(
                Optional.of(
                        document
                )
        );

        when(
                document.getStatus()
        ).thenReturn(
                DocumentStatus.SUBMITTED
        );

        when(
                document.getEnvironment()
        ).thenReturn(
                FiscalEnvironment.TEST
        );

        DocumentArtifact metadata =
                new DocumentArtifact(
                        UUID.randomUUID(),
                        DOCUMENT_ID,
                        DocumentArtifactType.SIGNED_XML,
                        "application/xml",
                        "documents/"
                                + DOCUMENT_ID
                                + "/signed.xml",
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                                + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        signedXml.length,
                        NOW
                );

        when(
                loadDocumentArtifactUseCase.load(
                        DOCUMENT_ID,
                        DocumentArtifactType.SIGNED_XML
                )
        ).thenReturn(
                new LoadedDocumentArtifact(
                        metadata,
                        signedXml
                )
        );

        SriReceptionResult sriResult =
                new SriReceptionResult(
                        SriReceptionStatus.RECEIVED,
                        List.of()
                );

        when(
                sriReceptionPort.submit(
                        org.mockito.ArgumentMatchers.any()
                )
        ).thenReturn(
                sriResult
        );

        SriReceptionResult result =
                service.submit(
                        DOCUMENT_ID
                );

        assertSame(
                sriResult,
                result
        );

        verify(
                sriReceptionPort
        ).submit(
                org.mockito.ArgumentMatchers.any()
        );
    }
}