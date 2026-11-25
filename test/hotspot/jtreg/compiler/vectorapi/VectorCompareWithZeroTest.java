/*
 * Copyright (c) 2023, Arm Limited. All rights reserved.
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

import java.util.Random;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.LongVector;
import jdk.incubator.vector.ShortVector;
import jdk.incubator.vector.VectorSpecies;
import jdk.incubator.vector.VectorOperators;

import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

/**
 * @test
 * @bug 8297753
 * @key randomness
 * @library /test/lib /
 * @requires os.arch=="aarch64"
 * @summary Add optimized rules for vector compare with zero on NEON
 * @modules jdk.incubator.vector
 *
 * @run driver compiler.vectorapi.VectorCompareWithZeroTest
 */

public class VectorCompareWithZeroTest {
    private static final VectorSpecies<Byte> B_SPECIES = ByteVector.SPECIES_PREFERRED;
    private static final VectorSpecies<Short> S_SPECIES = ShortVector.SPECIES_PREFERRED;
    private static final VectorSpecies<Integer> I_SPECIES = IntVector.SPECIES_PREFERRED;
    private static final VectorSpecies<Long> L_SPECIES = LongVector.SPECIES_PREFERRED;
    private static final VectorSpecies<Float> F_SPECIES = FloatVector.SPECIES_PREFERRED;
    private static final VectorSpecies<Double> D_SPECIES = DoubleVector.SPECIES_PREFERRED;
    private static final int LENGTH = 1024;
    private static final Random RD = Utils.getRandomInstance();
    private static byte[] ba;
    private static boolean[] br;
    private static short[] sa;
    private static boolean[] sr;
    private static int[] ia;
    private static boolean[] ir;
    private static long[] la;
    private static boolean[] lr;
    private static float[] fa;
    private static boolean[] fr;
    private static double[] da;
    private static boolean[] dr;

    static {
        ba = new byte[LENGTH];
        sa = new short[LENGTH];
        ia = new int[LENGTH];
        la = new long[LENGTH];
        fa = new float[LENGTH];
        da = new double[LENGTH];

        br = new boolean[LENGTH];
        sr = new boolean[LENGTH];
        ir = new boolean[LENGTH];
        lr = new boolean[LENGTH];
        fr = new boolean[LENGTH];
        dr = new boolean[LENGTH];

        for (int i = 0; i < LENGTH; i++) {
            ba[i] = (byte) RD.nextInt(25);
            sa[i] = (short) RD.nextInt(25);
            ia[i] = RD.nextInt(25);
            la[i] = RD.nextLong(25);
            fa[i] = RD.nextFloat(25.0F);
            da[i] = RD.nextDouble(25.0);
        }
    }

    interface ByteOp {
        boolean apply(byte a);
    }

    interface ShortOp {
        boolean apply(short a);
    }

    interface IntOp {
        boolean apply(int a);
    }

    interface LongOp {
        boolean apply(long a);
    }

    interface FloatOp {
        boolean apply(float a);
    }

    interface DoubleOp {
        boolean apply(double a);
    }

    private static void assertArrayEquals(byte[] a, boolean[] r, ByteOp f) {
        for (int i = 0; i < B_SPECIES.length(); i++) {
            Asserts.assertEquals(f.apply(a[i]), r[i]);
        }
    }

    private static void assertArrayEquals(short[] a, boolean[] r, ShortOp f) {
        for (int i = 0; i < S_SPECIES.length(); i++) {
            Asserts.assertEquals(f.apply(a[i]), r[i]);
        }
    }

    private static void assertArrayEquals(int[] a, boolean[] r, IntOp f) {
        for (int i = 0; i < I_SPECIES.length(); i++) {
            Asserts.assertEquals(f.apply(a[i]), r[i]);
        }
    }

    private static void assertArrayEquals(long[] a, boolean[] r, LongOp f) {
        for (int i = 0; i < L_SPECIES.length(); i++) {
            Asserts.assertEquals(f.apply(a[i]), r[i]);
        }
    }

    private static void assertArrayEquals(float[] a, boolean[] r, FloatOp f) {
        for (int i = 0; i < F_SPECIES.length(); i++) {
            Asserts.assertEquals(f.apply(a[i]), r[i]);
        }
    }

    private static void assertArrayEquals(double[] a, boolean[] r, DoubleOp f) {
        for (int i = 0; i < D_SPECIES.length(); i++) {
            Asserts.assertEquals(f.apply(a[i]), r[i]);
        }
    }

