/*
 * Copyright (c) 2022, Arm Limited. All rights reserved.
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
import jdk.incubator.vector.Float16;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

/*
 * @test
 * @bug 8289422 8306088 8313720
 * @key randomness
 * @summary Auto-vectorization enhancement to support vector conditional move.
 * @modules jdk.incubator.vector
 * @library /test/lib /
 * @compile TestVectorConditionalMove.java
 * @run driver compiler.c2.irTests.TestVectorConditionalMove
 */

public class TestVectorConditionalMove {
    final private static int SIZE = 1024;
    private static final Random RANDOM = Utils.getRandomInstance();

    public static void main(String[] args) {
        // Cross-product: +-AlignVector and +-UseCompactObjectHeaders
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector",
                                   "-XX:+UseCMoveUnconditionally", "-XX:+UseVectorCmov",
                                   "-XX:+UnlockExperimentalVMOptions", "-XX:-UseCompactObjectHeaders", "-XX:-AlignVector");
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector",
                                   "-XX:+UseCMoveUnconditionally", "-XX:+UseVectorCmov",
                                   "-XX:+UnlockExperimentalVMOptions", "-XX:-UseCompactObjectHeaders", "-XX:+AlignVector");
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector",
                                   "-XX:+UseCMoveUnconditionally", "-XX:+UseVectorCmov",
                                   "-XX:+UnlockExperimentalVMOptions", "-XX:+UseCompactObjectHeaders", "-XX:-AlignVector");
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector",
                                   "-XX:+UseCMoveUnconditionally", "-XX:+UseVectorCmov",
                                   "-XX:+UnlockExperimentalVMOptions", "-XX:+UseCompactObjectHeaders", "-XX:+AlignVector");
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
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
        applyIfOr = {"UseCompactObjectHeaders", "false", "AlignVector", "false"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    private static void testCMoveFLTforFConstH2(float[] a, float[] b, float[] c) {
        for (int i = 0; i < a.length; i+=2) {
            c[i+0] = (a[i+0] < b[i+0]) ? 0.1f : -0.1f;
            c[i+1] = (a[i+1] < b[i+1]) ? 0.1f : -0.1f;
            // With AlignVector, we need 8-byte alignment of vector loads/stores.
            // UseCompactObjectHeaders=false                        UseCompactObjectHeaders=true
            // adr = base + 16 + 8*i      ->  always                adr = base + 12 + 8*i      ->  never
            // -> vectorize                                         -> no vectorization
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_F, ">0",
                  IRNode.VECTOR_MASK_CMP_F, ">0",
                  IRNode.VECTOR_BLEND_F, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfOr = {"UseCompactObjectHeaders", "false", "AlignVector", "false"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    private static void testCMoveFLEforFConstH2(float[] a, float[] b, float[] c) {
        for (int i = 0; i < a.length; i+=2) {
            c[i+0] = (a[i+0] <= b[i+0]) ? 0.1f : -0.1f;
            c[i+1] = (a[i+1] <= b[i+1]) ? 0.1f : -0.1f;
            // With AlignVector, we need 8-byte alignment of vector loads/stores.
            // UseCompactObjectHeaders=false                        UseCompactObjectHeaders=true
            // adr = base + 16 + 8*i      ->  always                adr = base + 12 + 8*i      ->  never
            // -> vectorize                                         -> no vectorization
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_F, "=0",
                  IRNode.VECTOR_MASK_CMP_F, "=0",
                  IRNode.VECTOR_BLEND_F, "=0",
                  IRNode.STORE_VECTOR, "=0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    private static void testCMoveDXXforDConstH2(double[] a, double[] b, double[] c) {
        for (int i = 0; i < a.length; i+=2) {
            c[i+0] = (a[i+0] <  b[i+0]) ? 0.1 : -0.1;
            c[i+1] = (a[i+1] <= b[i+1]) ? 0.1 : -0.1;
        }
    }







    private int cmoveIGTforI_OK(int a, int b) {
        return (a > b) ? 1 : 2;
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR}, applyIfPlatform = {"riscv64", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_I, ">0",
                  IRNode.VECTOR_MASK_CMP_I, ">0",
                  IRNode.VECTOR_BLEND_I, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfPlatform = {"riscv64", "true"}, applyIfCPUFeature = {"rvv", "true"})
    private static void testCMoveIGTforI_OK(int[] a, int[] b, int[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] > b[i]) ? 1 : 2;
        }
    }

    private long cmoveIGTforL_OK(int a, int b) {
        return (a > b) ? (long)1 : (long)2;
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR}, applyIfPlatform = {"riscv64", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_I, ">0",
                  IRNode.VECTOR_MASK_CMP_I, ">0",
                  IRNode.VECTOR_BLEND_I, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfPlatform = {"riscv64", "true"}, applyIfCPUFeature = {"rvv", "true"})
    private static void testCMoveIGTforL_OK(int[] a, int[] b, long[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] > b[i]) ? (long)1 : (long)2;
        }
    }

    private byte cmoveIGTforB_OK(int a, int b) {
        return (a > b) ? (byte)1 : (byte)2;
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR}, applyIfPlatform = {"riscv64", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_I, ">0",
                  IRNode.VECTOR_MASK_CMP_I, ">0",
                  IRNode.VECTOR_BLEND_I, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfPlatform = {"riscv64", "true"}, applyIfCPUFeature = {"rvv", "true"})
    private static void testCMoveIGTforB_OK(int[] a, int[] b, byte[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] > b[i]) ? (byte)1 : (byte)2;
        }
    }

    private boolean cmoveIGTforBool_OK(int a, int b) {
        return (a > b) ? true : false;
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR}, applyIfPlatform = {"riscv64", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_I, ">0",
                  IRNode.VECTOR_MASK_CMP_I, ">0",
                  IRNode.VECTOR_BLEND_I, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfPlatform = {"riscv64", "true"}, applyIfCPUFeature = {"rvv", "true"})
    private static void testCMoveIGTforBool_OK(int[] a, int[] b, boolean[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] > b[i]) ? true : false;
        }
    }

    private char cmoveIGTforChar_OK(int a, int b) {
        return (a > b) ? '1' : '2';
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR}, applyIfPlatform = {"riscv64", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_I, ">0",
                  IRNode.VECTOR_MASK_CMP_I, ">0",
                  IRNode.VECTOR_BLEND_I, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfPlatform = {"riscv64", "true"}, applyIfCPUFeature = {"rvv", "true"})
    private static void testCMoveIGTforChar_OK(int[] a, int[] b, char[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] > b[i]) ? '1' : '2';
        }
    }

    private short cmoveIGTforShort_OK(int a, int b) {
        return (a > b) ? (short)1 : (short)2;
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR}, applyIfPlatform = {"riscv64", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_I, ">0",
                  IRNode.VECTOR_MASK_CMP_I, ">0",
                  IRNode.VECTOR_BLEND_I, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfPlatform = {"riscv64", "true"}, applyIfCPUFeature = {"rvv", "true"})
    private static void testCMoveIGTforShort_OK(int[] a, int[] b, short[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] > b[i]) ? (short)1 : (short)2;
        }
    }

    private Float16 cmoveIGTforHF_OK(int a, int b) {
        return (a > b) ? Float16.valueOf(1) : Float16.valueOf(2);
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    private static void testCMoveIGTforHF_OK(int[] a, int[] b, Float16[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] > b[i]) ? Float16.valueOf(1) : Float16.valueOf(2);
        }
    }





    private long cmoveLGTforL_OK(long a, long b) {
        return (a > b) ? 1L : 2L;
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR}, applyIfPlatform = {"riscv64", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_L, ">0",
                  IRNode.VECTOR_MASK_CMP_L, ">0",
                  IRNode.VECTOR_BLEND_L, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfPlatform = {"riscv64", "true"}, applyIfCPUFeature = {"rvv", "true"})
    private static void testCMoveLGTforL_OK(long[] a, long[] b, long[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] > b[i]) ? 1 : 2;
        }
    }

    private int cmoveLGTforI_OK(long a, long b) {
        return (a > b) ? 1 : 2;
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR}, applyIfPlatform = {"riscv64", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_L, ">0",
                  IRNode.VECTOR_MASK_CMP_L, ">0",
                  IRNode.VECTOR_BLEND_I, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfPlatform = {"riscv64", "true"}, applyIfCPUFeature = {"rvv", "true"})
    private static void testCMoveLGTforI_OK(long[] a, long[] b, int[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] > b[i]) ? 1 : 2;
        }
    }

    private byte cmoveLGTforB_OK(long a, long b) {
        return (a > b) ? (byte)1 : (byte)2;
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR}, applyIfPlatform = {"riscv64", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_L, ">0",
                  IRNode.VECTOR_MASK_CMP_L, ">0",
                  IRNode.VECTOR_BLEND_I, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfPlatform = {"riscv64", "true"}, applyIfCPUFeature = {"rvv", "true"})
    private static void testCMoveLGTforB_OK(long[] a, long[] b, byte[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] > b[i]) ? (byte)1 : (byte)2;
        }
    }

    private boolean cmoveLGTforBool_OK(long a, long b) {
        return (a > b) ? true : false;
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR}, applyIfPlatform = {"riscv64", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_L, ">0",
                  IRNode.VECTOR_MASK_CMP_L, ">0",
                  IRNode.VECTOR_BLEND_I, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfPlatform = {"riscv64", "true"}, applyIfCPUFeature = {"rvv", "true"})
    private static void testCMoveLGTforBool_OK(long[] a, long[] b, boolean[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] > b[i]) ? true : false;
        }
    }

    private char cmoveLGTforChar_OK(long a, long b) {
        return (a > b) ? '1' : '2';
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR}, applyIfPlatform = {"riscv64", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_I, ">0",
                  IRNode.VECTOR_MASK_CMP_I, ">0",
                  IRNode.VECTOR_BLEND_I, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfPlatform = {"riscv64", "true"}, applyIfCPUFeature = {"rvv", "true"})
    private static void testCMoveLGTforChar_OK(long[] a, long[] b, char[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] > b[i]) ? '1' : '2';
        }
    }

    private short cmoveLGTforShort_OK(long a, long b) {
        return (a > b) ? (short)1 : (short)2;
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR}, applyIfPlatform = {"riscv64", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_I, ">0",
                  IRNode.VECTOR_MASK_CMP_I, ">0",
                  IRNode.VECTOR_BLEND_I, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfPlatform = {"riscv64", "true"}, applyIfCPUFeature = {"rvv", "true"})
    private static void testCMoveLGTforShort_OK(long[] a, long[] b, short[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] > b[i]) ? (short)1 : (short)2;
        }
    }

    private Float16 cmoveLGTforHF_OK(long a, long b) {
        return (a > b) ? Float16.valueOf(1) : Float16.valueOf(2);
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    private static void testCMoveLGTforHF_OK(long[] a, long[] b, Float16[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] > b[i]) ? Float16.valueOf(1) : Float16.valueOf(2);
        }
    }




    private byte cmoveDGTforB_OK(double a, double b) {
        return (a > b) ? (byte)1 : (byte)2;
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR}, applyIfPlatform = {"riscv64", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_D, ">0",
                  IRNode.VECTOR_MASK_CMP_D, ">0",
                  IRNode.VECTOR_BLEND_I, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfPlatform = {"riscv64", "true"}, applyIfCPUFeature = {"rvv", "true"})
    private static void testCMoveDGTforB_OK(double[] a, double[] b, byte[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] > b[i]) ? (byte)1 : (byte)2;
        }
    }

    private boolean cmoveDGTforBool_OK(double a, double b) {
        return (a > b) ? true : false;
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR}, applyIfPlatform = {"riscv64", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_D, ">0",
                  IRNode.VECTOR_MASK_CMP_D, ">0",
                  IRNode.VECTOR_BLEND_I, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfPlatform = {"riscv64", "true"}, applyIfCPUFeature = {"rvv", "true"})
    private static void testCMoveDGTforBool_OK(double[] a, double[] b, boolean[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] > b[i]) ? true : false;
        }
    }

    private char cmoveDGTforChar_OK(double a, double b) {
        return (a > b) ? '1' : '2';
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR}, applyIfPlatform = {"riscv64", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_D, ">0",
                  IRNode.VECTOR_MASK_CMP_D, ">0",
                  IRNode.VECTOR_BLEND_I, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfPlatform = {"riscv64", "true"}, applyIfCPUFeature = {"rvv", "true"})
    private static void testCMoveDGTforChar_OK(double[] a, double[] b, char[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] > b[i]) ? '1' : '2';
        }
    }

    private short cmoveDGTforShort_OK(double a, double b) {
        return (a > b) ? (short)1 : (short)2;
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR}, applyIfPlatform = {"riscv64", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_D, ">0",
                  IRNode.VECTOR_MASK_CMP_D, ">0",
                  IRNode.VECTOR_BLEND_I, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfPlatform = {"riscv64", "true"}, applyIfCPUFeature = {"rvv", "true"})
    private static void testCMoveDGTforShort_OK(double[] a, double[] b, short[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] > b[i]) ? (short)1 : (short)2;
        }
    }

    private Float16 cmoveDGTforHF_OK(double a, double b) {
        return (a > b) ? Float16.valueOf(1) : Float16.valueOf(2);
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    private static void testCMoveDGTforHF_OK(double[] a, double[] b, Float16[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] > b[i]) ? Float16.valueOf(1) : Float16.valueOf(2);
        }
    }




    private byte cmoveFGTforB_OK(float a, float b) {
        return (a > b) ? (byte)1 : (byte)2;
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR}, applyIfPlatform = {"riscv64", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_F, ">0",
                  IRNode.VECTOR_MASK_CMP_F, ">0",
                  IRNode.VECTOR_BLEND_I, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfPlatform = {"riscv64", "true"}, applyIfCPUFeature = {"rvv", "true"})
    private static void testCMoveFGTforB_OK(float[] a, float[] b, byte[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] > b[i]) ? (byte)1 : (byte)2;
        }
    }

    private boolean cmoveFGTforBool_OK(float a, float b) {
        return (a > b) ? true : false;
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR}, applyIfPlatform = {"riscv64", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_F, ">0",
                  IRNode.VECTOR_MASK_CMP_F, ">0",
                  IRNode.VECTOR_BLEND_I, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfPlatform = {"riscv64", "true"}, applyIfCPUFeature = {"rvv", "true"})
    private static void testCMoveFGTforBool_OK(float[] a, float[] b, boolean[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] > b[i]) ? true : false;
        }
    }

    private char cmoveFGTforChar_OK(float a, float b) {
        return (a > b) ? '1' : '2';
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR}, applyIfPlatform = {"riscv64", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_F, ">0",
                  IRNode.VECTOR_MASK_CMP_F, ">0",
                  IRNode.VECTOR_BLEND_I, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfPlatform = {"riscv64", "true"}, applyIfCPUFeature = {"rvv", "true"})
    private static void testCMoveFGTforChar_OK(float[] a, float[] b, char[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] > b[i]) ? '1' : '2';
        }
    }

    private short cmoveFGTforShort_OK(float a, float b) {
        return (a > b) ? (short)1 : (short)2;
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR}, applyIfPlatform = {"riscv64", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_F, ">0",
                  IRNode.VECTOR_MASK_CMP_F, ">0",
                  IRNode.VECTOR_BLEND_I, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfPlatform = {"riscv64", "true"}, applyIfCPUFeature = {"rvv", "true"})
    private static void testCMoveFGTforShort_OK(float[] a, float[] b, short[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] > b[i]) ? (short)1 : (short)2;
        }
    }

    private Float16 cmoveFGTforHF_OK(float a, float b) {
        return (a > b) ? Float16.valueOf(1) : Float16.valueOf(2);
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    private static void testCMoveFGTforHF_OK(float[] a, float[] b, Float16[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] > b[i]) ? Float16.valueOf(1) : Float16.valueOf(2);
        }
    }





    private byte cmoveBGTforB_OK(byte a, byte b) {
        return (a > b) ? (byte)1 : (byte)2;
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR}, applyIfPlatform = {"riscv64", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_I, ">0",
                  IRNode.VECTOR_MASK_CMP_I, ">0",
                  IRNode.VECTOR_BLEND_I, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfPlatform = {"riscv64", "true"}, applyIfCPUFeature = {"rvv", "true"})
    private static void testCMoveBGTforB_OK(byte[] a, byte[] b, byte[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] > b[i]) ? (byte)1 : (byte)2;
        }
    }

    private boolean cmoveBGTforBool_OK(byte a, byte b) {
        return (a > b) ? true : false;
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR}, applyIfPlatform = {"riscv64", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_I, ">0",
                  IRNode.VECTOR_MASK_CMP_I, ">0",
                  IRNode.VECTOR_BLEND_I, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfPlatform = {"riscv64", "true"}, applyIfCPUFeature = {"rvv", "true"})
    private static void testCMoveBGTforBool_OK(byte[] a, byte[] b, boolean[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] > b[i]) ? true : false;
        }
    }

    private char cmoveBGTforChar_OK(byte a, byte b) {
        return (a > b) ? '1' : '2';
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR}, applyIfPlatform = {"riscv64", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_I, ">0",
                  IRNode.VECTOR_MASK_CMP_I, ">0",
                  IRNode.VECTOR_BLEND_I, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfPlatform = {"riscv64", "true"}, applyIfCPUFeature = {"rvv", "true"})
    private static void testCMoveBGTforChar_OK(byte[] a, byte[] b, char[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] > b[i]) ? '1' : '2';
        }
    }

    private short cmoveBGTforShort_OK(byte a, byte b) {
        return (a > b) ? (short)1 : (short)2;
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR}, applyIfPlatform = {"riscv64", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_I, ">0",
                  IRNode.VECTOR_MASK_CMP_I, ">0",
                  IRNode.VECTOR_BLEND_I, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfPlatform = {"riscv64", "true"}, applyIfCPUFeature = {"rvv", "true"})
    private static void testCMoveBGTforShort_OK(byte[] a, byte[] b, short[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] > b[i]) ? (short)1 : (short)2;
        }
    }

    private double cmoveBGTforD_OK(byte a, byte b) {
        return (a > b) ? 1.0 : 2.0;
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR}, applyIfPlatform = {"riscv64", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_I, ">0",
                  IRNode.VECTOR_MASK_CMP_I, ">0",
                  IRNode.VECTOR_BLEND_D, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfPlatform = {"riscv64", "true"}, applyIfCPUFeature = {"rvv", "true"})
    private static void testCMoveBGTforD_OK(byte[] a, byte[] b, double[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] > b[i]) ? 1.0 : 2.0;
        }
    }





    private byte cmoveCGTforB_OK(char a, char b) {
        return (a > b) ? (byte)1 : (byte)2;
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR}, applyIfPlatform = {"riscv64", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_I, ">0",
                  IRNode.VECTOR_MASK_CMP_I, ">0",
                  IRNode.VECTOR_BLEND_I, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfPlatform = {"riscv64", "true"}, applyIfCPUFeature = {"rvv", "true"})
    private static void testCMoveCGTforB_OK(char[] a, char[] b, byte[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] > b[i]) ? (byte)1 : (byte)2;
        }
    }

    private boolean cmoveCGTforBool_OK(char a, char b) {
        return (a > b) ? true : false;
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR}, applyIfPlatform = {"riscv64", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_I, ">0",
                  IRNode.VECTOR_MASK_CMP_I, ">0",
                  IRNode.VECTOR_BLEND_I, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfPlatform = {"riscv64", "true"}, applyIfCPUFeature = {"rvv", "true"})
    private static void testCMoveCGTforBool_OK(char[] a, char[] b, boolean[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] > b[i]) ? true : false;
        }
    }

    private char cmoveCGTforChar_OK(char a, char b) {
        return (a > b) ? '1' : '2';
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR}, applyIfPlatform = {"riscv64", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_I, ">0",
                  IRNode.VECTOR_MASK_CMP_I, ">0",
                  IRNode.VECTOR_BLEND_I, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfPlatform = {"riscv64", "true"}, applyIfCPUFeature = {"rvv", "true"})
    private static void testCMoveCGTforChar_OK(char[] a, char[] b, char[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] > b[i]) ? '1' : '2';
        }
    }

    private short cmoveCGTforShort_OK(char a, char b) {
        return (a > b) ? (short)1 : (short)2;
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR}, applyIfPlatform = {"riscv64", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_I, ">0",
                  IRNode.VECTOR_MASK_CMP_I, ">0",
                  IRNode.VECTOR_BLEND_I, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfPlatform = {"riscv64", "true"}, applyIfCPUFeature = {"rvv", "true"})
    private static void testCMoveCGTforShort_OK(char[] a, char[] b, short[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] > b[i]) ? (short)1 : (short)2;
        }
    }

    private double cmoveCGTforD_OK(char a, char b) {
        return (a > b) ? 1.0 : 2.0;
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR}, applyIfPlatform = {"riscv64", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_I, ">0",
                  IRNode.VECTOR_MASK_CMP_I, ">0",
                  IRNode.VECTOR_BLEND_D, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfPlatform = {"riscv64", "true"}, applyIfCPUFeature = {"rvv", "true"})
    private static void testCMoveCGTforD_OK(char[] a, char[] b, double[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] > b[i]) ? 1.0 : 2.0;
        }
    }






    private double cmoveULGTforD_OK(long a, long b) {
        return Long.compareUnsigned(a, b) > 0 ? 1.0 : 2.0;
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR}, applyIfPlatform = {"riscv64", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_I, ">0",
                  IRNode.VECTOR_MASK_CMP_I, ">0",
                  IRNode.VECTOR_BLEND_D, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfPlatform = {"riscv64", "true"}, applyIfCPUFeature = {"rvv", "true"})
    private static void testCMoveULGTforD_OK(long[] a, long[] b, double[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = Long.compareUnsigned(a[i], b[i]) > 0 ? 1.0 : 2.0;
        }
    }

    private double cmoveLGTforD_OK(long a, long b) {
        return Long.compare(a, b) > 0 ? 1.0 : 2.0;
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR}, applyIfPlatform = {"riscv64", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_I, ">0",
                  IRNode.VECTOR_MASK_CMP_I, ">0",
                  IRNode.VECTOR_BLEND_D, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfPlatform = {"riscv64", "true"}, applyIfCPUFeature = {"rvv", "true"})
    private static void testCMoveLGTforD_OK(long[] a, long[] b, double[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = Long.compare(a[i], b[i]) > 0 ? 1.0 : 2.0;
        }
    }



    // Extension: Compare 2 ILFD values, and pick from 2 ILFD values
    // Note:
    //   To guarantee that CMove is introduced, I need to perform the loads before the branch. To ensure they
    //   do not float down into the branches, I compute a value, and store it to r2 (same as r, except that the
    //   compilation does not know that).
    //   So far, vectorization works for:
    //      - CMoveF/D, with same data-width comparison (F/I for F, D/L for D)
    //      - CMoveI/L/F/D, with same or differet data-width (optionally on some platforms)
    @Test
    @IR(failOn = {IRNode.STORE_VECTOR}, applyIfPlatform = {"riscv64", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_I, ">0",
                  IRNode.VECTOR_MASK_CMP_I, ">0",
                  IRNode.VECTOR_BLEND_I, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfPlatform = {"riscv64", "true"}, applyIfCPUFeature = {"rvv", "true"})
    private static void testCMoveIGTforI(int[] a, int[] b, int[] c, int[] d, int[] r, int[] r2) {
        for (int i = 0; i < a.length; i++) {
            int cc = c[i];
            int dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] > b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR}, applyIfPlatform = {"riscv64", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_I, ">0",
                  IRNode.VECTOR_MASK_CMP_I, ">0",
                  IRNode.VECTOR_BLEND_L, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfPlatform = {"riscv64", "true"}, applyIfCPUFeature = {"rvv", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    private static void testCMoveIGTforF(int[] a, int[] b, float[] c, float[] d, float[] r, float[] r2) {
        for (int i = 0; i < a.length; i++) {
            float cc = c[i];
            float dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] > b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR}, applyIfPlatform = {"riscv64", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_I, ">0",
                  IRNode.VECTOR_MASK_CMP_I, ">0",
                  IRNode.VECTOR_BLEND_D, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfPlatform = {"riscv64", "true"}, applyIfCPUFeature = {"rvv", "true"})
    private static void testCMoveIGTforD(int[] a, int[] b, double[] c, double[] d, double[] r, double[] r2) {
        for (int i = 0; i < a.length; i++) {
            double cc = c[i];
            double dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] > b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR}, applyIfPlatform = {"riscv64", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_L, ">0",
                  IRNode.VECTOR_MASK_CMP_L, ">0",
                  IRNode.VECTOR_BLEND_I, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfPlatform = {"riscv64", "true"}, applyIfCPUFeature = {"rvv", "true"})
    private static void testCMoveLGTforI(long[] a, long[] b, int[] c, int[] d, int[] r, int[] r2) {
        for (int i = 0; i < a.length; i++) {
            int cc = c[i];
            int dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] > b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR}, applyIfPlatform = {"riscv64", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_L, ">0",
                  IRNode.VECTOR_MASK_CMP_L, ">0",
                  IRNode.VECTOR_BLEND_L, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfPlatform = {"riscv64", "true"}, applyIfCPUFeature = {"rvv", "true"})
    private static void testCMoveLGTforL(long[] a, long[] b, long[] c, long[] d, long[] r, long[] r2) {
        for (int i = 0; i < a.length; i++) {
            long cc = c[i];
            long dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] > b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR}, applyIfPlatform = {"riscv64", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_L, ">0",
                  IRNode.VECTOR_MASK_CMP_L, ">0",
                  IRNode.VECTOR_BLEND_F, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfPlatform = {"riscv64", "true"}, applyIfCPUFeature = {"rvv", "true"})
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
        applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true", "rvv", "true"})
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
    @IR(failOn = {IRNode.STORE_VECTOR}, applyIfPlatform = {"riscv64", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_F, ">0",
                  IRNode.VECTOR_MASK_CMP_F, ">0",
                  IRNode.VECTOR_BLEND_I, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfPlatform = {"riscv64", "true"}, applyIfCPUFeature = {"rvv", "true"})
    private static void testCMoveFGTforI(float[] a, float[] b, int[] c, int[] d, int[] r, int[] r2) {
        for (int i = 0; i < a.length; i++) {
            int cc = c[i];
            int dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] > b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR}, applyIfPlatform = {"riscv64", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_F, ">0",
                  IRNode.VECTOR_MASK_CMP_F, ">0",
                  IRNode.VECTOR_BLEND_L, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfPlatform = {"riscv64", "true"}, applyIfCPUFeature = {"rvv", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    private static void testCMoveFGTforF(float[] a, float[] b, float[] c, float[] d, float[] r, float[] r2) {
        for (int i = 0; i < a.length; i++) {
            float cc = c[i];
            float dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] > b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR}, applyIfPlatform = {"riscv64", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_F, ">0",
                  IRNode.VECTOR_MASK_CMP_F, ">0",
                  IRNode.VECTOR_BLEND_D, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfPlatform = {"riscv64", "true"}, applyIfCPUFeature = {"rvv", "true"})
    private static void testCMoveFGTforD(float[] a, float[] b, double[] c, double[] d, double[] r, double[] r2) {
        for (int i = 0; i < a.length; i++) {
            double cc = c[i];
            double dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] > b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR}, applyIfPlatform = {"riscv64", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_D, ">0",
                  IRNode.VECTOR_MASK_CMP_D, ">0",
                  IRNode.VECTOR_BLEND_I, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfPlatform = {"riscv64", "true"}, applyIfCPUFeature = {"rvv", "true"})
    private static void testCMoveDGTforI(double[] a, double[] b, int[] c, int[] d, int[] r, int[] r2) {
        for (int i = 0; i < a.length; i++) {
            int cc = c[i];
            int dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] > b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR}, applyIfPlatform = {"riscv64", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_D, ">0",
                  IRNode.VECTOR_MASK_CMP_D, ">0",
                  IRNode.VECTOR_BLEND_L, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfPlatform = {"riscv64", "true"}, applyIfCPUFeature = {"rvv", "true"})
    private static void testCMoveDGTforL(double[] a, double[] b, long[] c, long[] d, long[] r, long[] r2) {
        for (int i = 0; i < a.length; i++) {
            long cc = c[i];
            long dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] > b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR}, applyIfPlatform = {"riscv64", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_D, ">0",
                  IRNode.VECTOR_MASK_CMP_D, ">0",
                  IRNode.VECTOR_BLEND_F, ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfPlatform = {"riscv64", "true"}, applyIfCPUFeature = {"rvv", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
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
                 "testCMoveFGTforFCmpCon2"

                 ,
                 "testCMoveIGTforI_OK", "testCMoveIGTforL_OK", "testCMoveIGTforB_OK", "testCMoveIGTforBool_OK",
                  "testCMoveIGTforChar_OK", "testCMoveIGTforShort_OK", "testCMoveIGTforHF_OK",
                  "testCMoveLGTforL_OK", "testCMoveLGTforI_OK", "testCMoveLGTforB_OK", "testCMoveLGTforBool_OK",
                  "testCMoveLGTforChar_OK", "testCMoveLGTforShort_OK", "testCMoveLGTforHF_OK",
                  "testCMoveDGTforB_OK", "testCMoveDGTforBool_OK",
                  "testCMoveDGTforChar_OK", "testCMoveDGTforShort_OK", "testCMoveDGTforHF_OK",
                  "testCMoveFGTforB_OK", "testCMoveFGTforBool_OK",
                  "testCMoveFGTforChar_OK", "testCMoveFGTforShort_OK", "testCMoveFGTforHF_OK",
                  "testCMoveBGTforB_OK", "testCMoveBGTforBool_OK",
                  "testCMoveBGTforChar_OK", "testCMoveBGTforShort_OK", "testCMoveBGTforD_OK",
                  "testCMoveCGTforB_OK", "testCMoveCGTforBool_OK",
                  "testCMoveCGTforChar_OK", "testCMoveCGTforShort_OK", "testCMoveCGTforD_OK",
                  "testCMoveULGTforD_OK",
                  "testCMoveLGTforD_OK",})
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

        byte[] aB = new byte[SIZE];
        byte[] bB = new byte[SIZE];
        byte[] cB = new byte[SIZE];
        boolean[] cBool = new boolean[SIZE];
        char[] aChar = new char[SIZE];
        char[] bChar = new char[SIZE];
        char[] cChar = new char[SIZE];
        short[] cShort = new short[SIZE];
        Float16[] cHF = new Float16[SIZE];

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






        init(aB);
        init(bB);
        init(cB);
        init(cBool);
        init(aChar);
        init(bChar);
        init(cChar);
        init(cShort);
        init(cHF);

        testCMoveIGTforI_OK(aI, bI, cI);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(cI[i], cmoveIGTforI_OK(aI[i], bI[i]));
        }
        testCMoveIGTforL_OK(aI, bI, cL);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(cL[i], cmoveIGTforL_OK(aI[i], bI[i]));
        }
        testCMoveIGTforB_OK(aI, bI, cB);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(cB[i], cmoveIGTforB_OK(aI[i], bI[i]));
        }
        testCMoveIGTforBool_OK(aI, bI, cBool);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(cBool[i], cmoveIGTforBool_OK(aI[i], bI[i]));
        }
        testCMoveIGTforChar_OK(aI, bI, cChar);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(cChar[i], cmoveIGTforChar_OK(aI[i], bI[i]));
        }
        testCMoveIGTforShort_OK(aI, bI, cShort);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(cShort[i], cmoveIGTforShort_OK(aI[i], bI[i]));
        }
        testCMoveIGTforHF_OK(aI, bI, cHF);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(cHF[i], cmoveIGTforHF_OK(aI[i], bI[i]));
        }



        testCMoveLGTforL_OK(aL, bL, cL);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(cL[i], cmoveLGTforL_OK(aL[i], bL[i]));
        }
        testCMoveLGTforI_OK(aL, bL, cI);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(cI[i], cmoveLGTforI_OK(aL[i], bL[i]));
        }
        testCMoveLGTforB_OK(aL, bL, cB);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(cB[i], cmoveLGTforB_OK(aL[i], bL[i]));
        }
        testCMoveLGTforBool_OK(aL, bL, cBool);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(cBool[i], cmoveLGTforBool_OK(aL[i], bL[i]));
        }
        testCMoveLGTforChar_OK(aL, bL, cChar);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(cChar[i], cmoveLGTforChar_OK(aL[i], bL[i]));
        }
        testCMoveLGTforShort_OK(aL, bL, cShort);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(cShort[i], cmoveLGTforShort_OK(aL[i], bL[i]));
        }
        testCMoveLGTforHF_OK(aL, bL, cHF);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(cHF[i], cmoveLGTforHF_OK(aL[i], bL[i]));
        }




        testCMoveDGTforB_OK(aD, bD, cB);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(cB[i], cmoveDGTforB_OK(aD[i], bD[i]));
        }
        testCMoveDGTforBool_OK(aD, bD, cBool);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(cBool[i], cmoveDGTforBool_OK(aD[i], bD[i]));
        }
        testCMoveDGTforChar_OK(aD, bD, cChar);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(cChar[i], cmoveDGTforChar_OK(aD[i], bD[i]));
        }
        testCMoveDGTforShort_OK(aD, bD, cShort);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(cShort[i], cmoveDGTforShort_OK(aD[i], bD[i]));
        }
        testCMoveDGTforHF_OK(aD, bD, cHF);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(cHF[i], cmoveDGTforHF_OK(aD[i], bD[i]));
        }




        testCMoveFGTforB_OK(aF, bF, cB);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(cB[i], cmoveFGTforB_OK(aF[i], bF[i]));
        }
        testCMoveFGTforBool_OK(aF, bF, cBool);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(cBool[i], cmoveFGTforBool_OK(aF[i], bF[i]));
        }
        testCMoveFGTforChar_OK(aF, bF, cChar);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(cChar[i], cmoveFGTforChar_OK(aF[i], bF[i]));
        }
        testCMoveFGTforShort_OK(aF, bF, cShort);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(cShort[i], cmoveFGTforShort_OK(aF[i], bF[i]));
        }
        testCMoveFGTforHF_OK(aF, bF, cHF);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(cHF[i], cmoveFGTforHF_OK(aF[i], bF[i]));
        }




        init(aB);
        init(bB);
        testCMoveBGTforB_OK(aB, bB, cB);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(cB[i], cmoveBGTforB_OK(aB[i], bB[i]));
        }
        testCMoveBGTforBool_OK(aB, bB, cBool);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(cBool[i], cmoveBGTforBool_OK(aB[i], bB[i]));
        }
        testCMoveBGTforChar_OK(aB, bB, cChar);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(cChar[i], cmoveBGTforChar_OK(aB[i], bB[i]));
        }
        testCMoveBGTforShort_OK(aB, bB, cShort);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(cShort[i], cmoveBGTforShort_OK(aB[i], bB[i]));
        }
        testCMoveBGTforD_OK(aB, bB, cD);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(cD[i], cmoveBGTforD_OK(aB[i], bB[i]));
        }


        testCMoveLGTforD_OK(aL, bL, cD);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(cD[i], cmoveLGTforD_OK(aL[i], bL[i]));
        }

        testCMoveULGTforD_OK(aL, bL, cD);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(cD[i], cmoveULGTforD_OK(aL[i], bL[i]));
        }




        init(aChar);
        init(bChar);
        testCMoveCGTforB_OK(aChar, bChar, cB);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(cB[i], cmoveCGTforB_OK(aChar[i], bChar[i]));
        }
        testCMoveCGTforBool_OK(aChar, bChar, cBool);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(cBool[i], cmoveCGTforBool_OK(aChar[i], bChar[i]));
        }
        testCMoveCGTforChar_OK(aChar, bChar, cChar);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(cChar[i], cmoveCGTforChar_OK(aChar[i], bChar[i]));
        }
        testCMoveCGTforShort_OK(aChar, bChar, cShort);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(cShort[i], cmoveCGTforShort_OK(aChar[i], bChar[i]));
        }
        testCMoveCGTforD_OK(aChar, bChar, cD);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(cD[i], cmoveCGTforD_OK(aChar[i], bChar[i]));
        }







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

    private static void init(byte[] a) {
        for (int i = 0; i < SIZE; i++) {
            a[i] = (byte)RANDOM.nextInt();
        }
    }
    private static void init(short[] a) {
        for (int i = 0; i < SIZE; i++) {
            a[i] = (short)RANDOM.nextInt();
        }
    }
    private static void init(char[] a) {
        for (int i = 0; i < SIZE; i++) {
            a[i] = (char)RANDOM.nextInt();
        }
    }
    private static void init(Float16[] a) {
        for (int i = 0; i < SIZE; i++) {
            a[i] = Float16.valueOf(RANDOM.nextInt());
        }
    }

    private static void init(boolean[] a) {
        for (int i = 0; i < SIZE; i++) {
            a[i] = RANDOM.nextBoolean();
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
