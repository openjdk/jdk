/*
 * Copyright (c) 2025, NVIDIA CORPORATION & AFFILIATES. All rights reserved.
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

/*
 * @test
 * @bug 8354242
 * @key randomness
 * @library /test/lib /
 * @summary test combining vector not operation with compare
 * @modules jdk.incubator.vector
 *
 * @run driver compiler.vectorapi.VectorMaskCompareNotTest
 */

public class VectorMaskCompareNotTest {
    private static int LENGTH = 128;
    private static final VectorSpecies<Byte> B_SPECIES = ByteVector.SPECIES_MAX;
    private static final VectorSpecies<Short> S_SPECIES = ShortVector.SPECIES_MAX;
    private static final VectorSpecies<Integer> I_SPECIES = IntVector.SPECIES_MAX;
    private static final VectorSpecies<Long> L_SPECIES = LongVector.SPECIES_MAX;
    private static final VectorSpecies<Float> F_SPECIES = FloatVector.SPECIES_MAX;
    private static final VectorSpecies<Double> D_SPECIES = DoubleVector.SPECIES_MAX;
    private static final Generators RD = Generators.G;

    private static byte[] ba;
    private static byte[] bb;
    private static short[] sa;
    private static short[] sb;
    private static int[] ia;
    private static int[] ib;
    private static long[] la;
    private static long[] lb;
    private static float[] fa;
    private static float[] fb;
    private static float[] fnan;
    private static float[] fpinf;
    private static float[] fninf;
    private static double[] da;
    private static double[] db;
    private static double[] dnan;
    private static double[] dpinf;
    private static double[] dninf;
    private static boolean[] mr;

    static {
        ba = new byte[LENGTH];
        bb = new byte[LENGTH];
        sa = new short[LENGTH];
        sb = new short[LENGTH];
        ia = new int[LENGTH];
        ib = new int[LENGTH];
        la = new long[LENGTH];
        lb = new long[LENGTH];
        fa = new float[LENGTH];
        fb = new float[LENGTH];
        fnan = new float[LENGTH];
        fpinf = new float[LENGTH];
        fninf = new float[LENGTH];
        da = new double[LENGTH];
        db = new double[LENGTH];
        dnan = new double[LENGTH];
        dpinf = new double[LENGTH];
        dninf = new double[LENGTH];
        mr = new boolean[LENGTH];

        Generator<Integer> iGen = RD.uniformInts(Integer.MIN_VALUE, Integer.MAX_VALUE);
        Generator<Long> lGen = RD.uniformLongs(Long.MIN_VALUE, Long.MAX_VALUE);
        Generator<Float> fGen = RD.uniformFloats(Float.MIN_VALUE, Float.MAX_VALUE);
        Generator<Double> dGen = RD.uniformDoubles(Double.MIN_VALUE, Double.MAX_VALUE);
        for (int i = 0; i < LENGTH; i++) {
            ba[i] = iGen.next().byteValue();
            bb[i] = iGen.next().byteValue();
            sa[i] = iGen.next().shortValue();
            sb[i] = iGen.next().shortValue();
            ia[i] = iGen.next();
            ib[i] = iGen.next();
            la[i] = lGen.next();
            lb[i] = lGen.next();
            fa[i] = fGen.next();
            fb[i] = fGen.next();
            fnan[i] = Float.NaN;
            fpinf[i] = Float.POSITIVE_INFINITY;
            fninf[i] = Float.NEGATIVE_INFINITY;
            da[i] = dGen.next();
            db[i] = dGen.next();
            dnan[i] = Double.NaN;
            dpinf[i] = Double.POSITIVE_INFINITY;
            dninf[i] = Double.NEGATIVE_INFINITY;
        }
    }

