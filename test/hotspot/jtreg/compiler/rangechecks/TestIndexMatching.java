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

package compiler.rangechecks;

import compiler.lib.ir_framework.*;
import java.util.Objects;

/**
 * @test
 * @bug 8382378
 * @summary Tests that valid index expressions in range checks are matched for
 *          range check optimizations, and that invalid expressions are
 *          rejected. These tests should be kept updated with the grammar-based
 *          specification provided as a comment on
 *          PhaseIdealLoop::is_scaled_iv_plus_offset().
 * @library /test/lib /
 * @run driver ${test.main.class}
 */

public class TestIndexMatching {

    static int intVar0     = 0;
    static int intVar3     = 3;
    static int intVar100   = 100;
    static int intVar1000  = 1000;
    static int intVar10000 = 10000;

    static long longVar0     = 0L;
    static long longVar3     = 3L;
    static long longVar100   = 100L;
    static long longVar1000  = 1000L;
    static long longVar10000 = 10000L;

    public static void main(String[] args) {
        TestFramework.run();
    }

    // Positive tests: expect the index expressions to be matched and range
    // check optimizations to trigger. Use C2-opaque range check limits to
    // increase the likelihood that loop elimination is indeed caused by range
    // check optimizations and not accidentally by other transformations.

    // Int indices using int induction variables

    // iv
    @Test
    @IR(failOn = {IRNode.ANY_LOOP})
    static void testIntIndexIntIV() {
        for (int iv = 0; iv <= intVar100; iv++) {
            Objects.checkIndex(iv, intVar10000);
        }
    }

    // AddI(iv, e)
    @Test
    @IR(failOn = {IRNode.ANY_LOOP})
    static void testIntIndexIntIVWithAddedVarOffset() {
        for (int iv = 0; iv <= intVar100; iv++) {
            Objects.checkIndex(iv + intVar0, intVar10000);
        }
    }

    // SubI(iv, e)
    @Test
    @IR(failOn = {IRNode.ANY_LOOP})
    static void testIntIndexIntIVWithSubtractedVarOffset() {
        for (int iv = 0; iv <= intVar100; iv++) {
            Objects.checkIndex(iv - intVar0, intVar10000);
        }
    }

    // SubI(e, iv)
    @Test
    @IR(failOn = {IRNode.ANY_LOOP})
    static void testIntIndexIntIVSubtractedFromVarOffset() {
        for (int iv = 0; iv <= intVar100; iv++) {
            Objects.checkIndex(intVar1000 - iv, intVar10000);
        }
    }

    // AddI(iv, ConI)
    @Test
    @IR(failOn = {IRNode.ANY_LOOP})
    static void testIntIndexIntIVWithAddedConstOffset() {
        for (int iv = 0; iv <= intVar100; iv++) {
            Objects.checkIndex(iv + 7, intVar10000);
        }
    }

    // AddI((AddI(iv, e)), ConI)
    @Test
    @IR(failOn = {IRNode.ANY_LOOP})
    static void testIntIndexIntIVWithAddedNestedOffset() {
        for (int iv = 0; iv <= intVar100; iv++) {
            Objects.checkIndex((iv + intVar0) + 7, intVar10000);
        }
    }

    // MulI(iv, ConI)
    @Test
    @IR(failOn = {IRNode.ANY_LOOP})
    static void testIntIndexIntIVWithMulScaling() {
        for (int iv = 0; iv <= intVar100; iv++) {
            Objects.checkIndex(iv * 11, intVar10000);
        }
    }

    // AddI(MulI(iv, ConI), e)
    @Test
    @IR(failOn = {IRNode.ANY_LOOP})
    static void testIntIndexIntIVWithMulScalingAndAddedVarOffset() {
        for (int iv = 0; iv <= intVar100; iv++) {
            Objects.checkIndex((iv * 11) + intVar0, intVar10000);
        }
    }

