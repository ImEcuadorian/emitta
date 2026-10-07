package io.github.imecuadorian.emitta.invoicexml.adapter.out.persistence;

import io.github.imecuadorian.emitta.invoicexml.application.model.InvoiceXmlSourceData;
import io.github.imecuadorian.emitta.invoicexml.application.port.out.InvoiceXmlSourcePort;
import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PostgreSqlInvoiceXmlSourceAdapter
        implements InvoiceXmlSourcePort {

    private static final String HEADER_SQL = """
            SELECT
                d.environment,
                d.access_key,
                d.sequential,
                d.issued_at,

                t.legal_name,
                t.trade_name,
                t.ruc,
                t.main_address,

                BTRIM(e.code) AS establishment_code,
                e.address AS establishment_address,

                BTRIM(poi.code) AS point_of_issue_code,

                i.buyer_identification_type,
                i.buyer_identification,
                i.buyer_name,
                i.buyer_address,

                i.subtotal,
                i.discount_total,
                i.total,
                i.currency

            FROM documents d

            JOIN invoices i
                ON i.document_id = d.id

            JOIN taxpayers t
                ON t.id = d.taxpayer_id

            JOIN points_of_issue poi
                ON poi.id = d.point_of_issue_id

            JOIN establishments e
                ON e.id = poi.establishment_id

            WHERE d.id = :documentId
              AND d.document_type = 'INVOICE'
            """;

    private static final String ITEMS_SQL = """
            SELECT
                ii.id,
                ii.line_number,
                ii.sku,
                ii.description,
                ii.quantity,
                ii.unit_price,
                ii.discount,
                ii.subtotal

            FROM invoice_items ii

            WHERE ii.invoice_id = :documentId

            ORDER BY ii.line_number
            """;

    private static final String TAXES_SQL = """
            SELECT
                iit.invoice_item_id,
                iit.tax_code,
                iit.percentage_code,
                iit.rate,
                iit.taxable_base,
                iit.tax_amount

            FROM invoice_item_taxes iit

            JOIN invoice_items ii
                ON ii.id = iit.invoice_item_id

            WHERE ii.invoice_id = :documentId

            ORDER BY
                ii.line_number,
                iit.tax_code,
                iit.percentage_code
            """;

    private static final String PAYMENTS_SQL = """
            SELECT
                payment_method,
                total,
                term,
                unit_time

            FROM invoice_payments

            WHERE invoice_id = :documentId

            ORDER BY line_number
            """;

    private final JdbcClient jdbcClient;

    public PostgreSqlInvoiceXmlSourceAdapter(
            JdbcClient jdbcClient
    ) {

        this.jdbcClient =
                jdbcClient;
    }

    @Override
    public Optional<InvoiceXmlSourceData> findByDocumentId(
            UUID documentId
    ) {

        Optional<HeaderRow> header =
                jdbcClient
                        .sql(
                                HEADER_SQL
                        )
                        .param(
                                "documentId",
                                documentId
                        )
                        .query(
                                this::mapHeader
                        )
                        .optional();

        if (header.isEmpty()) {
            return Optional.empty();
        }

        HeaderRow sourceHeader =
                header.orElseThrow();

        List<ItemRow> itemRows =
                jdbcClient
                        .sql(
                                ITEMS_SQL
                        )
                        .param(
                                "documentId",
                                documentId
                        )
                        .query(
                                this::mapItem
                        )
                        .list();

        List<TaxRow> taxRows =
                jdbcClient
                        .sql(
                                TAXES_SQL
                        )
                        .param(
                                "documentId",
                                documentId
                        )
                        .query(
                                this::mapTax
                        )
                        .list();

        List<InvoiceXmlSourceData.Payment> payments =
                jdbcClient
                        .sql(
                                PAYMENTS_SQL
                        )
                        .param(
                                "documentId",
                                documentId
                        )
                        .query(
                                this::mapPayment
                        )
                        .list();

        Map<UUID, List<InvoiceXmlSourceData.ItemTax>>
                taxesByItem =
                groupTaxes(
                        taxRows
                );

        List<InvoiceXmlSourceData.Item> items =
                itemRows
                        .stream()
                        .map(
                                item ->
                                        new InvoiceXmlSourceData.Item(
                                                item.sku(),
                                                item.description(),
                                                item.quantity(),
                                                item.unitPrice(),
                                                item.discount(),
                                                item.subtotal(),
                                                taxesByItem.getOrDefault(
                                                        item.id(),
                                                        List.of()
                                                )
                                        )
                        )
                        .toList();

        return Optional.of(
                new InvoiceXmlSourceData(

                        sourceHeader.environment(),

                        sourceHeader.legalName(),
                        sourceHeader.tradeName(),
                        sourceHeader.ruc(),
                        sourceHeader.mainAddress(),

                        sourceHeader.accessKey(),
                        requireSequential(
                                sourceHeader.sequential()
                        ),
                        sourceHeader.issuedAt(),

                        sourceHeader.establishmentCode(),
                        sourceHeader.establishmentAddress(),
                        sourceHeader.pointOfIssueCode(),

                        new InvoiceXmlSourceData.Buyer(
                                sourceHeader
                                        .buyerIdentificationType(),
                                sourceHeader
                                        .buyerIdentification(),
                                sourceHeader
                                        .buyerName(),
                                sourceHeader
                                        .buyerAddress()
                        ),

                        sourceHeader.subtotal(),
                        sourceHeader.discountTotal(),
                        sourceHeader.total(),
                        sourceHeader.currency(),

                        items,
                        payments
                )
        );
    }

    private HeaderRow mapHeader(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {

        return new HeaderRow(

                FiscalEnvironment.valueOf(
                        resultSet.getString(
                                "environment"
                        )
                ),

                resultSet.getString(
                        "legal_name"
                ),

                resultSet.getString(
                        "trade_name"
                ),

                resultSet.getString(
                        "ruc"
                ),

                resultSet.getString(
                        "main_address"
                ),

                resultSet.getString(
                        "access_key"
                ),

                resultSet.getObject(
                        "sequential",
                        Long.class
                ),

                resultSet
                        .getTimestamp(
                                "issued_at"
                        )
                        .toInstant(),

                resultSet.getString(
                        "establishment_code"
                ),

                resultSet.getString(
                        "establishment_address"
                ),

                resultSet.getString(
                        "point_of_issue_code"
                ),

                resultSet.getString(
                        "buyer_identification_type"
                ),

                resultSet.getString(
                        "buyer_identification"
                ),

                resultSet.getString(
                        "buyer_name"
                ),

                resultSet.getString(
                        "buyer_address"
                ),

                resultSet.getBigDecimal(
                        "subtotal"
                ),

                resultSet.getBigDecimal(
                        "discount_total"
                ),

                resultSet.getBigDecimal(
                        "total"
                ),

                resultSet.getString(
                        "currency"
                )
        );
    }

    private ItemRow mapItem(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {

        return new ItemRow(

                resultSet.getObject(
                        "id",
                        UUID.class
                ),

                resultSet.getString(
                        "sku"
                ),

                resultSet.getString(
                        "description"
                ),

                resultSet.getBigDecimal(
                        "quantity"
                ),

                resultSet.getBigDecimal(
                        "unit_price"
                ),

                resultSet.getBigDecimal(
                        "discount"
                ),

                resultSet.getBigDecimal(
                        "subtotal"
                )
        );
    }

    private TaxRow mapTax(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {

        return new TaxRow(

                resultSet.getObject(
                        "invoice_item_id",
                        UUID.class
                ),

                resultSet.getString(
                        "tax_code"
                ),

                resultSet.getString(
                        "percentage_code"
                ),

                resultSet.getBigDecimal(
                        "rate"
                ),

                resultSet.getBigDecimal(
                        "taxable_base"
                ),

                resultSet.getBigDecimal(
                        "tax_amount"
                )
        );
    }

    private InvoiceXmlSourceData.Payment mapPayment(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {

        return new InvoiceXmlSourceData.Payment(

                resultSet.getString(
                        "payment_method"
                ),

                resultSet.getBigDecimal(
                        "total"
                ),

                resultSet.getBigDecimal(
                        "term"
                ),

                resultSet.getString(
                        "unit_time"
                )
        );
    }

    private static Map<
            UUID,
            List<InvoiceXmlSourceData.ItemTax>
            > groupTaxes(
            List<TaxRow> taxRows
    ) {

        Map<
                UUID,
                List<InvoiceXmlSourceData.ItemTax>
                > grouped =
                new LinkedHashMap<>();

        for (TaxRow row : taxRows) {

            grouped
                    .computeIfAbsent(
                            row.invoiceItemId(),
                            ignored ->
                                    new ArrayList<>()
                    )
                    .add(
                            new InvoiceXmlSourceData.ItemTax(
                                    row.taxCode(),
                                    row.percentageCode(),
                                    row.rate(),
                                    row.taxableBase(),
                                    row.amount()
                            )
                    );
        }

        grouped.replaceAll(
                (ignored, taxes) ->
                        List.copyOf(
                                taxes
                        )
        );

        return grouped;
    }

    private static long requireSequential(
            Long sequential
    ) {

        if (sequential == null) {
            throw new IllegalStateException(
                    "Invoice does not have a fiscal sequential yet"
            );
        }

        return sequential;
    }

    private record HeaderRow(

            FiscalEnvironment environment,

            String legalName,
            String tradeName,
            String ruc,
            String mainAddress,

            String accessKey,
            Long sequential,
            java.time.Instant issuedAt,

            String establishmentCode,
            String establishmentAddress,
            String pointOfIssueCode,

            String buyerIdentificationType,
            String buyerIdentification,
            String buyerName,
            String buyerAddress,

            BigDecimal subtotal,
            BigDecimal discountTotal,
            BigDecimal total,
            String currency
    ) {
    }

    private record ItemRow(

            UUID id,

            String sku,
            String description,

            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal discount,
            BigDecimal subtotal
    ) {
    }

    private record TaxRow(

            UUID invoiceItemId,

            String taxCode,
            String percentageCode,

            BigDecimal rate,
            BigDecimal taxableBase,
            BigDecimal amount
    ) {
    }
}