/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
import jdk.incubator.vector.*;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

import java.util.Arrays;
import java.util.Random;

/**
 * @test
 * @bug 8341102
 * @library /test/lib /
 * @summary Constant fold constant vectors into ConV nodes
 * @modules jdk.incubator.vector
 *
 * @run driver compiler.vectorapi.TestConVNode
 */
public class TestConVNode {
    private static final ByteVector VB;
    private static final ShortVector VS;
    private static final IntVector VI;
    private static final LongVector VL;
    private static final FloatVector VF;
    private static final DoubleVector VD;

    private static final byte[] AB;
    private static final short[] AS;
    private static final int[] AI;
    private static final long[] AL;
    private static final float[] AF;
    private static final double[] AD;

    private static final IntVector V1;
    private static final IntVector V2;
    private static final IntVector V4;
    private static final IntVector V8;

    private static final int[] A1;
    private static final int[] A2;
    private static final int[] A4;
    private static final int[] A8;

    private static final VectorMask<Byte> M;
    private static final boolean[] AZ;

    static {
        Random r = Utils.getRandomInstance();
        AB = new byte[ByteVector.SPECIES_PREFERRED.length()];
        for (int i = 0; i < AB.length; i++) {
            AB[i] = (byte) r.nextInt();
        }
        VB = ByteVector.fromArray(ByteVector.SPECIES_PREFERRED, AB, 0);
        AS = new short[ShortVector.SPECIES_PREFERRED.length()];
        for (int i = 0; i < AS.length; i++) {
            AS[i] = (short) r.nextInt();
        }
        VS = ShortVector.fromArray(ShortVector.SPECIES_PREFERRED, AS, 0);
        AI = new int[IntVector.SPECIES_PREFERRED.length()];
        for (int i = 0; i < AI.length; i++) {
            AI[i] = r.nextInt();
        }
        VI = IntVector.fromArray(IntVector.SPECIES_PREFERRED, AI, 0);
        AL = new long[LongVector.SPECIES_PREFERRED.length()];
        for (int i = 0; i < AL.length; i++) {
            AL[i] = r.nextLong();
        }
        VL = LongVector.fromArray(LongVector.SPECIES_PREFERRED, AL, 0);
        AF = new float[FloatVector.SPECIES_PREFERRED.length()];
        for (int i = 0; i < AF.length; i++) {
            AF[i] = r.nextFloat();
        }
        VF = FloatVector.fromArray(FloatVector.SPECIES_PREFERRED, AF, 0);
        AD = new double[DoubleVector.SPECIES_PREFERRED.length()];
        for (int i = 0; i < AD.length; i++) {
            AD[i] = r.nextDouble();
        }
        VD = DoubleVector.fromArray(DoubleVector.SPECIES_PREFERRED, AD, 0);

        A1 = new int[IntVector.SPECIES_PREFERRED.length()];
        A2 = new int[IntVector.SPECIES_PREFERRED.length()];
        A4 = new int[IntVector.SPECIES_PREFERRED.length()];
        A8 = new int[IntVector.SPECIES_PREFERRED.length()];
        A1[0] = r.nextInt();
        for (int i = 1; i < A1.length; i++) {
            A1[i] = A1[0];
        }
        for (int i = 0; i < A2.length; i++) {
            if (i < 2) {
                A2[i] = r.nextInt();
            } else {
                A2[i] = A2[i - 2];
            }
        }
        for (int i = 0; i < A4.length; i++) {
            if (i < 4) {
                A4[i] = r.nextInt();
            } else {
                A4[i] = A4[i - 4];
            }
        }
        for (int i = 0; i < A8.length; i++) {
            if (i < 8) {
                A8[i] = r.nextInt();
            } else {
                A8[i] = A8[i - 8];
            }
        }
        V1 = IntVector.fromArray(IntVector.SPECIES_PREFERRED, A1, 0);
        V2 = IntVector.fromArray(IntVector.SPECIES_PREFERRED, A2, 0);
        V4 = IntVector.fromArray(IntVector.SPECIES_PREFERRED, A4, 0);
        V8 = IntVector.fromArray(IntVector.SPECIES_PREFERRED, A8, 0);

        AZ = new boolean[ByteVector.SPECIES_PREFERRED.length()];
        for (int i = 0; i < AZ.length; i++) {
            AZ[i] = r.nextBoolean();
        }
        M = VectorMask.fromArray(ByteVector.SPECIES_PREFERRED, AZ, 0);
    }

