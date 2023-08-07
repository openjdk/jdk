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
 * @summary Vectorization test on multiple loops in a method
 * @library /test/lib /
 *
 * @build jdk.test.whitebox.WhiteBox
 *        compiler.vectorization.runner.VectorizationTestRunner
 *
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI
 *                   compiler.vectorization.runner.MultipleLoopsTest
 *
 * @requires vm.compiler2.enabled
 */

package compiler.vectorization.runner;

import compiler.lib.ir_framework.*;

public class MultipleLoopsTest extends VectorizationTestRunner {

    private static final int SIZE = 543;

    private int[] a;
    private int[] b;
    private int[] c;

    public MultipleLoopsTest() {
        a = new int[SIZE];
        b = new int[SIZE];
        c = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            a[i] = -654321 * i;
            b[i] =  123456 * i;
            c[i] = -998877 * i;
        }
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.STORE_VECTOR, ">0"})
    public int[] consecutiveLoops() {
        int[] res1 = new int[SIZE];
        int[] res2 = new int[SIZE];
        int[] res3 = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res1[i] = a[i] + b[i];
        }
        for (int i = 0; i < SIZE; i++) {
            res2[i] = a[i] - b[i];
        }
        for (int i = 0; i < SIZE; i++) {
            res3[i] = res1[i] * res2[i];
        }
        return res3;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.STORE_VECTOR, ">0"})
    public int[] consecutiveLoopsNested() {
        int[] res = new int[SIZE];
        for (int outer = 0; outer < 30; outer++) {
            for (int i = 0; i < SIZE / 2; i++) {
                res[i] += a[i];
            }
            for (int i = SIZE / 2; i < SIZE; i++) {
                res[i] *= b[i];
            }
        } // Outer loop is counted
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse4.1", "true"},
        counts = {IRNode.STORE_VECTOR, ">0"})
    public int[] nestedLoopOuterNonCounted() {
        int i = 1;
        int[] res = new int[SIZE];
        while (i < SIZE) {
            int val = i * a[i];
            for (int j = 0; j < SIZE; j++) {
                res[j] = b[j] * val;
            }
            i *= 2;
        } // Outer loop is non-counted
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse4.1", "true"},
        counts = {IRNode.STORE_VECTOR, ">0"})
    public int[] nestedLoopIndexCompute() {
        int[] res = new int[SIZE];
        for (int i = 50; i < 100; i++) {
            for (int j = 0; j < 200 - i; j++) {
                res[i + j] = a[i + j] * b[i + j];
            }
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.STORE_VECTOR, ">0"})
    public float reductionLoopWithAnotherReductionInput() {
        float res = 0.0F;
        int N = 400;
        int[] arr = new int[N];
        int i1 = 0, i2 = 0, i3, i4;
        for (int j = 0; j < N; ++j) {
            for (i3 = 1; i3 < 63; ++i3) {
                arr[i3] -= 1;
                i1 += i3;
            }
            for (i4 = 2; i4 < 63; ++i4) {
                res += i4 - i2;
                i2 = i1;
            }
        }
        return res;
    }
}