    // AddI(AddI(MulI(iv, ConI), e), ConI)
    @Test
    @IR(failOn = {IRNode.ANY_LOOP})
    static void testIntIndexIntIVWithMulScalingAndAddedNestedOffset() {
        for (int iv = 0; iv <= intVar100; iv++) {
            Objects.checkIndex(((iv * 11) + intVar0) + 7, intVar10000);
        }
    }

    // LShiftI(iv, ConI)
    @Test
    @IR(failOn = {IRNode.ANY_LOOP})
    static void testIntIndexIntIVWithShiftScaling() {
        for (int iv = 0; iv <= intVar100; iv++) {
            Objects.checkIndex(iv << 2, intVar10000);
        }
    }

    // SubI(0, MulI(iv, ConI))
    @Test
    @IR(failOn = {IRNode.ANY_LOOP})
    static void testIntIndexIntIVWithNegatedMulScaling() {
        for (int iv = -intVar100; iv <= 0; iv++) {
            Objects.checkIndex(0 - (iv * 11), intVar10000);
        }
    }

    // AddI(MulI(iv, ConI), MulI(iv, ConI))
    @Test
    @IR(failOn = {IRNode.ANY_LOOP})
    static void testIntIndexIntIVWithAddOfMulsScaling() {
        for (int iv = 0; iv <= intVar100; iv++) {
            Objects.checkIndex((iv * 3) + (iv * 7), intVar10000);
        }
    }

    // SubI(LShiftI(iv, ConI), MulI(iv, ConI))
    @Test
    @IR(failOn = {IRNode.ANY_LOOP})
    static void testIntIndexIntIVWithSubOfShiftAndMulScaling() {
        for (int iv = 0; iv <= intVar100; iv++) {
            Objects.checkIndex((iv << 4) - (iv * 13), intVar10000);
        }
    }

    // Long indices using long induction variables

    // iv
    @Test
    @IR(failOn = {IRNode.CONV_I2L}, phase = CompilePhase.AFTER_PARSING)
    @IR(failOn = {IRNode.ANY_LOOP})
    static void testLongIndexLongIV() {
        for (long iv = 0L; iv <= longVar100; iv++) {
            Objects.checkIndex(iv, longVar10000);
        }
    }

    // AddL(iv, e)
    @Test
    @IR(failOn = {IRNode.CONV_I2L}, phase = CompilePhase.AFTER_PARSING)
    @IR(failOn = {IRNode.ANY_LOOP})
    static void testLongIndexLongIVWithAddedVarOffset() {
        for (long iv = 0L; iv <= longVar100; iv++) {
            Objects.checkIndex(iv + longVar0, longVar10000);
        }
    }

    // SubL(iv, e)
    @Test
    @IR(failOn = {IRNode.CONV_I2L}, phase = CompilePhase.AFTER_PARSING)
    @IR(failOn = {IRNode.ANY_LOOP})
    static void testLongIndexLongIVWithSubtractedVarOffset() {
        for (long iv = 0L; iv <= longVar100; iv++) {
            Objects.checkIndex(iv - longVar0, longVar10000);
        }
    }

    // SubL(e, iv)
    @Test
    @IR(failOn = {IRNode.CONV_I2L}, phase = CompilePhase.AFTER_PARSING)
    @IR(failOn = {IRNode.ANY_LOOP})
    static void testLongIndexLongIVSubtractedFromVarOffset() {
        for (long iv = 0L; iv <= longVar100; iv++) {
            Objects.checkIndex(longVar1000 - iv, longVar10000);
        }
    }

    // AddL(iv, ConL)
    @Test
    @IR(failOn = {IRNode.CONV_I2L}, phase = CompilePhase.AFTER_PARSING)
    @IR(failOn = {IRNode.ANY_LOOP})
    static void testLongIndexLongIVWithAddedConstOffset() {
        for (long iv = 0L; iv <= longVar100; iv++) {
            Objects.checkIndex(iv + 1L, longVar10000);
        }
    }

