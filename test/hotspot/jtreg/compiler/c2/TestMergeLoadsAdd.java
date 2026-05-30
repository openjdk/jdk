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
 * You should have received a copy of the GNU General Public License
 * version 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package compiler.c2;

import compiler.lib.ir_framework.*;
import java.util.Random;
import jdk.test.lib.Utils;

/*
 * @test
 * @bug 8345485
 * @summary Test MergeLoads with Add operator support (unsigned loads only)
 * @library /test/lib /
 * @run main compiler.c2.TestMergeLoadsAdd
 */

public class TestMergeLoadsAdd {

    private static final String BYTE_ARRAY = "byte\\[int:>=0] \\(java/lang/Cloneable,java/io/Serializable\\)";
    private static final String SHORT_ARRAY = "short\\[int:>=0] \\(java/lang/Cloneable,java/io/Serializable\\)";

    private static final Random RANDOM = Utils.getRandomInstance();
    private static final int RANGE = 100;

    byte[] aB = new byte[RANGE];
    short[] aS = new short[RANGE / 2];

    static volatile int sideEffect;

    public static void main(String[] args) {
        TestFramework framework = new TestFramework(TestMergeLoadsAdd.class);
        framework.addFlags("-XX:+UseUnalignedAccesses");
        framework.start();
    }

    private void initRandom() {
        for (int i = 0; i < aB.length; i++) {
            aB[i] = (byte) RANDOM.nextInt();
        }
        for (int i = 0; i < aS.length; i++) {
            aS[i] = (short) RANDOM.nextInt();
        }
    }

    // ==================== Reference implementations (@DontCompile) ====================

    @DontCompile
    static int refMergeTwoBytesWithAdd(byte[] arr) {
        return (arr[0] & 0xFF) + ((arr[1] & 0xFF) << 8);
    }

    @DontCompile
    static int refMergeFourBytesWithAdd(byte[] arr) {
        return (arr[0] & 0xFF) + ((arr[1] & 0xFF) << 8) +
               ((arr[2] & 0xFF) << 16) + ((arr[3] & 0xFF) << 24);
    }

    @DontCompile
    static int refMergeTwoShortsWithAdd(short[] arr) {
        return (arr[0] & 0xFFFF) + ((arr[1] & 0xFFFF) << 16);
    }

    @DontCompile
    static long refMergeEightBytesWithAdd(byte[] arr) {
        return (arr[0] & 0xFFL) + ((arr[1] & 0xFFL) << 8) +
               ((arr[2] & 0xFFL) << 16) + ((arr[3] & 0xFFL) << 24) +
               ((arr[4] & 0xFFL) << 32) + ((arr[5] & 0xFFL) << 40) +
               ((arr[6] & 0xFFL) << 48) + ((arr[7] & 0xFFL) << 56);
    }

    @DontCompile
    static long refMergeFourShortsWithAdd(short[] arr) {
        return (arr[0] & 0xFFFFL) + ((arr[1] & 0xFFFFL) << 16) +
               ((arr[2] & 0xFFFFL) << 32) + ((arr[3] & 0xFFFFL) << 48);
    }

    @DontCompile
    static int refSignedByteAdd(byte[] arr) {
        return arr[0] + (arr[1] << 8);
    }

    @DontCompile
    static int refMixedOrAdd(byte[] arr) {
        return (arr[0] & 0xFF) + ((arr[1] & 0xFF) << 8) | ((arr[2] & 0xFF) << 16);
    }

    @DontCompile
    static int refMixedAddOr(byte[] arr) {
        return (arr[0] & 0xFF) | ((arr[1] & 0xFF) << 8) + ((arr[2] & 0xFF) << 16);
    }

    @DontCompile
    static int refLoadWithOtherUses(byte[] arr) {
        int b0 = arr[0] & 0xFF;
        int merged = b0 + ((arr[1] & 0xFF) << 8);
        return merged;
    }

    @DontCompile
    static int refShiftWithOtherUses(byte[] arr) {
        int shifted = (arr[0] & 0xFF) << 8;
        int merged = shifted + (arr[1] & 0xFF);
        return merged;
    }

