/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package compiler.intrinsics.chacha;

import javax.crypto.Cipher;
import javax.crypto.spec.ChaCha20ParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.util.*;

public class ExerciseChaCha20 {

    private static final int WARMUP_CYCLES = 200000;

    // Use the test vectors from RFC 7539 to exercise the ChaCha20 block
    // intrinsic
    public static final List<TestData> testList = List.of(
        new TestData("RFC 7539 Sample Test Vector",
            "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f",
            "000000000000004a00000000",
            1, Cipher.ENCRYPT_MODE,
            "4c616469657320616e642047656e746c656d656e206f662074686520636c6173" +
            "73206f66202739393a204966204920636f756c64206f6666657220796f75206f" +
            "6e6c79206f6e652074697020666f7220746865206675747572652c2073756e73" +
            "637265656e20776f756c642062652069742e",
            null,
            "6e2e359a2568f98041ba0728dd0d6981e97e7aec1d4360c20a27afccfd9fae0b" +
            "f91b65c5524733ab8f593dabcd62b3571639d624e65152ab8f530c359f0861d8" +
            "07ca0dbf500d6a6156a38e088a22b65e52bc514d16ccf806818ce91ab7793736" +
            "5af90bbf74a35be6b40b8eedf2785e42874d"),
        new TestData("RFC 7539 Test Vector 1 (all zeroes)",
            "0000000000000000000000000000000000000000000000000000000000000000",
            "000000000000000000000000",
            0, Cipher.ENCRYPT_MODE,
            "0000000000000000000000000000000000000000000000000000000000000000" +
            "0000000000000000000000000000000000000000000000000000000000000000",
            null,
            "76b8e0ada0f13d90405d6ae55386bd28bdd219b8a08ded1aa836efcc8b770dc7" +
            "da41597c5157488d7724e03fb8d84a376a43b8f41518a11cc387b669b2ee6586"),
        new TestData("RFC 7539 Test Vector 2",
            "0000000000000000000000000000000000000000000000000000000000000001",
            "000000000000000000000002",
            1, Cipher.ENCRYPT_MODE,
            "416e79207375626d697373696f6e20746f20746865204945544620696e74656e" +
            "6465642062792074686520436f6e7472696275746f7220666f72207075626c69" +
            "636174696f6e20617320616c6c206f722070617274206f6620616e2049455446" +
            "20496e7465726e65742d4472616674206f722052464320616e6420616e792073" +
            "746174656d656e74206d6164652077697468696e2074686520636f6e74657874" +
            "206f6620616e204945544620616374697669747920697320636f6e7369646572" +
            "656420616e20224945544620436f6e747269627574696f6e222e205375636820" +
            "73746174656d656e747320696e636c756465206f72616c2073746174656d656e" +
            "747320696e20494554462073657373696f6e732c2061732077656c6c20617320" +
            "7772697474656e20616e6420656c656374726f6e696320636f6d6d756e696361" +
            "74696f6e73206d61646520617420616e792074696d65206f7220706c6163652c" +
            "207768696368206172652061646472657373656420746f",
            null,
            "a3fbf07df3fa2fde4f376ca23e82737041605d9f4f4f57bd8cff2c1d4b7955ec" +
            "2a97948bd3722915c8f3d337f7d370050e9e96d647b7c39f56e031ca5eb6250d" +
            "4042e02785ececfa4b4bb5e8ead0440e20b6e8db09d881a7c6132f420e527950" +
            "42bdfa7773d8a9051447b3291ce1411c680465552aa6c405b7764d5e87bea85a" +
            "d00f8449ed8f72d0d662ab052691ca66424bc86d2df80ea41f43abf937d3259d" +
            "c4b2d0dfb48a6c9139ddd7f76966e928e635553ba76c5c879d7b35d49eb2e62b" +
            "0871cdac638939e25e8a1e0ef9d5280fa8ca328b351c3c765989cbcf3daa8b6c" +
            "cc3aaf9f3979c92b3720fc88dc95ed84a1be059c6499b9fda236e7e818b04b0b" +
            "c39c1e876b193bfe5569753f88128cc08aaa9b63d1a16f80ef2554d7189c411f" +
            "5869ca52c5b83fa36ff216b9c1d30062bebcfd2dc5bce0911934fda79a86f6e6" +
            "98ced759c3ff9b6477338f3da4f9cd8514ea9982ccafb341b2384dd902f3d1ab" +
            "7ac61dd29c6f21ba5b862f3730e37cfdc4fd806c22f221"),
        new TestData("RFC 7539 Test Vector 3",
            "1c9240a5eb55d38af333888604f6b5f0473917c1402b80099dca5cbc207075c0",
            "000000000000000000000002",
            42, Cipher.ENCRYPT_MODE,
            "2754776173206272696c6c69672c20616e642074686520736c6974687920746f" +
            "7665730a446964206779726520616e642067696d626c6520696e207468652077" +
            "6162653a0a416c6c206d696d737920776572652074686520626f726f676f7665" +
            "732c0a416e6420746865206d6f6d65207261746873206f757467726162652e",
            null,
            "62e6347f95ed87a45ffae7426f27a1df5fb69110044c0d73118effa95b01e5cf" +
            "166d3df2d721caf9b21e5fb14c616871fd84c54f9d65b283196c7fe4f60553eb" +
            "f39c6402c42234e32a356b3e764312a61a5532055716ead6962568f87d3f3f77" +
            "04c6a8d1bcd1bf4d50d6154b6da731b187b58dfd728afa36757a797ac188d1")
    );

