package io.github.imecuadorian.emitta.invoice.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InvoiceTest {

    @Test
    void shouldCalculateAuthoritativeInvoiceTotals() {

        InvoiceTaxSpec iva15 =
                new InvoiceTaxSpec(
                        "2",
                        "4",
                        new BigDecimal(
                                "15"
                        )
                );

        InvoiceItem item =
                InvoiceItem.create(
                        UUID.randomUUID(),
                        1,
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
                                iva15
                        ),
                        UUID::randomUUID
                );

        assertEquals(
                new BigDecimal(
                        "18.00"
                ),
                item.getSubtotal()
        );

        assertEquals(
                new BigDecimal(
                        "2.70"
                ),
                item.getTaxTotal()
        );

        assertEquals(
                new BigDecimal(
                        "20.70"
                ),
                item.getTotal()
        );

        InvoicePayment payment =
                new InvoicePayment(
                        UUID.randomUUID(),
                        1,
                        "01",
                        new BigDecimal(
                                "20.70"
                        ),
                        null,
                        null
                );

        Invoice invoice =
                Invoice.create(
                        UUID.randomUUID(),
                        null,
                        new BuyerSnapshot(
                                "07",
                                "9999999999999",
                                "CONSUMIDOR FINAL",
                                null,
                                null
                        ),
                        List.of(
                                item
                        ),
                        List.of(
                                payment
                        ),
                        "DOLAR"
                );

        assertEquals(
                new BigDecimal(
                        "18.00"
                ),
                invoice.getSubtotal()
        );

        assertEquals(
                new BigDecimal(
                        "2.00"
                ),
                invoice.getDiscountTotal()
        );

        assertEquals(
                new BigDecimal(
                        "2.70"
                ),
                invoice.getTaxTotal()
        );

        assertEquals(
                new BigDecimal(
                        "20.70"
                ),
                invoice.getTotal()
        );
    }

    @Test
    void shouldSupportSixDecimalQuantityAndUnitPrice() {

        InvoiceItem item =
                InvoiceItem.create(
                        UUID.randomUUID(),
                        1,
                        "125BJC-01",
                        "DERIVADOS PETROLEO",
                        new BigDecimal(
                                "2.542563"
                        ),
                        new BigDecimal(
                                "25.542365"
                        ),
                        BigDecimal.ZERO,
                        List.of(
                                new InvoiceTaxSpec(
                                        "2",
                                        "0",
                                        BigDecimal.ZERO
                                )
                        ),
                        UUID::randomUUID
                );

        assertEquals(
                new BigDecimal(
                        "64.94"
                ),
                item.getSubtotal()
        );
    }

    @Test
    void shouldRejectDiscountGreaterThanGrossAmount() {

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        InvoiceItem.create(
                                UUID.randomUUID(),
                                1,
                                "P001",
                                "Test product",
                                BigDecimal.ONE,
                                new BigDecimal(
                                        "10.00"
                                ),
                                new BigDecimal(
                                        "10.01"
                                ),
                                List.of(
                                        new InvoiceTaxSpec(
                                                "2",
                                                "4",
                                                new BigDecimal(
                                                        "15"
                                                )
                                        )
                                ),
                                UUID::randomUUID
                        )
        );
    }

    @Test
    void shouldRejectPaymentsThatDoNotMatchInvoiceTotal() {

        InvoiceItem item =
                InvoiceItem.create(
                        UUID.randomUUID(),
                        1,
                        "P001",
                        "Test product",
                        BigDecimal.ONE,
                        new BigDecimal(
                                "10.00"
                        ),
                        BigDecimal.ZERO,
                        List.of(
                                new InvoiceTaxSpec(
                                        "2",
                                        "4",
                                        new BigDecimal(
                                                "15"
                                        )
                                )
                        ),
                        UUID::randomUUID
                );

        InvoicePayment incorrectPayment =
                new InvoicePayment(
                        UUID.randomUUID(),
                        1,
                        "01",
                        new BigDecimal(
                                "10.00"
                        ),
                        null,
                        null
                );

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        Invoice.create(
                                UUID.randomUUID(),
                                null,
                                new BuyerSnapshot(
                                        "07",
                                        "9999999999999",
                                        "CONSUMIDOR FINAL",
                                        null,
                                        null
                                ),
                                List.of(
                                        item
                                ),
                                List.of(
                                        incorrectPayment
                                ),
                                "DOLAR"
                        )
        );
    }

    @Test
    void shouldRejectDuplicateItemLineNumbers() {

        InvoiceTaxSpec iva =
                new InvoiceTaxSpec(
                        "2",
                        "4",
                        new BigDecimal(
                                "15"
                        )
                );

        InvoiceItem first =
                InvoiceItem.create(
                        UUID.randomUUID(),
                        1,
                        "A",
                        "Item A",
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        BigDecimal.ZERO,
                        List.of(iva),
                        UUID::randomUUID
                );

        InvoiceItem second =
                InvoiceItem.create(
                        UUID.randomUUID(),
                        1,
                        "B",
                        "Item B",
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        BigDecimal.ZERO,
                        List.of(iva),
                        UUID::randomUUID
                );

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        Invoice.create(
                                UUID.randomUUID(),
                                null,
                                new BuyerSnapshot(
                                        "07",
                                        "9999999999999",
                                        "CONSUMIDOR FINAL",
                                        null,
                                        null
                                ),
                                List.of(
                                        first,
                                        second
                                ),
                                List.of(
                                        new InvoicePayment(
                                                UUID.randomUUID(),
                                                1,
                                                "01",
                                                new BigDecimal(
                                                        "2.30"
                                                ),
                                                null,
                                                null
                                        )
                                ),
                                "DOLAR"
                        )
        );
    }
}