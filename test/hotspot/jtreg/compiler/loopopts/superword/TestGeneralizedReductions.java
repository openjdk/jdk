/*
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

/**
 * @test
 * @bug 8287087
 * @summary Test reduction vectorizations that are enabled by performing SLP
 *          reduction analysis on unrolled loops.
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestGeneralizedReductions
 */

package compiler.loopopts.superword;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;

public class TestGeneralizedReductions {

    private static int acc = 0;

    public static void main(String[] args) throws Exception {
        // Fix maximum number of unrolls for test stability.
        TestFramework.runWithFlags("-XX:+IgnoreUnrecognizedVMOptions", "-XX:LoopMaxUnroll=16");
    }

    @Run(test = {"testReductionOnGlobalAccumulator",
                 "testReductionOnPartiallyUnrolledLoop",
                 "testReductionOnLargePartiallyUnrolledLoop",
                 "testReductionOnPartiallyUnrolledLoopWithSwappedInputs",
                 "testMapReductionOnGlobalAccumulator"})
    void run() {
        long[] array = new long[128];
        long result;

        initArray(array);
        result = testReductionOnGlobalAccumulator(array);
        Asserts.assertEQ(result, 8128L, "unexpected result");

        initArray(array);
        result = testReductionOnPartiallyUnrolledLoop(array);
        Asserts.assertEQ(result, 8128L, "unexpected result");

        initArray(array);
        result = testReductionOnLargePartiallyUnrolledLoop(array);
        Asserts.assertEQ(result, 8128L, "unexpected result");

        initArray(array);
        result = testReductionOnPartiallyUnrolledLoopWithSwappedInputs(array);
        Asserts.assertEQ(result, 8128L, "unexpected result");

        initArray(array);
        result = testMapReductionOnGlobalAccumulator(array);
        Asserts.assertEQ(result, 448L, "unexpected result");
    }

    private static void initArray(long[] array) {
        for (int i = 0; i < array.length; i++) {
            array[i] = i;
        }
    }

    @Test
    @IR(applyIfCPUFeature = {"avx2", "true"},
        applyIf = {"SuperWordReductions", "true"},
        applyIfPlatform = {"64-bit", "true"},
        counts = {IRNode.ADD_REDUCTION_VI, ">= 1"})
    @IR(applyIfPlatform = {"riscv64", "true"},
        applyIfCPUFeature = {"rvv", "true"},
        applyIfAnd = {"SuperWordReductions", "true", "MaxVectorSize", ">=32"},
        counts = {IRNode.ADD_REDUCTION_VI, ">= 1"})
    private static long testReductionOnGlobalAccumulator(long[] array) {
        acc = 0;
        for (int i = 0; i < array.length; i++) {
            acc += array[i];
        }
        return acc;
    }

    @Test
    @IR(applyIfCPUFeature = {"avx2", "true"},
        applyIf = {"SuperWordReductions", "true"},
        applyIfPlatform = {"64-bit", "true"},
        counts = {IRNode.ADD_REDUCTION_VI, ">= 1"})
    @IR(applyIfPlatform = {"riscv64", "true"},
        applyIfCPUFeature = {"rvv", "true"},
        applyIfAnd = {"SuperWordReductions", "true", "MaxVectorSize", ">=32"},
        counts = {IRNode.ADD_REDUCTION_VI, ">= 1"})
    private static long testReductionOnPartiallyUnrolledLoop(long[] array) {
        int sum = 0;
        for (int i = 0; i < array.length / 2; i++) {
            sum += array[2*i];
            sum += array[2*i + 1];
        }
        return sum;
    }

    @Test
    @IR(applyIfCPUFeature = {"avx2", "true"},
        applyIf = {"SuperWordReductions", "true"},
        applyIfPlatform = {"64-bit", "true"},
        counts = {IRNode.ADD_REDUCTION_VI, ">= 1"})
    @IR(applyIfPlatform = {"riscv64", "true"},
        applyIfCPUFeature = {"rvv", "true"},
        applyIfAnd = {"SuperWordReductions", "true", "MaxVectorSize", ">=32"},
        counts = {IRNode.ADD_REDUCTION_VI, ">= 1"})
    private static long testReductionOnLargePartiallyUnrolledLoop(long[] array) {
        int sum = 0;
        for (int i = 0; i < array.length / 8; i++) {
            sum += array[8*i];
            sum += array[8*i + 1];
            sum += array[8*i + 2];
            sum += array[8*i + 3];
            sum += array[8*i + 4];
            sum += array[8*i + 5];
            sum += array[8*i + 6];
            sum += array[8*i + 7];
        }
        return sum;
    }

    // This test illustrates a limitation of the current reduction analysis: it
    // fails to detect reduction cycles where nodes are connected via different
    // input indices (except if the differences result from C2 edge swapping).
    // If this limitation is overcome in the future, the test case should be
    // turned into a positive one.
    @Test
    @IR(applyIfCPUFeatureOr = {"avx2", "true", "rvv", "true"},
        applyIf = {"SuperWordReductions", "true"},
        applyIfPlatform = {"64-bit", "true"},
        failOn = {IRNode.ADD_REDUCTION_VI})
    private static long testReductionOnPartiallyUnrolledLoopWithSwappedInputs(long[] array) {
        int sum = 0;
        for (int i = 0; i < array.length / 2; i++) {
            sum = sum + (int)array[2*i];
            sum = (int)array[2*i + 1] + sum;
        }
        return sum;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"avx2", "true"},
        applyIfAnd = {"SuperWordReductions", "true","UsePopCountInstruction", "true"},
        applyIfPlatform = {"64-bit", "true"},
        counts = {IRNode.ADD_REDUCTION_VI, ">= 1",
                  IRNode.POPCOUNT_VL, ">= 1"})
    @IR(applyIfPlatform = {"riscv64", "true"},
        applyIfCPUFeatureOr = {"zvbb", "true"},
        applyIfAnd = {"SuperWordReductions", "true","UsePopCountInstruction", "true"},
        counts = {IRNode.ADD_REDUCTION_VI, ">= 1",
                  IRNode.POPCOUNT_VL, ">= 1"})
    private static long testMapReductionOnGlobalAccumulator(long[] array) {
        acc = 0;
        for (int i = 0; i < array.length; i++) {
            acc += Long.bitCount(array[i]);
        }
        return acc;
    }
}
