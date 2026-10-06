package io.github.imecuadorian.emitta.documentsequence.domain;

public record SequentialNumber(long value) {

    private static final long MIN_VALUE = 1L;
    private static final long MAX_VALUE = 999_999_999L;

    public SequentialNumber {

        if (value < MIN_VALUE || value > MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Sequential number must be between 1 and 999999999"
            );
        }
    }

    public String formatted() {
        return "%09d".formatted(value);
    }

    @Override
    public String toString() {
        return formatted();
    }
}