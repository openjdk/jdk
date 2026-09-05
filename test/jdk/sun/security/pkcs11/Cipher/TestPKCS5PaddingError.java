/*
 * Copyright (c) 2010, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 6687725 8365883
 * @summary Test internal PKCS5Padding impl with various error conditions.
 * @author Valerie Peng
 * @library /test/lib ..
 * @modules jdk.crypto.cryptoki
 * @run main/othervm TestPKCS5PaddingError
 */

import java.security.AlgorithmParameters;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.util.Arrays;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public class TestPKCS5PaddingError extends PKCS11Test {
    private static class CI { // class for holding Cipher Information
        String transformation;
        String keyAlgo;

        CI(String transformation, String keyAlgo) {
            this.transformation = transformation;
            this.keyAlgo = keyAlgo;
        }
    }

    private static final CI[] TEST_LIST = {
        // algorithms which use the native padding impl
        new CI("DES/CBC/PKCS5Padding", "DES"),
        new CI("DESede/CBC/PKCS5Padding", "DESede"),
        new CI("AES/CBC/PKCS5Padding", "AES"),
        // algorithms which use SunPKCS11's own padding impl
        new CI("DES/ECB/PKCS5Padding", "DES"),
        new CI("DESede/ECB/PKCS5Padding", "DESede"),
        new CI("AES/ECB/PKCS5Padding", "AES"),
    };

    private static StringBuffer debugBuf = new StringBuffer();
    private static final String sunJCEProvider =
            System.getProperty("test.provider.name", "SunJCE");

    @Override
    public void main(Provider p) throws Exception {

        // Checking for SunJCE first
        System.out.println("Checking " + sunJCEProvider + " provider");
        doTest(Security.getProvider(sunJCEProvider));

        System.out.printf("Checking %s provider%n", p.getName());
        doTest(p);
    }

    private void doTest(final Provider p) throws Exception {
        try {
            byte[] plainText = "testtexttesttext".getBytes(); // 16 bytes text

            for (CI currTest : TEST_LIST) {
                System.out.println("===" + currTest.transformation + "===");
                try {
                    KeyGenerator kg =
                            KeyGenerator.getInstance(currTest.keyAlgo, p);
                    SecretKey key = kg.generateKey();
                    // Encrypting without padding to guarantee bad padding
                    // exception when decrypting
                    Cipher c1 = Cipher.getInstance(currTest.transformation
                                    .replace("/PKCS5Padding", "/NoPadding"),
                            sunJCEProvider);
                    c1.init(Cipher.ENCRYPT_MODE, key);
                    byte[] cipherText = c1.doFinal(plainText);
                    AlgorithmParameters params = c1.getParameters();
                    Cipher c2 = Cipher.getInstance(currTest.transformation, p);
                    c2.init(Cipher.DECRYPT_MODE, key, params);

                    // 1st test: wrong output length
                    // NOTE: Skip NSS since it reports CKR_DEVICE_ERROR when the
                    // data passed to its EncryptUpdate and
                    // CKR_ENCRYPTED_DATA_LEN_RANGE when passed through
                    // DecryptUpdate are not multiple of blocks
                    if (!p.getName().equals("SunPKCS11-NSS")) {
                        try {
                            System.out.println("Testing with wrong cipherText length");
                            c2.doFinal(cipherText, 0, cipherText.length - 2);
                            throw new RuntimeException(
                                    "Expected IBSE NOT thrown");
                        } catch (IllegalBlockSizeException ibe) {
                            // expected
                        } catch (Exception ex) {
                            System.out.println("Error: Unexpected Ex " + ex);
                            throw ex;
                        }
                    }
                    // 2nd test: wrong padding value
                    try {
                        System.out.println("Testing with wrong padding bytes");
                        byte[] result = c2.doFinal(cipherText);

                        final String errorDescription =
                                "Decrypted text " + Arrays.toString(result);
                        if (Arrays.equals(result, plainText)) {
                            System.out.println("WARNING: initial text and " +
                                               "decoded text are the same");
                        }
                        System.out.println(errorDescription);
                        throw new RuntimeException(
                                "Expected BPE NOT thrown \n" + errorDescription);
                    } catch (BadPaddingException bpe) {
                        // expected
                    } catch (Exception ex) {
                        System.out.println("Error: Unexpected Ex " + ex);
                        throw ex;
                    }
                    System.out.println("DONE");
                } catch (NoSuchAlgorithmException nsae) {
                    System.out.println("Skipping unsupported algorithm: " +
                            nsae);
                }
            }
        } catch (Exception ex) {
            // print out debug info when exception is encountered
            if (debugBuf != null) {
                System.out.println(debugBuf);
                debugBuf = new StringBuffer();
            }
            throw ex;
        }
    }
    public static void main(String[] args) throws Exception {
        main(new TestPKCS5PaddingError(), args);
    }
}
