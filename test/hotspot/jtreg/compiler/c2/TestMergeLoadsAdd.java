/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, Alibaba Group Holding Limited. All rights reserved.
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
 * or visit www.oracle.com or need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 8345485
 * @summary Test MergeLoads with Add operator (unsigned loads only)
 * @run main/othervm -Xbatch -XX:CompileCommand=compileonly,compiler.c2.TestMergeLoadsAdd::* -XX:+OptimizeFill -XX:+UnlockDiagnosticVMOptions -XX:+UseUnalignedAccesses compiler.c2.TestMergeLoadsAdd
 */

package compiler.c2;

public class TestMergeLoadsAdd {

    // ==================== Positive tests (should be optimized) ====================

    // Test merging 2 unsigned bytes with Add (should work - LoadUB)
    public static int mergeTwoBytesWithAdd(byte[] arr) {
        return (arr[0] & 0xFF) + ((arr[1] & 0xFF) << 8);
    }

    // Test merging 4 unsigned bytes with Add
    public static int mergeFourBytesWithAdd(byte[] arr) {
        return (arr[0] & 0xFF) +
               ((arr[1] & 0xFF) << 8) +
               ((arr[2] & 0xFF) << 16) +
               ((arr[3] & 0xFF) << 24);
    }

    // Test merging 2 unsigned shorts with Add
    public static int mergeTwoShortsWithAdd(short[] arr) {
        return (arr[0] & 0xFFFF) + ((arr[1] & 0xFFFF) << 16);
    }

    // Test merging 8 unsigned bytes into long with Add
    public static long mergeEightBytesWithAdd(byte[] arr) {
        return (arr[0] & 0xFFL) +
               ((arr[1] & 0xFFL) << 8) +
               ((arr[2] & 0xFFL) << 16) +
               ((arr[3] & 0xFFL) << 24) +
               ((arr[4] & 0xFFL) << 32) +
               ((arr[5] & 0xFFL) << 40) +
               ((arr[6] & 0xFFL) << 48) +
               ((arr[7] & 0xFFL) << 56);
    }

    // Test merging 4 unsigned shorts into long with Add
    public static long mergeFourShortsWithAdd(short[] arr) {
        return (arr[0] & 0xFFFFL) +
               ((arr[1] & 0xFFFFL) << 16) +
               ((arr[2] & 0xFFFFL) << 32) +
               ((arr[3] & 0xFFFFL) << 48);
    }

    // Reference implementation using Or (for comparison)
    public static int mergeWithOr(byte[] arr) {
        return (arr[0] & 0xFF) |
               ((arr[1] & 0xFF) << 8) |
               ((arr[2] & 0xFF) << 16) |
               ((arr[3] & 0xFF) << 24);
    }

    public static long mergeLongWithOr(byte[] arr) {
        return (arr[0] & 0xFFL) |
               ((arr[1] & 0xFFL) << 8) |
               ((arr[2] & 0xFFL) << 16) |
               ((arr[3] & 0xFFL) << 24) |
               ((arr[4] & 0xFFL) << 32) |
               ((arr[5] & 0xFFL) << 40) |
               ((arr[6] & 0xFFL) << 48) |
               ((arr[7] & 0xFFL) << 56);
    }

    // ==================== Negative tests (should NOT be optimized) ====================

    // Signed byte + Add - should NOT be optimized (LoadB can be negative)
    // This tests that signed loads are rejected for Add merging
    public static int signedByteAdd(byte[] arr) {
        return arr[0] + (arr[1] << 8);
    }

    // Mixed Or and Add - should NOT be optimized
    // This tests that we don't mix Or and Add in the same merge chain
    public static int mixedOrAdd(byte[] arr) {
        return (arr[0] & 0xFF) + ((arr[1] & 0xFF) << 8) | ((arr[2] & 0xFF) << 16);
    }

    // Mixed Add and Or - another variation
    public static int mixedAddOr(byte[] arr) {
        return (arr[0] & 0xFF) | ((arr[1] & 0xFF) << 8) + ((arr[2] & 0xFF) << 16);
    }

    // ==================== Nodes with other uses tests (eme64 review comment #4) ====================

    // Volatile variable to prevent optimization of side effects
    static volatile int sideEffect;

    // Test where a load node has multiple uses - should NOT be merged
    // Requested by eme64: need tests where nodes in the load/shift/add expression have other uses
    public static int loadWithOtherUses(byte[] arr) {
        // Load b0 has multiple uses: in the merge expression and separately
        int b0 = arr[0] & 0xFF;
        int merged = b0 + ((arr[1] & 0xFF) << 8);
        sideEffect = b0; // b0 is used separately here, should prevent merging
        return merged;
    }

