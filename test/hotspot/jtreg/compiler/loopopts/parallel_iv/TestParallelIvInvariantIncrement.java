/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
import compiler.lib.generators.RestrictableGenerator;
import jdk.test.lib.Asserts;

import static compiler.lib.generators.Generators.G;

/**
 * @test
 * @bug 8346177
 * @key randomness
 * @summary test parallel IV replacement with loop-invariant increments
 * @library /test/lib /
 * @requires vm.compiler2.enabled
 * @run driver ${test.main.class}
 *
 * Test loops run at most ~20000 iterations, except the two tests whose trip
 * count is itself under test: countDownMaxRange and longStride1FullRange.
 */
public class TestParallelIvInvariantIncrement {

    private static final RestrictableGenerator<Integer> TRIP_COUNT = G.uniformInts(0, 10000);
    private static final RestrictableGenerator<Integer> NEAR_MAX = G.uniformInts(Integer.MAX_VALUE - 10000, Integer.MAX_VALUE - 10);

    private static final int LOOP_STRIDE_RAND = G.ints().restricted(1, Integer.MAX_VALUE).next();
    private static final int LOOP_STRIDE_RAND_MAX_STOP
            = (int) Math.min(Integer.MAX_VALUE - (long) LOOP_STRIDE_RAND, 1000L * LOOP_STRIDE_RAND);

    private static final int WIDE_STRIDE = 1 << 20;
    private static final int WIDE_ITERATIONS = 4094;
    private static final int WIDE_START = Integer.MIN_VALUE;
    private static final int WIDE_STOP = (int) (Integer.MIN_VALUE + (long) WIDE_ITERATIONS * WIDE_STRIDE);
    private static final int WIDE_DOWN_START = Integer.MAX_VALUE;
    private static final int WIDE_DOWN_STOP = (int) (Integer.MAX_VALUE - (long) WIDE_ITERATIONS * WIDE_STRIDE);

    public static void main(String[] args) {
        TestFramework framework = new TestFramework();
        framework.setDefaultWarmup(0).addFlags("-XX:LoopUnrollLimit=0").start();
    }

    @Test
    @IR(failOn = { IRNode.MUL_I }, phase = CompilePhase.BEFORE_CLOOPS)
    @IR(failOn = { IRNode.COUNTED_LOOP, IRNode.LOOP })
    @IR(counts = { IRNode.MUL_I, "=1" })
    private static int intAdd(int stop, int inc) {
        int a = 0;
        for (int i = 0; i < stop; i++) {
            a += inc;
        }
        return a;
    }

    @Run(test = "intAdd")
    private static void runIntAdd() {
        int s = TRIP_COUNT.next();
        int inc = G.ints().next();
        Asserts.assertEQ(s * inc, intAdd(s, inc));
        Asserts.assertEQ(s * Integer.MAX_VALUE, intAdd(s, Integer.MAX_VALUE));
        Asserts.assertEQ(s * Integer.MIN_VALUE, intAdd(s, Integer.MIN_VALUE));
        Asserts.assertEQ(0, intAdd(s, 0));
        Asserts.assertEQ(0, intAdd(0, inc));
        Asserts.assertEQ(inc, intAdd(1, inc));
        Asserts.assertEQ(Integer.MIN_VALUE, intAdd(1, Integer.MIN_VALUE));
    }

    @Test
    @IR(failOn = { IRNode.MUL_I }, phase = CompilePhase.BEFORE_CLOOPS)
    @IR(failOn = { IRNode.COUNTED_LOOP, IRNode.LOOP })
    @IR(counts = { IRNode.MUL_I, "=1" })
    private static int intSub(int stop, int inc) {
        int a = 0;
        for (int i = 0; i < stop; i++) {
            a -= inc;
        }
        return a;
    }

    @Run(test = "intSub")
    private static void runIntSub() {
        int s = TRIP_COUNT.next();
        int inc = G.ints().next();
        Asserts.assertEQ(-s * inc, intSub(s, inc));
    }