    public static int compareUnsigned(Number a, Number b) {
        if (a instanceof Byte) {
            return Integer.compareUnsigned(Byte.toUnsignedInt(a.byteValue()),
                    Byte.toUnsignedInt(b.byteValue()));
        } else if (a instanceof Short) {
            return Integer.compareUnsigned(Short.toUnsignedInt(a.shortValue()),
                    Short.toUnsignedInt(b.shortValue()));
        } else if (a instanceof Integer) {
            return Integer.compareUnsigned(a.intValue(), b.intValue());
        } else if (a instanceof Long) {
            return Long.compareUnsigned(a.longValue(), b.longValue());
        }
        return 0;
    }

    public static <T extends Number & Comparable<T>> void verifyResults(T a, T b, boolean r,
            VectorOperators.Comparison op) {
        if (op == VectorOperators.EQ) {
            Asserts.assertEquals(a.compareTo(b) != 0, r);
        } else if (op == VectorOperators.NE) {
            Asserts.assertEquals(a.compareTo(b) == 0, r);
        } else if (op == VectorOperators.LE) {
            Asserts.assertEquals(a.compareTo(b) > 0, r);
        } else if (op == VectorOperators.GE) {
            Asserts.assertEquals(a.compareTo(b) < 0, r);
        } else if (op == VectorOperators.LT) {
            Asserts.assertEquals(a.compareTo(b) >= 0, r);
        } else if (op == VectorOperators.GT) {
            Asserts.assertEquals(a.compareTo(b) <= 0, r);
        } else if (op == VectorOperators.ULE) {
            Asserts.assertEquals(compareUnsigned(a, b) > 0, r);
        } else if (op == VectorOperators.UGE) {
            Asserts.assertEquals(compareUnsigned(a, b) < 0, r);
        } else if (op == VectorOperators.ULT) {
            Asserts.assertEquals(compareUnsigned(a, b) >= 0, r);
        } else if (op == VectorOperators.UGT) {
            Asserts.assertEquals(compareUnsigned(a, b) <= 0, r);
        }
    }

    @ForceInline
    public static void testCompareMaskNotByte(VectorOperators.Comparison op) {
        ByteVector av = ByteVector.fromArray(B_SPECIES, ba, 0);
        ByteVector bv = ByteVector.fromArray(B_SPECIES, bb, 0);
        VectorMask<Byte> m1 = av.compare(op, bv);
        m1.not().intoArray(mr, 0);

        for (int i = 0; i < B_SPECIES.length(); i++) {
            verifyResults(ba[i], bb[i], mr[i], op);
        }
    }

    @ForceInline
    public static void testCompareMaskNotShort(VectorOperators.Comparison op) {
        ShortVector av = ShortVector.fromArray(S_SPECIES, sa, 0);
        ShortVector bv = ShortVector.fromArray(S_SPECIES, sb, 0);
        VectorMask<Short> m1 = av.compare(op, bv);
        m1.not().intoArray(mr, 0);

        for (int i = 0; i < S_SPECIES.length(); i++) {
            verifyResults(sa[i], sb[i], mr[i], op);
        }
    }

    @ForceInline
    public static void testCompareMaskNotInt(VectorOperators.Comparison op) {
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector bv = IntVector.fromArray(I_SPECIES, ib, 0);
        VectorMask<Integer> m1 = av.compare(op, bv);
        m1.not().intoArray(mr, 0);

        for (int i = 0; i < I_SPECIES.length(); i++) {
            verifyResults(ia[i], ib[i], mr[i], op);
        }
    }

    @ForceInline
    public static void testCompareMaskNotLong(VectorOperators.Comparison op) {
        LongVector av = LongVector.fromArray(L_SPECIES, la, 0);
        LongVector bv = LongVector.fromArray(L_SPECIES, lb, 0);
        VectorMask<Long> m1 = av.compare(op, bv);
        m1.not().intoArray(mr, 0);

        for (int i = 0; i < L_SPECIES.length(); i++) {
            verifyResults(la[i], lb[i], mr[i], op);
        }
    }

