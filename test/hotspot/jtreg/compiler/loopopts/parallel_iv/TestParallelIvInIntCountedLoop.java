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

import compiler.lib.ir_framework.*;

import java.util.Random;
import java.util.function.Function;

import jdk.test.lib.Asserts;

/**
 * @test
 * @bug 8328528
 * @summary test the long typed parallel iv replacing transformation for int counted loop
 * @library /test/lib /
 * @run driver compiler.loopopts.parallel_iv.TestParallelIvInIntCountedLoop
 */
public class TestParallelIvInIntCountedLoop {
    private static final int stride;
    private static final int stride2;

    static {
        Random rng = new Random();

        // stride2 must be a multiple of stride and must not overflow for the optimization to work
        stride = rng.nextInt(1, Integer.MAX_VALUE / 16);
        stride2 = stride * rng.nextInt(1, 16);
    }

    public static void main(String[] args) {
        TestFramework.run();
        testCorrectness();
    }

    /*
     * The IR framework can only test against static code, and the transformation relies on strides being constants to
     * perform constant propagation. Therefore, we have no choice but repeating the same test case multiple times with
     * different numbers.
     *
     * For good measures, a randomly initialized static final stride and stride2 is also tested.
     */

    // A controlled test making sure a simple non-counted loop can be found by the test framework.
    @Test
    @Arguments(values = {Argument.NUMBER_42}) // otherwise a large number may take too long
    @IR(counts = {IRNode.COUNTED_LOOP, ">=1"})
    private static int testControlledSimpleLoop(int stop) {
        int a = 0;
        for (int i = 0; i < stop; i++) {
            a += i; // cannot be extracted to multiplications
        }

        return a;
    }

    @Test
    @Arguments(values = {Argument.RANDOM_ONCE})
    @IR(failOn = {IRNode.COUNTED_LOOP})
    private static int testIntCountedLoopWithIntIV(int stop) {
        int a = 0;
        for (int i = 0; i < stop; i++) {
            a += 1;
        }

        return a;
    }

    @Test
    @Arguments(values = {Argument.RANDOM_ONCE})
    @IR(failOn = {IRNode.COUNTED_LOOP})
    private static int testIntCountedLoopWithIntIVLeq(int stop) {
        int a = 0;
        for (int i = 0; i <= stop; i++) {
            a += 1;
        }

        return a;
    }

    @Test
    @Arguments(values = {Argument.RANDOM_ONCE})
    @IR(failOn = {IRNode.COUNTED_LOOP})
    private static int testIntCountedLoopWithIntIVZero(int stop) {
        int a = 0;
        for (int i = 0; i < stop; i++) {
            a += 0;
        }

        return a;
    }

    @Test
    @Arguments(values = {Argument.RANDOM_ONCE})
    @IR(failOn = {IRNode.COUNTED_LOOP})
    private static int testIntCountedLoopWithIntIVMax(int stop) {
        int a = 0;
        for (int i = 0; i < stop; i++) {
            a += Integer.MAX_VALUE;
        }

        return a;
    }

    @Test
    @Arguments(values = {Argument.RANDOM_ONCE})
    @IR(failOn = {IRNode.COUNTED_LOOP})
    private static int testIntCountedLoopWithIntIVMaxMinusOne(int stop) {
        int a = 0;
        for (int i = 0; i < stop; i++) {
            a += Integer.MAX_VALUE - 1;
        }

        return a;
    }

    @Test
    @Arguments(values = {Argument.RANDOM_ONCE})
    @IR(failOn = {IRNode.COUNTED_LOOP})
    private static int testIntCountedLoopWithIntIVMaxPlusOne(int stop) {
        int a = 0;
        for (int i = 0; i < stop; i++) {
            a += Integer.MAX_VALUE + 1;
        }

        return a;
    }

    @Test
    @Arguments(values = {Argument.RANDOM_ONCE})
    @IR(failOn = {IRNode.COUNTED_LOOP})
    private static int testIntCountedLoopWithIntIVWithStrideTwo(int stop) {
        int a = 0;
        for (int i = 0; i < stop; i += 2) {
            a += 2; // this stride2 constant must be a multiple of the first stride (i += ...) for optimization
        }

        return a;
    }

