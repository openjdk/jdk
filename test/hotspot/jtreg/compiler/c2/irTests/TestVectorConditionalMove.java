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
 * @test id=vanilla
 * @bug 8289422 8306088
 * @key randomness
 * @summary Auto-vectorization enhancement to support vector conditional move.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestVectorConditionalMove vanilla
 */

/*
 * @test id=vec-32
 * @bug 8289422 8306088
 * @key randomness
 * @summary Change vector size to MaxVectorSize=32
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestVectorConditionalMove vec-32
 */

/*
 * @test id=vec-16
 * @bug 8289422 8306088
 * @key randomness
 * @summary Change vector size to MaxVectorSize=32
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestVectorConditionalMove vec-16
 */

public class TestVectorConditionalMove {
    final private static int SIZE = 1024;
    private static final Random RANDOM = Utils.getRandomInstance();

    private static float[] floata = new float[SIZE];
    private static float[] floatb = new float[SIZE];
    private static float[] floatc = new float[SIZE];
    private static double[] doublea = new double[SIZE];
    private static double[] doubleb = new double[SIZE];
    private static double[] doublec = new double[SIZE];

    public static void main(String[] args) {
        TestFramework framework = new TestFramework(TestVectorConditionalMove.class);
        framework.addFlags("-XX:-TieredCompilation",
                           "-XX:+UseCMoveUnconditionally",
                           "-XX:+UseVectorCmov",
                           "-XX:CompileCommand=compileonly,*.TestVectorConditionalMove.test*");

        if (args.length != 1) {
            throw new RuntimeException("Test requires exactly one argument!");
        }

        switch (args[0]) {
        case "vanilla":
            break;
        case "vec-32":
            framework.addFlags("-XX:MaxVectorSize=32");
            break;
        case "vec-16":
            framework.addFlags("-XX:MaxVectorSize=16");
            break;
        default:
            throw new RuntimeException("Test argument not recognized: " + args[0]);
        }
        framework.start();
    }

    // Compare 2 values, and pick one of them
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

    // Extensions: compare 2 values, and pick from 2 consts
    private float cmoveFGTforFConst(float a, float b) {
        return (a > b) ? 0.1f : -0.1f;
    }

    private float cmoveFGEforFConst(float a, float b) {
        return (a >= b) ? 0.1f : -0.1f;
    }

    private float cmoveFLTforFConst(float a, float b) {
        return (a < b) ? 0.1f : -0.1f;
    }

    private float cmoveFLEforFConst(float a, float b) {
        return (a <= b) ? 0.1f : -0.1f;
    }

    private float cmoveFEQforFConst(float a, float b) {
        return (a == b) ? 0.1f : -0.1f;
    }

    private float cmoveFNEQforFConst(float a, float b) {
        return (a != b) ? 0.1f : -0.1f;
    }

    private double cmoveDGTforDConst(double a, double b) {
        return (a > b) ? 0.1 : -0.1;
    }

    private double cmoveDGEforDConst(double a, double b) {
        return (a >= b) ? 0.1 : -0.1;
    }

    private double cmoveDLTforDConst(double a, double b) {
        return (a < b) ? 0.1 : -0.1;
    }

    private double cmoveDLEforDConst(double a, double b) {
        return (a <= b) ? 0.1 : -0.1;
    }

    private double cmoveDEQforDConst(double a, double b) {
        return (a == b) ? 0.1 : -0.1;
    }

    private double cmoveDNEQforDConst(double a, double b) {
        return (a != b) ? 0.1 : -0.1;
    }

