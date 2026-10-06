package io.github.imecuadorian.emitta.accesskey.adapter.out.random;

import io.github.imecuadorian.emitta.accesskey.application.port.out.NumericCodeGenerator;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class SecureRandomNumericCodeGenerator
        implements NumericCodeGenerator {

    private static final int BOUND =
            100_000_000;

    private final SecureRandom secureRandom =
            new SecureRandom();

    @Override
    public String generate() {

        int value =
                secureRandom.nextInt(
                        BOUND
                );

        return "%08d".formatted(
                value
        );
    }
}