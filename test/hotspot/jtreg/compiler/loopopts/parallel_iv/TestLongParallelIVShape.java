/*
 * Copyright (c) 2026 IBM and/or its affiliates. All rights reserved.
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

import compiler.lib.generators.Generators;
import compiler.lib.generators.RestrictableGenerator;
import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;

import java.util.Objects;

/**
 * @test
 * @bug 8342708
 * @key randomness
 * @summary Test parallel IV replacement in long counted loops
 * @library /test/lib /
 * @requires vm.compiler2.enabled
 * @run driver ${test.main.class}
 */
public class TestLongParallelIVShape {
    private static final RestrictableGenerator<Long> LONGS = Generators.G.longs();
    private static final RestrictableGenerator<Integer> INTS = Generators.G.ints();

    public static void main(String[] args) {
        // Turn off converting long loops to short loops so it doesn't interfere with our pipeline.
        TestFramework.runWithFlags("-XX:-ShortRunningLongLoop");
    }

    // A controlled test making sure a simple long counted loop is found by the test framework and
    // goes through the long counted loop pipeline: it is converted into a LongCountedLoop and split
    // into a nest, with the inner int CountedLoop and outer plain Loop remain in the final graph.
    @Test
    @IR(counts = { IRNode.LOOP, ">=1", IRNode.COUNTED_LOOP, ">=1" })
    static long testControlledSimpleLoop(long stop) {
        long a = 0;
        for (long i = 0; i < stop; i++) {
            a += i; // cannot be extracted to multiplications
        }

        return a;
    }

    @Run(test = "testControlledSimpleLoop")
    private static void runTestControlledSimpleLoop() {
        long s = LONGS.restricted(0L, 9_999L).next();
        Asserts.assertEQ(s * (s - 1) / 2, testControlledSimpleLoop(s));
    }

    // Long parallel IV with constant int stride in a long counted loop. Note that this test case
    // will pass even without enabling replace_parallel_iv() for long loops: for int-sized strides,
    // a loop nest is created with inner int loop. Existing replace_parallel_iv() for int loops
    // fires, then empty loop removal eliminates the inner loop. This is already handled by the old
    // loop opt pipeline.
    //
    // The final IR shape is the same with or without having replace_parallel_iv() for long loops.
    // There more cases below that demonstrates improvement over the old pipeline.
    @Test
    @IR(failOn = { IRNode.COUNTED_LOOP, IRNode.LONG_COUNTED_LOOP })
    static long testLongLoopWithLongIV(long stop) {
        long a = 0;
        for (long i = 0; i < stop; i++) {
            a += 42;
        }
        return a;
    }

    @Run(test = "testLongLoopWithLongIV")
    private static void runTestLongLoopWithLongIV() {
        long s = LONGS.restricted(0L, 9_999L).next();
        Asserts.assertEQ(42L * s, testLongLoopWithLongIV(s));
    }

    // Int parallel IV in a long counted loop. Same pipeline as above.
    @Test
    @IR(failOn = { IRNode.COUNTED_LOOP, IRNode.LONG_COUNTED_LOOP })
    static int testLongLoopIntIV(long stop) {
        int a = 0;
        for (long i = 0; i < stop; i++) {
            a += 42;
        }
        return a;
    }

    @Run(test = "testLongLoopIntIV")
    private static void runTestLongLoopIntIV() {
        long s = LONGS.restricted(0L, 9_999L).next();
        Asserts.assertEQ((int)(42L * s), testLongLoopIntIV(s));
    }

    // Multiple parallel IVs. Both handled by the existing loop-nest pipeline.
    @Test
    @IR(failOn = { IRNode.COUNTED_LOOP, IRNode.LONG_COUNTED_LOOP })
    static long testLongLoopTwoIVs(long stop) {
        long a = 0;
        long b = 0;
        for (long i = 0; i < stop; i++) {
            a += 42;
            b += 7;
        }
        return a + b;
    }

    @Run(test = "testLongLoopTwoIVs")
    private static void runTestLongLoopTwoIVs() {
        long s = LONGS.restricted(0L, 9_999L).next();
        Asserts.assertEQ(49L * s, testLongLoopTwoIVs(s));
    }

