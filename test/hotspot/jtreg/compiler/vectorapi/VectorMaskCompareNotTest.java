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

    interface VectorMaskOperator {
        public VectorMask apply(VectorMask m1);
    }

    @ForceInline
    public static void testCompareMaskNotByte(VectorOperators.Comparison op, VectorMaskOperator func) {
        ByteVector av = ByteVector.fromArray(B_SPECIES, ba, 0);
        ByteVector bv = ByteVector.fromArray(B_SPECIES, bb, 0);
        VectorMask<Byte> m1 = av.compare(op, bv);
        func.apply(m1).intoArray(mr, 0);

        for (int i = 0; i < B_SPECIES.length(); i++) {
            verifyResults(ba[i], bb[i], mr[i], op);
        }
    }

    @ForceInline
    public static void testCompareMaskNotShort(VectorOperators.Comparison op, VectorMaskOperator func) {
        ShortVector av = ShortVector.fromArray(S_SPECIES, sa, 0);
        ShortVector bv = ShortVector.fromArray(S_SPECIES, sb, 0);
        VectorMask<Short> m1 = av.compare(op, bv);
        func.apply(m1).intoArray(mr, 0);

        for (int i = 0; i < S_SPECIES.length(); i++) {
            verifyResults(sa[i], sb[i], mr[i], op);
        }
    }

    @ForceInline
    public static void testCompareMaskNotInt(VectorOperators.Comparison op, VectorMaskOperator func) {
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector bv = IntVector.fromArray(I_SPECIES, ib, 0);
        VectorMask<Integer> m1 = av.compare(op, bv);
        func.apply(m1).intoArray(mr, 0);

        for (int i = 0; i < I_SPECIES.length(); i++) {
            verifyResults(ia[i], ib[i], mr[i], op);
        }
    }

    @ForceInline
    public static void testCompareMaskNotLong(VectorOperators.Comparison op, VectorMaskOperator func) {
        LongVector av = LongVector.fromArray(L_SPECIES, la, 0);
        LongVector bv = LongVector.fromArray(L_SPECIES, lb, 0);
        VectorMask<Long> m1 = av.compare(op, bv);
        func.apply(m1).intoArray(mr, 0);

        for (int i = 0; i < L_SPECIES.length(); i++) {
            verifyResults(la[i], lb[i], mr[i], op);
        }
    }

    @ForceInline
    public static void testCompareMaskNotFloat(VectorOperators.Comparison op, float[] a, float[] b, VectorMaskOperator func) {
        FloatVector av = FloatVector.fromArray(F_SPECIES, a, 0);
        FloatVector bv = FloatVector.fromArray(F_SPECIES, b, 0);
        VectorMask<Float> m1 = av.compare(op, bv);
        func.apply(m1).intoArray(mr, 0);

        for (int i = 0; i < F_SPECIES.length(); i++) {
            verifyResults(a[i], b[i], mr[i], op);
        }
    }

    @ForceInline
    public static void testCompareMaskNotDouble(VectorOperators.Comparison op, double[] a, double[] b, VectorMaskOperator func) {
        DoubleVector av = DoubleVector.fromArray(D_SPECIES, a, 0);
        DoubleVector bv = DoubleVector.fromArray(D_SPECIES, b, 0);
        VectorMask<Double> m1 = av.compare(op, bv);
        func.apply(m1).intoArray(mr, 0);

        for (int i = 0; i < D_SPECIES.length(); i++) {
            verifyResults(a[i], b[i], mr[i], op);
        }
    }

    // Byte tests
    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VB, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareEQMaskNotByte() {
        testCompareMaskNotByte(VectorOperators.EQ, m -> m.not());
        testCompareMaskNotByte(VectorOperators.EQ, m -> m.xor(B_SPECIES.maskAll(true)));
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VB, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareNEMaskNotByte() {
        testCompareMaskNotByte(VectorOperators.NE, m -> m.not());
        testCompareMaskNotByte(VectorOperators.NE, m -> m.xor(B_SPECIES.maskAll(true)));
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VB, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareLTMaskNotByte() {
        testCompareMaskNotByte(VectorOperators.LT, m -> m.not());
        testCompareMaskNotByte(VectorOperators.LT, m -> m.xor(B_SPECIES.maskAll(true)));
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VB, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareGTMaskNotByte() {
        testCompareMaskNotByte(VectorOperators.GT, m -> m.not());
        testCompareMaskNotByte(VectorOperators.GT, m -> m.xor(B_SPECIES.maskAll(true)));
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VB, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareLEMaskNotByte() {
        testCompareMaskNotByte(VectorOperators.LE, m -> m.not());
        testCompareMaskNotByte(VectorOperators.LE, m -> m.xor(B_SPECIES.maskAll(true)));
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VB, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareGEMaskNotByte() {
        testCompareMaskNotByte(VectorOperators.GE, m -> m.not());
        testCompareMaskNotByte(VectorOperators.GE, m -> m.xor(B_SPECIES.maskAll(true)));
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VB, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareULTMaskNotByte() {
        testCompareMaskNotByte(VectorOperators.ULT, m -> m.not());
        testCompareMaskNotByte(VectorOperators.ULT, m -> m.xor(B_SPECIES.maskAll(true)));
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VB, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareUGTMaskNotByte() {
        testCompareMaskNotByte(VectorOperators.UGT, m -> m.not());
        testCompareMaskNotByte(VectorOperators.UGT, m -> m.xor(B_SPECIES.maskAll(true)));
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VB, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareULEMaskNotByte() {
        testCompareMaskNotByte(VectorOperators.ULE, m -> m.not());
        testCompareMaskNotByte(VectorOperators.ULE, m -> m.xor(B_SPECIES.maskAll(true)));
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VB, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareUGEMaskNotByte() {
        testCompareMaskNotByte(VectorOperators.UGE, m -> m.not());
        testCompareMaskNotByte(VectorOperators.UGE, m -> m.xor(B_SPECIES.maskAll(true)));
    }

    // Short tests
    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VS, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareEQMaskNotShort() {
        testCompareMaskNotShort(VectorOperators.EQ, m -> m.not());
        testCompareMaskNotShort(VectorOperators.EQ, m -> m.xor(S_SPECIES.maskAll(true)));
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VS, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareNEMaskNotShort() {
        testCompareMaskNotShort(VectorOperators.NE, m -> m.not());
        testCompareMaskNotShort(VectorOperators.NE, m -> m.xor(S_SPECIES.maskAll(true)));
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VS, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareLTMaskNotShort() {
        testCompareMaskNotShort(VectorOperators.LT, m -> m.not());
        testCompareMaskNotShort(VectorOperators.LT, m -> m.xor(S_SPECIES.maskAll(true)));
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VS, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareGTMaskNotShort() {
        testCompareMaskNotShort(VectorOperators.GT, m -> m.not());
        testCompareMaskNotShort(VectorOperators.GT, m -> m.xor(S_SPECIES.maskAll(true)));
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VS, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareLEMaskNotShort() {
        testCompareMaskNotShort(VectorOperators.LE, m -> m.not());
        testCompareMaskNotShort(VectorOperators.LE, m -> m.xor(S_SPECIES.maskAll(true)));
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VS, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareGEMaskNotShort() {
        testCompareMaskNotShort(VectorOperators.GE, m -> m.not());
        testCompareMaskNotShort(VectorOperators.GE, m -> m.xor(S_SPECIES.maskAll(true)));
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VS, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareULTMaskNotShort() {
        testCompareMaskNotShort(VectorOperators.ULT, m -> m.not());
        testCompareMaskNotShort(VectorOperators.ULT, m -> m.xor(S_SPECIES.maskAll(true)));
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VS, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareUGTMaskNotShort() {
        testCompareMaskNotShort(VectorOperators.UGT, m -> m.not());
        testCompareMaskNotShort(VectorOperators.UGT, m -> m.xor(S_SPECIES.maskAll(true)));
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VS, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareULEMaskNotShort() {
        testCompareMaskNotShort(VectorOperators.ULE, m -> m.not());
        testCompareMaskNotShort(VectorOperators.ULE, m -> m.xor(S_SPECIES.maskAll(true)));
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VS, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareUGEMaskNotShort() {
        testCompareMaskNotShort(VectorOperators.UGE, m -> m.not());
        testCompareMaskNotShort(VectorOperators.UGE, m -> m.xor(S_SPECIES.maskAll(true)));
    }

    // Int tests
    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VI, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareEQMaskNotInt() {
        testCompareMaskNotInt(VectorOperators.EQ, m -> m.not());
        testCompareMaskNotInt(VectorOperators.EQ, m -> m.xor(I_SPECIES.maskAll(true)));
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VI, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareNEMaskNotInt() {
        testCompareMaskNotInt(VectorOperators.NE, m -> m.not());
        testCompareMaskNotInt(VectorOperators.NE, m -> m.xor(I_SPECIES.maskAll(true)));
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VI, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareLTMaskNotInt() {
        testCompareMaskNotInt(VectorOperators.LT, m -> m.not());
        testCompareMaskNotInt(VectorOperators.LT, m -> m.xor(I_SPECIES.maskAll(true)));
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VI, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareGTMaskNotInt() {
        testCompareMaskNotInt(VectorOperators.GT, m -> m.not());
        testCompareMaskNotInt(VectorOperators.GT, m -> m.xor(I_SPECIES.maskAll(true)));
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VI, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareLEMaskNotInt() {
        testCompareMaskNotInt(VectorOperators.LE, m -> m.not());
        testCompareMaskNotInt(VectorOperators.LE, m -> m.xor(I_SPECIES.maskAll(true)));
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VI, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareGEMaskNotInt() {
        testCompareMaskNotInt(VectorOperators.GE, m -> m.not());
        testCompareMaskNotInt(VectorOperators.GE, m -> m.xor(I_SPECIES.maskAll(true)));
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VI, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareULTMaskNotInt() {
        testCompareMaskNotInt(VectorOperators.ULT, m -> m.not());
        testCompareMaskNotInt(VectorOperators.ULT, m -> m.xor(I_SPECIES.maskAll(true)));
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VI, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareUGTMaskNotInt() {
        testCompareMaskNotInt(VectorOperators.UGT, m -> m.not());
        testCompareMaskNotInt(VectorOperators.UGT, m -> m.xor(I_SPECIES.maskAll(true)));
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VI, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareULEMaskNotInt() {
        testCompareMaskNotInt(VectorOperators.ULE, m -> m.not());
        testCompareMaskNotInt(VectorOperators.ULE, m -> m.xor(I_SPECIES.maskAll(true)));
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VI, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareUGEMaskNotInt() {
        testCompareMaskNotInt(VectorOperators.UGE, m -> m.not());
        testCompareMaskNotInt(VectorOperators.UGE, m -> m.xor(I_SPECIES.maskAll(true)));
    }

    // Long tests
    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VL, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareEQMaskNotLong() {
        testCompareMaskNotLong(VectorOperators.EQ, m -> m.not());
        testCompareMaskNotLong(VectorOperators.EQ, m -> m.xor(L_SPECIES.maskAll(true)));
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VL, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareNEMaskNotLong() {
        testCompareMaskNotLong(VectorOperators.NE, m -> m.not());
        testCompareMaskNotLong(VectorOperators.NE, m -> m.xor(L_SPECIES.maskAll(true)));
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VL, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareLTMaskNotLong() {
        testCompareMaskNotLong(VectorOperators.LT, m -> m.not());
        testCompareMaskNotLong(VectorOperators.LT, m -> m.xor(L_SPECIES.maskAll(true)));
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VL, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareGTMaskNotLong() {
        testCompareMaskNotLong(VectorOperators.GT, m -> m.not());
        testCompareMaskNotLong(VectorOperators.GT, m -> m.xor(L_SPECIES.maskAll(true)));
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VL, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareLEMaskNotLong() {
        testCompareMaskNotLong(VectorOperators.LE, m -> m.not());
        testCompareMaskNotLong(VectorOperators.LE, m -> m.xor(L_SPECIES.maskAll(true)));
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VL, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareGEMaskNotLong() {
        testCompareMaskNotLong(VectorOperators.GE, m -> m.not());
        testCompareMaskNotLong(VectorOperators.GE, m -> m.xor(L_SPECIES.maskAll(true)));
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VL, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareULTMaskNotLong() {
        testCompareMaskNotLong(VectorOperators.ULT, m -> m.not());
        testCompareMaskNotLong(VectorOperators.ULT, m -> m.xor(L_SPECIES.maskAll(true)));
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VL, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareUGTMaskNotLong() {
        testCompareMaskNotLong(VectorOperators.UGT, m -> m.not());
        testCompareMaskNotLong(VectorOperators.UGT, m -> m.xor(L_SPECIES.maskAll(true)));
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VL, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareULEMaskNotLong() {
        testCompareMaskNotLong(VectorOperators.ULE, m -> m.not());
        testCompareMaskNotLong(VectorOperators.ULE, m -> m.xor(L_SPECIES.maskAll(true)));
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VL, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareUGEMaskNotLong() {
        testCompareMaskNotLong(VectorOperators.UGE, m -> m.not());
        testCompareMaskNotLong(VectorOperators.UGE, m -> m.xor(L_SPECIES.maskAll(true)));
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VI, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareEQMaskNotFloat() {
        testCompareMaskNotFloat(VectorOperators.EQ, fa, fb, m -> m.not());
        testCompareMaskNotFloat(VectorOperators.EQ, fa, fb, m -> m.xor(F_SPECIES.maskAll(true)));
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VI, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareNEMaskNotFloat() {
        testCompareMaskNotFloat(VectorOperators.NE, fa, fb, m -> m.not());
        testCompareMaskNotFloat(VectorOperators.NE, fa, fb, m -> m.xor(F_SPECIES.maskAll(true)));
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VL, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareEQMaskNotFloatNaN() {
        testCompareMaskNotFloat(VectorOperators.EQ, fa, fnan, m -> m.not());
        testCompareMaskNotFloat(VectorOperators.EQ, fa, fnan, m -> m.xor(F_SPECIES.maskAll(true)));
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VL, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareNEMaskNotFloatNaN() {
        testCompareMaskNotFloat(VectorOperators.NE, fa, fnan, m -> m.not());
        testCompareMaskNotFloat(VectorOperators.NE, fa, fnan, m -> m.xor(F_SPECIES.maskAll(true)));
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VL, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareEQMaskNotFloatPositiveInfinity() {
        testCompareMaskNotFloat(VectorOperators.EQ, fa, fpinf, m -> m.not());
        testCompareMaskNotFloat(VectorOperators.EQ, fa, fpinf, m -> m.xor(F_SPECIES.maskAll(true)));
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VL, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareNEMaskNotFloatPositiveInfinity() {
        testCompareMaskNotFloat(VectorOperators.NE, fa, fpinf, m -> m.not());
        testCompareMaskNotFloat(VectorOperators.NE, fa, fpinf, m -> m.xor(F_SPECIES.maskAll(true)));
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VL, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareEQMaskNotFloatNegativeInfinity() {
        testCompareMaskNotFloat(VectorOperators.EQ, fa, fninf, m -> m.not());
        testCompareMaskNotFloat(VectorOperators.EQ, fa, fninf, m -> m.xor(F_SPECIES.maskAll(true)));
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VL, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareNEMaskNotFloatNegativeInfinity() {
        testCompareMaskNotFloat(VectorOperators.NE, fa, fninf, m -> m.not());
        testCompareMaskNotFloat(VectorOperators.NE, fa, fninf, m -> m.xor(F_SPECIES.maskAll(true)));
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VL, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareEQMaskNotDouble() {
        testCompareMaskNotDouble(VectorOperators.EQ, da, db, m -> m.not());
        testCompareMaskNotDouble(VectorOperators.EQ, da, db, m -> m.xor(D_SPECIES.maskAll(true)));
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VL, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareNEMaskNotDouble() {
        testCompareMaskNotDouble(VectorOperators.NE, da, db, m -> m.not());
        testCompareMaskNotDouble(VectorOperators.NE, da, db, m -> m.xor(D_SPECIES.maskAll(true)));
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VL, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareEQMaskNotDoubleNaN() {
        testCompareMaskNotDouble(VectorOperators.EQ, da, dnan, m -> m.not());
        testCompareMaskNotDouble(VectorOperators.EQ, da, dnan, m -> m.xor(D_SPECIES.maskAll(true)));
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VL, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareNEMaskNotDoubleNaN() {
        testCompareMaskNotDouble(VectorOperators.NE, da, dnan, m -> m.not());
        testCompareMaskNotDouble(VectorOperators.NE, da, dnan, m -> m.xor(D_SPECIES.maskAll(true)));
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VL, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareEQMaskNotDoublePositiveInfinity() {
        testCompareMaskNotDouble(VectorOperators.EQ, da, dpinf, m -> m.not());
        testCompareMaskNotDouble(VectorOperators.EQ, da, dpinf, m -> m.xor(D_SPECIES.maskAll(true)));
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VL, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareNEMaskNotDoublePositiveInfinity() {
        testCompareMaskNotDouble(VectorOperators.NE, da, dpinf, m -> m.not());
        testCompareMaskNotDouble(VectorOperators.NE, da, dpinf, m -> m.xor(D_SPECIES.maskAll(true)));
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VL, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareEQMaskNotDoubleNegativeInfinity() {
        testCompareMaskNotDouble(VectorOperators.EQ, da, dninf, m -> m.not());
        testCompareMaskNotDouble(VectorOperators.EQ, da, dninf, m -> m.xor(D_SPECIES.maskAll(true)));
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0", IRNode.XOR_VL, "= 0" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testCompareNEMaskNotDoubleNegativeInfinity() {
        testCompareMaskNotDouble(VectorOperators.NE, da, dninf, m -> m.not());
        testCompareMaskNotDouble(VectorOperators.NE, da, dninf, m -> m.xor(D_SPECIES.maskAll(true)));
    }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.addFlags("--add-modules=jdk.incubator.vector");
        testFramework.setDefaultWarmup(10000);
        testFramework.start();
    }
}
