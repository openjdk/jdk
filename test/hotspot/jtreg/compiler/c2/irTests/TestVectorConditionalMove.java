/*
 * Copyright (c) 2022, Arm Limited. All rights reserved.
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8289422 8306088 8313720
 * @key randomness
 * @summary Auto-vectorization enhancement to support vector conditional move.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestVectorConditionalMove
 */

public class TestVectorConditionalMove {
    final private static int SIZE = 1024;
    private static final Random RANDOM = Utils.getRandomInstance();

    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:+UseCMoveUnconditionally", "-XX:+UseVectorCmov");
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

    // Extension: Compare 2 ILFD values, and pick from 2 ILFD values
    private int cmoveIGTforI(int a, int b, int c, int d) {
        return (a > b) ? c : d;
    }

    private long cmoveIGTforL(int a, int b, long c, long d) {
        return (a > b) ? c : d;
    }

    private float cmoveIGTforF(int a, int b, float c, float d) {
        return (a > b) ? c : d;
    }

    private double cmoveIGTforD(int a, int b, double c, double d) {
        return (a > b) ? c : d;
    }

    private int cmoveLGTforI(long a, long b, int c, int d) {
        return (a > b) ? c : d;
    }

    private long cmoveLGTforL(long a, long b, long c, long d) {
        return (a > b) ? c : d;
    }

    private float cmoveLGTforF(long a, long b, float c, float d) {
        return (a > b) ? c : d;
    }

    private double cmoveLGTforD(long a, long b, double c, double d) {
        return (a > b) ? c : d;
    }

    private int cmoveFGTforI(float a, float b, int c, int d) {
        return (a > b) ? c : d;
    }

    private long cmoveFGTforL(float a, float b, long c, long d) {
        return (a > b) ? c : d;
    }

    private float cmoveFGTforF(float a, float b, float c, float d) {
        return (a > b) ? c : d;
    }

    private double cmoveFGTforD(float a, float b, double c, double d) {
        return (a > b) ? c : d;
    }

    private int cmoveDGTforI(double a, double b, int c, int d) {
        return (a > b) ? c : d;
    }

    private long cmoveDGTforL(double a, double b, long c, long d) {
        return (a > b) ? c : d;
    }

    private float cmoveDGTforF(double a, double b, float c, float d) {
        return (a > b) ? c : d;
    }

    private double cmoveDGTforD(double a, double b, double c, double d) {
        return (a > b) ? c : d;
    }

