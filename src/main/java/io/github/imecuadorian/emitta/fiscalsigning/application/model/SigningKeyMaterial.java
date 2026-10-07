package io.github.imecuadorian.emitta.fiscalsigning.application.model;

import java.util.Objects;

public final class SigningKeyMaterial {

    private final byte[] pkcs12Content;
    private final char[] password;
    private final String alias;

    public SigningKeyMaterial(
            byte[] pkcs12Content,
            char[] password,
            String alias
    ) {

        Objects.requireNonNull(
                pkcs12Content,
                "PKCS#12 content cannot be null"
        );

        if (pkcs12Content.length == 0) {
            throw new IllegalArgumentException(
                    "PKCS#12 content cannot be empty"
            );
        }

        this.pkcs12Content =
                pkcs12Content.clone();

        this.password =
                Objects.requireNonNull(
                        password,
                        "PKCS#12 password cannot be null"
                ).clone();

        this.alias =
                normalizeAlias(
                        alias
                );
    }

    public byte[] pkcs12Content() {

        return pkcs12Content.clone();
    }

    public char[] password() {

        return password.clone();
    }

    public String alias() {

        return alias;
    }

    private static String normalizeAlias(
            String alias
    ) {

        if (
                alias == null
                        || alias.isBlank()
        ) {
            return null;
        }

        return alias.trim();
    }
}