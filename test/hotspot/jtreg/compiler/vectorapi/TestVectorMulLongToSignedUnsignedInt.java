/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package compiler.vectorapi;

import jdk.incubator.vector.*;
import static jdk.incubator.vector.VectorOperators.AND;
import static jdk.incubator.vector.VectorOperators.MUL;
import static jdk.incubator.vector.VectorOperators.LSHR;
import static jdk.incubator.vector.VectorOperators.ASHR;
import compiler.lib.generators.Generators;
import compiler.lib.ir_framework.*;
import compiler.lib.verify.*;

/*
 * @test
 * @bug 8384963 8383905
 * @key randomness
 * @summary C2: Incorrect uint constant match mishandles negative values in vectors
 * @modules jdk.incubator.vector
 * @library /test/lib /
 * @run driver compiler.vectorapi.TestVectorMulLongToSignedUnsignedInt
 */
public class TestVectorMulLongToSignedUnsignedInt {
    static final VectorSpecies<Long> SPECIES = LongVector.SPECIES_PREFERRED;
    static final int SIZE = SPECIES.length();

    private static final Generators RD = Generators.G;

    static final long[] src1 = new long[SIZE];
    static final long[] src2 = new long[SIZE];
    static long[] res = new long[SIZE];

    static final boolean[] mask_arr = new boolean[SIZE];
    static final VectorMask<Long> MASK;

    // Random compile-time-constant masks.
    static final long RAND_MASK1 = RD.longs().next();
    static final long RAND_MASK2 = RD.longs().next();

    // Random compile-time-constant shift count in [0, 63]. A shift >= 32 clears
    // the upper doubleword (fits uint); a smaller shift may not.
    static final int RAND_SHIFT1 = RD.ints().next() & 0x3F;

    // Random input arrays for the random-mask correctness cases.
    static final long[] rsrc1 = new long[SIZE];
    static final long[] rsrc2 = new long[SIZE];

