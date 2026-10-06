package io.github.imecuadorian.emitta.invoice.application.command;

public record InvoiceBuyerCommand(
        String identificationType,
        String identification,
        String name,
        String email,
        String address
) {
}