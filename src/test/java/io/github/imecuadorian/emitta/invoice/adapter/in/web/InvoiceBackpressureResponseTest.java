package io.github.imecuadorian.emitta.invoice.adapter.in.web;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InvoiceBackpressureResponseTest {
    @Test void preCommitRejectionHasStableCodeRetryAfterAndNoStore() {
        var response=new InvoiceExceptionHandler().saturated();
        assertEquals(503,response.getStatusCode().value());
        assertEquals("2",response.getHeaders().getFirst("Retry-After"));
        assertEquals("no-store",response.getHeaders().getCacheControl());
        assertEquals("ADMISSION_SATURATED",response.getBody().getProperties().get("code"));
        assertTrue(response.getBody().getDetail().contains("same idempotency key"));
    }
}