    @Test
    @IR(counts = { IRNode.VMASK_CMP_ZERO_I_NEON, ">= 1" })
    public static void testByteVectorEqualToZero() {
        ByteVector av = ByteVector.fromArray(B_SPECIES, ba, 0);
        av.compare(VectorOperators.EQ, 0).intoArray(br, 0);
    }

    @Run(test = "testByteVectorEqualToZero")
    public static void testByteVectorEqualToZero_runner() {
        testByteVectorEqualToZero();
        assertArrayEquals(ba, br, (a) -> (a == (byte) 0 ? true : false));
    }

    @Test
    @IR(counts = { IRNode.VMASK_CMP_ZERO_I_NEON, ">= 1" })
    public static void testShortVectorNotEqualToZero() {
        ShortVector av = ShortVector.fromArray(S_SPECIES, sa, 0);
        av.compare(VectorOperators.NE, 0).intoArray(sr, 0);
    }

    @Run(test = "testShortVectorNotEqualToZero")
    public static void testShortVectorNotEqualToZero_runner() {
        testShortVectorNotEqualToZero();
        assertArrayEquals(sa, sr, (a) -> (a != (short) 0 ? true : false));
    }

    @Test
    @IR(counts = { IRNode.VMASK_CMP_ZERO_I_NEON, ">= 1" })
    public static void testIntVectorGreaterEqualToZero() {
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        av.compare(VectorOperators.GE, 0).intoArray(ir, 0);
    }

    @Run(test = "testIntVectorGreaterEqualToZero")
    public static void testIntVectorGreaterEqualToZero_runner() {
        testIntVectorGreaterEqualToZero();
        assertArrayEquals(ia, ir, (a) -> (a >= 0 ? true : false));
    }

    @Test
    @IR(counts = { IRNode.VMASK_CMP_ZERO_L_NEON, ">= 1" })
    public static void testLongVectorGreaterThanZero() {
        LongVector av = LongVector.fromArray(L_SPECIES, la, 0);
        av.compare(VectorOperators.GT, 0).intoArray(lr, 0);
    }

    @Run(test = "testLongVectorGreaterThanZero")
    public static void testLongVectorGreaterThanZero_runner() {
        testLongVectorGreaterThanZero();
        assertArrayEquals(la, lr, (a) -> (a > 0 ? true : false));
    }

    @Test
    @IR(counts = { IRNode.VMASK_CMP_ZERO_F_NEON, ">= 1" })
    public static void testFloatVectorLessEqualToZero() {
        FloatVector av = FloatVector.fromArray(F_SPECIES, fa, 0);
        av.compare(VectorOperators.LE, 0).intoArray(fr, 0);
    }

    @Run(test = "testFloatVectorLessEqualToZero")
    public static void testFloatVectorLessEqualToZero_runner() {
        testFloatVectorLessEqualToZero();
        assertArrayEquals(fa, fr, (a) -> (a <= 0.0F ? true : false));
    }

    @Test
    @IR(counts = { IRNode.VMASK_CMP_ZERO_D_NEON, ">= 1" })
    public static void testDoubleVectorLessThanZero() {
        DoubleVector av = DoubleVector.fromArray(D_SPECIES, da, 0);
        av.compare(VectorOperators.LT, 0).intoArray(dr, 0);
    }

    @Run(test = "testDoubleVectorLessThanZero")
    public static void testDoubleVectorLessThanZero_runner() {
        testDoubleVectorLessThanZero();
        assertArrayEquals(da, dr, (a) -> (a < 0.0 ? true : false));
    }

    @Test
    @IR(failOn = { IRNode.VMASK_CMP_ZERO_I_NEON })
    public static void testIntVectorUnsignedCondition() {
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        av.compare(VectorOperators.UGT, 0).intoArray(ir, 0);
    }

    @Test
    @IR(failOn = { IRNode.VMASK_CMP_ZERO_L_NEON })
    public static void testLongVectorUnsignedCondition() {
        LongVector av = LongVector.fromArray(L_SPECIES, la, 0);
        av.compare(VectorOperators.UGE, 0).intoArray(lr, 0);
    }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(10000)
                     .addFlags("--add-modules=jdk.incubator.vector")
                     .addFlags("-XX:UseSVE=0")
                     .start();
    }
}
