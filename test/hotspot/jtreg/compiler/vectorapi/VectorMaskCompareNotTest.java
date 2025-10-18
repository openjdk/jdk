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

import compiler.lib.generators.*;
import compiler.lib.ir_framework.*;
import jdk.incubator.vector.*;
import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8354242
 * @key randomness
 * @library /test/lib /
 * @summary test combining vector not operation with compare
 * @modules jdk.incubator.vector
 * @requires vm.opt.final.MaxVectorSize == "null" | vm.opt.final.MaxVectorSize >= 16
 *
 * @run driver compiler.vectorapi.VectorMaskCompareNotTest
 */

public class VectorMaskCompareNotTest {
    private static int LENGTH = 128;

    private static final VectorSpecies<Byte> B_SPECIES = VectorSpecies.ofLargestShape(byte.class);
    private static final VectorSpecies<Short> S_SPECIES = VectorSpecies.ofLargestShape(short.class);
    private static final VectorSpecies<Integer> I_SPECIES = VectorSpecies.ofLargestShape(int.class);
    private static final VectorSpecies<Long> L_SPECIES = VectorSpecies.ofLargestShape(long.class);
    private static final VectorSpecies<Float> F_SPECIES = VectorSpecies.ofLargestShape(float.class);
    private static final VectorSpecies<Double> D_SPECIES = VectorSpecies.ofLargestShape(double.class);

    // Vector species for vector mask cast operation between int and long types,
    // they must have the same number of elements.
    // For other types, use a vector species of the specified width.
    private static final VectorSpecies<Long> L_SPECIES_FOR_CAST = VectorSpecies.ofLargestShape(long.class);
    private static final VectorSpecies<Integer> I_SPECIES_FOR_CAST = VectorSpecies.of(int.class, VectorShape.forBitSize(L_SPECIES_FOR_CAST.vectorBitSize() / 2));

    private static final Generators RD = Generators.G;

    private static byte[] ba;
    private static byte[] bb;
    private static short[] sa;
    private static short[] sb;
    private static int[] ia;
    private static int[] ib;
    private static int[] ic;
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
        ic = new int[LENGTH];
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

