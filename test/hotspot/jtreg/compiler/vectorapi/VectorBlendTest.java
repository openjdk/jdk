/*
 * Copyright (c) 2026, NVIDIA CORPORATION & AFFILIATES. All rights reserved.
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

import compiler.lib.ir_framework.*;
import compiler.lib.generators.*;
import jdk.incubator.vector.*;
import jdk.test.lib.Asserts;

import java.util.function.DoubleBinaryOperator;
import java.util.function.IntBinaryOperator;
import java.util.function.LongBinaryOperator;

/**
 * @test
 * @bug 8384571 8385051
 * @key randomness
 * @library /test/lib /
 * @summary Test Ideal/Identity transformations for VectorBlendNode
 * @modules jdk.incubator.vector
 *
 * @run driver ${test.main.class}
 */

public class VectorBlendTest {
    private static final VectorSpecies<Integer> I_SPECIES = IntVector.SPECIES_MAX;
    private static final VectorSpecies<Long> L_SPECIES = LongVector.SPECIES_MAX;
    private static final VectorSpecies<Float> F_SPECIES = FloatVector.SPECIES_MAX;
    private static final VectorSpecies<Double> D_SPECIES = DoubleVector.SPECIES_MAX;

    private static final int LENGTH = 128;
    private static final Generators RD = Generators.G;

    private static int[] ia;
    private static int[] ib;
    private static int[] ic;
    private static int[] ir;
    private static int[] ir2;
    private static int[] ir3;
    private static long[] la;
    private static long[] lb;
    private static long[] lc;
    private static long[] lr;
    private static float[] fa;
    private static float[] fb;
    private static float[] fc;
    private static float[] fr;
    private static double[] da;
    private static double[] db;
    private static double[] dc;
    private static double[] dr;
    private static boolean[] m;

    static {
        ia = new int[LENGTH];
        ib = new int[LENGTH];
        ic = new int[LENGTH];
        ir = new int[LENGTH];
        ir2 = new int[LENGTH];
        ir3 = new int[LENGTH];
        la = new long[LENGTH];
        lb = new long[LENGTH];
        lc = new long[LENGTH];
        lr = new long[LENGTH];
        fa = new float[LENGTH];
        fb = new float[LENGTH];
        fc = new float[LENGTH];
        fr = new float[LENGTH];
        da = new double[LENGTH];
        db = new double[LENGTH];
        dc = new double[LENGTH];
        dr = new double[LENGTH];
        m = new boolean[LENGTH];

        Generator<Integer> iGen = RD.ints();
        Generator<Long> lGen = RD.longs();
        Generator<Float> fGen = RD.floats();
        Generator<Double> dGen = RD.doubles();
        RD.fill(iGen, ia);
        RD.fill(iGen, ib);
        RD.fill(iGen, ic);
        RD.fill(lGen, la);
        RD.fill(lGen, lb);
        RD.fill(lGen, lc);
        RD.fill(fGen, fa);
        RD.fill(fGen, fb);
        RD.fill(fGen, fc);
        RD.fill(dGen, da);
        RD.fill(dGen, db);
        RD.fill(dGen, dc);

        for (int i = 0; i < LENGTH; i++) {
            m[i] = (i & 1) == 1;
            // Keep shift counts in [0, 31] so LShiftVI is well-defined.
            ic[i] &= 31;
        }
    }

    @DontInline
    public static void verifyInt(int[] trueValues, int[] falseValues) {
        for (int i = 0; i < I_SPECIES.length(); i++) {
            Asserts.assertEquals(m[i] ? trueValues[i] : falseValues[i], ir[i]);
        }
    }

    @DontInline
    public static void verifyLong(long[] trueValues, long[] falseValues) {
        for (int i = 0; i < L_SPECIES.length(); i++) {
            Asserts.assertEquals(m[i] ? trueValues[i] : falseValues[i], lr[i]);
        }
    }

