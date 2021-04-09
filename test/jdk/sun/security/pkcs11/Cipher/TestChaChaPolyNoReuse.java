/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8255410
 * @library /test/lib ..
 * @run main/othervm TestChaChaPolyNoReuse
 * @summary Test PKCS#11 ChaCha20-Poly1305 Cipher Implementation
 * (key/nonce reuse check)
 */

import java.util.*;
import javax.crypto.Cipher;
import java.security.spec.AlgorithmParameterSpec;
import java.security.Provider;
import java.security.NoSuchAlgorithmException;
import javax.crypto.spec.ChaCha20ParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.AEADBadTagException;
import javax.crypto.SecretKey;
import java.security.InvalidKeyException;
import java.security.InvalidAlgorithmParameterException;

public class TestChaChaPolyNoReuse extends PKCS11Test {

    private static final String KEY_ALGO = "ChaCha20";
    private static final String CIPHER_ALGO = "ChaCha20-Poly1305";

    /**
     * Basic TestMethod interface definition
     */
    public interface TestMethod {
        /**
         * Runs the actual test case
         *
         * @param provider the provider to provide the requested Cipher obj.
         *
         * @return true if the test passes, false otherwise.
         */
        boolean run(Provider p);
    }

    public static class TestData {
        public TestData(String name, String keyStr, String nonceStr, int ctr,
                int dir, String inputStr, String aadStr, String outStr) {
            testName = Objects.requireNonNull(name);
            HexFormat hex = HexFormat.of();
            key = hex.parseHex(keyStr);
            nonce = hex.parseHex(nonceStr);
            if ((counter = ctr) < 0) {
                throw new IllegalArgumentException(
                        "counter must be 0 or greater");
            }
            direction = dir;
            if ((direction != Cipher.ENCRYPT_MODE) &&
                    (direction != Cipher.DECRYPT_MODE)) {
                throw new IllegalArgumentException(
                        "Direction must be ENCRYPT_MODE or DECRYPT_MODE");
            }
            input = hex.parseHex(inputStr);
            aad = (aadStr != null) ? hex.parseHex(aadStr) : null;
            expOutput = hex.parseHex(outStr);
        }

        public final String testName;
        public final byte[] key;
        public final byte[] nonce;
        public final int counter;
        public final int direction;
        public final byte[] input;
        public final byte[] aad;
        public final byte[] expOutput;
    }

    public static final List<TestData> aeadTestList =
            new LinkedList<TestData>() {{
        add(new TestData("RFC 7539 Sample AEAD Test Vector",
            "808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f",
            "070000004041424344454647",
            1, Cipher.ENCRYPT_MODE,
            "4c616469657320616e642047656e746c656d656e206f662074686520636c6173" +
            "73206f66202739393a204966204920636f756c64206f6666657220796f75206f" +
            "6e6c79206f6e652074697020666f7220746865206675747572652c2073756e73" +
            "637265656e20776f756c642062652069742e",
            "50515253c0c1c2c3c4c5c6c7",
            "d31a8d34648e60db7b86afbc53ef7ec2a4aded51296e08fea9e2b5a736ee62d6" +
            "3dbea45e8ca9671282fafb69da92728b1a71de0a9e060b2905d6a5b67ecd3b36" +
            "92ddbd7f2d778b8c9803aee328091b58fab324e4fad675945585808b4831d7bc" +
            "3ff4def08e4b7a9de576d26586cec64b61161ae10b594f09e26a7e902ecbd060" +
            "0691"));
        add(new TestData("RFC 7539 A.5 Sample Decryption",
            "1c9240a5eb55d38af333888604f6b5f0473917c1402b80099dca5cbc207075c0",
            "000000000102030405060708",
            1, Cipher.DECRYPT_MODE,
            "64a0861575861af460f062c79be643bd5e805cfd345cf389f108670ac76c8cb2" +
            "4c6cfc18755d43eea09ee94e382d26b0bdb7b73c321b0100d4f03b7f355894cf" +
            "332f830e710b97ce98c8a84abd0b948114ad176e008d33bd60f982b1ff37c855" +
            "9797a06ef4f0ef61c186324e2b3506383606907b6a7c02b0f9f6157b53c867e4" +
            "b9166c767b804d46a59b5216cde7a4e99040c5a40433225ee282a1b0a06c523e" +
            "af4534d7f83fa1155b0047718cbc546a0d072b04b3564eea1b422273f548271a" +
            "0bb2316053fa76991955ebd63159434ecebb4e466dae5a1073a6727627097a10" +
            "49e617d91d361094fa68f0ff77987130305beaba2eda04df997b714d6c6f2c29" +
            "a6ad5cb4022b02709beead9d67890cbb22392336fea1851f38",
            "f33388860000000000004e91",
            "496e7465726e65742d4472616674732061726520647261667420646f63756d65" +
            "6e74732076616c696420666f722061206d6178696d756d206f6620736978206d" +
            "6f6e74687320616e64206d617920626520757064617465642c207265706c6163" +
            "65642c206f72206f62736f6c65746564206279206f7468657220646f63756d65" +
            "6e747320617420616e792074696d652e20497420697320696e617070726f7072" +
            "6961746520746f2075736520496e7465726e65742d4472616674732061732072" +
            "65666572656e6365206d6174657269616c206f7220746f206369746520746865" +
            "6d206f74686572207468616e206173202fe2809c776f726b20696e2070726f67" +
            "726573732e2fe2809d"));
    }};