    // Compare 2 values, and pick one of them
    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, ">0", IRNode.VECTOR_MASK_CMP, ">0", IRNode.VECTOR_BLEND, ">0", IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveVFGT(float[] a, float[] b, float[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] > b[i]) ? a[i] : b[i];
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, ">0", IRNode.VECTOR_MASK_CMP, ">0", IRNode.VECTOR_BLEND, ">0", IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveVFGTSwap(float[] a, float[] b, float[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (b[i] > a[i]) ? a[i] : b[i];
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, ">0", IRNode.VECTOR_MASK_CMP, ">0", IRNode.VECTOR_BLEND, ">0", IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveVFLT(float[] a, float[] b, float[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] < b[i]) ? a[i] : b[i];
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, ">0", IRNode.VECTOR_MASK_CMP, ">0", IRNode.VECTOR_BLEND, ">0", IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveVFLTSwap(float[] a, float[] b, float[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (b[i] < a[i]) ? a[i] : b[i];
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, ">0", IRNode.VECTOR_MASK_CMP, ">0", IRNode.VECTOR_BLEND, ">0", IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveVFEQ(float[] a, float[] b, float[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] == b[i]) ? a[i] : b[i];
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, ">0", IRNode.VECTOR_MASK_CMP, ">0", IRNode.VECTOR_BLEND, ">0", IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveVDLE(double[] a, double[] b, double[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] <= b[i]) ? a[i] : b[i];
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, ">0", IRNode.VECTOR_MASK_CMP, ">0", IRNode.VECTOR_BLEND, ">0", IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveVDLESwap(double[] a, double[] b, double[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (b[i] <= a[i]) ? a[i] : b[i];
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, ">0", IRNode.VECTOR_MASK_CMP, ">0", IRNode.VECTOR_BLEND, ">0", IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveVDGE(double[] a, double[] b, double[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] >= b[i]) ? a[i] : b[i];
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, ">0", IRNode.VECTOR_MASK_CMP, ">0", IRNode.VECTOR_BLEND, ">0", IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveVDGESwap(double[] a, double[] b, double[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (b[i] >= a[i]) ? a[i] : b[i];
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, ">0", IRNode.VECTOR_MASK_CMP, ">0", IRNode.VECTOR_BLEND, ">0", IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveVDNE(double[] a, double[] b, double[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] != b[i]) ? a[i] : b[i];
        }
    }

    // Extensions: compare 2 values, and pick from 2 consts
    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, ">0", IRNode.VECTOR_MASK_CMP, ">0", IRNode.VECTOR_BLEND, ">0", IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveFGTforFConst(float[] a, float[] b, float[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] > b[i]) ? 0.1f : -0.1f;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, ">0", IRNode.VECTOR_MASK_CMP, ">0", IRNode.VECTOR_BLEND, ">0", IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveFGEforFConst(float[] a, float[] b, float[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] >= b[i]) ? 0.1f : -0.1f;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, ">0", IRNode.VECTOR_MASK_CMP, ">0", IRNode.VECTOR_BLEND, ">0", IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveFLTforFConst(float[] a, float[] b, float[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] < b[i]) ? 0.1f : -0.1f;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, ">0", IRNode.VECTOR_MASK_CMP, ">0", IRNode.VECTOR_BLEND, ">0", IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveFLEforFConst(float[] a, float[] b, float[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] <= b[i]) ? 0.1f : -0.1f;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, ">0", IRNode.VECTOR_MASK_CMP, ">0", IRNode.VECTOR_BLEND, ">0", IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveFEQforFConst(float[] a, float[] b, float[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] == b[i]) ? 0.1f : -0.1f;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, ">0", IRNode.VECTOR_MASK_CMP, ">0", IRNode.VECTOR_BLEND, ">0", IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveFNEQforFConst(float[] a, float[] b, float[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] != b[i]) ? 0.1f : -0.1f;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, ">0", IRNode.VECTOR_MASK_CMP, ">0", IRNode.VECTOR_BLEND, ">0", IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveDGTforDConst(double[] a, double[] b, double[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] > b[i]) ? 0.1 : -0.1;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, ">0", IRNode.VECTOR_MASK_CMP, ">0", IRNode.VECTOR_BLEND, ">0", IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveDGEforDConst(double[] a, double[] b, double[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] >= b[i]) ? 0.1 : -0.1;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, ">0", IRNode.VECTOR_MASK_CMP, ">0", IRNode.VECTOR_BLEND, ">0", IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveDLTforDConst(double[] a, double[] b, double[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] < b[i]) ? 0.1 : -0.1;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, ">0", IRNode.VECTOR_MASK_CMP, ">0", IRNode.VECTOR_BLEND, ">0", IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveDLEforDConst(double[] a, double[] b, double[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] <= b[i]) ? 0.1 : -0.1;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, ">0", IRNode.VECTOR_MASK_CMP, ">0", IRNode.VECTOR_BLEND, ">0", IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveDEQforDConst(double[] a, double[] b, double[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] == b[i]) ? 0.1 : -0.1;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, ">0", IRNode.VECTOR_MASK_CMP, ">0", IRNode.VECTOR_BLEND, ">0", IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveDNEQforDConst(double[] a, double[] b, double[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] != b[i]) ? 0.1 : -0.1;
        }
    }


    @Test
    @IR(failOn = {IRNode.VECTOR_MASK_CMP, IRNode.VECTOR_BLEND})
    private static void testCMoveVDUnsupported() {
        int seed = 1001;
        for (int i = 0; i < doublec.length; i++) {
            doublec[i] = (i % 2 == 0) ? seed + i : seed - i;
        }
    }

    @Warmup(0)
    @Run(test = {"testCMoveVFGT", "testCMoveVFLT","testCMoveVDLE", "testCMoveVDGE", "testCMoveVFEQ", "testCMoveVDNE",
                 "testCMoveVFGTSwap", "testCMoveVFLTSwap","testCMoveVDLESwap", "testCMoveVDGESwap",
                 "testCMoveFGTforFConst", "testCMoveFGEforFConst", "testCMoveFLTforFConst",
                 "testCMoveFLEforFConst", "testCMoveFEQforFConst", "testCMoveFNEQforFConst",
                 "testCMoveDGTforDConst", "testCMoveDGEforDConst", "testCMoveDLTforDConst",
                 "testCMoveDLEforDConst", "testCMoveDEQforDConst", "testCMoveDNEQforDConst"})
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

        // Ensure we frequently have equals
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

        // Extensions: compare 2 values, and pick from 2 consts
        testCMoveFGTforFConst(floata, floatb, floatc);
        testCMoveDGTforDConst(doublea, doubleb, doublec);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(floatc[i], cmoveFGTforFConst(floata[i], floatb[i]));
            Asserts.assertEquals(doublec[i], cmoveDGTforDConst(doublea[i], doubleb[i]));
        }

        testCMoveFGEforFConst(floata, floatb, floatc);
        testCMoveDGEforDConst(doublea, doubleb, doublec);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(floatc[i], cmoveFGEforFConst(floata[i], floatb[i]));
            Asserts.assertEquals(doublec[i], cmoveDGEforDConst(doublea[i], doubleb[i]));
        }

        testCMoveFLTforFConst(floata, floatb, floatc);
        testCMoveDLTforDConst(doublea, doubleb, doublec);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(floatc[i], cmoveFLTforFConst(floata[i], floatb[i]));
            Asserts.assertEquals(doublec[i], cmoveDLTforDConst(doublea[i], doubleb[i]));
        }

        testCMoveFLEforFConst(floata, floatb, floatc);
        testCMoveDLEforDConst(doublea, doubleb, doublec);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(floatc[i], cmoveFLEforFConst(floata[i], floatb[i]));
            Asserts.assertEquals(doublec[i], cmoveDLEforDConst(doublea[i], doubleb[i]));
        }

        testCMoveFEQforFConst(floata, floatb, floatc);
        testCMoveDEQforDConst(doublea, doubleb, doublec);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(floatc[i], cmoveFEQforFConst(floata[i], floatb[i]));
            Asserts.assertEquals(doublec[i], cmoveDEQforDConst(doublea[i], doubleb[i]));
        }

        testCMoveFNEQforFConst(floata, floatb, floatc);
        testCMoveDNEQforDConst(doublea, doubleb, doublec);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(floatc[i], cmoveFNEQforFConst(floata[i], floatb[i]));
            Asserts.assertEquals(doublec[i], cmoveDNEQforDConst(doublea[i], doubleb[i]));
        }
    }
}
