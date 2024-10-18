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
 * @summary Vectorization test on basic char operations
 * @library /test/lib /
 *
 * @build jdk.test.whitebox.WhiteBox
 *        compiler.vectorization.runner.VectorizationTestRunner
 *
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI
 *                   compiler.vectorization.runner.BasicCharOpTest
 *
 * @requires vm.compiler2.enabled
 */

package compiler.vectorization.runner;

import compiler.lib.ir_framework.*;

public class BasicCharOpTest extends VectorizationTestRunner {

    private static final int SIZE = 543;

    private char[] a;
    private char[] b;
    private char[] c;
    private int[] idx;

    public BasicCharOpTest() {
        a = new char[SIZE];
        b = new char[SIZE];
        c = new char[SIZE];
        idx = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            a[i] = (char) (20 * i);
            b[i] = (char) (i + 44444);
            c[i] = (char) 10000;
            idx[i] = i;
        }
    }

    // ---------------- Arithmetic ----------------
    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.SUB_VS, ">0"})
    public char[] vectorNeg() {
        char[] res = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (char) -a[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "ssse3", "true"},
        counts = {IRNode.STORE_VECTOR, ">0"})
    @IR(failOn = {IRNode.ABS_VI, IRNode.ABS_VB, IRNode.ABS_VL}) // AVS_VC does not exist
    public char[] vectorAbs() {
        char[] res = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (char) Math.abs(a[i]);
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.ADD_VS, ">0"}) // char add same as for short
    public char[] vectorAdd() {
        char[] res = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (char) (a[i] + b[i]);
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.SUB_VS, ">0"})
    public char[] vectorSub() {
        char[] res = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (char) (a[i] - b[i]);
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.MUL_VS, ">0"})
    public char[] vectorMul() {
        char[] res = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (char) (a[i] * b[i]);
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.MUL_VS, ">0",
                  IRNode.ADD_VS, ">0"}) // char add same as for short
    public char[] vectorMulAdd() {
        char[] res = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (char) (c[i] + a[i] * b[i]);
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.MUL_VS, ">0", IRNode.SUB_VS, ">0"})
    public char[] vectorMulSub() {
        char[] res = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (char) (c[i] - a[i] * b[i]);
        }
        return res;
    }

    // ---------------- Logic ----------------
    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.XOR_VS, ">0"})
    public char[] vectorNot() {
        char[] res = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (char) ~a[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.AND_VS, ">0"})
    public char[] vectorAnd() {
        char[] res = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (char) (a[i] & b[i]);
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.OR_VS, ">0"})
    public char[] vectorOr() {
        char[] res = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (char) (a[i] | b[i]);
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.XOR_VS, ">0"})
    public char[] vectorXor() {
        char[] res = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (char) (a[i] ^ b[i]);
        }
        return res;
    }

    // ---------------- Shift ----------------
    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true", "rvv", "true"},
        counts = {IRNode.LSHIFT_VC, ">0"})
    public char[] vectorShiftLeft() {
        char[] res = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (char) (a[i] << 3);
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true", "rvv", "true"},
        counts = {IRNode.URSHIFT_VC, ">0"})
    public char[] vectorSignedShiftRight() {
        char[] res = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (char) (a[i] >> 2);
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true", "rvv", "true"},
        counts = {IRNode.URSHIFT_VC, ">0"})
    public char[] vectorUnsignedShiftRight() {
        char[] res = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (char) (a[i] >>> 5);
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
    public char[] reverseBytesWithChar() {
        char[] res = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = Character.reverseBytes(a[i]);
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
            res[i] = Character.reverseBytes((char) idx[i]);
        }
        return res;
    }
}