    // AddL((AddL(iv, e)), ConL)
    @Test
    @IR(failOn = {IRNode.CONV_I2L}, phase = CompilePhase.AFTER_PARSING)
    @IR(failOn = {IRNode.ANY_LOOP})
    static void testLongIndexLongIVWithAddedNestedOffset() {
        for (long iv = 0L; iv <= longVar100; iv++) {
            Objects.checkIndex((iv + longVar0) + 7L, longVar10000);
        }
    }

    // MulL(iv, ConL)
    @Test
    @IR(failOn = {IRNode.CONV_I2L}, phase = CompilePhase.AFTER_PARSING)
    @IR(failOn = {IRNode.ANY_LOOP})
    static void testLongIndexLongIVWithMulScaling() {
        for (long iv = 0L; iv <= longVar100; iv++) {
            Objects.checkIndex(iv * 11L, longVar10000);
        }
    }

    // AddL(MulL(iv, ConL), e)
    @Test
    @IR(failOn = {IRNode.CONV_I2L}, phase = CompilePhase.AFTER_PARSING)
    @IR(failOn = {IRNode.ANY_LOOP})
    static void testLongIndexLongIVWithMulScalingAndAddedVarOffset() {
        for (long iv = 0L; iv <= longVar100; iv++) {
            Objects.checkIndex((iv * 11L) + longVar0, longVar10000);
        }
    }

    // AddL(AddL(MulL(iv, ConL), e), ConL)
    @Test
    @IR(failOn = {IRNode.CONV_I2L}, phase = CompilePhase.AFTER_PARSING)
    @IR(failOn = {IRNode.ANY_LOOP})
    static void testLongIndexLongIVWithMulScalingAndAddedNestedOffset() {
        for (long iv = 0L; iv <= longVar100; iv++) {
            Objects.checkIndex(((iv * 11L) + longVar0) + 7L, longVar10000);
        }
    }

    // LShiftL(iv, ConI)
    @Test
    @IR(failOn = {IRNode.CONV_I2L}, phase = CompilePhase.AFTER_PARSING)
    @IR(failOn = {IRNode.ANY_LOOP})
    static void testLongIndexLongIVWithShiftScaling() {
        for (long iv = 0L; iv <= longVar100; iv++) {
            Objects.checkIndex(iv << 2, longVar10000);
        }
    }

    // SubL(0L, MulL(iv, ConL))
    @Test
    @IR(failOn = {IRNode.CONV_I2L}, phase = CompilePhase.AFTER_PARSING)
    @IR(failOn = {IRNode.ANY_LOOP})
    static void testLongIndexLongIVWithNegatedMulScaling() {
        for (long iv = -longVar100; iv <= 0; iv++) {
            Objects.checkIndex(0L - (iv * 11L), longVar10000);
        }
    }

    // Long indices using int induction variables

    // ConvI2L(iv)
    @Test
    @IR(counts = {IRNode.CONV_I2L, ">0"}, phase = CompilePhase.AFTER_PARSING)
    @IR(failOn = {IRNode.ANY_LOOP})
    static void testLongIndexIntIV() {
        for (int iv = 0; iv <= intVar100; iv++) {
            Objects.checkIndex((long)iv, longVar10000);
        }
    }

    // AddL(ConvI2L(iv), e)
    @Test
    @IR(counts = {IRNode.CONV_I2L, ">0"}, phase = CompilePhase.AFTER_PARSING)
    @IR(failOn = {IRNode.ANY_LOOP})
    static void testLongIndexIntIVWithAddedVarOffset() {
        for (int iv = 0; iv <= intVar100; iv++) {
            Objects.checkIndex((long)iv + longVar0, longVar10000);
        }
    }

    // AddL(ConvI2L(iv), ConL)
    @Test
    @IR(counts = {IRNode.CONV_I2L, ">0"}, phase = CompilePhase.AFTER_PARSING)
    @IR(failOn = {IRNode.ANY_LOOP})
    static void testLongIndexIntIVWithAddedConstOffset() {
        for (int iv = 0; iv <= intVar100; iv++) {
            Objects.checkIndex((long)iv + 1L, longVar10000);
        }
    }