    @Test
    @Arguments(values = {Argument.RANDOM_ONCE})
    @IR(failOn = {IRNode.COUNTED_LOOP})
    private static int testIntCountedLoopWithIntIVWithStrideMinusOne(int stop) {
        int a = 0;
        for (int i = stop; i > 0; i += -1) {
            a += 1;
        }

        return a;
    }

    @Test
    @Arguments(values = {Argument.RANDOM_ONCE})
    @IR(failOn = {IRNode.COUNTED_LOOP})
    private static int testIntCountedLoopWithIntIVWithRandomStrides(int stop) {
        int a = 0;
        for (int i = 0; i < stop; i += stride) {
            a += stride2;
        }

        return a;
    }

    @Test
    @Arguments(values = {Argument.RANDOM_ONCE, Argument.RANDOM_ONCE, Argument.RANDOM_ONCE})
    @IR(failOn = {IRNode.COUNTED_LOOP})
    private static int testIntCountedLoopWithIntIVWithRandomStridesAndInits(int init, int init2, int stop) {
        int a = init;
        for (int i = init2; i < stop; i += stride) {
            a += stride2;
        }

        return a;
    }

    @Test
    @Arguments(values = {Argument.RANDOM_ONCE})
    @IR(failOn = {IRNode.COUNTED_LOOP})
    private static long testIntCountedLoopWithLongIV(int stop) {
        long a = 0;
        for (int i = 0; i < stop; i++) {
            a += 1;
        }

        return a;
    }

    @Test
    @Arguments(values = {Argument.RANDOM_ONCE})
    @IR(failOn = {IRNode.COUNTED_LOOP})
    private static long testIntCountedLoopWithLongIVLeq(int stop) {
        long a = 0;
        for (int i = 0; i <= stop; i++) {
            a += 1;
        }

        return a;
    }

    @Test
    @Arguments(values = {Argument.RANDOM_ONCE})
    @IR(failOn = {IRNode.COUNTED_LOOP})
    private static long testIntCountedLoopWithLongIVZero(int stop) {
        long a = 0;
        for (int i = 0; i < stop; i++) {
            a += 0;
        }

        return a;
    }

    @Test
    @Arguments(values = {Argument.RANDOM_ONCE})
    @IR(failOn = {IRNode.COUNTED_LOOP})
    private static long testIntCountedLoopWithLongIVMax(int stop) {
        long a = 0;
        for (int i = 0; i < stop; i++) {
            a += Long.MAX_VALUE;
        }

        return a;
    }

    @Test
    @Arguments(values = {Argument.RANDOM_ONCE})
    @IR(failOn = {IRNode.COUNTED_LOOP})
    private static long testIntCountedLoopWithLongIVMaxMinusOne(int stop) {
        long a = 0;
        for (int i = 0; i < stop; i++) {
            a += Long.MAX_VALUE - 1;
        }

        return a;
    }

    @Test
    @Arguments(values = {Argument.RANDOM_ONCE})
    @IR(failOn = {IRNode.COUNTED_LOOP})
    private static long testIntCountedLoopWithLongIVMaxPlusOne(int stop) {
        long a = 0;
        for (int i = 0; i < stop; i++) {
            a += Long.MAX_VALUE + 1;
        }

        return a;
    }

    @Test
    @Arguments(values = {Argument.RANDOM_ONCE})
    @IR(failOn = {IRNode.COUNTED_LOOP})
    private static long testIntCountedLoopWithLongIVWithStrideTwo(int stop) {
        long a = 0;
        for (int i = 0; i < stop; i += 2) {
            a += 2;
        }

        return a;
    }

    @Test
    @Arguments(values = {Argument.RANDOM_ONCE})
    @IR(failOn = {IRNode.COUNTED_LOOP})
    private static long testIntCountedLoopWithLongIVWithStrideMinusOne(int stop) {
        long a = 0;
        for (int i = stop; i > 0; i += -1) {
            a += 1;
        }

        return a;
    }

