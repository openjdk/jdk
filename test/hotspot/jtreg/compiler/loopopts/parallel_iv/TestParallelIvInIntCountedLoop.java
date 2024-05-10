/*
 * Copyright (c) 2024 Red Hat and/or its affiliates. All rights reserved.
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

package compiler.loopopts.parallel_iv;

import compiler.lib.ir_framework.Argument;
import compiler.lib.ir_framework.Arguments;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.TestFramework;

import java.util.function.Function;

/**
 * @test
 * @bug 8328528
 * @summary test the long typed parallel iv replacing transformation for int counted loop
 * @library /test/lib /
 * @run main compiler.c2.irTests.TestCountedLoopIV
 */
public class TestParallelIvInIntCountedLoop {
    public static void main(String[] args) {
        TestFramework.run();
        testCorrectness();
    }

    /*
     * The IR framework can only test against static code, and the transformation relies on strides being constants to
     * perform constant propagation. Therefore, we have no choice but repeating the same test case multiple times with
     * different numbers.
     */
    @Test
    @Arguments(values = {Argument.DEFAULT})
    @IR(failOn = {IRNode.LOOP, IRNode.COUNTED_LOOP})
    private static int testIntCountedLoopWithIntIV(int stop) {
        int a = 0;
        for (int i = 0; i < stop; i++) {
            a += 1;
        }

        return a;
    }

    @Test
    @Arguments(values = {Argument.DEFAULT})
    @IR(failOn = {IRNode.LOOP, IRNode.COUNTED_LOOP})
    private static int testIntCountedLoopWithIntIVZero(int stop) {
        int a = 0;
        for (int i = 0; i < stop; i++) {
            a += 0;
        }

        return a;
    }

    @Test
    @Arguments(values = {Argument.DEFAULT})
    @IR(failOn = {IRNode.LOOP, IRNode.COUNTED_LOOP})
    private static int testIntCountedLoopWithIntIVMax(int stop) {
        int a = 0;
        for (int i = 0; i < stop; i++) {
            a += Integer.MAX_VALUE;
        }

        return a;
    }

    @Test
    @Arguments(values = {Argument.DEFAULT})
    @IR(failOn = {IRNode.LOOP, IRNode.COUNTED_LOOP})
    private static int testIntCountedLoopWithIntIVMaxMinusOne(int stop) {
        int a = 0;
        for (int i = 0; i < stop; i++) {
            a += Integer.MAX_VALUE - 1;
        }

        return a;
    }

    @Test
    @Arguments(values = {Argument.DEFAULT})
    @IR(failOn = {IRNode.LOOP, IRNode.COUNTED_LOOP})
    private static int testIntCountedLoopWithIntIVMaxPlusOne(int stop) {
        int a = 0;
        for (int i = 0; i < stop; i++) {
            a += Integer.MAX_VALUE + 1;
        }

        return a;
    }

    @Test
    @Arguments(values = {Argument.DEFAULT})
    @IR(failOn = {IRNode.LOOP, IRNode.COUNTED_LOOP})
    private static int testIntCountedLoopWithIntIVWithStrideTwo(int stop) {
        int a = 0;
        for (int i = 0; i < stop; i += 2) {
            a += 2; // this stride2 constant must be multiple of the first stride (i += ...) for optimization
        }

        return a;
    }

    @Test
    @Arguments(values = {Argument.DEFAULT})
    @IR(failOn = {IRNode.LOOP, IRNode.COUNTED_LOOP})
    private static int testIntCountedLoopWithIntIVWithStrideMinusOne(int stop) {
        int a = 0;
        for (int i = stop; i > 0; i += -1) {
            a += 1;
        }

        return a;
    }

    @Test
    @Arguments(values = {Argument.DEFAULT})
    @IR(failOn = {IRNode.LOOP, IRNode.COUNTED_LOOP})
    private static long testIntCountedLoopWithLongIV(int stop) {
        long a = 0;
        for (int i = 0; i < stop; i++) {
            a += 1;
        }

        return a;
    }

    @Test
    @Arguments(values = {Argument.DEFAULT})
    @IR(failOn = {IRNode.LOOP, IRNode.COUNTED_LOOP})
    private static long testIntCountedLoopWithLongIVZero(int stop) {
        long a = 0;
        for (int i = 0; i < stop; i++) {
            a += 0;
        }

        return a;
    }