    // Compare 2 values, and pick one of them
    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_F, ">0",
                  IRNode.VECTOR_MASK_CMP_F, ">0",
                  IRNode.VECTOR_BLEND_F, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveVFGT(float[] a, float[] b, float[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] > b[i]) ? a[i] : b[i];
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_F, ">0",
                  IRNode.VECTOR_MASK_CMP_F, ">0",
                  IRNode.VECTOR_BLEND_F, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveVFGTSwap(float[] a, float[] b, float[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (b[i] > a[i]) ? a[i] : b[i];
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_F, ">0",
                  IRNode.VECTOR_MASK_CMP_F, ">0",
                  IRNode.VECTOR_BLEND_F, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveVFLT(float[] a, float[] b, float[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] < b[i]) ? a[i] : b[i];
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_F, ">0",
                  IRNode.VECTOR_MASK_CMP_F, ">0",
                  IRNode.VECTOR_BLEND_F, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveVFLTSwap(float[] a, float[] b, float[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (b[i] < a[i]) ? a[i] : b[i];
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_F, ">0",
                  IRNode.VECTOR_MASK_CMP_F, ">0",
                  IRNode.VECTOR_BLEND_F, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveVFEQ(float[] a, float[] b, float[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] == b[i]) ? a[i] : b[i];
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_D, ">0",
                  IRNode.VECTOR_MASK_CMP_D, ">0",
                  IRNode.VECTOR_BLEND_D, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveVDLE(double[] a, double[] b, double[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] <= b[i]) ? a[i] : b[i];
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_D, ">0",
                  IRNode.VECTOR_MASK_CMP_D, ">0",
                  IRNode.VECTOR_BLEND_D, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveVDLESwap(double[] a, double[] b, double[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (b[i] <= a[i]) ? a[i] : b[i];
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_D, ">0",
                  IRNode.VECTOR_MASK_CMP_D, ">0",
                  IRNode.VECTOR_BLEND_D, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveVDGE(double[] a, double[] b, double[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] >= b[i]) ? a[i] : b[i];
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_D, ">0",
                  IRNode.VECTOR_MASK_CMP_D, ">0",
                  IRNode.VECTOR_BLEND_D, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveVDGESwap(double[] a, double[] b, double[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (b[i] >= a[i]) ? a[i] : b[i];
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_D, ">0",
                  IRNode.VECTOR_MASK_CMP_D, ">0",
                  IRNode.VECTOR_BLEND_D, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveVDNE(double[] a, double[] b, double[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] != b[i]) ? a[i] : b[i];
        }
    }

    // Extensions: compare 2 values, and pick from 2 consts
    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_F, ">0",
                  IRNode.VECTOR_MASK_CMP_F, ">0",
                  IRNode.VECTOR_BLEND_F, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveFGTforFConst(float[] a, float[] b, float[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] > b[i]) ? 0.1f : -0.1f;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_F, ">0",
                  IRNode.VECTOR_MASK_CMP_F, ">0",
                  IRNode.VECTOR_BLEND_F, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveFGEforFConst(float[] a, float[] b, float[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] >= b[i]) ? 0.1f : -0.1f;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_F, ">0",
                  IRNode.VECTOR_MASK_CMP_F, ">0",
                  IRNode.VECTOR_BLEND_F, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveFLTforFConst(float[] a, float[] b, float[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] < b[i]) ? 0.1f : -0.1f;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_F, ">0",
                  IRNode.VECTOR_MASK_CMP_F, ">0",
                  IRNode.VECTOR_BLEND_F, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveFLEforFConst(float[] a, float[] b, float[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] <= b[i]) ? 0.1f : -0.1f;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_F, ">0",
                  IRNode.VECTOR_MASK_CMP_F, ">0",
                  IRNode.VECTOR_BLEND_F, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveFEQforFConst(float[] a, float[] b, float[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] == b[i]) ? 0.1f : -0.1f;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_F, ">0",
                  IRNode.VECTOR_MASK_CMP_F, ">0",
                  IRNode.VECTOR_BLEND_F, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveFNEQforFConst(float[] a, float[] b, float[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] != b[i]) ? 0.1f : -0.1f;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_F, ">0",
                  IRNode.VECTOR_MASK_CMP_F, ">0",
                  IRNode.VECTOR_BLEND_F, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveFLTforFConstH2(float[] a, float[] b, float[] c) {
        for (int i = 0; i < a.length; i+=2) {
            c[i+0] = (a[i+0] < b[i+0]) ? 0.1f : -0.1f;
            c[i+1] = (a[i+1] < b[i+1]) ? 0.1f : -0.1f;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_F, ">0",
                  IRNode.VECTOR_MASK_CMP_F, ">0",
                  IRNode.VECTOR_BLEND_F, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveFLEforFConstH2(float[] a, float[] b, float[] c) {
        for (int i = 0; i < a.length; i+=2) {
            c[i+0] = (a[i+0] <= b[i+0]) ? 0.1f : -0.1f;
            c[i+1] = (a[i+1] <= b[i+1]) ? 0.1f : -0.1f;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_F, "=0",
                  IRNode.VECTOR_MASK_CMP_F, "=0",
                  IRNode.VECTOR_BLEND_F, "=0",
                  IRNode.STORE_VECTOR, "=0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveFYYforFConstH2(float[] a, float[] b, float[] c) {
        for (int i = 0; i < a.length; i+=2) {
            c[i+0] = (a[i+0] <= b[i+0]) ? 0.1f : -0.1f;
            c[i+1] = (a[i+1] <  b[i+1]) ? 0.1f : -0.1f;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_F, "=0",
                  IRNode.VECTOR_MASK_CMP_F, "=0",
                  IRNode.VECTOR_BLEND_F, "=0",
                  IRNode.STORE_VECTOR, "=0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveFXXforFConstH2(float[] a, float[] b, float[] c) {
        for (int i = 0; i < a.length; i+=2) {
            c[i+0] = (a[i+0] <  b[i+0]) ? 0.1f : -0.1f;
            c[i+1] = (a[i+1] <= b[i+1]) ? 0.1f : -0.1f;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_D, ">0",
                  IRNode.VECTOR_MASK_CMP_D, ">0",
                  IRNode.VECTOR_BLEND_D, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveDGTforDConst(double[] a, double[] b, double[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] > b[i]) ? 0.1 : -0.1;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_D, ">0",
                  IRNode.VECTOR_MASK_CMP_D, ">0",
                  IRNode.VECTOR_BLEND_D, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveDGEforDConst(double[] a, double[] b, double[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] >= b[i]) ? 0.1 : -0.1;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_D, ">0",
                  IRNode.VECTOR_MASK_CMP_D, ">0",
                  IRNode.VECTOR_BLEND_D, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveDLTforDConst(double[] a, double[] b, double[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] < b[i]) ? 0.1 : -0.1;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_D, ">0",
                  IRNode.VECTOR_MASK_CMP_D, ">0",
                  IRNode.VECTOR_BLEND_D, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveDLEforDConst(double[] a, double[] b, double[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] <= b[i]) ? 0.1 : -0.1;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_D, ">0",
                  IRNode.VECTOR_MASK_CMP_D, ">0",
                  IRNode.VECTOR_BLEND_D, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveDEQforDConst(double[] a, double[] b, double[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] == b[i]) ? 0.1 : -0.1;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_D, ">0",
                  IRNode.VECTOR_MASK_CMP_D, ">0",
                  IRNode.VECTOR_BLEND_D, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveDNEQforDConst(double[] a, double[] b, double[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] != b[i]) ? 0.1 : -0.1;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_D, ">0",
                  IRNode.VECTOR_MASK_CMP_D, ">0",
                  IRNode.VECTOR_BLEND_D, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveDLTforDConstH2(double[] a, double[] b, double[] c) {
        for (int i = 0; i < a.length; i+=2) {
            c[i+0] = (a[i+0] < b[i+0]) ? 0.1 : -0.1;
            c[i+1] = (a[i+1] < b[i+1]) ? 0.1 : -0.1;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_D, ">0",
                  IRNode.VECTOR_MASK_CMP_D, ">0",
                  IRNode.VECTOR_BLEND_D, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveDLEforDConstH2(double[] a, double[] b, double[] c) {
        for (int i = 0; i < a.length; i+=2) {
            c[i+0] = (a[i+0] <= b[i+0]) ? 0.1 : -0.1;
            c[i+1] = (a[i+1] <= b[i+1]) ? 0.1 : -0.1;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_D, "=0",
                  IRNode.VECTOR_MASK_CMP_D, "=0",
                  IRNode.VECTOR_BLEND_D, "=0",
                  IRNode.STORE_VECTOR, "=0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveDYYforDConstH2(double[] a, double[] b, double[] c) {
        for (int i = 0; i < a.length; i+=2) {
            c[i+0] = (a[i+0] <= b[i+0]) ? 0.1 : -0.1;
            c[i+1] = (a[i+1] <  b[i+1]) ? 0.1 : -0.1;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_D, "=0",
                  IRNode.VECTOR_MASK_CMP_D, "=0",
                  IRNode.VECTOR_BLEND_D, "=0",
                  IRNode.STORE_VECTOR, "=0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveDXXforDConstH2(double[] a, double[] b, double[] c) {
        for (int i = 0; i < a.length; i+=2) {
            c[i+0] = (a[i+0] <  b[i+0]) ? 0.1 : -0.1;
            c[i+1] = (a[i+1] <= b[i+1]) ? 0.1 : -0.1;
        }
    }

    // Extension: Compare 2 ILFD values, and pick from 2 ILFD values
    // Note:
    //   To guarantee that CMove is introduced, I need to perform the loads before the branch. To ensure they
    //   do not float down into the branches, I compute a value, and store it to r2 (same as r, except that the
    //   compilation does not know that).
    //   So far, vectorization only works for CMoveF/D, with same data-width comparison (F/I for F, D/L for D).
    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    private static void testCMoveIGTforI(int[] a, int[] b, int[] c, int[] d, int[] r, int[] r2) {
        for (int i = 0; i < a.length; i++) {
            int cc = c[i];
            int dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] > b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    private static void testCMoveIGTforL(int[] a, int[] b, long[] c, long[] d, long[] r, long[] r2) {
        for (int i = 0; i < a.length; i++) {
            long cc = c[i];
            long dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] > b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I,     IRNode.VECTOR_SIZE + "min(max_int, max_float)", ">0",
                  IRNode.LOAD_VECTOR_F,     IRNode.VECTOR_SIZE + "min(max_int, max_float)", ">0",
                  IRNode.VECTOR_MASK_CMP_I, IRNode.VECTOR_SIZE + "min(max_int, max_float)", ">0",
                  IRNode.VECTOR_BLEND_F,    IRNode.VECTOR_SIZE + "min(max_int, max_float)", ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveIGTforF(int[] a, int[] b, float[] c, float[] d, float[] r, float[] r2) {
        for (int i = 0; i < a.length; i++) {
            float cc = c[i];
            float dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] > b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    private static void testCMoveIGTforD(int[] a, int[] b, double[] c, double[] d, double[] r, double[] r2) {
        for (int i = 0; i < a.length; i++) {
            double cc = c[i];
            double dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] > b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    private static void testCMoveLGTforI(long[] a, long[] b, int[] c, int[] d, int[] r, int[] r2) {
        for (int i = 0; i < a.length; i++) {
            int cc = c[i];
            int dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] > b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    private static void testCMoveLGTforL(long[] a, long[] b, long[] c, long[] d, long[] r, long[] r2) {
        for (int i = 0; i < a.length; i++) {
            long cc = c[i];
            long dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] > b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    private static void testCMoveLGTforF(long[] a, long[] b, float[] c, float[] d, float[] r, float[] r2) {
        for (int i = 0; i < a.length; i++) {
            float cc = c[i];
            float dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] > b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_L,     IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0",
                  IRNode.LOAD_VECTOR_D,     IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0",
                  IRNode.VECTOR_MASK_CMP_L, IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0",
                  IRNode.VECTOR_BLEND_D,    IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true"})
    // Requires avx2, else L is restricted to 16 byte, and D has 32. That leads to a vector elements mismatch of 2 to 4.
    private static void testCMoveLGTforD(long[] a, long[] b, double[] c, double[] d, double[] r, double[] r2) {
        for (int i = 0; i < a.length; i++) {
            double cc = c[i];
            double dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] > b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    private static void testCMoveFGTforI(float[] a, float[] b, int[] c, int[] d, int[] r, int[] r2) {
        for (int i = 0; i < a.length; i++) {
            int cc = c[i];
            int dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] > b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    private static void testCMoveFGTforL(float[] a, float[] b, long[] c, long[] d, long[] r, long[] r2) {
        for (int i = 0; i < a.length; i++) {
            long cc = c[i];
            long dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] > b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_F, ">0",
                  IRNode.VECTOR_MASK_CMP_F, ">0",
                  IRNode.VECTOR_BLEND_F, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveFGTforF(float[] a, float[] b, float[] c, float[] d, float[] r, float[] r2) {
        for (int i = 0; i < a.length; i++) {
            float cc = c[i];
            float dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] > b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    private static void testCMoveFGTforD(float[] a, float[] b, double[] c, double[] d, double[] r, double[] r2) {
        for (int i = 0; i < a.length; i++) {
            double cc = c[i];
            double dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] > b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    private static void testCMoveDGTforI(double[] a, double[] b, int[] c, int[] d, int[] r, int[] r2) {
        for (int i = 0; i < a.length; i++) {
            int cc = c[i];
            int dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] > b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    private static void testCMoveDGTforL(double[] a, double[] b, long[] c, long[] d, long[] r, long[] r2) {
        for (int i = 0; i < a.length; i++) {
            long cc = c[i];
            long dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] > b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    private static void testCMoveDGTforF(double[] a, double[] b, float[] c, float[] d, float[] r, float[] r2) {
        for (int i = 0; i < a.length; i++) {
            float cc = c[i];
            float dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] > b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_D, ">0",
                  IRNode.VECTOR_MASK_CMP_D, ">0",
                  IRNode.VECTOR_BLEND_D, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveDGTforD(double[] a, double[] b, double[] c, double[] d, double[] r, double[] r2) {
        for (int i = 0; i < a.length; i++) {
            double cc = c[i];
            double dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] > b[i]) ? cc : dd;
        }
    }

    // Use some constants in the comparison
    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_F, ">0",
                  IRNode.VECTOR_MASK_CMP_F, ">0",
                  IRNode.VECTOR_BLEND_F, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveFGTforFCmpCon1(float a, float[] b, float[] c, float[] d, float[] r, float[] r2) {
        for (int i = 0; i < b.length; i++) {
            float cc = c[i];
            float dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a > b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_F, ">0",
                  IRNode.VECTOR_MASK_CMP_F, ">0",
                  IRNode.VECTOR_BLEND_F, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static void testCMoveFGTforFCmpCon2(float[] a, float b, float[] c, float[] d, float[] r, float[] r2) {
        for (int i = 0; i < a.length; i++) {
            float cc = c[i];
            float dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] > b) ? cc : dd;
        }
    }

    // A case that is currently not supported and is not expected to vectorize
    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    private static void testCMoveVDUnsupported() {
        double[] doublec = new double[SIZE];
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
                 "testCMoveDLEforDConst", "testCMoveDEQforDConst", "testCMoveDNEQforDConst",
                 "testCMoveFLTforFConstH2", "testCMoveFLEforFConstH2",
                 "testCMoveFYYforFConstH2", "testCMoveFXXforFConstH2",
                 "testCMoveDLTforDConstH2", "testCMoveDLEforDConstH2",
                 "testCMoveDYYforDConstH2", "testCMoveDXXforDConstH2"})
    private void testCMove_runner() {
        float[] floata = new float[SIZE];
        float[] floatb = new float[SIZE];
        float[] floatc = new float[SIZE];
        double[] doublea = new double[SIZE];
        double[] doubleb = new double[SIZE];
        double[] doublec = new double[SIZE];

        init(floata);
        init(floatb);
        init(doublea);
        init(doubleb);

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

        // Hand-unrolled (H2) examples:
        testCMoveFLTforFConstH2(floata, floatb, floatc);
        testCMoveDLTforDConstH2(doublea, doubleb, doublec);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(floatc[i], cmoveFLTforFConst(floata[i], floatb[i]));
            Asserts.assertEquals(doublec[i], cmoveDLTforDConst(doublea[i], doubleb[i]));
        }

        testCMoveFLEforFConstH2(floata, floatb, floatc);
        testCMoveDLEforDConstH2(doublea, doubleb, doublec);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(floatc[i], cmoveFLEforFConst(floata[i], floatb[i]));
            Asserts.assertEquals(doublec[i], cmoveDLEforDConst(doublea[i], doubleb[i]));
        }

        testCMoveFYYforFConstH2(floata, floatb, floatc);
        testCMoveDYYforDConstH2(doublea, doubleb, doublec);
        for (int i = 0; i < SIZE; i+=2) {
            Asserts.assertEquals(floatc[i+0], cmoveFLEforFConst(floata[i+0], floatb[i+0]));
            Asserts.assertEquals(doublec[i+0], cmoveDLEforDConst(doublea[i+0], doubleb[i+0]));
            Asserts.assertEquals(floatc[i+1], cmoveFLTforFConst(floata[i+1], floatb[i+1]));
            Asserts.assertEquals(doublec[i+1], cmoveDLTforDConst(doublea[i+1], doubleb[i+1]));
        }

        testCMoveFXXforFConstH2(floata, floatb, floatc);
        testCMoveDXXforDConstH2(doublea, doubleb, doublec);
        for (int i = 0; i < SIZE; i+=2) {
            Asserts.assertEquals(floatc[i+0], cmoveFLTforFConst(floata[i+0], floatb[i+0]));
            Asserts.assertEquals(doublec[i+0], cmoveDLTforDConst(doublea[i+0], doubleb[i+0]));
            Asserts.assertEquals(floatc[i+1], cmoveFLEforFConst(floata[i+1], floatb[i+1]));
            Asserts.assertEquals(doublec[i+1], cmoveDLEforDConst(doublea[i+1], doubleb[i+1]));
        }
    }

    @Warmup(0)
    @Run(test = {"testCMoveIGTforI",
                 "testCMoveIGTforL",
                 "testCMoveIGTforF",
                 "testCMoveIGTforD",
                 "testCMoveLGTforI",
                 "testCMoveLGTforL",
                 "testCMoveLGTforF",
                 "testCMoveLGTforD",
                 "testCMoveFGTforI",
                 "testCMoveFGTforL",
                 "testCMoveFGTforF",
                 "testCMoveFGTforD",
                 "testCMoveDGTforI",
                 "testCMoveDGTforL",
                 "testCMoveDGTforF",
                 "testCMoveDGTforD",
                 "testCMoveFGTforFCmpCon1",
                 "testCMoveFGTforFCmpCon2"})
    private void testCMove_runner_two() {
        int[] aI = new int[SIZE];
        int[] bI = new int[SIZE];
        int[] cI = new int[SIZE];
        int[] dI = new int[SIZE];
        int[] rI = new int[SIZE];
        long[] aL = new long[SIZE];
        long[] bL = new long[SIZE];
        long[] cL = new long[SIZE];
        long[] dL = new long[SIZE];
        long[] rL = new long[SIZE];
        float[] aF = new float[SIZE];
        float[] bF = new float[SIZE];
        float[] cF = new float[SIZE];
        float[] dF = new float[SIZE];
        float[] rF = new float[SIZE];
        double[] aD = new double[SIZE];
        double[] bD = new double[SIZE];
        double[] cD = new double[SIZE];
        double[] dD = new double[SIZE];
        double[] rD = new double[SIZE];

        init(aI);
        init(bI);
        init(cI);
        init(dI);
        init(aL);
        init(bL);
        init(cL);
        init(dL);
        init(aF);
        init(bF);
        init(cF);
        init(dF);
        init(aD);
        init(bD);
        init(cD);
        init(dD);

        testCMoveIGTforI(aI, bI, cI, dI, rI, rI);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rI[i], cmoveIGTforI(aI[i], bI[i], cI[i], dI[i]));
        }

        testCMoveIGTforL(aI, bI, cL, dL, rL, rL);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rL[i], cmoveIGTforL(aI[i], bI[i], cL[i], dL[i]));
        }

        testCMoveIGTforF(aI, bI, cF, dF, rF, rF);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rF[i], cmoveIGTforF(aI[i], bI[i], cF[i], dF[i]));
        }

        testCMoveIGTforD(aI, bI, cD, dD, rD, rD);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rD[i], cmoveIGTforD(aI[i], bI[i], cD[i], dD[i]));
        }

        testCMoveLGTforI(aL, bL, cI, dI, rI, rI);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rI[i], cmoveLGTforI(aL[i], bL[i], cI[i], dI[i]));
        }

        testCMoveLGTforL(aL, bL, cL, dL, rL, rL);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rL[i], cmoveLGTforL(aL[i], bL[i], cL[i], dL[i]));
        }

        testCMoveLGTforF(aL, bL, cF, dF, rF, rF);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rF[i], cmoveLGTforF(aL[i], bL[i], cF[i], dF[i]));
        }

        testCMoveLGTforD(aL, bL, cD, dD, rD, rD);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rD[i], cmoveLGTforD(aL[i], bL[i], cD[i], dD[i]));
        }

        testCMoveFGTforI(aF, bF, cI, dI, rI, rI);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rI[i], cmoveFGTforI(aF[i], bF[i], cI[i], dI[i]));
        }

        testCMoveFGTforL(aF, bF, cL, dL, rL, rL);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rL[i], cmoveFGTforL(aF[i], bF[i], cL[i], dL[i]));
        }

        testCMoveFGTforF(aF, bF, cF, dF, rF, rF);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rF[i], cmoveFGTforF(aF[i], bF[i], cF[i], dF[i]));
        }

        testCMoveFGTforD(aF, bF, cD, dD, rD, rD);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rD[i], cmoveFGTforD(aF[i], bF[i], cD[i], dD[i]));
        }

        testCMoveDGTforI(aD, bD, cI, dI, rI, rI);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rI[i], cmoveDGTforI(aD[i], bD[i], cI[i], dI[i]));
        }

        testCMoveDGTforL(aD, bD, cL, dL, rL, rL);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rL[i], cmoveDGTforL(aD[i], bD[i], cL[i], dL[i]));
        }

        testCMoveDGTforF(aD, bD, cF, dF, rF, rF);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rF[i], cmoveDGTforF(aD[i], bD[i], cF[i], dF[i]));
        }

        testCMoveDGTforD(aD, bD, cD, dD, rD, rD);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rD[i], cmoveDGTforD(aD[i], bD[i], cD[i], dD[i]));
        }

        // Use some constants/invariants in the comparison
        testCMoveFGTforFCmpCon1(aF[0], bF, cF, dF, rF, rF);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rF[i], cmoveFGTforF(aF[0], bF[i], cF[i], dF[i]));
        }

        testCMoveFGTforFCmpCon2(aF, bF[0], cF, dF, rF, rF);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rF[i], cmoveFGTforF(aF[i], bF[0], cF[i], dF[i]));
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