    @Test
    @IR(failOn = { IRNode.MUL_L }, phase = CompilePhase.BEFORE_CLOOPS)
    @IR(failOn = { IRNode.COUNTED_LOOP, IRNode.LOOP })
    @IR(counts = { IRNode.MUL_L, "=1" })
    @IR(counts = { IRNode.AND_L, "=1" })
    private static long longAdd(int stop, long inc) {
        long a = 0;
        for (int i = 0; i < stop; i++) {
            a += inc;
        }
        return a;
    }

    @Run(test = "longAdd")
    private static void runLongAdd() {
        int s = TRIP_COUNT.next();
        long inc = G.longs().next();
        Asserts.assertEQ((long) s * inc, longAdd(s, inc));
    }

    @Test
    @IR(failOn = { IRNode.MUL_L }, phase = CompilePhase.BEFORE_CLOOPS)
    @IR(failOn = { IRNode.COUNTED_LOOP, IRNode.LOOP })
    @IR(counts = { IRNode.MUL_L, "=1" })
    private static long longSub(int stop, long inc) {
        long a = 0;
        for (int i = 0; i < stop; i++) {
            a -= inc;
        }
        return a;
    }

    @Run(test = "longSub")
    private static void runLongSub() {
        int s = TRIP_COUNT.next();
        long inc = G.longs().next();
        Asserts.assertEQ(-(long) s * inc, longSub(s, inc));
    }

    @Test
    @IR(failOn = { IRNode.COUNTED_LOOP, IRNode.LOOP })
    @IR(counts = { IRNode.MUL_I, "=1" })
    @IR(counts = { IRNode.URSHIFT_I, "=1" })
    private static int stride2(int stop, int inc) {
        int a = 0;
        for (int i = 0; i < stop; i += 2) {
            a += inc;
        }
        return a;
    }

    @Run(test = "stride2")
    private static void runStride2() {
        int s = TRIP_COUNT.next();
        int inc = G.ints().next();
        Asserts.assertEQ(Math.ceilDiv(s, 2) * inc, stride2(s, inc));
    }

    @Test
    @IR(failOn = { IRNode.COUNTED_LOOP, IRNode.LOOP })
    @IR(counts = { IRNode.MUL_I, "=1" })
    private static int countDown(int stop, int inc) {
        int a = 0;
        for (int i = stop; i > 0; i--) {
            a += inc;
        }
        return a;
    }

    @Run(test = "countDown")
    private static void runCountDown() {
        int s = TRIP_COUNT.next();
        int inc = G.ints().next();
        Asserts.assertEQ(s * inc, countDown(s, inc));
    }

    @Test
    @IR(failOn = { IRNode.COUNTED_LOOP, IRNode.LOOP, IRNode.SUB_L })
    @IR(counts = { IRNode.MUL_I, "=1" })
    @IR(counts = { IRNode.MUL_L, "=1" })
    private static long multipleIVs(int stop, int incA, long incB) {
        int a = 0;
        long b = 0;
        for (int i = 0; i < stop; i++) {
            a += incA;
            b += incB;
        }
        return a + b;
    }

    @Run(test = "multipleIVs")
    private static void runMultipleIVs() {
        int s = TRIP_COUNT.next();
        int incA = G.ints().next();
        long incB = G.longs().next();
        long expected = (long)(s * incA) + ((long) s * incB);
        Asserts.assertEQ(expected, multipleIVs(s, incA, incB));
    }

    @Test
    @IR(failOn = { IRNode.COUNTED_LOOP, IRNode.LOOP, IRNode.SUB_L, IRNode.URSHIFT_L })
    @IR(counts = { IRNode.MUL_I, "=1" })
    @IR(counts = { IRNode.MUL_L, "=1" })
    @IR(counts = { IRNode.URSHIFT_I, "=1" })
    private static long multipleIVsStride2(int stop, int incA, long incB) {
        int a = 0;
        long b = 0;
        for (int i = 0; i < stop; i += 2) {
            a += incA;
            b += incB;
        }
        return a + b;
    }

