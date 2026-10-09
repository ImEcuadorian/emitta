package io.github.imecuadorian.emitta.invoice.adapter.out.persistence;

import io.github.imecuadorian.emitta.invoice.application.port.out.InvoiceRepository;
import io.github.imecuadorian.emitta.invoice.domain.BuyerSnapshot;
import io.github.imecuadorian.emitta.invoice.domain.Invoice;
import io.github.imecuadorian.emitta.invoice.domain.InvoiceItem;
import io.github.imecuadorian.emitta.invoice.domain.InvoiceItemTax;
import io.github.imecuadorian.emitta.invoice.domain.InvoicePayment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PostgreSqlInvoicePersistenceAdapter
        implements InvoiceRepository {

    private final JdbcTemplate jdbcTemplate;

    public PostgreSqlInvoicePersistenceAdapter(
            JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate =
                Objects.requireNonNull(
                        jdbcTemplate
                );
    }

    @Override
    public void insert(
            Invoice invoice
    ) {
        if (invoice.getCustomerId() != null && jdbcTemplate.queryForObject("""
                SELECT count(*) FROM emitta.customers c
                JOIN emitta.documents d ON d.tenant_id=c.tenant_id
                WHERE c.id=? AND d.id=? AND c.status='ACTIVE'
                """, Integer.class, invoice.getCustomerId(), invoice.getDocumentId()) != 1) {
            throw new io.github.imecuadorian.emitta.invoice.application.exception.InvoiceCustomerUnavailableException();
        }

        jdbcTemplate.update(
                """
                INSERT INTO emitta.invoices (
                    document_id,
                    customer_id,
                    buyer_identification_type,
                    buyer_identification,
                    buyer_name,
                    buyer_email,
                    buyer_address,
                    subtotal,
                    discount_total,
                    tax_total,
                    total,
                    currency
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                invoice.getDocumentId(),
                invoice.getCustomerId(),
                invoice.getBuyer()
                        .identificationType(),
                invoice.getBuyer()
                        .identification(),
                invoice.getBuyer()
                        .name(),
                invoice.getBuyer()
                        .email(),
                invoice.getBuyer()
                        .address(),
                invoice.getSubtotal(),
                invoice.getDiscountTotal(),
                invoice.getTaxTotal(),
                invoice.getTotal(),
                invoice.getCurrency()
        );

        for (
                InvoiceItem item
                : invoice.getItems()
        ) {

            jdbcTemplate.update(
                    """
                    INSERT INTO emitta.invoice_items (
                        id,
                        invoice_id,
                        line_number,
                        sku,
                        description,
                        quantity,
                        unit_price,
                        discount,
                        subtotal,
                        tax_total,
                        total
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    item.getId(),
                    invoice.getDocumentId(),
                    item.getLineNumber(),
                    item.getSku(),
                    item.getDescription(),
                    item.getQuantity(),
                    item.getUnitPrice(),
                    item.getDiscount(),
                    item.getSubtotal(),
                    item.getTaxTotal(),
                    item.getTotal()
            );

            for (
                    InvoiceItemTax tax
                    : item.getTaxes()
            ) {

                jdbcTemplate.update(
                        """
                        INSERT INTO emitta.invoice_item_taxes (
                            id,
                            invoice_item_id,
                            tax_code,
                            percentage_code,
                            rate,
                            taxable_base,
                            tax_amount
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                        tax.id(),
                        item.getId(),
                        tax.taxCode(),
                        tax.percentageCode(),
                        tax.rate(),
                        tax.taxableBase(),
                        tax.taxAmount()
                );
            }
        }

        for (
                InvoicePayment payment
                : invoice.getPayments()
        ) {

            jdbcTemplate.update(
                    """
                    INSERT INTO emitta.invoice_payments (
                        id,
                        invoice_id,
                        line_number,
                        payment_method,
                        total,
                        term,
                        unit_time
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    payment.id(),
                    invoice.getDocumentId(),
                    payment.lineNumber(),
                    payment.paymentMethod(),
                    payment.total(),
                    payment.term(),
                    payment.unitTime()
            );
        }
    }

    @Override
    public Optional<Invoice> findByDocumentId(
            UUID documentId
    ) {

        List<InvoiceHeaderRow> headers =
                jdbcTemplate.query(
                        """
                        SELECT
                            document_id,
                            customer_id,
                            buyer_identification_type,
                            buyer_identification,
                            buyer_name,
                            buyer_email,
                            buyer_address,
                            subtotal,
                            discount_total,
                            tax_total,
                            total,
                            currency
                        FROM emitta.invoices
                        WHERE document_id = ?
                        """,
                        (rs, rowNum) ->
                                new InvoiceHeaderRow(
                                        rs.getObject(
                                                "document_id",
                                                UUID.class
                                        ),
                                        rs.getObject(
                                                "customer_id",
                                                UUID.class
                                        ),
                                        rs.getString(
                                                "buyer_identification_type"
                                        ),
                                        rs.getString(
                                                "buyer_identification"
                                        ),
                                        rs.getString(
                                                "buyer_name"
                                        ),
                                        rs.getString(
                                                "buyer_email"
                                        ),
                                        rs.getString(
                                                "buyer_address"
                                        ),
                                        rs.getBigDecimal(
                                                "subtotal"
                                        ),
                                        rs.getBigDecimal(
                                                "discount_total"
                                        ),
                                        rs.getBigDecimal(
                                                "tax_total"
                                        ),
                                        rs.getBigDecimal(
                                                "total"
                                        ),
                                        rs.getString(
                                                "currency"
                                        )
                                ),
                        documentId
                );

        if (headers.isEmpty()) {
            return Optional.empty();
        }

        InvoiceHeaderRow header =
                headers.getFirst();

        List<InvoiceItem> items =
                jdbcTemplate.query(
                        """
                        SELECT
                            id,
                            line_number,
                            sku,
                            description,
                            quantity,
                            unit_price,
                            discount,
                            subtotal,
                            tax_total,
                            total
                        FROM emitta.invoice_items
                        WHERE invoice_id = ?
                        ORDER BY line_number
                        """,
                        (rs, rowNum) -> {

                            UUID itemId =
                                    rs.getObject(
                                            "id",
                                            UUID.class
                                    );

                            List<InvoiceItemTax> taxes =
                                    findTaxes(
                                            itemId
                                    );

                            return InvoiceItem.restore(
                                    itemId,
                                    rs.getInt(
                                            "line_number"
                                    ),
                                    rs.getString(
                                            "sku"
                                    ),
                                    rs.getString(
                                            "description"
                                    ),
                                    rs.getBigDecimal(
                                            "quantity"
                                    ),
                                    rs.getBigDecimal(
                                            "unit_price"
                                    ),
                                    rs.getBigDecimal(
                                            "discount"
                                    ),
                                    rs.getBigDecimal(
                                            "subtotal"
                                    ),
                                    rs.getBigDecimal(
                                            "tax_total"
                                    ),
                                    rs.getBigDecimal(
                                            "total"
                                    ),
                                    taxes
                            );
                        },
                        documentId
                );

        List<InvoicePayment> payments =
                jdbcTemplate.query(
                        """
                        SELECT
                            id,
                            line_number,
                            payment_method,
                            total,
                            term,
                            unit_time
                        FROM emitta.invoice_payments
                        WHERE invoice_id = ?
                        ORDER BY line_number
                        """,
                        (rs, rowNum) ->
                                new InvoicePayment(
                                        rs.getObject(
                                                "id",
                                                UUID.class
                                        ),
                                        rs.getInt(
                                                "line_number"
                                        ),
                                        rs.getString(
                                                "payment_method"
                                        ),
                                        rs.getBigDecimal(
                                                "total"
                                        ),
                                        rs.getBigDecimal(
                                                "term"
                                        ),
                                        rs.getString(
                                                "unit_time"
                                        )
                                ),
                        documentId
                );

        return Optional.of(
                Invoice.restore(
                        header.documentId(),
                        header.customerId(),
                        new BuyerSnapshot(
                                header.buyerIdentificationType(),
                                header.buyerIdentification(),
                                header.buyerName(),
                                header.buyerEmail(),
                                header.buyerAddress()
                        ),
                        header.subtotal(),
                        header.discountTotal(),
                        header.taxTotal(),
                        header.total(),
                        header.currency(),
                        items,
                        payments
                )
        );
    }

    private List<InvoiceItemTax> findTaxes(
            UUID itemId
    ) {

        return jdbcTemplate.query(
                """
                SELECT
                    id,
                    tax_code,
                    percentage_code,
                    rate,
                    taxable_base,
                    tax_amount
                FROM emitta.invoice_item_taxes
                WHERE invoice_item_id = ?
                ORDER BY tax_code, percentage_code
                """,
                (rs, rowNum) ->
                        new InvoiceItemTax(
                                rs.getObject(
                                        "id",
                                        UUID.class
                                ),
                                rs.getString(
                                        "tax_code"
                                ),
                                rs.getString(
                                        "percentage_code"
                                ),
                                rs.getBigDecimal(
                                        "rate"
                                ),
                                rs.getBigDecimal(
                                        "taxable_base"
                                ),
                                rs.getBigDecimal(
                                        "tax_amount"
                                )
                        ),
                itemId
        );
    }

    private record InvoiceHeaderRow(
            UUID documentId,
            UUID customerId,
            String buyerIdentificationType,
            String buyerIdentification,
            String buyerName,
            String buyerEmail,
            String buyerAddress,
            java.math.BigDecimal subtotal,
            java.math.BigDecimal discountTotal,
            java.math.BigDecimal taxTotal,
            java.math.BigDecimal total,
            String currency
    ) {
    }
}