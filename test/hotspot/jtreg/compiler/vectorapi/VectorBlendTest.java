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

/**
 * @test
 * @bug 8384571
 * @key randomness
 * @library /test/lib /
 * @summary Test Ideal/Identity transformations for VectorBlendNode
 * @modules jdk.incubator.vector
 *
 * @run driver ${test.main.class}
 */

public class VectorBlendTest {
    private static final VectorSpecies<Byte> B_SPECIES = ByteVector.SPECIES_MAX;
    private static final VectorSpecies<Short> S_SPECIES = ShortVector.SPECIES_MAX;
    private static final VectorSpecies<Integer> I_SPECIES = IntVector.SPECIES_MAX;
    private static final VectorSpecies<Long> L_SPECIES = LongVector.SPECIES_MAX;
    private static final VectorSpecies<Float> F_SPECIES = FloatVector.SPECIES_MAX;
    private static final VectorSpecies<Double> D_SPECIES = DoubleVector.SPECIES_MAX;

    private static final int LENGTH = 256;
    private static final Generators RD = Generators.G;

    private static byte[] ba;
    private static byte[] bb;
    private static byte[] bc;
    private static byte[] br;
    private static short[] sa;
    private static short[] sb;
    private static short[] sc;
    private static short[] sr;
    private static int[] ia;
    private static int[] ib;
    private static int[] ic;
    private static int[] ir;
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
        ba = new byte[LENGTH];
        bb = new byte[LENGTH];
        bc = new byte[LENGTH];
        br = new byte[LENGTH];
        sa = new short[LENGTH];
        sb = new short[LENGTH];
        sc = new short[LENGTH];
        sr = new short[LENGTH];
        ia = new int[LENGTH];
        ib = new int[LENGTH];
        ic = new int[LENGTH];
        ir = new int[LENGTH];
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
        for (int i = 0; i < LENGTH; i++) {
            ba[i] = iGen.next().byteValue();
            bb[i] = iGen.next().byteValue();
            bc[i] = iGen.next().byteValue();
            sa[i] = iGen.next().shortValue();
            sb[i] = iGen.next().shortValue();
            sc[i] = iGen.next().shortValue();
            m[i] = (i & 1) == 1;
        }
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
    }

    @DontInline
    public static void verifySelect(byte[] result, byte[] trueValues, byte[] falseValues, int len) {
        for (int i = 0; i < len; i++) {
            Asserts.assertEquals(m[i] ? trueValues[i] : falseValues[i], result[i]);
        }
    }

    @DontInline
    public static void verifySelect(short[] result, short[] trueValues, short[] falseValues, int len) {
        for (int i = 0; i < len; i++) {
            Asserts.assertEquals(m[i] ? trueValues[i] : falseValues[i], result[i]);
        }
    }

    @DontInline
    public static void verifySelect(int[] result, int[] trueValues, int[] falseValues, int len) {
        for (int i = 0; i < len; i++) {
            Asserts.assertEquals(m[i] ? trueValues[i] : falseValues[i], result[i]);
        }
    }

    @DontInline
    public static void verifySelect(long[] result, long[] trueValues, long[] falseValues, int len) {
        for (int i = 0; i < len; i++) {
            Asserts.assertEquals(m[i] ? trueValues[i] : falseValues[i], result[i]);
        }
    }

    @DontInline
    public static void verifySelect(float[] result, float[] trueValues, float[] falseValues, int len) {
        for (int i = 0; i < len; i++) {
            Asserts.assertEquals(m[i] ? trueValues[i] : falseValues[i], result[i]);
        }
    }

    @DontInline
    public static void verifySelect(double[] result, double[] trueValues, double[] falseValues, int len) {
        for (int i = 0; i < len; i++) {
            Asserts.assertEquals(m[i] ? trueValues[i] : falseValues[i], result[i]);
        }
    }

    @DontInline
    public static void verifyAll(byte[] result, byte[] expected, int len) {
        for (int i = 0; i < len; i++) {
            Asserts.assertEquals(expected[i], result[i]);
        }
    }

    @DontInline
    public static void verifyAll(short[] result, short[] expected, int len) {
        for (int i = 0; i < len; i++) {
            Asserts.assertEquals(expected[i], result[i]);
        }
    }

    @DontInline
    public static void verifyAll(int[] result, int[] expected, int len) {
        for (int i = 0; i < len; i++) {
            Asserts.assertEquals(expected[i], result[i]);
        }
    }

    @DontInline
    public static void verifyAll(long[] result, long[] expected, int len) {
        for (int i = 0; i < len; i++) {
            Asserts.assertEquals(expected[i], result[i]);
        }
    }

    @DontInline
    public static void verifyAll(float[] result, float[] expected, int len) {
        for (int i = 0; i < len; i++) {
            Asserts.assertEquals(expected[i], result[i]);
        }
    }

    @DontInline
    public static void verifyAll(double[] result, double[] expected, int len) {
        for (int i = 0; i < len; i++) {
            Asserts.assertEquals(expected[i], result[i]);
        }
    }

    // VectorBlend(VectorBlend(a, b, m), c, m) => VectorBlend(a, c, m)

    @Test
    @IR(counts = {IRNode.VECTOR_BLEND_B, "1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testVectorBlendSameMask1Byte() {
        VectorMask<Byte> mask = VectorMask.fromArray(B_SPECIES, m, 0);
        ByteVector av = ByteVector.fromArray(B_SPECIES, ba, 0);
        ByteVector bv = ByteVector.fromArray(B_SPECIES, bb, 0);
        ByteVector cv = ByteVector.fromArray(B_SPECIES, bc, 0);
        av.blend(bv, mask).blend(cv, mask).intoArray(br, 0);

        verifySelect(br, bc, ba, B_SPECIES.length());
    }

    @Test
    @IR(counts = {IRNode.VECTOR_BLEND_S, "1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testVectorBlendSameMask1Short() {
        VectorMask<Short> mask = VectorMask.fromArray(S_SPECIES, m, 0);
        ShortVector av = ShortVector.fromArray(S_SPECIES, sa, 0);
        ShortVector bv = ShortVector.fromArray(S_SPECIES, sb, 0);
        ShortVector cv = ShortVector.fromArray(S_SPECIES, sc, 0);
        av.blend(bv, mask).blend(cv, mask).intoArray(sr, 0);

        verifySelect(sr, sc, sa, S_SPECIES.length());
    }

    @Test
    @IR(counts = {IRNode.VECTOR_BLEND_I, "1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testVectorBlendSameMask1Int() {
        VectorMask<Integer> mask = VectorMask.fromArray(I_SPECIES, m, 0);
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector bv = IntVector.fromArray(I_SPECIES, ib, 0);
        IntVector cv = IntVector.fromArray(I_SPECIES, ic, 0);
        av.blend(bv, mask).blend(cv, mask).intoArray(ir, 0);

        verifySelect(ir, ic, ia, I_SPECIES.length());
    }

    @Test
    @IR(counts = {IRNode.VECTOR_BLEND_L, "1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx2", "true", "rvv", "true"})
    public static void testVectorBlendSameMask1Long() {
        VectorMask<Long> mask = VectorMask.fromArray(L_SPECIES, m, 0);
        LongVector av = LongVector.fromArray(L_SPECIES, la, 0);
        LongVector bv = LongVector.fromArray(L_SPECIES, lb, 0);
        LongVector cv = LongVector.fromArray(L_SPECIES, lc, 0);
        av.blend(bv, mask).blend(cv, mask).intoArray(lr, 0);

        verifySelect(lr, lc, la, L_SPECIES.length());
    }

    @Test
    @IR(counts = {IRNode.VECTOR_BLEND_F, "1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx2", "true", "rvv", "true"})
    public static void testVectorBlendSameMask1Float() {
        VectorMask<Float> mask = VectorMask.fromArray(F_SPECIES, m, 0);
        FloatVector av = FloatVector.fromArray(F_SPECIES, fa, 0);
        FloatVector bv = FloatVector.fromArray(F_SPECIES, fb, 0);
        FloatVector cv = FloatVector.fromArray(F_SPECIES, fc, 0);
        av.blend(bv, mask).blend(cv, mask).intoArray(fr, 0);

        verifySelect(fr, fc, fa, F_SPECIES.length());
    }

    @Test
    @IR(counts = {IRNode.VECTOR_BLEND_D, "1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx2", "true", "rvv", "true"})
    public static void testVectorBlendSameMask1Double() {
        VectorMask<Double> mask = VectorMask.fromArray(D_SPECIES, m, 0);
        DoubleVector av = DoubleVector.fromArray(D_SPECIES, da, 0);
        DoubleVector bv = DoubleVector.fromArray(D_SPECIES, db, 0);
        DoubleVector cv = DoubleVector.fromArray(D_SPECIES, dc, 0);
        av.blend(bv, mask).blend(cv, mask).intoArray(dr, 0);

        verifySelect(dr, dc, da, D_SPECIES.length());
    }

    // VectorBlend(a, VectorBlend(b, c, m), m) => VectorBlend(a, c, m)

    @Test
    @IR(counts = {IRNode.VECTOR_BLEND_B, "1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testVectorBlendSameMask2Byte() {
        VectorMask<Byte> mask = VectorMask.fromArray(B_SPECIES, m, 0);
        ByteVector av = ByteVector.fromArray(B_SPECIES, ba, 0);
        ByteVector bv = ByteVector.fromArray(B_SPECIES, bb, 0);
        ByteVector cv = ByteVector.fromArray(B_SPECIES, bc, 0);
        av.blend(bv.blend(cv, mask), mask).intoArray(br, 0);

        verifySelect(br, bc, ba, B_SPECIES.length());
    }

    @Test
    @IR(counts = {IRNode.VECTOR_BLEND_S, "1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testVectorBlendSameMask2Short() {
        VectorMask<Short> mask = VectorMask.fromArray(S_SPECIES, m, 0);
        ShortVector av = ShortVector.fromArray(S_SPECIES, sa, 0);
        ShortVector bv = ShortVector.fromArray(S_SPECIES, sb, 0);
        ShortVector cv = ShortVector.fromArray(S_SPECIES, sc, 0);
        av.blend(bv.blend(cv, mask), mask).intoArray(sr, 0);

        verifySelect(sr, sc, sa, S_SPECIES.length());
    }

    @Test
    @IR(counts = {IRNode.VECTOR_BLEND_I, "1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testVectorBlendSameMask2Int() {
        VectorMask<Integer> mask = VectorMask.fromArray(I_SPECIES, m, 0);
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector bv = IntVector.fromArray(I_SPECIES, ib, 0);
        IntVector cv = IntVector.fromArray(I_SPECIES, ic, 0);
        av.blend(bv.blend(cv, mask), mask).intoArray(ir, 0);

        verifySelect(ir, ic, ia, I_SPECIES.length());
    }

    @Test
    @IR(counts = {IRNode.VECTOR_BLEND_L, "1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx2", "true", "rvv", "true"})
    public static void testVectorBlendSameMask2Long() {
        VectorMask<Long> mask = VectorMask.fromArray(L_SPECIES, m, 0);
        LongVector av = LongVector.fromArray(L_SPECIES, la, 0);
        LongVector bv = LongVector.fromArray(L_SPECIES, lb, 0);
        LongVector cv = LongVector.fromArray(L_SPECIES, lc, 0);
        av.blend(bv.blend(cv, mask), mask).intoArray(lr, 0);

        verifySelect(lr, lc, la, L_SPECIES.length());
    }

    @Test
    @IR(counts = {IRNode.VECTOR_BLEND_F, "1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx2", "true", "rvv", "true"})
    public static void testVectorBlendSameMask2Float() {
        VectorMask<Float> mask = VectorMask.fromArray(F_SPECIES, m, 0);
        FloatVector av = FloatVector.fromArray(F_SPECIES, fa, 0);
        FloatVector bv = FloatVector.fromArray(F_SPECIES, fb, 0);
        FloatVector cv = FloatVector.fromArray(F_SPECIES, fc, 0);
        av.blend(bv.blend(cv, mask), mask).intoArray(fr, 0);

        verifySelect(fr, fc, fa, F_SPECIES.length());
    }

    @Test
    @IR(counts = {IRNode.VECTOR_BLEND_D, "1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx2", "true", "rvv", "true"})
    public static void testVectorBlendSameMask2Double() {
        VectorMask<Double> mask = VectorMask.fromArray(D_SPECIES, m, 0);
        DoubleVector av = DoubleVector.fromArray(D_SPECIES, da, 0);
        DoubleVector bv = DoubleVector.fromArray(D_SPECIES, db, 0);
        DoubleVector cv = DoubleVector.fromArray(D_SPECIES, dc, 0);
        av.blend(bv.blend(cv, mask), mask).intoArray(dr, 0);

        verifySelect(dr, dc, da, D_SPECIES.length());
    }

    // VectorBlend(a, b, NOT(m)) => VectorBlend(b, a, m)

    @Test
    @IR(failOn = {IRNode.XOR_V_MASK, IRNode.XOR_V},
        counts = {IRNode.VECTOR_BLEND_B, "1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testBlendNegatedMaskByte() {
        VectorMask<Byte> mask = VectorMask.fromArray(B_SPECIES, m, 0);
        ByteVector av = ByteVector.fromArray(B_SPECIES, ba, 0);
        ByteVector bv = ByteVector.fromArray(B_SPECIES, bb, 0);
        av.blend(bv, mask.not()).intoArray(br, 0);

        verifySelect(br, ba, bb, B_SPECIES.length());
    }

    @Test
    @IR(failOn = {IRNode.XOR_V_MASK, IRNode.XOR_V},
        counts = {IRNode.VECTOR_BLEND_S, "1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testBlendNegatedMaskShort() {
        VectorMask<Short> mask = VectorMask.fromArray(S_SPECIES, m, 0);
        ShortVector av = ShortVector.fromArray(S_SPECIES, sa, 0);
        ShortVector bv = ShortVector.fromArray(S_SPECIES, sb, 0);
        av.blend(bv, mask.not()).intoArray(sr, 0);

        verifySelect(sr, sa, sb, S_SPECIES.length());
    }

    @Test
    @IR(failOn = {IRNode.XOR_V_MASK, IRNode.XOR_V},
        counts = {IRNode.VECTOR_BLEND_I, "1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testBlendNegatedMaskInt() {
        VectorMask<Integer> mask = VectorMask.fromArray(I_SPECIES, m, 0);
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector bv = IntVector.fromArray(I_SPECIES, ib, 0);
        av.blend(bv, mask.not()).intoArray(ir, 0);

        verifySelect(ir, ia, ib, I_SPECIES.length());
    }

    @Test
    @IR(failOn = {IRNode.XOR_V_MASK, IRNode.XOR_V},
        counts = {IRNode.VECTOR_BLEND_L, "1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx2", "true", "rvv", "true"})
    public static void testBlendNegatedMaskLong() {
        VectorMask<Long> mask = VectorMask.fromArray(L_SPECIES, m, 0);
        LongVector av = LongVector.fromArray(L_SPECIES, la, 0);
        LongVector bv = LongVector.fromArray(L_SPECIES, lb, 0);
        av.blend(bv, mask.not()).intoArray(lr, 0);

        verifySelect(lr, la, lb, L_SPECIES.length());
    }

    @Test
    @IR(failOn = {IRNode.XOR_V_MASK, IRNode.XOR_V},
        counts = {IRNode.VECTOR_BLEND_F, "1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx2", "true", "rvv", "true"})
    public static void testBlendNegatedMaskFloat() {
        VectorMask<Float> mask = VectorMask.fromArray(F_SPECIES, m, 0);
        FloatVector av = FloatVector.fromArray(F_SPECIES, fa, 0);
        FloatVector bv = FloatVector.fromArray(F_SPECIES, fb, 0);
        av.blend(bv, mask.not()).intoArray(fr, 0);

        verifySelect(fr, fa, fb, F_SPECIES.length());
    }

    @Test
    @IR(failOn = {IRNode.XOR_V_MASK, IRNode.XOR_V},
        counts = {IRNode.VECTOR_BLEND_D, "1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx2", "true", "rvv", "true"})
    public static void testBlendNegatedMaskDouble() {
        VectorMask<Double> mask = VectorMask.fromArray(D_SPECIES, m, 0);
        DoubleVector av = DoubleVector.fromArray(D_SPECIES, da, 0);
        DoubleVector bv = DoubleVector.fromArray(D_SPECIES, db, 0);
        av.blend(bv, mask.not()).intoArray(dr, 0);

        verifySelect(dr, da, db, D_SPECIES.length());
    }

    // VectorBlend(a, b, AllOnesMask) => b

    @Test
    @IR(failOn = {IRNode.VECTOR_BLEND_B},
        counts = {IRNode.STORE_VECTOR, ">=1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testBlendMaskAllTrueByte() {
        VectorMask<Byte> mask = B_SPECIES.maskAll(true);
        ByteVector av = ByteVector.fromArray(B_SPECIES, ba, 0);
        ByteVector bv = ByteVector.fromArray(B_SPECIES, bb, 0);
        av.blend(bv, mask).intoArray(br, 0);

        verifyAll(br, bb, B_SPECIES.length());
    }

    @Test
    @IR(failOn = {IRNode.VECTOR_BLEND_S},
        counts = {IRNode.STORE_VECTOR, ">=1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testBlendMaskAllTrueShort() {
        VectorMask<Short> mask = S_SPECIES.maskAll(true);
        ShortVector av = ShortVector.fromArray(S_SPECIES, sa, 0);
        ShortVector bv = ShortVector.fromArray(S_SPECIES, sb, 0);
        av.blend(bv, mask).intoArray(sr, 0);

        verifyAll(sr, sb, S_SPECIES.length());
    }

    @Test
    @IR(failOn = {IRNode.VECTOR_BLEND_I},
        counts = {IRNode.STORE_VECTOR, ">=1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testBlendMaskAllTrueInt() {
        VectorMask<Integer> mask = I_SPECIES.maskAll(true);
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector bv = IntVector.fromArray(I_SPECIES, ib, 0);
        av.blend(bv, mask).intoArray(ir, 0);

        verifyAll(ir, ib, I_SPECIES.length());
    }

    @Test
    @IR(failOn = {IRNode.VECTOR_BLEND_L},
        counts = {IRNode.STORE_VECTOR, ">=1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx2", "true", "rvv", "true"})
    public static void testBlendMaskAllTrueLong() {
        VectorMask<Long> mask = L_SPECIES.maskAll(true);
        LongVector av = LongVector.fromArray(L_SPECIES, la, 0);
        LongVector bv = LongVector.fromArray(L_SPECIES, lb, 0);
        av.blend(bv, mask).intoArray(lr, 0);

        verifyAll(lr, lb, L_SPECIES.length());
    }

    @Test
    @IR(failOn = {IRNode.VECTOR_BLEND_F},
        counts = {IRNode.STORE_VECTOR, ">=1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testBlendMaskAllTrueFloat() {
        VectorMask<Float> mask = F_SPECIES.maskAll(true);
        FloatVector av = FloatVector.fromArray(F_SPECIES, fa, 0);
        FloatVector bv = FloatVector.fromArray(F_SPECIES, fb, 0);
        av.blend(bv, mask).intoArray(fr, 0);

        verifyAll(fr, fb, F_SPECIES.length());
    }

    @Test
    @IR(failOn = {IRNode.VECTOR_BLEND_D},
        counts = {IRNode.STORE_VECTOR, ">=1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testBlendMaskAllTrueDouble() {
        VectorMask<Double> mask = D_SPECIES.maskAll(true);
        DoubleVector av = DoubleVector.fromArray(D_SPECIES, da, 0);
        DoubleVector bv = DoubleVector.fromArray(D_SPECIES, db, 0);
        av.blend(bv, mask).intoArray(dr, 0);

        verifyAll(dr, db, D_SPECIES.length());
    }

    // VectorBlend(a, b, AllZerosMask) => a

    @Test
    @IR(failOn = {IRNode.VECTOR_BLEND_B},
        counts = {IRNode.STORE_VECTOR, ">=1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testBlendMaskAllFalseByte() {
        VectorMask<Byte> mask = B_SPECIES.maskAll(false);
        ByteVector av = ByteVector.fromArray(B_SPECIES, ba, 0);
        ByteVector bv = ByteVector.fromArray(B_SPECIES, bb, 0);
        av.blend(bv, mask).intoArray(br, 0);

        verifyAll(br, ba, B_SPECIES.length());
    }

    @Test
    @IR(failOn = {IRNode.VECTOR_BLEND_S},
        counts = {IRNode.STORE_VECTOR, ">=1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testBlendMaskAllFalseShort() {
        VectorMask<Short> mask = S_SPECIES.maskAll(false);
        ShortVector av = ShortVector.fromArray(S_SPECIES, sa, 0);
        ShortVector bv = ShortVector.fromArray(S_SPECIES, sb, 0);
        av.blend(bv, mask).intoArray(sr, 0);

        verifyAll(sr, sa, S_SPECIES.length());
    }

    @Test
    @IR(failOn = {IRNode.VECTOR_BLEND_I},
        counts = {IRNode.STORE_VECTOR, ">=1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testBlendMaskAllFalseInt() {
        VectorMask<Integer> mask = I_SPECIES.maskAll(false);
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector bv = IntVector.fromArray(I_SPECIES, ib, 0);
        av.blend(bv, mask).intoArray(ir, 0);

        verifyAll(ir, ia, I_SPECIES.length());
    }

    @Test
    @IR(failOn = {IRNode.VECTOR_BLEND_L},
        counts = {IRNode.STORE_VECTOR, ">=1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx2", "true", "rvv", "true"})
    public static void testBlendMaskAllFalseLong() {
        VectorMask<Long> mask = L_SPECIES.maskAll(false);
        LongVector av = LongVector.fromArray(L_SPECIES, la, 0);
        LongVector bv = LongVector.fromArray(L_SPECIES, lb, 0);
        av.blend(bv, mask).intoArray(lr, 0);

        verifyAll(lr, la, L_SPECIES.length());
    }

    @Test
    @IR(failOn = {IRNode.VECTOR_BLEND_F},
        counts = {IRNode.STORE_VECTOR, ">=1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testBlendMaskAllFalseFloat() {
        VectorMask<Float> mask = F_SPECIES.maskAll(false);
        FloatVector av = FloatVector.fromArray(F_SPECIES, fa, 0);
        FloatVector bv = FloatVector.fromArray(F_SPECIES, fb, 0);
        av.blend(bv, mask).intoArray(fr, 0);

        verifyAll(fr, fa, F_SPECIES.length());
    }

    @Test
    @IR(failOn = {IRNode.VECTOR_BLEND_D},
        counts = {IRNode.STORE_VECTOR, ">=1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testBlendMaskAllFalseDouble() {
        VectorMask<Double> mask = D_SPECIES.maskAll(false);
        DoubleVector av = DoubleVector.fromArray(D_SPECIES, da, 0);
        DoubleVector bv = DoubleVector.fromArray(D_SPECIES, db, 0);
        av.blend(bv, mask).intoArray(dr, 0);

        verifyAll(dr, da, D_SPECIES.length());
    }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(10000)
                     .addFlags("--add-modules=jdk.incubator.vector")
                     .start();
    }
}
