package com.vishalsecure.app;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class CryptoManager {

    private static final String TAG = "VishalSecure";

    private static final String KEYSTORE_NAME =
            "AndroidKeyStore";

    private static final String KEY_ALIAS =
            "VishalSecureVideoKey";

    private static final String TRANSFORMATION =
            "AES/GCM/NoPadding";

    private static final int IV_LENGTH = 12;

    private static final int BUFFER_SIZE =
            1024 * 1024;

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

            if (entry instanceof KeyStore.SecretKeyEntry) {

                return ((KeyStore.SecretKeyEntry) entry)
                        .getSecretKey();
            }

            /*
             * अगर पुराना alias सही SecretKey नहीं है,
             * तो उसे हटाकर नया AES key बनाया जाएगा।
             */
            keyStore.deleteEntry(KEY_ALIAS);
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
                !inputFile.exists() ||
                !inputFile.isFile()) {

            Log.e(
                    TAG,
                    "Encryption failed: input file missing"
            );

            return false;
        }

        try {

            SecretKey key =
                    getOrCreateKey();

            Cipher cipher =
                    Cipher.getInstance(
                            TRANSFORMATION
                    );

            /*
             * IMPORTANT:
             *
             * Android Keystore को खुद secure random IV
             * generate करने दिया जा रहा है।
             *
             * इससे कुछ devices पर होने वाली
             * GCM IV initialization समस्या से बचेंगे।
             */
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    key
            );

            byte[] iv =
                    cipher.getIV();

            if (iv == null ||
                    iv.length != IV_LENGTH) {

                Log.e(
                        TAG,
                        "Encryption failed: invalid IV"
                );

                return false;
            }

            File parent =
                    outputFile.getParentFile();

            if (parent != null &&
                    !parent.exists()) {

                if (!parent.mkdirs() &&
                        !parent.exists()) {

                    Log.e(
                            TAG,
                            "Encryption failed: cannot create directory"
                    );

                    return false;
                }
            }

            if (outputFile.exists()) {
                outputFile.delete();
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
                 * File की शुरुआत में 12-byte IV रहेगा।
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

                    if (encrypted != null &&
                            encrypted.length > 0) {

                        output.write(
                                encrypted
                        );
                    }
                }

                /*
                 * GCM authentication tag भी
                 * doFinal() में सुरक्षित रूप से जुड़ता है।
                 */
                byte[] finalBytes =
                        cipher.doFinal();

                if (finalBytes != null &&
                        finalBytes.length > 0) {

                    output.write(
                            finalBytes
                    );
                }

                output.flush();
            }

            if (!outputFile.exists() ||
                    outputFile.length() <= IV_LENGTH) {

                Log.e(
                        TAG,
                        "Encryption failed: output file invalid"
                );

                if (outputFile.exists()) {
                    outputFile.delete();
                }

                return false;
            }

            Log.d(
                    TAG,
                    "Encryption successful: "
                            + outputFile.getAbsolutePath()
            );

            return true;

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Encryption failed",
                    e
            );

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
                !encryptedFile.exists() ||
                !encryptedFile.isFile()) {

            Log.e(
                    TAG,
                    "Decryption failed: encrypted file missing"
            );

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

                    Log.e(
                            TAG,
                            "Decryption failed: invalid IV"
                    );

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

                    if (!parent.mkdirs() &&
                            !parent.exists()) {

                        Log.e(
                                TAG,
                                "Decryption failed: cannot create directory"
                        );

                        return false;
                    }
                }

                if (outputFile.exists()) {
                    outputFile.delete();
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

                        if (decrypted != null &&
                                decrypted.length > 0) {

                            output.write(
                                    decrypted
                            );
                        }
                    }

                    /*
                     * GCM authentication check यहाँ होगा।
                     */
                    byte[] finalBytes =
                            cipher.doFinal();

                    if (finalBytes != null &&
                            finalBytes.length > 0) {

                        output.write(
                                finalBytes
                        );
                    }

                    output.flush();
                }
            }

            if (!outputFile.exists()) {

                Log.e(
                        TAG,
                        "Decryption failed: output missing"
                );

                return false;
            }

            Log.d(
                    TAG,
                    "Decryption successful"
            );

            return true;

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Decryption failed",
                    e
            );

            if (outputFile.exists()) {
                outputFile.delete();
            }

            return false;
        }
    }
}
