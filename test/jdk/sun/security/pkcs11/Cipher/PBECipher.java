/*
 * Copyright (c) 2023, Red Hat, Inc.
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
 * @bug 8301553
 * @summary test password based encryption on SunPKCS11's Cipher service
 * @library /test/lib ..
 * @run main/othervm/timeout=30 PBECipher
 */

public final class PBECipher extends PKCS11Test {
    private static final char[] password = "123456".toCharArray();
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

        // Derive a key using SunPKCS11's SecretKeyFactory (wrapping password,
        // salt and iterations in a PBEKeySpec), and pass it to a Cipher.
        SecretKeyFactoryDerivedKey,

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
                    "AES/CBC/PKCS5Padding", "ba1c9614d550912925d99e0bc8969032" +
                    "7ac6258b72117dcf750c19ee6ca73dd4"),
            cipherAssertionData("PBEWithHmacSHA224AndAES_128",
                    "AES/CBC/PKCS5Padding", "41960c43ca99cf2184511aaf2f0508a9" +
                    "7da3762ee6c2b7e2027c8076811f2e52"),
            cipherAssertionData("PBEWithHmacSHA256AndAES_128",
                    "AES/CBC/PKCS5Padding", "6bb6a3dc3834e81e5ca6b5e70073ff46" +
                    "903b188940a269ed26db2ffe622b8e16"),
            cipherAssertionData("PBEWithHmacSHA384AndAES_128",
                    "AES/CBC/PKCS5Padding", "22aabf7a6a059415dc4ca7d985f3de06" +
                    "8f8300ca48d8de585d802670f4c1d9bd"),
            cipherAssertionData("PBEWithHmacSHA512AndAES_128",
                    "AES/CBC/PKCS5Padding", "b523e7c462a0b7fd74e492b3a6550464" +
                    "ceebe81f08649ae163673afc242ad8a2"),
            cipherAssertionData("PBEWithHmacSHA1AndAES_256",
                    "AES/CBC/PKCS5Padding", "1e7c25e166afae069cec68ef9affca61" +
                    "aea02ab1c3dc7471cb767ed7d6e37af0"),
            cipherAssertionData("PBEWithHmacSHA224AndAES_256",
                    "AES/CBC/PKCS5Padding", "6701f1cc75b6494ec4bd27158aa2c15d" +
                    "7d10bc2f1fbb7d92d8277c7edfd1dd57"),
            cipherAssertionData("PBEWithHmacSHA256AndAES_256",
                    "AES/CBC/PKCS5Padding", "f82eb2fc016505baeb23ecdf85163933" +
                    "5e8d6d48b48631185641febb75898a1d"),
            cipherAssertionData("PBEWithHmacSHA384AndAES_256",
                    "AES/CBC/PKCS5Padding", "ee9528022e58cdd9be80cd88443e03b3" +
                    "de13376cf97c53d946d5c5dfc88097be"),
            cipherAssertionData("PBEWithHmacSHA512AndAES_256",
                    "AES/CBC/PKCS5Padding", "18f472912ffaa31824e20a5486324e14" +
                    "0225e20cb158762e8647b1216fe0ab7e"),
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
            case SecretKeyFactoryDerivedKey -> {
                SecretKey key = getDerivedSecretKey(p, keyAlgo);
                cipher.init(Cipher.ENCRYPT_MODE, key,
                        pbeSpec.getParameterSpec());
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

    private static SecretKey getDerivedSecretKey(Provider sunPKCS11,
            String algorithm) throws GeneralSecurityException {
        return SecretKeyFactory.getInstance(algorithm, sunPKCS11)
                .generateSecret(new PBEKeySpec(password, salt, iterations));
    }

    private static SecretKey getAnonymousPBEKey(String algorithm,
            boolean isPbeCipherSvc) {
        return new PBEKey() {
            public byte[] getSalt() { return salt.clone(); }
            public int getIterationCount() { return iterations; }
            public String getAlgorithm() { return algorithm; }
            public String getFormat() { return "RAW"; }
            public char[] getPassword() { return password.clone(); }
            public byte[] getEncoded() {
                byte[] encodedKey = null;
                if (isPbeCipherSvc) {
                    encodedKey = new byte[password.length];
                    for (int i = 0; i < password.length; i++) {
                        encodedKey[i] = (byte) (password[i] & 0x7f);
                    }
                }
                return encodedKey;
            }
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
