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
 * @summary Vectorization test on different loop ranges and strides
 * @library /test/lib /
 *
 * @build jdk.test.whitebox.WhiteBox
 *        compiler.vectorization.runner.VectorizationTestRunner
 *
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI
 *                   compiler.vectorization.runner.LoopRangeStrideTest
 *
 * @requires (os.simpleArch == "x64") | (os.simpleArch == "aarch64")
 * @requires vm.compiler2.enabled
 */

package compiler.vectorization.runner;

import compiler.lib.ir_framework.*;

import java.util.Random;

public class LoopRangeStrideTest extends VectorizationTestRunner {

    private static final int SIZE = 543;

    private int[] a;
    private int[] b;
    private int start;
    private int end;

    public LoopRangeStrideTest() {
        a = new int[SIZE];
        b = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            a[i] = -i / 2;
            b[i] = 444 * i - 12345;
        }

        Random ran = new Random(0);
        start = Math.abs(ran.nextInt() % 100);
        end = start + 315;
    }

    // ---------------- Range ----------------
    @Test
    public int[] smallConstantRange() {
        int[] res = new int[SIZE];
        for (int i = 20; i < 27; i++) {
            res[i] = a[i] + b[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.STORE_VECTOR, ">0"})
    public int[] nonConstantRange() {
        int[] res = new int[SIZE];
        for (int i = start; i < end; i++) {
            res[i] = a[i] - b[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.STORE_VECTOR, ">0"})
    public int[] crossZeroRange() {
        int[] res = new int[SIZE];
        for (int i = -20; i < 20; i++) {
            res[i + 50] = a[i + 50] + b[i + 50];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.STORE_VECTOR, ">0"})
    public int[] nonEqualTestRange() {
        int[] res = new int[SIZE];
        for (int i = start; i != end; i++) {
            res[i] = a[i] - b[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse4.1", "true"},
        counts = {IRNode.STORE_VECTOR, ">0"})
    public int[] shortInductionLoop() {
        int[] res = new int[SIZE];
        for (short s = 123; s < 456; s++) {
            res[s] = a[s] * b[s];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.STORE_VECTOR, ">0"})
    public int[] whileLoop() {
        int[] res = new int[SIZE];
        int i = start;
        while (i < end) {
            res[i] = a[i] & b[i];
            i++;
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.STORE_VECTOR, ">0"})
    public int[] doWhileLoop() {
        int[] res = new int[SIZE];
        int i = start;
        do {
            res[i] = a[i] | b[i];
            i++;
        } while (i < end);
        return res;
    }

    // ---------------- Stride ----------------
    @Test
    public int[] stride2Loop() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i += 2) {
            res[i] = a[i] * b[i];
        }
        return res;
    }

    @Test
    public int[] stride3Loop() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i += 3) {
            res[i] = a[i] * b[i];
        }
        return res;
    }

    @Test
    public int[] stride4Loop() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i += 4) {
            res[i] = a[i] * b[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse4.1", "true"},
        counts = {IRNode.STORE_VECTOR, ">0"})
    public int[] countDownLoop() {
        int[] res = new int[SIZE];
        for (int i = SIZE - 1; i > 0; i--) {
            res[i] = a[i] * b[i];
        }
        return res;
    }

    @Test
    public int[] strideMinus2Loop() {
        int[] res = new int[SIZE];
        for (int i = SIZE - 1; i > 0; i -= 2) {
            res[i] = a[i] * b[i];
        }
        return res;
    }

    // ---------- Stride with scale ----------
    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse4.1", "true"},
        counts = {IRNode.STORE_VECTOR, ">0"})
    public int[] countupLoopWithNegScale() {
        int[] res = new int[SIZE];
        for (int i = SIZE / 2; i < SIZE; i++) {
            res[SIZE - i] = a[SIZE - i] * b[SIZE - i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse4.1", "true"},
        counts = {IRNode.STORE_VECTOR, ">0"})
    public int[] countDownLoopWithNegScale() {
        int[] res = new int[SIZE];
        for (int i = SIZE / 2; i > 0; i--) {
            res[SIZE - i] = a[SIZE - i] * b[SIZE - i];
        }
        return res;
    }
}