    @Test
    @Arguments(values = {Argument.DEFAULT})
    @IR(failOn = {IRNode.LOOP, IRNode.COUNTED_LOOP})
    private static long testIntCountedLoopWithLongIVMax(int stop) {
        long a = 0;
        for (int i = 0; i < stop; i++) {
            a += Long.MAX_VALUE;
        }

        return a;
    }

    @Test
    @Arguments(values = {Argument.DEFAULT})
    @IR(failOn = {IRNode.LOOP, IRNode.COUNTED_LOOP})
    private static long testIntCountedLoopWithLongIVMaxMinusOne(int stop) {
        long a = 0;
        for (int i = 0; i < stop; i++) {
            a += Long.MAX_VALUE - 1;
        }

        return a;
    }

    @Test
    @Arguments(values = {Argument.DEFAULT})
    @IR(failOn = {IRNode.LOOP, IRNode.COUNTED_LOOP})
    private static long testIntCountedLoopWithLongIVMaxPlusOne(int stop) {
        long a = 0;
        for (int i = 0; i < stop; i++) {
            a += Long.MAX_VALUE + 1;
        }

        return a;
    }

    @Test
    @Arguments(values = {Argument.DEFAULT})
    @IR(failOn = {IRNode.LOOP, IRNode.COUNTED_LOOP})
    private static long testIntCountedLoopWithLongIVWithStrideTwo(int stop) {
        long a = 0;
        for (int i = 0; i < stop; i += 2) {
            a += 1;
        }

        return a;
    }

    @Test
    @Arguments(values = {Argument.DEFAULT})
    @IR(failOn = {IRNode.LOOP, IRNode.COUNTED_LOOP})
    private static long testIntCountedLoopWithLongIVWithStrideMinusOne(int stop) {
        long a = 0;
        for (int i = stop; i > 0; i += -1) {
            a += 1;
        }

        return a;
    }

    private static <T extends Number> void test(Function<Integer, T> function, int iteration, T expected) {
        T result = function.apply(iteration);
        if (!result.equals(expected)) {
            throw new RuntimeException("Bad result for IV with stop = " + iteration + ", expected " + expected
                    + ", got " + result);
        }
    }

    private static void testCorrectness() {
        int[] iterations = {0, 1, 2, 42, 100};

        for (int i : iterations) {
            test(TestParallelIvInIntCountedLoop::testIntCountedLoopWithIntIV, i, i);
            test(TestParallelIvInIntCountedLoop::testIntCountedLoopWithIntIVZero, i, 0);
            test(TestParallelIvInIntCountedLoop::testIntCountedLoopWithIntIVMax, i, i * Integer.MAX_VALUE);
            test(TestParallelIvInIntCountedLoop::testIntCountedLoopWithIntIVMaxMinusOne, i, i * (Integer.MAX_VALUE - 1));
            test(TestParallelIvInIntCountedLoop::testIntCountedLoopWithIntIVMaxPlusOne, i, i * (Integer.MAX_VALUE + 1));
            test(TestParallelIvInIntCountedLoop::testIntCountedLoopWithIntIVWithStrideTwo, i, i);
            test(TestParallelIvInIntCountedLoop::testIntCountedLoopWithIntIVWithStrideMinusOne, i, i);

            test(TestParallelIvInIntCountedLoop::testIntCountedLoopWithLongIV, i, (long) i);
            test(TestParallelIvInIntCountedLoop::testIntCountedLoopWithLongIVZero, i, (long) 0);
            test(TestParallelIvInIntCountedLoop::testIntCountedLoopWithLongIVMax, i, (long) i * Long.MAX_VALUE);
            test(TestParallelIvInIntCountedLoop::testIntCountedLoopWithLongIVMaxMinusOne, i, (long) i * (Long.MAX_VALUE - 1));
            test(TestParallelIvInIntCountedLoop::testIntCountedLoopWithLongIVMaxPlusOne, i, (long) i * (Long.MAX_VALUE + 1));
            test(TestParallelIvInIntCountedLoop::testIntCountedLoopWithLongIVWithStrideTwo, i, (long) i);
            test(TestParallelIvInIntCountedLoop::testIntCountedLoopWithLongIVWithStrideMinusOne, i, (long) i);
        }
    }
}