        Generator<Integer> iGen = RD.ints();
        Generator<Long> lGen = RD.longs();
        // Use uniform generators for floating point numbers not to generate NaN values.
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
            return Integer.compareUnsigned(Byte.toUnsignedInt(a.byteValue()), Byte.toUnsignedInt(b.byteValue()));
        } else if (a instanceof Short) {
            return Integer.compareUnsigned(Short.toUnsignedInt(a.shortValue()), Short.toUnsignedInt(b.shortValue()));
        } else if (a instanceof Integer) {
            return Integer.compareUnsigned(a.intValue(), b.intValue());
        } else if (a instanceof Long) {
            return Long.compareUnsigned(a.longValue(), b.longValue());
        } else {
            throw new IllegalArgumentException("Unsupported type for unsigned comparison: " + a.getClass() + ", " + b.getClass());
        }
    }

    public static <T extends Number & Comparable<T>> void compareResults(T a, T b, boolean r, VectorOperators.Comparison op) {
        if (op == VectorOperators.EQ) {
            // For floating point numbers, a is not NaN, b may be NaN. If b is NaN,
            // a.compareTo(b) will return 1, 1 != 0 is true, r is expected to be true.
            Asserts.assertEquals(a.compareTo(b) != 0, r);
        } else if (op == VectorOperators.NE) {
            // For floating point numbers, a is not NaN, b may be NaN. If b is NaN,
            // a.compareTo(b) will return 1, 1 == 0 is false, r is expected to be false.
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
        } else {
            throw new IllegalArgumentException("Unknown comparison operator: " + op);
        }
    }

    @DontInline
    public static void verifyResultsByte(VectorSpecies<Byte> vs, VectorOperators.Comparison op) {
        for (int i = 0; i < vs.length(); i++) {
            compareResults(ba[i], bb[i], mr[i], op);
        }
    }

    @DontInline
    public static void verifyResultsShort(VectorSpecies<Short> vs, VectorOperators.Comparison op) {
        for (int i = 0; i < vs.length(); i++) {
            compareResults(sa[i], sb[i], mr[i], op);
        }
    }

    @DontInline
    public static void verifyResultsInt(VectorSpecies<Integer> vs, VectorOperators.Comparison op) {
        for (int i = 0; i < vs.length(); i++) {
            compareResults(ia[i], ib[i], mr[i], op);
        }
    }

    @DontInline
    public static void verifyResultsLong(VectorSpecies<Long> vs, VectorOperators.Comparison op) {
        for (int i = 0; i < vs.length(); i++) {
            compareResults(la[i], lb[i], mr[i], op);
        }
    }

    @DontInline
    public static void verifyResultsFloat(VectorSpecies<Float> vs, VectorOperators.Comparison op, float[] a, float[] b) {
        for (int i = 0; i < vs.length(); i++) {
            compareResults(a[i], b[i], mr[i], op);
        }
    }

    @DontInline
    public static void verifyResultsDouble(VectorSpecies<Double> vs, VectorOperators.Comparison op, double[] a, double[] b) {
        for (int i = 0; i < vs.length(); i++) {
            compareResults(a[i], b[i], mr[i], op);
        }
    }

    interface VectorMaskOperator {
        public VectorMask apply(VectorMask m);
    }

    @ForceInline
    public static void testCompareMaskNotByte(VectorSpecies<Byte> vs, VectorOperators.Comparison op, VectorMaskOperator func) {
        ByteVector av = ByteVector.fromArray(vs, ba, 0);
        ByteVector bv = ByteVector.fromArray(vs, bb, 0);
        VectorMask<Byte> m = av.compare(op, bv);
        func.apply(m).intoArray(mr, 0);
    }

    @ForceInline
    public static void testCompareMaskNotShort(VectorSpecies<Short> vs, VectorOperators.Comparison op, VectorMaskOperator func) {
        ShortVector av = ShortVector.fromArray(vs, sa, 0);
        ShortVector bv = ShortVector.fromArray(vs, sb, 0);
        VectorMask<Short> m = av.compare(op, bv);
        func.apply(m).intoArray(mr, 0);
    }

    @ForceInline
    public static void testCompareMaskNotInt(VectorSpecies<Integer> vs, VectorOperators.Comparison op, VectorMaskOperator func) {
        IntVector av = IntVector.fromArray(vs, ia, 0);
        IntVector bv = IntVector.fromArray(vs, ib, 0);
        VectorMask<Integer> m = av.compare(op, bv);
        func.apply(m).intoArray(mr, 0);
    }

    @ForceInline
    public static void testCompareMaskNotLong(VectorSpecies<Long> vs, VectorOperators.Comparison op, VectorMaskOperator func) {
        LongVector av = LongVector.fromArray(vs, la, 0);
        LongVector bv = LongVector.fromArray(vs, lb, 0);
        VectorMask<Long> m = av.compare(op, bv);
        func.apply(m).intoArray(mr, 0);
    }

    @ForceInline
    public static void testCompareMaskNotFloat(VectorSpecies<Float> vs, VectorOperators.Comparison op, float[] a, float[] b, VectorMaskOperator func) {
        FloatVector av = FloatVector.fromArray(vs, a, 0);
        FloatVector bv = FloatVector.fromArray(vs, b, 0);
        VectorMask<Float> m = av.compare(op, bv);
        func.apply(m).intoArray(mr, 0);
    }

    @ForceInline
    public static void testCompareMaskNotDouble(VectorSpecies<Double> vs, VectorOperators.Comparison op, double[] a, double[] b, VectorMaskOperator func) {
        DoubleVector av = DoubleVector.fromArray(vs, a, 0);
        DoubleVector bv = DoubleVector.fromArray(vs, b, 0);
        VectorMask<Double> m = av.compare(op, bv);
        func.apply(m).intoArray(mr, 0);
    }

    // Byte tests

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 1",
                   IRNode.VECTOR_MASK_CMP, "= 3" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx", "true", "rvv", "true" })
    public static void testCompareEQMaskNotByte() {
        testCompareMaskNotByte(B_SPECIES, VectorOperators.EQ, (m) -> { return m.not(); });
        verifyResultsByte(B_SPECIES, VectorOperators.EQ);
        testCompareMaskNotByte(B_SPECIES, VectorOperators.EQ, (m) -> { return B_SPECIES.maskAll(true).xor(m); });
        verifyResultsByte(B_SPECIES, VectorOperators.EQ);

        testCompareMaskNotByte(ByteVector.SPECIES_64, VectorOperators.EQ, (m) -> { return m.cast(ShortVector.SPECIES_128).not(); });
        verifyResultsByte(ByteVector.SPECIES_64, VectorOperators.EQ);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 1",
                   IRNode.VECTOR_MASK_CMP, "= 3" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx", "true", "rvv", "true" })
    public static void testCompareNEMaskNotByte() {
        testCompareMaskNotByte(B_SPECIES, VectorOperators.NE, (m) -> { return m.not(); });
        verifyResultsByte(B_SPECIES, VectorOperators.NE);
        testCompareMaskNotByte(B_SPECIES, VectorOperators.NE, (m) -> { return B_SPECIES.maskAll(true).xor(m); });
        verifyResultsByte(B_SPECIES, VectorOperators.NE);

        testCompareMaskNotByte(ByteVector.SPECIES_64, VectorOperators.NE, (m) -> { return m.cast(ShortVector.SPECIES_128).not(); });
        verifyResultsByte(ByteVector.SPECIES_64, VectorOperators.NE);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 1",
                   IRNode.VECTOR_MASK_CMP, "= 3" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx", "true", "rvv", "true" })
    public static void testCompareLTMaskNotByte() {
        testCompareMaskNotByte(B_SPECIES, VectorOperators.LT, (m) -> { return m.not(); });
        verifyResultsByte(B_SPECIES, VectorOperators.LT);
        testCompareMaskNotByte(B_SPECIES, VectorOperators.LT, (m) -> { return B_SPECIES.maskAll(true).xor(m); });
        verifyResultsByte(B_SPECIES, VectorOperators.LT);

        testCompareMaskNotByte(ByteVector.SPECIES_64, VectorOperators.LT, (m) -> { return m.cast(ShortVector.SPECIES_128).not(); });
        verifyResultsByte(ByteVector.SPECIES_64, VectorOperators.LT);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 1",
                   IRNode.VECTOR_MASK_CMP, "= 3" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx", "true", "rvv", "true" })
    public static void testCompareGTMaskNotByte() {
        testCompareMaskNotByte(B_SPECIES, VectorOperators.GT, (m) -> { return m.not(); });
        verifyResultsByte(B_SPECIES, VectorOperators.GT);
        testCompareMaskNotByte(B_SPECIES, VectorOperators.GT, (m) -> { return B_SPECIES.maskAll(true).xor(m); });
        verifyResultsByte(B_SPECIES, VectorOperators.GT);

        testCompareMaskNotByte(ByteVector.SPECIES_64, VectorOperators.GT, (m) -> { return m.cast(ShortVector.SPECIES_128).not(); });
        verifyResultsByte(ByteVector.SPECIES_64, VectorOperators.GT);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 1",
                   IRNode.VECTOR_MASK_CMP, "= 3" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx", "true", "rvv", "true" })
    public static void testCompareLEMaskNotByte() {
        testCompareMaskNotByte(B_SPECIES, VectorOperators.LE, (m) -> { return m.not(); });
        verifyResultsByte(B_SPECIES, VectorOperators.LE);
        testCompareMaskNotByte(B_SPECIES, VectorOperators.LE, (m) -> { return B_SPECIES.maskAll(true).xor(m); });
        verifyResultsByte(B_SPECIES, VectorOperators.LE);

        testCompareMaskNotByte(ByteVector.SPECIES_64, VectorOperators.LE, (m) -> { return m.cast(ShortVector.SPECIES_128).not(); });
        verifyResultsByte(ByteVector.SPECIES_64, VectorOperators.LE);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 1",
                   IRNode.VECTOR_MASK_CMP, "= 3" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx", "true", "rvv", "true" })
    public static void testCompareGEMaskNotByte() {
        testCompareMaskNotByte(B_SPECIES, VectorOperators.GE, (m) -> { return m.not(); });
        verifyResultsByte(B_SPECIES, VectorOperators.GE);
        testCompareMaskNotByte(B_SPECIES, VectorOperators.GE, (m) -> { return B_SPECIES.maskAll(true).xor(m); });
        verifyResultsByte(B_SPECIES, VectorOperators.GE);

        testCompareMaskNotByte(ByteVector.SPECIES_64, VectorOperators.GE, (m) -> { return m.cast(ShortVector.SPECIES_128).not(); });
        verifyResultsByte(ByteVector.SPECIES_64, VectorOperators.GE);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 1",
                   IRNode.VECTOR_MASK_CMP, "= 3" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx", "true", "rvv", "true" })
    public static void testCompareULTMaskNotByte() {
        testCompareMaskNotByte(B_SPECIES, VectorOperators.ULT, (m) -> { return m.not(); });
        verifyResultsByte(B_SPECIES, VectorOperators.ULT);
        testCompareMaskNotByte(B_SPECIES, VectorOperators.ULT, (m) -> { return B_SPECIES.maskAll(true).xor(m); });
        verifyResultsByte(B_SPECIES, VectorOperators.ULT);

        testCompareMaskNotByte(ByteVector.SPECIES_64, VectorOperators.ULT, (m) -> { return m.cast(ShortVector.SPECIES_128).not(); });
        verifyResultsByte(ByteVector.SPECIES_64, VectorOperators.ULT);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 1",
                   IRNode.VECTOR_MASK_CMP, "= 3" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx", "true", "rvv", "true" })
    public static void testCompareUGTMaskNotByte() {
        testCompareMaskNotByte(B_SPECIES, VectorOperators.UGT, (m) -> { return m.not(); });
        verifyResultsByte(B_SPECIES, VectorOperators.UGT);
        testCompareMaskNotByte(B_SPECIES, VectorOperators.UGT, (m) -> { return B_SPECIES.maskAll(true).xor(m); });
        verifyResultsByte(B_SPECIES, VectorOperators.UGT);

        testCompareMaskNotByte(ByteVector.SPECIES_64, VectorOperators.UGT, (m) -> { return m.cast(ShortVector.SPECIES_128).not(); });
        verifyResultsByte(ByteVector.SPECIES_64, VectorOperators.UGT);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 1",
                   IRNode.VECTOR_MASK_CMP, "= 3" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx", "true", "rvv", "true" })
    public static void testCompareULEMaskNotByte() {
        testCompareMaskNotByte(B_SPECIES, VectorOperators.ULE, (m) -> { return m.not(); });
        verifyResultsByte(B_SPECIES, VectorOperators.ULE);
        testCompareMaskNotByte(B_SPECIES, VectorOperators.ULE, (m) -> { return B_SPECIES.maskAll(true).xor(m); });
        verifyResultsByte(B_SPECIES, VectorOperators.ULE);

        testCompareMaskNotByte(ByteVector.SPECIES_64, VectorOperators.ULE, (m) -> { return m.cast(ShortVector.SPECIES_128).not(); });
        verifyResultsByte(ByteVector.SPECIES_64, VectorOperators.ULE);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 1",
                   IRNode.VECTOR_MASK_CMP, "= 3" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx", "true", "rvv", "true" })
    public static void testCompareUGEMaskNotByte() {
        testCompareMaskNotByte(B_SPECIES, VectorOperators.UGE, (m) -> { return m.not(); });
        verifyResultsByte(B_SPECIES, VectorOperators.UGE);
        testCompareMaskNotByte(B_SPECIES, VectorOperators.UGE, (m) -> { return B_SPECIES.maskAll(true).xor(m); });
        verifyResultsByte(B_SPECIES, VectorOperators.UGE);

        testCompareMaskNotByte(ByteVector.SPECIES_64, VectorOperators.UGE, (m) -> { return m.cast(ShortVector.SPECIES_128).not(); });
        verifyResultsByte(ByteVector.SPECIES_64, VectorOperators.UGE);
    }

    // Short tests
    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 2",
                   IRNode.VECTOR_MASK_CMP, "= 4" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx", "true", "rvv", "true" })
    public static void testCompareEQMaskNotShort() {
        testCompareMaskNotShort(S_SPECIES, VectorOperators.EQ, (m) -> { return m.not(); });
        verifyResultsShort(S_SPECIES, VectorOperators.EQ);
        testCompareMaskNotShort(S_SPECIES, VectorOperators.EQ, (m) -> { return S_SPECIES.maskAll(true).xor(m); });
        verifyResultsShort(S_SPECIES, VectorOperators.EQ);

        testCompareMaskNotShort(ShortVector.SPECIES_64, VectorOperators.EQ, (m) -> { return IntVector.SPECIES_128.maskAll(true).xor(m.cast(IntVector.SPECIES_128)); });
        verifyResultsShort(ShortVector.SPECIES_64, VectorOperators.EQ);
        testCompareMaskNotShort(ShortVector.SPECIES_128, VectorOperators.EQ, (m) -> { return m.cast(ByteVector.SPECIES_64).not(); });
        verifyResultsShort(ShortVector.SPECIES_128, VectorOperators.EQ);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 2",
                   IRNode.VECTOR_MASK_CMP, "= 4" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx", "true", "rvv", "true" })
    public static void testCompareNEMaskNotShort() {
        testCompareMaskNotShort(S_SPECIES, VectorOperators.NE, (m) -> { return m.not(); });
        verifyResultsShort(S_SPECIES, VectorOperators.NE);
        testCompareMaskNotShort(S_SPECIES, VectorOperators.NE, (m) -> { return S_SPECIES.maskAll(true).xor(m); });
        verifyResultsShort(S_SPECIES, VectorOperators.NE);

        testCompareMaskNotShort(ShortVector.SPECIES_64, VectorOperators.NE, (m) -> { return IntVector.SPECIES_128.maskAll(true).xor(m.cast(IntVector.SPECIES_128)); });
        verifyResultsShort(ShortVector.SPECIES_64, VectorOperators.NE);
        testCompareMaskNotShort(ShortVector.SPECIES_128, VectorOperators.NE, (m) -> { return m.cast(ByteVector.SPECIES_64).not(); });
        verifyResultsShort(ShortVector.SPECIES_128, VectorOperators.NE);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 2",
                   IRNode.VECTOR_MASK_CMP, "= 4" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx", "true", "rvv", "true" })
    public static void testCompareLTMaskNotShort() {
        testCompareMaskNotShort(S_SPECIES, VectorOperators.LT, (m) -> { return m.not(); });
        verifyResultsShort(S_SPECIES, VectorOperators.LT);
        testCompareMaskNotShort(S_SPECIES, VectorOperators.LT, (m) -> { return S_SPECIES.maskAll(true).xor(m); });
        verifyResultsShort(S_SPECIES, VectorOperators.LT);

        testCompareMaskNotShort(ShortVector.SPECIES_64, VectorOperators.LT, (m) -> { return IntVector.SPECIES_128.maskAll(true).xor(m.cast(IntVector.SPECIES_128)); });
        verifyResultsShort(ShortVector.SPECIES_64, VectorOperators.LT);
        testCompareMaskNotShort(ShortVector.SPECIES_128, VectorOperators.LT, (m) -> { return m.cast(ByteVector.SPECIES_64).not(); });
        verifyResultsShort(ShortVector.SPECIES_128, VectorOperators.LT);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 2",
                   IRNode.VECTOR_MASK_CMP, "= 4" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx", "true", "rvv", "true" })
    public static void testCompareGTMaskNotShort() {
        testCompareMaskNotShort(S_SPECIES, VectorOperators.GT, (m) -> { return m.not(); });
        verifyResultsShort(S_SPECIES, VectorOperators.GT);
        testCompareMaskNotShort(S_SPECIES, VectorOperators.GT, (m) -> { return S_SPECIES.maskAll(true).xor(m); });
        verifyResultsShort(S_SPECIES, VectorOperators.GT);

        testCompareMaskNotShort(ShortVector.SPECIES_64, VectorOperators.GT, (m) -> { return IntVector.SPECIES_128.maskAll(true).xor(m.cast(IntVector.SPECIES_128)); });
        verifyResultsShort(ShortVector.SPECIES_64, VectorOperators.GT);
        testCompareMaskNotShort(ShortVector.SPECIES_128, VectorOperators.GT, (m) -> { return m.cast(ByteVector.SPECIES_64).not(); });
        verifyResultsShort(ShortVector.SPECIES_128, VectorOperators.GT);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 2",
                   IRNode.VECTOR_MASK_CMP, "= 4" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx", "true", "rvv", "true" })
    public static void testCompareLEMaskNotShort() {
        testCompareMaskNotShort(S_SPECIES, VectorOperators.LE, (m) -> { return m.not(); });
        verifyResultsShort(S_SPECIES, VectorOperators.LE);
        testCompareMaskNotShort(S_SPECIES, VectorOperators.LE, (m) -> { return S_SPECIES.maskAll(true).xor(m); });
        verifyResultsShort(S_SPECIES, VectorOperators.LE);

        testCompareMaskNotShort(ShortVector.SPECIES_64, VectorOperators.LE, (m) -> { return IntVector.SPECIES_128.maskAll(true).xor(m.cast(IntVector.SPECIES_128)); });
        verifyResultsShort(ShortVector.SPECIES_64, VectorOperators.LE);
        testCompareMaskNotShort(ShortVector.SPECIES_128, VectorOperators.LE, (m) -> { return m.cast(ByteVector.SPECIES_64).not(); });
        verifyResultsShort(ShortVector.SPECIES_128, VectorOperators.LE);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 2",
                   IRNode.VECTOR_MASK_CMP, "= 4" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx", "true", "rvv", "true" })
    public static void testCompareGEMaskNotShort() {
        testCompareMaskNotShort(S_SPECIES, VectorOperators.GE, (m) -> { return m.not(); });
        verifyResultsShort(S_SPECIES, VectorOperators.GE);
        testCompareMaskNotShort(S_SPECIES, VectorOperators.GE, (m) -> { return S_SPECIES.maskAll(true).xor(m); });
        verifyResultsShort(S_SPECIES, VectorOperators.GE);

        testCompareMaskNotShort(ShortVector.SPECIES_64, VectorOperators.GE, (m) -> { return IntVector.SPECIES_128.maskAll(true).xor(m.cast(IntVector.SPECIES_128)); });
        verifyResultsShort(ShortVector.SPECIES_64, VectorOperators.GE);
        testCompareMaskNotShort(ShortVector.SPECIES_128, VectorOperators.GE, (m) -> { return m.cast(ByteVector.SPECIES_64).not(); });
        verifyResultsShort(ShortVector.SPECIES_128, VectorOperators.GE);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 2",
                   IRNode.VECTOR_MASK_CMP, "= 4" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx", "true", "rvv", "true" })
    public static void testCompareULTMaskNotShort() {
        testCompareMaskNotShort(S_SPECIES, VectorOperators.ULT, (m) -> { return m.not(); });
        verifyResultsShort(S_SPECIES, VectorOperators.ULT);
        testCompareMaskNotShort(S_SPECIES, VectorOperators.ULT, (m) -> { return S_SPECIES.maskAll(true).xor(m); });
        verifyResultsShort(S_SPECIES, VectorOperators.ULT);

        testCompareMaskNotShort(ShortVector.SPECIES_64, VectorOperators.ULT, (m) -> { return IntVector.SPECIES_128.maskAll(true).xor(m.cast(IntVector.SPECIES_128)); });
        verifyResultsShort(ShortVector.SPECIES_64, VectorOperators.ULT);
        testCompareMaskNotShort(ShortVector.SPECIES_128, VectorOperators.ULT, (m) -> { return m.cast(ByteVector.SPECIES_64).not(); });
        verifyResultsShort(ShortVector.SPECIES_128, VectorOperators.ULT);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 2",
                   IRNode.VECTOR_MASK_CMP, "= 4" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx", "true", "rvv", "true" })
    public static void testCompareUGTMaskNotShort() {
        testCompareMaskNotShort(S_SPECIES, VectorOperators.UGT, (m) -> { return m.not(); });
        verifyResultsShort(S_SPECIES, VectorOperators.UGT);
        testCompareMaskNotShort(S_SPECIES, VectorOperators.UGT, (m) -> { return S_SPECIES.maskAll(true).xor(m); });
        verifyResultsShort(S_SPECIES, VectorOperators.UGT);

        testCompareMaskNotShort(ShortVector.SPECIES_64, VectorOperators.UGT, (m) -> { return IntVector.SPECIES_128.maskAll(true).xor(m.cast(IntVector.SPECIES_128)); });
        verifyResultsShort(ShortVector.SPECIES_64, VectorOperators.UGT);
        testCompareMaskNotShort(ShortVector.SPECIES_128, VectorOperators.UGT, (m) -> { return m.cast(ByteVector.SPECIES_64).not(); });
        verifyResultsShort(ShortVector.SPECIES_128, VectorOperators.UGT);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 2",
                   IRNode.VECTOR_MASK_CMP, "= 4" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx", "true", "rvv", "true" })
    public static void testCompareULEMaskNotShort() {
        testCompareMaskNotShort(S_SPECIES, VectorOperators.ULE, (m) -> { return m.not(); });
        verifyResultsShort(S_SPECIES, VectorOperators.ULE);
        testCompareMaskNotShort(S_SPECIES, VectorOperators.ULE, (m) -> { return S_SPECIES.maskAll(true).xor(m); });
        verifyResultsShort(S_SPECIES, VectorOperators.ULE);

        testCompareMaskNotShort(ShortVector.SPECIES_64, VectorOperators.ULE, (m) -> { return IntVector.SPECIES_128.maskAll(true).xor(m.cast(IntVector.SPECIES_128)); });
        verifyResultsShort(ShortVector.SPECIES_64, VectorOperators.ULE);
        testCompareMaskNotShort(ShortVector.SPECIES_128, VectorOperators.ULE, (m) -> { return m.cast(ByteVector.SPECIES_64).not(); });
        verifyResultsShort(ShortVector.SPECIES_128, VectorOperators.ULE);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 2",
                   IRNode.VECTOR_MASK_CMP, "= 4" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx", "true", "rvv", "true" })
    public static void testCompareUGEMaskNotShort() {
        testCompareMaskNotShort(S_SPECIES, VectorOperators.UGE, (m) -> { return m.not(); });
        verifyResultsShort(S_SPECIES, VectorOperators.UGE);
        testCompareMaskNotShort(S_SPECIES, VectorOperators.UGE, (m) -> { return S_SPECIES.maskAll(true).xor(m); });
        verifyResultsShort(S_SPECIES, VectorOperators.UGE);

        testCompareMaskNotShort(ShortVector.SPECIES_64, VectorOperators.UGE, (m) -> { return IntVector.SPECIES_128.maskAll(true).xor(m.cast(IntVector.SPECIES_128)); });
        verifyResultsShort(ShortVector.SPECIES_64, VectorOperators.UGE);
        testCompareMaskNotShort(ShortVector.SPECIES_128, VectorOperators.UGE, (m) -> { return m.cast(ByteVector.SPECIES_64).not(); });
        verifyResultsShort(ShortVector.SPECIES_128, VectorOperators.UGE);
    }

    // Int tests
    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 2",
                   IRNode.VECTOR_MASK_CMP, "= 4" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx2", "true", "rvv", "true" })
    public static void testCompareEQMaskNotInt() {
        testCompareMaskNotInt(I_SPECIES, VectorOperators.EQ, (m) -> { return m.not(); });
        verifyResultsInt(I_SPECIES, VectorOperators.EQ);
        testCompareMaskNotInt(I_SPECIES, VectorOperators.EQ, (m) -> { return I_SPECIES.maskAll(true).xor(m); });
        verifyResultsInt(I_SPECIES, VectorOperators.EQ);

        testCompareMaskNotInt(I_SPECIES_FOR_CAST, VectorOperators.EQ, (m) -> { return L_SPECIES_FOR_CAST.maskAll(true).xor(m.cast(L_SPECIES_FOR_CAST)); });
        verifyResultsInt(I_SPECIES_FOR_CAST, VectorOperators.EQ);
        testCompareMaskNotInt(IntVector.SPECIES_128, VectorOperators.EQ, (m) -> { return m.cast(ShortVector.SPECIES_64).not(); });
        verifyResultsInt(IntVector.SPECIES_128, VectorOperators.EQ);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 2",
                   IRNode.VECTOR_MASK_CMP, "= 4" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx2", "true", "rvv", "true" })
    public static void testCompareNEMaskNotInt() {
        testCompareMaskNotInt(I_SPECIES, VectorOperators.NE, (m) -> { return m.not(); });
        verifyResultsInt(I_SPECIES, VectorOperators.NE);
        testCompareMaskNotInt(I_SPECIES, VectorOperators.NE, (m) -> { return I_SPECIES.maskAll(true).xor(m); });
        verifyResultsInt(I_SPECIES, VectorOperators.NE);

        testCompareMaskNotInt(I_SPECIES_FOR_CAST, VectorOperators.NE, (m) -> { return L_SPECIES_FOR_CAST.maskAll(true).xor(m.cast(L_SPECIES_FOR_CAST)); });
        verifyResultsInt(I_SPECIES_FOR_CAST, VectorOperators.NE);
        testCompareMaskNotInt(IntVector.SPECIES_128, VectorOperators.NE, (m) -> { return m.cast(ShortVector.SPECIES_64).not(); });
        verifyResultsInt(IntVector.SPECIES_128, VectorOperators.NE);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 2",
                   IRNode.VECTOR_MASK_CMP, "= 4" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx2", "true", "rvv", "true" })
    public static void testCompareLTMaskNotInt() {
        testCompareMaskNotInt(I_SPECIES, VectorOperators.LT, (m) -> { return m.not(); });
        verifyResultsInt(I_SPECIES, VectorOperators.LT);
        testCompareMaskNotInt(I_SPECIES, VectorOperators.LT, (m) -> { return I_SPECIES.maskAll(true).xor(m); });
        verifyResultsInt(I_SPECIES, VectorOperators.LT);

        testCompareMaskNotInt(I_SPECIES_FOR_CAST, VectorOperators.LT, (m) -> { return L_SPECIES_FOR_CAST.maskAll(true).xor(m.cast(L_SPECIES_FOR_CAST)); });
        verifyResultsInt(I_SPECIES_FOR_CAST, VectorOperators.LT);
        testCompareMaskNotInt(IntVector.SPECIES_128, VectorOperators.LT, (m) -> { return m.cast(ShortVector.SPECIES_64).not(); });
        verifyResultsInt(IntVector.SPECIES_128, VectorOperators.LT);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 2",
                   IRNode.VECTOR_MASK_CMP, "= 4" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx2", "true", "rvv", "true" })
    public static void testCompareGTMaskNotInt() {
        testCompareMaskNotInt(I_SPECIES, VectorOperators.GT, (m) -> { return m.not(); });
        verifyResultsInt(I_SPECIES, VectorOperators.GT);
        testCompareMaskNotInt(I_SPECIES, VectorOperators.GT, (m) -> { return I_SPECIES.maskAll(true).xor(m); });
        verifyResultsInt(I_SPECIES, VectorOperators.GT);

        testCompareMaskNotInt(I_SPECIES_FOR_CAST, VectorOperators.GT, (m) -> { return L_SPECIES_FOR_CAST.maskAll(true).xor(m.cast(L_SPECIES_FOR_CAST)); });
        verifyResultsInt(I_SPECIES_FOR_CAST, VectorOperators.GT);
        testCompareMaskNotInt(IntVector.SPECIES_128, VectorOperators.GT, (m) -> { return m.cast(ShortVector.SPECIES_64).not(); });
        verifyResultsInt(IntVector.SPECIES_128, VectorOperators.GT);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 2",
                   IRNode.VECTOR_MASK_CMP, "= 4" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx2", "true", "rvv", "true" })
    public static void testCompareLEMaskNotInt() {
        testCompareMaskNotInt(I_SPECIES, VectorOperators.LE, (m) -> { return m.not(); });
        verifyResultsInt(I_SPECIES, VectorOperators.LE);
        testCompareMaskNotInt(I_SPECIES, VectorOperators.LE, (m) -> { return I_SPECIES.maskAll(true).xor(m); });
        verifyResultsInt(I_SPECIES, VectorOperators.LE);

        testCompareMaskNotInt(I_SPECIES_FOR_CAST, VectorOperators.LE, (m) -> { return L_SPECIES_FOR_CAST.maskAll(true).xor(m.cast(L_SPECIES_FOR_CAST)); });
        verifyResultsInt(I_SPECIES_FOR_CAST, VectorOperators.LE);
        testCompareMaskNotInt(IntVector.SPECIES_128, VectorOperators.LE, (m) -> { return m.cast(ShortVector.SPECIES_64).not(); });
        verifyResultsInt(IntVector.SPECIES_128, VectorOperators.LE);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 2",
                   IRNode.VECTOR_MASK_CMP, "= 4" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx2", "true", "rvv", "true" })
    public static void testCompareGEMaskNotInt() {
        testCompareMaskNotInt(I_SPECIES, VectorOperators.GE, (m) -> { return m.not(); });
        verifyResultsInt(I_SPECIES, VectorOperators.GE);
        testCompareMaskNotInt(I_SPECIES, VectorOperators.GE, (m) -> { return I_SPECIES.maskAll(true).xor(m); });
        verifyResultsInt(I_SPECIES, VectorOperators.GE);

        testCompareMaskNotInt(I_SPECIES_FOR_CAST, VectorOperators.GE, (m) -> { return L_SPECIES_FOR_CAST.maskAll(true).xor(m.cast(L_SPECIES_FOR_CAST)); });
        verifyResultsInt(I_SPECIES_FOR_CAST, VectorOperators.GE);
        testCompareMaskNotInt(IntVector.SPECIES_128, VectorOperators.GE, (m) -> { return m.cast(ShortVector.SPECIES_64).not(); });
        verifyResultsInt(IntVector.SPECIES_128, VectorOperators.GE);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 2",
                   IRNode.VECTOR_MASK_CMP, "= 4" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx2", "true", "rvv", "true" })
    public static void testCompareULTMaskNotInt() {
        testCompareMaskNotInt(I_SPECIES, VectorOperators.ULT, (m) -> { return m.not(); });
        verifyResultsInt(I_SPECIES, VectorOperators.ULT);
        testCompareMaskNotInt(I_SPECIES, VectorOperators.ULT, (m) -> { return I_SPECIES.maskAll(true).xor(m); });
        verifyResultsInt(I_SPECIES, VectorOperators.ULT);

        testCompareMaskNotInt(I_SPECIES_FOR_CAST, VectorOperators.ULT, (m) -> { return L_SPECIES_FOR_CAST.maskAll(true).xor(m.cast(L_SPECIES_FOR_CAST)); });
        verifyResultsInt(I_SPECIES_FOR_CAST, VectorOperators.ULT);
        testCompareMaskNotInt(IntVector.SPECIES_128, VectorOperators.ULT, (m) -> { return m.cast(ShortVector.SPECIES_64).not(); });
        verifyResultsInt(IntVector.SPECIES_128, VectorOperators.ULT);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 2",
                   IRNode.VECTOR_MASK_CMP, "= 4" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx2", "true", "rvv", "true" })
    public static void testCompareUGTMaskNotInt() {
        testCompareMaskNotInt(I_SPECIES, VectorOperators.UGT, (m) -> { return m.not(); });
        verifyResultsInt(I_SPECIES, VectorOperators.UGT);
        testCompareMaskNotInt(I_SPECIES, VectorOperators.UGT, (m) -> { return I_SPECIES.maskAll(true).xor(m); });
        verifyResultsInt(I_SPECIES, VectorOperators.UGT);

        testCompareMaskNotInt(I_SPECIES_FOR_CAST, VectorOperators.UGT, (m) -> { return L_SPECIES_FOR_CAST.maskAll(true).xor(m.cast(L_SPECIES_FOR_CAST)); });
        verifyResultsInt(I_SPECIES_FOR_CAST, VectorOperators.UGT);
        testCompareMaskNotInt(IntVector.SPECIES_128, VectorOperators.UGT, (m) -> { return m.cast(ShortVector.SPECIES_64).not(); });
        verifyResultsInt(IntVector.SPECIES_128, VectorOperators.UGT);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 2",
                   IRNode.VECTOR_MASK_CMP, "= 4" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx2", "true", "rvv", "true" })
    public static void testCompareULEMaskNotInt() {
        testCompareMaskNotInt(I_SPECIES, VectorOperators.ULE, (m) -> { return m.not(); });
        verifyResultsInt(I_SPECIES, VectorOperators.ULE);
        testCompareMaskNotInt(I_SPECIES, VectorOperators.ULE, (m) -> { return I_SPECIES.maskAll(true).xor(m); });
        verifyResultsInt(I_SPECIES, VectorOperators.ULE);

        testCompareMaskNotInt(I_SPECIES_FOR_CAST, VectorOperators.ULE, (m) -> { return L_SPECIES_FOR_CAST.maskAll(true).xor(m.cast(L_SPECIES_FOR_CAST)); });
        verifyResultsInt(I_SPECIES_FOR_CAST, VectorOperators.ULE);
        testCompareMaskNotInt(IntVector.SPECIES_128, VectorOperators.ULE, (m) -> { return m.cast(ShortVector.SPECIES_64).not(); });
        verifyResultsInt(IntVector.SPECIES_128, VectorOperators.ULE);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 2",
                   IRNode.VECTOR_MASK_CMP, "= 4" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx2", "true", "rvv", "true" })
    public static void testCompareUGEMaskNotInt() {
        testCompareMaskNotInt(I_SPECIES, VectorOperators.UGE, (m) -> { return m.not(); });
        verifyResultsInt(I_SPECIES, VectorOperators.UGE);
        testCompareMaskNotInt(I_SPECIES, VectorOperators.UGE, (m) -> { return I_SPECIES.maskAll(true).xor(m); });
        verifyResultsInt(I_SPECIES, VectorOperators.UGE);

        testCompareMaskNotInt(I_SPECIES_FOR_CAST, VectorOperators.UGE, (m) -> { return L_SPECIES_FOR_CAST.maskAll(true).xor(m.cast(L_SPECIES_FOR_CAST)); });
        verifyResultsInt(I_SPECIES_FOR_CAST, VectorOperators.UGE);
        testCompareMaskNotInt(IntVector.SPECIES_128, VectorOperators.UGE, (m) -> { return m.cast(ShortVector.SPECIES_64).not(); });
        verifyResultsInt(IntVector.SPECIES_128, VectorOperators.UGE);
    }

    // Long tests
    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 1",
                   IRNode.VECTOR_MASK_CMP, "= 3" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx2", "true", "rvv", "true" })
    public static void testCompareEQMaskNotLong() {
        testCompareMaskNotLong(L_SPECIES, VectorOperators.EQ, (m) -> { return m.not(); });
        verifyResultsLong(L_SPECIES, VectorOperators.EQ);
        testCompareMaskNotLong(L_SPECIES, VectorOperators.EQ, (m) -> { return L_SPECIES.maskAll(true).xor(m); });
        verifyResultsLong(L_SPECIES, VectorOperators.EQ);

        testCompareMaskNotLong(L_SPECIES_FOR_CAST, VectorOperators.EQ, (m) -> { return m.cast(I_SPECIES_FOR_CAST).not(); });
        verifyResultsLong(L_SPECIES_FOR_CAST, VectorOperators.EQ);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 1",
                   IRNode.VECTOR_MASK_CMP, "= 3" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx2", "true", "rvv", "true" })
    public static void testCompareNEMaskNotLong() {
        testCompareMaskNotLong(L_SPECIES, VectorOperators.NE, (m) -> { return m.not(); });
        verifyResultsLong(L_SPECIES, VectorOperators.NE);
        testCompareMaskNotLong(L_SPECIES, VectorOperators.NE, (m) -> { return L_SPECIES.maskAll(true).xor(m); });
        verifyResultsLong(L_SPECIES, VectorOperators.NE);

        testCompareMaskNotLong(L_SPECIES_FOR_CAST, VectorOperators.NE, (m) -> { return m.cast(I_SPECIES_FOR_CAST).not(); });
        verifyResultsLong(L_SPECIES_FOR_CAST, VectorOperators.NE);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 1",
                   IRNode.VECTOR_MASK_CMP, "= 3" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx2", "true", "rvv", "true" })
    public static void testCompareLTMaskNotLong() {
        testCompareMaskNotLong(L_SPECIES, VectorOperators.LT, (m) -> { return m.not(); });
        verifyResultsLong(L_SPECIES, VectorOperators.LT);
        testCompareMaskNotLong(L_SPECIES, VectorOperators.LT, (m) -> { return L_SPECIES.maskAll(true).xor(m); });
        verifyResultsLong(L_SPECIES, VectorOperators.LT);

        testCompareMaskNotLong(L_SPECIES_FOR_CAST, VectorOperators.LT, (m) -> { return m.cast(I_SPECIES_FOR_CAST).not(); });
        verifyResultsLong(L_SPECIES_FOR_CAST, VectorOperators.LT);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 1",
                   IRNode.VECTOR_MASK_CMP, "= 3" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx2", "true", "rvv", "true" })
    public static void testCompareGTMaskNotLong() {
        testCompareMaskNotLong(L_SPECIES, VectorOperators.GT, (m) -> { return m.not(); });
        verifyResultsLong(L_SPECIES, VectorOperators.GT);
        testCompareMaskNotLong(L_SPECIES, VectorOperators.GT, (m) -> { return L_SPECIES.maskAll(true).xor(m); });
        verifyResultsLong(L_SPECIES, VectorOperators.GT);

        testCompareMaskNotLong(L_SPECIES_FOR_CAST, VectorOperators.GT, (m) -> { return m.cast(I_SPECIES_FOR_CAST).not(); });
        verifyResultsLong(L_SPECIES_FOR_CAST, VectorOperators.GT);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 1",
                   IRNode.VECTOR_MASK_CMP, "= 3" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx2", "true", "rvv", "true" })
    public static void testCompareLEMaskNotLong() {
        testCompareMaskNotLong(L_SPECIES, VectorOperators.LE, (m) -> { return m.not(); });
        verifyResultsLong(L_SPECIES, VectorOperators.LE);
        testCompareMaskNotLong(L_SPECIES, VectorOperators.LE, (m) -> { return L_SPECIES.maskAll(true).xor(m); });
        verifyResultsLong(L_SPECIES, VectorOperators.LE);

        testCompareMaskNotLong(L_SPECIES_FOR_CAST, VectorOperators.LE, (m) -> { return m.cast(I_SPECIES_FOR_CAST).not(); });
        verifyResultsLong(L_SPECIES_FOR_CAST, VectorOperators.LE);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 1",
                   IRNode.VECTOR_MASK_CMP, "= 3" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx2", "true", "rvv", "true" })
    public static void testCompareGEMaskNotLong() {
        testCompareMaskNotLong(L_SPECIES, VectorOperators.GE, (m) -> { return m.not(); });
        verifyResultsLong(L_SPECIES, VectorOperators.GE);
        testCompareMaskNotLong(L_SPECIES, VectorOperators.GE, (m) -> { return L_SPECIES.maskAll(true).xor(m); });
        verifyResultsLong(L_SPECIES, VectorOperators.GE);

        testCompareMaskNotLong(L_SPECIES_FOR_CAST, VectorOperators.GE, (m) -> { return m.cast(I_SPECIES_FOR_CAST).not(); });
        verifyResultsLong(L_SPECIES_FOR_CAST, VectorOperators.GE);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 1",
                   IRNode.VECTOR_MASK_CMP, "= 3" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx2", "true", "rvv", "true" })
    public static void testCompareULTMaskNotLong() {
        testCompareMaskNotLong(L_SPECIES, VectorOperators.ULT, (m) -> { return m.not(); });
        verifyResultsLong(L_SPECIES, VectorOperators.ULT);
        testCompareMaskNotLong(L_SPECIES, VectorOperators.ULT, (m) -> { return L_SPECIES.maskAll(true).xor(m); });
        verifyResultsLong(L_SPECIES, VectorOperators.ULT);

        testCompareMaskNotLong(L_SPECIES_FOR_CAST, VectorOperators.ULT, (m) -> { return m.cast(I_SPECIES_FOR_CAST).not(); });
        verifyResultsLong(L_SPECIES_FOR_CAST, VectorOperators.ULT);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 1",
                   IRNode.VECTOR_MASK_CMP, "= 3" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx2", "true", "rvv", "true" })
    public static void testCompareUGTMaskNotLong() {
        testCompareMaskNotLong(L_SPECIES, VectorOperators.UGT, (m) -> { return m.not(); });
        verifyResultsLong(L_SPECIES, VectorOperators.UGT);
        testCompareMaskNotLong(L_SPECIES, VectorOperators.UGT, (m) -> { return L_SPECIES.maskAll(true).xor(m); });
        verifyResultsLong(L_SPECIES, VectorOperators.UGT);

        testCompareMaskNotLong(L_SPECIES_FOR_CAST, VectorOperators.UGT, (m) -> { return m.cast(I_SPECIES_FOR_CAST).not(); });
        verifyResultsLong(L_SPECIES_FOR_CAST, VectorOperators.UGT);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 1",
                   IRNode.VECTOR_MASK_CMP, "= 3" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx2", "true", "rvv", "true" })
    public static void testCompareULEMaskNotLong() {
        testCompareMaskNotLong(L_SPECIES, VectorOperators.ULE, (m) -> { return m.not(); });
        verifyResultsLong(L_SPECIES, VectorOperators.ULE);
        testCompareMaskNotLong(L_SPECIES, VectorOperators.ULE, (m) -> { return L_SPECIES.maskAll(true).xor(m); });
        verifyResultsLong(L_SPECIES, VectorOperators.ULE);

        testCompareMaskNotLong(L_SPECIES_FOR_CAST, VectorOperators.ULE, (m) -> { return m.cast(I_SPECIES_FOR_CAST).not(); });
        verifyResultsLong(L_SPECIES_FOR_CAST, VectorOperators.ULE);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 1",
                   IRNode.VECTOR_MASK_CMP, "= 3" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx2", "true", "rvv", "true" })
    public static void testCompareUGEMaskNotLong() {
        testCompareMaskNotLong(L_SPECIES, VectorOperators.UGE, (m) -> { return m.not(); });
        verifyResultsLong(L_SPECIES, VectorOperators.UGE);
        testCompareMaskNotLong(L_SPECIES, VectorOperators.UGE, (m) -> { return L_SPECIES.maskAll(true).xor(m); });
        verifyResultsLong(L_SPECIES, VectorOperators.UGE);

        testCompareMaskNotLong(L_SPECIES_FOR_CAST, VectorOperators.UGE, (m) -> { return m.cast(I_SPECIES_FOR_CAST).not(); });
        verifyResultsLong(L_SPECIES_FOR_CAST, VectorOperators.UGE);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CMP, "= 2" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx", "true", "rvv", "true" })
    public static void testCompareEQMaskNotFloat() {
        testCompareMaskNotFloat(F_SPECIES, VectorOperators.EQ, fa, fb, (m) -> { return m.not(); });
        verifyResultsFloat(F_SPECIES, VectorOperators.EQ, fa, fb);
        testCompareMaskNotFloat(F_SPECIES, VectorOperators.EQ, fa, fb, (m) -> { return F_SPECIES.maskAll(true).xor(m); });
        verifyResultsFloat(F_SPECIES, VectorOperators.EQ, fa, fb);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CMP, "= 2" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx", "true", "rvv", "true" })
    public static void testCompareNEMaskNotFloat() {
        testCompareMaskNotFloat(F_SPECIES, VectorOperators.NE, fa, fb, (m) -> { return m.not(); });
        verifyResultsFloat(F_SPECIES, VectorOperators.NE, fa, fb);
        testCompareMaskNotFloat(F_SPECIES, VectorOperators.NE, fa, fb, (m) -> { return F_SPECIES.maskAll(true).xor(m); });
        verifyResultsFloat(F_SPECIES, VectorOperators.NE, fa, fb);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CMP, "= 2" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx", "true", "rvv", "true" })
    public static void testCompareEQMaskNotFloatNaN() {
        testCompareMaskNotFloat(F_SPECIES, VectorOperators.EQ, fa, fnan, (m) -> { return m.not(); });
        verifyResultsFloat(F_SPECIES, VectorOperators.EQ, fa, fnan);
        testCompareMaskNotFloat(F_SPECIES, VectorOperators.EQ, fa, fnan, (m) -> { return F_SPECIES.maskAll(true).xor(m); });
        verifyResultsFloat(F_SPECIES, VectorOperators.EQ, fa, fnan);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CMP, "= 2" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx", "true", "rvv", "true" })
    public static void testCompareNEMaskNotFloatNaN() {
        testCompareMaskNotFloat(F_SPECIES, VectorOperators.NE, fa, fnan, (m) -> { return m.not(); });
        verifyResultsFloat(F_SPECIES, VectorOperators.NE, fa, fnan);
        testCompareMaskNotFloat(F_SPECIES, VectorOperators.NE, fa, fnan, (m) -> { return F_SPECIES.maskAll(true).xor(m); });
        verifyResultsFloat(F_SPECIES, VectorOperators.NE, fa, fnan);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CMP, "= 2" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx", "true", "rvv", "true" })
    public static void testCompareEQMaskNotFloatPositiveInfinity() {
        testCompareMaskNotFloat(F_SPECIES, VectorOperators.EQ, fa, fpinf, (m) -> { return m.not(); });
        verifyResultsFloat(F_SPECIES, VectorOperators.EQ, fa, fpinf);
        testCompareMaskNotFloat(F_SPECIES, VectorOperators.EQ, fa, fpinf, (m) -> { return F_SPECIES.maskAll(true).xor(m); });
        verifyResultsFloat(F_SPECIES, VectorOperators.EQ, fa, fpinf);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CMP, "= 2" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx", "true", "rvv", "true" })
    public static void testCompareNEMaskNotFloatPositiveInfinity() {
        testCompareMaskNotFloat(F_SPECIES, VectorOperators.NE, fa, fpinf, (m) -> { return m.not(); });
        verifyResultsFloat(F_SPECIES, VectorOperators.NE, fa, fpinf);
        testCompareMaskNotFloat(F_SPECIES, VectorOperators.NE, fa, fpinf, (m) -> { return F_SPECIES.maskAll(true).xor(m); });
        verifyResultsFloat(F_SPECIES, VectorOperators.NE, fa, fpinf);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CMP, "= 2" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx", "true", "rvv", "true" })
    public static void testCompareEQMaskNotFloatNegativeInfinity() {
        testCompareMaskNotFloat(F_SPECIES, VectorOperators.EQ, fa, fninf, (m) -> { return m.not(); });
        verifyResultsFloat(F_SPECIES, VectorOperators.EQ, fa, fninf);
        testCompareMaskNotFloat(F_SPECIES, VectorOperators.EQ, fa, fninf, (m) -> { return F_SPECIES.maskAll(true).xor(m); });
        verifyResultsFloat(F_SPECIES, VectorOperators.EQ, fa, fninf);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CMP, "= 2" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx", "true", "rvv", "true" })
    public static void testCompareNEMaskNotFloatNegativeInfinity() {
        testCompareMaskNotFloat(F_SPECIES, VectorOperators.NE, fa, fninf, (m) -> { return m.not(); });
        verifyResultsFloat(F_SPECIES, VectorOperators.NE, fa, fninf);
        testCompareMaskNotFloat(F_SPECIES, VectorOperators.NE, fa, fninf, (m) -> { return F_SPECIES.maskAll(true).xor(m); });
        verifyResultsFloat(F_SPECIES, VectorOperators.NE, fa, fninf);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CMP, "= 2" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx2", "true", "rvv", "true" })
    public static void testCompareEQMaskNotDouble() {
        testCompareMaskNotDouble(D_SPECIES, VectorOperators.EQ, da, db, (m) -> { return m.not(); });
        verifyResultsDouble(D_SPECIES, VectorOperators.EQ, da, db);
        testCompareMaskNotDouble(D_SPECIES, VectorOperators.EQ, da, db, (m) -> { return D_SPECIES.maskAll(true).xor(m); });
        verifyResultsDouble(D_SPECIES, VectorOperators.EQ, da, db);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CMP, "= 2" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx2", "true", "rvv", "true" })
    public static void testCompareNEMaskNotDouble() {
        testCompareMaskNotDouble(D_SPECIES, VectorOperators.NE, da, db, (m) -> { return m.not(); });
        verifyResultsDouble(D_SPECIES, VectorOperators.NE, da, db);
        testCompareMaskNotDouble(D_SPECIES, VectorOperators.NE, da, db, (m) -> { return D_SPECIES.maskAll(true).xor(m); });
        verifyResultsDouble(D_SPECIES, VectorOperators.NE, da, db);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CMP, "= 2" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx2", "true", "rvv", "true" })
    public static void testCompareEQMaskNotDoubleNaN() {
        testCompareMaskNotDouble(D_SPECIES, VectorOperators.EQ, da, dnan, (m) -> { return m.not(); });
        verifyResultsDouble(D_SPECIES, VectorOperators.EQ, da, dnan);
        testCompareMaskNotDouble(D_SPECIES, VectorOperators.EQ, da, dnan, (m) -> { return D_SPECIES.maskAll(true).xor(m); });
        verifyResultsDouble(D_SPECIES, VectorOperators.EQ, da, dnan);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CMP, "= 2" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx2", "true", "rvv", "true" })
    public static void testCompareNEMaskNotDoubleNaN() {
        testCompareMaskNotDouble(D_SPECIES, VectorOperators.NE, da, dnan, (m) -> { return m.not(); });
        verifyResultsDouble(D_SPECIES, VectorOperators.NE, da, dnan);
        testCompareMaskNotDouble(D_SPECIES, VectorOperators.NE, da, dnan, (m) -> { return D_SPECIES.maskAll(true).xor(m); });
        verifyResultsDouble(D_SPECIES, VectorOperators.NE, da, dnan);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CMP, "= 2" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx2", "true", "rvv", "true" })
    public static void testCompareEQMaskNotDoublePositiveInfinity() {
        testCompareMaskNotDouble(D_SPECIES, VectorOperators.EQ, da, dpinf, (m) -> { return m.not(); });
        verifyResultsDouble(D_SPECIES, VectorOperators.EQ, da, dpinf);
        testCompareMaskNotDouble(D_SPECIES, VectorOperators.EQ, da, dpinf, (m) -> { return D_SPECIES.maskAll(true).xor(m); });
        verifyResultsDouble(D_SPECIES, VectorOperators.EQ, da, dpinf);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CMP, "= 2" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx2", "true", "rvv", "true" })
    public static void testCompareNEMaskNotDoublePositiveInfinity() {
        testCompareMaskNotDouble(D_SPECIES, VectorOperators.NE, da, dpinf, (m) -> { return m.not(); });
        verifyResultsDouble(D_SPECIES, VectorOperators.NE, da, dpinf);
        testCompareMaskNotDouble(D_SPECIES, VectorOperators.NE, da, dpinf, (m) -> { return D_SPECIES.maskAll(true).xor(m); });
        verifyResultsDouble(D_SPECIES, VectorOperators.NE, da, dpinf);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CMP, "= 2" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx2", "true", "rvv", "true" })
    public static void testCompareEQMaskNotDoubleNegativeInfinity() {
        testCompareMaskNotDouble(D_SPECIES, VectorOperators.EQ, da, dninf, (m) -> { return m.not(); });
        verifyResultsDouble(D_SPECIES, VectorOperators.EQ, da, dninf);
        testCompareMaskNotDouble(D_SPECIES, VectorOperators.EQ, da, dninf, (m) -> { return D_SPECIES.maskAll(true).xor(m); });
        verifyResultsDouble(D_SPECIES, VectorOperators.EQ, da, dninf);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 0",
                   IRNode.XOR_V, "= 0",
                   IRNode.VECTOR_MASK_CMP, "= 2" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx2", "true", "rvv", "true" })
    public static void testCompareNEMaskNotDoubleNegativeInfinity() {
        testCompareMaskNotDouble(D_SPECIES, VectorOperators.NE, da, dninf, (m) -> { return m.not(); });
        verifyResultsDouble(D_SPECIES, VectorOperators.NE, da, dninf);
        testCompareMaskNotDouble(D_SPECIES, VectorOperators.NE, da, dninf, (m) -> { return D_SPECIES.maskAll(true).xor(m); });
        verifyResultsDouble(D_SPECIES, VectorOperators.NE, da, dninf);
    }

    // negative tests

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 2",
                   IRNode.VECTOR_MASK_CMP, "= 2" },
        applyIfCPUFeatureOr = { "sve", "true", "avx512", "true", "rvv", "true"  })
    @IR(counts = { IRNode.XOR_V, "= 2",
                   IRNode.VECTOR_MASK_CMP, "= 2" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" })
    @IR(counts = { IRNode.XOR_V, "= 2",
                   IRNode.VECTOR_MASK_CMP, "= 2" },
        applyIfCPUFeatureAnd = { "avx2", "true" })
    public static void testCompareMaskNotByteNegative() {
        testCompareMaskNotByte(B_SPECIES, VectorOperators.EQ, (m) -> {
            // The vector mask is used multiple times.
            ic[0] = m.trueCount();
            return m.not();
        });
        verifyResultsByte(B_SPECIES, VectorOperators.EQ);

        // One of the operands of XOR is not all ones vector.
        testCompareMaskNotByte(B_SPECIES, VectorOperators.EQ, (m) -> { return B_SPECIES.maskAll(false).xor(m); });
        verifyResultsByte(B_SPECIES, VectorOperators.NE);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 2",
                   IRNode.VECTOR_MASK_CMP, "= 2" },
        applyIfCPUFeatureOr = { "sve", "true", "avx512", "true", "rvv", "true"  })
    @IR(counts = { IRNode.XOR_V, "= 2",
                   IRNode.VECTOR_MASK_CMP, "= 2" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" })
    @IR(counts = { IRNode.XOR_V, "= 2",
                   IRNode.VECTOR_MASK_CMP, "= 2" },
        applyIfCPUFeatureAnd = { "avx2", "true" })
    public static void testCompareMaskNotShortNegative() {
        testCompareMaskNotShort(S_SPECIES, VectorOperators.EQ, (m) -> {
            // The vector mask is used multiple times.
            ic[0] = m.trueCount();
            return m.not();
        });
        verifyResultsShort(S_SPECIES, VectorOperators.EQ);

        // One of the operands of XOR is not all ones vector.
        testCompareMaskNotShort(S_SPECIES, VectorOperators.EQ, (m) -> { return S_SPECIES.maskAll(false).xor(m); });
        verifyResultsShort(S_SPECIES, VectorOperators.NE);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 2",
                   IRNode.VECTOR_MASK_CMP, "= 2" },
        applyIfCPUFeatureOr = { "sve", "true", "avx512", "true", "rvv", "true"  })
    @IR(counts = { IRNode.XOR_V, "= 2",
                   IRNode.VECTOR_MASK_CMP, "= 2" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" })
    @IR(counts = { IRNode.XOR_V, "= 2",
                   IRNode.VECTOR_MASK_CMP, "= 2" },
        applyIfCPUFeatureAnd = { "avx2", "true" })
    public static void testCompareMaskNotIntNegative() {
        testCompareMaskNotInt(I_SPECIES, VectorOperators.EQ, (m) -> {
            // The vector mask is used multiple times.
            ic[0] = m.trueCount();
            return m.not();
        });
        verifyResultsInt(I_SPECIES, VectorOperators.EQ);

        // One of the operands of XOR is not all ones vector.
        testCompareMaskNotInt(I_SPECIES, VectorOperators.EQ, (m) -> { return I_SPECIES.maskAll(false).xor(m); });
        verifyResultsInt(I_SPECIES, VectorOperators.NE);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 2",
                   IRNode.VECTOR_MASK_CMP, "= 2" },
        applyIfCPUFeatureOr = { "sve", "true", "avx512", "true", "rvv", "true"  })
    @IR(counts = { IRNode.XOR_V, "= 2",
                   IRNode.VECTOR_MASK_CMP, "= 2" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" })
    @IR(counts = { IRNode.XOR_V, "= 2",
                   IRNode.VECTOR_MASK_CMP, "= 2" },
        applyIfCPUFeatureAnd = { "avx2", "true" })
    public static void testCompareMaskNotLongNegative() {
        testCompareMaskNotLong(L_SPECIES, VectorOperators.EQ, (m) -> {
            // The vector mask is used multiple times.
            ic[0] = m.trueCount();
            return m.not();
        });
        verifyResultsLong(L_SPECIES, VectorOperators.EQ);

        // One of the operands of XOR is not all ones vector.
        testCompareMaskNotLong(L_SPECIES, VectorOperators.EQ, (m) -> { return L_SPECIES.maskAll(false).xor(m); });
        verifyResultsLong(L_SPECIES, VectorOperators.NE);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 3",
                   IRNode.VECTOR_MASK_CMP, "= 3" },
        applyIfCPUFeatureOr = { "sve", "true", "avx512", "true", "rvv", "true"  })
    @IR(counts = { IRNode.XOR_V, "= 3",
                   IRNode.VECTOR_MASK_CMP, "= 3" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" })
    @IR(counts = { IRNode.XOR_V, "= 3",
                   IRNode.VECTOR_MASK_CMP, "= 3" },
        applyIfCPUFeatureAnd = { "avx2", "true" })
    public static void testCompareMaskNotFloatNegative() {
        testCompareMaskNotFloat(F_SPECIES, VectorOperators.EQ, fa, fb, (m) -> {
            // The vector mask is used multiple times.
            ic[0] = m.trueCount();
            return m.not();
        });
        verifyResultsFloat(F_SPECIES, VectorOperators.EQ, fa, fb);

        // One of the operands of XOR is not all ones vector.
        testCompareMaskNotFloat(F_SPECIES, VectorOperators.EQ, fa, fb, (m) -> { return F_SPECIES.maskAll(false).xor(m); });
        verifyResultsFloat(F_SPECIES, VectorOperators.NE, fa, fb);

        // Float vectors use the LT comparison.
        testCompareMaskNotFloat(F_SPECIES, VectorOperators.LT, fa, fb, (m) -> { return m.not(); });
        verifyResultsFloat(F_SPECIES, VectorOperators.LT, fa, fb);
    }

    @Test
    @IR(counts = { IRNode.XOR_V_MASK, "= 3",
                   IRNode.VECTOR_MASK_CMP, "= 3" },
        applyIfCPUFeatureOr = { "sve", "true", "avx512", "true", "rvv", "true"  })
    @IR(counts = { IRNode.XOR_V, "= 3",
                   IRNode.VECTOR_MASK_CMP, "= 3" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" })
    @IR(counts = { IRNode.XOR_V, "= 3",
                   IRNode.VECTOR_MASK_CMP, "= 3" },
        applyIfCPUFeatureAnd = { "avx2", "true" })
    public static void testCompareMaskNotDoubleNegative() {
        testCompareMaskNotDouble(D_SPECIES, VectorOperators.EQ, da, db, (m) -> {
            // The vector mask is used multiple times.
            ic[0] = m.trueCount();
            return m.not();
        });
        verifyResultsDouble(D_SPECIES, VectorOperators.EQ, da, db);

        // One of the operands of XOR is not all ones vector.
        testCompareMaskNotDouble(D_SPECIES, VectorOperators.EQ, da, db, (m) -> { return D_SPECIES.maskAll(false).xor(m); });
        verifyResultsDouble(D_SPECIES, VectorOperators.NE, da, db);

        // Double vectors use the LT comparison.
        testCompareMaskNotDouble(D_SPECIES, VectorOperators.LT, da, db, (m) -> { return m.not(); });
        verifyResultsDouble(D_SPECIES, VectorOperators.LT, da, db);
    }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(5000)
                     .addFlags("--add-modules=jdk.incubator.vector")
                     .start();
    }
}
