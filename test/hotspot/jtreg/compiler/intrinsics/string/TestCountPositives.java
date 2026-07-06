/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8281146
 * @summary Validates StringCoding.countPositives intrinsic with a small range of tests.
 * @key randomness
 * @library /compiler/patches
 * @library /test/lib
 *
 * @build java.base/java.lang.Helper
 * @run main compiler.intrinsics.string.TestCountPositives
 */
/*
 * @test
 * @bug 8281146 8318509
 * @summary Validates StringCoding.countPositives intrinsic for AVX3 works with and without
 *          AVX3Threshold=0
 * @key randomness
 * @library /compiler/patches
 * @library /test/lib
 *
 * @build java.base/java.lang.Helper
 * @requires vm.cpu.features ~= ".*avx512.*"
 * @run main/othervm/timeout=1200 -XX:UseAVX=3 compiler.intrinsics.string.TestCountPositives
 * @run main/othervm/timeout=1200 -XX:UseAVX=3 -XX:+UnlockDiagnosticVMOptions -XX:AVX3Threshold=0 compiler.intrinsics.string.TestCountPositives
 */
/**
 * This test was derived from compiler.intrinsics.string.TestHasNegatives
 */
package compiler.intrinsics.string;

import java.lang.Helper;
import java.util.Random;
import java.util.stream.IntStream;

import jdk.test.lib.Utils;

public class TestCountPositives {

    private static byte[] bytes = new byte[4096 + 32];

    private static final Random RANDOM = Utils.getRandomInstance();

    /**
     * Completely initialize the test array, preparing it for tests of the
     * StringCoding.hasNegatives method with a given array segment offset,
     * length, and number of negative bytes. The lowest index that will be
     * negative is marked by negOffset
     */
    public static void initialize(int off, int len, int neg, int negOffset) {
        assert (len + off <= bytes.length);
        // insert "canary" (negative) values before offset
        for (int i = 0; i < off; ++i) {
            bytes[i] = (byte) (((i + 15) & 0x7F) | 0x80);
        }
        // fill the array segment
        for (int i = off; i < len + off; ++i) {
            bytes[i] = (byte) (((i - off + 15) & 0x7F));
        }
        if (neg != 0) {
            // modify a number (neg) disparate array bytes inside
            // segment to be negative.
            for (int i = 0; i < neg; i++) {
                int idx = off + RANDOM.nextInt(len - negOffset) + negOffset;
                bytes[idx] = (byte) (0x80 | bytes[idx]);
            }
        }
        // insert "canary" negative values after array segment
        for (int i = len + off; i < bytes.length; ++i) {
            bytes[i] = (byte) (((i + 15) & 0x7F) | 0x80);
        }
    }

    /**
     * Test different array segment sizes, offsets, and number of negative
     * bytes.
     */
    public static void test_countPositives() throws Exception {
        for (int off = 0; off < 16; off++) { // starting offset of array segment
            // Test all array segment sizes 1-63
            for (int len = 1; len < 64; len++) {
                test_countPositives(off, len, 0, 0);
                test_countPositives(off, len, 1, 0);
                test_countPositives(off, len, RANDOM.nextInt(30) + 2, 0);
            }
            // Test a random selection of sizes between 64 and 4099, inclusive
            for (int i = 0; i < 20; i++) {
                int len = 64 + RANDOM.nextInt(4100 - 64);
                test_countPositives(off, len, 0, 0);
                test_countPositives(off, len, 1, 0);
                test_countPositives(off, len, RANDOM.nextInt(len) + 2, 0);
            }
            for (int len : new int[] { 128, 2048 }) {
                // test with negatives only in a 1-63 byte tail
                int tail = RANDOM.nextInt(63) + 1;
                int ng = RANDOM.nextInt(tail) + 1;
                test_countPositives(off, len + tail, ng, len);
            }
        }
    }

    private static void test_countPositives(int off, int len, int ng, int ngOffset) throws Exception {
        assert (len + off < bytes.length);
        initialize(off, len, ng, ngOffset);
        int calculated = Helper.StringCodingCountPositives(bytes, off, len);
        int expected = countPositives(bytes, off, len);
        if (calculated != expected) {
            if (expected != len && ng >= 0 && calculated >= 0 && calculated < expected) {
                // allow intrinsics to return early with a lower value,
                // but only if we're not expecting the full length (no
                // negative bytes)
                return;
            }
            throw new Exception("Failed test countPositives " + "offset: " + off + " "
                    + "length: " + len + " " + "return: " + calculated + " expected: " + expected + " negatives: "
                    + ng + " offset: " + ngOffset);
        }
    }

    private static int countPositives(byte[] ba, int off, int len) {
        int limit = off + len;
        for (int i = off; i < limit; i++) {
            if (ba[i] < 0) {
                return i - off;
            }
        }
        return len;
    }

    public void run() throws Exception {
        // iterate to eventually get intrinsic inlined
        for (int j = 0; j < 1000; ++j) {
            test_countPositives();
        }
    }

    public static void main(String[] args) throws Exception {
        (new TestCountPositives()).run();
        System.out.println("countPositives validated");
    }
}
