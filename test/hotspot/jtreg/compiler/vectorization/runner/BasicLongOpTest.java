/*
 * Copyright (c) 2022, 2023, Arm Limited. All rights reserved.
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

/*
 * @test
 * @summary Vectorization test on basic long operations
 * @library /test/lib /
 *
 * @build jdk.test.whitebox.WhiteBox
 *        compiler.vectorization.runner.VectorizationTestRunner
 *
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI
 *                   compiler.vectorization.runner.BasicLongOpTest
 *
 * @requires (os.simpleArch == "x64") | (os.simpleArch == "aarch64") | (os.simpleArch == "riscv64")
 * @requires vm.compiler2.enabled
 */

package compiler.vectorization.runner;

import compiler.lib.ir_framework.*;

public class BasicLongOpTest extends VectorizationTestRunner {

    private static final int SIZE = 543;

    private long[] a;
    private long[] b;
    private long[] c;

    public BasicLongOpTest() {
        a = new long[SIZE];
        b = new long[SIZE];
        c = new long[SIZE];
        for (int i = 0; i < SIZE; i++) {
            a[i] = -123456789L * i;
            b[i] = 66666666L * i + 8888888888888888888L;
            c[i] = -987654321098765L;
        }
    }

    // ---------------- Arithmetic ----------------
    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.SUB_VL, ">0"})
    public long[] vectorNeg() {
        long[] res = new long[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = -a[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "avx512vl", "true"},
        counts = {IRNode.ABS_VL, ">0"})
    public long[] vectorAbs() {
        long[] res = new long[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = Math.abs(a[i]);
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.ADD_VL, ">0"})
    public long[] vectorAdd() {
        long[] res = new long[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = a[i] + b[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.SUB_VL, ">0"})
    public long[] vectorSub() {
        long[] res = new long[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = a[i] - b[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"sve", "true", "avx512dq", "true"},
        counts = {IRNode.MUL_VL, ">0"})
    public long[] vectorMul() {
        long[] res = new long[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = a[i] * b[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"sve", "true", "sse4.1", "true"},
        counts = {IRNode.ADD_VL, ">0"})
    @IR(applyIfCPUFeatureOr = {"sve", "true", "sse4.1", "true"},
        counts = {IRNode.MUL_VL, ">0"})
    public long[] vectorMulAdd() {
        long[] res = new long[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = c[i] + a[i] * b[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"sve", "true", "sse4.1", "true"},
        counts = {IRNode.MUL_VL, ">0", IRNode.SUB_VL, ">0"})
    public long[] vectorMulSub() {
        long[] res = new long[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = c[i] - a[i] * b[i];
        }
        return res;
    }

    // ---------------- Logic ----------------
    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.XOR_VL, ">0"})
    public long[] vectorNot() {
        long[] res = new long[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = ~a[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.AND_VL, ">0"})
    public long[] vectorAnd() {
        long[] res = new long[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = a[i] & b[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.OR_VL, ">0"})
    public long[] vectorOr() {
        long[] res = new long[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = a[i] | b[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.XOR_VL, ">0"})
    public long[] vectorXor() {
        long[] res = new long[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = a[i] ^ b[i];
        }
        return res;
    }

    // ---------------- Shift ----------------
    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true", "rvv", "true"},
        counts = {IRNode.LSHIFT_VL, ">0"})
    public long[] vectorShiftLeft() {
        long[] res = new long[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = a[i] << 3;
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true", "rvv", "true"},
        counts = {IRNode.RSHIFT_VL, ">0"})
    public long[] vectorSignedShiftRight() {
        long[] res = new long[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = a[i] >> 2;
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true", "rvv", "true"},
        counts = {IRNode.URSHIFT_VL, ">0"})
    public long[] vectorUnsignedShiftRight() {
        long[] res = new long[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = a[i] >>> 5;
        }
        return res;
    }

    // ---------------- Reduction ----------------
    @Test
    public long reductionAdd() {
        long res = 0L;
        for (int i = 0; i < SIZE; i++) {
            res += a[i];
        }
        return res;
    }

    @Test
    public long reductionAnd() {
        long res = 0xffffffffffffffffL;
        for (int i = 0; i < SIZE; i++) {
            res &= a[i];
        }
        return res;
    }

    @Test
    public long reductionOr() {
        long res = 0L;
        for (int i = 0; i < SIZE; i++) {
            res |= a[i];
        }
        return res;
    }

    @Test
    public long reductionXor() {
        long res = 0xf0f0f0f0f0f0f0f0L;
        for (int i = 0; i < SIZE; i++) {
            res ^= a[i];
        }
        return res;
    }

    @Test
    // Note that long integer max produces non-vectorizable CMoveL node.
    @IR(failOn = {IRNode.STORE_VECTOR})
    public long reductionMax() {
        long res = Long.MIN_VALUE;
        for (int i = 0; i < SIZE; i++) {
            res = Math.max(res, a[i]);
        }
        return res;
    }

    @Test
    // Note that long integer min produces non-vectorizable CMoveL node.
    @IR(failOn = {IRNode.STORE_VECTOR})
    public long reductionMin() {
        long res = Long.MAX_VALUE;
        for (int i = 0; i < SIZE; i++) {
            res = Math.min(res, a[i]);
        }
        return res;
    }
}
