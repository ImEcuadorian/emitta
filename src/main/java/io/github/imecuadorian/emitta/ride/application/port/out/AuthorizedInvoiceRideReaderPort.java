package io.github.imecuadorian.emitta.ride.application.port.out;
import io.github.imecuadorian.emitta.ride.application.model.InvoiceRideRequest;
import java.time.Instant;
public interface AuthorizedInvoiceRideReaderPort {
    InvoiceRideRequest read(byte[] authorizedXml, String accessKey, Instant authorizedAt);
}
