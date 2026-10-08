package io.github.imecuadorian.emitta.sriauthorization.application.service;

import io.github.imecuadorian.emitta.document.application.port.out.DocumentRepository;
import io.github.imecuadorian.emitta.document.domain.Document;
import io.github.imecuadorian.emitta.documentartifact.application.port.in.LoadDocumentArtifactUseCase;
import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifact;
import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifactType;

import io.github.imecuadorian.emitta.fiscalsigning.application.exception.XmlSignatureVerificationException;
import io.github.imecuadorian.emitta.fiscalsigning.application.port.out.XmlSignatureVerifierPort;
import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;
import io.github.imecuadorian.emitta.sriauthorization.application.model.SriAuthorizationEvidence;
import io.github.imecuadorian.emitta.sriauthorization.application.model.SriAuthorizationResult;
import io.github.imecuadorian.emitta.sriauthorization.application.port.in.MarkDocumentAuthorizedUseCase;
import io.github.imecuadorian.emitta.sriauthorization.application.port.in.QueryDocumentAuthorizationUseCase;
import io.github.imecuadorian.emitta.sriauthorization.application.port.in.StoreAuthorizedDocumentXmlUseCase;
import io.github.imecuadorian.emitta.sriauthorization.application.port.in.VerifyAuthorizedXmlUseCase;
import io.github.imecuadorian.emitta.sriauthorization.application.port.out.SriAuthorizationEvidencePort;
import io.github.imecuadorian.emitta.sriauthorization.domain.SriAuthorizationStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Answers;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReconcileDocumentAuthorizationServiceTest {

    private static final UUID DOCUMENT_ID =
            UUID.fromString(
                    "11111111-2222-3333-4444-555555555555"
            );

    private static final String ACCESS_KEY =
            "2111202405176001321000110010010000001241234567810";

    private static final String XML = """
            <factura id="comprobante" version="2.1.0">
              <infoTributaria>
                <claveAcceso>%s</claveAcceso>
              </infoTributaria>
            </factura>
            """.formatted(ACCESS_KEY);

    private static final byte[] SIGNED_XML =
            XML.getBytes(StandardCharsets.UTF_8);

    @Mock
    private QueryDocumentAuthorizationUseCase
            queryDocumentAuthorizationUseCase;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private LoadDocumentArtifactUseCase
            loadDocumentArtifactUseCase;

    @Mock
    private VerifyAuthorizedXmlUseCase
            verifyAuthorizedXmlUseCase;

    @Mock
    private StoreAuthorizedDocumentXmlUseCase
            storeAuthorizedDocumentXmlUseCase;

    @Mock
    private MarkDocumentAuthorizedUseCase
            markDocumentAuthorizedUseCase;

    @Mock
    private DocumentArtifact storedArtifact;

    @Mock
    private XmlSignatureVerifierPort xmlSignatureVerifierPort;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private Document document;

    @Mock
    private SriAuthorizationEvidencePort sriAuthorizationEvidencePort;

    private ReconcileDocumentAuthorizationService service;

    @BeforeEach
    void setUp() {

        service =
                new ReconcileDocumentAuthorizationService(
                        queryDocumentAuthorizationUseCase,
                        loadDocumentArtifactUseCase,
                        verifyAuthorizedXmlUseCase,
                        storeAuthorizedDocumentXmlUseCase,
                        xmlSignatureVerifierPort,
                        markDocumentAuthorizedUseCase,
                        documentRepository,
                        sriAuthorizationEvidencePort
                );
    }

    @Test
    void shouldStoreEvidenceBeforeMarkingDocumentAuthorized() {

        SriAuthorizationResult authorization =
                authorizedResult();

        when(
                queryDocumentAuthorizationUseCase.query(
                        DOCUMENT_ID
                )
        ).thenReturn(authorization);

        when(
                loadDocumentArtifactUseCase.load(
                        DOCUMENT_ID,
                        DocumentArtifactType.SIGNED_XML
                ).content()
        ).thenReturn(SIGNED_XML);

        when(
                storeAuthorizedDocumentXmlUseCase.store(
                        DOCUMENT_ID,
                        authorization
                )
        ).thenReturn(storedArtifact);

        when(storedArtifact.sha256())
                .thenReturn("a".repeat(64));

        when(documentRepository.findById(DOCUMENT_ID))
                .thenReturn(Optional.of(document));

        when(document.getEnvironment())
                .thenReturn(FiscalEnvironment.TEST);

        /*
         * Simulate successful persistence by returning
         * the same evidence received by the repository.
         */
        when(sriAuthorizationEvidencePort.save(
                any(SriAuthorizationEvidence.class)
        )).thenAnswer(invocation -> invocation.getArgument(0));

        SriAuthorizationResult result =
                service.reconcile(DOCUMENT_ID);

        assertSame(authorization, result);

        InOrder order = inOrder(
                queryDocumentAuthorizationUseCase,
                loadDocumentArtifactUseCase,
                xmlSignatureVerifierPort,
                verifyAuthorizedXmlUseCase,
                storeAuthorizedDocumentXmlUseCase,
                markDocumentAuthorizedUseCase,
                documentRepository,
                sriAuthorizationEvidencePort
        );
        order.verify(
                queryDocumentAuthorizationUseCase
        ).query(DOCUMENT_ID);

        order.verify(
                loadDocumentArtifactUseCase
        ).load(
                DOCUMENT_ID,
                DocumentArtifactType.SIGNED_XML
        );

        order.verify(
                xmlSignatureVerifierPort
        ).verify(SIGNED_XML);

        order.verify(
                verifyAuthorizedXmlUseCase
        ).verify(
                SIGNED_XML,
                authorization
        );

        order.verify(
                storeAuthorizedDocumentXmlUseCase
        ).store(
                DOCUMENT_ID,
                authorization
        );

        order.verify(documentRepository)
                .findById(DOCUMENT_ID);

        order.verify(sriAuthorizationEvidencePort)
                .save(
                        new SriAuthorizationEvidence(
                                DOCUMENT_ID,
                                ACCESS_KEY,
                                authorization.authorizedAt(),
                                FiscalEnvironment.TEST,
                                "a".repeat(64),
                                authorization.messages()
                        )
                );

        order.verify(
                markDocumentAuthorizedUseCase
        ).markAuthorized(DOCUMENT_ID);
    }


    @Test
    void shouldRecoverWhenFinalAuthorizationUpdateFails() {

        SriAuthorizationResult authorization =
                authorizedResult();

        stubAuthorizedQueryAndSignedXml(authorization);

        when(storeAuthorizedDocumentXmlUseCase.store(
                DOCUMENT_ID,
                authorization
        )).thenReturn(storedArtifact);

        when(storedArtifact.sha256())
                .thenReturn("a".repeat(64));

        when(documentRepository.findById(DOCUMENT_ID))
                .thenReturn(Optional.of(document));

        when(document.getEnvironment())
                .thenReturn(FiscalEnvironment.TEST);

        /*
         * Simulate idempotent evidence persistence.
         */
        when(sriAuthorizationEvidencePort.save(
                any(SriAuthorizationEvidence.class)
        )).thenAnswer(invocation -> invocation.getArgument(0));

        /*
         * First attempt fails during document finalization.
         * Second attempt succeeds.
         */
        IllegalStateException failure =
                new IllegalStateException(
                        "Temporary database failure"
                );

        when(markDocumentAuthorizedUseCase.markAuthorized(DOCUMENT_ID))
                .thenThrow(failure)
                .thenReturn(document);

        IllegalStateException thrown =
                assertThrows(
                        IllegalStateException.class,
                        () -> service.reconcile(DOCUMENT_ID)
                );

        assertSame(failure, thrown);

        /*
         * Retry the authorization reconciliation.
         */
        SriAuthorizationResult recovered =
                assertDoesNotThrow(
                        () -> service.reconcile(DOCUMENT_ID)
                );

        assertSame(authorization, recovered);

        verify(storeAuthorizedDocumentXmlUseCase, times(2))
                .store(DOCUMENT_ID, authorization);

        verify(sriAuthorizationEvidencePort, times(2))
                .save(any(SriAuthorizationEvidence.class));

        verify(markDocumentAuthorizedUseCase, times(2))
                .markAuthorized(DOCUMENT_ID);
    }


    @Test
    void shouldKeepDocumentPendingWhenSriHasNoRecords() {

        SriAuthorizationResult authorization =
                new SriAuthorizationResult(
                        SriAuthorizationStatus.NOT_FOUND,
                        null,
                        null,
                        null,
                        List.of()
                );

        when(
                queryDocumentAuthorizationUseCase.query(
                        DOCUMENT_ID
                )
        ).thenReturn(authorization);

        assertSame(
                authorization,
                service.reconcile(DOCUMENT_ID)
        );

        verifyNoFurtherProcessing();
    }

    @Test
    void shouldNotAuthorizeWhenSriReportsNotAuthorized() {

        SriAuthorizationResult authorization =
                new SriAuthorizationResult(
                        SriAuthorizationStatus.NOT_AUTHORIZED,
                        null,
                        null,
                        null,
                        List.of()
                );

        when(
                queryDocumentAuthorizationUseCase.query(
                        DOCUMENT_ID
                )
        ).thenReturn(authorization);

        assertSame(
                authorization,
                service.reconcile(DOCUMENT_ID)
        );

        verifyNoFurtherProcessing();
    }

    @Test
    void shouldNotStoreOrAuthorizeWhenVerificationFails() {

        SriAuthorizationResult authorization =
                authorizedResult();

        stubAuthorizedQueryAndSignedXml(authorization);

        IllegalStateException failure =
                new IllegalStateException(
                        "SRI XML differs from original"
                );

        doThrow(failure)
                .when(verifyAuthorizedXmlUseCase)
                .verify(
                        SIGNED_XML,
                        authorization
                );

        IllegalStateException thrown =
                assertThrows(
                        IllegalStateException.class,
                        () -> service.reconcile(DOCUMENT_ID)
                );

        assertSame(failure, thrown);

        verifyNoInteractions(
                storeAuthorizedDocumentXmlUseCase,
                markDocumentAuthorizedUseCase
        );
    }

    @Test
    void shouldNotAuthorizeWhenArtifactStorageFails() {

        SriAuthorizationResult authorization =
                authorizedResult();

        stubAuthorizedQueryAndSignedXml(authorization);

        IllegalStateException failure =
                new IllegalStateException(
                        "Object storage unavailable"
                );

        when(
                storeAuthorizedDocumentXmlUseCase.store(
                        DOCUMENT_ID,
                        authorization
                )
        ).thenThrow(failure);

        IllegalStateException thrown =
                assertThrows(
                        IllegalStateException.class,
                        () -> service.reconcile(DOCUMENT_ID)
                );

        assertSame(failure, thrown);

        verify(
                markDocumentAuthorizedUseCase,
                never()
        ).markAuthorized(DOCUMENT_ID);
    }

    @Test
    void shouldStopWhenAuthorizationQueryFails() {

        RuntimeException failure =
                new RuntimeException(
                        "SRI unavailable"
                );

        when(
                queryDocumentAuthorizationUseCase.query(
                        DOCUMENT_ID
                )
        ).thenThrow(failure);

        RuntimeException thrown =
                assertThrows(
                        RuntimeException.class,
                        () -> service.reconcile(DOCUMENT_ID)
                );

        assertSame(failure, thrown);

        verifyNoFurtherProcessing();
    }

    @Test
    void shouldRejectNullDocumentId() {

        assertThrows(
                NullPointerException.class,
                () -> service.reconcile(null)
        );

        verifyNoInteractions(
                queryDocumentAuthorizationUseCase,
                loadDocumentArtifactUseCase,
                verifyAuthorizedXmlUseCase,
                storeAuthorizedDocumentXmlUseCase,
                markDocumentAuthorizedUseCase
        );
    }

    @Test
    void shouldNotAuthorizeWhenCryptographicSignatureIsInvalid() {

        SriAuthorizationResult authorization =
                authorizedResult();

        stubAuthorizedQueryAndSignedXml(
                authorization
        );

        XmlSignatureVerificationException failure =
                new XmlSignatureVerificationException(
                        "Invalid XAdES signature"
                );

        doThrow(failure)
                .when(xmlSignatureVerifierPort)
                .verify(SIGNED_XML);

        XmlSignatureVerificationException thrown =
                assertThrows(
                        XmlSignatureVerificationException.class,
                        () -> service.reconcile(DOCUMENT_ID)
                );

        assertSame(failure, thrown);

        verifyNoInteractions(
                verifyAuthorizedXmlUseCase,
                storeAuthorizedDocumentXmlUseCase,
                markDocumentAuthorizedUseCase
        );
    }

    private void stubAuthorizedQueryAndSignedXml(
            SriAuthorizationResult authorization
    ) {

        when(
                queryDocumentAuthorizationUseCase.query(
                        DOCUMENT_ID
                )
        ).thenReturn(authorization);

        when(
                loadDocumentArtifactUseCase.load(
                        DOCUMENT_ID,
                        DocumentArtifactType.SIGNED_XML
                ).content()
        ).thenReturn(SIGNED_XML);
    }

    private SriAuthorizationResult authorizedResult() {

        return new SriAuthorizationResult(
                SriAuthorizationStatus.AUTHORIZED,
                ACCESS_KEY,
                Instant.parse("2026-10-08T01:00:00Z"),
                XML,
                List.of()
        );
    }

    private void verifyNoFurtherProcessing() {

        verifyNoInteractions(
                loadDocumentArtifactUseCase,
                xmlSignatureVerifierPort,
                verifyAuthorizedXmlUseCase,
                storeAuthorizedDocumentXmlUseCase,
                markDocumentAuthorizedUseCase
        );
    }
}