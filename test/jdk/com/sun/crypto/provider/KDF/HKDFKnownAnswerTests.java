/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8331008
 * @run main HKDFKnownAnswerTests
 * @summary Tests for HKDF Expand and Extract Key Derivation Functions
 */

import javax.crypto.KDF;
import javax.crypto.SecretKey;
import javax.crypto.spec.HKDFParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public class HKDFKnownAnswerTests {
    public static class TestData {
        public TestData(String name, String algStr, String ikmStr,
                        String saltStr, String infoStr, int oLen,
                        String expPrkStr,
                        String expOkmStr) {
            testName = Objects.requireNonNull(name);
            algName = Objects.requireNonNull(algStr);
            ikm = HexFormat.of().parseHex(Objects.requireNonNull(ikmStr));
            if ((outLen = oLen) <= 0) {
                throw new IllegalArgumentException(
                    "Output length must be greater than 0");
            }
            expectedPRK = HexFormat.of().parseHex(Objects.requireNonNull(expPrkStr));
            expectedOKM = HexFormat.of().parseHex(Objects.requireNonNull(expOkmStr));

            // Non-mandatory fields - may be null
            salt = (saltStr != null) ? HexFormat.of().parseHex(saltStr) : new byte[0];
            info = (infoStr != null) ? HexFormat.of().parseHex(infoStr) : null;
        }

        public final String testName;
        public final String algName;
        public final byte[] ikm;
        public final byte[] salt;
        public final byte[] info;
        public final int outLen;
        public final byte[] expectedPRK;
        public final byte[] expectedOKM;
    }

    public static final List<TestData> testList = new LinkedList<TestData>() {{
        add(new TestData("RFC 5869 Test Case 1", "HKDF-SHA256",
                         "0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b",
                         "000102030405060708090a0b0c",
                         "f0f1f2f3f4f5f6f7f8f9",
                         42,
                         "077709362c2e32df0ddc3f0dc47bba6390b6c73bb50f9c3122ec844ad7c2b3e5",
                         "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf" +
                         "34007208d5b887185865"));
        add(new TestData("RFC 5869 Test Case 2", "HKDF-SHA256",
                         "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f" +
                         "202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f" +
                         "404142434445464748494a4b4c4d4e4f",
                         "606162636465666768696a6b6c6d6e6f707172737475767778797a7b7c7d7e7f" +
                         "808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f" +
                         "a0a1a2a3a4a5a6a7a8a9aaabacadaeaf",
                         "b0b1b2b3b4b5b6b7b8b9babbbcbdbebfc0c1c2c3c4c5c6c7c8c9cacbcccdcecf" +
                         "d0d1d2d3d4d5d6d7d8d9dadbdcdddedfe0e1e2e3e4e5e6e7e8e9eaebecedeeef" +
                         "f0f1f2f3f4f5f6f7f8f9fafbfcfdfeff",
                         82,
                         "06a6b88c5853361a06104c9ceb35b45cef760014904671014a193f40c15fc244",
                         "b11e398dc80327a1c8e7f78c596a49344f012eda2d4efad8a050cc4c19afa97c" +
                         "59045a99cac7827271cb41c65e590e09da3275600c2f09b8367793a9aca3db71" +
                         "cc30c58179ec3e87c14c01d5c1f3434f1d87"));
        add(new TestData("RFC 5869 Test Case 3", "HKDF-SHA256",
                         "0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b",
                         new String(new byte[0]), null, 42,
                         "19ef24a32c717b167f33a91d6f648bdf96596776afdb6377ac434c1c293ccb04",
                         "8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d" +
                         "9d201395faa4b61a96c8"));
    }};

    public static void main(String args[]) throws Exception {
        int testsPassed = 0;

        int testNo = 0;
        for (TestData test : testList) {
            System.out.println("*** Test " + ++testNo + ": " +
                               test.testName);
            if (runVector(test)) {
                testsPassed++;
            }
        }

        System.out.println("Total tests: " + testList.size() +
                           ", Passed: " + testsPassed + ", Failed: " +
                           (testList.size() - testsPassed));
        if (testsPassed != testList.size()) {
            throw new RuntimeException("One or more tests failed.  " +
                                       "Check output for details");
        }
    }

    private static boolean runVector(TestData testData)
        throws InvalidParameterSpecException,
               InvalidAlgorithmParameterException,
               NoSuchAlgorithmException {
        String kdfName, prfName;
        KDF kdfHkdf, kdfExtract, kdfExpand;
        boolean result = true;
        SecretKey actualPRK;
        SecretKey actualOKM;
        byte[] deriveData;

        try {
            kdfHkdf = KDF.getInstance(testData.algName);
            kdfExtract = KDF.getInstance(testData.algName);
            kdfExpand = KDF.getInstance(testData.algName);
        } catch (NoSuchAlgorithmException nsae) {
            InvalidParameterSpecException exc =
                new InvalidParameterSpecException();
            exc.initCause(nsae);
            throw exc;
        }

        // Set up the input keying material
        SecretKey ikmKey = new SecretKeySpec(testData.ikm, "HKDF-IKM");

        // *** HKDF-Extract-only testing
        // Create KDFParameterSpec for the Extract-only operation
        AlgorithmParameterSpec derivationSpecExtract =
            HKDFParameterSpec.ofExtract().addIKM(ikmKey)
                             .addSalt(testData.salt)
                             .extractOnly();
        actualPRK = kdfExtract.deriveKey("Generic", derivationSpecExtract);

        // Re-run the KDF to give us raw output data
        deriveData = kdfExtract.deriveData(derivationSpecExtract);

        System.out.println("* HKDF-Extract-Only:");
        result &= compareKeyAndData(actualPRK, deriveData,
                                    testData.expectedPRK);

        // *** HKDF Expand-Only testing
        // For these tests, we'll use the actualPRK as the input key
        // Create KDFParameterSpec for key output and raw byte output
        AlgorithmParameterSpec derivationSpecExpand = HKDFParameterSpec.expandOnly(
            actualPRK, testData.info,
            testData.outLen);
        actualOKM = kdfExpand.deriveKey("Generic", derivationSpecExpand);

        // Re-run the KDF to give us raw output data
        deriveData = kdfExpand.deriveData(derivationSpecExpand);

        System.out.println("* HKDF-Expand-Only:");
        result &= compareKeyAndData(actualOKM, deriveData,
                                    testData.expectedOKM);

        // *** HKDF Extract-then-Expand testing
        // We can reuse the KDFParameterSpec from the Expand-only test

        // Use the KDF to make us a key
        AlgorithmParameterSpec derivationSpecExtractExpand =
            HKDFParameterSpec.ofExtract().addIKM(ikmKey)
                             .addSalt(testData.salt)
                             .thenExpand(testData.info,
                                         testData.outLen);
        actualOKM = kdfHkdf.deriveKey("Generic", derivationSpecExtractExpand);

        // Re-run the KDF to give us raw output data
        deriveData = kdfHkdf.deriveData(derivationSpecExtractExpand);

        System.out.println("* HKDF-Extract-then-Expand:");
        result &= compareKeyAndData(actualOKM, deriveData,
                                    testData.expectedOKM);

        return result;
    }

    /**
     * Compare key-based and data-based productions from the KDF against an
     * expected output value.
     *
     * @param outKey
     *     the KDF output in key form
     * @param outData
     *     the KDF output as raw bytes
     * @param expectedOut
     *     the expected value
     *
     * @return true if the underlying data for outKey, outData and expectedOut
     * are the same.
     */
    private static boolean compareKeyAndData(Key outKey, byte[] outData,
                                             byte[] expectedOut) {
        boolean result = true;

        if (Arrays.equals(outKey.getEncoded(), expectedOut)) {
            System.out.println("\t* Key output: Pass");
        } else {
            result = false;
            System.out.println("\t* Key output: FAIL");
            System.out.println("Expected:\n" +
                               dumpHexBytes(expectedOut, 16, "\n", " "));
            System.out.println("Actual:\n" +
                               dumpHexBytes(outKey.getEncoded(), 16, "\n",
                                            " "));
            System.out.println();
        }

        if (Arrays.equals(outData, expectedOut)) {
            System.out.println("\t* Data output: Pass");
        } else {
            result = false;
            System.out.println("\t* Data output: FAIL");
            System.out.println("Expected:\n" +
                               dumpHexBytes(expectedOut, 16, "\n", " "));
            System.out.println("Actual:\n" +
                               dumpHexBytes(outData, 16, "\n", " "));
            System.out.println();
        }

        return result;
    }

    /**
     * Dump the hex bytes of a buffer into string form.
     *
     * @param data
     *     The array of bytes to dump to stdout.
     * @param itemsPerLine
     *     The number of bytes to display per line if the {@code lineDelim}
     *     character is blank then all bytes will be printed on a single line.
     * @param lineDelim
     *     The delimiter between lines
     * @param itemDelim
     *     The delimiter between bytes
     *
     * @return The hexdump of the byte array
     */
    private static String dumpHexBytes(byte[] data, int itemsPerLine,
                                       String lineDelim, String itemDelim) {
        StringBuilder sb = new StringBuilder();
        if (data != null) {
            for (int i = 0; i < data.length; i++) {
                if (i % itemsPerLine == 0 && i != 0) {
                    sb.append(lineDelim);
                }
                sb.append(String.format("%02X", data[i])).append(itemDelim);
            }
        }

        return sb.toString();
    }
}
