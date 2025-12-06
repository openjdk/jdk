/*
 * Copyright (c) 2025, Rivos Inc. All rights reserved.
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

package compiler.c2.irTests;

import compiler.lib.ir_framework.*;
import java.util.Random;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

/*
 * @test
 * @summary Test conditional move + compare object.
 * @requires vm.simpleArch == "riscv64"
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestScalarConditionalMoveCmpObj
 */

public class TestScalarConditionalMoveCmpObj {
    final private static int SIZE = 1024;
    private static final Random RANDOM = Utils.getRandomInstance();

    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:+UseCMoveUnconditionally", "-XX:-UseVectorCmov",
                                   "-XX:+UnlockExperimentalVMOptions", "-XX:-UseCompactObjectHeaders", "-XX:+UseCompressedOops");
        TestFramework.runWithFlags("-XX:+UseCMoveUnconditionally", "-XX:-UseVectorCmov",
                                   "-XX:+UnlockExperimentalVMOptions", "-XX:-UseCompactObjectHeaders", "-XX:-UseCompressedOops");
        TestFramework.runWithFlags("-XX:+UseCMoveUnconditionally", "-XX:-UseVectorCmov",
                                   "-XX:+UnlockExperimentalVMOptions", "-XX:+UseCompactObjectHeaders", "-XX:+UseCompressedOops");
        TestFramework.runWithFlags("-XX:+UseCMoveUnconditionally", "-XX:-UseVectorCmov",
                                   "-XX:+UnlockExperimentalVMOptions", "-XX:+UseCompactObjectHeaders", "-XX:-UseCompressedOops");
    }

    // Object comparison
    //    O for I
    private int cmoveOEQforI(Object a, Object b, int c, int d) {
        return (a == b) ? c : d;
    }

    private int cmoveONEforI(Object a, Object b, int c, int d) {
        return (a != b) ? c : d;
    }

    //    O for L
    private long cmoveOEQforL(Object a, Object b, long c, long d) {
        return (a == b) ? c : d;
    }

    private long cmoveONEforL(Object a, Object b, long c, long d) {
        return (a != b) ? c : d;
    }

    //    O for F
    private float cmoveOEQforF(Object a, Object b, float c, float d) {
        return (a == b) ? c : d;
    }

    private float cmoveONEforF(Object a, Object b, float c, float d) {
        return (a != b) ? c : d;
    }

    //    O for D
    private double cmoveOEQforD(Object a, Object b, double c, double d) {
        return (a == b) ? c : d;
    }

    private double cmoveONEforD(Object a, Object b, double c, double d) {
        return (a != b) ? c : d;
    }

    // Tests shows CMoveI is generated, so let @IR verify CMOVE_I.
    //
    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    @IR(counts = {IRNode.CMOVE_I, ">0", IRNode.CMP_P, ">0"},
        applyIf = {"UseCompressedOops", "false"})
    @IR(counts = {IRNode.CMOVE_I, ">0", IRNode.CMP_N, ">0"},
        applyIf = {"UseCompressedOops", "true"})
    private static void testCMoveOEQforI(Object[] a, Object[] b, int[] c, int[] d, int[] r, int[] r2) {
        for (int i = 0; i < a.length; i++) {
            int cc = c[i];
            int dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] == b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    @IR(counts = {IRNode.CMOVE_I, ">0", IRNode.CMP_P, ">0"},
        applyIf = {"UseCompressedOops", "false"})
    @IR(counts = {IRNode.CMOVE_I, ">0", IRNode.CMP_N, ">0"},
        applyIf = {"UseCompressedOops", "true"})
    private static void testCMoveONEforI(Object[] a, Object[] b, int[] c, int[] d, int[] r, int[] r2) {
        for (int i = 0; i < a.length; i++) {
            int cc = c[i];
            int dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] != b[i]) ? cc : dd;
        }
    }

    // So far, CMoveL is not guaranteed to be generated, so @IR not verify CMOVE_L.
    // TODO: enable CMOVE_L verification when it's guaranteed to generate CMOVE_L.
    //
    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // @IR(counts = {IRNode.CMOVE_L, ">0", IRNode.CMP_P, ">0"},
    //     applyIf = {"UseCompressedOops", "false"})
    // @IR(counts = {IRNode.CMOVE_L, ">0", IRNode.CMP_N, ">0"},
    //     applyIf = {"UseCompressedOops", "true"})
    private static void testCMoveOEQforL(Object[] a, Object[] b, long[] c, long[] d, long[] r, long[] r2) {
        for (int i = 0; i < a.length; i++) {
            long cc = c[i];
            long dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] == b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // @IR(counts = {IRNode.CMOVE_L, ">0", IRNode.CMP_P, ">0"},
    //     applyIf = {"UseCompressedOops", "false"})
    // @IR(counts = {IRNode.CMOVE_L, ">0", IRNode.CMP_N, ">0"},
    //     applyIf = {"UseCompressedOops", "true"})
    private static void testCMoveONEforL(Object[] a, Object[] b, long[] c, long[] d, long[] r, long[] r2) {
        for (int i = 0; i < a.length; i++) {
            long cc = c[i];
            long dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] != b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    @IR(counts = {IRNode.CMOVE_F, ">0", IRNode.CMP_P, ">0"},
        applyIf = {"UseCompressedOops", "false"})
    @IR(counts = {IRNode.CMOVE_F, ">0", IRNode.CMP_N, ">0"},
        applyIf = {"UseCompressedOops", "true"})
    private static void testCMoveOEQforF(Object[] a, Object[] b, float[] c, float[] d, float[] r, float[] r2) {
        for (int i = 0; i < a.length; i++) {
            float cc = c[i];
            float dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] == b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    @IR(counts = {IRNode.CMOVE_F, ">0", IRNode.CMP_P, ">0"},
        applyIf = {"UseCompressedOops", "false"})
    @IR(counts = {IRNode.CMOVE_F, ">0", IRNode.CMP_N, ">0"},
        applyIf = {"UseCompressedOops", "true"})
    private static void testCMoveONEforF(Object[] a, Object[] b, float[] c, float[] d, float[] r, float[] r2) {
        for (int i = 0; i < a.length; i++) {
            float cc = c[i];
            float dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] != b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    @IR(counts = {IRNode.CMOVE_D, ">0", IRNode.CMP_P, ">0"},
        applyIf = {"UseCompressedOops", "false"})
    @IR(counts = {IRNode.CMOVE_D, ">0", IRNode.CMP_N, ">0"},
        applyIf = {"UseCompressedOops", "true"})
    private static void testCMoveOEQforD(Object[] a, Object[] b, double[] c, double[] d, double[] r, double[] r2) {
        for (int i = 0; i < a.length; i++) {
            double cc = c[i];
            double dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] == b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    @IR(counts = {IRNode.CMOVE_D, ">0", IRNode.CMP_P, ">0"},
        applyIf = {"UseCompressedOops", "false"})
    @IR(counts = {IRNode.CMOVE_D, ">0", IRNode.CMP_N, ">0"},
        applyIf = {"UseCompressedOops", "true"})
    private static void testCMoveONEforD(Object[] a, Object[] b, double[] c, double[] d, double[] r, double[] r2) {
        for (int i = 0; i < a.length; i++) {
            double cc = c[i];
            double dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] != b[i]) ? cc : dd;
        }
    }

    @Warmup(0)
    @Run(test = {// Object
                 "testCMoveOEQforI",
                 "testCMoveONEforI",
                 "testCMoveOEQforL",
                 "testCMoveONEforL",
                 "testCMoveOEQforF",
                 "testCMoveONEforF",
                 "testCMoveOEQforD",
                 "testCMoveONEforD",
                })
    private void testCMove_runner_two() {
        Object[] aO = new Object[SIZE];
        Object[] bO = new Object[SIZE];
        int[] cI = new int[SIZE];
        int[] dI = new int[SIZE];
        int[] rI = new int[SIZE];
        long[] cL = new long[SIZE];
        long[] dL = new long[SIZE];
        long[] rL = new long[SIZE];
        float[] cF = new float[SIZE];
        float[] dF = new float[SIZE];
        float[] rF = new float[SIZE];
        double[] cD = new double[SIZE];
        double[] dD = new double[SIZE];
        double[] rD = new double[SIZE];

        init(aO);
        shuffle(aO, bO);
        init(cL);
        init(dL);
        init(cF);
        init(dF);
        init(cD);
        init(dD);

        testCMoveOEQforI(aO, bO, cI, dI, rI, rI);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rI[i], cmoveOEQforI(aO[i], bO[i], cI[i], dI[i]));
        }

        testCMoveONEforI(aO, bO, cI, dI, rI, rI);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rI[i], cmoveONEforI(aO[i], bO[i], cI[i], dI[i]));
        }

        testCMoveOEQforL(aO, bO, cL, dL, rL, rL);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rL[i], cmoveOEQforL(aO[i], bO[i], cL[i], dL[i]));
        }

        testCMoveONEforL(aO, bO, cL, dL, rL, rL);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rL[i], cmoveONEforL(aO[i], bO[i], cL[i], dL[i]));
        }

        testCMoveOEQforF(aO, bO, cF, dF, rF, rF);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rF[i], cmoveOEQforF(aO[i], bO[i], cF[i], dF[i]));
        }

        testCMoveONEforF(aO, bO, cF, dF, rF, rF);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rF[i], cmoveONEforF(aO[i], bO[i], cF[i], dF[i]));
        }

        testCMoveOEQforD(aO, bO, cD, dD, rD, rD);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rD[i], cmoveOEQforD(aO[i], bO[i], cD[i], dD[i]));
        }

        testCMoveONEforD(aO, bO, cD, dD, rD, rD);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rD[i], cmoveONEforD(aO[i], bO[i], cD[i], dD[i]));
        }

    }

    private static void init(Object[] a) {
        for (int i = 0; i < SIZE; i++) {
            a[i] = new Object();
        }
    }

    private static void shuffle(Object[] a, Object[] b) {
        for (int i = 0; i < a.length; i++) {
            b[i] = a[i];
        }
        Random rand = new Random();
        for (int i = 0; i < SIZE; i++) {
            if (rand.nextInt(5) == 0) {
                Object t = b[i];
                b[i] = b[SIZE-1-i];
                b[SIZE-1-i] = t;
            }
        }
    }

    private static void init(int[] a) {
        for (int i = 0; i < SIZE; i++) {
            a[i] = RANDOM.nextInt();
        }
    }

    private static void init(long[] a) {
        for (int i = 0; i < SIZE; i++) {
            a[i] = RANDOM.nextLong();
        }
    }

    private static void init(float[] a) {
        for (int i = 0; i < SIZE; i++) {
            a[i] = switch(RANDOM.nextInt() % 20) {
                case 0  -> Float.NaN;
                case 1  -> 0;
                case 2  -> 1;
                case 3  -> Float.POSITIVE_INFINITY;
                case 4  -> Float.NEGATIVE_INFINITY;
                case 5  -> Float.MAX_VALUE;
                case 6  -> Float.MIN_VALUE;
                case 7, 8, 9 -> RANDOM.nextFloat();
                default -> Float.intBitsToFloat(RANDOM.nextInt());
            };
        }
    }

    private static void init(double[] a) {
        for (int i = 0; i < SIZE; i++) {
            a[i] = switch(RANDOM.nextInt() % 20) {
                case 0  -> Double.NaN;
                case 1  -> 0;
                case 2  -> 1;
                case 3  -> Double.POSITIVE_INFINITY;
                case 4  -> Double.NEGATIVE_INFINITY;
                case 5  -> Double.MAX_VALUE;
                case 6  -> Double.MIN_VALUE;
                case 7, 8, 9 -> RANDOM.nextDouble();
                default -> Double.longBitsToDouble(RANDOM.nextLong());
            };
        }
    }
}