    @Run(test = "multipleIVsStride2")
    private static void runMultipleIVsStride2() {
        int s = TRIP_COUNT.next();
        int incA = G.ints().next();
        long incB = G.longs().next();
        int iters = Math.ceilDiv(s, 2);
        long expected = (long)(iters * incA) + ((long) iters * incB);
        Asserts.assertEQ(expected, multipleIVsStride2(s, incA, incB));
    }

    @Test
    @IR(failOn = { IRNode.COUNTED_LOOP, IRNode.LOOP })
    @IR(counts = { IRNode.MUL_I, "=1" })
    private static int nonZeroInit(int stop, int inc) {
        int a = 42;
        for (int i = 0; i < stop; i++) {
            a += inc;
        }
        return a;
    }

    @Run(test = "nonZeroInit")
    private static void runNonZeroInit() {
        int s = TRIP_COUNT.next();
        int inc = G.ints().next();
        Asserts.assertEQ(42 + s * inc, nonZeroInit(s, inc));
    }


    @Test
    @IR(failOn = { IRNode.COUNTED_LOOP, IRNode.LOOP })
    @IR(counts = { IRNode.MUL_I, "=1" })
    private static int nonZeroInitStride3(int stop, int inc) {
        int a = 42;
        for (int i = 0; i < stop; i += 3) {
            a += inc;
        }
        return a;
    }

    @Run(test = "nonZeroInitStride3")
    private static void runNonZeroInitStride3() {
        int s = TRIP_COUNT.next();
        int inc = G.ints().next();
        Asserts.assertEQ(42 + Math.ceilDiv(s, 3) * inc, nonZeroInitStride3(s, inc));
    }
    @Test
    @IR(counts = { IRNode.COUNTED_LOOP, "=1" })
    @IR(counts = { IRNode.MUL_I, "=1" })
    private static int sideEffectLoopAdd(int[] arr, int inc) {
        int a = 0;
        for (int i = 0; i < arr.length; i++) {
            arr[i] = i;
            a += inc;
        }
        return a;
    }

    @Run(test = "sideEffectLoopAdd")
    private static void runSideEffectLoopAdd() {
        int[] arr = new int[100];
        int inc = G.ints().next();
        Asserts.assertEQ(100 * inc, sideEffectLoopAdd(arr, inc));
    }

    @Test
    @IR(counts = { IRNode.COUNTED_LOOP, "=1" })
    @IR(counts = { IRNode.MUL_I, "=2" })
    @IR(counts = { IRNode.MUL_L, "=1" })
    private static long sideEffectLoopMultiIV(int[] arr, int incA, int incB, long incC) {
        int a = 0;
        int b = 0;
        long c = 0;
        for (int i = 0; i < arr.length; i++) {
            arr[i] = i;
            a += incA;
            b += incB;
            c += incC;
        }
        return a + b + c;
    }

    @Run(test = "sideEffectLoopMultiIV")
    private static void runSideEffectLoopMultiIV() {
        int[] arr = new int[100];
        int incA = G.ints().next();
        int incB = G.ints().next();
        long incC = G.longs().next();
        int a = 100 * incA;
        int b = 100 * incB;
        long c = 100L * incC;
        long expected = a + b + c;
        Asserts.assertEQ(expected, sideEffectLoopMultiIV(arr, incA, incB, incC));
    }

    @Test
    @IR(failOn = { IRNode.COUNTED_LOOP, IRNode.LOOP })
    @IR(counts = { IRNode.MUL_I, "=1" })
    private static int stride3NonMultiple(int stop, int inc) {
        int a = 0;
        for (int i = 0; i < stop; i += 3) {
            a += inc;
        }
        return a;
    }

    @Run(test = "stride3NonMultiple")
    private static void runStride3NonMultiple() {
        int s = TRIP_COUNT.next();
        int inc = G.ints().next();
        Asserts.assertEQ(Math.ceilDiv(s, 3) * inc, stride3NonMultiple(s, inc));
    }

