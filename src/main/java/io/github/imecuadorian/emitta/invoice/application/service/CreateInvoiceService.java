package io.github.imecuadorian.emitta.invoice.application.service;

import io.github.imecuadorian.emitta.document.application.command.CreateDocumentCommand;
import io.github.imecuadorian.emitta.document.application.model.CreateDocumentResult;
import io.github.imecuadorian.emitta.document.application.port.in.CreateDocumentUseCase;
import io.github.imecuadorian.emitta.invoice.application.command.CreateInvoiceCommand;
import io.github.imecuadorian.emitta.invoice.application.command.InvoiceItemCommand;
import io.github.imecuadorian.emitta.invoice.application.command.InvoicePaymentCommand;
import io.github.imecuadorian.emitta.invoice.application.command.InvoiceTaxCommand;
import io.github.imecuadorian.emitta.invoice.application.exception.ExpectedTotalMismatchException;
import io.github.imecuadorian.emitta.invoice.application.exception.InvoiceIdempotencyConflictException;
import io.github.imecuadorian.emitta.invoice.application.model.CreateInvoiceResult;
import io.github.imecuadorian.emitta.invoice.application.port.in.CreateInvoiceUseCase;
import io.github.imecuadorian.emitta.invoice.application.port.out.InvoiceRepository;
import io.github.imecuadorian.emitta.invoice.domain.BuyerSnapshot;
import io.github.imecuadorian.emitta.invoice.domain.Invoice;
import io.github.imecuadorian.emitta.invoice.domain.InvoiceItem;
import io.github.imecuadorian.emitta.invoice.domain.InvoicePayment;
import io.github.imecuadorian.emitta.invoice.domain.InvoiceTaxSpec;
import io.github.imecuadorian.emitta.shared.fiscal.DocumentType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class CreateInvoiceService
        implements CreateInvoiceUseCase {

    private final CreateDocumentUseCase
            createDocumentUseCase;

    private final InvoiceRepository
            invoiceRepository;

    private final Supplier<UUID>
            idGenerator;

    public CreateInvoiceService(
            CreateDocumentUseCase createDocumentUseCase,
            InvoiceRepository invoiceRepository,
            Supplier<UUID> idGenerator
    ) {
        this.createDocumentUseCase =
                Objects.requireNonNull(
                        createDocumentUseCase
                );

        this.invoiceRepository =
                Objects.requireNonNull(
                        invoiceRepository
                );

        this.idGenerator =
                Objects.requireNonNull(
                        idGenerator
                );
    }

    @Override
    public CreateInvoiceResult create(
            CreateInvoiceCommand command
    ) {

        Objects.requireNonNull(
                command,
                "Create invoice command cannot be null"
        );

        CreateDocumentResult documentResult =
                createDocumentUseCase.create(
                        new CreateDocumentCommand(
                                command.tenantId(),
                                command.pointOfIssueId(),
                                DocumentType.INVOICE,
                                command.environment(),
                                command.idempotencyKey(),
                                command.issuedAt()
                        )
                );

        Invoice requestedInvoice =
                buildInvoice(
                        documentResult
                                .document()
                                .getId(),
                        command
                );

        validateExpectedTotal(
                command.expectedTotal(),
                requestedInvoice.getTotal()
        );

        /*
         * New document:
         *
         * persist its complete fiscal business snapshot.
         */
        if (documentResult.created()) {

            invoiceRepository.insert(
                    requestedInvoice
            );

            return new CreateInvoiceResult(
                    documentResult.document(),
                    requestedInvoice,
                    true
            );
        }

        /*
         * Idempotent replay:
         *
         * the Document already exists. Its Invoice must also
         * exist because both were committed atomically.
         */
        Invoice existing =
                invoiceRepository
                        .findByDocumentId(
                                documentResult
                                        .document()
                                        .getId()
                        )
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Document exists without invoice snapshot: "
                                                        + documentResult
                                                        .document()
                                                        .getId()
                                        )
                        );

        if (
                !sameBusinessContent(
                        existing,
                        requestedInvoice
                )
        ) {
            throw new InvoiceIdempotencyConflictException(
                    existing.getDocumentId()
            );
        }

        return new CreateInvoiceResult(
                documentResult.document(),
                existing,
                false
        );
    }

    private Invoice buildInvoice(
            UUID documentId,
            CreateInvoiceCommand command
    ) {

        BuyerSnapshot buyer =
                new BuyerSnapshot(
                        command.buyer()
                                .identificationType(),
                        command.buyer()
                                .identification(),
                        command.buyer()
                                .name(),
                        command.buyer()
                                .email(),
                        command.buyer()
                                .address()
                );

        List<InvoiceItem> items =
                new ArrayList<>();

        int itemLine =
                1;

        for (
                InvoiceItemCommand item
                : command.items()
        ) {

            List<InvoiceTaxSpec> taxes =
                    item.taxes()
                            .stream()
                            .map(
                                    this::taxSpecification
                            )
                            .toList();

            items.add(
                    InvoiceItem.create(
                            idGenerator.get(),
                            itemLine++,
                            item.sku(),
                            item.description(),
                            item.quantity(),
                            item.unitPrice(),
                            item.discount(),
                            taxes,
                            idGenerator
                    )
            );
        }

        List<InvoicePayment> payments =
                new ArrayList<>();

        int paymentLine =
                1;

        for (
                InvoicePaymentCommand payment
                : command.payments()
        ) {

            payments.add(
                    new InvoicePayment(
                            idGenerator.get(),
                            paymentLine++,
                            payment.paymentMethod(),
                            payment.total(),
                            payment.term(),
                            payment.unitTime()
                    )
            );
        }

        return Invoice.create(
                documentId,
                command.customerId(),
                buyer,
                items,
                payments,
                "DOLAR"
        );
    }

    private InvoiceTaxSpec taxSpecification(
            InvoiceTaxCommand command
    ) {

        return new InvoiceTaxSpec(
                command.taxCode(),
                command.percentageCode(),
                command.rate()
        );
    }

    private static void validateExpectedTotal(
            BigDecimal expectedTotal,
            BigDecimal calculatedTotal
    ) {

        if (expectedTotal == null) {
            return;
        }

        BigDecimal normalizedExpected =
                expectedTotal.setScale(
                        2,
                        RoundingMode.HALF_UP
                );

        if (
                normalizedExpected.compareTo(
                        calculatedTotal
                ) != 0
        ) {
            throw new ExpectedTotalMismatchException(
                    normalizedExpected,
                    calculatedTotal
            );
        }
    }

    private static boolean sameBusinessContent(
            Invoice existing,
            Invoice requested
    ) {

        if (
                !Objects.equals(
                        existing.getCustomerId(),
                        requested.getCustomerId()
                )
                        || !existing.getBuyer()
                        .equals(
                                requested.getBuyer()
                        )
                        || !existing.getCurrency()
                        .equals(
                                requested.getCurrency()
                        )
                        || !sameDecimal(
                        existing.getSubtotal(),
                        requested.getSubtotal()
                )
                        || !sameDecimal(
                        existing.getDiscountTotal(),
                        requested.getDiscountTotal()
                )
                        || !sameDecimal(
                        existing.getTaxTotal(),
                        requested.getTaxTotal()
                )
                        || !sameDecimal(
                        existing.getTotal(),
                        requested.getTotal()
                )
        ) {
            return false;
        }

        if (
                existing.getItems().size()
                        != requested.getItems().size()
        ) {
            return false;
        }

        for (
                int index = 0;
                index < existing.getItems().size();
                index++
        ) {

            InvoiceItem left =
                    existing.getItems().get(index);

            InvoiceItem right =
                    requested.getItems().get(index);

            if (
                    left.getLineNumber()
                            != right.getLineNumber()
                            || !Objects.equals(
                            left.getSku(),
                            right.getSku()
                    )
                            || !left.getDescription()
                            .equals(
                                    right.getDescription()
                            )
                            || !sameDecimal(
                            left.getQuantity(),
                            right.getQuantity()
                    )
                            || !sameDecimal(
                            left.getUnitPrice(),
                            right.getUnitPrice()
                    )
                            || !sameDecimal(
                            left.getDiscount(),
                            right.getDiscount()
                    )
                            || !sameDecimal(
                            left.getSubtotal(),
                            right.getSubtotal()
                    )
                            || !sameDecimal(
                            left.getTaxTotal(),
                            right.getTaxTotal()
                    )
                            || !sameDecimal(
                            left.getTotal(),
                            right.getTotal()
                    )
            ) {
                return false;
            }

            if (
                    left.getTaxes().size()
                            != right.getTaxes().size()
            ) {
                return false;
            }

            for (
                    int taxIndex = 0;
                    taxIndex < left.getTaxes().size();
                    taxIndex++
            ) {

                var leftTax =
                        left.getTaxes().get(
                                taxIndex
                        );

                var rightTax =
                        right.getTaxes().get(
                                taxIndex
                        );

                if (
                        !leftTax.taxCode()
                                .equals(
                                        rightTax.taxCode()
                                )
                                || !leftTax.percentageCode()
                                .equals(
                                        rightTax.percentageCode()
                                )
                                || !sameDecimal(
                                leftTax.rate(),
                                rightTax.rate()
                        )
                                || !sameDecimal(
                                leftTax.taxableBase(),
                                rightTax.taxableBase()
                        )
                                || !sameDecimal(
                                leftTax.taxAmount(),
                                rightTax.taxAmount()
                        )
                ) {
                    return false;
                }
            }
        }

        if (
                existing.getPayments().size()
                        != requested.getPayments().size()
        ) {
            return false;
        }

        for (
                int index = 0;
                index < existing.getPayments().size();
                index++
        ) {

            InvoicePayment left =
                    existing.getPayments()
                            .get(index);

            InvoicePayment right =
                    requested.getPayments()
                            .get(index);

            if (
                    left.lineNumber()
                            != right.lineNumber()
                            || !left.paymentMethod()
                            .equals(
                                    right.paymentMethod()
                            )
                            || !sameDecimal(
                            left.total(),
                            right.total()
                    )
                            || !sameDecimal(
                            left.term(),
                            right.term()
                    )
                            || !Objects.equals(
                            left.unitTime(),
                            right.unitTime()
                    )
            ) {
                return false;
            }
        }

        return true;
    }

    private static boolean sameDecimal(
            BigDecimal left,
            BigDecimal right
    ) {

        if (
                left == null
                        || right == null
        ) {
            return left == right;
        }

        return left.compareTo(
                right
        ) == 0;
    }


}