    @Test
    @Arguments(values = {Argument.RANDOM_ONCE})
    @IR(failOn = {IRNode.COUNTED_LOOP})
    private static long testIntCountedLoopWithLongIVWithRandomStrides(int stop) {
        long a = 0;
        for (int i = 0; i < stop; i += stride) {
            a += (long) stride2;
        }

        return a;
    }

    @Test
    @Arguments(values = {Argument.RANDOM_ONCE, Argument.RANDOM_ONCE, Argument.RANDOM_ONCE})
    @IR(failOn = {IRNode.COUNTED_LOOP})
    private static long testIntCountedLoopWithLongIVWithRandomStridesAndInits(long init, int init2, int stop) {
        long a = init;
        for (int i = init2; i < stop; i += stride) {
            a += stride2;
        }

        return a;
    }

    private static void testCorrectness() {
        Random rng = new Random();

        int[] iterations = {0, 1, 2, 42, 100, rng.nextInt(0, Integer.MAX_VALUE - stride)};

        for (int i : iterations) {
            Asserts.assertEQ(i, testIntCountedLoopWithIntIV(i));
            Asserts.assertEQ(i + 1, testIntCountedLoopWithIntIVLeq(i));
            Asserts.assertEQ(0, testIntCountedLoopWithIntIVZero(i));
            Asserts.assertEQ(i * Integer.MAX_VALUE, testIntCountedLoopWithIntIVMax(i));
            Asserts.assertEQ(i * (Integer.MAX_VALUE - 1), testIntCountedLoopWithIntIVMaxMinusOne(i));
            Asserts.assertEQ(i * (Integer.MAX_VALUE + 1), testIntCountedLoopWithIntIVMaxPlusOne(i));
            Asserts.assertEQ(Math.ceilDiv(i, 2) * 2, testIntCountedLoopWithIntIVWithStrideTwo(i));
            Asserts.assertEQ(i, testIntCountedLoopWithIntIVWithStrideMinusOne(i));

            Asserts.assertEQ((long) i, testIntCountedLoopWithLongIV(i));
            Asserts.assertEQ((long) i + 1l, testIntCountedLoopWithLongIVLeq(i));
            Asserts.assertEQ((long) 0, testIntCountedLoopWithLongIVZero(i));
            Asserts.assertEQ((long) i * Long.MAX_VALUE, testIntCountedLoopWithLongIVMax(i));
            Asserts.assertEQ((long) i * (Long.MAX_VALUE - 1l), testIntCountedLoopWithLongIVMaxMinusOne(i));
            Asserts.assertEQ((long) i * (Long.MAX_VALUE + 1l), testIntCountedLoopWithLongIVMaxPlusOne(i));
            Asserts.assertEQ(Math.ceilDiv(i, 2l) * 2l, testIntCountedLoopWithLongIVWithStrideTwo(i));
            Asserts.assertEQ((long) i, testIntCountedLoopWithLongIVWithStrideMinusOne(i));

            // test with random stride and stride2
            Asserts.assertEQ(Math.ceilDiv(i, stride) * stride2, testIntCountedLoopWithIntIVWithRandomStrides(i));
            Asserts.assertEQ(Math.ceilDiv(i, (long) stride) * (long) stride2, testIntCountedLoopWithLongIVWithRandomStrides(i));

            // also test with random init and init2
            int init1 = rng.nextInt();
            int init2 = rng.nextInt(Integer.MIN_VALUE + i + 1, i);
            long init1L = rng.nextLong(Long.MIN_VALUE + i + 1, i);

            Asserts.assertEQ(Math.ceilDiv((i - init2), stride) * stride2 + init1,
                    testIntCountedLoopWithIntIVWithRandomStridesAndInits(init1, init2, i));

            Asserts.assertEQ(Math.ceilDiv(((long) i - init2), (long) stride) * (long) stride2 + init1L,
                    testIntCountedLoopWithLongIVWithRandomStridesAndInits(init1L, init2, i));
        }
    }
}
