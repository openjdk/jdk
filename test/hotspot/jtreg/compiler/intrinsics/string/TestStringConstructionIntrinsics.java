/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8311906
 * @summary Validates String constructor intrinsics using varied input data.
 * @key randomness
 * @library /compiler/patches /test/lib
 * @build java.base/java.lang.Helper
 * @run main/othervm/timeout=1200 -Xbatch -XX:CompileThreshold=100 compiler.intrinsics.string.TestStringConstructionIntrinsics
 */
/*
 * @test
 * @bug 8311906
 * @summary Validates String constructor intrinsic for AVX3 works with and without
 *          AVX3Threshold=0
 * @key randomness
 * @library /compiler/patches /test/lib
 * @build java.base/java.lang.Helper
 * @requires vm.cpu.features ~= ".*avx512.*"
 * @run main/othervm/timeout=1200 -Xbatch -XX:CompileThreshold=100 -XX:UseAVX=3 compiler.intrinsics.string.TestStringConstructionIntrinsics
 * @run main/othervm/timeout=1200 -Xbatch -XX:CompileThreshold=100 -XX:UseAVX=3 -XX:+UnlockDiagnosticVMOptions -XX:AVX3Threshold=0 compiler.intrinsics.string.TestStringConstructionIntrinsics
 */

package compiler.intrinsics.string;

import java.lang.Helper;
import java.util.Random;

import jdk.test.lib.Utils;

public class TestStringConstructionIntrinsics {

    private static byte[] bytes = new byte[2 * (4096 + 32)];

    private static char[] chars = new char[4096 + 32];

    // Used a scratch buffer, sized to accommodate inflated
    private static byte[] dst = new byte[bytes.length * 2];

    private static final Random RANDOM = Utils.getRandomInstance();

    /**
     * Completely initialize the bytes test array. The lowest index that will be
     * non-latin1 is marked by nlOffset
     */
    public static void initializeBytes(int off, int len, int nonLatin1, int nlOffset) {
        int maxLen = bytes.length >> 1;
        assert (len + off < maxLen);
        // insert "canary" (non-latin1) values before offset
        for (int i = 0; i < off; i++) {
            Helper.putCharSB(bytes, i, ((i + 15) & 0x7F) | 0x180);
        }
        // fill the array segment
        for (int i = off; i < len + off; i++) {
            Helper.putCharSB(bytes, i, ((i - off + 15) & 0xFF));
        }
        if (nonLatin1 != 0) {
            // modify a number disparate indexes to be non-latin1
            for (int i = 0; i < nonLatin1; i++) {
                int idx = off + RANDOM.nextInt(len - nlOffset) + nlOffset;
                Helper.putCharSB(bytes, i, ((i + 15) & 0x7F) | 0x180);
            }
        }
        // insert "canary" non-latin1 values after array segment
        for (int i = len + off; i < maxLen; i++) {
            Helper.putCharSB(bytes, i, ((i + 15) & 0x7F) | 0x180);
        }
    }

    /**
     * Completely initialize the char test array. The lowest index that will be
     * non-latin1 is marked by nlOffset
     */
    public static void initializeChars(int off, int len, int nonLatin1, int nlOffset) {
        assert (len + off <= chars.length);
        // insert "canary" non-latin1 values before offset
        for (int i = 0; i < off; ++i) {
            chars[i] = (char) (((i + 15) & 0x7F) | 0x180);
        }
        // fill the array segment
        for (int i = off; i < len + off; ++i) {
            chars[i] = (char) (((i - off + 15) & 0xFF));
        }
        if (nonLatin1 != 0) {
            // modify a number disparate chars inside
            // segment to be non-latin1.
            for (int i = 0; i < nonLatin1; i++) {
                int idx = off + RANDOM.nextInt(len - nlOffset) + nlOffset;
                chars[idx] = (char) (0x180 | chars[idx]);
            }
        }
        // insert "canary" non-latin1 values after array segment
        for (int i = len + off; i < chars.length; ++i) {
            chars[i] = (char) (((i + 15) & 0x7F) | 0x180);
        }
    }

