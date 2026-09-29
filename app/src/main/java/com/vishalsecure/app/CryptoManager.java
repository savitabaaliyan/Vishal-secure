package com.vishalsecure.app;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.KeyStore;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class CryptoManager {

    private static final String KEYSTORE_NAME = "AndroidKeyStore";
    private static final String KEY_ALIAS = "VishalSecureVideoKey";

    private static final String TRANSFORMATION =
            "AES/GCM/NoPadding";

    private static final int IV_LENGTH = 12;
    private static final int BUFFER_SIZE = 1024 * 1024;

    private CryptoManager() {
    }

    private static SecretKey getOrCreateKey()
            throws Exception {

        KeyStore keyStore =
                KeyStore.getInstance(KEYSTORE_NAME);

        keyStore.load(null);

        if (keyStore.containsAlias(KEY_ALIAS)) {

            KeyStore.Entry entry =
                    keyStore.getEntry(
                            KEY_ALIAS,
                            null
                    );

            return ((KeyStore.SecretKeyEntry) entry)
                    .getSecretKey();
        }

        KeyGenerator keyGenerator =
                KeyGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_AES,
                        KEYSTORE_NAME
                );

        KeyGenParameterSpec spec =
                new KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT |
                                KeyProperties.PURPOSE_DECRYPT
                )
                        .setBlockModes(
                                KeyProperties.BLOCK_MODE_GCM
                        )
                        .setEncryptionPaddings(
                                KeyProperties.ENCRYPTION_PADDING_NONE
                        )
                        .setRandomizedEncryptionRequired(true)
                        .build();

        keyGenerator.init(spec);

        return keyGenerator.generateKey();
    }

    public static boolean encryptFile(
            File inputFile,
            File outputFile
    ) {

        if (inputFile == null ||
                outputFile == null ||
                !inputFile.exists()) {

            return false;
        }

        try {

            SecretKey key =
                    getOrCreateKey();

            byte[] iv =
                    new byte[IV_LENGTH];

            SecureRandom random =
                    new SecureRandom();

            random.nextBytes(iv);

            Cipher cipher =
                    Cipher.getInstance(
                            TRANSFORMATION
                    );

            GCMParameterSpec gcmSpec =
                    new GCMParameterSpec(
                            128,
                            iv
                    );

            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    key,
                    gcmSpec
            );

            File parent =
                    outputFile.getParentFile();

            if (parent != null &&
                    !parent.exists()) {

                parent.mkdirs();
            }

            try (
                    FileInputStream input =
                            new FileInputStream(
                                    inputFile
                            );

                    FileOutputStream output =
                            new FileOutputStream(
                                    outputFile
                            )
            ) {

                /*
                 * पहले IV save होगा।
                 * बाकी पूरा data encrypted होगा।
                 */
                output.write(iv);

                byte[] buffer =
                        new byte[BUFFER_SIZE];

                int read;

                while (
                        (read = input.read(buffer))
                                != -1
                ) {

                    byte[] encrypted =
                            cipher.update(
                                    buffer,
                                    0,
                                    read
                            );

                    if (encrypted != null) {

                        output.write(
                                encrypted
                        );
                    }
                }

                byte[] finalBytes =
                        cipher.doFinal();

                if (finalBytes != null) {

                    output.write(
                            finalBytes
                    );
                }

                output.flush();
            }

            return true;

        } catch (Exception e) {

            if (outputFile.exists()) {
                outputFile.delete();
            }

            return false;
        }
    }

    public static boolean decryptFile(
            File encryptedFile,
            File outputFile
    ) {

        if (encryptedFile == null ||
                outputFile == null ||
                !encryptedFile.exists()) {

            return false;
        }

        try {

            SecretKey key =
                    getOrCreateKey();

            try (
                    FileInputStream input =
                            new FileInputStream(
                                    encryptedFile
                            )
            ) {

                byte[] iv =
                        new byte[IV_LENGTH];

                int ivRead =
                        input.read(iv);

                if (ivRead != IV_LENGTH) {
                    return false;
                }

                Cipher cipher =
                        Cipher.getInstance(
                                TRANSFORMATION
                        );

                GCMParameterSpec gcmSpec =
                        new GCMParameterSpec(
                                128,
                                iv
                        );

                cipher.init(
                        Cipher.DECRYPT_MODE,
                        key,
                        gcmSpec
                );

                File parent =
                        outputFile.getParentFile();

                if (parent != null &&
                        !parent.exists()) {

                    parent.mkdirs();
                }

                try (
                        FileOutputStream output =
                                new FileOutputStream(
                                        outputFile
                                )
                ) {

                    byte[] buffer =
                            new byte[BUFFER_SIZE];

                    int read;

                    while (
                            (read = input.read(buffer))
                                    != -1
                    ) {

                        byte[] decrypted =
                                cipher.update(
                                        buffer,
                                        0,
                                        read
                                );

                        if (decrypted != null) {

                            output.write(
                                    decrypted
                            );
                        }
                    }

                    byte[] finalBytes =
                            cipher.doFinal();

                    if (finalBytes != null) {

                        output.write(
                                finalBytes
                        );
                    }

                    output.flush();
                }
            }

            return true;

        } catch (Exception e) {

            if (outputFile.exists()) {
                outputFile.delete();
            }

            return false;
        }
    }
}