    // Stride exceeds Integer.MAX_VALUE. Still handled by the existing loop-nest pipeline,
    // essentially long IV in CountedLoop (replace_parallel_iv() uses jlong for stride_con2).
    @Test
    @IR(failOn = { IRNode.COUNTED_LOOP, IRNode.LONG_COUNTED_LOOP })
    static long testLongLoopHugeStride(long stop) {
        long a = 0;
        for (long i = 0; i < stop; i++) {
            a += 3_000_000_000L;
        }
        return a;
    }

    @Run(test = "testLongLoopHugeStride")
    private static void runTestLongLoopHugeStride() {
        long s = LONGS.restricted(0L, 9_999L).next();
        Asserts.assertEQ(3_000_000_000L * s, testLongLoopHugeStride(s));
    }

    // Counting down: a negative primary stride with a positive stride2 exercises the sign handling
    // of the stride ratio.
    @Test
    @IR(failOn = { IRNode.COUNTED_LOOP, IRNode.LONG_COUNTED_LOOP })
    static long testLongLoopLongIVCountdown(long stop) {
        long a = 0;
        for (long i = stop; i > 0; i--) {
            a += 42;
        }
        return a;
    }

    @Run(test = "testLongLoopLongIVCountdown")
    private static void runTestLongLoopLongIVCountdown() {
        long s = LONGS.restricted(0L, 9_999L).next();
        Asserts.assertEQ(42L * s, testLongLoopLongIVCountdown(s));
    }

    // Non-unit primary stride: the parallel IV stride must be an exact multiple of the primary
    // stride for the ratio to be an integer.
    @Test
    @IR(failOn = { IRNode.COUNTED_LOOP, IRNode.LONG_COUNTED_LOOP })
    static long testLongLoopLongIVStrideTwo(long stop) {
        long a = 0;
        for (long i = 0; i < stop; i += 2) {
            a += 6;
        }
        return a;
    }

    @Run(test = "testLongLoopLongIVStrideTwo")
    private static void runTestLongLoopLongIVStrideTwo() {
        long s = LONGS.restricted(0L, 9_999L).next();
        Asserts.assertEQ(Math.ceilDiv(s, 2) * 6, testLongLoopLongIVStrideTwo(s));
    }

    // Non-zero initial value of the parallel IV: exercises the `init2 - init * ratio` part of the
    // replacement.
    @Test
    @IR(failOn = { IRNode.COUNTED_LOOP, IRNode.LONG_COUNTED_LOOP })
    static long testLongLoopLongIVNonZeroInit(long stop) {
        long a = 123;
        for (long i = 0; i < stop; i++) {
            a += 42;
        }
        return a;
    }

    @Run(test = "testLongLoopLongIVNonZeroInit")
    private static void runTestLongLoopLongIVNonZeroInit() {
        long s = LONGS.restricted(0L, 9_999L).next();
        Asserts.assertEQ(123 + 42L * s, testLongLoopLongIVNonZeroInit(s));
    }

    // Zero stride2: the ratio is zero (an exact division), and the parallel IV is constant after
    // the replacement.
    @Test
    @IR(failOn = { IRNode.COUNTED_LOOP, IRNode.LONG_COUNTED_LOOP })
    static long testLongLoopLongIVZeroStride2(long stop) {
        long a = 0;
        for (long i = 0; i < stop; i++) {
            a += 0;
        }
        return a;
    }

    @Run(test = "testLongLoopLongIVZeroStride2")
    private static void runTestLongLoopLongIVZeroStride2() {
        long s = LONGS.restricted(0L, 9_999L).next();
        Asserts.assertEQ(0L, testLongLoopLongIVZeroStride2(s));
    }

    // `stride2 * trip_count` overflows long: the replacement must preserve the wrap-around
    // semantics of the additions it replaces. Both sides of the assertion wrap identically in long
    // arithmetic.
    @Test
    @IR(failOn = { IRNode.COUNTED_LOOP, IRNode.LONG_COUNTED_LOOP })
    static long testLongLoopLongIVOverflow(long stop) {
        long a = 0;
        for (long i = 0; i < stop; i++) {
            a += Long.MAX_VALUE;
        }
        return a;
    }