    @Test
    @IR(failOn = { IRNode.COUNTED_LOOP, IRNode.LOOP })
    @IR(counts = { IRNode.MUL_I, "=1" })
    private static int nonZeroIVStart(int start, int stop, int inc) {
        int a = 0;
        for (int i = start; i < stop; i++) {
            a += inc;
        }
        return a;
    }

    @Run(test = "nonZeroIVStart")
    private static void runNonZeroIVStart() {
        int start = NEAR_MAX.next() - 10000;
        int stop = NEAR_MAX.next();
        int inc = G.ints().next();
        Asserts.assertEQ((stop - start) * inc, nonZeroIVStart(start, stop, inc));
    }

    @Test
    @IR(failOn = { IRNode.COUNTED_LOOP, IRNode.LOOP })
    @IR(counts = { IRNode.MUL_I, "=1" })
    private static int nonZeroIVStartStride3(int start, int stop, int inc) {
        int a = 0;
        for (int i = start; i < stop; i += 3) {
            a += inc;
        }
        return a;
    }

    @Run(test = "nonZeroIVStartStride3")
    private static void runNonZeroIVStartStride3() {
        int start = NEAR_MAX.next() - 10000;
        int stop = NEAR_MAX.next();
        int inc = G.ints().next();
        int iters = Math.ceilDiv(stop - start, 3);
        Asserts.assertEQ(iters * inc, nonZeroIVStartStride3(start, stop, inc));
    }

    @Test
    // MAX_VALUE * inc is strength-reduced to (inc << 31) - inc
    @IR(failOn = { IRNode.COUNTED_LOOP, IRNode.LOOP, IRNode.MUL_I })
    @IR(counts = { IRNode.LSHIFT_I, "=1" })
    @IR(counts = { IRNode.SUB_I, "=1" })
    private static int countDownMaxRange(int inc) {
        int a = 0;
        for (int i = Integer.MAX_VALUE; i > 0; i--) {
            a += inc;
        }
        return a;
    }

    @Run(test = "countDownMaxRange")
    private static void runCountDownMaxRange() {
        int inc = G.ints().next();
        Asserts.assertEQ(Integer.MAX_VALUE * inc, countDownMaxRange(inc));
    }

    @Test
    @IR(failOn = { IRNode.MUL_L }, phase = CompilePhase.BEFORE_CLOOPS)
    @IR(counts = { IRNode.MUL_I, "=1" }, phase = CompilePhase.BEFORE_CLOOPS) // only MulI for pow exists before
    @IR(failOn = { IRNode.COUNTED_LOOP, IRNode.LOOP })
    @IR(counts = { IRNode.MUL_L, "=2" })
    private static long conditionalAccum(int load, int i) {
        long x = 0;
        long pow = (i % 8) * (i % 16);
        for (int j = 0; j < load; j++) {
            if (i % 2 == 0) {
                x += pow;
            } else {
                x -= pow;
            }
        }
        return x;
    }

    @Run(test = "conditionalAccum")
    private static void runConditionalAccum() {
        int load = TRIP_COUNT.next();
        int i = G.ints().next();
        long pow = (i % 8) * (i % 16);
        long expected = (i % 2 == 0) ? (long) load * pow : -(long) load * pow;
        Asserts.assertEQ(expected, conditionalAccum(load, i));
    }

    // Large IV range where iv - init overflows signed int (regression test
    // for signed overflow bug caught by Test6905845). Use a large stride to
    // keep the trip count small while still triggering the overflow.
    @Test
    @IR(failOn = { IRNode.COUNTED_LOOP, IRNode.LOOP })
    @IR(counts = { IRNode.MUL_I, "=1" })
    private static int largeRangeOverflow(int inc) {
        int a = 0;
        for (int i = -1_500_000_000; i < 1_500_000_000; i += 3_000_007) {
            a += inc;
        }
        return a;
    }

    @Run(test = "largeRangeOverflow")
    private static void runLargeRangeOverflow() {
        int inc = G.ints().next();
        // trip count = ceil(3_000_000_000 / 3_000_007) = 1000
        Asserts.assertEQ(1000 * inc, largeRangeOverflow(inc));
    }