    public static class TestData {
        public TestData(String name, String keyStr, String nonceStr, int ctr,
                        int dir, String inputStr, String aadStr, String outStr) {
            testName = Objects.requireNonNull(name);
            HexFormat hex = HexFormat.of();
            key = hex.parseHex(Objects.requireNonNull(keyStr));
            nonce = hex.parseHex(Objects.requireNonNull(nonceStr));
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
            input = hex.parseHex(Objects.requireNonNull(inputStr));
            aad = (aadStr != null) ? hex.parseHex(aadStr) : null;
            expOutput = hex.parseHex(Objects.requireNonNull(outStr));
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

    public static void main(String[] args) throws Exception {
        int testsPassed = 0;
        int testNumber = 0;

        // Use the first test vector to warm up the JVM and activate
        // the intrinsics.
        System.out.println("Running " + WARMUP_CYCLES + " warm up cycles");
        for (int i = 0; i < WARMUP_CYCLES; i++) {
            runSinglePartTest(testList.get(0));
        }

        System.out.println("----- Single-part Tests -----");
        for (TestData test : testList) {
            System.out.println("*** Test " + ++testNumber + ": " +
                    test.testName);
            if (runSinglePartTest(test)) {
                testsPassed++;
            }
        }
        System.out.println();

        System.out.println("----- Multi-part Tests -----");
        for (TestData test : testList) {
            System.out.println("*** Test " + ++testNumber + ": " +
                    test.testName);
            if (runMultiPartTest(test)) {
                testsPassed++;
            }
        }
        System.out.println();

        System.out.println("Total tests: " + testNumber +
                ", Passed: " + testsPassed + ", Failed: " +
                (testNumber - testsPassed));
        if (testsPassed != testNumber) {
            throw new RuntimeException("One or more tests failed.  " +
                    "Check output for details");
        }
    }

    private static boolean runSinglePartTest(TestData testData)
            throws GeneralSecurityException {
        boolean encRes = false;
        boolean decRes = false;
        byte[] encryptedResult;
        byte[] decryptedResult;

        // Get a Cipher instance and set up the parameters
        Cipher mambo = Cipher.getInstance("ChaCha20");
        SecretKeySpec mamboKey = new SecretKeySpec(testData.key, "ChaCha20");
        ChaCha20ParameterSpec mamboSpec = new ChaCha20ParameterSpec(
                testData.nonce, testData.counter);

        // Encrypt our input
        mambo.init(Cipher.ENCRYPT_MODE, mamboKey, mamboSpec);
        encryptedResult = mambo.doFinal(testData.input);

        if (!Arrays.equals(encryptedResult, testData.expOutput)) {
            System.out.println("ERROR - Output Mismatch!");
            System.out.println("Expected:\n" +
                    dumpHexBytes(testData.expOutput, 16, "\n", " "));
            System.out.println("Actual:\n" +
                    dumpHexBytes(encryptedResult, 16, "\n", " "));
            System.out.println();
        } else {
            encRes = true;
        }

        // Decrypt the result of the encryption operation
        mambo = Cipher.getInstance("ChaCha20");
        mambo.init(Cipher.DECRYPT_MODE, mamboKey, mamboSpec);
        decryptedResult = mambo.doFinal(encryptedResult);

        if (!Arrays.equals(decryptedResult, testData.input)) {
            System.out.println("ERROR - Output Mismatch!");
            System.out.println("Expected:\n" +
                    dumpHexBytes(testData.input, 16, "\n", " "));
            System.out.println("Actual:\n" +
                    dumpHexBytes(decryptedResult, 16, "\n", " "));
            System.out.println();
        } else {
            decRes = true;
        }

        return (encRes && decRes);
    }

    private static boolean runMultiPartTest(TestData testData)
            throws GeneralSecurityException {
        boolean encRes = false;
        boolean decRes = false;

        // Get a cipher instance and initialize it
        Cipher mambo = Cipher.getInstance("ChaCha20");
        SecretKeySpec mamboKey = new SecretKeySpec(testData.key, "ChaCha20");
        ChaCha20ParameterSpec mamboSpec = new ChaCha20ParameterSpec(
                testData.nonce, testData.counter);

        byte[] encryptedResult = new byte[testData.input.length];
        mambo.init(Cipher.ENCRYPT_MODE, mamboKey, mamboSpec);
        System.out.print("Encrypt - ");
        doMulti(mambo, testData.input, encryptedResult);

        if (!Arrays.equals(encryptedResult, testData.expOutput)) {
            System.out.println("ERROR - Output Mismatch!");
            System.out.println("Expected:\n" +
                    dumpHexBytes(testData.expOutput));
            System.out.println("Actual:\n" +
                    dumpHexBytes(encryptedResult));
            System.out.println();
        } else {
            encRes = true;
        }

        // Decrypt the result of the encryption operation
        mambo = Cipher.getInstance("ChaCha20");
        byte[] decryptedResult = new byte[encryptedResult.length];
        mambo.init(Cipher.DECRYPT_MODE, mamboKey, mamboSpec);
        System.out.print("Decrypt - ");
        doMulti(mambo, encryptedResult, decryptedResult);

        if (!Arrays.equals(decryptedResult, testData.input)) {
            System.out.println("ERROR - Output Mismatch!");
            System.out.println("Expected:\n" + dumpHexBytes(testData.input));
            System.out.println("Actual:\n" + dumpHexBytes(decryptedResult));
            System.out.println();
        } else {
            decRes = true;
        }

        return (encRes && decRes);
    }

    private static void doMulti(Cipher c, byte[] input, byte[] output)
            throws GeneralSecurityException {
        int offset = 0;
        boolean done = false;
        Random randIn = new Random(System.currentTimeMillis());

        // Send small updates between 1 - 8 bytes in length until we get
        // 8 or less bytes from the end of the input, then finalize.
        System.out.println("Input length: " + input.length);
        System.out.print("Multipart (bytes in/out): ");
        while (!done) {
            int mPartLen = randIn.nextInt(8) + 1;
            int bytesLeft = input.length - offset;
            int processed;
            if (mPartLen < bytesLeft) {
                System.out.print(mPartLen + "/");
                processed = c.update(input, offset, mPartLen,
                        output, offset);
                offset += processed;
                System.out.print(processed + " ");
            } else {
                processed = c.doFinal(input, offset, bytesLeft,
                        output, offset);
                System.out.print(bytesLeft + "/" + processed + " ");
                done = true;
            }
        }
        System.out.println();
    }

    private static String dumpHexBytes(byte[] data) {
        return dumpHexBytes(data, 16, "\n", " ");
    }

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