    @Run(test = "testLongLoopLongIVOverflow")
    private static void runTestLongLoopLongIVOverflow() {
        long s = LONGS.restricted(2L, 99L).next();
        Asserts.assertEQ(s * Long.MAX_VALUE, testLongLoopLongIVOverflow(s));
    }

    // Parallel IV used inside the loop body: the replacement must be correct on every iteration,
    // not only at loop exit.
    @Test
    @IR(counts = { IRNode.COUNTED_LOOP, ">=1" })
    static long testLongLoopIVUsedInBody(long stop) {
        long sum = 0;
        long a = 0;
        for (long i = 0; i < stop; i++) {
            a += 3;
            sum += a % 7;
        }
        return sum;
    }

    @Run(test = "testLongLoopIVUsedInBody")
    private static void runTestLongLoopIVUsedInBody() {
        long s = LONGS.restricted(0L, 9_999L).next();
        long expected = 0;
        long a = 0;
        for (long i = 0; i < s; i++) {
            a += 3;
            expected += a % 7;
        }
        Asserts.assertEQ(expected, testLongLoopIVUsedInBody(s));
    }

    // stride2 is Long.MIN_VALUE with primary stride -1: the ratio cannot be
    // represented (`Long.MIN_VALUE / -1` overflows), so the transformation must not happen. The
    // loop remains as a loop nest whose inner int CountedLoop still updates the IV every iteration.
    @Test
    @IR(counts = { IRNode.COUNTED_LOOP, ">=1" })
    static long testLongLoopMinLongStride2(long stop) {
        long a = 0;
        for (long i = stop; i > 0; i--) {
            a += Long.MIN_VALUE;
        }
        return a;
    }

    @Run(test = "testLongLoopMinLongStride2")
    private static void runTestLongLoopMinLongStride2() {
        long s = LONGS.restricted(0L, 9_999L).next();
        Asserts.assertEQ(s * Long.MIN_VALUE, testLongLoopMinLongStride2(s));
    }

    // Same bail-out for an int-typed parallel IV: `Integer.MIN_VALUE / -1` cannot be represented as
    // an int ratio.
    @Test
    @IR(counts = { IRNode.COUNTED_LOOP, ">=1" })
    static int testLongLoopIntIVMinIntStride2(long stop) {
        int a = 0;
        for (long i = stop; i > 0; i--) {
            a += Integer.MIN_VALUE;
        }
        return a;
    }

    @Run(test = "testLongLoopIntIVMinIntStride2")
    private static void runTestLongLoopIntIVMinIntStride2() {
        long s = LONGS.restricted(0L, 9_999L).next();
        Asserts.assertEQ((int)(s * Integer.MIN_VALUE), testLongLoopIntIVMinIntStride2(s));
    }

    // Two parallel IVs, only one of them has a stride that is an exact multiple of the primary
    // stride: only 'a' can be replaced, and the loop must remain because 'b' still needs updating
    // every iteration.
    @Test
    @IR(counts = { IRNode.COUNTED_LOOP, ">=1" })
    static long testLongLoopTwoIVsOneReplaceable(long stop) {
        long a = 0;
        long b = 0;
        for (long i = 0; i < stop; i += 2) {
            a += 4;
            b += 3;
        }
        return a + b;
    }

    @Run(test = "testLongLoopTwoIVsOneReplaceable")
    private static void runTestLongLoopTwoIVsOneReplaceable() {
        long s = LONGS.restricted(0L, 9_999L).next();
        long trips = Math.ceilDiv(s, 2);
        Asserts.assertEQ(trips * 4 + trips * 3, testLongLoopTwoIVsOneReplaceable(s));
    }

    // The primary stride is not int-representable, so the old pipeline with create_loop_nest()
    // cannot split the this loop into a int counted counted loop that replace_parallel_iv() for int
    // can handle.
    //
    // The new pipe line enables replace_parallel_iv() directly for long counted loops, replacing
    // `a` as `i * 1_000_000_003 + 0`. The ratio is large and odd, so that the multiplication
    // survives strength reduction (to bit-shifts and adds) and appears as MulL in the final IR. We
    // match MulL to verify IV replacement is happening.
    //
    // The empty LongCountedLoop is not removed and is out-of-scope of this PR. Therefore we still
    // expect an empty LongCountedLoop.
    @Test
    @IR(counts = { IRNode.LONG_COUNTED_LOOP, ">=1", IRNode.MUL_L, ">=1" })
    @IR(failOn = { IRNode.COUNTED_LOOP })
    static long testLongLoopHugePrimaryStride(long stop) {
        long a = 0;
        for (long i = 0; i < stop; i += 4_000_000_000L) {
            a += 4_000_000_012_000_000_000L;
        }
        return a;
    }