    /**
     * Make sure we do not use this Cipher object without initializing it
     * at all
     */
    public static final TestMethod noInitTest = new TestMethod() {
        @Override
        public boolean run(Provider p) {
            System.out.println("----- No Init Test -----");
            try {
                Cipher cipher = Cipher.getInstance(CIPHER_ALGO, p);
                TestData testData = aeadTestList.get(0);

                // Attempting to use the cipher without initializing it
                // should throw an IllegalStateException
                try {
                    cipher.updateAAD(testData.aad);
                    throw new RuntimeException(
                            "Expected IllegalStateException not thrown");
                } catch (IllegalStateException ise) {
                    // Do nothing, this is what we expected to happen
                }
            } catch (Exception exc) {
                System.out.println("Unexpected exception: " + exc);
                exc.printStackTrace();
                return false;
            }

            return true;
        }
    };

    /**
     * Attempt to run two full encryption operations without an init in
     * between.
     */
    public static final TestMethod encTwiceNoInit = new TestMethod() {
        @Override
        public boolean run(Provider p) {
            System.out.println("----- Encrypt 2nd time without init -----");
            try {
                AlgorithmParameterSpec spec;
                Cipher cipher = Cipher.getInstance(CIPHER_ALGO, p);
                TestData testData = aeadTestList.get(0);
                spec = new IvParameterSpec(testData.nonce);
                SecretKey key = new SecretKeySpec(testData.key, KEY_ALGO);

                // Initialize and encrypt
                cipher.init(testData.direction, key, spec);
                cipher.updateAAD(testData.aad);
                cipher.doFinal(testData.input);
                System.out.println("First encryption complete");

                // Now attempt to encrypt again without changing the key/IV
                // This should fail.
                try {
                    cipher.updateAAD(testData.aad);
                } catch (IllegalStateException ise) {
                    // Do nothing, this is what we expected to happen
                }
                try {
                    cipher.doFinal(testData.input);
                    throw new RuntimeException(
                            "Expected IllegalStateException not thrown");
                } catch (IllegalStateException ise) {
                    // Do nothing, this is what we expected to happen
                }
            } catch (Exception exc) {
                System.out.println("Unexpected exception: " + exc);
                exc.printStackTrace();
                return false;
            }

            return true;
        }
    };

    /**
     * Attempt to run two full decryption operations without an init in
     * between.
     */
    public static final TestMethod decTwiceNoInit = new TestMethod() {

        @Override
        public boolean run(Provider p) {
            System.out.println("----- Decrypt 2nd time without init -----");
            try {
                AlgorithmParameterSpec spec;
                Cipher cipher = Cipher.getInstance(CIPHER_ALGO, p);
                TestData testData = aeadTestList.get(1);
                spec = new IvParameterSpec(testData.nonce);
                SecretKey key = new SecretKeySpec(testData.key, KEY_ALGO);

                // Initialize and encrypt
                cipher.init(testData.direction, key, spec);
                cipher.updateAAD(testData.aad);
                cipher.doFinal(testData.input);
                System.out.println("First decryption complete");

                // Now attempt to encrypt again without changing the key/IV
                // This should fail.
                try {
                    cipher.updateAAD(testData.aad);
                } catch (IllegalStateException ise) {
                    // Do nothing, this is what we expected to happen
                }
                try {
                    cipher.doFinal(testData.input);
                    throw new RuntimeException(
                            "Expected IllegalStateException not thrown");
                } catch (IllegalStateException ise) {
                    // Do nothing, this is what we expected to happen
                }
            } catch (Exception exc) {
                System.out.println("Unexpected exception: " + exc);
                exc.printStackTrace();
                return false;
            }

            return true;
        }
    };

