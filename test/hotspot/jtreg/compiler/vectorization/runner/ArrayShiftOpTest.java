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
 * @bug 8183390 8332905
 * @summary Vectorization test on bug-prone shift operation
 * @library /test/lib /
 *
 * @build jdk.test.whitebox.WhiteBox
 *        compiler.vectorization.runner.VectorizationTestRunner
 *
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI
 *                   compiler.vectorization.runner.ArrayShiftOpTest
 *
 * @requires (os.simpleArch == "x64") | (os.simpleArch == "aarch64") | (os.simpleArch == "riscv64")
 * @requires vm.compiler2.enabled
 */

package compiler.vectorization.runner;

import compiler.lib.ir_framework.*;

import java.util.Random;

public class ArrayShiftOpTest extends VectorizationTestRunner {

    private static final int SIZE = 543;
    private static       int size = 543;

    private int[] ints;
    private long[] longs;
    private short[] shorts1;
    private short[] shorts2;
    private int largeDist;

    public ArrayShiftOpTest() {
        ints = new int[SIZE];
        longs = new long[SIZE];
        shorts1 = new short[SIZE];
        shorts2 = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            ints[i] = -888999 * i;
            longs[i] = 999998888800000L * i;
            shorts1[i] = (short) (4 * i);
            shorts2[i] = (short) (-3 * i);
        }
        Random ran = new Random(999);
        largeDist = 123;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "avx2", "true"},
        counts = {IRNode.STORE_VECTOR, ">0"})
    @IR(applyIfCPUFeature = {"avx512f", "true"},
        counts = {IRNode.ROTATE_RIGHT_V, ">0"})
    @IR(applyIfPlatform = {"riscv64", "true"},
        applyIfCPUFeature = {"zvbb", "true"},
        counts = {IRNode.ROTATE_RIGHT_V, ">0"})
    public int[] intCombinedRotateShift() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (ints[i] << 14) | (ints[i] >>> 18);
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"sve", "true", "avx2", "true"},
        counts = {IRNode.STORE_VECTOR, ">0"})
    @IR(applyIfCPUFeature = {"avx512f", "true"},
        counts = {IRNode.ROTATE_RIGHT_V, ">0"})
    // Requires size to not be known at compile time, otherwise the shift
    // can get constant folded with the (iv + const) pattern from the
    // PopulateIndex.
    public int[] intCombinedRotateShiftWithPopulateIndex() {
        int[] res = new int[size];
        for (int i = 0; i < size; i++) {
            res[i] = (i << 14) | (i >>> 18);
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "avx2", "true"},
        counts = {IRNode.STORE_VECTOR, ">0"})
    @IR(applyIfCPUFeature = {"avx512f", "true"},
        counts = {IRNode.ROTATE_RIGHT_V, ">0"})
    @IR(applyIfPlatform = {"riscv64", "true"},
        applyIfCPUFeature = {"zvbb", "true"},
        counts = {IRNode.ROTATE_RIGHT_V, ">0"})
    public long[] longCombinedRotateShift() {
        long[] res = new long[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (longs[i] << 55) | (longs[i] >>> 9);
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.RSHIFT_VI, ">0"})
    @IR(applyIfPlatform = {"riscv64", "true"},
        applyIfCPUFeature = {"v", "true"},
        counts = {IRNode.RSHIFT_VI, ">0"})
    public int[] intShiftLargeDistConstant() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = ints[i] >> 35;
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.RSHIFT_VI, ">0"})
    @IR(applyIfPlatform = {"riscv64", "true"},
        applyIfCPUFeature = {"v", "true"},
        counts = {IRNode.RSHIFT_VI, ">0"})
    public int[] intShiftLargeDistInvariant() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = ints[i] >> largeDist;
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.RSHIFT_VS, ">0"})
    @IR(applyIfPlatform = {"riscv64", "true"},
        applyIfCPUFeature = {"v", "true"},
        counts = {IRNode.RSHIFT_VS, ">0"})
    public short[] shortShiftLargeDistConstant() {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (short) (shorts1[i] >> 65);
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.RSHIFT_VS, ">0"})
    @IR(applyIfPlatform = {"riscv64", "true"},
        applyIfCPUFeature = {"v", "true"},
        counts = {IRNode.RSHIFT_VS, ">0"})
    public short[] shortShiftLargeDistInvariant() {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (short) (shorts2[i] >> (largeDist - 25));
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.LSHIFT_VL, ">0"})
    @IR(applyIfPlatform = {"riscv64", "true"},
        applyIfCPUFeature = {"v", "true"},
        counts = {IRNode.LSHIFT_VL, ">0"})
    public long[] longShiftLargeDistConstant() {
        long[] res = new long[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = longs[i] << 77;
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.URSHIFT_VL, ">0"})
    @IR(applyIfPlatform = {"riscv64", "true"},
        applyIfCPUFeature = {"v", "true"},
        counts = {IRNode.URSHIFT_VL, ">0"})
    public long[] longShiftLargeDistInvariant() {
        long[] res = new long[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = longs[i] >>> largeDist;
        }
        return res;
    }

    @Test
    // Note that shift with variant distance cannot be vectorized.
    @IR(failOn = {IRNode.STORE_VECTOR})
    public long[] variantShiftDistance() {
        long[] res = new long[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = longs[i] >> ints[i];
        }
        return res;
    }

    @Test
    // Note that shift with variant distance cannot be vectorized.
    @IR(failOn = {IRNode.STORE_VECTOR})
    public short[] loopIndexShiftDistance() {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (short) (shorts1[i] >> i);
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.RSHIFT_VS, ">0"})
    @IR(applyIfPlatform = {"riscv64", "true"},
        applyIfCPUFeature = {"v", "true"},
        counts = {IRNode.RSHIFT_VS, ">0"})
    public short[] vectorUnsignedShiftRight() {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (short) (shorts2[i] >>> 3);
        }
        return res;
    }

    @Test
    // Note that right shift operations on subword expressions cannot be
    // vectorized since precise type info about signedness is missing.
    @IR(failOn = {IRNode.STORE_VECTOR})
    public short[] subwordExpressionRightShift() {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (short) ((shorts1[i] + shorts2[i]) >> 4);
        }
        return res;
    }
}