    @Test
    @IR(failOn = { IRNode.COUNTED_LOOP, IRNode.LOOP })
    @IR(counts = { IRNode.MUL_I, "=1" })
    @IR(counts = { IRNode.URSHIFT_I, "=1" })
    private static int wideRangePowerOfTwoStride(int start, int stop, int inc) {
        int a = 0;
        for (int i = start; i < stop; i += WIDE_STRIDE) {
            a += inc;
        }
        return a;
    }

    @Run(test = "wideRangePowerOfTwoStride")
    private static void runWideRangePowerOfTwoStride() {
        int inc = G.ints().next();
        Asserts.assertEQ(WIDE_ITERATIONS * inc, wideRangePowerOfTwoStride(WIDE_START, WIDE_STOP, inc));
    }

    @Test
    @IR(failOn = { IRNode.COUNTED_LOOP, IRNode.LOOP })
    @IR(counts = { IRNode.MUL_L, "=1" })
    @IR(counts = { IRNode.URSHIFT_I, "=1" })
    private static long wideRangePowerOfTwoStrideLong(int start, int stop, long inc) {
        long a = 0;
        for (int i = start; i < stop; i += WIDE_STRIDE) {
            a += inc;
        }
        return a;
    }

    @Run(test = "wideRangePowerOfTwoStrideLong")
    private static void runWideRangePowerOfTwoStrideLong() {
        long inc = G.longs().next();
        Asserts.assertEQ(WIDE_ITERATIONS * inc, wideRangePowerOfTwoStrideLong(WIDE_START, WIDE_STOP, inc));
    }

    @Test
    @IR(failOn = { IRNode.COUNTED_LOOP, IRNode.LOOP })
    @IR(counts = { IRNode.MUL_I, "=1" })
    @IR(counts = { IRNode.URSHIFT_I, "=1" })
    private static int wideRangeNegativePowerOfTwoStride(int start, int stop, int inc) {
        int a = 0;
        for (int i = start; i > stop; i -= WIDE_STRIDE) {
            a += inc;
        }
        return a;
    }

    @Run(test = "wideRangeNegativePowerOfTwoStride")
    private static void runWideRangeNegativePowerOfTwoStride() {
        int inc = G.ints().next();
        Asserts.assertEQ(WIDE_ITERATIONS * inc,
                wideRangeNegativePowerOfTwoStride(WIDE_DOWN_START, WIDE_DOWN_STOP, inc));
    }

    @Test
    @IR(failOn = { IRNode.COUNTED_LOOP, IRNode.LOOP })
    @IR(counts = { IRNode.MUL_I, "=1" })
    private static int subStride3(int stop, int inc) {
        int a = 0;
        for (int i = 0; i < stop; i += 3) {
            a -= inc;
        }
        return a;
    }

    @Run(test = "subStride3")
    private static void runSubStride3() {
        int s = TRIP_COUNT.next();
        int inc = G.ints().next();
        Asserts.assertEQ(-Math.ceilDiv(s, 3) * inc, subStride3(s, inc));
    }

    @Test
    @IR(failOn = { IRNode.COUNTED_LOOP, IRNode.LOOP })
    @IR(counts = { IRNode.MUL_L, "=1" })
    private static long longStride3(int stop, long inc) {
        long a = 0;
        for (int i = 0; i < stop; i += 3) {
            a += inc;
        }
        return a;
    }

    @Run(test = "longStride3")
    private static void runLongStride3() {
        int s = TRIP_COUNT.next();
        long inc = G.longs().next();
        Asserts.assertEQ((long) Math.ceilDiv(s, 3) * inc, longStride3(s, inc));
    }


    @Test
    @IR(failOn = { IRNode.COUNTED_LOOP, IRNode.LOOP })
    @IR(counts = { IRNode.MUL_L, "=1" })
    private static long longSubStride3(int stop, long inc) {
        long a = 0;
        for (int i = 0; i < stop; i += 3) {
            a -= inc;
        }
        return a;
    }

