/*
 * Copyright (c) 2023, 2025, Red Hat, Inc.
 *
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

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.interfaces.PBEKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;

/*
 * @test
 * @bug 8301553 8348732
 * @summary test password based encryption on SunPKCS11's Cipher service
 * @library /test/lib ..
 * @run main/othervm/timeout=30 PBECipher
 */

public final class PBECipher extends PKCS11Test {
    private static final char[] password = "123456\uA4F7".toCharArray();
    private static final byte[] salt = "abcdefgh".getBytes(
            StandardCharsets.UTF_8);
    private static final int iterations = 1000;
    private static final int AES_BLOCK_SIZE = 16;
    private static final PBEParameterSpec pbeSpec = new PBEParameterSpec(salt,
            iterations, new IvParameterSpec(new byte[AES_BLOCK_SIZE]));
    private static final String plainText = "This is a known plain text!";
    private static final String sep = "======================================" +
            "===================================";

    private enum Configuration {
        // Pass salt and iterations to a Cipher through a PBEParameterSpec.
        PBEParameterSpec,

        // Pass salt and iterations to a Cipher through an AlgorithmParameters.
        AlgorithmParameters,

        // Pass password, salt and iterations and iterations to
        // a Cipher through an anonymous class implementing the
        // javax.crypto.interfaces.PBEKey interface.
        AnonymousPBEKey,
    }

    private static Provider sunJCE = Security.getProvider(
                            System.getProperty("test.provider.name", "SunJCE"));

    private record AssertionData(String pbeCipherAlgo, String cipherAlgo,
            BigInteger expectedCiphertext) {}

    private static AssertionData cipherAssertionData(String pbeCipherAlgo,
            String cipherAlgo, String staticExpectedCiphertextString) {
        BigInteger staticExpectedCiphertext =
                new BigInteger(staticExpectedCiphertextString, 16);
        BigInteger expectedCiphertext = null;
        if (sunJCE != null) {
            try {
                expectedCiphertext = computeCipherText(sunJCE, pbeCipherAlgo,
                        pbeCipherAlgo, Configuration.PBEParameterSpec);
                checkAssertionValues(expectedCiphertext,
                        staticExpectedCiphertext);
            } catch (GeneralSecurityException e) {
                // Move to staticExpectedCiphertext as it's unlikely
                // that any of the algorithms are available.
                sunJCE = null;
            }
        }
        if (expectedCiphertext == null) {
            expectedCiphertext = staticExpectedCiphertext;
        }
        return new AssertionData(pbeCipherAlgo, cipherAlgo, expectedCiphertext);
    }

    private static void checkAssertionValues(BigInteger expectedValue,
            BigInteger staticExpectedValue) {
        if (!expectedValue.equals(staticExpectedValue)) {
            printHex("SunJCE value", expectedValue);
            printHex("Static value", staticExpectedValue);
            throw new Error("Static and SunJCE values do not match.");
        }
    }

    // Generated with SunJCE.
    private static final AssertionData[] assertionData = new AssertionData[]{
            cipherAssertionData("PBEWithHmacSHA1AndAES_128",
                    "AES/CBC/PKCS5Padding", "9e097796e8d8224f2a7f5c950677d879" +
                    "c0c578340147c7ae357550e2f4d4c6ce"),
            cipherAssertionData("PBEWithHmacSHA224AndAES_128",
                    "AES/CBC/PKCS5Padding", "7b915941d8e3a87c00e2fbd8ad67a578" +
                    "9a25648933b737706de4e4d48bdb61b6"),
            cipherAssertionData("PBEWithHmacSHA256AndAES_128",
                    "AES/CBC/PKCS5Padding", "c23912d15599908f47cc32c9da56b37f" +
                    "e41e958e9c3a6c6e4e631a2a9e6cd20f"),
            cipherAssertionData("PBEWithHmacSHA384AndAES_128",
                    "AES/CBC/PKCS5Padding", "f05c6b2dea545d59f2a6fde845170dd6" +
                    "7aebd6b1cc28904699d7dcff1a0a238c"),
            cipherAssertionData("PBEWithHmacSHA512AndAES_128",
                    "AES/CBC/PKCS5Padding", "949c0c01a29375b9d421f6e2bf6ed0d7" +
                    "15a118e0980494797d3a3b799b67daf6"),
            cipherAssertionData("PBEWithHmacSHA1AndAES_256",
                    "AES/CBC/PKCS5Padding", "7bd686b15bc09e5fb5aa1f881c92aa5a" +
                    "e72bdcd864c74e62395b9aaea7443bcd"),
            cipherAssertionData("PBEWithHmacSHA224AndAES_256",
                    "AES/CBC/PKCS5Padding", "df58a1b26cca7e9e297da61ada03ddc4" +
                    "39d2a5699753433f19891de33f8741a2"),
            cipherAssertionData("PBEWithHmacSHA256AndAES_256",
                    "AES/CBC/PKCS5Padding", "f6ae5a15ec2c18eaa25927858f1da990" +
                    "6df58a3b4830dbaaaa4c4317e53d717d"),
            cipherAssertionData("PBEWithHmacSHA384AndAES_256",
                    "AES/CBC/PKCS5Padding", "5795625f51ec701594506944e5ed79f0" +
                    "c9d8e82319762f00f8ff06a8b6195ac4"),
            cipherAssertionData("PBEWithHmacSHA512AndAES_256",
                    "AES/CBC/PKCS5Padding", "ddf55933f80f42f2a8d4e8726290766e" +
                    "024f225b76b594e8005c00227d553d05"),
    };