    @DontCompile
    static int refMultipleNodesWithOtherUses(byte[] arr) {
        int b0 = arr[0] & 0xFF;
        int b1 = arr[1] & 0xFF;
        return (b0 & 0xFF) + ((b1 & 0xFF) << 8) + ((arr[2] & 0xFF) << 16);
    }

    // ==================== Positive tests with @IR verification ====================

    // 2 bytes with Add -> LoadUS, with ReverseBytesUS on big-endian
    @Test
    @IR(counts = {
            IRNode.LOAD_UB_OF_CLASS, BYTE_ARRAY, "0",
            IRNode.LOAD_US_OF_CLASS, BYTE_ARRAY, "1",
        },
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatformAnd = {"little-endian", "true"})
    @IR(counts = {
            IRNode.LOAD_UB_OF_CLASS, BYTE_ARRAY, "0",
            IRNode.LOAD_US_OF_CLASS, BYTE_ARRAY, "1",
            IRNode.REVERSE_BYTES_US, "1",
        },
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatform = {"big-endian", "true"})
    int mergeTwoBytesWithAdd() {
        return (aB[0] & 0xFF) + ((aB[1] & 0xFF) << 8);
    }

    // 2 shorts with Add -> LoadI
    @Test
    @IR(counts = {
            IRNode.LOAD_S_OF_CLASS, SHORT_ARRAY, "0",
            IRNode.LOAD_US_OF_CLASS, SHORT_ARRAY, "0",
            IRNode.LOAD_I_OF_CLASS, SHORT_ARRAY, "1",
            IRNode.LOAD_L_OF_CLASS, SHORT_ARRAY, "0",
        },
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatform = {"little-endian", "true"})
    int mergeTwoShortsWithAdd() {
        return (aS[0] & 0xFFFF) + ((aS[1] & 0xFFFF) << 16);
    }

    // 4 bytes with Add -> LoadI
    @Test
    @IR(counts = {
            IRNode.LOAD_B_OF_CLASS, BYTE_ARRAY, "0",
            IRNode.LOAD_UB_OF_CLASS, BYTE_ARRAY, "0",
            IRNode.LOAD_US_OF_CLASS, BYTE_ARRAY, "0",
            IRNode.LOAD_I_OF_CLASS, BYTE_ARRAY, "1",
            IRNode.LOAD_L_OF_CLASS, BYTE_ARRAY, "0",
        },
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatform = {"little-endian", "true"})
    int mergeFourBytesWithAdd() {
        return (aB[0] & 0xFF) + ((aB[1] & 0xFF) << 8) +
               ((aB[2] & 0xFF) << 16) + ((aB[3] & 0xFF) << 24);
    }

    @Test
    @IR(counts = {
            IRNode.LOAD_UB_OF_CLASS, BYTE_ARRAY, "0",
            IRNode.LOAD_I_OF_CLASS, BYTE_ARRAY, "0",
            IRNode.LOAD_L_OF_CLASS, BYTE_ARRAY, "1",
        },
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatformAnd = {"little-endian", "true"})
    @IR(counts = {
            IRNode.LOAD_UB_OF_CLASS, BYTE_ARRAY, "0",
            IRNode.LOAD_I_OF_CLASS, BYTE_ARRAY, "0",
            IRNode.LOAD_L_OF_CLASS, BYTE_ARRAY, "1",
            IRNode.REVERSE_BYTES_L, "1",
        },
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatform = {"big-endian", "true"})
    long mergeEightBytesWithAdd() {
        return (aB[0] & 0xFFL) + ((aB[1] & 0xFFL) << 8) +
               ((aB[2] & 0xFFL) << 16) + ((aB[3] & 0xFFL) << 24) +
               ((aB[4] & 0xFFL) << 32) + ((aB[5] & 0xFFL) << 40) +
               ((aB[6] & 0xFFL) << 48) + ((aB[7] & 0xFFL) << 56);
    }

