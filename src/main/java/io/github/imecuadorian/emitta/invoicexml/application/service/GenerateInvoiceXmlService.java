package io.github.imecuadorian.emitta.invoicexml.application.service;

import io.github.imecuadorian.emitta.invoicexml.application.exception.InvoiceXmlSourceNotFoundException;
import io.github.imecuadorian.emitta.invoicexml.application.model.InvoiceXmlData;
import io.github.imecuadorian.emitta.invoicexml.application.model.InvoiceXmlSourceData;
import io.github.imecuadorian.emitta.invoicexml.application.port.in.GenerateInvoiceXmlUseCase;
import io.github.imecuadorian.emitta.invoicexml.application.port.out.InvoiceXmlGeneratorPort;
import io.github.imecuadorian.emitta.invoicexml.application.port.out.InvoiceXmlSourcePort;
import io.github.imecuadorian.emitta.invoicexml.domain.GeneratedInvoiceXml;
import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;
import io.github.imecuadorian.emitta.shared.fiscal.ProviderPolicyPort;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class GenerateInvoiceXmlService
        implements GenerateInvoiceXmlUseCase {

    private static final BigDecimal ZERO_MONEY =
            new BigDecimal(
                    "0.00"
            );

    private final InvoiceXmlSourcePort sourcePort;

    private final InvoiceXmlGeneratorPort generatorPort;

    private final ProviderPolicyPort policyPort;

    private final ZoneId issueZone;

    public GenerateInvoiceXmlService(InvoiceXmlSourcePort sourcePort, InvoiceXmlGeneratorPort generatorPort,
                                    ProviderPolicyPort policyPort, ZoneId issueZone) {
        this.sourcePort = Objects.requireNonNull(sourcePort);
        this.generatorPort = Objects.requireNonNull(generatorPort);
        this.policyPort = Objects.requireNonNull(policyPort);
        this.issueZone = Objects.requireNonNull(issueZone);
    }

    @Override
    public GeneratedInvoiceXml generate(
            UUID documentId
    ) {

        Objects.requireNonNull(
                documentId,
                "Document id cannot be null"
        );

        InvoiceXmlSourceData source =
                sourcePort
                        .findByDocumentId(
                                documentId
                        )
                        .orElseThrow(
                                () ->
                                        new InvoiceXmlSourceNotFoundException(
                                                documentId
                                        )
                        );

        validateFiscalIdentity(
                source
        );

        InvoiceXmlData data =
                toInvoiceXmlData(
                        source, policyPort.resolve(documentId).xmlProviderRuc()
                );

        return generatorPort.generate(
                data
        );
    }

    private InvoiceXmlData toInvoiceXmlData(
            InvoiceXmlSourceData source, String providerRuc
    ) {

        return new InvoiceXmlData(

                environmentCode(
                        source.environment()
                ),

                source.legalName(),
                source.tradeName(),
                source.ruc(),

                source.accessKey(),

                source.establishmentCode(),
                source.pointOfIssueCode(),
                formatSequential(
                        source.sequential()
                ),

                source.mainAddress(),

                source.issuedAt()
                        .atZone(
                                issueZone
                        )
                        .toLocalDate(),

                source.establishmentAddress(),

                new InvoiceXmlData.Buyer(
                        source.buyer()
                                .identificationType(),
                        source.buyer()
                                .identification(),
                        source.buyer()
                                .name(),
                        source.buyer()
                                .address()
                ),

                source.subtotal(),

                source.discountTotal(),

                aggregateTaxes(
                        source.items()
                ),

                ZERO_MONEY,

                source.total(),

                source.currency(),

                mapItems(
                        source.items()
                ),

                mapPayments(
                        source.payments()
                ),

                providerRuc
        );
    }

    private static List<InvoiceXmlData.Item> mapItems(
            List<InvoiceXmlSourceData.Item> items
    ) {

        return items
                .stream()
                .map(
                        item ->
                                new InvoiceXmlData.Item(
                                        item.sku(),
                                        item.description(),
                                        item.quantity(),
                                        item.unitPrice(),
                                        item.discount(),
                                        item.subtotal(),
                                        item.taxes()
                                                .stream()
                                                .map(
                                                        tax ->
                                                                new InvoiceXmlData.ItemTax(
                                                                        tax.taxCode(),
                                                                        tax.percentageCode(),
                                                                        tax.rate(),
                                                                        tax.taxableBase(),
                                                                        tax.amount()
                                                                )
                                                )
                                                .toList()
                                )
                )
                .toList();
    }

    private static List<InvoiceXmlData.Payment> mapPayments(
            List<InvoiceXmlSourceData.Payment> payments
    ) {

        return payments
                .stream()
                .map(
                        payment ->
                                new InvoiceXmlData.Payment(
                                        payment.method(),
                                        payment.total(),
                                        payment.term(),
                                        payment.unitTime()
                                )
                )
                .toList();
    }

    private static List<InvoiceXmlData.TaxTotal> aggregateTaxes(
            List<InvoiceXmlSourceData.Item> items
    ) {

        Map<TaxKey, TaxAccumulator> totals =
                new LinkedHashMap<>();

        for (InvoiceXmlSourceData.Item item
                : items) {

            for (InvoiceXmlSourceData.ItemTax tax
                    : item.taxes()) {

                TaxKey key =
                        new TaxKey(
                                tax.taxCode(),
                                tax.percentageCode()
                        );

                totals.merge(
                        key,
                        new TaxAccumulator(
                                tax.taxableBase(),
                                tax.amount()
                        ),
                        TaxAccumulator::add
                );
            }
        }

        List<InvoiceXmlData.TaxTotal> result =
                new ArrayList<>();

        totals.forEach(
                (key, value) ->
                        result.add(
                                new InvoiceXmlData.TaxTotal(
                                        key.taxCode(),
                                        key.percentageCode(),
                                        value.taxableBase(),
                                        value.amount()
                                )
                        )
        );

        return List.copyOf(
                result
        );
    }

    private static String environmentCode(
            FiscalEnvironment environment
    ) {

        return switch (environment) {

            case TEST ->
                    "1";

            case PRODUCTION ->
                    "2";
        };
    }

    private static String formatSequential(
            long sequential
    ) {

        if (sequential < 1
                || sequential > 999_999_999L) {

            throw new IllegalStateException(
                    "Invalid fiscal sequential: "
                            + sequential
            );
        }

        return "%09d".formatted(
                sequential
        );
    }

    private static void validateFiscalIdentity(
            InvoiceXmlSourceData source
    ) {

        if (source.accessKey() == null
                || !source.accessKey()
                .matches(
                        "\\d{49}"
                )) {

            throw new IllegalStateException(
                    "Document must have a valid 49-digit access key before XML generation"
            );
        }

        if (source.items().isEmpty()) {

            throw new IllegalStateException(
                    "Invoice must contain at least one item"
            );
        }

        if (source.payments().isEmpty()) {

            throw new IllegalStateException(
                    "Invoice must contain at least one payment"
            );
        }
    }

    private record TaxKey(
            String taxCode,
            String percentageCode
    ) {
    }

    private record TaxAccumulator(
            BigDecimal taxableBase,
            BigDecimal amount
    ) {

        private TaxAccumulator add(
                TaxAccumulator other
        ) {

            return new TaxAccumulator(
                    taxableBase.add(
                            other.taxableBase
                    ),
                    amount.add(
                            other.amount
                    )
            );
        }
    }
}