    /**
     * Test different array segment sizes, offsets, and number of non-latin1
     * chars.
     */
    public static void testConstructBytes() throws Exception {
        for (int off = 0; off < 16; off++) { // starting offset of array segment
            // Test all array segment sizes 1-63
            for (int len = 1; len < 64; len++) {
                testConstructBytes(off, len, 0, 0);
                testConstructBytes(off, len, 1, 0);
                testConstructBytes(off, len, RANDOM.nextInt(30) + 2, 0);
            }
            // Test a random selection of sizes between 64 and 4099, inclusive
            for (int i = 0; i < 20; i++) {
                int len = 64 + RANDOM.nextInt(4100 - 64);
                testConstructBytes(off, len, 0, 0);
                testConstructBytes(off, len, 1, 0);
                testConstructBytes(off, len, RANDOM.nextInt(len) + 2, 0);
            }
            for (int len : new int[] { 128, 2048 }) {
                // test with negatives only in a 1-63 byte tail
                int tail = RANDOM.nextInt(63) + 1;
                int ng = RANDOM.nextInt(tail) + 1;
                testConstructBytes(off, len + tail, ng, len);
            }
        }
    }

    private static void testConstructBytes(int off, int len, int ng, int ngOffset) throws Exception {
        assert (len + off < bytes.length);
        initializeBytes(off, len, ng, ngOffset);
        byte[] dst = new byte[bytes.length];

        int calculated = Helper.compress(bytes, off, dst, 0, len);
        int expected = compress(bytes, off, dst, 0, len);
        if (calculated != expected) {
            if (expected != len && ng >= 0 && calculated >= 0 && calculated < expected) {
                // allow intrinsics to return early with a lower value,
                // but only if we're not expecting the full length (no
                // negative bytes)
                return;
            }
            throw new Exception("Failed testConstructBytes: " + "offset: " + off + " "
                    + "length: " + len + " " + "return: " + calculated + " expected: " + expected + " negatives: "
                    + ng + " offset: " + ngOffset);
        }
    }

    private static int compress(byte[] src, int srcOff, byte[] dst, int dstOff, int len) {
        for (int i = 0; i < len; i++) {
            char c = Helper.charAt(src, srcOff);
            if (c > 0xff) {
                return i;  // return index of non-latin1 char
            }
            dst[dstOff] = (byte)c;
            srcOff++;
            dstOff++;
        }
        return len;
    }

    /**
     * Test different array segment sizes, offsets, and number of non-latin1
     * chars.
     */
    public static void testConstructChars() throws Exception {
        for (int off = 0; off < 16; off++) { // starting offset of array segment
            // Test all array segment sizes 1-63
            for (int len = 1; len < 64; len++) {
                testConstructChars(off, len, 0, 0);
                testConstructChars(off, len, 1, 0);
                testConstructChars(off, len, RANDOM.nextInt(30) + 2, 0);
            }
            // Test a random selection of sizes between 64 and 4099, inclusive
            for (int i = 0; i < 20; i++) {
                int len = 64 + RANDOM.nextInt(4100 - 64);
                testConstructChars(off, len, 0, 0);
                testConstructChars(off, len, 1, 0);
                testConstructChars(off, len, RANDOM.nextInt(len) + 2, 0);
            }
            for (int len : new int[] { 128, 2048 }) {
                // test with negatives only in a 1-63 byte tail
                int tail = RANDOM.nextInt(63) + 1;
                int ng = RANDOM.nextInt(tail) + 1;
                testConstructChars(off, len + tail, ng, len);
            }
        }
    }

    private static void testConstructChars(int off, int len, int nonLatin1, int nlOffset) throws Exception {
        assert (len + off < bytes.length);
        initializeChars(off, len, nonLatin1, nlOffset);

        int calculated = Helper.compress(chars, off, dst, 0, len);
        int expected = compress(chars, off, dst, 0, len);
        if (calculated != expected) {
            if (expected != len && nonLatin1 >= 0 && calculated >= 0 && calculated < expected) {
                // allow intrinsics to return early with a lower value,
                // but only if we're not expecting the full length (no
                // negative bytes)
                return;
            }
            throw new Exception("Failed testConstructChars: " + "offset: " + off + " "
                    + "length: " + len + " " + "return: " + calculated + " expected: " + expected + " non-latin1: "
                    + nonLatin1 + " offset: " + nlOffset);
        }
    }

    private static int compress(char[] src, int srcOff, byte[] dst, int dstOff, int len) {
        for (int i = 0; i < len; i++) {
            char c = src[srcOff];
            if (c > 0xff) {
                return i;  // return index of non-latin1 char
            }
            dst[dstOff] = (byte)c;
            srcOff++;
            dstOff++;
        }
        return len;
    }

    public void run() throws Exception {
        // iterate to eventually get intrinsic inlined
        for (int j = 0; j < 200; ++j) {
            testConstructBytes();
            testConstructChars();
        }
    }

    public static void main(String[] args) throws Exception {
        (new TestStringConstructionIntrinsics()).run();
        System.out.println("string construction intrinsics validated");
    }
}