    @Test
    @IR(counts = {
            IRNode.LOAD_S_OF_CLASS, SHORT_ARRAY, "0",
            IRNode.LOAD_US_OF_CLASS, SHORT_ARRAY, "0",
            IRNode.LOAD_I_OF_CLASS, SHORT_ARRAY, "0",
            IRNode.LOAD_L_OF_CLASS, SHORT_ARRAY, "1",
        },
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatform = {"little-endian", "true"})
    long mergeFourShortsWithAdd() {
        return (aS[0] & 0xFFFFL) + ((aS[1] & 0xFFFFL) << 16) +
               ((aS[2] & 0xFFFFL) << 32) + ((aS[3] & 0xFFFFL) << 48);
    }

    // ==================== Negative tests - should NOT merge ====================

    // Signed byte + Add - should NOT merge (LoadB can be negative, causing carry)
    @Test
    @IR(counts = {
            IRNode.LOAD_B_OF_CLASS, BYTE_ARRAY, "2",
        },
        applyIf = {"UseUnalignedAccesses", "true"})
    int signedByteAdd() {
        return aB[0] + (aB[1] << 8);
    }

    // Mixed Or and Add - AddI subchain can merge independently
    @Test
    @IR(counts = {
            IRNode.LOAD_UB_OF_CLASS,  BYTE_ARRAY, "1",
            IRNode.LOAD_US_OF_CLASS,  BYTE_ARRAY, "1",
        },
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatform = {"little-endian", "true"})
    int mixedOrAdd() {
        return (aB[0] & 0xFF) + ((aB[1] & 0xFF) << 8) | ((aB[2] & 0xFF) << 16);
    }

    // Mixed Add and Or - AddI subchain missing shift 0, should NOT merge
    @Test
    @IR(counts = {
            IRNode.LOAD_UB_OF_CLASS, BYTE_ARRAY, "3",
        },
        applyIf = {"UseUnalignedAccesses", "true"})
    int mixedAddOr() {
        return (aB[0] & 0xFF) | ((aB[1] & 0xFF) << 8) + ((aB[2] & 0xFF) << 16);
    }

    // ==================== "Other uses" tests (eme64 reviewer requirement) ====================

    // Load node has other uses - should NOT merge
    @Test
    @IR(counts = {
            IRNode.LOAD_UB_OF_CLASS, BYTE_ARRAY, "2",
        },
        applyIf = {"UseUnalignedAccesses", "true"})
    int loadWithOtherUses() {
        int b0 = aB[0] & 0xFF;
        int merged = b0 + ((aB[1] & 0xFF) << 8);
        sideEffect = b0;  // Other use of b0 (which depends on load)
        return merged;
    }

    // Shift node has other uses - should NOT merge
    @Test
    @IR(counts = {
            IRNode.LOAD_UB_OF_CLASS, BYTE_ARRAY, "2",
        },
        applyIf = {"UseUnalignedAccesses", "true"})
    int shiftWithOtherUses() {
        int shifted = (aB[0] & 0xFF) << 8;
        int merged = shifted + (aB[1] & 0xFF);
        sideEffect = shifted;  // Other use of shifted value
        return merged;
    }

    // Multiple nodes have other uses - should NOT merge
    @Test
    @IR(counts = {
            IRNode.LOAD_UB_OF_CLASS, BYTE_ARRAY, "3",
        },
        applyIf = {"UseUnalignedAccesses", "true"})
    int multipleNodesWithOtherUses() {
        int b0 = aB[0] & 0xFF;
        int b1 = aB[1] & 0xFF;
        int shifted0 = b0 << 8;
        int shifted1 = b1 << 16;
        sideEffect = shifted0 + shifted1;  // Other uses
        return (b0 & 0xFF) + ((b1 & 0xFF) << 8) + ((aB[2] & 0xFF) << 16);
    }

    // ==================== Single use test - should merge ====================