    @ForceInline
    public static void testCompareMaskNotFloat(VectorOperators.Comparison op, float[] a, float[] b) {
        FloatVector av = FloatVector.fromArray(F_SPECIES, a, 0);
        FloatVector bv = FloatVector.fromArray(F_SPECIES, b, 0);
        VectorMask<Float> m1 = av.compare(op, bv);
        m1.not().intoArray(mr, 0);

        for (int i = 0; i < F_SPECIES.length(); i++) {
            verifyResults(a[i], b[i], mr[i], op);
        }
    }

    @ForceInline
    public static void testCompareMaskNotDouble(VectorOperators.Comparison op, double[] a, double[] b) {
        DoubleVector av = DoubleVector.fromArray(D_SPECIES, a, 0);
        DoubleVector bv = DoubleVector.fromArray(D_SPECIES, b, 0);
        VectorMask<Double> m1 = av.compare(op, bv);
        m1.not().intoArray(mr, 0);

        for (int i = 0; i < D_SPECIES.length(); i++) {
            verifyResults(a[i], b[i], mr[i], op);
        }
    }

    // Byte tests
    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VB, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareEQMaskNotByte() {
        testCompareMaskNotByte(VectorOperators.EQ);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VB, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareNEMaskNotByte() {
        testCompareMaskNotByte(VectorOperators.NE);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VB, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareLTMaskNotByte() {
        testCompareMaskNotByte(VectorOperators.LT);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VB, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareGTMaskNotByte() {
        testCompareMaskNotByte(VectorOperators.GT);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VB, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareLEMaskNotByte() {
        testCompareMaskNotByte(VectorOperators.LE);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VB, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareGEMaskNotByte() {
        testCompareMaskNotByte(VectorOperators.GE);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VB, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareULTMaskNotByte() {
        testCompareMaskNotByte(VectorOperators.ULT);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VB, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareUGTMaskNotByte() {
        testCompareMaskNotByte(VectorOperators.UGT);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VB, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareULEMaskNotByte() {
        testCompareMaskNotByte(VectorOperators.ULE);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VB, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareUGEMaskNotByte() {
        testCompareMaskNotByte(VectorOperators.UGE);
    }

    // Short tests
    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VS, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareEQMaskNotShort() {
        testCompareMaskNotShort(VectorOperators.EQ);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VS, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareNEMaskNotShort() {
        testCompareMaskNotShort(VectorOperators.NE);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VS, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareLTMaskNotShort() {
        testCompareMaskNotShort(VectorOperators.LT);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VS, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareGTMaskNotShort() {
        testCompareMaskNotShort(VectorOperators.GT);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VS, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareLEMaskNotShort() {
        testCompareMaskNotShort(VectorOperators.LE);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VS, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareGEMaskNotShort() {
        testCompareMaskNotShort(VectorOperators.GE);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VS, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareULTMaskNotShort() {
        testCompareMaskNotShort(VectorOperators.ULT);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VS, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareUGTMaskNotShort() {
        testCompareMaskNotShort(VectorOperators.UGT);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VS, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareULEMaskNotShort() {
        testCompareMaskNotShort(VectorOperators.ULE);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VS, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareUGEMaskNotShort() {
        testCompareMaskNotShort(VectorOperators.UGE);
    }

    // Int tests
    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VI, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareEQMaskNotInt() {
        testCompareMaskNotInt(VectorOperators.EQ);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VI, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareNEMaskNotInt() {
        testCompareMaskNotInt(VectorOperators.NE);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VI, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareLTMaskNotInt() {
        testCompareMaskNotInt(VectorOperators.LT);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VI, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareGTMaskNotInt() {
        testCompareMaskNotInt(VectorOperators.GT);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VI, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareLEMaskNotInt() {
        testCompareMaskNotInt(VectorOperators.LE);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VI, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareGEMaskNotInt() {
        testCompareMaskNotInt(VectorOperators.GE);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VI, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareULTMaskNotInt() {
        testCompareMaskNotInt(VectorOperators.ULT);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VI, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareUGTMaskNotInt() {
        testCompareMaskNotInt(VectorOperators.UGT);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VI, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareULEMaskNotInt() {
        testCompareMaskNotInt(VectorOperators.ULE);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VI, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareUGEMaskNotInt() {
        testCompareMaskNotInt(VectorOperators.UGE);
    }

    // Long tests
    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VL, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareEQMaskNotLong() {
        testCompareMaskNotLong(VectorOperators.EQ);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VL, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareNEMaskNotLong() {
        testCompareMaskNotLong(VectorOperators.NE);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VL, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareLTMaskNotLong() {
        testCompareMaskNotLong(VectorOperators.LT);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VL, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareGTMaskNotLong() {
        testCompareMaskNotLong(VectorOperators.GT);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VL, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareLEMaskNotLong() {
        testCompareMaskNotLong(VectorOperators.LE);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VL, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareGEMaskNotLong() {
        testCompareMaskNotLong(VectorOperators.GE);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VL, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareULTMaskNotLong() {
        testCompareMaskNotLong(VectorOperators.ULT);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VL, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareUGTMaskNotLong() {
        testCompareMaskNotLong(VectorOperators.UGT);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VL, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareULEMaskNotLong() {
        testCompareMaskNotLong(VectorOperators.ULE);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VL, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareUGEMaskNotLong() {
        testCompareMaskNotLong(VectorOperators.UGE);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VI, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareEQMaskNotFloat() {
        testCompareMaskNotFloat(VectorOperators.EQ, fa, fb);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VI, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareNEMaskNotFloat() {
        testCompareMaskNotFloat(VectorOperators.NE, fa, fb);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VL, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareEQMaskNotFloatNaN() {
        testCompareMaskNotFloat(VectorOperators.EQ, fa, fnan);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VL, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareNEMaskNotFloatNaN() {
        testCompareMaskNotFloat(VectorOperators.NE, fa, fnan);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VL, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareEQMaskNotFloatPositiveInfinity() {
        testCompareMaskNotFloat(VectorOperators.EQ, fa, fpinf);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VL, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareNEMaskNotFloatPositiveInfinity() {
        testCompareMaskNotFloat(VectorOperators.NE, fa, fpinf);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VL, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareEQMaskNotFloatNegativeInfinity() {
        testCompareMaskNotFloat(VectorOperators.EQ, fa, fninf);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VL, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareNEMaskNotFloatNegativeInfinity() {
        testCompareMaskNotFloat(VectorOperators.NE, fa, fninf);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VL, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareEQMaskNotDouble() {
        testCompareMaskNotDouble(VectorOperators.EQ, da, db);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VL, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareNEMaskNotDouble() {
        testCompareMaskNotDouble(VectorOperators.NE, da, db);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VL, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareEQMaskNotDoubleNaN() {
        testCompareMaskNotDouble(VectorOperators.EQ, da, dnan);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VL, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareNEMaskNotDoubleNaN() {
        testCompareMaskNotDouble(VectorOperators.NE, da, dnan);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VL, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareEQMaskNotDoublePositiveInfinity() {
        testCompareMaskNotDouble(VectorOperators.EQ, da, dpinf);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VL, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareNEMaskNotDoublePositiveInfinity() {
        testCompareMaskNotDouble(VectorOperators.NE, da, dpinf);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VL, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareEQMaskNotDoubleNegativeInfinity() {
        testCompareMaskNotDouble(VectorOperators.EQ, da, dninf);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VL, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareNEMaskNotDoubleNegativeInfinity() {
        testCompareMaskNotDouble(VectorOperators.NE, da, dninf);
    }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.addFlags("--add-modules=jdk.incubator.vector");
        testFramework.setDefaultWarmup(10000);
        testFramework.start();
    }
}
