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
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.LongVector;
import jdk.incubator.vector.ShortVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

/**
 * @test
 * @bug 8301739
 * @key randomness
 * @library /test/lib /
 * @requires vm.cpu.features ~= ".*sve.*"
 * @summary AArch64: Add optimized rules for vector compare with immediate for SVE
 * @modules jdk.incubator.vector
 *
 * @run driver compiler.vectorapi.VectorCompareWithImmTest
 */

public class VectorCompareWithImmTest {
    private static final VectorSpecies<Byte> B_SPECIES = ByteVector.SPECIES_PREFERRED;
    private static final VectorSpecies<Short> S_SPECIES = ShortVector.SPECIES_PREFERRED;
    private static final VectorSpecies<Integer> I_SPECIES = IntVector.SPECIES_PREFERRED;
    private static final VectorSpecies<Long> L_SPECIES = LongVector.SPECIES_PREFERRED;

    private static final int LENGTH = 3000;
    private static final Random RD = Utils.getRandomInstance();

    private static byte[] ba;
    private static boolean[] br;
    private static short[] sa;
    private static boolean[] sr;
    private static int[] ia;
    private static boolean[] ir;
    private static long[] la;
    private static boolean[] lr;

    static {
        ba = new byte[LENGTH];
        sa = new short[LENGTH];
        ia = new int[LENGTH];
        la = new long[LENGTH];

        br = new boolean[LENGTH];
        sr = new boolean[LENGTH];
        ir = new boolean[LENGTH];
        lr = new boolean[LENGTH];

        for (int i = 0; i < LENGTH; i++) {
            ba[i] = (byte) RD.nextInt();
            sa[i] = (short) (RD.nextInt(1000) - 500); // range [-500, 500)
            ia[i] = RD.nextInt(1000) - 500;           // range [-500, 500)
            la[i] = RD.nextLong(1000) - 500;          // range [-500, 500)
        }
    }

    interface ByteOp {
        boolean apply(byte a);
    }

    interface ShortOp {
        boolean apply(Short a);
    }

    interface IntOp {
        boolean apply(int a);
    }

    interface LongOp {
        boolean apply(long a);
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

    @Test
    @IR(counts = { IRNode.VMASK_CMP_IMM_I_SVE, ">= 1" })
    public static void testByteGTInRange() {
        ByteVector av = ByteVector.fromArray(B_SPECIES, ba, 0);
        av.compare(VectorOperators.GT, 12).intoArray(br, 0);
    }

    @Run(test = "testByteGTInRange")
    public static void testByteGTInRange_runner() {
        testByteGTInRange();
        assertArrayEquals(ba, br, (a) -> (a > 12 ? true : false));
    }

    @Test
    @IR(counts = { IRNode.VMASK_CMPU_IMM_I_SVE, ">= 1" })
    public static void testByteUnsignedGTInRange() {
        ByteVector av = ByteVector.fromArray(B_SPECIES, ba, 0);
        av.compare(VectorOperators.UNSIGNED_GT, 64).intoArray(br, 0);
    }

    @Run(test = "testByteUnsignedGTInRange")
    public static void testByteUnsignedGTInRange_runner() {
        testByteUnsignedGTInRange();
        assertArrayEquals(ba, br, (a) -> (Byte.toUnsignedInt(a) > 64 ? true : false));
    }

    @Test
    @IR(failOn = { IRNode.VMASK_CMP_IMM_I_SVE })
    public static void testByteGTOutOfRange() {
        ByteVector av = ByteVector.fromArray(B_SPECIES, ba, 0);
        av.compare(VectorOperators.GT, -91).intoArray(br, 0);
    }

    @Test
    @IR(failOn = { IRNode.VMASK_CMPU_IMM_I_SVE })
    public static void testByteUnsignedGTOutOfRange() {
        ByteVector av = ByteVector.fromArray(B_SPECIES, ba, 0);
        av.compare(VectorOperators.UNSIGNED_GT, -91).intoArray(br, 0);
    }

    @Test
    @IR(counts = { IRNode.VMASK_CMP_IMM_I_SVE, ">= 1" })
    public static void testShortGEInRange() {
        ShortVector av = ShortVector.fromArray(S_SPECIES, sa, 0);
        av.compare(VectorOperators.GE, 5).intoArray(sr, 0);
    }

    @Run(test = "testShortGEInRange")
    public static void testShortGEInRange_runner() {
        testShortGEInRange();
        assertArrayEquals(sa, sr, (a) -> (a >= 5 ? true : false));
    }

    @Test
    @IR(counts = { IRNode.VMASK_CMPU_IMM_I_SVE, ">= 1" })
    public static void testShortUnsignedGEInRange() {
        ShortVector av = ShortVector.fromArray(S_SPECIES, sa, 0);
        av.compare(VectorOperators.UNSIGNED_GE, 56).intoArray(sr, 0);
    }

    @Run(test = "testShortUnsignedGEInRange")
    public static void testShortUnsignedGEInRange_runner() {
        testShortUnsignedGEInRange();
        assertArrayEquals(sa, sr, (a) -> (Short.toUnsignedInt(a) >= 56 ? true : false));
    }

    @Test
    @IR(failOn = { IRNode.VMASK_CMP_IMM_I_SVE })
    public static void testShortGEOutOfRange() {
        ShortVector av = ShortVector.fromArray(S_SPECIES, sa, 0);
        av.compare(VectorOperators.GE, -85).intoArray(sr, 0);
    }

