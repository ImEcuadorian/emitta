package io.github.imecuadorian.emitta.fiscalsigning.application.service;

import io.github.imecuadorian.emitta.documentartifact.application.exception.DocumentArtifactNotFoundException;
import io.github.imecuadorian.emitta.documentartifact.application.model.LoadedDocumentArtifact;
import io.github.imecuadorian.emitta.documentartifact.application.model.StoreDocumentArtifactCommand;
import io.github.imecuadorian.emitta.documentartifact.application.port.in.LoadDocumentArtifactUseCase;
import io.github.imecuadorian.emitta.documentartifact.application.port.in.StoreDocumentArtifactUseCase;
import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifact;
import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifactType;

import io.github.imecuadorian.emitta.fiscalsigning.application.model.ResolvedSigningKeyMaterial;
import io.github.imecuadorian.emitta.fiscalsigning.application.model.SignedXml;
import io.github.imecuadorian.emitta.fiscalsigning.application.model.SigningKeyMaterial;
import io.github.imecuadorian.emitta.fiscalsigning.application.port.out.SigningKeyMaterialPort;
import io.github.imecuadorian.emitta.fiscalsigning.application.port.out.XmlSignerPort;

import io.github.imecuadorian.emitta.invoicexml.application.port.out.InvoiceXmlValidatorPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SignFiscalDocumentServiceTest {

    private static final UUID DOCUMENT_ID =
            UUID.fromString(
                    "11111111-2222-3333-4444-555555555555"
            );

    private static final UUID CERTIFICATE_ID =
            UUID.fromString(
                    "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
            );

    private static final Instant NOW =
            Instant.parse(
                    "2026-10-07T03:00:00Z"
            );

    @Mock
    private LoadDocumentArtifactUseCase
            loadDocumentArtifactUseCase;

    @Mock
    private StoreDocumentArtifactUseCase
            storeDocumentArtifactUseCase;

    @Mock
    private SigningKeyMaterialPort
            signingKeyMaterialPort;

    @Mock
    private XmlSignerPort
            xmlSignerPort;

    @Mock
    private InvoiceXmlValidatorPort
            invoiceXmlValidatorPort;

    private SignFiscalDocumentService service;

    @BeforeEach
    void setUp() {

        service =
                new SignFiscalDocumentService(
                        loadDocumentArtifactUseCase,
                        storeDocumentArtifactUseCase,
                        signingKeyMaterialPort,
                        xmlSignerPort,
                        invoiceXmlValidatorPort,
                        Clock.fixed(
                                NOW,
                                ZoneOffset.UTC
                        )
                );
    }

    @Test
    void shouldSignUnsignedXmlAndStoreSignedArtifact() {

        byte[] unsignedXml =
                "<factura/>"
                        .getBytes(
                                StandardCharsets.UTF_8
                        );

        byte[] signedXml =
                "<factura><Signature/></factura>"
                        .getBytes(
                                StandardCharsets.UTF_8
                        );

        DocumentArtifact unsignedMetadata =
                artifact(
                        DocumentArtifactType.UNSIGNED_XML,
                        "unsigned.xml",
                        unsignedXml.length
                );

        when(
                loadDocumentArtifactUseCase.load(
                        DOCUMENT_ID,
                        DocumentArtifactType.SIGNED_XML
                )
        ).thenThrow(
                new DocumentArtifactNotFoundException(
                        DOCUMENT_ID,
                        DocumentArtifactType.SIGNED_XML
                )
        );

        when(
                loadDocumentArtifactUseCase.load(
                        DOCUMENT_ID,
                        DocumentArtifactType.UNSIGNED_XML
                )
        ).thenReturn(
                new LoadedDocumentArtifact(
                        unsignedMetadata,
                        unsignedXml
                )
        );

        SigningKeyMaterial keyMaterial =
                new SigningKeyMaterial(
                        new byte[]{
                                1,
                                2,
                                3
                        },
                        "password".toCharArray(),
                        "emitta-test"
                );

        when(
                signingKeyMaterialPort.loadForDocument(
                        DOCUMENT_ID,
                        NOW
                )
        ).thenReturn(
                new ResolvedSigningKeyMaterial(
                        CERTIFICATE_ID,
                        keyMaterial
                )
        );

        when(
                xmlSignerPort.sign(
                        argThat(
                                content ->
                                        Arrays.equals(
                                                content,
                                                unsignedXml
                                        )
                        ),
                        org.mockito.ArgumentMatchers.same(
                                keyMaterial
                        )
                )
        ).thenReturn(
                new SignedXml(
                        signedXml
                )
        );

        DocumentArtifact storedArtifact =
                artifact(
                        DocumentArtifactType.SIGNED_XML,
                        "signed.xml",
                        signedXml.length
                );

        when(
                storeDocumentArtifactUseCase.store(
                        org.mockito.ArgumentMatchers.any()
                )
        ).thenReturn(
                storedArtifact
        );

        DocumentArtifact result =
                service.sign(
                        DOCUMENT_ID
                );

        verify(
                invoiceXmlValidatorPort
        ).validate(
                signedXml
        );

        assertSame(
                storedArtifact,
                result
        );

        ArgumentCaptor<StoreDocumentArtifactCommand> commandCaptor =
                ArgumentCaptor.forClass(
                        StoreDocumentArtifactCommand.class
                );

        verify(
                storeDocumentArtifactUseCase
        ).store(
                commandCaptor.capture()
        );

        StoreDocumentArtifactCommand command =
                commandCaptor.getValue();

        assertArrayEquals(
                signedXml,
                command.content()
        );
    }

    @Test
    void shouldReturnExistingSignedArtifactWithoutSigningAgain() {

        byte[] signedXml =
                "<signed/>"
                        .getBytes(
                                StandardCharsets.UTF_8
                        );

        DocumentArtifact signedArtifact =
                artifact(
                        DocumentArtifactType.SIGNED_XML,
                        "signed.xml",
                        signedXml.length
                );

        when(
                loadDocumentArtifactUseCase.load(
                        DOCUMENT_ID,
                        DocumentArtifactType.SIGNED_XML
                )
        ).thenReturn(
                new LoadedDocumentArtifact(
                        signedArtifact,
                        signedXml
                )
        );

        verify(
                invoiceXmlValidatorPort,
                never()
        ).validate(
                any()
        );

        DocumentArtifact result =
                service.sign(
                        DOCUMENT_ID
                );

        assertSame(
                signedArtifact,
                result
        );

        verify(
                signingKeyMaterialPort,
                never()
        ).loadForDocument(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );

        verify(
                xmlSignerPort,
                never()
        ).sign(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );

        verify(
                storeDocumentArtifactUseCase,
                never()
        ).store(
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void shouldFailWhenUnsignedXmlDoesNotExist() {

        when(
                loadDocumentArtifactUseCase.load(
                        DOCUMENT_ID,
                        DocumentArtifactType.SIGNED_XML
                )
        ).thenThrow(
                new DocumentArtifactNotFoundException(
                        DOCUMENT_ID,
                        DocumentArtifactType.SIGNED_XML
                )
        );

        when(
                loadDocumentArtifactUseCase.load(
                        DOCUMENT_ID,
                        DocumentArtifactType.UNSIGNED_XML
                )
        ).thenThrow(
                new DocumentArtifactNotFoundException(
                        DOCUMENT_ID,
                        DocumentArtifactType.UNSIGNED_XML
                )
        );

        assertThrows(
                DocumentArtifactNotFoundException.class,
                () ->
                        service.sign(
                                DOCUMENT_ID
                        )
        );

        verify(
                signingKeyMaterialPort,
                never()
        ).loadForDocument(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void shouldNotStoreSignedXmlWhenSchemaValidationFails() {

        byte[] unsignedBytes =
                "<factura/>"
                        .getBytes(
                                StandardCharsets.UTF_8
                        );

        byte[] signedBytes =
                "<factura><Signature/></factura>"
                        .getBytes(
                                StandardCharsets.UTF_8
                        );

        DocumentArtifact unsignedMetadata =
                artifact(
                        DocumentArtifactType.UNSIGNED_XML,
                        "unsigned.xml",
                        unsignedBytes.length
                );

        SigningKeyMaterial keyMaterial =
                new SigningKeyMaterial(
                        new byte[]{
                                1,
                                2,
                                3
                        },
                        "password".toCharArray(),
                        "emitta-test"
                );

        when(
                loadDocumentArtifactUseCase.load(
                        DOCUMENT_ID,
                        DocumentArtifactType.SIGNED_XML
                )
        ).thenThrow(
                new DocumentArtifactNotFoundException(
                        DOCUMENT_ID,
                        DocumentArtifactType.SIGNED_XML
                )
        );

        when(
                loadDocumentArtifactUseCase.load(
                        DOCUMENT_ID,
                        DocumentArtifactType.UNSIGNED_XML
                )
        ).thenReturn(
                new LoadedDocumentArtifact(
                        unsignedMetadata,
                        unsignedBytes
                )
        );

        when(
                signingKeyMaterialPort.loadForDocument(
                        eq(
                                DOCUMENT_ID
                        ),
                        eq(
                                NOW
                        )
                )
        ).thenReturn(
                new ResolvedSigningKeyMaterial(
                        CERTIFICATE_ID,
                        keyMaterial
                )
        );

        when(
                xmlSignerPort.sign(
                        argThat(
                                content ->
                                        Arrays.equals(
                                                content,
                                                unsignedBytes
                                        )
                        ),
                        same(
                                keyMaterial
                        )
                )
        ).thenReturn(
                new SignedXml(
                        signedBytes
                )
        );

        RuntimeException failure =
                new RuntimeException(
                        "Signed XML does not conform to SRI schema"
                );

        doThrow(
                failure
        ).when(
                invoiceXmlValidatorPort
        ).validate(
                signedBytes
        );

        RuntimeException thrown =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                service.sign(
                                        DOCUMENT_ID
                                )
                );

        assertSame(
                failure,
                thrown
        );

        verify(
                invoiceXmlValidatorPort
        ).validate(
                signedBytes
        );

        verify(
                storeDocumentArtifactUseCase,
                never()
        ).store(
                any()
        );
    }

    private static DocumentArtifact artifact(
            DocumentArtifactType type,
            String fileName,
            long size
    ) {

        return new DocumentArtifact(
                UUID.randomUUID(),
                DOCUMENT_ID,
                type,
                "application/xml",
                "documents/"
                        + DOCUMENT_ID
                        + "/"
                        + fileName,
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                        + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                size,
                NOW
        );
    }
}