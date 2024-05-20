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
 * @summary Vectorization test on basic short operations
 * @library /test/lib /
 *
 * @build jdk.test.whitebox.WhiteBox
 *        compiler.vectorization.runner.VectorizationTestRunner
 *
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI
 *                   compiler.vectorization.runner.BasicShortOpTest
 *
 * @requires vm.compiler2.enabled
 */

package compiler.vectorization.runner;

import compiler.lib.ir_framework.*;

public class BasicShortOpTest extends VectorizationTestRunner {

    private static final int SIZE = 543;

    private short[] a;
    private short[] b;
    private short[] c;
    private int[] idx;

    public BasicShortOpTest() {
        a = new short[SIZE];
        b = new short[SIZE];
        c = new short[SIZE];
        idx = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            a[i] = (short) (-12 * i);
            b[i] = (short) (9 * i + 8888);
            c[i] = (short) -32323;
            idx[i] = i;
        }
    }

    // ---------------- Arithmetic ----------------
    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.SUB_VS, ">0"})
    public short[] vectorNeg() {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (short) -a[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "ssse3", "true"},
        counts = {IRNode.ABS_VS, ">0"})
    public short[] vectorAbs() {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (short) Math.abs(a[i]);
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.ADD_VS, ">0"})
    public short[] vectorAdd() {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (short) (a[i] + b[i]);
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.SUB_VS, ">0"})
    public short[] vectorSub() {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (short) (a[i] - b[i]);
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.MUL_VS, ">0"})
    public short[] vectorMul() {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (short) (a[i] * b[i]);
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.MUL_VS, ">0", IRNode.ADD_VS, ">0"})
    public short[] vectorMulAdd() {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (short) (c[i] + a[i] * b[i]);
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.MUL_VS, ">0", IRNode.SUB_VS, ">0"})
    public short[] vectorMulSub() {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (short) (c[i] - a[i] * b[i]);
        }
        return res;
    }

    // ---------------- Logic ----------------
    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.XOR_VS, ">0"})
    public short[] vectorNot() {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (short) ~a[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.AND_VS, ">0"})
    public short[] vectorAnd() {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (short) (a[i] & b[i]);
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.OR_VS, ">0"})
    public short[] vectorOr() {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (short) (a[i] | b[i]);
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.XOR_VS, ">0"})
    public short[] vectorXor() {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (short) (a[i] ^ b[i]);
        }
        return res;
    }

    // ---------------- Shift ----------------
    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.LSHIFT_VS, ">0"})
    @IR(applyIfPlatform = {"riscv64", "true"},
        applyIfCPUFeature = {"v", "true"},
        counts = {IRNode.LSHIFT_VS, ">0"})
    public short[] vectorShiftLeft() {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (short) (a[i] << 3);
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.RSHIFT_VS, ">0"})
    @IR(applyIfPlatform = {"riscv64", "true"},
        applyIfCPUFeature = {"v", "true"},
        counts = {IRNode.RSHIFT_VS, ">0"})
    public short[] vectorSignedShiftRight() {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (short) (a[i] >> 2);
        }
        return res;
    }

    @Test
    // Note that min operations on subword types cannot be vectorized
    // because higher bits will be lost.
    @IR(failOn = {IRNode.STORE_VECTOR})
    public short[] vectorMin() {
        short[] res = new short[SIZE];
        int val = 65536;
        for (int i = 0; i < SIZE; i++) {
            res[i] = (short) Math.min(a[i], val);
        }
        return res;
    }

    @Test
    // Note that max operations on subword types cannot be vectorized
    // because higher bits will be lost.
    @IR(failOn = {IRNode.STORE_VECTOR})
    public short[] vectorMax() {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (short) Math.max(a[i], b[i]);
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
            res[i] = (short) (a[i] >>> 5);
        }
        return res;
    }

    // ------------- ReverseBytes -------------
    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "avx2", "true"},
        counts = {IRNode.REVERSE_BYTES_VS, ">0"})
    @IR(applyIfPlatform = {"riscv64", "true"},
        applyIfCPUFeature = {"zvbb", "true"},
        counts = {IRNode.REVERSE_BYTES_VS, ">0"})
    public short[] reverseBytesWithShort() {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = Short.reverseBytes(a[i]);
        }
        return res;
    }

    @Test
    // Note that reverseBytes cannot be vectorized if the vector element
    // type doesn't match the caller's class type.
    @IR(failOn = {IRNode.STORE_VECTOR})
    public int[] reverseBytesWithInt() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = Short.reverseBytes((short) idx[i]);
        }
        return res;
    }
}