    @Test
    @IR(failOn = { IRNode.VMASK_CMPU_IMM_I_SVE })
    public static void testShortUnsignedGEOutOfRange() {
        ShortVector av = ShortVector.fromArray(S_SPECIES, sa, 0);
        av.compare(VectorOperators.UNSIGNED_GE, -85).intoArray(sr, 0);
    }

    @Test
    @IR(counts = { IRNode.VMASK_CMP_IMM_I_SVE, ">= 1" })
    public static void testIntLTInRange() {
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        av.compare(VectorOperators.LT, 10).intoArray(ir, 0);
    }

    @Run(test = "testIntLTInRange")
    public static void testIntLTInRange_runner() {
        testIntLTInRange();
        assertArrayEquals(ia, ir, (a) -> (a < 10 ? true : false));
    }

    @Test
    @IR(counts = { IRNode.VMASK_CMPU_IMM_I_SVE, ">= 1" })
    public static void testIntUnsignedLTInRange() {
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        av.compare(VectorOperators.UNSIGNED_LT, 101).intoArray(ir, 0);
    }

    @Run(test = "testIntUnsignedLTInRange")
    public static void testIntUnsignedLTInRange_runner() {
        testIntUnsignedLTInRange();
        assertArrayEquals(ia, ir, (a) -> (Integer.compareUnsigned(a, 101) < 0 ? true : false));
    }

    @Test
    @IR(failOn = { IRNode.VMASK_CMP_IMM_I_SVE })
    public static void testIntLTOutOfRange() {
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        av.compare(VectorOperators.LT, -110).intoArray(ir, 0);
    }

    @Test
    @IR(failOn = { IRNode.VMASK_CMPU_IMM_I_SVE })
    public static void testIntUnsignedLTOutOfRange() {
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        av.compare(VectorOperators.UNSIGNED_LT, -110).intoArray(ir, 0);
    }

    @Test
    @IR(counts = { IRNode.VMASK_CMP_IMM_L_SVE, ">= 1" })
    public static void testLongLEInRange() {
        LongVector av = LongVector.fromArray(L_SPECIES, la, 0);
        av.compare(VectorOperators.LE, 9).intoArray(lr, 0);
    }

    @Run(test = "testLongLEInRange")
    public static void testLongLEInRange_runner() {
        testLongLEInRange();
        assertArrayEquals(la, lr, (a) -> (a <= 9 ? true : false));
    }

    @Test
    @IR(counts = { IRNode.VMASK_CMPU_IMM_L_SVE, ">= 1" })
    public static void testLongUnsignedLEInRange() {
        LongVector av = LongVector.fromArray(L_SPECIES, la, 0);
        av.compare(VectorOperators.UNSIGNED_LE, 95).intoArray(lr, 0);
    }

    @Run(test = "testLongUnsignedLEInRange")
    public static void testLongUnsignedLEInRange_runner() {
        testLongUnsignedLEInRange();
        assertArrayEquals(la, lr, (a) -> (Long.compareUnsigned(a, 95) <= 0 ? true : false));
    }

    @Test
    @IR(failOn = { IRNode.VMASK_CMP_IMM_L_SVE })
    public static void testLongLEOutOfRange() {
        LongVector av = LongVector.fromArray(L_SPECIES, la, 0);
        av.compare(VectorOperators.LE, -99).intoArray(lr, 0);
    }

    @Test
    @IR(failOn = { IRNode.VMASK_CMPU_IMM_L_SVE })
    public static void testLongUnsignedLEOutOfRange() {
        LongVector av = LongVector.fromArray(L_SPECIES, la, 0);
        av.compare(VectorOperators.UNSIGNED_LE, -99).intoArray(lr, 0);
    }

    @Test
    @IR(counts = { IRNode.VMASK_CMP_IMM_I_SVE, ">= 1" })
    public static void testIntEQInRange() {
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        av.compare(VectorOperators.EQ, 8).intoArray(ir, 0);
    }

    @Run(test = "testIntEQInRange")
    public static void testIntEQInRange_runner() {
        testIntEQInRange();
        assertArrayEquals(ia, ir, (a) -> (a == 8 ? true : false));
    }

    @Test
    @IR(failOn = {IRNode.VMASK_CMP_IMM_I_SVE})
    public static void testIntEQOutOfRange() {
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        av.compare(VectorOperators.EQ, 19).intoArray(ir, 0);
    }

    @Test
    @IR(counts = { IRNode.VMASK_CMP_IMM_L_SVE, ">= 1" })
    public static void testLongNEInRange() {
        LongVector av = LongVector.fromArray(L_SPECIES, la, 0);
        av.compare(VectorOperators.NE, 7).intoArray(lr, 0);
    }

    @Run(test = "testLongNEInRange")
    public static void testLongNEInRange_runner() {
        testLongNEInRange();
        assertArrayEquals(la, lr, (a) -> (a != 7 ? true : false));
    }

    @Test
    @IR(failOn = { IRNode.VMASK_CMP_IMM_L_SVE })
    public static void testLongNEOutOfRange() {
        LongVector av = LongVector.fromArray(L_SPECIES, la, 0);
        av.compare(VectorOperators.NE, 17).intoArray(lr, 0);
    }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(10000)
                     .addFlags("--add-modules=jdk.incubator.vector")
                     .addFlags("-XX:UseSVE=1")
                     .start();
    }
}
