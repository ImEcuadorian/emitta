package io.github.imecuadorian.emitta.invoice.application.exception;

import java.math.BigDecimal;

public final class ExpectedTotalMismatchException
        extends RuntimeException {

    private final BigDecimal expected;
    private final BigDecimal calculated;

    public ExpectedTotalMismatchException(
            BigDecimal expected,
            BigDecimal calculated
    ) {
        super(
                "Expected invoice total "
                        + expected
                        + " does not match calculated total "
                        + calculated
        );

        this.expected = expected;
        this.calculated = calculated;
    }

    public BigDecimal getExpected() {
        return expected;
    }

    public BigDecimal getCalculated() {
        return calculated;
    }
}