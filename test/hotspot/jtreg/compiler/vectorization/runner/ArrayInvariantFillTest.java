/*
 * Copyright (c) 2022, 2023, Arm Limited. All rights reserved.
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

/*
 * @test
 * @summary Vectorization test on array invariant fill
 * @library /test/lib /
 *
 * @build jdk.test.whitebox.WhiteBox
 *        compiler.vectorization.runner.VectorizationTestRunner
 *
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI
 *                   -XX:-OptimizeFill
 *                   compiler.vectorization.runner.ArrayInvariantFillTest
 * @run main/othervm -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI
 *                   -XX:+OptimizeFill
 *                   compiler.vectorization.runner.ArrayInvariantFillTest
 *
 * @requires (os.simpleArch == "x64") | (os.simpleArch == "aarch64")
 * @requires vm.compiler2.enabled
 */

package compiler.vectorization.runner;

import compiler.lib.ir_framework.*;

import java.util.Random;

public class ArrayInvariantFillTest extends VectorizationTestRunner {

    private static final int SIZE = 543;

    private int intInv;
    private char charInv;
    private float floatInv;
    private double doubleInv;

    public ArrayInvariantFillTest() {
        Random ran = new Random(10);
        intInv = ran.nextInt();
        charInv = (char) ran.nextInt();
        floatInv = ran.nextFloat();
        doubleInv = ran.nextDouble();
    }

    // ---------------- Simple Fill ----------------
    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        applyIf = {"OptimizeFill", "false"},
        counts = {IRNode.REPLICATE_B, ">0"})
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        applyIf = {"OptimizeFill", "true"},
        counts = {IRNode.REPLICATE_B, "0"})
    public byte[] fillByteArray() {
        byte[] res = new byte[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (byte) 10;
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        applyIf = {"OptimizeFill", "false"},
        counts = {IRNode.REPLICATE_S, ">0"})
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        applyIf = {"OptimizeFill", "true"},
        counts = {IRNode.REPLICATE_S, "0"})
    public short[] fillShortArray() {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (short) -3333;
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        applyIf = {"OptimizeFill", "false"},
        counts = {IRNode.REPLICATE_S, ">0"})
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        applyIf = {"OptimizeFill", "true"},
        counts = {IRNode.REPLICATE_S, "0"})
    public char[] fillCharArray() {
        char[] res = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (char) 55555;
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        applyIf = {"OptimizeFill", "false"},
        counts = {IRNode.REPLICATE_I, ">0"})
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        applyIf = {"OptimizeFill", "true"},
        counts = {IRNode.REPLICATE_I, "0"})
    public int[] fillIntArray() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = 2147483647;
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.REPLICATE_L, ">0"})
    public long[] fillLongArray() {
        long[] res = new long[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = -2222222222222222L;
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        applyIf = {"OptimizeFill", "false"},
        counts = {IRNode.REPLICATE_F, ">0"})
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        applyIf = {"OptimizeFill", "true"},
        counts = {IRNode.REPLICATE_F, "0"})
    public float[] fillFloatArray() {
        float[] res = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = 3.234567e8f;
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.REPLICATE_D, ">0"})
    public double[] fillDoubleArray() {
        double[] res = new double[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = -9.87654321e50;
        }
        return res;
    }

    // ---------------- Fill With Type Change ----------------
    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.REPLICATE_L, ">0"})
    public long[] fillLongArrayWithInt() {
        long[] res = new long[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = intInv;
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.REPLICATE_L, ">0"})
    public long[] fillLongArrayWithUnsigned() {
        long[] res = new long[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = charInv;
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.REPLICATE_L, ">0"})
    public long[] fillLongArrayWithFloat() {
        long[] res = new long[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (long) floatInv;
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        applyIf = {"OptimizeFill", "false"},
        counts = {IRNode.REPLICATE_I, ">0"})
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        applyIf = {"OptimizeFill", "true"},
        counts = {IRNode.REPLICATE_I, "0"})
    public int[] fillIntArrayWithDouble() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (int) doubleInv;
        }
        return res;
    }
}
