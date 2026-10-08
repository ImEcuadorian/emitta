package io.github.imecuadorian.emitta.fiscalsigning.adapter.out.crypto;

import org.junit.jupiter.api.Test;

import javax.crypto.AEADBadTagException;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class AesGcmSecretCipherTest {

    private static final String MASTER_KEY =
            Base64.getEncoder()
                    .encodeToString(
                            new byte[32]
                    );

    @Test
    void shouldEncryptAndDecryptSecret() {

        AesGcmSecretCipher cipher =
                new AesGcmSecretCipher(
                        MASTER_KEY
                );

        byte[] plaintext =
                "PKCS12-CONTENT"
                        .getBytes(
                                StandardCharsets.UTF_8
                        );

        String aad =
                "certificate:taxpayer-1:content";

        byte[] encrypted =
                cipher.encrypt(
                        plaintext,
                        aad
                );

        assertNotEquals(
                new String(
                        plaintext,
                        StandardCharsets.UTF_8
                ),
                new String(
                        encrypted,
                        StandardCharsets.ISO_8859_1
                )
        );

        byte[] decrypted =
                cipher.decrypt(
                        encrypted,
                        aad
                );

        assertArrayEquals(
                plaintext,
                decrypted
        );
    }

    @Test
    void shouldProduceDifferentCiphertextForSamePlaintext() {

        AesGcmSecretCipher cipher =
                new AesGcmSecretCipher(
                        MASTER_KEY
                );

        byte[] plaintext =
                "same-secret"
                        .getBytes(
                                StandardCharsets.UTF_8
                        );

        String aad =
                "certificate:taxpayer-1:password";

        byte[] first =
                cipher.encrypt(
                        plaintext,
                        aad
                );

        byte[] second =
                cipher.encrypt(
                        plaintext,
                        aad
                );

        assertFalse(
                java.util.Arrays.equals(
                        first,
                        second
                )
        );
    }

    @Test
    void shouldRejectModifiedCiphertext() {

        AesGcmSecretCipher cipher =
                new AesGcmSecretCipher(
                        MASTER_KEY
                );

        byte[] encrypted =
                cipher.encrypt(
                        "secret"
                                .getBytes(
                                        StandardCharsets.UTF_8
                                ),
                        "certificate:taxpayer-1:content"
                );

        encrypted[
                encrypted.length - 1
                ] ^= 1;

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                cipher.decrypt(
                                        encrypted,
                                        "certificate:taxpayer-1:content"
                                )
                );

        assertInstanceOf(
                AEADBadTagException.class,
                exception.getCause()
        );
    }

    @Test
    void shouldRejectDifferentAssociatedData() {

        AesGcmSecretCipher cipher =
                new AesGcmSecretCipher(
                        MASTER_KEY
                );

        byte[] encrypted =
                cipher.encrypt(
                        "secret"
                                .getBytes(
                                        StandardCharsets.UTF_8
                                ),
                        "certificate:taxpayer-1:content"
                );

        assertThrows(
                IllegalStateException.class,
                () ->
                        cipher.decrypt(
                                encrypted,
                                "certificate:taxpayer-2:content"
                        )
        );
    }

    @Test
    void shouldRejectInvalidMasterKeyLength() {

        String invalidKey =
                Base64.getEncoder()
                        .encodeToString(
                                new byte[16]
                        );

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AesGcmSecretCipher(
                                invalidKey
                        )
        );
    }
}