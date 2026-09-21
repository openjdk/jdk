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

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

import java.util.Objects;
import java.util.Random;

/**
 * @test
 * @bug 8342708
 * @key randomness
 * @summary Test parallel IV replacement in long counted loops
 * @library /test/lib /
 * @requires vm.compiler2.enabled
 * @run driver compiler.loopopts.parallel_iv.TestLongParallelIVShape
 */
public class TestLongParallelIVShape {
    // TODO: use generators
    private static final Random RNG = Utils.getRandomInstance();

    static int[] array = new int[4096];

    public static void main(String[] args) {
        TestFramework.runWithFlags(
                "-XX:-ShortRunningLongLoop",
                "-XX:+IgnoreUnrecognizedVMOptions",
                "-XX:+UseNewCode"
        );
    }

    // A controlled test making sure a simple counted loop can be found by the test framework.
    @Test
    @Arguments(values = { Argument.NUMBER_42 })
    @IR(counts = { IRNode.COUNTED_LOOP, ">=1" })
    static long testControlledSimpleLoop(long stop) {
        long a = 0;
        for (long i = 0; i < stop; i++) {
            a += i; // cannot be extracted to multiplications
        }

        return a;
    }

    // Long parallel IV with constant stride in a long counted loop.
    // Loop nest creates inner int CountedLoop, existing int
    // replace_parallel_iv fires, then empty loop removal eliminates the loop.
    @Test
    @IR(failOn = { IRNode.COUNTED_LOOP })
    static long testLongLoopWithLongIV(long stop) {
        long a = 0;
        for (long i = 0; i < stop; i++) {
            a += 42;
        }
        return a;
    }

    @Run(test = "testLongLoopWithLongIV")
    private static void runTestLongLoopWithLongIV() {
        long s = RNG.nextLong(0, 10_000);
        Asserts.assertEQ(42L * s, testLongLoopWithLongIV(s));
    }

    @Test
    @IR(failOn = { IRNode.COUNTED_LOOP })
    static long testIntLoopWithLongIV(long stop) {
        long a = 0;
        for (int i = 0; i < stop; i++) {
            a += 42;
        }
        return a;
    }

    @Run(test = "testIntLoopWithLongIV")
    private static void runTestIntLoopWithLongIV() {
        long s = RNG.nextInt(0, 10_000);
        Asserts.assertEQ(42L * s, testIntLoopWithLongIV(s));
    }

    // Int parallel IV in a long counted loop. Same pipeline as above.
    @Test
    @IR(failOn = { IRNode.COUNTED_LOOP })
    static int testLongLoopIntIV(long stop) {
        int a = 0;
        for (long i = 0; i < stop; i++) {
            a += 42;
        }
        return a;
    }

    @Run(test = "testLongLoopIntIV")
    private static void runTestLongLoopIntIV() {
        long s = RNG.nextLong(0, 10_000);
        Asserts.assertEQ((int)(42L * s), testLongLoopIntIV(s));
    }

    // Multiple parallel IVs. Both handled by the existing int pipeline.
    @Test
    @IR(failOn = { IRNode.COUNTED_LOOP })
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
        long s = RNG.nextLong(0, 10_000);
        Asserts.assertEQ(49L * s, testLongLoopTwoIVs(s));
    }

    // Stride exceeds Integer.MAX_VALUE. Still handled by the existing int
    // pipeline (replace_parallel_iv uses jlong for stride_con2).
    @Test
    @IR(failOn = { IRNode.COUNTED_LOOP })
    static long testLongLoopHugeStride(long stop) {
        long a = 0;
        for (long i = 0; i < stop; i++) {
            a += 3_000_000_000L;
        }
        return a;
    }

    @Run(test = "testLongLoopHugeStride")
    private static void runTestLongLoopHugeStride() {
        long s = RNG.nextLong(0, 10_000);
        Asserts.assertEQ(3_000_000_000L * s, testLongLoopHugeStride(s));
    }

    // Parallel IV used in a range check (via Objects.checkIndex). Without long
    // replace_parallel_iv, j is its own phi and extract_long_range_checks
    // cannot match it against the primary IV i — the range check stays in the
    // loop body and cannot be hoisted. With long replace_parallel_iv running
    // before loop nest creation, j is replaced with i*3, allowing
    // is_range_check_if to recongize Objects.checkIndex().
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
        long s = RNG.nextInt(0, 1366);
        Asserts.assertEQ(0L, testLongLoopParallelIVRangeCheck(s));
    }
}