    @Run(test = "longSubStride3")
    private static void runLongSubStride3() {
        int s = TRIP_COUNT.next();
        long inc = G.longs().next();
        Asserts.assertEQ(-((long) Math.ceilDiv(s, 3) * inc), longSubStride3(s, inc));
    }
    @Test
    @IR(counts = { IRNode.COUNTED_LOOP, "=1" })
    @IR(counts = { IRNode.LSHIFT_I, "=1" })
    private static int usedInsideRatioFallback(int[] arr, int stop) {
        int a = 0;
        for (int i = 0; i < stop; i += 3) {
            arr[i % arr.length] = a;
            a += 9;
        }
        return a;
    }

    @Run(test = "usedInsideRatioFallback")
    private static void runUsedInsideRatioFallback() {
        int[] arr = new int[100];
        int s = TRIP_COUNT.next();
        Asserts.assertEQ(Math.ceilDiv(s, 3) * 9, usedInsideRatioFallback(arr, s));
    }

    @Test
    @IR(counts = { IRNode.COUNTED_LOOP, "=1" })
    private static int usedInsideNonConstant(int[] arr, int stop, int inc) {
        int a = 0;
        for (int i = 0; i < stop; i += 3) {
            arr[i % arr.length] = a;
            a += inc;
        }
        return a;
    }

    @Run(test = "usedInsideNonConstant")
    private static void runUsedInsideNonConstant() {
        int[] arr = new int[100];
        int s = TRIP_COUNT.next();
        int inc = G.ints().next();
        Asserts.assertEQ(Math.ceilDiv(s, 3) * inc, usedInsideNonConstant(arr, s, inc));
    }

    @Test
    @IR(counts = { IRNode.COUNTED_LOOP, "=1" })
    private static int usedInsideNonMultiple(int[] arr, int stop) {
        int a = 0;
        for (int i = 0; i < stop; i += 3) {
            arr[i % arr.length] = a;
            a += 7;
        }
        return a;
    }

    @Run(test = "usedInsideNonMultiple")
    private static void runUsedInsideNonMultiple() {
        int[] arr = new int[100];
        int s = TRIP_COUNT.next();
        Asserts.assertEQ(Math.ceilDiv(s, 3) * 7, usedInsideNonMultiple(arr, s));
    }

    @Test
    @IR(failOn = { IRNode.COUNTED_LOOP, IRNode.LOOP })
    @IR(counts = { IRNode.MUL_I, "=2" })
    private static int countDownStride3(int start, int inc) {
        int a = 0;
        for (int i = start; i > 0; i -= 3) {
            a += inc;
        }
        return a;
    }

    @Run(test = "countDownStride3")
    private static void runCountDownStride3() {
        int s = TRIP_COUNT.next();
        int inc = G.ints().next();
        Asserts.assertEQ(Math.ceilDiv(s, 3) * inc, countDownStride3(s, inc));
    }

    @Test
    @Arguments(values = { Argument.NUMBER_42, Argument.NUMBER_42 })
    @IR(counts = { IRNode.COUNTED_LOOP, "=1" })
    private static int loopVariantNotOptimized(int stop, int inc) {
        int a = 0;
        for (int i = 0; i < stop; i++) {
            a += i * inc;
        }
        return a;
    }


    @Test
    @IR(failOn = { IRNode.MUL_I })
    @IR(counts = { IRNode.COUNTED_LOOP, "=1" })
    @IR(counts = { IRNode.SUB_I, ">=1" })
    private static int subPhiOnRight(int stop, int inc) {
        int a = 0;
        for (int i = 0; i < stop; i++) {
            a = inc - a;
        }
        return a;
    }

    @Run(test = "subPhiOnRight")
    private static void runSubPhiOnRight() {
        int s = TRIP_COUNT.next();
        int inc = G.ints().next();
        Asserts.assertEQ((s & 1) == 0 ? 0 : inc, subPhiOnRight(s, inc));
    }

    @Test
    @IR(failOn = { IRNode.MUL_L })
    @IR(counts = { IRNode.COUNTED_LOOP, "=1" })
    @IR(counts = { IRNode.SUB_L, "=1" })
    private static long subPhiOnRightLong(int stop, long inc) {
        long a = 0;
        for (int i = 0; i < stop; i++) {
            a = inc - a;
        }
        return a;
    }