    /**
     * Perform an AEAD decryption with corrupted data so the tag does not
     * match.  Then attempt to reuse the cipher without initialization.
     */
    public static final TestMethod decFailNoInit = new TestMethod() {
        @Override
        public boolean run(Provider p) {
            System.out.println(
                    "----- Fail decryption, try again with no init -----");
            try {
                TestData testData = aeadTestList.get(1);
                AlgorithmParameterSpec spec =
                        new IvParameterSpec(testData.nonce);
                byte[] corruptInput = testData.input.clone();
                corruptInput[0]++;      // Corrupt the ciphertext
                SecretKey key = new SecretKeySpec(testData.key, KEY_ALGO);
                Cipher cipher = Cipher.getInstance(CIPHER_ALGO, p);

                try {
                    // Initialize and encrypt
                    cipher.init(testData.direction, key, spec);
                    cipher.updateAAD(testData.aad);
                    cipher.doFinal(corruptInput);
                    throw new RuntimeException(
                            "Expected AEADBadTagException not thrown");
                } catch (AEADBadTagException abte) {
                    System.out.println("Expected decryption failure occurred");
                }

                // Make sure that despite the exception, the Cipher object is
                // not in a state that would leave it initialized and able
                // to process future decryption operations without init.
                try {
                    cipher.updateAAD(testData.aad);
                    cipher.doFinal(testData.input);
                    throw new RuntimeException(
                            "Expected IllegalStateException not thrown");
                } catch (IllegalStateException ise) {
                    // Do nothing, this is what we expected to happen
                }
            } catch (Exception exc) {
                System.out.println("Unexpected exception: " + exc);
                exc.printStackTrace();
                return false;
            }

            return true;
        }
    };

    /**
     * Encrypt once successfully, then attempt to init with the same
     * key and nonce.
     */
    public static final TestMethod encTwiceInitSameParams = new TestMethod() {
        @Override
        public boolean run(Provider p) {
            System.out.println("----- Encrypt, then init with same params " +
                     "-----");
            try {
                AlgorithmParameterSpec spec;
                Cipher cipher = Cipher.getInstance(CIPHER_ALGO, p);
                TestData testData = aeadTestList.get(0);
                spec = new IvParameterSpec(testData.nonce);
                SecretKey key = new SecretKeySpec(testData.key, KEY_ALGO);

                // Initialize then encrypt
                cipher.init(testData.direction, key, spec);
                cipher.updateAAD(testData.aad);
                cipher.doFinal(testData.input);
                System.out.println("First encryption complete");

                // Initializing after the completed encryption with
                // the same key and nonce should fail.
                try {
                    cipher.init(testData.direction, key, spec);
                    throw new RuntimeException(
                            "Expected IKE or IAPE not thrown");
                } catch (InvalidKeyException |
                        InvalidAlgorithmParameterException e) {
                    // Do nothing, this is what we expected to happen
                }
            } catch (Exception exc) {
                System.out.println("Unexpected exception: " + exc);
                exc.printStackTrace();
                return false;
            }

            return true;
        }
    };

    /**
     * Decrypt once successfully, then attempt to init with the same
     * key and nonce.
     */
    public static final TestMethod decTwiceInitSameParams = new TestMethod() {
        @Override
        public boolean run(Provider p) {
            System.out.println("----- Decrypt, then init with same params " +
                    "-----");
            try {
                AlgorithmParameterSpec spec;
                Cipher cipher = Cipher.getInstance(CIPHER_ALGO, p);
                TestData testData = aeadTestList.get(1);
                spec = new IvParameterSpec(testData.nonce);
                SecretKey key = new SecretKeySpec(testData.key, KEY_ALGO);

                // Initialize then decrypt
                cipher.init(testData.direction, key, spec);
                cipher.updateAAD(testData.aad);
                cipher.doFinal(testData.input);
                System.out.println("First decryption complete");

                // Initializing after the completed decryption with
                // the same key and nonce should fail.
                try {
                    cipher.init(testData.direction, key, spec);
                    throw new RuntimeException(
                            "Expected IKE or IAPE not thrown");
                } catch (InvalidKeyException |
                        InvalidAlgorithmParameterException e) {
                    // Do nothing, this is what we expected to happen
                }
            } catch (Exception exc) {
                System.out.println("Unexpected exception: " + exc);
                exc.printStackTrace();
                return false;
            }

            return true;
        }
    };

    public static final List<TestMethod> testMethodList =
            Arrays.asList(noInitTest, encTwiceNoInit,
                    decTwiceNoInit, decFailNoInit, encTwiceInitSameParams,
                    decTwiceInitSameParams);

    @Override
    public void main(Provider p) throws Exception {
        System.out.println("Testing " + p.getName());
        try {
            Cipher.getInstance(CIPHER_ALGO, p);
        } catch (NoSuchAlgorithmException nsae) {
            System.out.println("Skip; no support for " + CIPHER_ALGO);
            return;
        }

        int testsPassed = 0;
        int testNumber = 0;

        for (TestMethod tm : testMethodList) {
            testNumber++;
            boolean result = tm.run(p);
            System.out.println("Result: " + (result ? "PASS" : "FAIL"));
            if (result) {
                testsPassed++;
            }
        }

        System.out.println("Total Tests: " + testNumber +
                ", Tests passed: " + testsPassed);
        if (testsPassed < testNumber) {
            throw new RuntimeException(
                    "Not all tests passed.  See output for failure info");
        }
    }

    public static void main(String[] args) throws Exception {
        main(new TestChaChaPolyNoReuse(), args);
    }
}
