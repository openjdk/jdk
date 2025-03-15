/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

/**
* @test
* @key randomness
* @bug 8350835
* @summary Test bug fix for JDK-8350835 discovered through Template Framework
* @library /test/lib /
* @run driver compiler.vectorization.TestFloat16ToFloatConv
*/

package compiler.vectorization;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Utils;
import java.util.Random;

import static compiler.lib.generators.Generators.G;

public class TestFloat16ToFloatConv {
    private static final Random RANDOM = Utils.getRandomInstance();
    private static final int SIZE = 1024;
    private static byte[] aB = new byte[SIZE];
    private static char[] aC = new char[SIZE];
    private static short[] aS = new short[SIZE];
    private static int[] aI = new int[SIZE];
    private static long[] aL = new long[SIZE];
    private static float[] goldB, goldC, goldS, goldI, goldL;

    static {
        // Fill int and long array using Generators
        G.fill(G.ints(), aI);
        G.fill(G.longs(), aL);
        // Generators do not support byte, char, short array currently so perform manual random fill
        for (int i = 0; i < aB.length; i++) {
            aB[i] = (byte)RANDOM.nextInt();
            aC[i] = (char)RANDOM.nextInt();
            aS[i] = (short)RANDOM.nextInt();
        }
        goldB = testByteKernel(aB);
        goldC = testCharKernel(aC);
        goldS = testShortKernel(aS);
        goldI = testIntKernel(aI);
        goldL = testLongKernel(aL);
    }

    @Test
    // Scalar IR for loop body: LoadB, ConvHF2F, StoreF
    // Vectorized IR: LoadVector, VectorCastHF2F , StoreVector
    // Vectorization disabled as input to VectorCastHF2F would have been a byte vector instead of short vector
    // but the VectorCastHF2F expects short vector as input
    // See JDK-8352093 for details
    @IR(failOn = { IRNode.VECTOR_CAST_HF2F })
    public static float[] testByteKernel(byte[] barr) {
        float[] res = new float[barr.length];
        for (int i = 0; i < barr.length; i++) {
            res[i] = Float.float16ToFloat(barr[i]);
        }
        return res;
    }

    @Test
    @IR(counts = {IRNode.VECTOR_CAST_HF2F, "> 0"},
        applyIfOr = {"UseCompactObjectHeaders", "false", "AlignVector", "false"},
        applyIfPlatformOr = {"x64", "true", "aarch64", "true", "riscv64", "true"},
        applyIfCPUFeatureOr = {"f16c", "true", "avx512f", "true", "zvfh", "true", "asimd", "true", "sve", "true"})
    public static float[] testCharKernel(char[] carr) {
        float[] res = new float[carr.length];
        for (int i = 0; i < carr.length; i++) {
            res[i] = Float.float16ToFloat((short)carr[i]);
        }
        return res;
    }

    @Test
    @IR(counts = {IRNode.VECTOR_CAST_HF2F, "> 0"},
        applyIfOr = {"UseCompactObjectHeaders", "false", "AlignVector", "false"},
        applyIfPlatformOr = {"x64", "true", "aarch64", "true", "riscv64", "true"},
        applyIfCPUFeatureOr = {"f16c", "true", "avx512f", "true", "zvfh", "true", "asimd", "true", "sve", "true"})
    public static float[] testShortKernel(short[] sarr) {
        float[] res = new float[sarr.length];
        for (int i = 0; i < sarr.length; i++) {
            res[i] = Float.float16ToFloat(sarr[i]);
        }
        return res;
    }

    @Test
    // Scalar IR for loop body: LoadI, LShiftI by 16, RShiftI by 16, ConvHF2F, StoreF
    // Vectorized IR: LoadVector, LShiftVI, RShiftVI, VectorCastHF2F, StoreVector
    // Vectorization disabled as input to VectorCastHF2F would have been an int vector instead of short vector
    // but the VectorCastHF2F expects short vector as input
    // See JDK-8352093 for details
    @IR(failOn = { IRNode.VECTOR_CAST_HF2F })
    public static float[] testIntKernel(int[] iarr) {
        float[] res = new float[iarr.length];
        for (int i = 0; i < iarr.length; i++) {
            res[i] = Float.float16ToFloat((short)iarr[i]);
        }
        return res;
    }

    @Test
    // Scalar IR for loop body: ConvL2I, LoadI, LShiftI by 16, RShiftI by 16, ConvHF2F, StoreF
    // Vectorized IR: VectorCastL2X, LoadVector, LShiftVI, RShiftVI, VectorCastHF2F, StoreVector
    // Vectorization disabled as input to VectorCastHF2F would have been an int vector instead of short vector
    // but the VectorCastHF2F expects short vector as input
    // See JDK-8352093 for details
    @IR(failOn = { IRNode.VECTOR_CAST_HF2F })
    public static float[] testLongKernel(long[] larr) {
        float[] res = new float[larr.length];
        for (int i = 0; i < larr.length; i++) {
            res[i] = Float.float16ToFloat((short)larr[i]);
        }
        return res;
    }

    public static void checkResult(float[] res, float[] gold) {
        for (int i = 0; i < res.length; i++) {
            if (Float.floatToIntBits(res[i]) != Float.floatToIntBits(gold[i])) {
                throw new RuntimeException("wrong value: " + Float.floatToRawIntBits(res[i]) + " " + Float.floatToRawIntBits(gold[i]));
            }
        }
    }

    @Run(test = {"testByteKernel"})
    public static void testByte() {
        float[] farr = testByteKernel(aB);
        checkResult(farr, goldB);
    }

    @Run(test = {"testCharKernel"})
    public static void testChar() {
        float[] farr = testCharKernel(aC);
        checkResult(farr, goldC);
    }

    @Run(test = {"testShortKernel"})
    public static void testShort() {
        float[] farr = testShortKernel(aS);
        checkResult(farr, goldS);
    }

    @Run(test = {"testIntKernel"})
    public static void testInt() {
        float[] farr = testIntKernel(aI);
        checkResult(farr, goldI);
    }

    @Run(test = {"testLongKernel"})
    public static void testLong() {
        float[] farr = testLongKernel(aL);
        checkResult(farr, goldL);
    }

    public static void main(String [] args) {
        TestFramework.run(TestFloat16ToFloatConv.class);
    }
}

