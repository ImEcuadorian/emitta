package io.github.imecuadorian.emitta.invoicexml.application.service;

import io.github.imecuadorian.emitta.invoicexml.application.model.InvoiceXmlData;
import io.github.imecuadorian.emitta.invoicexml.application.model.InvoiceXmlSourceData;
import io.github.imecuadorian.emitta.invoicexml.application.port.out.InvoiceXmlGeneratorPort;
import io.github.imecuadorian.emitta.invoicexml.application.port.out.InvoiceXmlSourcePort;
import io.github.imecuadorian.emitta.invoicexml.domain.GeneratedInvoiceXml;
import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenerateInvoiceXmlServiceTest {

    private static final UUID DOCUMENT_ID =
            UUID.randomUUID();

    private static final String ACCESS_KEY =
            "0610202601179001234500110010010000000028055561612";

    @Mock
    private InvoiceXmlSourcePort sourcePort;

    @Mock
    private InvoiceXmlGeneratorPort generatorPort;

    private GenerateInvoiceXmlService service;

    @BeforeEach
    void setUp() {

        service =
                new GenerateInvoiceXmlService(
                        sourcePort,
                        generatorPort,
                        ignored -> io.github.imecuadorian.emitta.support.TestProviderPolicies.external("1799999999001"),
                        ZoneId.of(
                                "America/Guayaquil"
                        )
                );
    }

    @Test
    void shouldBuildInvoiceXmlDataAndGenerateXml() {

        when(
                sourcePort.findByDocumentId(
                        DOCUMENT_ID
                )
        ).thenReturn(
                Optional.of(
                        source()
                )
        );

        GeneratedInvoiceXml generated =
                new GeneratedInvoiceXml(
                        "<factura/>"
                );

        when(
                generatorPort.generate(
                        any()
                )
        ).thenReturn(
                generated
        );

        service.generate(
                DOCUMENT_ID
        );

        ArgumentCaptor<InvoiceXmlData> captor =
                ArgumentCaptor.forClass(
                        InvoiceXmlData.class
                );

        verify(
                generatorPort
        ).generate(
                captor.capture()
        );

        InvoiceXmlData data =
                captor.getValue();

        assertEquals(
                "1",
                data.environmentCode()
        );

        assertEquals(
                "000000002",
                data.sequential()
        );

        assertEquals(
                LocalDate.of(
                        2026,
                        10,
                        6
                ),
                data.issueDate()
        );

        assertEquals(
                ACCESS_KEY,
                data.accessKey()
        );

        assertEquals(
                new BigDecimal(
                        "18.00"
                ),
                data.totalWithoutTaxes()
        );

        assertEquals(
                new BigDecimal(
                        "20.70"
                ),
                data.total()
        );

        assertEquals(
                1,
                data.taxTotals()
                        .size()
        );

        assertEquals(
                new BigDecimal(
                        "18.00"
                ),
                data.taxTotals()
                        .getFirst()
                        .taxableBase()
        );

        assertEquals(
                new BigDecimal(
                        "2.70"
                ),
                data.taxTotals()
                        .getFirst()
                        .amount()
        );

        assertEquals(
                "1799999999001",
                data.providerRuc()
        );
    }

    @Test
    void shouldBlockUnresolvedScenarioBeforeXmlGeneration() {
        when(sourcePort.findByDocumentId(DOCUMENT_ID)).thenReturn(Optional.of(source()));
        var blocked = new GenerateInvoiceXmlService(sourcePort, generatorPort,
                ignored -> io.github.imecuadorian.emitta.shared.fiscal.ProviderPolicy.unresolved(),
                ZoneId.of("America/Guayaquil"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> blocked.generate(DOCUMENT_ID));
        org.mockito.Mockito.verifyNoInteractions(generatorPort);
    }

    private static InvoiceXmlSourceData source() {

        return new InvoiceXmlSourceData(

                FiscalEnvironment.TEST,

                "FAXORF LOCAL DEVELOPMENT S.A.S.",
                "FAXORF",
                "1790012345001",
                "Quito, Ecuador",

                ACCESS_KEY,
                2,
                Instant.parse(
                        "2026-10-06T23:00:00Z"
                ),

                "001",
                "Quito, Ecuador",
                "001",

                new InvoiceXmlSourceData.Buyer(
                        "07",
                        "9999999999999",
                        "CONSUMIDOR FINAL",
                        null
                ),

                new BigDecimal(
                        "18.00"
                ),

                new BigDecimal(
                        "2.00"
                ),

                new BigDecimal(
                        "20.70"
                ),

                "DOLAR",

                List.of(
                        new InvoiceXmlSourceData.Item(
                                "P001",
                                "Producto de prueba",
                                new BigDecimal(
                                        "2.000000"
                                ),
                                new BigDecimal(
                                        "10.000000"
                                ),
                                new BigDecimal(
                                        "2.00"
                                ),
                                new BigDecimal(
                                        "18.00"
                                ),
                                List.of(
                                        new InvoiceXmlSourceData.ItemTax(
                                                "2",
                                                "4",
                                                new BigDecimal(
                                                        "15.0000"
                                                ),
                                                new BigDecimal(
                                                        "18.00"
                                                ),
                                                new BigDecimal(
                                                        "2.70"
                                                )
                                        )
                                )
                        )
                ),

                List.of(
                        new InvoiceXmlSourceData.Payment(
                                "01",
                                new BigDecimal(
                                        "20.70"
                                ),
                                null,
                                null
                        )
                )
        );
    }
}
