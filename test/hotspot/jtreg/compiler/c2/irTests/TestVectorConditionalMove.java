/*
 * Copyright (c) 2022, Arm Limited. All rights reserved.
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
 * @bug 8289422
 * @key randomness
 * @summary Auto-vectorization enhancement to support vector conditional move on AArch64
 * @requires os.arch=="aarch64"
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestVectorConditionalMove
 */

public class TestVectorConditionalMove {
    final private static int SIZE = 3000;
    private static final Random RANDOM = Utils.getRandomInstance();

    private static float[] floata = new float[SIZE];
    private static float[] floatb = new float[SIZE];
    private static float[] floatc = new float[SIZE];
    private static double[] doublea = new double[SIZE];
    private static double[] doubleb = new double[SIZE];
    private static double[] doublec = new double[SIZE];

    public static void main(String[] args) {
        TestFramework.runWithFlags("-Xcomp", "-XX:-TieredCompilation", "-XX:+UseCMoveUnconditionally",
                                   "-XX:+UseVectorCmov", "-XX:CompileCommand=exclude,*.cmove*");
    }

    private float cmoveFloatGT(float a, float b) {
        return (a > b) ? a : b;
    }

    private float cmoveFloatGTSwap(float a, float b) {
        return (b > a) ? a : b;
    }

    private float cmoveFloatLT(float a, float b) {
        return (a < b) ? a : b;
    }

    private float cmoveFloatLTSwap(float a, float b) {
        return (b < a) ? a : b;
    }

    private float cmoveFloatEQ(float a, float b) {
        return (a == b) ? a : b;
    }

    private double cmoveDoubleLE(double a, double b) {
        return (a <= b) ? a : b;
    }

    private double cmoveDoubleLESwap(double a, double b) {
        return (b <= a) ? a : b;
    }

    private double cmoveDoubleGE(double a, double b) {
        return (a >= b) ? a : b;
    }

    private double cmoveDoubleGESwap(double a, double b) {
        return (b >= a) ? a : b;
    }

    private double cmoveDoubleNE(double a, double b) {
        return (a != b) ? a : b;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, ">0", IRNode.CMOVEVF, ">0", IRNode.STORE_VECTOR, ">0"})
    private static void testCMoveVFGT(float[] a, float[] b, float[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] > b[i]) ? a[i] : b[i];
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, ">0", IRNode.CMOVEVF, ">0", IRNode.STORE_VECTOR, ">0"})
    private static void testCMoveVFGTSwap(float[] a, float[] b, float[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (b[i] > a[i]) ? a[i] : b[i];
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, ">0", IRNode.CMOVEVF, ">0", IRNode.STORE_VECTOR, ">0"})
    private static void testCMoveVFLT(float[] a, float[] b, float[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] < b[i]) ? a[i] : b[i];
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, ">0", IRNode.CMOVEVF, ">0", IRNode.STORE_VECTOR, ">0"})
    private static void testCMoveVFLTSwap(float[] a, float[] b, float[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (b[i] < a[i]) ? a[i] : b[i];
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, ">0", IRNode.CMOVEVF, ">0", IRNode.STORE_VECTOR, ">0"})
    private static void testCMoveVFEQ(float[] a, float[] b, float[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] == b[i]) ? a[i] : b[i];
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, ">0", IRNode.CMOVEVD, ">0", IRNode.STORE_VECTOR, ">0"})
    private static void testCMoveVDLE(double[] a, double[] b, double[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] <= b[i]) ? a[i] : b[i];
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, ">0", IRNode.CMOVEVD, ">0", IRNode.STORE_VECTOR, ">0"})
    private static void testCMoveVDLESwap(double[] a, double[] b, double[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (b[i] <= a[i]) ? a[i] : b[i];
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, ">0", IRNode.CMOVEVD, ">0", IRNode.STORE_VECTOR, ">0"})
    private static void testCMoveVDGE(double[] a, double[] b, double[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] >= b[i]) ? a[i] : b[i];
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, ">0", IRNode.CMOVEVD, ">0", IRNode.STORE_VECTOR, ">0"})
    private static void testCMoveVDGESwap(double[] a, double[] b, double[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (b[i] >= a[i]) ? a[i] : b[i];
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, ">0", IRNode.CMOVEVD, ">0", IRNode.STORE_VECTOR, ">0"})
    private static void testCMoveVDNE(double[] a, double[] b, double[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] != b[i]) ? a[i] : b[i];
        }
    }

    @Test
    @IR(failOn = {IRNode.CMOVEVD})
    private static void testCMoveVDUnsupported() {
        int seed = 1001;
        for (int i = 0; i < doublec.length; i++) {
            doublec[i] = (i % 2 == 0) ? seed + i : seed - i;
        }
    }

    @Run(test = {"testCMoveVFGT", "testCMoveVFLT","testCMoveVDLE", "testCMoveVDGE", "testCMoveVFEQ", "testCMoveVDNE",
                 "testCMoveVFGTSwap", "testCMoveVFLTSwap","testCMoveVDLESwap", "testCMoveVDGESwap"})
    private void testCMove_runner() {
        for (int i = 0; i < SIZE; i++) {
            floata[i] = RANDOM.nextFloat();
            floatb[i] = RANDOM.nextFloat();
            doublea[i] = RANDOM.nextDouble();
            doubleb[i] = RANDOM.nextDouble();
        }

        testCMoveVFGT(floata, floatb, floatc);
        testCMoveVDLE(doublea, doubleb, doublec);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(floatc[i], cmoveFloatGT(floata[i], floatb[i]));
            Asserts.assertEquals(doublec[i], cmoveDoubleLE(doublea[i], doubleb[i]));
        }

        testCMoveVFLT(floata, floatb, floatc);
        testCMoveVDGE(doublea, doubleb, doublec);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(floatc[i], cmoveFloatLT(floata[i], floatb[i]));
            Asserts.assertEquals(doublec[i], cmoveDoubleGE(doublea[i], doubleb[i]));
        }

        for (int i = 0; i < SIZE; i++) {
            if (i % 3 == 0) {
                floatb[i] = floata[i];
                doubleb[i] = doublea[i];
            }
        }

        testCMoveVFEQ(floata, floatb, floatc);
        testCMoveVDNE(doublea, doubleb, doublec);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(floatc[i], cmoveFloatEQ(floata[i], floatb[i]));
            Asserts.assertEquals(doublec[i], cmoveDoubleNE(doublea[i], doubleb[i]));
        }

        testCMoveVFGTSwap(floata, floatb, floatc);
        testCMoveVDLESwap(doublea, doubleb, doublec);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(floatc[i], cmoveFloatGTSwap(floata[i], floatb[i]));
            Asserts.assertEquals(doublec[i], cmoveDoubleLESwap(doublea[i], doubleb[i]));
        }

        testCMoveVFLTSwap(floata, floatb, floatc);
        testCMoveVDGESwap(doublea, doubleb, doublec);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(floatc[i], cmoveFloatLTSwap(floata[i], floatb[i]));
            Asserts.assertEquals(doublec[i], cmoveDoubleGESwap(doublea[i], doubleb[i]));
        }
    }
}
