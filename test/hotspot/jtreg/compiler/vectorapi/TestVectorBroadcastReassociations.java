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

/*
 * @test
 * @bug 8358521
 * @summary Optimize vector operations by reassociating broadcasted inputs
 * @modules jdk.incubator.vector
 * @library /test/lib /
 * @run driver compiler.vectorapi.TestVectorBroadcastReassociations
 */

package compiler.vectorapi;

import compiler.lib.ir_framework.*;
import jdk.incubator.vector.*;
import java.util.stream.IntStream;

public class TestVectorBroadcastReassociations {

    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector");
    }

    /* =======================
     * INT
     * ======================= */

    static final VectorSpecies<Integer> ISP = IntVector.SPECIES_PREFERRED;
    static int ia = 17, ib = 9;

    @Test
    @IR(failOn = IRNode.ADD_VI,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.ADD_I, ">= 1", IRNode.REPLICATE_I, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static int int_add() {
        return IntVector.broadcast(ISP, ia)
                .add(IntVector.broadcast(ISP, ib))
                .reduceLanes(VectorOperators.ADD);
    }

    @Test
    @IR(failOn = IRNode.SUB_VI,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.SUB_I, ">= 1", IRNode.REPLICATE_I, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static int int_sub() {
        return IntVector.broadcast(ISP, ia)
                .sub(IntVector.broadcast(ISP, ib))
                .reduceLanes(VectorOperators.ADD);
    }

    @Test
    @IR(failOn = IRNode.MUL_VI,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MUL_I, ">= 1", IRNode.REPLICATE_I, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static int int_mul() {
        return IntVector.broadcast(ISP, ia)
                .mul(IntVector.broadcast(ISP, ib))
                .reduceLanes(VectorOperators.ADD);
    }

    @Test
    @IR(failOn = IRNode.AND_VI,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.AND_I, ">= 1", IRNode.REPLICATE_I, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static int int_and() {
        return IntVector.broadcast(ISP, ia)
                .and(IntVector.broadcast(ISP, ib))
                .reduceLanes(VectorOperators.ADD);
    }

    @Test
    @IR(failOn = IRNode.OR_VI,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.OR_I, ">= 1", IRNode.REPLICATE_I, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static int int_or() {
        return IntVector.broadcast(ISP, ia)
                .or(IntVector.broadcast(ISP, ib))
                .reduceLanes(VectorOperators.ADD);
    }

    @Test
    @IR(failOn = IRNode.XOR_VI,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.XOR_I, ">= 1", IRNode.REPLICATE_I, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static int int_xor() {
        return IntVector.broadcast(ISP, ia)
                .lanewise(VectorOperators.XOR, IntVector.broadcast(ISP, ib))
                .reduceLanes(VectorOperators.ADD);
    }

    @Test
    @IR(failOn = IRNode.MIN_VI,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MIN_I, ">= 1", IRNode.REPLICATE_I, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static int int_min() {
        return IntVector.broadcast(ISP, ia)
                .min(IntVector.broadcast(ISP, ib))
                .reduceLanes(VectorOperators.ADD);
    }

    @Test
    @IR(failOn = IRNode.MAX_VI,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MAX_I, ">= 1", IRNode.REPLICATE_I, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static int int_max() {
        return IntVector.broadcast(ISP, ia)
                .max(IntVector.broadcast(ISP, ib))
                .reduceLanes(VectorOperators.ADD);
    }

    /* =======================
     * LONG
     * ======================= */

    static final VectorSpecies<Long> LSP = LongVector.SPECIES_256;
    static long la = 42L, lb = 13L;

    @Test
    @IR(failOn = IRNode.ADD_VL,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.ADD_L, ">= 1", IRNode.REPLICATE_L, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static long long_add() {
        return LongVector.broadcast(LSP, la)
                .add(LongVector.broadcast(LSP, lb))
                .reduceLanes(VectorOperators.ADD);
    }

    @Test
    @IR(failOn = IRNode.MUL_VL,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MUL_L, ">= 1", IRNode.REPLICATE_L, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static long long_mul() {
        return LongVector.broadcast(LSP, la)
                .mul(LongVector.broadcast(LSP, lb))
                .reduceLanes(VectorOperators.ADD);
    }

    @Test
    @IR(failOn = IRNode.MIN_VL,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = {IRNode.REPLICATE_L, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static long long_min() {
        return LongVector.broadcast(LSP, la)
                .min(LongVector.broadcast(LSP, lb))
                .reduceLanes(VectorOperators.ADD);
    }

    @Test
    @IR(failOn = IRNode.MAX_VL,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = {IRNode.REPLICATE_L, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static long long_max() {
        return LongVector.broadcast(LSP, la)
                .max(LongVector.broadcast(LSP, lb))
                .reduceLanes(VectorOperators.ADD);
    }

    /* =======================
     * FLOAT
     * ======================= */

    static final VectorSpecies<Float> FSP = FloatVector.SPECIES_256;
    static float fa = 9.0f, fb = 4.0f;

    @Test
    @IR(failOn = IRNode.ADD_VF,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.ADD_F, ">= 1", IRNode.REPLICATE_F, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static float float_add() {
        return FloatVector.broadcast(FSP, fa)
                .add(FloatVector.broadcast(FSP, fb))
                .reduceLanes(VectorOperators.ADD);
    }

    @Test
    @IR(failOn = IRNode.SUB_VF,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.SUB_F, ">= 1", IRNode.REPLICATE_F, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static float float_sub() {
        return FloatVector.broadcast(FSP, fa)
                .sub(FloatVector.broadcast(FSP, fb))
                .reduceLanes(VectorOperators.ADD);
    }

    @Test
    @IR(failOn = IRNode.MUL_VF,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MUL_F, ">= 1", IRNode.REPLICATE_F, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static float float_mul() {
        return FloatVector.broadcast(FSP, fa)
                .mul(FloatVector.broadcast(FSP, fb))
                .reduceLanes(VectorOperators.ADD);
    }

    @Test
    @IR(failOn = IRNode.DIV_VF,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.DIV_F, ">= 1", IRNode.REPLICATE_F, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static float float_div() {
        return FloatVector.broadcast(FSP, fa)
                .div(FloatVector.broadcast(FSP, fb))
                .reduceLanes(VectorOperators.ADD);
    }

    @Test
    @IR(failOn = IRNode.MIN_VF,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MIN_F, ">= 1", IRNode.REPLICATE_F, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static float float_min() {
        return FloatVector.broadcast(FSP, fa)
                .min(FloatVector.broadcast(FSP, fb))
                .reduceLanes(VectorOperators.ADD);
    }

    @Test
    @IR(failOn = IRNode.MAX_VF,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MAX_F, ">= 1", IRNode.REPLICATE_F, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static float float_max() {
        return FloatVector.broadcast(FSP, fa)
                .max(FloatVector.broadcast(FSP, fb))
                .reduceLanes(VectorOperators.ADD);
    }

    @Test
    @IR(failOn = IRNode.SQRT_VF,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.SQRT_F, ">= 1", IRNode.REPLICATE_F, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static float float_sqrt() {
        return FloatVector.broadcast(FSP, fa)
                .sqrt()
                .reduceLanes(VectorOperators.ADD);
    }

    @Test
    @IR(failOn = IRNode.FMA_VF,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.FMA_F, ">= 1", IRNode.REPLICATE_F, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static float float_fma() {
        return FloatVector.broadcast(FSP, fa)
                .fma(FloatVector.broadcast(FSP, fb),
                     FloatVector.broadcast(FSP, 2.0f))
                .reduceLanes(VectorOperators.ADD);
    }

    /* =======================
     * DOUBLE
     * ======================= */

    static final VectorSpecies<Double> DSP = DoubleVector.SPECIES_256;
    static double da = 16.0, db = 3.0;

    @Test
    @IR(failOn = IRNode.ADD_VD,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.ADD_D, ">= 1", IRNode.REPLICATE_D, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static double double_add() {
        return DoubleVector.broadcast(DSP, fa)
                .add(DoubleVector.broadcast(DSP, fb))
                .reduceLanes(VectorOperators.ADD);
    }

    @Test
    @IR(failOn = IRNode.SUB_VD,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.SUB_D, ">= 1", IRNode.REPLICATE_D, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static double double_sub() {
        return DoubleVector.broadcast(DSP, fa)
                .sub(DoubleVector.broadcast(DSP, fb))
                .reduceLanes(VectorOperators.ADD);
    }

    @Test
    @IR(failOn = IRNode.MUL_VD,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MUL_D, ">= 1", IRNode.REPLICATE_D, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static double double_mul() {
        return DoubleVector.broadcast(DSP, fa)
                .mul(DoubleVector.broadcast(DSP, fb))
                .reduceLanes(VectorOperators.ADD);
    }

    @Test
    @IR(failOn = IRNode.DIV_VD,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.DIV_D, ">= 1", IRNode.REPLICATE_D, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static double double_div() {
        return DoubleVector.broadcast(DSP, fa)
                .div(DoubleVector.broadcast(DSP, fb))
                .reduceLanes(VectorOperators.ADD);
    }

    @Test
    @IR(failOn = IRNode.MIN_VD,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MIN_D, ">= 1", IRNode.REPLICATE_D, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static double double_min() {
        return DoubleVector.broadcast(DSP, da)
                .min(DoubleVector.broadcast(DSP, db))
                .reduceLanes(VectorOperators.ADD);
    }

    @Test
    @IR(failOn = IRNode.MAX_VD,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MAX_D, ">= 1", IRNode.REPLICATE_D, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static double double_max() {
        return DoubleVector.broadcast(DSP, da)
                .max(DoubleVector.broadcast(DSP, db))
                .reduceLanes(VectorOperators.ADD);
    }

    @Test
    @IR(failOn = IRNode.SQRT_VD,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.SQRT_D, ">= 1", IRNode.REPLICATE_D, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static double double_sqrt() {
        return DoubleVector.broadcast(DSP, da)
                .sqrt()
                .reduceLanes(VectorOperators.ADD);
    }

    @Test
    @IR(failOn = IRNode.FMA_VD,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.FMA_D, ">= 1", IRNode.REPLICATE_D, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static double double_fma() {
        return DoubleVector.broadcast(DSP, da)
                .fma(DoubleVector.broadcast(DSP, db),
                     DoubleVector.broadcast(DSP, 4.0))
                .reduceLanes(VectorOperators.ADD);
    }

    /* =======================
     * Reassociation
     * ======================= */
    static int [] intIn;
    static int [] intOut;
    static {
        intIn = IntStream.range(0, ISP.length()).toArray();
        intOut = new int[ISP.length()];
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.ADD_VI, IRNode.VECTOR_SIZE_ANY, " 1 ", IRNode.ADD_I, ">= 1",
                   IRNode.REPLICATE_I, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    @Warmup(value = 10000)
    static void test_reassociation1() {
        IntVector.broadcast(ISP, ia)
                 .lanewise(VectorOperators.ADD,
                           IntVector.broadcast(ISP, ib)
                                    .lanewise(VectorOperators.ADD,
                                              IntVector.fromArray(ISP, intIn, 0)))
                 .intoArray(intOut, 0);
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.ADD_VI, IRNode.VECTOR_SIZE_ANY, " 1 ", IRNode.ADD_I, ">= 1",
                   IRNode.REPLICATE_I, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    @Warmup(value = 10000)
    static void test_reassociation2() {
        IntVector.broadcast(ISP, ia)
                 .lanewise(VectorOperators.ADD,
                           IntVector.fromArray(ISP, intIn, 0)
                                    .lanewise(VectorOperators.ADD,
                                              IntVector.broadcast(ISP, ib)))
                 .intoArray(intOut, 0);
    }
}