    @Test
    @IR(counts = {
            IRNode.LOAD_UB_OF_CLASS, BYTE_ARRAY, "0",
            IRNode.LOAD_US_OF_CLASS, BYTE_ARRAY, "1",
        },
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatformAnd = {"little-endian", "true"})
    @IR(counts = {
            IRNode.LOAD_UB_OF_CLASS, BYTE_ARRAY, "0",
            IRNode.LOAD_US_OF_CLASS, BYTE_ARRAY, "1",
            IRNode.REVERSE_BYTES_US, "1",
        },
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatform = {"big-endian", "true"})
    int singleUseShouldMerge() {
        int b0 = aB[0] & 0xFF;
        int b1 = aB[1] & 0xFF;
        return b0 + (b1 << 8);  // Each load has single use
    }

    // ==================== Boundary value test ====================

    @Test
    int boundaryBytesAdd() {
        return (aB[0] & 0xFF) + ((aB[1] & 0xFF) << 8) +
               ((aB[2] & 0xFF) << 16) + ((aB[3] & 0xFF) << 24);
    }

    // ==================== Run method for correctness verification ====================

    @Run(test = {
            "mergeTwoBytesWithAdd",
            "mergeTwoShortsWithAdd",
            "mergeFourBytesWithAdd",
            "mergeEightBytesWithAdd",
            "mergeFourShortsWithAdd",
            "signedByteAdd",
            "mixedOrAdd",
            "mixedAddOr",
            "loadWithOtherUses",
            "shiftWithOtherUses",
            "multipleNodesWithOtherUses",
            "singleUseShouldMerge",
            "boundaryBytesAdd",
    })
    public void runCorrectnessTests(RunInfo info) {
        int iters = info.isWarmUp() ? 1_000 : 10_000;
        for (int iter = 0; iter < iters; iter++) {
            initRandom();

            // Positive tests - verify correctness
            checkEQ(refMergeTwoBytesWithAdd(aB), mergeTwoBytesWithAdd(), "mergeTwoBytesWithAdd");
            checkEQ(refMergeTwoShortsWithAdd(aS), mergeTwoShortsWithAdd(), "mergeTwoShortsWithAdd");
            checkEQ(refMergeFourBytesWithAdd(aB), mergeFourBytesWithAdd(), "mergeFourBytesWithAdd");
            checkEQLong(refMergeEightBytesWithAdd(aB), mergeEightBytesWithAdd(), "mergeEightBytesWithAdd");
            checkEQLong(refMergeFourShortsWithAdd(aS), mergeFourShortsWithAdd(), "mergeFourShortsWithAdd");

            // Negative tests - verify correctness (optimization may not apply but values must be correct)
            checkEQ(refSignedByteAdd(aB), signedByteAdd(), "signedByteAdd");
            checkEQ(refMixedOrAdd(aB), mixedOrAdd(), "mixedOrAdd");
            checkEQ(refMixedAddOr(aB), mixedAddOr(), "mixedAddOr");

            // "Other uses" tests - verify correctness
            checkEQ(refLoadWithOtherUses(aB), loadWithOtherUses(), "loadWithOtherUses");
            checkEQ(refShiftWithOtherUses(aB), shiftWithOtherUses(), "shiftWithOtherUses");
            checkEQ(refMultipleNodesWithOtherUses(aB), multipleNodesWithOtherUses(), "multipleNodesWithOtherUses");

            // Single use test
            checkEQ(refMergeTwoBytesWithAdd(aB), singleUseShouldMerge(), "singleUseShouldMerge");

            // Boundary value test
            checkEQ(refMergeFourBytesWithAdd(aB), boundaryBytesAdd(), "boundaryBytesAdd");
        }
    }

    private static void checkEQ(int expected, int actual, String test) {
        if (expected != actual) {
            throw new RuntimeException(test + " failed: expected 0x" + Integer.toHexString(expected) +
                                       ", got 0x" + Integer.toHexString(actual));
        }
    }

    private static void checkEQLong(long expected, long actual, String test) {
        if (expected != actual) {
            throw new RuntimeException(test + " failed: expected 0x" + Long.toHexString(expected) +
                                       ", got 0x" + Long.toHexString(actual));
        }
    }
}
