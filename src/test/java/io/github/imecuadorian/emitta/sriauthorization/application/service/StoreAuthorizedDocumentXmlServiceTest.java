package io.github.imecuadorian.emitta.sriauthorization.application.service;

import io.github.imecuadorian.emitta.document.application.port.out.DocumentRepository;
import io.github.imecuadorian.emitta.document.domain.Document;
import io.github.imecuadorian.emitta.document.domain.DocumentStatus;
import io.github.imecuadorian.emitta.documentartifact.application.model.StoreDocumentArtifactCommand;
import io.github.imecuadorian.emitta.documentartifact.application.port.in.StoreDocumentArtifactUseCase;
import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifact;
import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifactType;
import io.github.imecuadorian.emitta.sriauthorization.application.model.SriAuthorizationResult;
import io.github.imecuadorian.emitta.sriauthorization.domain.SriAuthorizationStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoreAuthorizedDocumentXmlServiceTest {

    private static final UUID DOCUMENT_ID =
            UUID.fromString(
                    "11111111-2222-3333-4444-555555555555"
            );

    private static final String ACCESS_KEY =
            "2111202405176001321000110010010000001241234567810";

    private static final Instant AUTHORIZED_AT =
            Instant.parse("2026-10-08T01:00:00Z");

    private static final String XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <factura id="comprobante" version="2.1.0">
              <infoTributaria>
                <claveAcceso>%s</claveAcceso>
              </infoTributaria>
            </factura>
            """.formatted(ACCESS_KEY);

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private StoreDocumentArtifactUseCase storeDocumentArtifactUseCase;

    @Mock
    private Document document;

    @Mock
    private DocumentArtifact artifact;

    private StoreAuthorizedDocumentXmlService service;

    @BeforeEach
    void setUp() {

        service = new StoreAuthorizedDocumentXmlService(
                documentRepository,
                storeDocumentArtifactUseCase
        );
    }

    @Test
    void shouldStoreAuthorizedXml() {

        stubDocument(DocumentStatus.SUBMITTED);

        when(document.getAccessKey()).thenReturn(ACCESS_KEY);

        when(
                storeDocumentArtifactUseCase.store(
                        any(StoreDocumentArtifactCommand.class)
                )
        ).thenReturn(artifact);

        DocumentArtifact result =
                service.store(
                        DOCUMENT_ID,
                        authorizedResult(XML)
                );

        assertSame(artifact, result);

        ArgumentCaptor<StoreDocumentArtifactCommand> captor =
                ArgumentCaptor.forClass(
                        StoreDocumentArtifactCommand.class
                );

        verify(storeDocumentArtifactUseCase)
                .store(captor.capture());

        StoreDocumentArtifactCommand command =
                captor.getValue();

        assertEquals(DOCUMENT_ID, command.documentId());
        assertEquals(
                DocumentArtifactType.AUTHORIZED_XML,
                command.type()
        );
        assertEquals(
                "application/xml",
                command.contentType()
        );
        assertArrayEquals(
                XML.getBytes(StandardCharsets.UTF_8),
                command.content()
        );

        verify(documentRepository, never()).save(any());
    }

    @Test
    void shouldAllowStorageDuringRetryPendingRecovery() {

        stubDocument(DocumentStatus.RETRY_PENDING);
        when(document.getAccessKey()).thenReturn(ACCESS_KEY);

        when(
                storeDocumentArtifactUseCase.store(any())
        ).thenReturn(artifact);

        assertSame(
                artifact,
                service.store(DOCUMENT_ID, authorizedResult(XML))
        );
    }

    @Test
    void shouldRejectNotFoundAuthorization() {

        SriAuthorizationResult result =
                new SriAuthorizationResult(
                        SriAuthorizationStatus.NOT_FOUND,
                        null,
                        null,
                        null,
                        List.of()
                );

        assertThrows(
                IllegalStateException.class,
                () -> service.store(DOCUMENT_ID, result)
        );

        verifyNoInteractions(
                documentRepository,
                storeDocumentArtifactUseCase
        );
    }

    @Test
    void shouldRejectDifferentAuthorizationNumber() {

        stubDocument(DocumentStatus.SUBMITTED);
        when(document.getAccessKey()).thenReturn(ACCESS_KEY);

        SriAuthorizationResult result =
                new SriAuthorizationResult(
                        SriAuthorizationStatus.AUTHORIZED,
                        "0".repeat(49),
                        AUTHORIZED_AT,
                        XML,
                        List.of()
                );

        assertThrows(
                IllegalStateException.class,
                () -> service.store(DOCUMENT_ID, result)
        );

        verifyNoInteractions(storeDocumentArtifactUseCase);
    }

    @Test
    void shouldRejectDifferentEmbeddedAccessKey() {

        stubDocument(DocumentStatus.SUBMITTED);
        when(document.getAccessKey()).thenReturn(ACCESS_KEY);

        String modifiedXml =
                XML.replace(
                        ACCESS_KEY,
                        "0".repeat(49)
                );

        assertThrows(
                IllegalStateException.class,
                () -> service.store(
                        DOCUMENT_ID,
                        authorizedResult(modifiedXml)
                )
        );

        verifyNoInteractions(storeDocumentArtifactUseCase);
    }

    @Test
    void shouldRejectInvalidDocumentStatus() {

        stubDocument(DocumentStatus.SIGNED);

        assertThrows(
                IllegalStateException.class,
                () -> service.store(
                        DOCUMENT_ID,
                        authorizedResult(XML)
                )
        );

        verifyNoInteractions(storeDocumentArtifactUseCase);
    }

    @Test
    void shouldRejectMalformedXml() {

        stubDocument(DocumentStatus.SUBMITTED);
        when(document.getAccessKey()).thenReturn(ACCESS_KEY);

        assertThrows(
                IllegalStateException.class,
                () -> service.store(
                        DOCUMENT_ID,
                        authorizedResult(
                                "<factura><infoTributaria>"
                        )
                )
        );

        verifyNoInteractions(storeDocumentArtifactUseCase);
    }

    @Test
    void shouldRejectMissingAuthorizationDate() {

        SriAuthorizationResult result =
                new SriAuthorizationResult(
                        SriAuthorizationStatus.AUTHORIZED,
                        ACCESS_KEY,
                        null,
                        XML,
                        List.of()
                );

        assertThrows(
                IllegalStateException.class,
                () -> service.store(DOCUMENT_ID, result)
        );

        verifyNoInteractions(
                documentRepository,
                storeDocumentArtifactUseCase
        );
    }

    private SriAuthorizationResult authorizedResult(
            String xml
    ) {

        return new SriAuthorizationResult(
                SriAuthorizationStatus.AUTHORIZED,
                ACCESS_KEY,
                AUTHORIZED_AT,
                xml,
                List.of()
        );
    }

    private void stubDocument(
            DocumentStatus status
    ) {

        when(documentRepository.findById(DOCUMENT_ID))
                .thenReturn(Optional.of(document));

        when(document.getStatus())
                .thenReturn(status);
    }
}