    // MulL(ConvI2L(iv), ConL)
    @Test
    @IR(counts = {IRNode.CONV_I2L, ">0"}, phase = CompilePhase.AFTER_PARSING)
    @IR(failOn = {IRNode.ANY_LOOP})
    static void testLongIndexIntIVWithMulScaling() {
        for (int iv = 0; iv <= intVar100; iv++) {
            Objects.checkIndex((long)iv * 11L, longVar10000);
        }
    }

    // AddL(MulL(ConvI2L(iv), ConL), e)
    @Test
    @IR(counts = {IRNode.CONV_I2L, ">0"}, phase = CompilePhase.AFTER_PARSING)
    @IR(failOn = {IRNode.ANY_LOOP})
    static void testLongIndexIntIVWithMulScalingAndAddedVarOffset() {
        for (int iv = 0; iv <= intVar100; iv++) {
            Objects.checkIndex(((long)iv * 11L) + longVar0, longVar10000);
        }
    }

    // LShiftL(ConvI2L(iv), ConI)
    @Test
    @IR(counts = {IRNode.CONV_I2L, ">0"}, phase = CompilePhase.AFTER_PARSING)
    @IR(failOn = {IRNode.ANY_LOOP})
    static void testLongIndexIntIVWithShiftScaling() {
        for (int iv = 0; iv <= intVar100; iv++) {
            Objects.checkIndex((long)iv << 2, longVar10000);
        }
    }

    // SubL(0L, MulL(ConvI2L(iv), ConL))
    @Test
    @IR(counts = {IRNode.CONV_I2L, ">0"}, phase = CompilePhase.AFTER_PARSING)
    @IR(failOn = {IRNode.ANY_LOOP})
    static void testLongIndexIntIVWithNegatedMulScaling() {
        for (int iv = -intVar100; iv <= 0; iv++) {
            Objects.checkIndex(0L - ((long)iv * 11L), longVar10000);
        }
    }

    // ConvI2L(MulI(iv, ConI))
    // Range check optimizations still trigger because the short scaling is
    // optimized away during IGVN.
    @Test
    @IR(counts = {IRNode.CONV_I2L, ">0"}, phase = CompilePhase.AFTER_PARSING)
    @IR(failOn = {IRNode.ANY_LOOP})
    static void testLongIndexIntIVWithShortMulScaling() {
        for (int iv = 0; iv <= intVar100; iv++) {
            Objects.checkIndex((long)(iv * 1), longVar10000);
        }
    }

    // ConvI2L(LShiftI(iv, ConI))
    // Range check optimizations still trigger because the short scaling is
    // optimized away during IGVN.
    @Test
    @IR(counts = {IRNode.CONV_I2L, ">0"}, phase = CompilePhase.AFTER_PARSING)
    @IR(failOn = {IRNode.ANY_LOOP})
    static void testLongIndexIntIVWithShortShiftScaling() {
        for (int iv = 0; iv <= intVar100; iv++) {
            Objects.checkIndex((long)(iv << 0), longVar10000);
        }
    }

    // ConvI2L(AddI(MulI(iv, ConI), MulI(iv, ConI)))
    // Range check optimizations still trigger because the short scaling is
    // optimized away during IGVN.
    @Test
    @IR(counts = {IRNode.CONV_I2L, ">0"}, phase = CompilePhase.AFTER_PARSING)
    @IR(failOn = {IRNode.ANY_LOOP})
    static void testLongIndexIntIVWithShortAddOfMulsScaling() {
        for (int iv = 0; iv <= intVar100; iv++) {
            Objects.checkIndex((long)((iv * 3) + (iv * -2)), longVar10000);
        }
    }

