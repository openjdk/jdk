/*
 * Copyright (c) 2022, 2025, Arm Limited. All rights reserved.
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8289422 8306088 8313720
 * @key randomness
 * @summary Auto-vectorization enhancement to support vector conditional move.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestConditionalMove
 */

public class TestConditionalMove {
    final private static int SIZE = 1024;
    private static final Random RANDOM = Utils.getRandomInstance();

    public static void main(String[] args) {
        // Vectorizaion: +UseCMoveUnconditionally, +UseVectorCmov
        // Cross-product: +-AlignVector and +-UseCompactObjectHeaders
        TestFramework.runWithFlags("-XX:+UseCMoveUnconditionally", "-XX:+UseVectorCmov",
                                   "-XX:-UseCompactObjectHeaders", "-XX:-AlignVector");
        TestFramework.runWithFlags("-XX:+UseCMoveUnconditionally", "-XX:+UseVectorCmov",
                                   "-XX:-UseCompactObjectHeaders", "-XX:+AlignVector");
        TestFramework.runWithFlags("-XX:+UseCMoveUnconditionally", "-XX:+UseVectorCmov",
                                   "-XX:+UseCompactObjectHeaders", "-XX:-AlignVector");
        TestFramework.runWithFlags("-XX:+UseCMoveUnconditionally", "-XX:+UseVectorCmov",
                                   "-XX:+UseCompactObjectHeaders", "-XX:+AlignVector");

        // Scalar: +UseCMoveUnconditionally, -UseVectorCmov
        TestFramework.runWithFlags("-XX:+UseCMoveUnconditionally", "-XX:-UseVectorCmov",
                                   "-XX:+UnlockExperimentalVMOptions", "-XX:-UseCompactObjectHeaders");
        TestFramework.runWithFlags("-XX:+UseCMoveUnconditionally", "-XX:-UseVectorCmov",
                                   "-XX:+UnlockExperimentalVMOptions", "-XX:+UseCompactObjectHeaders");
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
    // Signed comparison: I/L
    //    I for I
    private int cmoveIEQforI(int a, int b, int c, int d) {
        return (a == b) ? c : d;
    }

    private int cmoveINEforI(int a, int b, int c, int d) {
        return (a != b) ? c : d;
    }

    private int cmoveIGTforI(int a, int b, int c, int d) {
        return (a > b) ? c : d;
    }

    private int cmoveIGEforI(int a, int b, int c, int d) {
        return (a >= b) ? c : d;
    }

    private int cmoveILTforI(int a, int b, int c, int d) {
        return (a < b) ? c : d;
    }

    private int cmoveILEforI(int a, int b, int c, int d) {
        return (a <= b) ? c : d;
    }

    //    I for L
    private long cmoveIEQforL(int a, int b, long c, long d) {
        return (a == b) ? c : d;
    }

    private long cmoveINEforL(int a, int b, long c, long d) {
        return (a != b) ? c : d;
    }

    private long cmoveIGTforL(int a, int b, long c, long d) {
        return (a > b) ? c : d;
    }

    private long cmoveIGEforL(int a, int b, long c, long d) {
        return (a >= b) ? c : d;
    }

    private long cmoveILTforL(int a, int b, long c, long d) {
        return (a < b) ? c : d;
    }

    private long cmoveILEforL(int a, int b, long c, long d) {
        return (a <= b) ? c : d;
    }

    //    I for F
    private float cmoveIEQforF(int a, int b, float c, float d) {
        return (a == b) ? c : d;
    }

    private float cmoveINEforF(int a, int b, float c, float d) {
        return (a != b) ? c : d;
    }

    private float cmoveIGTforF(int a, int b, float c, float d) {
        return (a > b) ? c : d;
    }

    private float cmoveIGEforF(int a, int b, float c, float d) {
        return (a >= b) ? c : d;
    }

    private float cmoveILTforF(int a, int b, float c, float d) {
        return (a < b) ? c : d;
    }

    private float cmoveILEforF(int a, int b, float c, float d) {
        return (a <= b) ? c : d;
    }

    //    I for D
    private double cmoveIEQforD(int a, int b, double c, double d) {
        return (a == b) ? c : d;
    }

    private double cmoveINEforD(int a, int b, double c, double d) {
        return (a != b) ? c : d;
    }

    private double cmoveIGTforD(int a, int b, double c, double d) {
        return (a > b) ? c : d;
    }

    private double cmoveIGEforD(int a, int b, double c, double d) {
        return (a >= b) ? c : d;
    }

    private double cmoveILTforD(int a, int b, double c, double d) {
        return (a < b) ? c : d;
    }

    private double cmoveILEforD(int a, int b, double c, double d) {
        return (a <= b) ? c : d;
    }

    //    L for I
    private int cmoveLEQforI(long a, long b, int c, int d) {
        return (a == b) ? c : d;
    }

    private int cmoveLNEforI(long a, long b, int c, int d) {
        return (a != b) ? c : d;
    }

    private int cmoveLGTforI(long a, long b, int c, int d) {
        return (a > b) ? c : d;
    }

    private int cmoveLGEforI(long a, long b, int c, int d) {
        return (a >= b) ? c : d;
    }

    private int cmoveLLTforI(long a, long b, int c, int d) {
        return (a < b) ? c : d;
    }

    private int cmoveLLEforI(long a, long b, int c, int d) {
        return (a <= b) ? c : d;
    }

    //    L for L
    private long cmoveLEQforL(long a, long b, long c, long d) {
        return (a == b) ? c : d;
    }

    private long cmoveLNEforL(long a, long b, long c, long d) {
        return (a != b) ? c : d;
    }

    private long cmoveLGTforL(long a, long b, long c, long d) {
        return (a > b) ? c : d;
    }

    private long cmoveLGEforL(long a, long b, long c, long d) {
        return (a >= b) ? c : d;
    }

    private long cmoveLLTforL(long a, long b, long c, long d) {
        return (a < b) ? c : d;
    }

    private long cmoveLLEforL(long a, long b, long c, long d) {
        return (a <= b) ? c : d;
    }

    //    L for F
    private float cmoveLEQforF(long a, long b, float c, float d) {
        return (a == b) ? c : d;
    }

    private float cmoveLNEforF(long a, long b, float c, float d) {
        return (a != b) ? c : d;
    }

    private float cmoveLGTforF(long a, long b, float c, float d) {
        return (a > b) ? c : d;
    }

    private float cmoveLGEforF(long a, long b, float c, float d) {
        return (a >= b) ? c : d;
    }

    private float cmoveLLTforF(long a, long b, float c, float d) {
        return (a < b) ? c : d;
    }

    private float cmoveLLEforF(long a, long b, float c, float d) {
        return (a <= b) ? c : d;
    }

    //    L for D
    private double cmoveLEQforD(long a, long b, double c, double d) {
        return (a == b) ? c : d;
    }

    private double cmoveLNEforD(long a, long b, double c, double d) {
        return (a != b) ? c : d;
    }

    private double cmoveLGTforD(long a, long b, double c, double d) {
        return (a > b) ? c : d;
    }

    private double cmoveLGEforD(long a, long b, double c, double d) {
        return (a >= b) ? c : d;
    }

    private double cmoveLLTforD(long a, long b, double c, double d) {
        return (a < b) ? c : d;
    }

    private double cmoveLLEforD(long a, long b, double c, double d) {
        return (a <= b) ? c : d;
    }

    // Unsigned comparison: I/L
    //    I for I
    private int cmoveUIEQforI(int a, int b, int c, int d) {
        return Integer.compareUnsigned(a, b) == 0 ? c : d;
    }

    private int cmoveUINEforI(int a, int b, int c, int d) {
        return Integer.compareUnsigned(a, b) != 0 ? c : d;
    }

    private int cmoveUIGTforI(int a, int b, int c, int d) {
        return Integer.compareUnsigned(a, b) > 0 ? c : d;
    }

    private int cmoveUIGEforI(int a, int b, int c, int d) {
        return Integer.compareUnsigned(a, b) >= 0 ? c : d;
    }

    private int cmoveUILTforI(int a, int b, int c, int d) {
        return Integer.compareUnsigned(a, b) < 0 ? c : d;
    }

    private int cmoveUILEforI(int a, int b, int c, int d) {
        return Integer.compareUnsigned(a, b) <= 0 ? c : d;
    }

    //    I for L
    private long cmoveUIEQforL(int a, int b, long c, long d) {
        return Integer.compareUnsigned(a, b) == 0 ? c : d;
    }

    private long cmoveUINEforL(int a, int b, long c, long d) {
        return Integer.compareUnsigned(a, b) != 0 ? c : d;
    }

    private long cmoveUIGTforL(int a, int b, long c, long d) {
        return Integer.compareUnsigned(a, b) > 0 ? c : d;
    }

    private long cmoveUIGEforL(int a, int b, long c, long d) {
        return Integer.compareUnsigned(a, b) >= 0 ? c : d;
    }

    private long cmoveUILTforL(int a, int b, long c, long d) {
        return Integer.compareUnsigned(a, b) < 0 ? c : d;
    }

    private long cmoveUILEforL(int a, int b, long c, long d) {
        return Integer.compareUnsigned(a, b) <= 0 ? c : d;
    }

    //    I for F
    private float cmoveUIEQforF(int a, int b, float c, float d) {
        return Integer.compareUnsigned(a, b) == 0 ? c : d;
    }

    private float cmoveUINEforF(int a, int b, float c, float d) {
        return Integer.compareUnsigned(a, b) != 0 ? c : d;
    }

    private float cmoveUIGTforF(int a, int b, float c, float d) {
        return Integer.compareUnsigned(a, b) > 0 ? c : d;
    }

    private float cmoveUIGEforF(int a, int b, float c, float d) {
        return Integer.compareUnsigned(a, b) >= 0 ? c : d;
    }

    private float cmoveUILTforF(int a, int b, float c, float d) {
        return Integer.compareUnsigned(a, b) < 0 ? c : d;
    }

    private float cmoveUILEforF(int a, int b, float c, float d) {
        return Integer.compareUnsigned(a, b) <= 0 ? c : d;
    }

    //    I for D
    private double cmoveUIEQforD(int a, int b, double c, double d) {
        return Integer.compareUnsigned(a, b) == 0 ? c : d;
    }

    private double cmoveUINEforD(int a, int b, double c, double d) {
        return Integer.compareUnsigned(a, b) != 0 ? c : d;
    }

    private double cmoveUIGTforD(int a, int b, double c, double d) {
        return Integer.compareUnsigned(a, b) > 0 ? c : d;
    }

    private double cmoveUIGEforD(int a, int b, double c, double d) {
        return Integer.compareUnsigned(a, b) >= 0 ? c : d;
    }

    private double cmoveUILTforD(int a, int b, double c, double d) {
        return Integer.compareUnsigned(a, b) < 0 ? c : d;
    }

    private double cmoveUILEforD(int a, int b, double c, double d) {
        return Integer.compareUnsigned(a, b) <= 0 ? c : d;
    }

    //    L for I
    private int cmoveULEQforI(long a, long b, int c, int d) {
        return Long.compareUnsigned(a, b) == 0 ? c : d;
    }

    private int cmoveULNEforI(long a, long b, int c, int d) {
        return Long.compareUnsigned(a, b) != 0 ? c : d;
    }

    private int cmoveULGTforI(long a, long b, int c, int d) {
        return Long.compareUnsigned(a, b) > 0 ? c : d;
    }

    private int cmoveULGEforI(long a, long b, int c, int d) {
        return Long.compareUnsigned(a, b) >= 0 ? c : d;
    }

    private int cmoveULLTforI(long a, long b, int c, int d) {
        return Long.compareUnsigned(a, b) < 0 ? c : d;
    }

    private int cmoveULLEforI(long a, long b, int c, int d) {
        return Long.compareUnsigned(a, b) <= 0 ? c : d;
    }

    //    L for L
    private long cmoveULEQforL(long a, long b, long c, long d) {
        return Long.compareUnsigned(a, b) == 0 ? c : d;
    }

    private long cmoveULNEforL(long a, long b, long c, long d) {
        return Long.compareUnsigned(a, b) != 0 ? c : d;
    }

    private long cmoveULGTforL(long a, long b, long c, long d) {
        return Long.compareUnsigned(a, b) > 0 ? c : d;
    }

    private long cmoveULGEforL(long a, long b, long c, long d) {
        return Long.compareUnsigned(a, b) >= 0 ? c : d;
    }

    private long cmoveULLTforL(long a, long b, long c, long d) {
        return Long.compareUnsigned(a, b) < 0 ? c : d;
    }

    private long cmoveULLEforL(long a, long b, long c, long d) {
        return Long.compareUnsigned(a, b) <= 0 ? c : d;
    }

    //    L for F
    private float cmoveULEQforF(long a, long b, float c, float d) {
        return Long.compareUnsigned(a, b) == 0 ? c : d;
    }

    private float cmoveULNEforF(long a, long b, float c, float d) {
        return Long.compareUnsigned(a, b) != 0 ? c : d;
    }

    private float cmoveULGTforF(long a, long b, float c, float d) {
        return Long.compareUnsigned(a, b) > 0 ? c : d;
    }

    private float cmoveULGEforF(long a, long b, float c, float d) {
        return Long.compareUnsigned(a, b) >= 0 ? c : d;
    }

    private float cmoveULLTforF(long a, long b, float c, float d) {
        return Long.compareUnsigned(a, b) < 0 ? c : d;
    }

    private float cmoveULLEforF(long a, long b, float c, float d) {
        return Long.compareUnsigned(a, b) <= 0 ? c : d;
    }

    //    L for D
    private double cmoveULEQforD(long a, long b, double c, double d) {
        return Long.compareUnsigned(a, b) == 0 ? c : d;
    }

    private double cmoveULNEforD(long a, long b, double c, double d) {
        return Long.compareUnsigned(a, b) != 0 ? c : d;
    }

    private double cmoveULGTforD(long a, long b, double c, double d) {
        return Long.compareUnsigned(a, b) > 0 ? c : d;
    }

    private double cmoveULGEforD(long a, long b, double c, double d) {
        return Long.compareUnsigned(a, b) >= 0 ? c : d;
    }

    private double cmoveULLTforD(long a, long b, double c, double d) {
        return Long.compareUnsigned(a, b) < 0 ? c : d;
    }

    private double cmoveULLEforD(long a, long b, double c, double d) {
        return Long.compareUnsigned(a, b) <= 0 ? c : d;
    }

    // Float comparison
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

    // Double comparison
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_F, ">0", IRNode.CMP_F, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_F, ">0", IRNode.CMP_F, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_F, ">0", IRNode.CMP_F, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_F, ">0", IRNode.CMP_F, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_F, ">0", IRNode.CMP_F, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_D, ">0", IRNode.CMP_D, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_D, ">0", IRNode.CMP_D, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_D, ">0", IRNode.CMP_D, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_D, ">0", IRNode.CMP_D, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_D, ">0", IRNode.CMP_D, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_F, ">0", IRNode.CMP_F, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_F, ">0", IRNode.CMP_F, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_F, ">0", IRNode.CMP_F, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_F, ">0", IRNode.CMP_F, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_F, ">0", IRNode.CMP_F, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_F, ">0", IRNode.CMP_F, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
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
        applyIfAnd = {"UseCompactObjectHeaders", "false", "UseVectorCmov", "true"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    @IR(counts = {IRNode.LOAD_VECTOR_F, ">0",
                    IRNode.VECTOR_MASK_CMP_F, ">0",
                    IRNode.VECTOR_BLEND_F, ">0",
                    IRNode.STORE_VECTOR, ">0"},
        applyIfAnd = {"AlignVector", "false", "UseVectorCmov", "true"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_F, ">0", IRNode.CMP_F, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
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
        applyIfAnd = {"UseCompactObjectHeaders", "false", "UseVectorCmov", "true"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    @IR(counts = {IRNode.LOAD_VECTOR_F, ">0",
                    IRNode.VECTOR_MASK_CMP_F, ">0",
                    IRNode.VECTOR_BLEND_F, ">0",
                    IRNode.STORE_VECTOR, ">0"},
        applyIfAnd = {"AlignVector", "false", "UseVectorCmov", "true"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_F, ">0", IRNode.CMP_F, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_F, ">0", IRNode.CMP_F, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_F, ">0", IRNode.CMP_F, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_D, ">0", IRNode.CMP_D, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_D, ">0", IRNode.CMP_D, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_D, ">0", IRNode.CMP_D, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_D, ">0", IRNode.CMP_D, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_D, ">0", IRNode.CMP_D, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_D, ">0", IRNode.CMP_D, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_D, ">0", IRNode.CMP_D, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_D, ">0", IRNode.CMP_D, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_D, ">0", IRNode.CMP_D, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_D, ">0", IRNode.CMP_D, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
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
    //   TODO: enable CMOVE_I/L verification when it's guaranteed to generate CMOVE_I/L, JDK-8371984.
    //
    // Signed comparison: I/L
    //     I fo I
    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // @IR(counts = {IRNode.CMOVE_I, ">0", IRNode.CMP_I, ">0"},
    //     applyIf = {"UseVectorCmov", "false"},
    //     applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveIEQforI(int[] a, int[] b, int[] c, int[] d, int[] r, int[] r2) {
        for (int i = 0; i < a.length; i++) {
            int cc = c[i];
            int dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] == b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // @IR(counts = {IRNode.CMOVE_I, ">0", IRNode.CMP_I, ">0"},
    //     applyIf = {"UseVectorCmov", "false"},
    //     applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveINEforI(int[] a, int[] b, int[] c, int[] d, int[] r, int[] r2) {
        for (int i = 0; i < a.length; i++) {
            int cc = c[i];
            int dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] != b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // @IR(counts = {IRNode.CMOVE_I, ">0", IRNode.CMP_I, ">0"},
    //     applyIf = {"UseVectorCmov", "false"},
    //     applyIfPlatform = {"riscv64", "true"})
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
    // @IR(counts = {IRNode.CMOVE_I, ">0", IRNode.CMP_I, ">0"},
    //     applyIf = {"UseVectorCmov", "false"},
    //     applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveIGEforI(int[] a, int[] b, int[] c, int[] d, int[] r, int[] r2) {
        for (int i = 0; i < a.length; i++) {
            int cc = c[i];
            int dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] >= b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // @IR(counts = {IRNode.CMOVE_I, ">0", IRNode.CMP_I, ">0"},
    //     applyIf = {"UseVectorCmov", "false"},
    //     applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveILTforI(int[] a, int[] b, int[] c, int[] d, int[] r, int[] r2) {
        for (int i = 0; i < a.length; i++) {
            int cc = c[i];
            int dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] < b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // @IR(counts = {IRNode.CMOVE_I, ">0", IRNode.CMP_I, ">0"},
    //     applyIf = {"UseVectorCmov", "false"},
    //     applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveILEforI(int[] a, int[] b, int[] c, int[] d, int[] r, int[] r2) {
        for (int i = 0; i < a.length; i++) {
            int cc = c[i];
            int dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] <= b[i]) ? cc : dd;
        }
    }

    //     I fo L
    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // @IR(counts = {IRNode.CMOVE_L, ">0", IRNode.CMP_I, ">0"},
    //     applyIf = {"UseVectorCmov", "false"},
    //     applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveIEQforL(int[] a, int[] b, long[] c, long[] d, long[] r, long[] r2) {
        for (int i = 0; i < a.length; i++) {
            long cc = c[i];
            long dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] == b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // @IR(counts = {IRNode.CMOVE_L, ">0", IRNode.CMP_I, ">0"},
    //     applyIf = {"UseVectorCmov", "false"},
    //     applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveINEforL(int[] a, int[] b, long[] c, long[] d, long[] r, long[] r2) {
        for (int i = 0; i < a.length; i++) {
            long cc = c[i];
            long dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] != b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // @IR(counts = {IRNode.CMOVE_L, ">0", IRNode.CMP_I, ">0"},
    //     applyIf = {"UseVectorCmov", "false"},
    //     applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveIGTforL(int[] a, int[] b, long[] c, long[] d, long[] r, long[] r2) {
        for (int i = 0; i < a.length; i++) {
            long cc = c[i];
            long dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] > b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // @IR(counts = {IRNode.CMOVE_L, ">0", IRNode.CMP_I, ">0"},
    //     applyIf = {"UseVectorCmov", "false"},
    //     applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveIGEforL(int[] a, int[] b, long[] c, long[] d, long[] r, long[] r2) {
        for (int i = 0; i < a.length; i++) {
            long cc = c[i];
            long dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] >= b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // @IR(counts = {IRNode.CMOVE_L, ">0", IRNode.CMP_I, ">0"},
    //     applyIf = {"UseVectorCmov", "false"},
    //     applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveILTforL(int[] a, int[] b, long[] c, long[] d, long[] r, long[] r2) {
        for (int i = 0; i < a.length; i++) {
            long cc = c[i];
            long dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] < b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // @IR(counts = {IRNode.CMOVE_L, ">0", IRNode.CMP_I, ">0"},
    //     applyIf = {"UseVectorCmov", "false"},
    //     applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveILEforL(int[] a, int[] b, long[] c, long[] d, long[] r, long[] r2) {
        for (int i = 0; i < a.length; i++) {
            long cc = c[i];
            long dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] <= b[i]) ? cc : dd;
        }
    }

    //     I fo F
    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I,     IRNode.VECTOR_SIZE + "min(max_int, max_float)", ">0",
                  IRNode.LOAD_VECTOR_F,     IRNode.VECTOR_SIZE + "min(max_int, max_float)", ">0",
                  IRNode.VECTOR_MASK_CMP_I, IRNode.VECTOR_SIZE + "min(max_int, max_float)", ">0",
                  IRNode.VECTOR_BLEND_F,    IRNode.VECTOR_SIZE + "min(max_int, max_float)", ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_F, ">0", IRNode.CMP_I, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveIEQforF(int[] a, int[] b, float[] c, float[] d, float[] r, float[] r2) {
        for (int i = 0; i < a.length; i++) {
            float cc = c[i];
            float dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] == b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I,     IRNode.VECTOR_SIZE + "min(max_int, max_float)", ">0",
                  IRNode.LOAD_VECTOR_F,     IRNode.VECTOR_SIZE + "min(max_int, max_float)", ">0",
                  IRNode.VECTOR_MASK_CMP_I, IRNode.VECTOR_SIZE + "min(max_int, max_float)", ">0",
                  IRNode.VECTOR_BLEND_F,    IRNode.VECTOR_SIZE + "min(max_int, max_float)", ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_F, ">0", IRNode.CMP_I, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveINEforF(int[] a, int[] b, float[] c, float[] d, float[] r, float[] r2) {
        for (int i = 0; i < a.length; i++) {
            float cc = c[i];
            float dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] != b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I,     IRNode.VECTOR_SIZE + "min(max_int, max_float)", ">0",
                  IRNode.LOAD_VECTOR_F,     IRNode.VECTOR_SIZE + "min(max_int, max_float)", ">0",
                  IRNode.VECTOR_MASK_CMP_I, IRNode.VECTOR_SIZE + "min(max_int, max_float)", ">0",
                  IRNode.VECTOR_BLEND_F,    IRNode.VECTOR_SIZE + "min(max_int, max_float)", ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_F, ">0", IRNode.CMP_I, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveIGTforF(int[] a, int[] b, float[] c, float[] d, float[] r, float[] r2) {
        for (int i = 0; i < a.length; i++) {
            float cc = c[i];
            float dd = d[i];
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_F, ">0", IRNode.CMP_I, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveIGEforF(int[] a, int[] b, float[] c, float[] d, float[] r, float[] r2) {
        for (int i = 0; i < a.length; i++) {
            float cc = c[i];
            float dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] >= b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I,     IRNode.VECTOR_SIZE + "min(max_int, max_float)", ">0",
                  IRNode.LOAD_VECTOR_F,     IRNode.VECTOR_SIZE + "min(max_int, max_float)", ">0",
                  IRNode.VECTOR_MASK_CMP_I, IRNode.VECTOR_SIZE + "min(max_int, max_float)", ">0",
                  IRNode.VECTOR_BLEND_F,    IRNode.VECTOR_SIZE + "min(max_int, max_float)", ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_F, ">0", IRNode.CMP_I, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveILTforF(int[] a, int[] b, float[] c, float[] d, float[] r, float[] r2) {
        for (int i = 0; i < a.length; i++) {
            float cc = c[i];
            float dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] < b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I,     IRNode.VECTOR_SIZE + "min(max_int, max_float)", ">0",
                  IRNode.LOAD_VECTOR_F,     IRNode.VECTOR_SIZE + "min(max_int, max_float)", ">0",
                  IRNode.VECTOR_MASK_CMP_I, IRNode.VECTOR_SIZE + "min(max_int, max_float)", ">0",
                  IRNode.VECTOR_BLEND_F,    IRNode.VECTOR_SIZE + "min(max_int, max_float)", ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_F, ">0", IRNode.CMP_I, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveILEforF(int[] a, int[] b, float[] c, float[] d, float[] r, float[] r2) {
        for (int i = 0; i < a.length; i++) {
            float cc = c[i];
            float dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] <= b[i]) ? cc : dd;
        }
    }

    //     I fo D
    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    @IR(counts = {IRNode.CMOVE_D, ">0", IRNode.CMP_I, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveIEQforD(int[] a, int[] b, double[] c, double[] d, double[] r, double[] r2) {
        for (int i = 0; i < a.length; i++) {
            double cc = c[i];
            double dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] == b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    @IR(counts = {IRNode.CMOVE_D, ">0", IRNode.CMP_I, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveINEforD(int[] a, int[] b, double[] c, double[] d, double[] r, double[] r2) {
        for (int i = 0; i < a.length; i++) {
            double cc = c[i];
            double dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] != b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    @IR(counts = {IRNode.CMOVE_D, ">0", IRNode.CMP_I, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
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
    @IR(counts = {IRNode.CMOVE_D, ">0", IRNode.CMP_I, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveIGEforD(int[] a, int[] b, double[] c, double[] d, double[] r, double[] r2) {
        for (int i = 0; i < a.length; i++) {
            double cc = c[i];
            double dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] >= b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    @IR(counts = {IRNode.CMOVE_D, ">0", IRNode.CMP_I, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveILTforD(int[] a, int[] b, double[] c, double[] d, double[] r, double[] r2) {
        for (int i = 0; i < a.length; i++) {
            double cc = c[i];
            double dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] < b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    @IR(counts = {IRNode.CMOVE_D, ">0", IRNode.CMP_I, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveILEforD(int[] a, int[] b, double[] c, double[] d, double[] r, double[] r2) {
        for (int i = 0; i < a.length; i++) {
            double cc = c[i];
            double dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] <= b[i]) ? cc : dd;
        }
    }

    //     L fo I
    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // @IR(counts = {IRNode.CMOVE_I, ">0", IRNode.CMP_L, ">0"},
    //     applyIf = {"UseVectorCmov", "false"},
    //     applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveLEQforI(long[] a, long[] b, int[] c, int[] d, int[] r, int[] r2) {
        for (int i = 0; i < a.length; i++) {
            int cc = c[i];
            int dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] == b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // @IR(counts = {IRNode.CMOVE_I, ">0", IRNode.CMP_L, ">0"},
    //     applyIf = {"UseVectorCmov", "false"},
    //     applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveLNEforI(long[] a, long[] b, int[] c, int[] d, int[] r, int[] r2) {
        for (int i = 0; i < a.length; i++) {
            int cc = c[i];
            int dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] != b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // @IR(counts = {IRNode.CMOVE_I, ">0", IRNode.CMP_L, ">0"},
    //     applyIf = {"UseVectorCmov", "false"},
    //     applyIfPlatform = {"riscv64", "true"})
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
    // @IR(counts = {IRNode.CMOVE_I, ">0", IRNode.CMP_L, ">0"},
    //     applyIf = {"UseVectorCmov", "false"},
    //     applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveLGEforI(long[] a, long[] b, int[] c, int[] d, int[] r, int[] r2) {
        for (int i = 0; i < a.length; i++) {
            int cc = c[i];
            int dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] >= b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // @IR(counts = {IRNode.CMOVE_I, ">0", IRNode.CMP_L, ">0"},
    //     applyIf = {"UseVectorCmov", "false"},
    //     applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveLLTforI(long[] a, long[] b, int[] c, int[] d, int[] r, int[] r2) {
        for (int i = 0; i < a.length; i++) {
            int cc = c[i];
            int dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] < b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // @IR(counts = {IRNode.CMOVE_I, ">0", IRNode.CMP_L, ">0"},
    //     applyIf = {"UseVectorCmov", "false"},
    //     applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveLLEforI(long[] a, long[] b, int[] c, int[] d, int[] r, int[] r2) {
        for (int i = 0; i < a.length; i++) {
            int cc = c[i];
            int dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] <= b[i]) ? cc : dd;
        }
    }

    //     L fo L
    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // @IR(counts = {IRNode.CMOVE_L, ">0", IRNode.CMP_L, ">0"},
    //     applyIf = {"UseVectorCmov", "false"},
    //     applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveLEQforL(long[] a, long[] b, long[] c, long[] d, long[] r, long[] r2) {
        for (int i = 0; i < a.length; i++) {
            long cc = c[i];
            long dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] == b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // @IR(counts = {IRNode.CMOVE_L, ">0", IRNode.CMP_L, ">0"},
    //     applyIf = {"UseVectorCmov", "false"},
    //     applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveLNEforL(long[] a, long[] b, long[] c, long[] d, long[] r, long[] r2) {
        for (int i = 0; i < a.length; i++) {
            long cc = c[i];
            long dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] != b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // @IR(counts = {IRNode.CMOVE_L, ">0", IRNode.CMP_L, ">0"},
    //     applyIf = {"UseVectorCmov", "false"},
    //     applyIfPlatform = {"riscv64", "true"})
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
    // @IR(counts = {IRNode.CMOVE_L, ">0", IRNode.CMP_L, ">0"},
    //     applyIf = {"UseVectorCmov", "false"},
    //     applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveLGEforL(long[] a, long[] b, long[] c, long[] d, long[] r, long[] r2) {
        for (int i = 0; i < a.length; i++) {
            long cc = c[i];
            long dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] >= b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // @IR(counts = {IRNode.CMOVE_L, ">0", IRNode.CMP_L, ">0"},
    //     applyIf = {"UseVectorCmov", "false"},
    //     applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveLLTforL(long[] a, long[] b, long[] c, long[] d, long[] r, long[] r2) {
        for (int i = 0; i < a.length; i++) {
            long cc = c[i];
            long dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] < b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // @IR(counts = {IRNode.CMOVE_L, ">0", IRNode.CMP_L, ">0"},
    //     applyIf = {"UseVectorCmov", "false"},
    //     applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveLLEforL(long[] a, long[] b, long[] c, long[] d, long[] r, long[] r2) {
        for (int i = 0; i < a.length; i++) {
            long cc = c[i];
            long dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] <= b[i]) ? cc : dd;
        }
    }

    //     L fo F
    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    @IR(counts = {IRNode.CMOVE_F, ">0", IRNode.CMP_L, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveLEQforF(long[] a, long[] b, float[] c, float[] d, float[] r, float[] r2) {
        for (int i = 0; i < a.length; i++) {
            float cc = c[i];
            float dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] == b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    @IR(counts = {IRNode.CMOVE_F, ">0", IRNode.CMP_L, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveLNEforF(long[] a, long[] b, float[] c, float[] d, float[] r, float[] r2) {
        for (int i = 0; i < a.length; i++) {
            float cc = c[i];
            float dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] != b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    @IR(counts = {IRNode.CMOVE_F, ">0", IRNode.CMP_L, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveLGTforF(long[] a, long[] b, float[] c, float[] d, float[] r, float[] r2) {
        for (int i = 0; i < a.length; i++) {
            float cc = c[i];
            float dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] > b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    @IR(counts = {IRNode.CMOVE_F, ">0", IRNode.CMP_L, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveLGEforF(long[] a, long[] b, float[] c, float[] d, float[] r, float[] r2) {
        for (int i = 0; i < a.length; i++) {
            float cc = c[i];
            float dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] >= b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    @IR(counts = {IRNode.CMOVE_F, ">0", IRNode.CMP_L, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveLLTforF(long[] a, long[] b, float[] c, float[] d, float[] r, float[] r2) {
        for (int i = 0; i < a.length; i++) {
            float cc = c[i];
            float dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] < b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    @IR(counts = {IRNode.CMOVE_F, ">0", IRNode.CMP_L, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveLLEforF(long[] a, long[] b, float[] c, float[] d, float[] r, float[] r2) {
        for (int i = 0; i < a.length; i++) {
            float cc = c[i];
            float dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] <= b[i]) ? cc : dd;
        }
    }

    //     L fo D
    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_L,     IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0",
                  IRNode.LOAD_VECTOR_D,     IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0",
                  IRNode.VECTOR_MASK_CMP_L, IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0",
                  IRNode.VECTOR_BLEND_D,    IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_D, ">0", IRNode.CMP_L, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
    // Requires avx2, else L is restricted to 16 byte, and D has 32. That leads to a vector elements mismatch of 2 to 4.
    private static void testCMoveLEQforD(long[] a, long[] b, double[] c, double[] d, double[] r, double[] r2) {
        for (int i = 0; i < a.length; i++) {
            double cc = c[i];
            double dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] == b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_L,     IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0",
                  IRNode.LOAD_VECTOR_D,     IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0",
                  IRNode.VECTOR_MASK_CMP_L, IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0",
                  IRNode.VECTOR_BLEND_D,    IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_D, ">0", IRNode.CMP_L, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
    // Requires avx2, else L is restricted to 16 byte, and D has 32. That leads to a vector elements mismatch of 2 to 4.
    private static void testCMoveLNEforD(long[] a, long[] b, double[] c, double[] d, double[] r, double[] r2) {
        for (int i = 0; i < a.length; i++) {
            double cc = c[i];
            double dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] != b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_L,     IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0",
                  IRNode.LOAD_VECTOR_D,     IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0",
                  IRNode.VECTOR_MASK_CMP_L, IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0",
                  IRNode.VECTOR_BLEND_D,    IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_D, ">0", IRNode.CMP_L, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
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
    @IR(counts = {IRNode.LOAD_VECTOR_L,     IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0",
                  IRNode.LOAD_VECTOR_D,     IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0",
                  IRNode.VECTOR_MASK_CMP_L, IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0",
                  IRNode.VECTOR_BLEND_D,    IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_D, ">0", IRNode.CMP_L, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
    // Requires avx2, else L is restricted to 16 byte, and D has 32. That leads to a vector elements mismatch of 2 to 4.
    private static void testCMoveLGEforD(long[] a, long[] b, double[] c, double[] d, double[] r, double[] r2) {
        for (int i = 0; i < a.length; i++) {
            double cc = c[i];
            double dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] >= b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_L,     IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0",
                  IRNode.LOAD_VECTOR_D,     IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0",
                  IRNode.VECTOR_MASK_CMP_L, IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0",
                  IRNode.VECTOR_BLEND_D,    IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_D, ">0", IRNode.CMP_L, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
    // Requires avx2, else L is restricted to 16 byte, and D has 32. That leads to a vector elements mismatch of 2 to 4.
    private static void testCMoveLLTforD(long[] a, long[] b, double[] c, double[] d, double[] r, double[] r2) {
        for (int i = 0; i < a.length; i++) {
            double cc = c[i];
            double dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] < b[i]) ? cc : dd;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_L,     IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0",
                  IRNode.LOAD_VECTOR_D,     IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0",
                  IRNode.VECTOR_MASK_CMP_L, IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0",
                  IRNode.VECTOR_BLEND_D,    IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_D, ">0", IRNode.CMP_L, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
    // Requires avx2, else L is restricted to 16 byte, and D has 32. That leads to a vector elements mismatch of 2 to 4.
    private static void testCMoveLLEforD(long[] a, long[] b, double[] c, double[] d, double[] r, double[] r2) {
        for (int i = 0; i < a.length; i++) {
            double cc = c[i];
            double dd = d[i];
            r2[i] = cc + dd;
            r[i] = (a[i] <= b[i]) ? cc : dd;
        }
    }

    // Unsigned comparison: I/L
    //     I fo I
    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // @IR(counts = {IRNode.CMOVE_I, ">0", IRNode.CMP_U, ">0"},
    //     applyIf = {"UseVectorCmov", "false"},
    //     applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveUIEQforI(int[] a, int[] b, int[] c, int[] d, int[] r, int[] r2) {
        for (int i = 0; i < a.length; i++) {
            int cc = c[i];
            int dd = d[i];
            r2[i] = cc + dd;
            r[i] = Integer.compareUnsigned(a[i], b[i]) == 0 ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // @IR(counts = {IRNode.CMOVE_I, ">0", IRNode.CMP_U, ">0"},
    //     applyIf = {"UseVectorCmov", "false"},
    //     applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveUINEforI(int[] a, int[] b, int[] c, int[] d, int[] r, int[] r2) {
        for (int i = 0; i < a.length; i++) {
            int cc = c[i];
            int dd = d[i];
            r2[i] = cc + dd;
            r[i] = Integer.compareUnsigned(a[i], b[i]) != 0 ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // @IR(counts = {IRNode.CMOVE_I, ">0", IRNode.CMP_U, ">0"},
    //     applyIf = {"UseVectorCmov", "false"},
    //     applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveUIGTforI(int[] a, int[] b, int[] c, int[] d, int[] r, int[] r2) {
        for (int i = 0; i < a.length; i++) {
            int cc = c[i];
            int dd = d[i];
            r2[i] = cc + dd;
            r[i] = Integer.compareUnsigned(a[i], b[i]) > 0 ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // @IR(counts = {IRNode.CMOVE_I, ">0", IRNode.CMP_U, ">0"},
    //     applyIf = {"UseVectorCmov", "false"},
    //     applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveUIGEforI(int[] a, int[] b, int[] c, int[] d, int[] r, int[] r2) {
        for (int i = 0; i < a.length; i++) {
            int cc = c[i];
            int dd = d[i];
            r2[i] = cc + dd;
            r[i] = Integer.compareUnsigned(a[i], b[i]) >= 0 ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // @IR(counts = {IRNode.CMOVE_I, ">0", IRNode.CMP_U, ">0"},
    //     applyIf = {"UseVectorCmov", "false"},
    //     applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveUILTforI(int[] a, int[] b, int[] c, int[] d, int[] r, int[] r2) {
        for (int i = 0; i < a.length; i++) {
            int cc = c[i];
            int dd = d[i];
            r2[i] = cc + dd;
            r[i] = Integer.compareUnsigned(a[i], b[i]) < 0 ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // @IR(counts = {IRNode.CMOVE_I, ">0", IRNode.CMP_U, ">0"},
    //     applyIf = {"UseVectorCmov", "false"},
    //     applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveUILEforI(int[] a, int[] b, int[] c, int[] d, int[] r, int[] r2) {
        for (int i = 0; i < a.length; i++) {
            int cc = c[i];
            int dd = d[i];
            r2[i] = cc + dd;
            r[i] = Integer.compareUnsigned(a[i], b[i]) <= 0 ? cc : dd;
        }
    }

    //     I fo L
    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // @IR(counts = {IRNode.CMOVE_L, ">0", IRNode.CMP_U, ">0"},
    //     applyIf = {"UseVectorCmov", "false"},
    //     applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveUIEQforL(int[] a, int[] b, long[] c, long[] d, long[] r, long[] r2) {
        for (int i = 0; i < a.length; i++) {
            long cc = c[i];
            long dd = d[i];
            r2[i] = cc + dd;
            r[i] = Integer.compareUnsigned(a[i], b[i]) == 0 ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // @IR(counts = {IRNode.CMOVE_L, ">0", IRNode.CMP_U, ">0"},
    //     applyIf = {"UseVectorCmov", "false"},
    //     applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveUINEforL(int[] a, int[] b, long[] c, long[] d, long[] r, long[] r2) {
        for (int i = 0; i < a.length; i++) {
            long cc = c[i];
            long dd = d[i];
            r2[i] = cc + dd;
            r[i] = Integer.compareUnsigned(a[i], b[i]) != 0 ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // @IR(counts = {IRNode.CMOVE_L, ">0", IRNode.CMP_U, ">0"},
    //     applyIf = {"UseVectorCmov", "false"},
    //     applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveUIGTforL(int[] a, int[] b, long[] c, long[] d, long[] r, long[] r2) {
        for (int i = 0; i < a.length; i++) {
            long cc = c[i];
            long dd = d[i];
            r2[i] = cc + dd;
            r[i] = Integer.compareUnsigned(a[i], b[i]) > 0 ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // @IR(counts = {IRNode.CMOVE_L, ">0", IRNode.CMP_U, ">0"},
    //     applyIf = {"UseVectorCmov", "false"},
    //     applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveUIGEforL(int[] a, int[] b, long[] c, long[] d, long[] r, long[] r2) {
        for (int i = 0; i < a.length; i++) {
            long cc = c[i];
            long dd = d[i];
            r2[i] = cc + dd;
            r[i] = Integer.compareUnsigned(a[i], b[i]) >= 0 ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // @IR(counts = {IRNode.CMOVE_L, ">0", IRNode.CMP_U, ">0"},
    //     applyIf = {"UseVectorCmov", "false"},
    //     applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveUILTforL(int[] a, int[] b, long[] c, long[] d, long[] r, long[] r2) {
        for (int i = 0; i < a.length; i++) {
            long cc = c[i];
            long dd = d[i];
            r2[i] = cc + dd;
            r[i] = Integer.compareUnsigned(a[i], b[i]) < 0 ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // @IR(counts = {IRNode.CMOVE_L, ">0", IRNode.CMP_U, ">0"},
    //     applyIf = {"UseVectorCmov", "false"},
    //     applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveUILEforL(int[] a, int[] b, long[] c, long[] d, long[] r, long[] r2) {
        for (int i = 0; i < a.length; i++) {
            long cc = c[i];
            long dd = d[i];
            r2[i] = cc + dd;
            r[i] = Integer.compareUnsigned(a[i], b[i]) <= 0 ? cc : dd;
        }
    }

    //     I fo F
    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I,     IRNode.VECTOR_SIZE + "min(max_int, max_float)", ">0",
                  IRNode.LOAD_VECTOR_F,     IRNode.VECTOR_SIZE + "min(max_int, max_float)", ">0",
                  IRNode.VECTOR_MASK_CMP_I, IRNode.VECTOR_SIZE + "min(max_int, max_float)", ">0",
                  IRNode.VECTOR_BLEND_F,    IRNode.VECTOR_SIZE + "min(max_int, max_float)", ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_F, ">0", IRNode.CMP_U, ">0"},
        applyIf = {"UseVectorCmov", "false"},
            applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveUIEQforF(int[] a, int[] b, float[] c, float[] d, float[] r, float[] r2) {
        for (int i = 0; i < a.length; i++) {
            float cc = c[i];
            float dd = d[i];
            r2[i] = cc + dd;
            r[i] = Integer.compareUnsigned(a[i], b[i]) == 0 ? cc : dd;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I,     IRNode.VECTOR_SIZE + "min(max_int, max_float)", ">0",
                  IRNode.LOAD_VECTOR_F,     IRNode.VECTOR_SIZE + "min(max_int, max_float)", ">0",
                  IRNode.VECTOR_MASK_CMP_I, IRNode.VECTOR_SIZE + "min(max_int, max_float)", ">0",
                  IRNode.VECTOR_BLEND_F,    IRNode.VECTOR_SIZE + "min(max_int, max_float)", ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_F, ">0", IRNode.CMP_U, ">0"},
        applyIf = {"UseVectorCmov", "false"},
            applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveUINEforF(int[] a, int[] b, float[] c, float[] d, float[] r, float[] r2) {
        for (int i = 0; i < a.length; i++) {
            float cc = c[i];
            float dd = d[i];
            r2[i] = cc + dd;
            r[i] = Integer.compareUnsigned(a[i], b[i]) != 0 ? cc : dd;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I,     IRNode.VECTOR_SIZE + "min(max_int, max_float)", ">0",
                  IRNode.LOAD_VECTOR_F,     IRNode.VECTOR_SIZE + "min(max_int, max_float)", ">0",
                  IRNode.VECTOR_MASK_CMP_I, IRNode.VECTOR_SIZE + "min(max_int, max_float)", ">0",
                  IRNode.VECTOR_BLEND_F,    IRNode.VECTOR_SIZE + "min(max_int, max_float)", ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_F, ">0", IRNode.CMP_U, ">0"},
        applyIf = {"UseVectorCmov", "false"},
            applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveUIGTforF(int[] a, int[] b, float[] c, float[] d, float[] r, float[] r2) {
        for (int i = 0; i < a.length; i++) {
            float cc = c[i];
            float dd = d[i];
            r2[i] = cc + dd;
            r[i] = Integer.compareUnsigned(a[i], b[i]) > 0 ? cc : dd;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I,     IRNode.VECTOR_SIZE + "min(max_int, max_float)", ">0",
                  IRNode.LOAD_VECTOR_F,     IRNode.VECTOR_SIZE + "min(max_int, max_float)", ">0",
                  IRNode.VECTOR_MASK_CMP_I, IRNode.VECTOR_SIZE + "min(max_int, max_float)", ">0",
                  IRNode.VECTOR_BLEND_F,    IRNode.VECTOR_SIZE + "min(max_int, max_float)", ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_F, ">0", IRNode.CMP_U, ">0"},
        applyIf = {"UseVectorCmov", "false"},
            applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveUIGEforF(int[] a, int[] b, float[] c, float[] d, float[] r, float[] r2) {
        for (int i = 0; i < a.length; i++) {
            float cc = c[i];
            float dd = d[i];
            r2[i] = cc + dd;
            r[i] = Integer.compareUnsigned(a[i], b[i]) >= 0 ? cc : dd;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I,     IRNode.VECTOR_SIZE + "min(max_int, max_float)", ">0",
                  IRNode.LOAD_VECTOR_F,     IRNode.VECTOR_SIZE + "min(max_int, max_float)", ">0",
                  IRNode.VECTOR_MASK_CMP_I, IRNode.VECTOR_SIZE + "min(max_int, max_float)", ">0",
                  IRNode.VECTOR_BLEND_F,    IRNode.VECTOR_SIZE + "min(max_int, max_float)", ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_F, ">0", IRNode.CMP_U, ">0"},
        applyIf = {"UseVectorCmov", "false"},
            applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveUILTforF(int[] a, int[] b, float[] c, float[] d, float[] r, float[] r2) {
        for (int i = 0; i < a.length; i++) {
            float cc = c[i];
            float dd = d[i];
            r2[i] = cc + dd;
            r[i] = Integer.compareUnsigned(a[i], b[i]) < 0 ? cc : dd;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I,     IRNode.VECTOR_SIZE + "min(max_int, max_float)", ">0",
                  IRNode.LOAD_VECTOR_F,     IRNode.VECTOR_SIZE + "min(max_int, max_float)", ">0",
                  IRNode.VECTOR_MASK_CMP_I, IRNode.VECTOR_SIZE + "min(max_int, max_float)", ">0",
                  IRNode.VECTOR_BLEND_F,    IRNode.VECTOR_SIZE + "min(max_int, max_float)", ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_F, ">0", IRNode.CMP_U, ">0"},
        applyIf = {"UseVectorCmov", "false"},
            applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveUILEforF(int[] a, int[] b, float[] c, float[] d, float[] r, float[] r2) {
        for (int i = 0; i < a.length; i++) {
            float cc = c[i];
            float dd = d[i];
            r2[i] = cc + dd;
            r[i] = Integer.compareUnsigned(a[i], b[i]) <= 0 ? cc : dd;
        }
    }

    //     I fo D
    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    @IR(counts = {IRNode.CMOVE_D, ">0", IRNode.CMP_U, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveUIEQforD(int[] a, int[] b, double[] c, double[] d, double[] r, double[] r2) {
        for (int i = 0; i < a.length; i++) {
            double cc = c[i];
            double dd = d[i];
            r2[i] = cc + dd;
            r[i] = Integer.compareUnsigned(a[i], b[i]) == 0 ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    @IR(counts = {IRNode.CMOVE_D, ">0", IRNode.CMP_U, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveUINEforD(int[] a, int[] b, double[] c, double[] d, double[] r, double[] r2) {
        for (int i = 0; i < a.length; i++) {
            double cc = c[i];
            double dd = d[i];
            r2[i] = cc + dd;
            r[i] = Integer.compareUnsigned(a[i], b[i]) != 0 ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    @IR(counts = {IRNode.CMOVE_D, ">0", IRNode.CMP_U, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveUIGTforD(int[] a, int[] b, double[] c, double[] d, double[] r, double[] r2) {
        for (int i = 0; i < a.length; i++) {
            double cc = c[i];
            double dd = d[i];
            r2[i] = cc + dd;
            r[i] = Integer.compareUnsigned(a[i], b[i]) > 0 ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    @IR(counts = {IRNode.CMOVE_D, ">0", IRNode.CMP_U, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveUIGEforD(int[] a, int[] b, double[] c, double[] d, double[] r, double[] r2) {
        for (int i = 0; i < a.length; i++) {
            double cc = c[i];
            double dd = d[i];
            r2[i] = cc + dd;
            r[i] = Integer.compareUnsigned(a[i], b[i]) >= 0 ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    @IR(counts = {IRNode.CMOVE_D, ">0", IRNode.CMP_U, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveUILTforD(int[] a, int[] b, double[] c, double[] d, double[] r, double[] r2) {
        for (int i = 0; i < a.length; i++) {
            double cc = c[i];
            double dd = d[i];
            r2[i] = cc + dd;
            r[i] = Integer.compareUnsigned(a[i], b[i]) < 0 ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    @IR(counts = {IRNode.CMOVE_D, ">0", IRNode.CMP_U, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveUILEforD(int[] a, int[] b, double[] c, double[] d, double[] r, double[] r2) {
        for (int i = 0; i < a.length; i++) {
            double cc = c[i];
            double dd = d[i];
            r2[i] = cc + dd;
            r[i] = Integer.compareUnsigned(a[i], b[i]) <= 0 ? cc : dd;
        }
    }

    //     L fo I
    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // @IR(counts = {IRNode.CMOVE_I, ">0", IRNode.CMP_UL, ">0"},
    //     applyIf = {"UseVectorCmov", "false"},
    //     applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveULEQforI(long[] a, long[] b, int[] c, int[] d, int[] r, int[] r2) {
        for (int i = 0; i < a.length; i++) {
            int cc = c[i];
            int dd = d[i];
            r2[i] = cc + dd;
            r[i] = Long.compareUnsigned(a[i], b[i]) == 0 ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // @IR(counts = {IRNode.CMOVE_I, ">0", IRNode.CMP_UL, ">0"},
    //     applyIf = {"UseVectorCmov", "false"},
    //     applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveULNEforI(long[] a, long[] b, int[] c, int[] d, int[] r, int[] r2) {
        for (int i = 0; i < a.length; i++) {
            int cc = c[i];
            int dd = d[i];
            r2[i] = cc + dd;
            r[i] = Long.compareUnsigned(a[i], b[i]) != 0 ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // @IR(counts = {IRNode.CMOVE_I, ">0", IRNode.CMP_UL, ">0"},
    //     applyIf = {"UseVectorCmov", "false"},
    //     applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveULGTforI(long[] a, long[] b, int[] c, int[] d, int[] r, int[] r2) {
        for (int i = 0; i < a.length; i++) {
            int cc = c[i];
            int dd = d[i];
            r2[i] = cc + dd;
            r[i] = Long.compareUnsigned(a[i], b[i]) > 0 ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // @IR(counts = {IRNode.CMOVE_I, ">0", IRNode.CMP_UL, ">0"},
    //     applyIf = {"UseVectorCmov", "false"},
    //     applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveULGEforI(long[] a, long[] b, int[] c, int[] d, int[] r, int[] r2) {
        for (int i = 0; i < a.length; i++) {
            int cc = c[i];
            int dd = d[i];
            r2[i] = cc + dd;
            r[i] = Long.compareUnsigned(a[i], b[i]) >= 0 ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // @IR(counts = {IRNode.CMOVE_I, ">0", IRNode.CMP_UL, ">0"},
    //     applyIf = {"UseVectorCmov", "false"},
    //     applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveULLTforI(long[] a, long[] b, int[] c, int[] d, int[] r, int[] r2) {
        for (int i = 0; i < a.length; i++) {
            int cc = c[i];
            int dd = d[i];
            r2[i] = cc + dd;
            r[i] = Long.compareUnsigned(a[i], b[i]) < 0 ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // @IR(counts = {IRNode.CMOVE_I, ">0", IRNode.CMP_UL, ">0"},
    //     applyIf = {"UseVectorCmov", "false"},
    //     applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveULLEforI(long[] a, long[] b, int[] c, int[] d, int[] r, int[] r2) {
        for (int i = 0; i < a.length; i++) {
            int cc = c[i];
            int dd = d[i];
            r2[i] = cc + dd;
            r[i] = Long.compareUnsigned(a[i], b[i]) <= 0 ? cc : dd;
        }
    }

    //     L fo L
    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // @IR(counts = {IRNode.CMOVE_L, ">0", IRNode.CMP_UL, ">0"},
    //     applyIf = {"UseVectorCmov", "false"},
    //     applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveULEQforL(long[] a, long[] b, long[] c, long[] d, long[] r, long[] r2) {
        for (int i = 0; i < a.length; i++) {
            long cc = c[i];
            long dd = d[i];
            r2[i] = cc + dd;
            r[i] = Long.compareUnsigned(a[i], b[i]) == 0 ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // @IR(counts = {IRNode.CMOVE_L, ">0", IRNode.CMP_UL, ">0"},
    //     applyIf = {"UseVectorCmov", "false"},
    //     applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveULNEforL(long[] a, long[] b, long[] c, long[] d, long[] r, long[] r2) {
        for (int i = 0; i < a.length; i++) {
            long cc = c[i];
            long dd = d[i];
            r2[i] = cc + dd;
            r[i] = Long.compareUnsigned(a[i], b[i]) != 0 ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // @IR(counts = {IRNode.CMOVE_L, ">0", IRNode.CMP_UL, ">0"},
    //     applyIf = {"UseVectorCmov", "false"},
    //     applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveULGTforL(long[] a, long[] b, long[] c, long[] d, long[] r, long[] r2) {
        for (int i = 0; i < a.length; i++) {
            long cc = c[i];
            long dd = d[i];
            r2[i] = cc + dd;
            r[i] = Long.compareUnsigned(a[i], b[i]) > 0 ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // @IR(counts = {IRNode.CMOVE_L, ">0", IRNode.CMP_UL, ">0"},
    //     applyIf = {"UseVectorCmov", "false"},
    //     applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveULGEforL(long[] a, long[] b, long[] c, long[] d, long[] r, long[] r2) {
        for (int i = 0; i < a.length; i++) {
            long cc = c[i];
            long dd = d[i];
            r2[i] = cc + dd;
            r[i] = Long.compareUnsigned(a[i], b[i]) >= 0 ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // @IR(counts = {IRNode.CMOVE_L, ">0", IRNode.CMP_UL, ">0"},
    //     applyIf = {"UseVectorCmov", "false"},
    //     applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveULLTforL(long[] a, long[] b, long[] c, long[] d, long[] r, long[] r2) {
        for (int i = 0; i < a.length; i++) {
            long cc = c[i];
            long dd = d[i];
            r2[i] = cc + dd;
            r[i] = Long.compareUnsigned(a[i], b[i]) < 0 ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // @IR(counts = {IRNode.CMOVE_L, ">0", IRNode.CMP_UL, ">0"},
    //     applyIf = {"UseVectorCmov", "false"},
    //     applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveULLEforL(long[] a, long[] b, long[] c, long[] d, long[] r, long[] r2) {
        for (int i = 0; i < a.length; i++) {
            long cc = c[i];
            long dd = d[i];
            r2[i] = cc + dd;
            r[i] = Long.compareUnsigned(a[i], b[i]) <= 0 ? cc : dd;
        }
    }

    //     L fo F
    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    @IR(counts = {IRNode.CMOVE_F, ">0", IRNode.CMP_UL, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveULEQforF(long[] a, long[] b, float[] c, float[] d, float[] r, float[] r2) {
        for (int i = 0; i < a.length; i++) {
            float cc = c[i];
            float dd = d[i];
            r2[i] = cc + dd;
            r[i] = Long.compareUnsigned(a[i], b[i]) == 0 ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    @IR(counts = {IRNode.CMOVE_F, ">0", IRNode.CMP_UL, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveULNEforF(long[] a, long[] b, float[] c, float[] d, float[] r, float[] r2) {
        for (int i = 0; i < a.length; i++) {
            float cc = c[i];
            float dd = d[i];
            r2[i] = cc + dd;
            r[i] = Long.compareUnsigned(a[i], b[i]) != 0 ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    @IR(counts = {IRNode.CMOVE_F, ">0", IRNode.CMP_UL, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveULGTforF(long[] a, long[] b, float[] c, float[] d, float[] r, float[] r2) {
        for (int i = 0; i < a.length; i++) {
            float cc = c[i];
            float dd = d[i];
            r2[i] = cc + dd;
            r[i] = Long.compareUnsigned(a[i], b[i]) > 0 ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    @IR(counts = {IRNode.CMOVE_F, ">0", IRNode.CMP_UL, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveULGEforF(long[] a, long[] b, float[] c, float[] d, float[] r, float[] r2) {
        for (int i = 0; i < a.length; i++) {
            float cc = c[i];
            float dd = d[i];
            r2[i] = cc + dd;
            r[i] = Long.compareUnsigned(a[i], b[i]) >= 0 ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    @IR(counts = {IRNode.CMOVE_F, ">0", IRNode.CMP_UL, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveULLTforF(long[] a, long[] b, float[] c, float[] d, float[] r, float[] r2) {
        for (int i = 0; i < a.length; i++) {
            float cc = c[i];
            float dd = d[i];
            r2[i] = cc + dd;
            r[i] = Long.compareUnsigned(a[i], b[i]) < 0 ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    @IR(counts = {IRNode.CMOVE_F, ">0", IRNode.CMP_UL, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
    private static void testCMoveULLEforF(long[] a, long[] b, float[] c, float[] d, float[] r, float[] r2) {
        for (int i = 0; i < a.length; i++) {
            float cc = c[i];
            float dd = d[i];
            r2[i] = cc + dd;
            r[i] = Long.compareUnsigned(a[i], b[i]) <= 0 ? cc : dd;
        }
    }

    //     L fo D
    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_L,     IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0",
                  IRNode.LOAD_VECTOR_D,     IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0",
                  IRNode.VECTOR_MASK_CMP_L, IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0",
                  IRNode.VECTOR_BLEND_D,    IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_D, ">0", IRNode.CMP_UL, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
    // Requires avx2, else L is restricted to 16 byte, and D has 32. That leads to a vector elements mismatch of 2 to 4.
    private static void testCMoveULEQforD(long[] a, long[] b, double[] c, double[] d, double[] r, double[] r2) {
        for (int i = 0; i < a.length; i++) {
            double cc = c[i];
            double dd = d[i];
            r2[i] = cc + dd;
            r[i] = Long.compareUnsigned(a[i], b[i]) == 0 ? cc : dd;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_L,     IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0",
                  IRNode.LOAD_VECTOR_D,     IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0",
                  IRNode.VECTOR_MASK_CMP_L, IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0",
                  IRNode.VECTOR_BLEND_D,    IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_D, ">0", IRNode.CMP_UL, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
    // Requires avx2, else L is restricted to 16 byte, and D has 32. That leads to a vector elements mismatch of 2 to 4.
    private static void testCMoveULNEforD(long[] a, long[] b, double[] c, double[] d, double[] r, double[] r2) {
        for (int i = 0; i < a.length; i++) {
            double cc = c[i];
            double dd = d[i];
            r2[i] = cc + dd;
            r[i] = Long.compareUnsigned(a[i], b[i]) != 0 ? cc : dd;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_L,     IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0",
                  IRNode.LOAD_VECTOR_D,     IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0",
                  IRNode.VECTOR_MASK_CMP_L, IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0",
                  IRNode.VECTOR_BLEND_D,    IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_D, ">0", IRNode.CMP_UL, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
    // Requires avx2, else L is restricted to 16 byte, and D has 32. That leads to a vector elements mismatch of 2 to 4.
    private static void testCMoveULGTforD(long[] a, long[] b, double[] c, double[] d, double[] r, double[] r2) {
        for (int i = 0; i < a.length; i++) {
            double cc = c[i];
            double dd = d[i];
            r2[i] = cc + dd;
            r[i] = Long.compareUnsigned(a[i], b[i]) > 0 ? cc : dd;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_L,     IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0",
                  IRNode.LOAD_VECTOR_D,     IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0",
                  IRNode.VECTOR_MASK_CMP_L, IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0",
                  IRNode.VECTOR_BLEND_D,    IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_D, ">0", IRNode.CMP_UL, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
    // Requires avx2, else L is restricted to 16 byte, and D has 32. That leads to a vector elements mismatch of 2 to 4.
    private static void testCMoveULGEforD(long[] a, long[] b, double[] c, double[] d, double[] r, double[] r2) {
        for (int i = 0; i < a.length; i++) {
            double cc = c[i];
            double dd = d[i];
            r2[i] = cc + dd;
            r[i] = Long.compareUnsigned(a[i], b[i]) >= 0 ? cc : dd;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_L,     IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0",
                  IRNode.LOAD_VECTOR_D,     IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0",
                  IRNode.VECTOR_MASK_CMP_L, IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0",
                  IRNode.VECTOR_BLEND_D,    IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_D, ">0", IRNode.CMP_UL, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
    // Requires avx2, else L is restricted to 16 byte, and D has 32. That leads to a vector elements mismatch of 2 to 4.
    private static void testCMoveULLTforD(long[] a, long[] b, double[] c, double[] d, double[] r, double[] r2) {
        for (int i = 0; i < a.length; i++) {
            double cc = c[i];
            double dd = d[i];
            r2[i] = cc + dd;
            r[i] = Long.compareUnsigned(a[i], b[i]) < 0 ? cc : dd;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_L,     IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0",
                  IRNode.LOAD_VECTOR_D,     IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0",
                  IRNode.VECTOR_MASK_CMP_L, IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0",
                  IRNode.VECTOR_BLEND_D,    IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0",
                  IRNode.STORE_VECTOR, ">0"},
        applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_D, ">0", IRNode.CMP_UL, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
    // Requires avx2, else L is restricted to 16 byte, and D has 32. That leads to a vector elements mismatch of 2 to 4.
    private static void testCMoveULLEforD(long[] a, long[] b, double[] c, double[] d, double[] r, double[] r2) {
        for (int i = 0; i < a.length; i++) {
            double cc = c[i];
            double dd = d[i];
            r2[i] = cc + dd;
            r[i] = Long.compareUnsigned(a[i], b[i]) <= 0 ? cc : dd;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // @IR(counts = {IRNode.CMOVE_I, ">0", IRNode.CMP_F, ">0"},
    //     applyIf = {"UseVectorCmov", "false"},
    //     applyIfPlatform = {"riscv64", "true"})
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
    // @IR(counts = {IRNode.CMOVE_L, ">0", IRNode.CMP_F, ">0"},
    //     applyIf = {"UseVectorCmov", "false"},
    //     applyIfPlatform = {"riscv64", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_F, ">0", IRNode.CMP_F, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
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
    @IR(counts = {IRNode.CMOVE_D, ">0", IRNode.CMP_F, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
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
    // @IR(counts = {IRNode.CMOVE_I, ">0", IRNode.CMP_D, ">0"},
    //     applyIf = {"UseVectorCmov", "false"},
    //     applyIfPlatform = {"riscv64", "true"})
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
    // @IR(counts = {IRNode.CMOVE_L, ">0", IRNode.CMP_D, ">0"},
    //     applyIf = {"UseVectorCmov", "false"},
    //     applyIfPlatform = {"riscv64", "true"})
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
    @IR(counts = {IRNode.CMOVE_F, ">0", IRNode.CMP_D, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_D, ">0", IRNode.CMP_D, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_F, ">0", IRNode.CMP_F, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
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
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"},
        applyIf = {"UseVectorCmov", "true"})
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseVectorCmov", "false"})
    @IR(counts = {IRNode.CMOVE_F, ">0", IRNode.CMP_F, ">0"},
        applyIf = {"UseVectorCmov", "false"},
        applyIfPlatform = {"riscv64", "true"})
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
    @Run(test = {// Signed
                 //     I for I
                 "testCMoveIEQforI",
                 "testCMoveINEforI",
                 "testCMoveIGTforI",
                 "testCMoveIGEforI",
                 "testCMoveILTforI",
                 "testCMoveILEforI",
                 //     I for L
                 "testCMoveIEQforL",
                 "testCMoveINEforL",
                 "testCMoveIGTforL",
                 "testCMoveIGEforL",
                 "testCMoveILTforL",
                 "testCMoveILEforL",
                 //     I for F
                 "testCMoveIEQforF",
                 "testCMoveINEforF",
                 "testCMoveIGTforF",
                 "testCMoveIGEforF",
                 "testCMoveILTforF",
                 "testCMoveILEforF",
                 //     I for D
                 "testCMoveIEQforD",
                 "testCMoveINEforD",
                 "testCMoveIGTforD",
                 "testCMoveIGEforD",
                 "testCMoveILTforD",
                 "testCMoveILEforD",
                 //     L for I
                 "testCMoveLEQforI",
                 "testCMoveLNEforI",
                 "testCMoveLGTforI",
                 "testCMoveLGEforI",
                 "testCMoveLLTforI",
                 "testCMoveLLEforI",
                 //     L for L
                 "testCMoveLEQforL",
                 "testCMoveLNEforL",
                 "testCMoveLGTforL",
                 "testCMoveLGEforL",
                 "testCMoveLLTforL",
                 "testCMoveLLEforL",
                 //     L for F
                 "testCMoveLEQforF",
                 "testCMoveLNEforF",
                 "testCMoveLGTforF",
                 "testCMoveLGEforF",
                 "testCMoveLLTforF",
                 "testCMoveLLEforF",
                 //     L for D
                 "testCMoveLEQforD",
                 "testCMoveLNEforD",
                 "testCMoveLGTforD",
                 "testCMoveLGEforD",
                 "testCMoveLLTforD",
                 "testCMoveLLEforD",
                 // Unsigned
                 //     I for I
                 "testCMoveUIEQforI",
                 "testCMoveUINEforI",
                 "testCMoveUIGTforI",
                 "testCMoveUIGEforI",
                 "testCMoveUILTforI",
                 "testCMoveUILEforI",
                 //     I for L
                 "testCMoveUIEQforL",
                 "testCMoveUINEforL",
                 "testCMoveUIGTforL",
                 "testCMoveUIGEforL",
                 "testCMoveUILTforL",
                 "testCMoveUILEforL",
                 //     I for F
                 "testCMoveUIEQforF",
                 "testCMoveUINEforF",
                 "testCMoveUIGTforF",
                 "testCMoveUIGEforF",
                 "testCMoveUILTforF",
                 "testCMoveUILEforF",
                 //     I for D
                 "testCMoveUIEQforD",
                 "testCMoveUINEforD",
                 "testCMoveUIGTforD",
                 "testCMoveUIGEforD",
                 "testCMoveUILTforD",
                 "testCMoveUILEforD",
                 //     L for I
                 "testCMoveULEQforI",
                 "testCMoveULNEforI",
                 "testCMoveULGTforI",
                 "testCMoveULGEforI",
                 "testCMoveULLTforI",
                 "testCMoveULLEforI",
                 //     L for L
                 "testCMoveULEQforL",
                 "testCMoveULNEforL",
                 "testCMoveULGTforL",
                 "testCMoveULGEforL",
                 "testCMoveULLTforL",
                 "testCMoveULLEforL",
                 //     L for F
                 "testCMoveULEQforF",
                 "testCMoveULNEforF",
                 "testCMoveULGTforF",
                 "testCMoveULGEforF",
                 "testCMoveULLTforF",
                 "testCMoveULLEforF",
                 //     L for D
                 "testCMoveULEQforD",
                 "testCMoveULNEforD",
                 "testCMoveULGTforD",
                 "testCMoveULGEforD",
                 "testCMoveULLTforD",
                 "testCMoveULLEforD",
                 // Float
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

        // Signed
        //     I for I
        testCMoveIEQforI(aI, bI, cI, dI, rI, rI);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rI[i], cmoveIEQforI(aI[i], bI[i], cI[i], dI[i]));
        }

        testCMoveINEforI(aI, bI, cI, dI, rI, rI);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rI[i], cmoveINEforI(aI[i], bI[i], cI[i], dI[i]));
        }

        testCMoveIGTforI(aI, bI, cI, dI, rI, rI);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rI[i], cmoveIGTforI(aI[i], bI[i], cI[i], dI[i]));
        }

        testCMoveIGEforI(aI, bI, cI, dI, rI, rI);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rI[i], cmoveIGEforI(aI[i], bI[i], cI[i], dI[i]));
        }

        testCMoveILTforI(aI, bI, cI, dI, rI, rI);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rI[i], cmoveILTforI(aI[i], bI[i], cI[i], dI[i]));
        }

        testCMoveILEforI(aI, bI, cI, dI, rI, rI);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rI[i], cmoveILEforI(aI[i], bI[i], cI[i], dI[i]));
        }

        //     I for L
        testCMoveIEQforL(aI, bI, cL, dL, rL, rL);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rL[i], cmoveIEQforL(aI[i], bI[i], cL[i], dL[i]));
        }

        testCMoveINEforL(aI, bI, cL, dL, rL, rL);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rL[i], cmoveINEforL(aI[i], bI[i], cL[i], dL[i]));
        }

        testCMoveIGTforL(aI, bI, cL, dL, rL, rL);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rL[i], cmoveIGTforL(aI[i], bI[i], cL[i], dL[i]));
        }

        testCMoveIGEforL(aI, bI, cL, dL, rL, rL);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rL[i], cmoveIGEforL(aI[i], bI[i], cL[i], dL[i]));
        }

        testCMoveILTforL(aI, bI, cL, dL, rL, rL);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rL[i], cmoveILTforL(aI[i], bI[i], cL[i], dL[i]));
        }

        testCMoveILEforL(aI, bI, cL, dL, rL, rL);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rL[i], cmoveILEforL(aI[i], bI[i], cL[i], dL[i]));
        }

        //     I for F
        testCMoveIEQforF(aI, bI, cF, dF, rF, rF);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rF[i], cmoveIEQforF(aI[i], bI[i], cF[i], dF[i]));
        }

        testCMoveINEforF(aI, bI, cF, dF, rF, rF);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rF[i], cmoveINEforF(aI[i], bI[i], cF[i], dF[i]));
        }

        testCMoveIGTforF(aI, bI, cF, dF, rF, rF);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rF[i], cmoveIGTforF(aI[i], bI[i], cF[i], dF[i]));
        }

        testCMoveIGEforF(aI, bI, cF, dF, rF, rF);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rF[i], cmoveIGEforF(aI[i], bI[i], cF[i], dF[i]));
        }

        testCMoveILTforF(aI, bI, cF, dF, rF, rF);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rF[i], cmoveILTforF(aI[i], bI[i], cF[i], dF[i]));
        }

        testCMoveILEforF(aI, bI, cF, dF, rF, rF);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rF[i], cmoveILEforF(aI[i], bI[i], cF[i], dF[i]));
        }

        //     I for D
        testCMoveIEQforD(aI, bI, cD, dD, rD, rD);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rD[i], cmoveIEQforD(aI[i], bI[i], cD[i], dD[i]));
        }

        testCMoveINEforD(aI, bI, cD, dD, rD, rD);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rD[i], cmoveINEforD(aI[i], bI[i], cD[i], dD[i]));
        }

        testCMoveIGTforD(aI, bI, cD, dD, rD, rD);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rD[i], cmoveIGTforD(aI[i], bI[i], cD[i], dD[i]));
        }

        testCMoveIGEforD(aI, bI, cD, dD, rD, rD);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rD[i], cmoveIGEforD(aI[i], bI[i], cD[i], dD[i]));
        }

        testCMoveILTforD(aI, bI, cD, dD, rD, rD);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rD[i], cmoveILTforD(aI[i], bI[i], cD[i], dD[i]));
        }

        testCMoveILEforD(aI, bI, cD, dD, rD, rD);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rD[i], cmoveILEforD(aI[i], bI[i], cD[i], dD[i]));
        }

        //     L for I
        testCMoveLEQforI(aL, bL, cI, dI, rI, rI);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rI[i], cmoveLEQforI(aL[i], bL[i], cI[i], dI[i]));
        }

        testCMoveLNEforI(aL, bL, cI, dI, rI, rI);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rI[i], cmoveLNEforI(aL[i], bL[i], cI[i], dI[i]));
        }

        testCMoveLGTforI(aL, bL, cI, dI, rI, rI);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rI[i], cmoveLGTforI(aL[i], bL[i], cI[i], dI[i]));
        }

        testCMoveLGEforI(aL, bL, cI, dI, rI, rI);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rI[i], cmoveLGEforI(aL[i], bL[i], cI[i], dI[i]));
        }

        testCMoveLLTforI(aL, bL, cI, dI, rI, rI);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rI[i], cmoveLLTforI(aL[i], bL[i], cI[i], dI[i]));
        }

        testCMoveLLEforI(aL, bL, cI, dI, rI, rI);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rI[i], cmoveLLEforI(aL[i], bL[i], cI[i], dI[i]));
        }

        //     L for L
        testCMoveLEQforL(aL, bL, cL, dL, rL, rL);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rL[i], cmoveLEQforL(aL[i], bL[i], cL[i], dL[i]));
        }

        testCMoveLNEforL(aL, bL, cL, dL, rL, rL);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rL[i], cmoveLNEforL(aL[i], bL[i], cL[i], dL[i]));
        }

        testCMoveLGTforL(aL, bL, cL, dL, rL, rL);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rL[i], cmoveLGTforL(aL[i], bL[i], cL[i], dL[i]));
        }

        testCMoveLGEforL(aL, bL, cL, dL, rL, rL);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rL[i], cmoveLGEforL(aL[i], bL[i], cL[i], dL[i]));
        }

        testCMoveLLTforL(aL, bL, cL, dL, rL, rL);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rL[i], cmoveLLTforL(aL[i], bL[i], cL[i], dL[i]));
        }

        testCMoveLLEforL(aL, bL, cL, dL, rL, rL);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rL[i], cmoveLLEforL(aL[i], bL[i], cL[i], dL[i]));
        }

        //     L for F
        testCMoveLEQforF(aL, bL, cF, dF, rF, rF);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rF[i], cmoveLEQforF(aL[i], bL[i], cF[i], dF[i]));
        }

        testCMoveLNEforF(aL, bL, cF, dF, rF, rF);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rF[i], cmoveLNEforF(aL[i], bL[i], cF[i], dF[i]));
        }

        testCMoveLGTforF(aL, bL, cF, dF, rF, rF);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rF[i], cmoveLGTforF(aL[i], bL[i], cF[i], dF[i]));
        }

        testCMoveLGEforF(aL, bL, cF, dF, rF, rF);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rF[i], cmoveLGEforF(aL[i], bL[i], cF[i], dF[i]));
        }

        testCMoveLLTforF(aL, bL, cF, dF, rF, rF);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rF[i], cmoveLLTforF(aL[i], bL[i], cF[i], dF[i]));
        }

        testCMoveLLEforF(aL, bL, cF, dF, rF, rF);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rF[i], cmoveLLEforF(aL[i], bL[i], cF[i], dF[i]));
        }

        //     L for D
        testCMoveLEQforD(aL, bL, cD, dD, rD, rD);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rD[i], cmoveLEQforD(aL[i], bL[i], cD[i], dD[i]));
        }

        testCMoveLNEforD(aL, bL, cD, dD, rD, rD);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rD[i], cmoveLNEforD(aL[i], bL[i], cD[i], dD[i]));
        }

        testCMoveLGTforD(aL, bL, cD, dD, rD, rD);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rD[i], cmoveLGTforD(aL[i], bL[i], cD[i], dD[i]));
        }

        testCMoveLGEforD(aL, bL, cD, dD, rD, rD);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rD[i], cmoveLGEforD(aL[i], bL[i], cD[i], dD[i]));
        }

        testCMoveLLTforD(aL, bL, cD, dD, rD, rD);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rD[i], cmoveLLTforD(aL[i], bL[i], cD[i], dD[i]));
        }

        testCMoveLLEforD(aL, bL, cD, dD, rD, rD);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rD[i], cmoveLLEforD(aL[i], bL[i], cD[i], dD[i]));
        }

        // Unsigned
        //     I for I
        testCMoveUIEQforI(aI, bI, cI, dI, rI, rI);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rI[i], cmoveUIEQforI(aI[i], bI[i], cI[i], dI[i]));
        }

        testCMoveUINEforI(aI, bI, cI, dI, rI, rI);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rI[i], cmoveUINEforI(aI[i], bI[i], cI[i], dI[i]));
        }

        testCMoveUIGTforI(aI, bI, cI, dI, rI, rI);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rI[i], cmoveUIGTforI(aI[i], bI[i], cI[i], dI[i]));
        }

        testCMoveUIGEforI(aI, bI, cI, dI, rI, rI);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rI[i], cmoveUIGEforI(aI[i], bI[i], cI[i], dI[i]));
        }

        testCMoveUILTforI(aI, bI, cI, dI, rI, rI);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rI[i], cmoveUILTforI(aI[i], bI[i], cI[i], dI[i]));
        }

        testCMoveUILEforI(aI, bI, cI, dI, rI, rI);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rI[i], cmoveUILEforI(aI[i], bI[i], cI[i], dI[i]));
        }

        //     I for L
        testCMoveUIEQforL(aI, bI, cL, dL, rL, rL);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rL[i], cmoveUIEQforL(aI[i], bI[i], cL[i], dL[i]));
        }

        testCMoveUINEforL(aI, bI, cL, dL, rL, rL);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rL[i], cmoveUINEforL(aI[i], bI[i], cL[i], dL[i]));
        }

        testCMoveUIGTforL(aI, bI, cL, dL, rL, rL);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rL[i], cmoveUIGTforL(aI[i], bI[i], cL[i], dL[i]));
        }

        testCMoveUIGEforL(aI, bI, cL, dL, rL, rL);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rL[i], cmoveUIGEforL(aI[i], bI[i], cL[i], dL[i]));
        }

        testCMoveUILTforL(aI, bI, cL, dL, rL, rL);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rL[i], cmoveUILTforL(aI[i], bI[i], cL[i], dL[i]));
        }

        testCMoveUILEforL(aI, bI, cL, dL, rL, rL);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rL[i], cmoveUILEforL(aI[i], bI[i], cL[i], dL[i]));
        }

        //     I for F
        testCMoveUIEQforF(aI, bI, cF, dF, rF, rF);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rF[i], cmoveUIEQforF(aI[i], bI[i], cF[i], dF[i]));
        }

        testCMoveUINEforF(aI, bI, cF, dF, rF, rF);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rF[i], cmoveUINEforF(aI[i], bI[i], cF[i], dF[i]));
        }

        testCMoveUIGTforF(aI, bI, cF, dF, rF, rF);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rF[i], cmoveUIGTforF(aI[i], bI[i], cF[i], dF[i]));
        }

        testCMoveUIGEforF(aI, bI, cF, dF, rF, rF);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rF[i], cmoveUIGEforF(aI[i], bI[i], cF[i], dF[i]));
        }

        testCMoveUILTforF(aI, bI, cF, dF, rF, rF);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rF[i], cmoveUILTforF(aI[i], bI[i], cF[i], dF[i]));
        }

        testCMoveUILEforF(aI, bI, cF, dF, rF, rF);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rF[i], cmoveUILEforF(aI[i], bI[i], cF[i], dF[i]));
        }

        //     I for D
        testCMoveUIEQforD(aI, bI, cD, dD, rD, rD);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rD[i], cmoveUIEQforD(aI[i], bI[i], cD[i], dD[i]));
        }

        testCMoveUINEforD(aI, bI, cD, dD, rD, rD);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rD[i], cmoveUINEforD(aI[i], bI[i], cD[i], dD[i]));
        }

        testCMoveUIGTforD(aI, bI, cD, dD, rD, rD);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rD[i], cmoveUIGTforD(aI[i], bI[i], cD[i], dD[i]));
        }

        testCMoveUIGEforD(aI, bI, cD, dD, rD, rD);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rD[i], cmoveUIGEforD(aI[i], bI[i], cD[i], dD[i]));
        }

        testCMoveUILTforD(aI, bI, cD, dD, rD, rD);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rD[i], cmoveUILTforD(aI[i], bI[i], cD[i], dD[i]));
        }

        testCMoveUILEforD(aI, bI, cD, dD, rD, rD);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rD[i], cmoveUILEforD(aI[i], bI[i], cD[i], dD[i]));
        }

        //     L for I
        testCMoveULEQforI(aL, bL, cI, dI, rI, rI);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rI[i], cmoveULEQforI(aL[i], bL[i], cI[i], dI[i]));
        }

        testCMoveULNEforI(aL, bL, cI, dI, rI, rI);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rI[i], cmoveULNEforI(aL[i], bL[i], cI[i], dI[i]));
        }

        testCMoveULGTforI(aL, bL, cI, dI, rI, rI);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rI[i], cmoveULGTforI(aL[i], bL[i], cI[i], dI[i]));
        }

        testCMoveULGEforI(aL, bL, cI, dI, rI, rI);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rI[i], cmoveULGEforI(aL[i], bL[i], cI[i], dI[i]));
        }

        testCMoveULLTforI(aL, bL, cI, dI, rI, rI);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rI[i], cmoveULLTforI(aL[i], bL[i], cI[i], dI[i]));
        }

        testCMoveULLEforI(aL, bL, cI, dI, rI, rI);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rI[i], cmoveULLEforI(aL[i], bL[i], cI[i], dI[i]));
        }

        //     L for L
        testCMoveULEQforL(aL, bL, cL, dL, rL, rL);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rL[i], cmoveULEQforL(aL[i], bL[i], cL[i], dL[i]));
        }

        testCMoveULNEforL(aL, bL, cL, dL, rL, rL);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rL[i], cmoveULNEforL(aL[i], bL[i], cL[i], dL[i]));
        }

        testCMoveULGTforL(aL, bL, cL, dL, rL, rL);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rL[i], cmoveULGTforL(aL[i], bL[i], cL[i], dL[i]));
        }

        testCMoveULGEforL(aL, bL, cL, dL, rL, rL);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rL[i], cmoveULGEforL(aL[i], bL[i], cL[i], dL[i]));
        }

        testCMoveULLTforL(aL, bL, cL, dL, rL, rL);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rL[i], cmoveULLTforL(aL[i], bL[i], cL[i], dL[i]));
        }

        testCMoveULLEforL(aL, bL, cL, dL, rL, rL);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rL[i], cmoveULLEforL(aL[i], bL[i], cL[i], dL[i]));
        }

        //     L for F
        testCMoveULEQforF(aL, bL, cF, dF, rF, rF);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rF[i], cmoveULEQforF(aL[i], bL[i], cF[i], dF[i]));
        }

        testCMoveULNEforF(aL, bL, cF, dF, rF, rF);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rF[i], cmoveULNEforF(aL[i], bL[i], cF[i], dF[i]));
        }

        testCMoveULGTforF(aL, bL, cF, dF, rF, rF);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rF[i], cmoveULGTforF(aL[i], bL[i], cF[i], dF[i]));
        }

        testCMoveULGEforF(aL, bL, cF, dF, rF, rF);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rF[i], cmoveULGEforF(aL[i], bL[i], cF[i], dF[i]));
        }

        testCMoveULLTforF(aL, bL, cF, dF, rF, rF);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rF[i], cmoveULLTforF(aL[i], bL[i], cF[i], dF[i]));
        }

        testCMoveULLEforF(aL, bL, cF, dF, rF, rF);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rF[i], cmoveULLEforF(aL[i], bL[i], cF[i], dF[i]));
        }

        //     L for D
        testCMoveULEQforD(aL, bL, cD, dD, rD, rD);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rD[i], cmoveULEQforD(aL[i], bL[i], cD[i], dD[i]));
        }

        testCMoveULNEforD(aL, bL, cD, dD, rD, rD);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rD[i], cmoveULNEforD(aL[i], bL[i], cD[i], dD[i]));
        }

        testCMoveULGTforD(aL, bL, cD, dD, rD, rD);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rD[i], cmoveULGTforD(aL[i], bL[i], cD[i], dD[i]));
        }

        testCMoveULGEforD(aL, bL, cD, dD, rD, rD);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rD[i], cmoveULGEforD(aL[i], bL[i], cD[i], dD[i]));
        }

        testCMoveULLTforD(aL, bL, cD, dD, rD, rD);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rD[i], cmoveULLTforD(aL[i], bL[i], cD[i], dD[i]));
        }

        testCMoveULLEforD(aL, bL, cD, dD, rD, rD);
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(rD[i], cmoveULLEforD(aL[i], bL[i], cD[i], dD[i]));
        }

        // Float
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