    @Run(test = "testLongLoopHugePrimaryStride")
    private static void runTestLongLoopHugePrimaryStride() {
        long s = LONGS.restricted(0L, 9_999L).next();
        Asserts.assertEQ(Math.ceilDiv(s, 4_000_000_000L) * 4_000_000_012_000_000_000L,
                testLongLoopHugePrimaryStride(s));
        // Exercise more than a single iteration.
        long s2 = 4_000_000_123L;
        Asserts.assertEQ(Math.ceilDiv(s2, 4_000_000_000L) * 4_000_000_012_000_000_000L,
                testLongLoopHugePrimaryStride(s2));
    }

    // Same as above, counting down: the stride ratio is negative.
    @Test
    @IR(counts = { IRNode.MUL_L, ">=1" })
    static long testLongLoopHugePrimaryStrideCountdown(long stop) {
        long a = 0;
        for (long i = stop; i > 0; i -= 4_000_000_000L) {
            a += 4_000_000_012_000_000_000L;
        }
        return a;
    }

    @Run(test = "testLongLoopHugePrimaryStrideCountdown")
    private static void runTestLongLoopHugePrimaryStrideCountdown() {
        long s = LONGS.restricted(0L, 9_999L).next();
        Asserts.assertEQ(Math.ceilDiv(s, 4_000_000_000L) * 4_000_000_012_000_000_000L,
                testLongLoopHugePrimaryStrideCountdown(s));
        long s2 = 4_000_000_123L;
        Asserts.assertEQ(Math.ceilDiv(s2, 4_000_000_000L) * 4_000_000_012_000_000_000L,
                testLongLoopHugePrimaryStrideCountdown(s2));
    }

    // Non-exact ratio (i.e., stride2 is not a multiple of the primary stride) with a primary stride
    // that is not in-representable: the replacement must not happen, and since no loop nest can be
    // created either, the LongCountedLoop itself remains.
    @Test
    @IR(counts = { IRNode.LONG_COUNTED_LOOP, ">=1" })
    static long testLongLoopHugePrimaryStrideInexact(long stop) {
        long a = 0;
        for (long i = 0; i < stop; i += 4_000_000_000L) {
            a += 4_000_000_002L;
        }
        return a;
    }

    @Run(test = "testLongLoopHugePrimaryStrideInexact")
    private static void runTestLongLoopHugePrimaryStrideInexact() {
        long s = LONGS.restricted(0L, 9_999L).next();
        Asserts.assertEQ(Math.ceilDiv(s, 4_000_000_000L) * 4_000_000_002L,
                testLongLoopHugePrimaryStrideInexact(s));
    }

    static int[] array;
    static {
        array = new int[4096];
        for (int i = 0; i < array.length; i++) {
            array[i] = i;
        }
    }

    // Parallel IV used in a range check (via Objects.checkIndex). The long replace_parallel_iv()
    // runs before loop nest creation, so `j` is rewritten as `i * 3` early, and no range check
    // remains in the final code. The array is initialized with its indices so that the assertion
    // below also verifies that the replacement reads the right elements, not just that the accesses
    // stay in bounds.
    @Test
    @IR(failOn = { IRNode.RANGE_CHECK })
    static long testLongLoopParallelIVRangeCheck(long stop) {
        long sum = 0;
        long j = 0;
        for (long i = 0; i < stop; i++) {
            sum += array[Objects.checkIndex((int) j, array.length)];
            j += 3;
        }
        return sum;
    }

    @Run(test = "testLongLoopParallelIVRangeCheck")
    private static void runTestLongLoopParallelIVRangeCheck() {
        int s = INTS.restricted(0, 1_365).next();
        Asserts.assertEQ(3L * s * (s - 1) / 2, testLongLoopParallelIVRangeCheck(s));
    }
}
