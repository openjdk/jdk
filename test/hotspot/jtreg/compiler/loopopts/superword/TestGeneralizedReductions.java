/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, Red Hat Inc. All rights reserved.
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
 * @bug 8283187 8287087
 * @summary Test reduction vectorizations that are enabled by performing SLP
 *          reduction analysis on unrolled loops.
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestGeneralizedReductions
 */

package compiler.loopopts.superword;

import compiler.lib.ir_framework.*;

public class TestGeneralizedReductions {

    private static int acc = 0;

    public static void main(String[] args) throws Exception {
        TestFramework.run();
    }

    @Run(test = {"testReductionOnGlobalAccumulator",
                 "testReductionOnPartiallyUnrolledLoop",
                 "testMapReductionOnGlobalAccumulator"})
    void run() {
        long[] array = new long[100];
        long result;
        initArray(array);
        result = testReductionOnGlobalAccumulator(array);
        if (result != 4950) {
            throw new RuntimeException("unexpected result");
        }
        initArray(array);
        result = testReductionOnPartiallyUnrolledLoop(array);
        if (result != 4950) {
            throw new RuntimeException("unexpected result");
        }
        initArray(array);
        result = testMapReductionOnGlobalAccumulator(array);
        if (result != 316) {
            throw new RuntimeException("unexpected result");
        }
    }

    private static void initArray(long[] array) {
        for (int i = 0; i < array.length; i++) {
            array[i] = i;
        }
    }

    @Test
    @IR(applyIfCPUFeature = {"sse4.1", "true"},
        applyIfAnd = {"SuperWordReductions", "true", "LoopMaxUnroll", ">= 8"},
        counts = {IRNode.ADD_REDUCTION_VI, ">= 1"})
    private static long testReductionOnGlobalAccumulator(long[] array) {
        acc = 0;
        for (int i = 0; i < array.length; i++) {
            acc += array[i];
        }
        return acc;
    }

    @Test
    @IR(applyIfCPUFeature = {"sse4.1", "true"},
        applyIfAnd = {"SuperWordReductions", "true", "LoopMaxUnroll", ">= 8"},
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
    @IR(applyIfCPUFeature = {"sse4.1", "true"},
        applyIfAnd = {"SuperWordReductions", "true",
                      "UsePopCountInstruction", "true",
                      "LoopMaxUnroll", ">= 8"},
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
