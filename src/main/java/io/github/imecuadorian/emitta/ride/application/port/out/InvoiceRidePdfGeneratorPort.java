package io.github.imecuadorian.emitta.ride.application.port.out;

import io.github.imecuadorian.emitta.ride.application.model.InvoiceRideRequest;

/** Pure document rendering port: does not query the SRI or change fiscal states. */
public interface InvoiceRidePdfGeneratorPort {
    byte[] generate(InvoiceRideRequest request);
}