    // Test where a shift node has multiple uses - should NOT be merged
    public static int shiftWithOtherUses(byte[] arr) {
        // Shift result has multiple uses
        int shifted = (arr[0] & 0xFF) << 8;
        int merged = shifted + (arr[1] & 0xFF);
        sideEffect = shifted; // shifted is used separately here
        return merged;
    }

    // Test where multiple intermediate nodes have other uses - should NOT be merged
    public static int multipleNodesWithOtherUses(byte[] arr) {
        int b0 = arr[0] & 0xFF;
        int b1 = arr[1] & 0xFF;
        int shifted0 = b0 << 8;
        int shifted1 = b1 << 16;
        // Use intermediate values separately
        sideEffect = shifted0 + shifted1;
        // Then try to merge (should be prevented by above use)
        return (b0 & 0xFF) + ((b1 & 0xFF) << 8) + ((arr[2] & 0xFF) << 16);
    }

    // Test where only the final result is used (should be mergeable)
    // This is a positive test showing the single-use case works
    public static int singleUseShouldMerge(byte[] arr) {
        int b0 = arr[0] & 0xFF;
        int b1 = arr[1] & 0xFF;
        // b0 and b1 are only used in this expression, so merging should work
        return b0 + (b1 << 8);
    }

    // ==================== Boundary value tests ====================

    // Test with maximum unsigned byte values (0xFF = 255)
    public static int maxBytesAdd(byte[] arr) {
        return (arr[0] & 0xFF) + ((arr[1] & 0xFF) << 8) +
               ((arr[2] & 0xFF) << 16) + ((arr[3] & 0xFF) << 24);
    }

    // Test with minimum values (0x00)
    public static int minBytesAdd(byte[] arr) {
        return (arr[0] & 0xFF) + ((arr[1] & 0xFF) << 8) +
               ((arr[2] & 0xFF) << 16) + ((arr[3] & 0xFF) << 24);
    }

    // ==================== Test runner ====================

