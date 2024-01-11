/*
 * Copyright (c) 2022, Intel Corporation. All rights reserved.
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

package com.sun.crypto.provider;

import java.util.*;
import java.nio.ByteBuffer;
import java.util.Arrays;

import javax.crypto.spec.SecretKeySpec;

public class Poly1305KAT {
    public static class TestData {
        public TestData(String name, String keyStr, String inputStr, String outStr) {
            HexFormat hex = HexFormat.of();
            testName = Objects.requireNonNull(name);
            key = hex.parseHex(Objects.requireNonNull(keyStr));
            input = hex.parseHex(Objects.requireNonNull(inputStr));
            expOutput = hex.parseHex(Objects.requireNonNull(outStr));
        }

        public final String testName;
        public final byte[] key;
        public final byte[] input;
        public final byte[] expOutput;
    }

    public static final List<TestData> testList = new LinkedList<TestData>() {{
        add(new TestData("RFC 7539 A.3 Test Vector #1",
            "0000000000000000000000000000000000000000000000000000000000000000",
            "0000000000000000000000000000000000000000000000000000000000000000" +
            "0000000000000000000000000000000000000000000000000000000000000000",
            "00000000000000000000000000000000"));
        add(new TestData("RFC 7539 A.3 Test Vector #2",
            "0000000000000000000000000000000036e5f6b5c5e06070f0efca96227a863e",
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
            "36e5f6b5c5e06070f0efca96227a863e"));
        add(new TestData("RFC 7539 A.3 Test Vector #3",
            "36e5f6b5c5e06070f0efca96227a863e00000000000000000000000000000000",
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
             "f3477e7cd95417af89a6b8794c310cf0"));
        add(new TestData("RFC 7539 A.3 Test Vector #4",
            "1c9240a5eb55d38af333888604f6b5f0473917c1402b80099dca5cbc207075c0",
            "2754776173206272696c6c69672c20616e642074686520736c6974687920746f" +
            "7665730a446964206779726520616e642067696d626c6520696e207468652077" +
            "6162653a0a416c6c206d696d737920776572652074686520626f726f676f7665" +
            "732c0a416e6420746865206d6f6d65207261746873206f757467726162652e",
            "4541669a7eaaee61e708dc7cbcc5eb62"));
        add(new TestData("RFC 7539 A.3 Test Vector #5: If one uses 130-bit partial reduction, does the code handle the case where partially reducedfinal result is not fully reduced?",
            "0200000000000000000000000000000000000000000000000000000000000000",
            "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF",
            "03000000000000000000000000000000"));
        add(new TestData("RFC 7539 A.3 Test Vector #6: What happens if addition of s overflows modulo 2^128?",
            "02000000000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF",
            "02000000000000000000000000000000",
            "03000000000000000000000000000000"));
        add(new TestData("RFC 7539 A.3 Test Vector #7: What happens if data limb is all ones and there is carry from lower limb?",
            "0100000000000000000000000000000000000000000000000000000000000000",
            "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF0FFFFFFFFFFFFFFFFFFFFFFFFFFFFFF" +
            "11000000000000000000000000000000",
             "05000000000000000000000000000000"));
        add(new TestData("RFC 7539 A.3 Test Vector #8: What happens if final result from polynomial part is exactly 2^130-5?",
            "0100000000000000000000000000000000000000000000000000000000000000",
            "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFBFEFEFEFEFEFEFEFEFEFEFEFEFEFEFE" +
            "01010101010101010101010101010101",
            "00000000000000000000000000000000"));
        add(new TestData("RFC 7539 A.3 Test Vector #9: What happens if final result from polynomial part is exactly 2^130-6?",
            "0200000000000000000000000000000000000000000000000000000000000000",
            "FDFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF",
            "FAFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"));
        add(new TestData("RFC 7539 A.3 Test Vector #10: What happens if 5*H+L-type reduction produces 131-bit intermediate result?",
            "0100000000000000040000000000000000000000000000000000000000000000",
            "E33594D7505E43B900000000000000003394D7505E4379CD0100000000000000" +
            "0000000000000000000000000000000001000000000000000000000000000000",
            "14000000000000005500000000000000"));
        add(new TestData("RFC 7539 A.3 Test Vector #11: What happens if 5*H+L-type reduction produces 131-bit final result?",
            "0100000000000000040000000000000000000000000000000000000000000000",
            "E33594D7505E43B900000000000000003394D7505E4379CD0100000000000000" +
            "00000000000000000000000000000000",
            "13000000000000000000000000000000"));
    }};

    public static void main(String args[]) throws Exception {
        int testsPassed = 0;
        int testNumber = 0;

        for (TestData test : testList) {
            System.out.println("*** Test " + ++testNumber + ": " +
                    test.testName);
            if (runSingleTest(test)) {
                testsPassed++;
            }
        }
        System.out.println();

        if (testsPassed != testNumber) {
            throw new RuntimeException("One or more tests failed.  " +
                    "Check output for details");
        }
    }

    private static boolean runSingleTest(TestData testData) throws Exception {
        Poly1305 authenticator = new Poly1305(false);
        authenticator.engineInit(new SecretKeySpec(testData.key, 0, testData.key.length, "Poly1305"), null);
        authenticator.engineUpdate(testData.input, 0, testData.input.length);
        byte[] tag = authenticator.engineDoFinal();
        if (!Arrays.equals(tag, testData.expOutput)) {
                System.out.println("ERROR - Output Mismatch!");
                System.out.println("Expected:\n" +
                        dumpHexBytes(testData.expOutput, testData.expOutput.length, "\n", " "));
                System.out.println("Actual:\n" +
                        dumpHexBytes(tag, tag.length, "\n", " "));
                System.out.println();
                return false;
        }
        return true;
    }

    /**
     * Dump the hex bytes of a buffer into string form.
     *
     * @param data The array of bytes to dump to stdout.
     * @param itemsPerLine The number of bytes to display per line
     *      if the {@code lineDelim} character is blank then all bytes
     *      will be printed on a single line.
     * @param lineDelim The delimiter between lines
     * @param itemDelim The delimiter between bytes
     *
     * @return The hexdump of the byte array
     */
    private static String dumpHexBytes(byte[] data, int itemsPerLine,
            String lineDelim, String itemDelim) {
        return dumpHexBytes(ByteBuffer.wrap(data), itemsPerLine, lineDelim,
                itemDelim);
    }

    private static String dumpHexBytes(ByteBuffer data, int itemsPerLine,
            String lineDelim, String itemDelim) {
        StringBuilder sb = new StringBuilder();
        if (data != null) {
            data.mark();
            int i = 0;
            while (data.remaining() > 0) {
                if (i % itemsPerLine == 0 && i != 0) {
                    sb.append(lineDelim);
                }
                sb.append(String.format("%02X", data.get())).append(itemDelim);
                i++;
            }
            data.reset();
        }

        return sb.toString();
    }
}

