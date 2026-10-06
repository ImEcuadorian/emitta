package io.github.imecuadorian.emitta.accesskey.domain;

import java.time.format.DateTimeFormatter;

public final class AccessKeyGenerator {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern(
                    "ddMMyyyy"
            );

    private static final String NORMAL_EMISSION_CODE =
            "1";

    private AccessKeyGenerator() {
    }

    public static AccessKey generate(
            AccessKeyComponents components
    ) {

        if (components == null) {
            throw new IllegalArgumentException(
                    "Access key components cannot be null"
            );
        }

        String base =
                DATE_FORMAT.format(
                        components.issueDate()
                )
                        + components.documentType().sriCode()
                        + components.ruc()
                        + components.environment().sriCode()
                        + components.establishmentCode()
                        + components.pointOfIssueCode()
                        + components.sequential()
                        + components.numericCode()
                        + NORMAL_EMISSION_CODE;

        if (base.length() != 48) {
            throw new IllegalStateException(
                    "Access key base must contain exactly 48 digits"
            );
        }

        int checkDigit =
                Modulus11.calculate(base);

        return new AccessKey(
                base + checkDigit
        );
    }
}