    // ConvI2L(SubI(0, MulI(iv, ConI)))
    // Range check optimizations still trigger because the short scaling is
    // optimized away during IGVN.
    @Test
    @IR(counts = {IRNode.CONV_I2L, ">0"}, phase = CompilePhase.AFTER_PARSING)
    @IR(failOn = {IRNode.ANY_LOOP})
    static void testLongIndexIntIVWithShortNegatedSubScaling() {
        for (int iv = 0; iv <= intVar100; iv++) {
            Objects.checkIndex((long)(0 - (iv * -1)), longVar10000);
        }
    }

    // ConvI2L(SubI(MulI(iv, ConI), MulI(iv, ConI)))
    // Range check optimizations still trigger because the short scaling is
    // optimized away during IGVN.
    @Test
    @IR(counts = {IRNode.CONV_I2L, ">0"}, phase = CompilePhase.AFTER_PARSING)
    @IR(failOn = {IRNode.ANY_LOOP})
    static void testLongIndexIntIVWithShortSubOfMulsScaling() {
        for (int iv = 0; iv <= intVar100; iv++) {
            Objects.checkIndex((long)((iv * 3) - (iv * 2)), longVar10000);
        }
    }

    // AddL(MulL(ConvI2L(iv), ConL), MulL(ConvI2L(iv), ConL))
    @Test
    @IR(counts = {IRNode.CONV_I2L, ">0"}, phase = CompilePhase.AFTER_PARSING)
    @IR(failOn = {IRNode.ANY_LOOP})
    static void testLongIndexIntIVWithAddOfMulsScaling() {
        for (int iv = 0; iv <= intVar100; iv++) {
            Objects.checkIndex(((long)iv * 3L) + ((long)iv * 7L), longVar10000);
        }
    }

    // SubL(LShiftL(ConvI2L(iv), ConI), ConvI2L(iv))
    @Test
    @IR(counts = {IRNode.CONV_I2L, ">0"}, phase = CompilePhase.AFTER_PARSING)
    @IR(failOn = {IRNode.ANY_LOOP})
    static void testLongIndexIntIVWithSubOfShiftScaling() {
        for (int iv = 0; iv <= intVar100; iv++) {
            Objects.checkIndex(((long)iv << 3) - iv, longVar10000);
        }
    }

    // ConvI2L(AddI(MulI(iv, ConI), LShiftI(iv, ConI)))
    // Range check optimizations still trigger because the short scaling is
    // optimized away during IGVN.
    @Test
    @IR(counts = {IRNode.CONV_I2L, ">0"}, phase = CompilePhase.AFTER_PARSING)
    @IR(failOn = {IRNode.ANY_LOOP})
    static void testLongIndexIntIVWithShortAddOfMulAndShiftScaling() {
        for (int iv = 0; iv <= intVar100; iv++) {
            Objects.checkIndex((long)((iv * -15) + (iv << 4)), longVar10000);
        }
    }

    // AddL(AddL(ConvI2L(AddI(MulI(iv, ConI), MulI(iv, ConI))), e), ConL)
    // Range check optimizations still trigger because the short scaling is
    // optimized away during IGVN.
    @Test
    @IR(counts = {IRNode.CONV_I2L, ">0"}, phase = CompilePhase.AFTER_PARSING)
    @IR(failOn = {IRNode.ANY_LOOP})
    static void testLongIndexIntIVWithShortAddOfMulsScalingAndAddedNestedOffset() {
        for (int iv = 0; iv <= intVar100; iv++) {
            Objects.checkIndex(((long)((iv * 3) + (iv * -2)) + longVar0) + 7L, longVar10000);
        }
    }

    // Negative tests: expect the index expressions to be rejected and the loop
    // to not be eliminated.

    // Int indices using int induction variables