    public static void main(String[] args) {
        // Test data
        byte[] testArr = new byte[]{0x12, 0x34, 0x56, 0x78, (byte)0x9A, (byte)0xBC, (byte)0xDE, (byte)0xF0};
        short[] testShorts = new short[]{0x1234, 0x5678, (short)0x9ABC, (short)0xDEF0};
        byte[] maxArr = new byte[]{(byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF,
                                   (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF};
        byte[] minArr = new byte[]{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        byte[] signedArr = new byte[]{(byte)0x80, 0x7F, 0x00, 0x01}; // 0x80 is -128 as signed byte

        // Warmup - run all methods many times
        for (int i = 0; i < 10000; i++) {
            mergeTwoBytesWithAdd(testArr);
            mergeFourBytesWithAdd(testArr);
            mergeTwoShortsWithAdd(testShorts);
            mergeEightBytesWithAdd(testArr);
            mergeFourShortsWithAdd(testShorts);
            mergeWithOr(testArr);
            mergeLongWithOr(testArr);
            signedByteAdd(signedArr);
            mixedOrAdd(testArr);
            mixedAddOr(testArr);
            maxBytesAdd(maxArr);
            minBytesAdd(minArr);
            loadWithOtherUses(testArr);
            shiftWithOtherUses(testArr);
            multipleNodesWithOtherUses(testArr);
            singleUseShouldMerge(testArr);
        }

        // ==================== Positive tests ====================

        // Test 2-byte merge with Add
        int result2 = mergeTwoBytesWithAdd(testArr);
        int expected2 = 0x3412;  // little-endian: 0x12 + (0x34 << 8)
        checkEquals(result2, expected2, "2-byte Add merge");

        // Test 4-byte merge with Add
        int result4 = mergeFourBytesWithAdd(testArr);
        int expected4 = 0x78563412;  // little-endian
        checkEquals(result4, expected4, "4-byte Add merge");

        // Test that Add gives same result as Or for unsigned loads
        int resultAdd = mergeFourBytesWithAdd(testArr);
        int resultOr = mergeWithOr(testArr);
        checkEquals(resultAdd, resultOr, "Add vs Or comparison");

        // Test 2-short merge with Add
        int resultShorts = mergeTwoShortsWithAdd(testShorts);
        int expectedShorts = 0x56781234;  // little-endian
        checkEquals(resultShorts, expectedShorts, "2-short Add merge");

        // Test 8-byte (long) merge with Add
        long resultLong = mergeEightBytesWithAdd(testArr);
        long expectedLong = 0xF0DEBC9A78563412L;  // little-endian
        checkEqualsLong(resultLong, expectedLong, "8-byte Add merge");

        // Test 8-byte Add vs Or comparison
        long resultLongAdd = mergeEightBytesWithAdd(testArr);
        long resultLongOr = mergeLongWithOr(testArr);
        checkEqualsLong(resultLongAdd, resultLongOr, "8-byte Add vs Or comparison");

        // Test 4-short (long) merge with Add
        long result4Shorts = mergeFourShortsWithAdd(testShorts);
        long expected4Shorts = 0xDEF09ABC56781234L;  // little-endian
        checkEqualsLong(result4Shorts, expected4Shorts, "4-short Add merge");

        // ==================== Boundary value tests ====================

        // Test maximum values (all 0xFF)
        int maxResult = maxBytesAdd(maxArr);
        int maxExpected = 0xFFFFFFFF;
        checkEquals(maxResult, maxExpected, "Max bytes (0xFFFFFFFF)");

        // Test minimum values (all 0x00)
        int minResult = minBytesAdd(minArr);
        int minExpected = 0x00000000;
        checkEquals(minResult, minExpected, "Min bytes (0x00000000)");

        // Test long boundary - maximum
        long maxLongAdd = mergeEightBytesWithAdd(maxArr);
        long maxLongOr = mergeLongWithOr(maxArr);
        checkEqualsLong(maxLongAdd, maxLongOr, "Max long Add vs Or");
        checkEqualsLong(maxLongAdd, 0xFFFFFFFFFFFFFFFFL, "Max long value");

        // Test long boundary - minimum
        long minLongAdd = mergeEightBytesWithAdd(minArr);
        long minLongOr = mergeLongWithOr(minArr);
        checkEqualsLong(minLongAdd, minLongOr, "Min long Add vs Or");
        checkEqualsLong(minLongAdd, 0L, "Min long value");

        // ==================== Negative tests (correctness, not optimization) ====================

        // Signed byte test - verify correct semantics even if not optimized
        // 0x80 as signed byte is -128, so arr[0] = -128
        // 0x7F as signed byte is 127, so (arr[1] << 8) = 127 << 8 = 32512
        // Result should be -128 + 32512 = 32384
        int signedResult = signedByteAdd(signedArr);
        int signedExpected = ((byte)0x80) + (((byte)0x7F) << 8);  // -128 + 32512 = 32384
        checkEquals(signedResult, signedExpected, "Signed byte (correctness)");

        // Mixed Or/Add tests - verify correct semantics
        // (0x12 & 0xFF) + ((0x34 & 0xFF) << 8) | ((0x56 & 0xFF) << 16)
        // = 0x12 + 0x3400 | 0x560000
        // = 0x3412 | 0x560000 = 0x563412
        int mixedResult = mixedOrAdd(testArr);
        int mixedExpected = (testArr[0] & 0xFF) + ((testArr[1] & 0xFF) << 8) | ((testArr[2] & 0xFF) << 16);
        checkEquals(mixedResult, mixedExpected, "Mixed Or/Add (correctness)");

        // ==================== Nodes with other uses tests (eme64 review comment #4) ====================

        // Test load with other uses - correctness check
        // The load b0 is used both in the merge and separately, so merging should be prevented
        // We verify the computation is still correct even if merging doesn't happen
        int loadMultiResult = loadWithOtherUses(testArr);
        int loadMultiExpected = (testArr[0] & 0xFF) + ((testArr[1] & 0xFF) << 8);
        checkEquals(loadMultiResult, loadMultiExpected, "Load with other uses (correctness)");

        // Test shift with other uses - correctness check
        int shiftMultiResult = shiftWithOtherUses(testArr);
        int shiftMultiExpected = ((testArr[0] & 0xFF) << 8) + (testArr[1] & 0xFF);
        checkEquals(shiftMultiResult, shiftMultiExpected, "Shift with other uses (correctness)");

        // Test multiple nodes with other uses - correctness check
        int multiNodesResult = multipleNodesWithOtherUses(testArr);
        int multiNodesExpected = (testArr[0] & 0xFF) + ((testArr[1] & 0xFF) << 8) + ((testArr[2] & 0xFF) << 16);
        checkEquals(multiNodesResult, multiNodesExpected, "Multiple nodes with other uses (correctness)");

        // Test single use case - should work and potentially be optimized
        int singleUseResult = singleUseShouldMerge(testArr);
        int singleUseExpected = (testArr[0] & 0xFF) + ((testArr[1] & 0xFF) << 8);
        checkEquals(singleUseResult, singleUseExpected, "Single use (should merge)");

        System.out.println("All tests passed!");
    }

    private static void checkEquals(int actual, int expected, String testName) {
        if (actual != expected) {
            throw new RuntimeException(testName + " failed: got 0x" + Integer.toHexString(actual) +
                                       ", expected 0x" + Integer.toHexString(expected));
        }
    }

    private static void checkEqualsLong(long actual, long expected, String testName) {
        if (actual != expected) {
            throw new RuntimeException(testName + " failed: got 0x" + Long.toHexString(actual) +
                                       ", expected 0x" + Long.toHexString(expected));
        }
    }
}