    static {
        for (int i = 0; i < SIZE; i++) {
            src1[i] = 0x1_0000_0001L;
            src2[i] = 0x2_0000_0002L;
            mask_arr[i] = (i % 2) == 0;
        }
        MASK = VectorMask.fromArray(SPECIES, mask_arr, 0);

        RD.fill(RD.longs(), rsrc1);
        RD.fill(RD.longs(), rsrc2);
    }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(10000)
                     .addFlags("--add-modules=jdk.incubator.vector")
                     .start();
    }

    // Case 1: Negative mask (-2L = 0xFFFF_FFFF_FFFF_FFFE).
    @Test
    @IR(counts = {IRNode.AND_VL, " >0 ",
                  IRNode.MUL_VL, " >0 "},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    @IR(failOn = {IRNode.X86_VMULUDQ_REG},
        phase = CompilePhase.MATCHING,
        applyIfCPUFeature = {"avx", "true"})
    @IR(failOn = {IRNode.AARCH64_VMULL_UINT_SVE2},
        phase = CompilePhase.MATCHING,
        applyIfCPUFeature = {"sve2", "true"})
    @IR(failOn = {IRNode.AARCH64_VMULL_UINT_NEON},
        phase = CompilePhase.MATCHING,
        applyIfCPUFeatureAnd = {"asimd", "true", "sve", "false"})
    public static void testNegativeMask() {
        LongVector v1 = LongVector.fromArray(SPECIES, src1, 0);
        LongVector v2 = LongVector.fromArray(SPECIES, src2, 0);
        v1.lanewise(AND, -2L).lanewise(MUL, v2.lanewise(AND, -2L)).intoArray(res, 0);
    }

    @Run(test = "testNegativeMask")
    public void runNegativeMask() {
        testNegativeMask();
        long[] expected = new long[SPECIES.length()];
        for (int i = 0; i < SPECIES.length(); i++) {
            expected[i] = (src1[i] & -2L) * (src2[i] & -2L);
        }
        Verify.checkEQ(res, expected);
    }

    // Case 3: Mask = 0x1_0000_0000L (bit 32 set, exceeds uint range).
    @Test
    @IR(counts = {IRNode.AND_VL, " >0 ",
                  IRNode.MUL_VL, " >0 "},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    @IR(failOn = {IRNode.X86_VMULUDQ_REG},
        phase = CompilePhase.MATCHING,
        applyIfCPUFeature = {"avx", "true"})
    @IR(failOn = {IRNode.AARCH64_VMULL_UINT_SVE2},
        phase = CompilePhase.MATCHING,
        applyIfCPUFeature = {"sve2", "true"})
    @IR(failOn = {IRNode.AARCH64_VMULL_UINT_NEON}, phase = CompilePhase.MATCHING, applyIfCPUFeatureAnd = {"asimd", "true", "sve", "false"})
    public static void testBit32SetMask() {
        LongVector v1 = LongVector.fromArray(SPECIES, src1, 0);
        LongVector v2 = LongVector.fromArray(SPECIES, src2, 0);
        v1.lanewise(AND, 0x1_0000_0000L).lanewise(MUL, v2.lanewise(AND, 0x1_0000_0000L)).intoArray(res, 0);
    }

    @Run(test = "testBit32SetMask")
    public void runBit32SetMask() {
        testBit32SetMask();
        long[] expected = new long[SPECIES.length()];
        for (int i = 0; i < SPECIES.length(); i++) {
            expected[i] = (src1[i] & 0x1_0000_0000L) * (src2[i] & 0x1_0000_0000L);
        }
        Verify.checkEQ(res, expected);
    }

    // Case 4: Mask = Long.MIN_VALUE (0x8000_0000_0000_0000).
    @Test
    @IR(counts = {IRNode.AND_VL, " >0 ",
                  IRNode.MUL_VL, " >0 "},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    @IR(failOn = {IRNode.X86_VMULUDQ_REG},
        phase = CompilePhase.MATCHING,
        applyIfCPUFeature = {"avx", "true"})
    @IR(failOn = {IRNode.AARCH64_VMULL_UINT_SVE2},
        phase = CompilePhase.MATCHING,
        applyIfCPUFeature = {"sve2", "true"})
    @IR(failOn = {IRNode.AARCH64_VMULL_UINT_NEON},
        phase = CompilePhase.MATCHING,
        applyIfCPUFeatureAnd = {"asimd", "true", "sve", "false"})
    public static void testMinValueMask() {
        LongVector v1 = LongVector.fromArray(SPECIES, src1, 0);
        LongVector v2 = LongVector.fromArray(SPECIES, src2, 0);
        v1.lanewise(AND, Long.MIN_VALUE).lanewise(MUL, v2.lanewise(AND, Long.MIN_VALUE)).intoArray(res, 0);
    }

    @Run(test = "testMinValueMask")
    public void runMinValueMask() {
        testMinValueMask();
        long[] expected = new long[SPECIES.length()];
        for (int i = 0; i < SPECIES.length(); i++) {
            expected[i] = (src1[i] & Long.MIN_VALUE) * (src2[i] & Long.MIN_VALUE);
        }
        Verify.checkEQ(res, expected);
    }

    // Case 5: Mask = 0xFFFF_FFFFL (exactly uint max, boundary valid case).
    @Test
    @IR(counts = {IRNode.MUL_VL, " >0 "},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    @IR(counts = {IRNode.X86_VMULUDQ_REG, " >0 "},
        phase = CompilePhase.MATCHING,
        applyIfCPUFeature = {"avx", "true"})
    @IR(counts = {IRNode.AARCH64_VMULL_UINT_SVE2, " >0 "},
        phase = CompilePhase.MATCHING,
        applyIfCPUFeature = {"sve2", "true"})
    @IR(counts = {IRNode.AARCH64_VMULL_UINT_NEON, " >0 "},
        phase = CompilePhase.MATCHING,
        applyIfCPUFeatureAnd = {"asimd", "true", "sve", "false"})
    public static void testUintMaxMask() {
        LongVector v1 = LongVector.fromArray(SPECIES, src1, 0);
        LongVector v2 = LongVector.fromArray(SPECIES, src2, 0);
        v1.lanewise(AND, 0xFFFF_FFFFL).lanewise(MUL, v2.lanewise(AND, 0xFFFF_FFFFL)).intoArray(res, 0);
    }

    @Run(test = "testUintMaxMask")
    public void runUintMaxMask() {
        testUintMaxMask();
        long[] expected = new long[SPECIES.length()];
        for (int i = 0; i < SPECIES.length(); i++) {
            expected[i] = (src1[i] & 0xFFFF_FFFFL) * (src2[i] & 0xFFFF_FFFFL);
        }
        Verify.checkEQ(res, expected);
    }

    // Case 6: Small mask (0xFFFFL), clearly fits in uint.
    @Test
    @IR(counts = {IRNode.MUL_VL, " >0 "},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    @IR(counts = {IRNode.X86_VMULUDQ_REG, " >0 "},
        phase = CompilePhase.MATCHING,
        applyIfCPUFeature = {"avx", "true"})
    @IR(counts = {IRNode.AARCH64_VMULL_UINT_SVE2, " >0 "},
        phase = CompilePhase.MATCHING,
        applyIfCPUFeature = {"sve2", "true"})
    @IR(counts = {IRNode.AARCH64_VMULL_UINT_NEON, " >0 "},
        phase = CompilePhase.MATCHING,
        applyIfCPUFeatureAnd = {"asimd", "true", "sve", "false"})
    public static void testSmallMask() {
        LongVector v1 = LongVector.fromArray(SPECIES, src1, 0);
        LongVector v2 = LongVector.fromArray(SPECIES, src2, 0);
        v1.lanewise(AND, 0xFFFFL).lanewise(MUL, v2.lanewise(AND, 0xFFFFL)).intoArray(res, 0);
    }

    @Run(test = "testSmallMask")
    public void runSmallMask() {
        testSmallMask();
        long[] expected = new long[SPECIES.length()];
        for (int i = 0; i < SPECIES.length(); i++) {
            expected[i] = (src1[i] & 0xFFFFL) * (src2[i] & 0xFFFFL);
        }
        Verify.checkEQ(res, expected);
    }

    // Case 7: URShift by 32 clears upper doubleword.
    @Test
    @IR(counts = {IRNode.MUL_VL, " >0 ",
                  IRNode.URSHIFT_VL, " >0 "},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    @IR(counts = {IRNode.X86_VMULUDQ_REG, " >0 "},
        phase = CompilePhase.MATCHING,
        applyIfCPUFeature = {"avx", "true"})
    @IR(counts = {IRNode.AARCH64_VMULL_UINT_SVE2, " >0 "},
        phase = CompilePhase.MATCHING,
        applyIfCPUFeature = {"sve2", "true"})
    @IR(counts = {IRNode.AARCH64_VMULL_UINT_NEON, " >0 "},
        phase = CompilePhase.MATCHING,
        applyIfCPUFeatureAnd = {"asimd", "true", "sve", "false"})
    public static void testURShift32() {
        LongVector v1 = LongVector.fromArray(SPECIES, src1, 0);
        LongVector v2 = LongVector.fromArray(SPECIES, src2, 0);
        v1.lanewise(LSHR, 32).lanewise(MUL, v2.lanewise(LSHR, 32)).intoArray(res, 0);
    }

    @Run(test = "testURShift32")
    public void runURShift32() {
        testURShift32();
        long[] expected = new long[SPECIES.length()];
        for (int i = 0; i < SPECIES.length(); i++) {
            expected[i] = (src1[i] >>> 32) * (src2[i] >>> 32);
        }
        Verify.checkEQ(res, expected);
    }

    // Case 8: Asymmetric — one input valid uint mask, other negative mask.
    @Test
    @IR(counts = {IRNode.AND_VL, " >0 ",
                  IRNode.MUL_VL, " >0 "},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    @IR(failOn = {IRNode.X86_VMULUDQ_REG},
        phase = CompilePhase.MATCHING,
        applyIfCPUFeature = {"avx", "true"})
    @IR(failOn = {IRNode.AARCH64_VMULL_UINT_SVE2},
        phase = CompilePhase.MATCHING,
        applyIfCPUFeature = {"sve2", "true"})
    @IR(failOn = {IRNode.AARCH64_VMULL_UINT_NEON},
        phase = CompilePhase.MATCHING,
        applyIfCPUFeatureAnd = {"asimd", "true", "sve", "false"})
    public static void testAsymmetricMask() {
        LongVector v1 = LongVector.fromArray(SPECIES, src1, 0);
        LongVector v2 = LongVector.fromArray(SPECIES, src2, 0);
        v1.lanewise(AND, 0xFFFF_FFFFL).lanewise(MUL, v2.lanewise(AND, -2L)).intoArray(res, 0);
    }

    @Run(test = "testAsymmetricMask")
    public void runAsymmetricMask() {
        testAsymmetricMask();
        long[] expected = new long[SPECIES.length()];
        for (int i = 0; i < SPECIES.length(); i++) {
            expected[i] = (src1[i] & 0xFFFF_FFFFL) * (src2[i] & -2L);
        }
        Verify.checkEQ(res, expected);
    }

    // Case 9: Mixed — one input URShift (valid), other negative mask (invalid).
    // Note: -2L is used (not -1L) since AND with -1L is identity and gets folded.
    @Test
    @IR(counts = {IRNode.URSHIFT_VL, " >0 ",
                  IRNode.AND_VL, " >0 ",
                  IRNode.MUL_VL, " >0 "},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    @IR(failOn = {IRNode.X86_VMULUDQ_REG},
        phase = CompilePhase.MATCHING,
        applyIfCPUFeature = {"avx", "true"})
    @IR(failOn = {IRNode.AARCH64_VMULL_UINT_SVE2},
        phase = CompilePhase.MATCHING,
        applyIfCPUFeature = {"sve2", "true"})
    @IR(failOn = {IRNode.AARCH64_VMULL_UINT_NEON},
        phase = CompilePhase.MATCHING,
        applyIfCPUFeatureAnd = {"asimd", "true", "sve", "false"})
    public static void testMixedURShiftAndNegMask() {
        LongVector v1 = LongVector.fromArray(SPECIES, src1, 0);
        LongVector v2 = LongVector.fromArray(SPECIES, src2, 0);
        v1.lanewise(LSHR, 32).lanewise(MUL, v2.lanewise(AND, -2L)).intoArray(res, 0);
    }

    @Run(test = "testMixedURShiftAndNegMask")
    public void runMixedURShiftAndNegMask() {
        testMixedURShiftAndNegMask();
        long[] expected = new long[SPECIES.length()];
        for (int i = 0; i < SPECIES.length(); i++) {
            expected[i] = (src1[i] >>> 32) * (src2[i] & -2L);
        }
        Verify.checkEQ(res, expected);
    }

    // Case 10: Predicated AndV (uint path). Inactive lanes preserves destination with non-zero upper 32 bits.
    @Test
    @IR(counts = {IRNode.AND_VL, " >0 ",
                  IRNode.MUL_VL, " >0 "},
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true", "rvv", "true"})
    @IR(failOn = {IRNode.X86_VMULUDQ_REG},
        phase = CompilePhase.MATCHING,
        applyIfCPUFeature = {"avx512f", "true"})
    @IR(failOn = {IRNode.AARCH64_VMULL_UINT_SVE2},
        phase = CompilePhase.MATCHING,
        applyIfCPUFeature = {"sve2", "true"})
    @IR(failOn = {IRNode.AARCH64_VMULL_UINT_NEON},
        phase = CompilePhase.MATCHING,
        applyIfCPUFeatureAnd = {"asimd", "true", "sve", "false"})
    public static void testPredicatedAndMask() {
        LongVector v1 = LongVector.fromArray(SPECIES, src1, 0);
        LongVector v2 = LongVector.fromArray(SPECIES, src2, 0);
        v1.lanewise(AND, 0xFFFF_FFFFL, MASK).lanewise(MUL, v2.lanewise(AND, 0xFFFF_FFFFL, MASK)).intoArray(res, 0);
    }

    @Run(test = "testPredicatedAndMask")
    public void runPredicatedAndMask() {
        testPredicatedAndMask();
        long[] expected = new long[SIZE];
        for (int i = 0; i < SIZE; i++) {
            long a = mask_arr[i] ? (src1[i] & 0xFFFF_FFFFL) : src1[i];
            long b = mask_arr[i] ? (src2[i] & 0xFFFF_FFFFL) : src2[i];
            expected[i] = a * b;
        }
        Verify.checkEQ(res, expected);
    }

    // Case 11: Predicated URShiftVL by 32 (uint path). Inactive lanes preserves destination with non-zero upper 32 bits.
    @Test
    @IR(counts = {IRNode.URSHIFT_VL, " >0 ",
                  IRNode.MUL_VL, " >0 "},
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true", "rvv", "true"})
    @IR(failOn = {IRNode.X86_VMULUDQ_REG},
        phase = CompilePhase.MATCHING,
        applyIfCPUFeature = {"avx512f", "true"})
    @IR(failOn = {IRNode.AARCH64_VMULL_UINT_SVE2},
        phase = CompilePhase.MATCHING,
        applyIfCPUFeature = {"sve2", "true"})
    @IR(failOn = {IRNode.AARCH64_VMULL_UINT_NEON},
        phase = CompilePhase.MATCHING,
        applyIfCPUFeatureAnd = {"asimd", "true", "sve", "false"})
    public static void testPredicatedURShift32() {
        LongVector v1 = LongVector.fromArray(SPECIES, src1, 0);
        LongVector v2 = LongVector.fromArray(SPECIES, src2, 0);
        v1.lanewise(LSHR, 32, MASK).lanewise(MUL, v2.lanewise(LSHR, 32, MASK)).intoArray(res, 0);
    }

    @Run(test = "testPredicatedURShift32")
    public void runPredicatedURShift32() {
        testPredicatedURShift32();
        long[] expected = new long[SIZE];
        for (int i = 0; i < SIZE; i++) {
            long a = mask_arr[i] ? (src1[i] >>> 32) : src1[i];
            long b = mask_arr[i] ? (src2[i] >>> 32) : src2[i];
            expected[i] = a * b;
        }
        Verify.checkEQ(res, expected);
    }

    // Case 12: Predicated RShiftVL (arithmetic) by 32.
    @Test
    @IR(counts = {IRNode.RSHIFT_VL, " >0 ",
                  IRNode.MUL_VL, " >0 "},
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true", "rvv", "true"})
    @IR(failOn = {IRNode.X86_VMULDQ_REG},
        phase = CompilePhase.MATCHING,
        applyIfCPUFeature = {"avx512f", "true"})
    @IR(failOn = {IRNode.AARCH64_VMULL_INT_SVE2},
        phase = CompilePhase.MATCHING,
        applyIfCPUFeature = {"sve2", "true"})
    @IR(failOn = {IRNode.AARCH64_VMULL_INT_NEON},
        phase = CompilePhase.MATCHING,
        applyIfCPUFeatureAnd = {"asimd", "true", "sve", "false"})
    public static void testPredicatedRShift32() {
        LongVector v1 = LongVector.fromArray(SPECIES, src1, 0);
        LongVector v2 = LongVector.fromArray(SPECIES, src2, 0);
        v1.lanewise(ASHR, 32, MASK).lanewise(MUL, v2.lanewise(ASHR, 32, MASK)).intoArray(res, 0);
    }

    @Run(test = "testPredicatedRShift32")
    public void runPredicatedRShift32() {
        testPredicatedRShift32();
        long[] expected = new long[SIZE];
        for (int i = 0; i < SIZE; i++) {
            long a = mask_arr[i] ? (src1[i] >> 32) : src1[i];
            long b = mask_arr[i] ? (src2[i] >> 32) : src2[i];
            expected[i] = a * b;
        }
        Verify.checkEQ(res, expected);
    }

    // Random-constant correctness cases with no IR rules.

    // Case 13: AND pattern with random masks on both inputs.
    @Test
    public static void testRandomAndMasks() {
        LongVector v1 = LongVector.fromArray(SPECIES, rsrc1, 0);
        LongVector v2 = LongVector.fromArray(SPECIES, rsrc2, 0);
        v1.lanewise(AND, RAND_MASK1).lanewise(MUL, v2.lanewise(AND, RAND_MASK2)).intoArray(res, 0);
    }

    @Run(test = "testRandomAndMasks")
    public void runRandomAndMasks() {
        testRandomAndMasks();
        long[] expected = new long[SIZE];
        for (int i = 0; i < SIZE; i++) {
            expected[i] = (rsrc1[i] & RAND_MASK1) * (rsrc2[i] & RAND_MASK2);
        }
        Verify.checkEQ(res, expected);
    }

    // Case 14: URShiftV pattern with a random shift count on both inputs.
    @Test
    public static void testRandomURShift() {
        LongVector v1 = LongVector.fromArray(SPECIES, rsrc1, 0);
        LongVector v2 = LongVector.fromArray(SPECIES, rsrc2, 0);
        v1.lanewise(LSHR, RAND_SHIFT1).lanewise(MUL, v2.lanewise(LSHR, RAND_SHIFT1)).intoArray(res, 0);
    }

    @Run(test = "testRandomURShift")
    public void runRandomURShift() {
        testRandomURShift();
        long[] expected = new long[SIZE];
        for (int i = 0; i < SIZE; i++) {
            expected[i] = (rsrc1[i] >>> RAND_SHIFT1) * (rsrc2[i] >>> RAND_SHIFT1);
        }
        Verify.checkEQ(res, expected);
    }
}