    @DontInline
    public static void verifyAllInt(int[] expected) {
        for (int i = 0; i < I_SPECIES.length(); i++) {
            Asserts.assertEquals(expected[i], ir[i]);
        }
    }

    @DontInline
    public static void verifyAllLong(long[] expected) {
        for (int i = 0; i < L_SPECIES.length(); i++) {
            Asserts.assertEquals(expected[i], lr[i]);
        }
    }

    @DontInline
    public static void verifyBlendFactor(int[] result,
                                         int[] lhs1, int[] rhs1,
                                         int[] lhs2, int[] rhs2,
                                         IntBinaryOperator op) {
        for (int i = 0; i < I_SPECIES.length(); i++) {
            int x1 = op.applyAsInt(lhs1[i], rhs1[i]);
            int x2 = op.applyAsInt(lhs2[i], rhs2[i]);
            Asserts.assertEquals(m[i] ? x2 : x1, result[i]);
        }
    }

    @DontInline
    public static void verifyBlendFactor(int[] result,
                                         int[] lhs1, int rhs1,
                                         int[] lhs2, int rhs2,
                                         IntBinaryOperator op) {
        for (int i = 0; i < I_SPECIES.length(); i++) {
            int x1 = op.applyAsInt(lhs1[i], rhs1);
            int x2 = op.applyAsInt(lhs2[i], rhs2);
            Asserts.assertEquals(m[i] ? x2 : x1, result[i]);
        }
    }

    @DontInline
    public static void verifyBlendFactor(long[] result,
                                         long[] lhs1, long[] rhs1,
                                         long[] lhs2, long[] rhs2,
                                         LongBinaryOperator op) {
        for (int i = 0; i < L_SPECIES.length(); i++) {
            long x1 = op.applyAsLong(lhs1[i], rhs1[i]);
            long x2 = op.applyAsLong(lhs2[i], rhs2[i]);
            Asserts.assertEquals(m[i] ? x2 : x1, result[i]);
        }
    }

    @FunctionalInterface
    private interface FloatBinaryOperator {
        float applyAsFloat(float a, float b);
    }

    @DontInline
    public static void verifyBlendFactor(float[] result,
                                         float[] lhs1, float[] rhs1,
                                         float[] lhs2, float[] rhs2,
                                         FloatBinaryOperator op) {
        for (int i = 0; i < F_SPECIES.length(); i++) {
            float x1 = op.applyAsFloat(lhs1[i], rhs1[i]);
            float x2 = op.applyAsFloat(lhs2[i], rhs2[i]);
            Asserts.assertEquals(m[i] ? x2 : x1, result[i]);
        }
    }

    @DontInline
    public static void verifyBlendFactor(double[] result,
                                         double[] lhs1, double[] rhs1,
                                         double[] lhs2, double[] rhs2,
                                         DoubleBinaryOperator op) {
        for (int i = 0; i < D_SPECIES.length(); i++) {
            double x1 = op.applyAsDouble(lhs1[i], rhs1[i]);
            double x2 = op.applyAsDouble(lhs2[i], rhs2[i]);
            Asserts.assertEquals(m[i] ? x2 : x1, result[i]);
        }
    }

