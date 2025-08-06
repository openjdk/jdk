/*
 * Copyright (c) 2022, 2023, Arm Limited. All rights reserved.
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

/*
 * @test
 * @summary Vectorization test on basic int operations
 * @library /test/lib /
 *
 * @build jdk.test.whitebox.WhiteBox
 *        compiler.vectorization.runner.VectorizationTestRunner
 *
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI
 *                   compiler.vectorization.runner.BasicIntOpTest
 *
 * @requires vm.compiler2.enabled
 */

package compiler.vectorization.runner;

import compiler.lib.ir_framework.*;

public class BasicIntOpTest extends VectorizationTestRunner {

    private static final int SIZE = 543;

    private int[] a;
    private int[] b;
    private int[] c;

    public BasicIntOpTest() {
        a = new int[SIZE];
        b = new int[SIZE];
        c = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            a[i] = -25 * i;
            b[i] = 333 * i + 9999;
            c[i] = -987654321;
        }
    }

    // ---------------- Arithmetic ----------------
    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true", "rvv", "true"},
        counts = {IRNode.SUB_VI, ">0"})
    public int[] vectorNeg() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = -a[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "ssse3", "true", "rvv", "true"},
        counts = {IRNode.ABS_VI, ">0"})
    public int[] vectorAbs() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = Math.abs(a[i]);
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true", "rvv", "true"},
        counts = {IRNode.ADD_VI, ">0"})
    public int[] vectorAdd() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = a[i] + b[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true", "rvv", "true"},
        counts = {IRNode.SUB_VI, ">0"})
    public int[] vectorSub() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = a[i] - b[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse4.1", "true", "rvv", "true"},
        counts = {IRNode.MUL_VI, ">0"})
    public int[] vectorMul() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = a[i] * b[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse4.1", "true", "rvv", "true"},
        counts = {IRNode.MUL_VI, ">0", IRNode.ADD_VI, ">0"})
    public int[] vectorMulAdd() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = c[i] + a[i] * b[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse4.1", "true", "rvv", "true"},
        counts = {IRNode.MUL_VI, ">0", IRNode.SUB_VI, ">0"})
    public int[] vectorMulSub() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = c[i] - a[i] * b[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "avx2", "true", "zvbb", "true"},
        counts = {IRNode.POPCOUNT_VI, ">0"})
    public int[] vectorPopCount() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = Integer.bitCount(a[i]);
        }
        return res;
    }

    // ---------------- Logic ----------------
    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true", "rvv", "true"},
        counts = {IRNode.XOR_VI, ">0"})
    public int[] vectorNot() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = ~a[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true", "rvv", "true"},
        counts = {IRNode.AND_VI, ">0"})
    public int[] vectorAnd() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = a[i] & b[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true", "rvv", "true"},
        counts = {IRNode.OR_VI, ">0"})
    public int[] vectorOr() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = a[i] | b[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true", "rvv", "true"},
        counts = {IRNode.XOR_VI, ">0"})
    public int[] vectorXor() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = a[i] ^ b[i];
        }
        return res;
    }

    // ---------------- Shift ----------------
    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true", "rvv", "true"},
        counts = {IRNode.LSHIFT_VI, ">0"})
    public int[] vectorShiftLeft() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = a[i] << 3;
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true", "rvv", "true"},
        counts = {IRNode.RSHIFT_VI, ">0"})
    public int[] vectorSignedShiftRight() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = a[i] >> 2;
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true", "rvv", "true"},
        counts = {IRNode.URSHIFT_VI, ">0"})
    public int[] vectorUnsignedShiftRight() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = a[i] >>> 5;
        }
        return res;
    }

    // ---------------- Reduction ----------------
    @Test
    public int reductionAdd() {
        int res = 0;
        for (int i = 0; i < SIZE; i++) {
            res += a[i];
        }
        return res;
    }

    @Test
    public int reductionAnd() {
        int res = 0xffffffff;
        for (int i = 0; i < SIZE; i++) {
            res &= a[i];
        }
        return res;
    }

    @Test
    public int reductionOr() {
        int res = 0;
        for (int i = 0; i < SIZE; i++) {
            res |= a[i];
        }
        return res;
    }

    @Test
    public int reductionXor() {
        int res = 0x0f0f0f0f;
        for (int i = 0; i < SIZE; i++) {
            res ^= a[i];
        }
        return res;
    }

    @Test
    // Note that integer max produces non-vectorizable CMoveI node.
    @IR(failOn = {IRNode.STORE_VECTOR})
    public int reductionMax() {
        int res = Integer.MIN_VALUE;
        for (int i = 0; i < SIZE; i++) {
            res = Math.max(res, a[i]);
        }
        return res;
    }

    @Test
    // Note that integer min produces non-vectorizable CMoveI node.
    @IR(failOn = {IRNode.STORE_VECTOR})
    public int reductionMin() {
        int res = Integer.MAX_VALUE;
        for (int i = 0; i < SIZE; i++) {
            res = Math.min(res, a[i]);
        }
        return res;
    }
}