    @Run(test = {"testByteVector", "testShortVector", "testIntVector",
            "testLongVector", "testFloatVector", "testDoubleVector",
            "testReplicate1IntVector", "testReplicate2IntVector",
            "testReplicate4IntVector", "testReplicate8IntVector",
            "testByteMask"})
    public void run() {
        byte[] rb = new byte[VB.length()];
        testByteVector(rb);
        Asserts.assertTrue(Arrays.equals(AB, rb));
        short[] rs = new short[VS.length()];
        testShortVector(rs);
        Asserts.assertTrue(Arrays.equals(AS, rs));
        int[] ri = new int[VI.length()];
        testIntVector(ri);
        Asserts.assertTrue(Arrays.equals(AI, ri));
        long[] rl = new long[VL.length()];
        testLongVector(rl);
        Asserts.assertTrue(Arrays.equals(AL, rl));
        float[] rf = new float[VF.length()];
        testFloatVector(rf);
        Asserts.assertEquals(AF.length, rf.length);
        for (int i = 0; i < rf.length; i++) {
            Asserts.assertEquals(Float.floatToRawIntBits(AF[i]), Float.floatToRawIntBits(rf[i]));
        }
        double[] rd = new double[VD.length()];
        testDoubleVector(rd);
        Asserts.assertEquals(AD.length, rd.length);
        for (int i = 0; i < rd.length; i++) {
            Asserts.assertEquals(Double.doubleToRawLongBits(AD[i]), Double.doubleToRawLongBits(rd[i]));
        }
        int[] r1 = new int[V1.length()];
        testReplicate1IntVector(r1);
        Asserts.assertTrue(Arrays.equals(A1, r1));
        int[] r2 = new int[V2.length()];
        testReplicate2IntVector(r2);
        Asserts.assertTrue(Arrays.equals(A2, r2));
        int[] r4 = new int[V4.length()];
        testReplicate4IntVector(r4);
        Asserts.assertTrue(Arrays.equals(A4, r4));
        int[] r8 = new int[V8.length()];
        testReplicate8IntVector(r8);
        Asserts.assertTrue(Arrays.equals(A8, r8));
        boolean[] rz = new boolean[M.length()];
        testByteMask(rz);
        Asserts.assertTrue(Arrays.equals(AZ, rz));
    }

    @Test
    @IR(counts = {IRNode.CON_V, "1"}, applyIfPlatform = {"x64", "true"})
    public void testByteVector(byte[] r) {
        VB.intoArray(r, 0);
    }

    @Test
    @IR(counts = {IRNode.CON_V, "1"}, applyIfPlatform = {"x64", "true"})
    public void testShortVector(short[] r) {
        VS.intoArray(r, 0);
    }

    @Test
    @IR(counts = {IRNode.CON_V, "1"}, applyIfPlatform = {"x64", "true"})
    public void testIntVector(int[] r) {
        VI.intoArray(r, 0);
    }

    @Test
    @IR(counts = {IRNode.CON_V, "1"}, applyIfPlatform = {"x64", "true"})
    public void testLongVector(long[] r) {
        VL.intoArray(r, 0);
    }

    @Test
    @IR(counts = {IRNode.CON_V, "1"}, applyIfPlatform = {"x64", "true"})
    public void testFloatVector(float[] r) {
        VF.intoArray(r, 0);
    }

    @Test
    @IR(counts = {IRNode.CON_V, "1"}, applyIfPlatform = {"x64", "true"})
    public void testDoubleVector(double[] r) {
        VD.intoArray(r, 0);
    }

    @Test
    @IR(counts = {IRNode.CON_V, "1"}, applyIfPlatform = {"x64", "true"})
    public void testReplicate1IntVector(int[] r) {
        V1.intoArray(r, 0);
    }

    @Test
    @IR(counts = {IRNode.CON_V, "1"}, applyIfPlatform = {"x64", "true"})
    public void testReplicate2IntVector(int[] r) {
        V2.intoArray(r, 0);
    }

    @Test
    @IR(counts = {IRNode.CON_V, "1"}, applyIfPlatform = {"x64", "true"})
    public void testReplicate4IntVector(int[] r) {
        V4.intoArray(r, 0);
    }

    @Test
    @IR(counts = {IRNode.CON_V, "1"}, applyIfPlatform = {"x64", "true"})
    public void testReplicate8IntVector(int[] r) {
        V8.intoArray(r, 0);
    }

    @Test
    @IR(counts = {IRNode.CON_V, "1"}, applyIfPlatform = {"x64", "true"})
    public void testByteMask(boolean[] r) {
        M.intoArray(r, 0);
    }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(10000)
                .addFlags("--add-modules=jdk.incubator.vector")
                .start();
    }
}