    // MulI(iv, LoadI(..))
    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, ">0"})
    static void testIntIndexIntIVWithVarMulScaling() {
        for (int iv = 0; iv <= intVar100; iv++) {
            Objects.checkIndex(iv * intVar3, 10000);
        }
    }

    // LShiftI(iv, LoadI(..))
    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, ">0"})
    static void testIntIndexIntIVWithVarShiftScaling() {
        for (int iv = 0; iv <= intVar100; iv++) {
            Objects.checkIndex(iv << intVar0, 10000);
        }
    }

    // MulI(AddI(iv, LoadI(..)), ConI)
    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, ">0"})
    static void testIntIndexIntIVWithNonTrivialMulScaling() {
        for (int iv = 0; iv <= intVar100; iv++) {
            Objects.checkIndex((iv + intVar0) * 11, 10000);
        }
    }

    // LShiftI(AddI(iv, LoadI(..)), LoadI(..))
    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, ">0"})
    static void testIntIndexIntIVWithNonTrivialShiftScaling() {
        for (int iv = 0; iv <= intVar100; iv++) {
            Objects.checkIndex((iv + intVar0) << intVar0, 10000);
        }
    }

    // RShiftI(iv, ConI)
    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, ">0"})
    static void testIntIndexIntIVWithUnsupportedRightShift() {
        for (int iv = 0; iv <= intVar100; iv++) {
            Objects.checkIndex(iv >> 1, 10000);
        }
    }

    // OrI(iv, ConI)
    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, ">0"})
    static void testIntIndexIntIVWithUnsupportedOr() {
        for (int iv = 0; iv <= intVar100; iv++) {
            Objects.checkIndex(iv | 1024, 10000);
        }
    }

    // XorI(iv, ConI)
    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, ">0"})
    static void testIntIndexIntIVWithUnsupportedXor() {
        for (int iv = 0; iv <= intVar100; iv++) {
            Objects.checkIndex(iv ^ 1024, 10000);
        }
    }

    // SubI(0, LShiftI(iv, ConI)) where scale(LShiftI(..)) == min_jint
    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, ">0"})
    static void testIntIndexIntIVWithNegatedMinJIntShiftScaling() {
        for (int iv = 0; iv <= intVar100; iv += 2) {
            Objects.checkIndex(0 - (iv << 31), 10000);
        }
    }

    // Long indices using long induction variables

    // MulL(iv, LoadL(..))
    @Test
    @IR(failOn = {IRNode.CONV_I2L}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.COUNTED_LOOP, ">0"})
    static void testLongIndexLongIVWithVarMulScaling() {
        for (long iv = 0L; iv <= longVar100; iv++) {
            Objects.checkIndex(iv * longVar3, 10000L);
        }
    }

    // LShiftL(iv, LoadI(..))
    @Test
    @IR(failOn = {IRNode.CONV_I2L}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.COUNTED_LOOP, ">0"})
    static void testLongIndexLongIVWithVarShiftScaling() {
        for (long iv = 0L; iv <= longVar100; iv++) {
            Objects.checkIndex(iv << intVar0, 10000L);
        }
    }

    // MulL(AddL(iv, LoadL(..)), ConL)
    @Test
    @IR(failOn = {IRNode.CONV_I2L}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.COUNTED_LOOP, ">0"})
    static void testLongIndexLongIVWithNonTrivialMulScaling() {
        for (long iv = 0L; iv <= longVar100; iv++) {
            Objects.checkIndex((iv + longVar0) * 11L, 10000L);
        }
    }

    // LShiftL(AddL(iv, LoadL(..)), LoadI(..))
    @Test
    @IR(failOn = {IRNode.CONV_I2L}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.COUNTED_LOOP, ">0"})
    static void testLongIndexLongIVWithNonTrivialShiftScaling() {
        for (long iv = 0L; iv <= longVar100; iv++) {
            Objects.checkIndex((iv + longVar0) << intVar0, 10000L);
        }
    }

    // RShiftL(iv, ConI)
    @Test
    @IR(failOn = {IRNode.CONV_I2L}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.COUNTED_LOOP, ">0"})
    static void testLongIndexLongIVWithUnsupportedRightShift() {
        for (long iv = 0L; iv <= longVar100; iv++) {
            Objects.checkIndex(iv >> 1, 10000L);
        }
    }

    // OrL(iv, ConL)
    @Test
    @IR(failOn = {IRNode.CONV_I2L}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.COUNTED_LOOP, ">0"})
    static void testLongIndexLongIVWithUnsupportedOr() {
        for (long iv = 0L; iv <= longVar100; iv++) {
            Objects.checkIndex(iv | 1024L, 10000L);
        }
    }

    // XorL(iv, ConL)
    @Test
    @IR(failOn = {IRNode.CONV_I2L}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.COUNTED_LOOP, ">0"})
    static void testLongIndexLongIVWithUnsupportedXor() {
        for (long iv = 0L; iv <= longVar100; iv++) {
            Objects.checkIndex(iv ^ 1024L, 10000L);
        }
    }

    // SubL(0L, LShiftL(iv, ConI)) where scale(LShiftL(..)) == min_jlong
    @Test
    @IR(failOn = {IRNode.CONV_I2L}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.COUNTED_LOOP, ">0"})
    static void testLongIndexLongIVWithNegatedMinJLongShiftScaling() {
        for (long iv = 0L; iv <= longVar100; iv += 2L) {
            Objects.checkIndex(0L - (iv << 63), 10000L);
        }
    }

    // Long indices using int induction variables

    // ConvI2L(MulI(iv, ConI))
    @Test
    @IR(counts = {IRNode.CONV_I2L, ">0"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.COUNTED_LOOP, ">0"})
    static void testLongIndexIntIVWithNonUnitShortMulScaling() {
        for (int iv = 0; iv <= intVar100; iv++) {
            Objects.checkIndex((long)(iv * 11), 10000L);
        }
    }

    // ConvI2L(LShiftI(iv, ConI))
    @Test
    @IR(counts = {IRNode.CONV_I2L, ">0"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.COUNTED_LOOP, ">0"})
    static void testLongIndexIntIVWithNonUnitShortShiftScaling() {
        for (int iv = 0; iv <= intVar100; iv++) {
            Objects.checkIndex((long)(iv << 2), 10000L);
        }
    }

    // ConvI2L(SubI(0, MulI(iv, ConI)))
    @Test
    @IR(counts = {IRNode.CONV_I2L, ">0"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.COUNTED_LOOP, ">0"})
    static void testLongIndexIntIVWithNonUnitShortNegatedSubScaling() {
        for (int iv = -100; iv <= 0; iv++) {
            Objects.checkIndex((long)(0 - (iv * 3)),
                               // Using a variable range check length is
                               // required here for the index expression not to
                               // be optimized into something else that matches.
                               longVar10000);
        }
    }

    // ConvI2L(SubI(0, LShiftI(iv, ConI))) where scale(LShiftI(..)) == min_jint
    @Test
    @IR(counts = {IRNode.CONV_I2L, ">0"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.COUNTED_LOOP, ">0"})
    static void testLongIndexIntIVWithShortNegatedSubMinJIntShiftScaling() {
        for (int iv = 0; iv <= intVar100; iv += 2) {
            Objects.checkIndex((long)(0 - (iv << 31)), 10000L);
        }
    }

    // ConvI2L(MulI(iv, LoadI(..)))
    @Test
    @IR(counts = {IRNode.CONV_I2L, ">0"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.COUNTED_LOOP, ">0"})
    static void testLongIndexIntIVWithShortVarMulScaling() {
        for (int iv = 0; iv <= intVar100; iv++) {
            Objects.checkIndex((long)(iv * intVar3), 10000L);
        }
    }

    // ConvI2L(LShiftI(iv, LoadI(..)))
    @Test
    @IR(counts = {IRNode.CONV_I2L, ">0"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.COUNTED_LOOP, ">0"})
    static void testLongIndexIntIVWithShortVarShiftScaling() {
        for (int iv = 0; iv <= intVar100; iv++) {
            Objects.checkIndex((long)(iv << intVar3), 10000L);
        }
    }

    // MulL(AddL(ConvI2L(iv), LoadL(..)), ConL)
    @Test
    @IR(counts = {IRNode.CONV_I2L, ">0"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.COUNTED_LOOP, ">0"})
    static void testLongIndexIntIVWithMulOfAddScaling() {
        for (int iv = 0; iv <= intVar100; iv++) {
            Objects.checkIndex(((long)iv + longVar0) * 11L, 10000L);
        }
    }

    // RShiftL(ConvI2L(iv), ConI)
    @Test
    @IR(counts = {IRNode.CONV_I2L, ">0"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.COUNTED_LOOP, ">0"})
    static void testLongIndexIntIVWithUnsupportedRightShift() {
        for (int iv = 0; iv <= intVar100; iv++) {
            Objects.checkIndex((long)iv >> 1, 10000L);
        }
    }

    // ConvI2L(AddI(MulI(iv, ConI), MulI(iv, ConI)))
    @Test
    @IR(counts = {IRNode.CONV_I2L, ">0"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.COUNTED_LOOP, ">0"})
    static void testLongIndexIntIVWithNonUnitShortAddOfMulsScaling() {
        for (int iv = 0; iv <= intVar100; iv++) {
            Objects.checkIndex((long)((iv * 3) + (iv * 5)), 10000L);
        }
    }

    // ConvI2L(AddI(MulI(iv, ConI), LShiftI(iv, ConI)))
    @Test
    @IR(counts = {IRNode.CONV_I2L, ">0"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.COUNTED_LOOP, ">0"})
    static void testLongIndexIntIVWithNonUnitShortAddOfMulAndShiftScaling() {
        for (int iv = 0; iv <= intVar100; iv++) {
            Objects.checkIndex((long)((iv * -15) + (iv << 5)), 10000L);
        }
    }

    // ConvI2L(SubI(MulI(iv, ConI), MulI(iv, ConI)))
    @Test
    @IR(counts = {IRNode.CONV_I2L, ">0"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.COUNTED_LOOP, ">0"})
    static void testLongIndexIntIVWithNonUnitShortSubOfMulsScaling() {
        for (int iv = 0; iv <= intVar100; iv++) {
            Objects.checkIndex((long)((iv * 7) - (iv * 2)), 10000L);
        }
    }

    // ConvI2L(SubI(LShiftI(iv, ConI), LShiftI(iv, ConI)))
    @Test
    @IR(counts = {IRNode.CONV_I2L, ">0"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.COUNTED_LOOP, ">0"})
    static void testLongIndexIntIVWithShortSubOfShiftsScaling() {
        for (int iv = 0; iv <= intVar100; iv++) {
            Objects.checkIndex((long)((iv << 33) - (iv << 0)), longVar10000);
        }
    }

    // ConvI2L(SubI(LShiftI(iv, ConI), LShiftI(iv, ConI)))
    @Test
    @IR(counts = {IRNode.CONV_I2L, ">0"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.COUNTED_LOOP, ">0"})
    static void testLongIndexIntIVWithNonUnitShortSubOfShiftsScaling() {
        for (int iv = 0; iv <= intVar100; iv++) {
            Objects.checkIndex((long)((iv << 5) - (iv << 1)), 10000L);
        }
    }

    // ConvI2L(SubI(0, LShiftI(iv, ConI)))
    @Test
    @IR(counts = {IRNode.CONV_I2L, ">0"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.COUNTED_LOOP, ">0"})
    static void testLongIndexIntIVWithNonUnitShortNegatedSubShiftScaling() {
        for (int iv = -100; iv <= 0; iv++) {
            Objects.checkIndex((long)(0 - (iv << 2)),
                               // Using a variable range check length is
                               // required here for the index expression not to
                               // be optimized into something else that matches.
                               longVar10000);
        }
    }
}
