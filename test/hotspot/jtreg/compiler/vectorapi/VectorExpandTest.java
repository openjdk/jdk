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

/**
 * @test
 * @bug 8363989
 * @key randomness
 * @library /test/lib /
 * @summary AArch64: Add missing backend support of VectorAPI expand operation
 * @modules jdk.incubator.vector
 *
 * @run driver compiler.vectorapi.VectorExpandTest
 */

public class VectorExpandTest {
    static final VectorSpecies<Byte> B_SPECIES = ByteVector.SPECIES_MAX;
    static final VectorSpecies<Short> S_SPECIES = ShortVector.SPECIES_MAX;
    static final VectorSpecies<Integer> I_SPECIES = IntVector.SPECIES_MAX;
    static final VectorSpecies<Float> F_SPECIES = FloatVector.SPECIES_MAX;
    static final VectorSpecies<Long> L_SPECIES = LongVector.SPECIES_MAX;
    static final VectorSpecies<Double> D_SPECIES = DoubleVector.SPECIES_MAX;
    static final int LENGTH = 512;
    static final Generators RD = Generators.G;
    static byte[] ba, bb;
    static short[] sa, sb;
    static int[] ia, ib;
    static long[] la, lb;
    static float[] fa, fb;
    static double[] da, db;
    static boolean[] ma;

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
        da = new double[LENGTH];
        db = new double[LENGTH];
        ma = new boolean[LENGTH];

        Generator<Integer> iGen = RD.ints();
        Generator<Long> lGen = RD.longs();
        Generator<Float> fGen = RD.floats();
        Generator<Double> dGen = RD.doubles();

        for (int i = 0; i < LENGTH; i++) {
            ba[i] = iGen.next().byteValue();
            sa[i] = iGen.next().shortValue();
            ma[i] = iGen.next() % 2 == 0;
        }
        RD.fill(iGen, ia);
        RD.fill(lGen, la);
        RD.fill(fGen, fa);
        RD.fill(dGen, da);
    }

    @Test
    @IR(counts = { IRNode.EXPAND_VB, "= 1" }, applyIfCPUFeatureOr = { "asimd", "true", "rvv", "true" })
    public static void testVectorExpandByte(ByteVector av, VectorMask<Byte> m) {
        av.expand(m).intoArray(bb, 0);
    }

    @Run(test = "testVectorExpandByte")
    public static void testVectorExpandByte_runner() {
        ByteVector av = ByteVector.fromArray(B_SPECIES, ba, 0);
        VectorMask<Byte> m = VectorMask.fromArray(B_SPECIES, ma, 0);
        testVectorExpandByte(av, m);
        int index = 0;
        for (int i = 0; i < m.length(); i++) {
            Asserts.assertEquals(m.laneIsSet(i) ? ba[index++] : (byte)0, bb[i]);
        }
    }

    @Test
    @IR(counts = { IRNode.EXPAND_VS, "= 1" }, applyIfCPUFeatureOr = { "asimd", "true", "rvv", "true" })
    public static void testVectorExpandShort(ShortVector av, VectorMask<Short> m) {
        av.expand(m).intoArray(sb, 0);
    }

    @Run(test = "testVectorExpandShort")
    public static void testVectorExpandShort_runner() {
        ShortVector av = ShortVector.fromArray(S_SPECIES, sa, 0);
        VectorMask<Short> m = VectorMask.fromArray(S_SPECIES, ma, 0);
        testVectorExpandShort(av, m);
        int index = 0;
        for (int i = 0; i < m.length(); i++) {
            Asserts.assertEquals(m.laneIsSet(i) ? sa[index++] : (short)0, sb[i]);
        }
    }

    @Test
    @IR(counts = { IRNode.EXPAND_VI, "= 1" }, applyIfCPUFeatureOr = { "asimd", "true", "rvv", "true" })
    public static void testVectorExpandInt(IntVector av, VectorMask<Integer> m) {
        av.expand(m).intoArray(ib, 0);
    }

    @Run(test = "testVectorExpandInt")
    public static void testVectorExpandInt_runner() {
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        VectorMask<Integer> m = VectorMask.fromArray(I_SPECIES, ma, 0);
        testVectorExpandInt(av, m);
        int index = 0;
        for (int i = 0; i < m.length(); i++) {
            Asserts.assertEquals(m.laneIsSet(i) ? ia[index++] : (int)0, ib[i]);
        }
    }

    @Test
    @IR(counts = { IRNode.EXPAND_VL, "= 1" }, applyIfCPUFeatureOr = { "asimd", "true", "rvv", "true" })
    public static void testVectorExpandLong(LongVector av, VectorMask<Long> m) {
        av.expand(m).intoArray(lb, 0);
    }

    @Run(test = "testVectorExpandLong")
    public static void testVectorExpandLong_runner() {
        LongVector av = LongVector.fromArray(L_SPECIES, la, 0);
        VectorMask<Long> m = VectorMask.fromArray(L_SPECIES, ma, 0);
        testVectorExpandLong(av, m);
        int index = 0;
        for (int i = 0; i < m.length(); i++) {
            Asserts.assertEquals(m.laneIsSet(i) ? la[index++] : (long)0, lb[i]);
        }
    }

    @Test
    @IR(counts = { IRNode.EXPAND_VF, "= 1" }, applyIfCPUFeatureOr = { "asimd", "true", "rvv", "true" })
    public static void testVectorExpandFloat(FloatVector av, VectorMask<Float> m) {
        av.expand(m).intoArray(fb, 0);
    }

    @Run(test = "testVectorExpandFloat")
    public static void testVectorExpandFloat_runner() {
        FloatVector av = FloatVector.fromArray(F_SPECIES, fa, 0);
        VectorMask<Float> m = VectorMask.fromArray(F_SPECIES, ma, 0);
        testVectorExpandFloat(av, m);
        int index = 0;
        for (int i = 0; i < m.length(); i++) {
            Asserts.assertEquals(m.laneIsSet(i) ? fa[index++] : (float)0, fb[i]);
        }
    }

    @Test
    @IR(counts = { IRNode.EXPAND_VD, "= 1" }, applyIfCPUFeatureOr = { "asimd", "true", "rvv", "true" })
    public static void testVectorExpandDouble(DoubleVector av, VectorMask<Double> m) {
        av.expand(m).intoArray(db, 0);
    }

    @Run(test = "testVectorExpandDouble")
    public static void testVectorExpandDouble_runner() {
        DoubleVector av = DoubleVector.fromArray(D_SPECIES, da, 0);
        VectorMask<Double> m = VectorMask.fromArray(D_SPECIES, ma, 0);
        testVectorExpandDouble(av, m);
        int index = 0;
        for (int i = 0; i < m.length(); i++) {
            Asserts.assertEquals(m.laneIsSet(i) ? da[index++] : (double)0, db[i]);
        }
    }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(10000)
                     .addFlags("--add-modules=jdk.incubator.vector")
                     .start();
    }
}
