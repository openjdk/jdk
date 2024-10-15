/*
 * Copyright (c) 2022, 2023, Arm Limited. All rights reserved.
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

/*
 * @test
 * @summary Vectorization test on array index fill
 * @library /test/lib /
 *
 * @build jdk.test.whitebox.WhiteBox
 *        compiler.vectorization.runner.VectorizationTestRunner
 *
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI
 *                   compiler.vectorization.runner.ArrayIndexFillTest
 *
 * @requires (os.simpleArch == "x64") | (os.simpleArch == "aarch64")
 * @requires vm.compiler2.enabled
 */

package compiler.vectorization.runner;

import compiler.lib.ir_framework.*;

public class ArrayIndexFillTest extends VectorizationTestRunner {

    private static final int SIZE = 543;
    private static int init = 0;
    private static int limit = SIZE;

    private int[] a;

    public ArrayIndexFillTest() {
        a = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            a[i] = -5050 * i;
        }
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"sve", "true", "avx2", "true"},
        counts = {IRNode.POPULATE_INDEX, ">0"})
    public byte[] fillByteArray() {
        byte[] res = new byte[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (byte) i;
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"sve", "true", "avx2", "true"},
        counts = {IRNode.POPULATE_INDEX, ">0"})
    public short[] fillShortArray() {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (short) i;
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"sve", "true", "avx2", "true"},
        counts = {IRNode.POPULATE_INDEX, ">0"})
    public char[] fillCharArray() {
        char[] res = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (char) i;
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"sve", "true", "avx2", "true"},
        counts = {IRNode.POPULATE_INDEX, ">0"})
    public int[] fillIntArray() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = i;
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"sve", "true", "avx2", "true"},
        counts = {IRNode.POPULATE_INDEX, "=0"})
    // The ConvI2L can be split through the AddI, creating a mix of
    // ConvI2L(AddI) and AddL(ConvI2L) cases, which do not vectorize.
    // See: JDK-8332878
    public long[] fillLongArray() {
        long[] res = new long[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = i;
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"sve", "true", "avx2", "true"},
        counts = {IRNode.POPULATE_INDEX, ">0"})
    // The variable init/limit has the consequence that we do not split
    // the ConvI2L through the AddI.
    public long[] fillLongArray2() {
        long[] res = new long[SIZE];
        for (int i = init; i < limit; i++) {
            res[i] = i;
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"sve", "true", "avx2", "true"},
        counts = {IRNode.POPULATE_INDEX, "=0"})
    // See: JDK-8332878
    public float[] fillFloatArray() {
        float[] res = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = i;
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"sve", "true", "avx2", "true"},
        counts = {IRNode.POPULATE_INDEX, ">0"})
    public float[] fillFloatArray2() {
        float[] res = new float[SIZE];
        for (int i = init; i < limit; i++) {
            res[i] = i;
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"sve", "true", "avx2", "true"},
        counts = {IRNode.POPULATE_INDEX, "=0"})
    // See: JDK-8332878
    public double[] fillDoubleArray() {
        double[] res = new double[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = i;
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"sve", "true", "avx2", "true"},
        counts = {IRNode.POPULATE_INDEX, ">0"})
    public double[] fillDoubleArray2() {
        double[] res = new double[SIZE];
        for (int i = init; i < limit; i++) {
            res[i] = i;
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"sve", "true", "avx2", "true"},
        counts = {IRNode.POPULATE_INDEX, ">0"})
    public short[] fillShortArrayWithShortIndex() {
        short[] res = new short[SIZE];
        for (short i = 0; i < SIZE; i++) {
            res[i] = i;
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"sve", "true", "avx2", "true"},
        counts = {IRNode.POPULATE_INDEX, ">0"})
    public int[] fillMultipleArraysDifferentTypes1() {
        int[] res1 = new int[SIZE];
        short[] res2 = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res1[i] = i;
            res2[i] = (short) i;
        }
        return res1;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"sve", "true", "avx2", "true"},
        counts = {IRNode.POPULATE_INDEX, ">0"})
    public char[] fillMultipleArraysDifferentTypes2() {
        int[] res1 = new int[SIZE];
        char[] res2 = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res1[i] = i;
            res2[i] = (char) i;
        }
        return res2;
    }

    @Test
    public int[] fillNonIndexValue() {
        int[] res = new int[SIZE];
        int val = 10000;
        for (int i = 0; i < SIZE; i++) {
            res[i] = val++;
        }
        return res;
    }
}
