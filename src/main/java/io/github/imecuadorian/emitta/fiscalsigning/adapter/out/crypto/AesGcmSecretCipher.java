package io.github.imecuadorian.emitta.fiscalsigning.adapter.out.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

public final class AesGcmSecretCipher {

    private static final byte FORMAT_VERSION = 1;

    private static final int KEY_SIZE_BYTES = 32;
    private static final int IV_SIZE_BYTES = 12;
    private static final int GCM_TAG_SIZE_BITS = 128;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom;

    public AesGcmSecretCipher(
            String masterKeyBase64
    ) {

        this(
                masterKeyBase64,
                new SecureRandom()
        );
    }

    AesGcmSecretCipher(
            String masterKeyBase64,
            SecureRandom secureRandom
    ) {

        Objects.requireNonNull(
                masterKeyBase64,
                "Master key cannot be null"
        );

        this.secureRandom =
                Objects.requireNonNull(
                        secureRandom,
                        "Secure random cannot be null"
                );

        byte[] decodedKey;

        try {

            decodedKey =
                    Base64.getDecoder()
                            .decode(
                                    masterKeyBase64
                            );

        } catch (IllegalArgumentException exception) {

            throw new IllegalArgumentException(
                    "Master key must be valid Base64",
                    exception
            );
        }

        try {

            if (
                    decodedKey.length
                            != KEY_SIZE_BYTES
            ) {

                throw new IllegalArgumentException(
                        "Master key must decode to exactly 32 bytes"
                );
            }

            this.secretKey =
                    new SecretKeySpec(
                            decodedKey,
                            "AES"
                    );

        } finally {

            Arrays.fill(
                    decodedKey,
                    (byte) 0
            );
        }
    }

    public byte[] encrypt(
            byte[] plaintext,
            String associatedData
    ) {

        Objects.requireNonNull(
                plaintext,
                "Plaintext cannot be null"
        );

        validateAssociatedData(
                associatedData
        );

        byte[] iv =
                new byte[IV_SIZE_BYTES];

        secureRandom.nextBytes(
                iv
        );

        try {

            Cipher cipher =
                    Cipher.getInstance(
                            "AES/GCM/NoPadding"
                    );

            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    secretKey,
                    new GCMParameterSpec(
                            GCM_TAG_SIZE_BITS,
                            iv
                    )
            );

            cipher.updateAAD(
                    associatedData.getBytes(
                            StandardCharsets.UTF_8
                    )
            );

            byte[] ciphertext =
                    cipher.doFinal(
                            plaintext
                    );

            return ByteBuffer
                    .allocate(
                            1
                                    + IV_SIZE_BYTES
                                    + ciphertext.length
                    )
                    .put(
                            FORMAT_VERSION
                    )
                    .put(
                            iv
                    )
                    .put(
                            ciphertext
                    )
                    .array();

        } catch (GeneralSecurityException exception) {

            throw new IllegalStateException(
                    "Unable to encrypt secret",
                    exception
            );

        } finally {

            Arrays.fill(
                    iv,
                    (byte) 0
            );
        }
    }

    public byte[] decrypt(
            byte[] envelope,
            String associatedData
    ) {

        Objects.requireNonNull(
                envelope,
                "Encrypted envelope cannot be null"
        );

        validateAssociatedData(
                associatedData
        );

        if (
                envelope.length
                        <= 1 + IV_SIZE_BYTES
        ) {

            throw new IllegalArgumentException(
                    "Encrypted envelope is invalid"
            );
        }

        ByteBuffer buffer =
                ByteBuffer.wrap(
                        envelope
                );

        byte version =
                buffer.get();

        if (
                version
                        != FORMAT_VERSION
        ) {

            throw new IllegalArgumentException(
                    "Unsupported encrypted envelope version: "
                            + version
            );
        }

        byte[] iv =
                new byte[IV_SIZE_BYTES];

        buffer.get(
                iv
        );

        byte[] ciphertext =
                new byte[
                        buffer.remaining()
                        ];

        buffer.get(
                ciphertext
        );

        try {

            Cipher cipher =
                    Cipher.getInstance(
                            "AES/GCM/NoPadding"
                    );

            cipher.init(
                    Cipher.DECRYPT_MODE,
                    secretKey,
                    new GCMParameterSpec(
                            GCM_TAG_SIZE_BITS,
                            iv
                    )
            );

            cipher.updateAAD(
                    associatedData.getBytes(
                            StandardCharsets.UTF_8
                    )
            );

            return cipher.doFinal(
                    ciphertext
            );

        } catch (GeneralSecurityException exception) {

            throw new IllegalStateException(
                    "Unable to decrypt secret or integrity check failed",
                    exception
            );

        } finally {

            Arrays.fill(
                    iv,
                    (byte) 0
            );

            Arrays.fill(
                    ciphertext,
                    (byte) 0
            );
        }
    }

    private static void validateAssociatedData(
            String associatedData
    ) {

        if (
                associatedData == null
                        || associatedData.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "Associated data cannot be blank"
            );
        }
    }
}