    // VectorBlend(VectorBlend(a, b, m), c, m) => VectorBlend(a, c, m)
    @Test
    @IR(counts = {IRNode.VECTOR_BLEND_I, "1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testVectorBlendSameMask1() {
        VectorMask<Integer> mask = VectorMask.fromArray(I_SPECIES, m, 0);
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector bv = IntVector.fromArray(I_SPECIES, ib, 0);
        IntVector cv = IntVector.fromArray(I_SPECIES, ic, 0);
        av.blend(bv, mask).blend(cv, mask).intoArray(ir, 0);

        verifyInt(ic, ia);
    }

    // VectorBlend(a, VectorBlend(b, c, m), m) => VectorBlend(a, c, m)
    @Test
    @IR(counts = {IRNode.VECTOR_BLEND_L, "1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx2", "true", "rvv", "true"})
    public static void testVectorBlendSameMask2() {
        VectorMask<Long> mask = VectorMask.fromArray(L_SPECIES, m, 0);
        LongVector av = LongVector.fromArray(L_SPECIES, la, 0);
        LongVector bv = LongVector.fromArray(L_SPECIES, lb, 0);
        LongVector cv = LongVector.fromArray(L_SPECIES, lc, 0);
        av.blend(bv.blend(cv, mask), mask).intoArray(lr, 0);

        verifyLong(lc, la);
    }

    // VectorBlend(a, b, AllOnesMask) => b
    @Test
    @IR(failOn = {IRNode.VECTOR_BLEND_I},
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testBlendMaskAllTrueInt() {
        VectorMask<Integer> mask = I_SPECIES.maskAll(true);
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector bv = IntVector.fromArray(I_SPECIES, ib, 0);
        av.blend(bv, mask).intoArray(ir, 0);

        verifyAllInt(ib);
    }

    // VectorBlend(a, b, AllZerosMask) => a
    @Test
    @IR(failOn = {IRNode.VECTOR_BLEND_L},
        counts = {IRNode.STORE_VECTOR, ">=1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx2", "true", "rvv", "true"})
    public static void testBlendMaskAllFalseLong() {
        VectorMask<Long> mask = L_SPECIES.maskAll(false);
        LongVector av = LongVector.fromArray(L_SPECIES, la, 0);
        LongVector bv = LongVector.fromArray(L_SPECIES, lb, 0);
        av.blend(bv, mask).intoArray(lr, 0);

        verifyAllLong(la);
    }

    // VectorBlend(a, b, NOT(m)) => VectorBlend(b, a, m)
    @Test
    @IR(failOn = {IRNode.XOR_V_MASK, IRNode.XOR_V},
        counts = {IRNode.VECTOR_BLEND_I, "1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testBlendNegatedMaskInt() {
        VectorMask<Integer> mask = VectorMask.fromArray(I_SPECIES, m, 0);
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector bv = IntVector.fromArray(I_SPECIES, ib, 0);
        av.blend(bv, mask.not()).intoArray(ir, 0);

        // mask.not()[i] is true when m[i] is false, and selects bv on those
        // lanes. After the swap, the equivalent VectorBlend selects:
        // m=true -> av, m=false -> bv.
        verifyInt(ia, ib);
    }

    // VectorBlend(AndV(a, c), AndV(b, c), m) => AndV(VectorBlend(a, b, m), c)
    @Test
    @IR(counts = {IRNode.AND_VI, "1", IRNode.VECTOR_BLEND_I, "1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testBlendFactorAndInt() {
        VectorMask<Integer> mask = VectorMask.fromArray(I_SPECIES, m, 0);
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector bv = IntVector.fromArray(I_SPECIES, ib, 0);
        IntVector cv = IntVector.fromArray(I_SPECIES, ic, 0);
        av.lanewise(VectorOperators.AND, cv)
          .blend(bv.lanewise(VectorOperators.AND, cv), mask)
          .intoArray(ir, 0);

        verifyBlendFactor(ir, ia, ic, ib, ic, (a, b) -> a & b);
    }

    // VectorBlend(OrV(a, c), OrV(b, c), m) => OrV(VectorBlend(a, b, m), c)
    @Test
    @IR(counts = {IRNode.OR_VL, "1", IRNode.VECTOR_BLEND_L, "1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx2", "true", "rvv", "true"})
    public static void testBlendFactorOrLong() {
        VectorMask<Long> mask = VectorMask.fromArray(L_SPECIES, m, 0);
        LongVector av = LongVector.fromArray(L_SPECIES, la, 0);
        LongVector bv = LongVector.fromArray(L_SPECIES, lb, 0);
        LongVector cv = LongVector.fromArray(L_SPECIES, lc, 0);
        av.lanewise(VectorOperators.OR, cv)
          .blend(bv.lanewise(VectorOperators.OR, cv), mask)
          .intoArray(lr, 0);

        verifyBlendFactor(lr, la, lc, lb, lc, (a, b) -> a | b);
    }

    // VectorBlend(XorV(a, c), XorV(b, c), m) => XorV(VectorBlend(a, b, m), c)
    @Test
    @IR(counts = {IRNode.XOR_VI, "1", IRNode.VECTOR_BLEND_I, "1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testBlendFactorXorInt() {
        VectorMask<Integer> mask = VectorMask.fromArray(I_SPECIES, m, 0);
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector bv = IntVector.fromArray(I_SPECIES, ib, 0);
        IntVector cv = IntVector.fromArray(I_SPECIES, ic, 0);
        av.lanewise(VectorOperators.XOR, cv)
          .blend(bv.lanewise(VectorOperators.XOR, cv), mask)
          .intoArray(ir, 0);

        verifyBlendFactor(ir, ia, ic, ib, ic, (a, b) -> a ^ b);
    }

    // Common operand on the left side of one inner op and right side of the
    // other -- exercises the commutative-match path of the optimization.
    @Test
    @IR(counts = {IRNode.XOR_VI, "1", IRNode.VECTOR_BLEND_I, "1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testBlendFactorXorCommutedInt() {
        VectorMask<Integer> mask = VectorMask.fromArray(I_SPECIES, m, 0);
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector bv = IntVector.fromArray(I_SPECIES, ib, 0);
        IntVector cv = IntVector.fromArray(I_SPECIES, ic, 0);
        cv.lanewise(VectorOperators.XOR, av)
          .blend(bv.lanewise(VectorOperators.XOR, cv), mask)
          .intoArray(ir, 0);

        verifyBlendFactor(ir, ic, ia, ib, ic, (a, b) -> a ^ b);
    }

    // VectorBlend(AddV(a, c), AddV(b, c), m) => AddV(VectorBlend(a, b, m), c)
    @Test
    @IR(counts = {IRNode.ADD_VI, "1", IRNode.VECTOR_BLEND_I, "1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testBlendFactorAddInt() {
        VectorMask<Integer> mask = VectorMask.fromArray(I_SPECIES, m, 0);
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector bv = IntVector.fromArray(I_SPECIES, ib, 0);
        IntVector cv = IntVector.fromArray(I_SPECIES, ic, 0);
        av.lanewise(VectorOperators.ADD, cv)
          .blend(bv.lanewise(VectorOperators.ADD, cv), mask)
          .intoArray(ir, 0);

        verifyBlendFactor(ir, ia, ic, ib, ic, Integer::sum);
    }

    // VectorBlend(MulV(a, c), MulV(b, c), m) => MulV(VectorBlend(a, b, m), c)
    @Test
    @IR(counts = {IRNode.MUL_VI, "1", IRNode.VECTOR_BLEND_I, "1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testBlendFactorMulInt() {
        VectorMask<Integer> mask = VectorMask.fromArray(I_SPECIES, m, 0);
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector bv = IntVector.fromArray(I_SPECIES, ib, 0);
        IntVector cv = IntVector.fromArray(I_SPECIES, ic, 0);
        av.lanewise(VectorOperators.MUL, cv)
          .blend(bv.lanewise(VectorOperators.MUL, cv), mask)
          .intoArray(ir, 0);

        verifyBlendFactor(ir, ia, ic, ib, ic, (a, b) -> a * b);
    }

    // VectorBlend(SubV(a, c), SubV(b, c), m) => SubV(VectorBlend(a, b, m), c)
    @Test
    @IR(counts = {IRNode.SUB_VL, "1", IRNode.VECTOR_BLEND_L, "1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx2", "true", "rvv", "true"})
    public static void testBlendFactorSubLong() {
        VectorMask<Long> mask = VectorMask.fromArray(L_SPECIES, m, 0);
        LongVector av = LongVector.fromArray(L_SPECIES, la, 0);
        LongVector bv = LongVector.fromArray(L_SPECIES, lb, 0);
        LongVector cv = LongVector.fromArray(L_SPECIES, lc, 0);
        av.lanewise(VectorOperators.SUB, cv)
          .blend(bv.lanewise(VectorOperators.SUB, cv), mask)
          .intoArray(lr, 0);

        verifyBlendFactor(lr, la, lc, lb, lc, (a, b) -> a - b);
    }

    // VectorBlend(SubV(c, a), SubV(c, b), m) => SubV(c, VectorBlend(a, b, m))
    @Test
    @IR(counts = {IRNode.SUB_VI, "1", IRNode.VECTOR_BLEND_I, "1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testBlendFactorSubLeftInt() {
        VectorMask<Integer> mask = VectorMask.fromArray(I_SPECIES, m, 0);
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector bv = IntVector.fromArray(I_SPECIES, ib, 0);
        IntVector cv = IntVector.fromArray(I_SPECIES, ic, 0);
        cv.lanewise(VectorOperators.SUB, av)
          .blend(cv.lanewise(VectorOperators.SUB, bv), mask)
          .intoArray(ir, 0);

        verifyBlendFactor(ir, ic, ia, ic, ib, (a, b) -> a - b);
    }

    // Negative test: non-commutative op with the common operand in different
    // positions in the two inner ops must NOT factor; both SubV nodes survive.
    @Test
    @IR(counts = {IRNode.SUB_VI, "2", IRNode.VECTOR_BLEND_I, "1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testBlendFactorSubNegative() {
        VectorMask<Integer> mask = VectorMask.fromArray(I_SPECIES, m, 0);
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector bv = IntVector.fromArray(I_SPECIES, ib, 0);
        IntVector cv = IntVector.fromArray(I_SPECIES, ic, 0);
        av.lanewise(VectorOperators.SUB, cv)
          .blend(cv.lanewise(VectorOperators.SUB, bv), mask)
          .intoArray(ir, 0);

        verifyBlendFactor(ir, ia, ic, ic, ib, (a, b) -> a - b);
    }

    // VectorBlend(LShiftV(a, c), LShiftV(b, c), m)
    //   => LShiftV(VectorBlend(a, b, m), c)
    @Test
    @IR(counts = {IRNode.LSHIFT_VI, "1", IRNode.VECTOR_BLEND_I, "1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx2", "true", "rvv", "true"})
    public static void testBlendFactorLShiftInt() {
        VectorMask<Integer> mask = VectorMask.fromArray(I_SPECIES, m, 0);
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector bv = IntVector.fromArray(I_SPECIES, ib, 0);
        IntVector cv = IntVector.fromArray(I_SPECIES, ic, 0);
        av.lanewise(VectorOperators.LSHL, cv)
          .blend(bv.lanewise(VectorOperators.LSHL, cv), mask)
          .intoArray(ir, 0);

        verifyBlendFactor(ir, ia, ic, ib, ic, (a, b) -> a << b);
    }

    // Negative test: shift with the common operand on the left must NOT factor.
    // Folding the two counts into a VectorBlend would force is_var_shift=true
    // on the rebuilt shift and regress codegen on ISAs that have cheaper
    // constant-count shifts.
    @Test
    @IR(counts = {IRNode.LSHIFT_VI, "2", IRNode.VECTOR_BLEND_I, "1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx2", "true", "rvv", "true"})
    public static void testBlendFactorLShiftNegative() {
        VectorMask<Integer> mask = VectorMask.fromArray(I_SPECIES, m, 0);
        IntVector cv = IntVector.fromArray(I_SPECIES, ic, 0);
        IntVector av = IntVector.broadcast(I_SPECIES, 3);
        IntVector bv = IntVector.broadcast(I_SPECIES, 5);
        cv.lanewise(VectorOperators.LSHL, av)
          .blend(cv.lanewise(VectorOperators.LSHL, bv), mask)
          .intoArray(ir, 0);

        verifyBlendFactor(ir, ic, 3, ic, 5, (a, b) -> a << b);
    }

    // VectorBlend(RotateLeftV(a, c), RotateLeftV(b, c), m)
    //   => RotateLeftV(VectorBlend(a, b, m), c)
    @Test
    @IR(counts = {IRNode.ROTATE_LEFT_V, "1", IRNode.VECTOR_BLEND_I, "1"},
        applyIfCPUFeatureOr = {"avx512f", "true", "zvbb", "true"})
    public static void testBlendFactorRolInt() {
        VectorMask<Integer> mask = VectorMask.fromArray(I_SPECIES, m, 0);
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector bv = IntVector.fromArray(I_SPECIES, ib, 0);
        IntVector cv = IntVector.fromArray(I_SPECIES, ic, 0);
        av.lanewise(VectorOperators.ROL, cv)
          .blend(bv.lanewise(VectorOperators.ROL, cv), mask)
          .intoArray(ir, 0);

        verifyBlendFactor(ir, ia, ic, ib, ic, Integer::rotateLeft);
    }

    // Negative test: rotate with the common operand on the left must NOT factor
    //  -- folding the two counts would fall off the const-count rotate match
    // (e.g. x86 vprotate_immI8), or trigger the degenerate shift-OR fallback
    // on ISAs that do not support per-lane variable rotates.
    @Test
    @IR(counts = {IRNode.ROTATE_LEFT_V, "2", IRNode.VECTOR_BLEND_I, "1"},
        applyIfCPUFeatureOr = {"avx512f", "true", "zvbb", "true"})
    public static void testBlendFactorRolNegative() {
        VectorMask<Integer> mask = VectorMask.fromArray(I_SPECIES, m, 0);
        IntVector cv = IntVector.fromArray(I_SPECIES, ic, 0);
        IntVector av = IntVector.broadcast(I_SPECIES, 3);
        IntVector bv = IntVector.broadcast(I_SPECIES, 5);
        cv.lanewise(VectorOperators.ROL, av)
          .blend(cv.lanewise(VectorOperators.ROL, bv), mask)
          .intoArray(ir, 0);

        verifyBlendFactor(ir, ic, 3, ic, 5, Integer::rotateLeft);
    }

    // VectorBlend(CompressBitsV(a, c), CompressBitsV(b, c), m)
    //   => CompressBitsV(VectorBlend(a, b, m), c)
    @Test
    @IR(counts = {IRNode.COMPRESS_BITS_VI, "1", IRNode.VECTOR_BLEND_I, "1"},
        applyIfCPUFeatureAnd = {"sve2", "true", "svebitperm", "true"})
    public static void testBlendFactorCompressBitsInt() {
        VectorMask<Integer> mask = VectorMask.fromArray(I_SPECIES, m, 0);
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector bv = IntVector.fromArray(I_SPECIES, ib, 0);
        IntVector cv = IntVector.fromArray(I_SPECIES, ic, 0);
        av.lanewise(VectorOperators.COMPRESS_BITS, cv)
          .blend(bv.lanewise(VectorOperators.COMPRESS_BITS, cv), mask)
          .intoArray(ir, 0);

        verifyBlendFactor(ir, ia, ic, ib, ic, Integer::compress);
    }

    // VectorBlend(ExpandBitsV(a, c), ExpandBitsV(b, c), m)
    //   => ExpandBitsV(VectorBlend(a, b, m), c)
    @Test
    @IR(counts = {IRNode.EXPAND_BITS_VL, "1", IRNode.VECTOR_BLEND_L, "1"},
        applyIfCPUFeatureAnd = {"sve2", "true", "svebitperm", "true"})
    public static void testBlendFactorExpandBitsLong() {
        VectorMask<Long> mask = VectorMask.fromArray(L_SPECIES, m, 0);
        LongVector av = LongVector.fromArray(L_SPECIES, la, 0);
        LongVector bv = LongVector.fromArray(L_SPECIES, lb, 0);
        LongVector cv = LongVector.fromArray(L_SPECIES, lc, 0);
        av.lanewise(VectorOperators.EXPAND_BITS, cv)
          .blend(bv.lanewise(VectorOperators.EXPAND_BITS, cv), mask)
          .intoArray(lr, 0);

        verifyBlendFactor(lr, la, lc, lb, lc, Long::expand);
    }

    // VectorBlend(MinV(a, c), MinV(b, c), m) => MinV(VectorBlend(a, b, m), c)
    @Test
    @IR(counts = {IRNode.MIN_VF, "1", IRNode.VECTOR_BLEND_F, "1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx2", "true", "rvv", "true"})
    public static void testBlendFactorMinFloat() {
        VectorMask<Float> mask = VectorMask.fromArray(F_SPECIES, m, 0);
        FloatVector av = FloatVector.fromArray(F_SPECIES, fa, 0);
        FloatVector bv = FloatVector.fromArray(F_SPECIES, fb, 0);
        FloatVector cv = FloatVector.fromArray(F_SPECIES, fc, 0);
        av.lanewise(VectorOperators.MIN, cv)
          .blend(bv.lanewise(VectorOperators.MIN, cv), mask)
          .intoArray(fr, 0);

        verifyBlendFactor(fr, fa, fc, fb, fc, Math::min);
    }

    // VectorBlend(MaxV(a, c), MaxV(b, c), m) => MaxV(VectorBlend(a, b, m), c)
    @Test
    @IR(counts = {IRNode.MAX_VD, "1", IRNode.VECTOR_BLEND_D, "1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx2", "true", "rvv", "true"})
    public static void testBlendFactorMaxDouble() {
        VectorMask<Double> mask = VectorMask.fromArray(D_SPECIES, m, 0);
        DoubleVector av = DoubleVector.fromArray(D_SPECIES, da, 0);
        DoubleVector bv = DoubleVector.fromArray(D_SPECIES, db, 0);
        DoubleVector cv = DoubleVector.fromArray(D_SPECIES, dc, 0);
        av.lanewise(VectorOperators.MAX, cv)
          .blend(bv.lanewise(VectorOperators.MAX, cv), mask)
          .intoArray(dr, 0);

        verifyBlendFactor(dr, da, dc, db, dc, Math::max);
    }

    // VectorBlend(AddVF(a, c), AddVF(b, c), m) => AddVF(VectorBlend(a, b, m), c)
    @Test
    @IR(counts = {IRNode.ADD_VF, "1", IRNode.VECTOR_BLEND_F, "1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx2", "true", "rvv", "true"})
    public static void testBlendFactorAddFloat() {
        VectorMask<Float> mask = VectorMask.fromArray(F_SPECIES, m, 0);
        FloatVector av = FloatVector.fromArray(F_SPECIES, fa, 0);
        FloatVector bv = FloatVector.fromArray(F_SPECIES, fb, 0);
        FloatVector cv = FloatVector.fromArray(F_SPECIES, fc, 0);
        av.lanewise(VectorOperators.ADD, cv)
          .blend(bv.lanewise(VectorOperators.ADD, cv), mask)
          .intoArray(fr, 0);

        verifyBlendFactor(fr, fa, fc, fb, fc, Float::sum);
    }

    // VectorBlend(SubVD(a, c), SubVD(b, c), m) => SubVD(VectorBlend(a, b, m), c)
    @Test
    @IR(counts = {IRNode.SUB_VD, "1", IRNode.VECTOR_BLEND_D, "1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx2", "true", "rvv", "true"})
    public static void testBlendFactorSubDouble() {
        VectorMask<Double> mask = VectorMask.fromArray(D_SPECIES, m, 0);
        DoubleVector av = DoubleVector.fromArray(D_SPECIES, da, 0);
        DoubleVector bv = DoubleVector.fromArray(D_SPECIES, db, 0);
        DoubleVector cv = DoubleVector.fromArray(D_SPECIES, dc, 0);
        av.lanewise(VectorOperators.SUB, cv)
          .blend(bv.lanewise(VectorOperators.SUB, cv), mask)
          .intoArray(dr, 0);

        verifyBlendFactor(dr, da, dc, db, dc, (a, b) -> a - b);
    }

    // VectorBlend(DivVF(a, c), DivVF(b, c), m) => DivVF(VectorBlend(a, b, m), c)
    @Test
    @IR(counts = {IRNode.DIV_VF, "1", IRNode.VECTOR_BLEND_F, "1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx2", "true", "rvv", "true"})
    public static void testBlendFactorDivFloat() {
        VectorMask<Float> mask = VectorMask.fromArray(F_SPECIES, m, 0);
        FloatVector av = FloatVector.fromArray(F_SPECIES, fa, 0);
        FloatVector bv = FloatVector.fromArray(F_SPECIES, fb, 0);
        FloatVector cv = FloatVector.fromArray(F_SPECIES, fc, 0);
        av.lanewise(VectorOperators.DIV, cv)
          .blend(bv.lanewise(VectorOperators.DIV, cv), mask)
          .intoArray(fr, 0);

        verifyBlendFactor(fr, fa, fc, fb, fc, (a, b) -> a / b);
    }

    // VectorBlend(SaturatingAddV(a, c), SaturatingAddV(b, c), m)
    //   => SaturatingAddV(VectorBlend(a, b, m), c)
    @Test
    @IR(counts = {IRNode.SATURATING_ADD_VI, "1", IRNode.VECTOR_BLEND_I, "1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testBlendFactorSAddInt() {
        VectorMask<Integer> mask = VectorMask.fromArray(I_SPECIES, m, 0);
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector bv = IntVector.fromArray(I_SPECIES, ib, 0);
        IntVector cv = IntVector.fromArray(I_SPECIES, ic, 0);
        av.lanewise(VectorOperators.SADD, cv)
          .blend(bv.lanewise(VectorOperators.SADD, cv), mask)
          .intoArray(ir, 0);

        verifyBlendFactor(ir, ia, ic, ib, ic, VectorMath::addSaturating);
    }

    // VectorBlend(SaturatingSubV(a, c), SaturatingSubV(b, c), m)
    //   => SaturatingSubV(VectorBlend(a, b, m), c)
    @Test
    @IR(counts = {IRNode.SATURATING_SUB_VI, "1", IRNode.VECTOR_BLEND_I, "1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testBlendFactorSSubInt() {
        VectorMask<Integer> mask = VectorMask.fromArray(I_SPECIES, m, 0);
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector bv = IntVector.fromArray(I_SPECIES, ib, 0);
        IntVector cv = IntVector.fromArray(I_SPECIES, ic, 0);
        av.lanewise(VectorOperators.SSUB, cv)
          .blend(bv.lanewise(VectorOperators.SSUB, cv), mask)
          .intoArray(ir, 0);

        verifyBlendFactor(ir, ia, ic, ib, ic, VectorMath::subSaturating);
    }

    // Negative test for the use-count guard on the rule:
    // VectorBlend(OP(a, c), OP(b, c), m) => OP(VectorBlend(a, b, m), c)
    @Test
    @IR(counts = {IRNode.AND_VI, "2", IRNode.VECTOR_BLEND_I, "1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testBlendFactorMultiUsed() {
        VectorMask<Integer> mask = VectorMask.fromArray(I_SPECIES, m, 0);
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector bv = IntVector.fromArray(I_SPECIES, ib, 0);
        IntVector cv = IntVector.fromArray(I_SPECIES, ic, 0);
        IntVector and1 = av.lanewise(VectorOperators.AND, cv);
        IntVector and2 = bv.lanewise(VectorOperators.AND, cv);
        // Force both inner ops to be multi-used.
        and1.intoArray(ir2, 0);
        and2.intoArray(ir3, 0);
        and1.blend(and2, mask).intoArray(ir, 0);

        verifyBlendFactor(ir, ia, ic, ib, ic, (a, b) -> a & b);
    }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(10000)
                     .addFlags("--add-modules=jdk.incubator.vector")
                     .start();
    }
}