    private static final class NoRandom extends SecureRandom {
        @Override
        public void nextBytes(byte[] bytes) {}
    }

    public void main(Provider sunPKCS11) throws Exception {
        System.out.println("SunPKCS11: " + sunPKCS11.getName());
        for (Configuration conf : Configuration.values()) {
            for (AssertionData data : assertionData) {
                testWith(sunPKCS11, data, true, conf);
                if (conf != Configuration.PBEParameterSpec &&
                        conf != Configuration.AlgorithmParameters) {
                    testWith(sunPKCS11, data, false, conf);
                }
            }
        }
        System.out.println("TEST PASS - OK");
    }

    private static void testWith(Provider sunPKCS11, AssertionData data,
            boolean testPBEService, Configuration conf) throws Exception {
        String svcAlgo = testPBEService ? data.pbeCipherAlgo : data.cipherAlgo;
        System.out.println(sep + System.lineSeparator() + svcAlgo
                + " (with " + conf.name() + ")");

        BigInteger cipherText = computeCipherText(sunPKCS11, svcAlgo,
                data.pbeCipherAlgo, conf);
        printHex("Cipher Text", cipherText);

        if (!cipherText.equals(data.expectedCiphertext)) {
            printHex("Expected Cipher Text", data.expectedCiphertext);
            throw new Exception("Expected Cipher Text did not match");
        }
    }

    private static BigInteger computeCipherText(Provider p, String svcAlgo,
            String keyAlgo, Configuration conf)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(svcAlgo, p);
        switch (conf) {
            case PBEParameterSpec, AlgorithmParameters -> {
                SecretKey key = getPasswordOnlyPBEKey();
                switch (conf) {
                    case PBEParameterSpec -> {
                        cipher.init(Cipher.ENCRYPT_MODE, key, pbeSpec);
                    }
                    case AlgorithmParameters -> {
                        AlgorithmParameters algoParams =
                                AlgorithmParameters.getInstance("PBES2");
                        algoParams.init(pbeSpec);
                        cipher.init(Cipher.ENCRYPT_MODE, key, algoParams);
                    }
                }
            }
            case AnonymousPBEKey -> {
                SecretKey key = getAnonymousPBEKey(keyAlgo,
                        svcAlgo.equals(keyAlgo));
                cipher.init(Cipher.ENCRYPT_MODE, key, new NoRandom());
            }
        }
        return new BigInteger(1, cipher.doFinal(
                plainText.getBytes(StandardCharsets.UTF_8)));
    }

    private static SecretKey getPasswordOnlyPBEKey()
            throws GeneralSecurityException {
        return SecretKeyFactory.getInstance("PBE")
                .generateSecret(new PBEKeySpec(password));
    }

    private static SecretKey getAnonymousPBEKey(String algorithm,
            boolean isPbeCipherSvc) throws GeneralSecurityException {
        byte[] enc = (isPbeCipherSvc ?
                    getPasswordOnlyPBEKey().getEncoded() : null);
        return new PBEKey() {
            public byte[] getSalt() { return salt.clone(); }
            public int getIterationCount() { return iterations; }
            public String getAlgorithm() { return algorithm; }
            public String getFormat() { return "RAW"; }
            public char[] getPassword() { return password.clone(); }
            public byte[] getEncoded() { return enc; }
        };
    }

    private static void printHex(String title, BigInteger b) {
        String repr = (b == null) ? "buffer is null" : b.toString(16);
        System.out.println(title + ": " + repr + System.lineSeparator());
    }

    public static void main(String[] args) throws Exception {
        main(new PBECipher());
    }
}
