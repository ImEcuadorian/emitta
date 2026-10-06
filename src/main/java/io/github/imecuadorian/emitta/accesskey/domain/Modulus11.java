package io.github.imecuadorian.emitta.accesskey.domain;

final class Modulus11 {

    private Modulus11() {
    }

    static int calculate(String digits) {

        if (
                digits == null
                        || digits.isBlank()
                        || !digits.matches("\\d+")
        ) {
            throw new IllegalArgumentException(
                    "Modulus 11 input must contain only numeric digits"
            );
        }

        int sum = 0;
        int factor = 2;

        for (
                int i = digits.length() - 1;
                i >= 0;
                i--
        ) {
            int digit =
                    Character.digit(
                            digits.charAt(i),
                            10
                    );

            sum += digit * factor;

            factor++;

            if (factor > 7) {
                factor = 2;
            }
        }

        int result =
                11 - (sum % 11);

        if (result == 11) {
            return 0;
        }

        if (result == 10) {
            return 1;
        }

        return result;
    }
}