    @Run(test = "subPhiOnRightLong")
    private static void runSubPhiOnRightLong() {
        int s = TRIP_COUNT.next();
        long inc = G.longs().next();
        Asserts.assertEQ((s & 1) == 0 ? 0L : inc, subPhiOnRightLong(s, inc));
    }

    @Test
    @IR(counts = { IRNode.MUL_I, "=1" }, phase = CompilePhase.BEFORE_CLOOPS)
    @IR(failOn = { IRNode.COUNTED_LOOP, IRNode.LOOP })
    @IR(counts = { IRNode.MUL_I, "=2" })
    private static int invariantComputedInLoop(int stop, int inc) {
        int a = 0;
        for (int i = 0; i < stop; i++) {
            int t = inc * inc;
            a += t;
        }
        return a;
    }

    @Run(test = "invariantComputedInLoop")
    private static void runInvariantComputedInLoop() {
        int s = TRIP_COUNT.next();
        int inc = G.ints().next();
        Asserts.assertEQ(s * (inc * inc), invariantComputedInLoop(s, inc));
    }
    @Test
    // Either fully optimized or has LOOP and not COUNTED_LOOP (random stride may leave a non-counted Loop).
    @IR(failOn = { IRNode.COUNTED_LOOP })
    private static int randomLoopStrideConst(int stop, int inc) {
        int a = 0;
        for (int i = 0; i < stop; i += LOOP_STRIDE_RAND) {
            a += inc;
        }
        return a;
    }

    @Run(test = "randomLoopStrideConst")
    private static void runRandomLoopStrideConst() {
        int s = G.uniformInts(0, LOOP_STRIDE_RAND_MAX_STOP).next();
        int inc = G.ints().next();
        Asserts.assertEQ(Math.ceilDiv(s, LOOP_STRIDE_RAND) * inc, randomLoopStrideConst(s, inc));
    }

    @Test
    @IR(counts = { IRNode.COUNTED_LOOP, "=1" })
    @IR(counts = { IRNode.MUL_I, "=1" })
    private static int multIvNotOptimized(int stop, int inc) {
        int a = 1;
        for (int i = 0; i < stop; i++) {
            a *= inc;
        }
        return a;
    }

    @Run(test = "multIvNotOptimized")
    private static void runMultIvNotOptimized() {
        int s = G.ints().restricted(0, 29).next();
        int inc = G.ints().next();
        int expected = 1;
        for (int k = 0; k < s; k++) {
            expected *= inc;
        }
        Asserts.assertEQ(expected, multIvNotOptimized(s, inc));
    }

    @Test
    @IR(counts = { IRNode.COUNTED_LOOP, "=1" })
    private static int xorIvNotOptimized(int stop, int inc) {
        int a = 0;
        for (int i = 0; i < stop; i++) {
            a ^= inc;
        }
        return a;
    }

    @Run(test = "xorIvNotOptimized")
    private static void runXorIvNotOptimized() {
        int s = TRIP_COUNT.next();
        int inc = G.ints().next();
        Asserts.assertEQ((s & 1) == 0 ? 0 : inc, xorIvNotOptimized(s, inc));
    }

    @Test
    @IR(failOn = { IRNode.COUNTED_LOOP, IRNode.LOOP, IRNode.MUL_L })
    @IR(counts = { IRNode.SUB_L, "=1" })
    @IR(counts = { IRNode.LSHIFT_L, "=1" })
    private static long longStride1FullRange(long inc) {
        long b = 0;
        for (int i = Integer.MIN_VALUE; i < Integer.MAX_VALUE; i++) {
            b += inc;
        }
        return b;
    }

    @Run(test = "longStride1FullRange")
    private static void runLongStride1FullRange() {
        long inc = G.longs().next();
        long iters = (long) Integer.MAX_VALUE - (long) Integer.MIN_VALUE;
        Asserts.assertEQ(iters * inc, longStride1FullRange(inc));
    }
}
