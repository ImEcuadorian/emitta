package io.github.imecuadorian.emitta.invoicexml.application.service;

import io.github.imecuadorian.emitta.documentartifact.application.model.StoreDocumentArtifactCommand;
import io.github.imecuadorian.emitta.documentartifact.application.port.in.StoreDocumentArtifactUseCase;
import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifact;
import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifactType;
import io.github.imecuadorian.emitta.invoicexml.application.exception.InvalidInvoiceXmlException;
import io.github.imecuadorian.emitta.invoicexml.application.port.in.GenerateInvoiceXmlUseCase;
import io.github.imecuadorian.emitta.invoicexml.application.port.out.InvoiceXmlValidatorPort;

import io.github.imecuadorian.emitta.invoicexml.domain.GeneratedInvoiceXml;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenerateAndStoreInvoiceXmlServiceTest {

    private static final UUID DOCUMENT_ID =
            UUID.fromString(
                    "77777777-7777-7777-7777-777777777777"
            );

    @Mock
    private GenerateInvoiceXmlUseCase
            generateInvoiceXmlUseCase;

    @Mock
    private InvoiceXmlValidatorPort
            validatorPort;

    @Mock
    private StoreDocumentArtifactUseCase
            storeDocumentArtifactUseCase;

    @Mock
    private GeneratedInvoiceXml generatedInvoiceXml;

    @Mock
    private DocumentArtifact storedArtifact;

    private GenerateAndStoreInvoiceXmlService service;

    @BeforeEach
    void setUp() {

        service =
                new GenerateAndStoreInvoiceXmlService(
                        generateInvoiceXmlUseCase,
                        validatorPort,
                        storeDocumentArtifactUseCase
                );
    }

    @Test
    void shouldGenerateValidateAndStoreUnsignedXml() {

        byte[] xml =
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <factura id="comprobante" version="2.1.0"/>
                """
                        .getBytes(
                                StandardCharsets.UTF_8
                        );

        when(
                generateInvoiceXmlUseCase.generate(
                        DOCUMENT_ID
                )
        ).thenReturn(
                generatedInvoiceXml
        );

        when(
                generatedInvoiceXml.bytes()
        ).thenReturn(
                xml
        );

        when(
                storeDocumentArtifactUseCase.store(
                        any(
                                StoreDocumentArtifactCommand.class
                        )
                )
        ).thenReturn(
                storedArtifact
        );

        DocumentArtifact result =
                service.generateAndStore(
                        DOCUMENT_ID
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

        assertEquals(
                DOCUMENT_ID,
                command.documentId()
        );

        assertEquals(
                DocumentArtifactType.UNSIGNED_XML,
                command.type()
        );

        assertEquals(
                "application/xml",
                command.contentType()
        );

        assertArrayEquals(
                xml,
                command.content()
        );

        InOrder order =
                inOrder(
                        generateInvoiceXmlUseCase,
                        validatorPort,
                        storeDocumentArtifactUseCase
                );

        order.verify(
                generateInvoiceXmlUseCase
        ).generate(
                DOCUMENT_ID
        );

        order.verify(
                validatorPort
        ).validate(
                xml
        );

        order.verify(
                storeDocumentArtifactUseCase
        ).store(
                any(
                        StoreDocumentArtifactCommand.class
                )
        );
    }

    @Test
    void shouldNotStoreArtifactWhenXmlValidationFails() {

        byte[] xml =
                "<invalid/>"
                        .getBytes(
                                StandardCharsets.UTF_8
                        );

        when(
                generateInvoiceXmlUseCase.generate(
                        DOCUMENT_ID
                )
        ).thenReturn(
                generatedInvoiceXml
        );

        when(
                generatedInvoiceXml.bytes()
        ).thenReturn(
                xml
        );

        InvalidInvoiceXmlException failure =
                new InvalidInvoiceXmlException(
                        "Invalid invoice XML",
                        new IllegalArgumentException(
                                "XSD validation failed"
                        )
                );

        doThrow(
                failure
        ).when(
                validatorPort
        ).validate(
                xml
        );

        InvalidInvoiceXmlException thrown =
                assertThrows(
                        InvalidInvoiceXmlException.class,
                        () ->
                                service.generateAndStore(
                                        DOCUMENT_ID
                                )
                );

        assertSame(
                failure,
                thrown
        );

        verify(
                storeDocumentArtifactUseCase,
                never()
        ).store(
                any()
        );
    }
}