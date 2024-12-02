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

package compiler.loopopts;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;

import java.util.Random;

/**
 * @test
 * @bug 8336759
 * @summary test long limits in int counted loops are speculatively converted to int for counted loop
 *         optimizations
 * @library /test/lib /
 * @requires vm.compiler2.enabled
 * @run driver compiler.loopopts.TestIntCountedLoopLongLimit
 */
public class TestIntCountedLoopLongLimit {
    private static final Random RNG = jdk.test.lib.Utils.getRandomInstance();

    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:+IgnoreUnrecognizedVMOptions",
                // StressLongCountedLoop is only available in debug builds
                "-XX:StressLongCountedLoop=0", // Don't convert int counted loops to long ones
                "-XX:PerMethodTrapLimit=100" // allow slow-path loop limit checks
        );
    }

    @Test
    @IR(counts = { IRNode.COUNTED_LOOP, "2" }) // Make sure IR tests can pick up counted loops.
    @IR(failOn = { IRNode.LOOP })
    public static int testControlledCountedLoop(int limit) {
        int sum = 0;
        for (int i = 0; i < limit; i++) {
            sum += i;
        }
        return sum;
    }

    @Test
    @IR(counts = { IRNode.COUNTED_LOOP, "2" })
    @IR(failOn = { IRNode.LOOP })
    public static int testCountedLoopWithLongLimit(long limit) {
        int sum = 0;
        for (int i = 0; i < limit; i++) {
            sum += i;
        }
        return sum;
    }

    @Test
    @IR(counts = { IRNode.COUNTED_LOOP, "2" })
    @IR(failOn = { IRNode.LOOP })
    public static int testCountedLoopWithSwappedComparisonOperand(long limit) {
        int sum = 0;
        for (int i = 0; limit > i; i++) {
            sum += i;
        }
        return sum;
    }

    // Test counted loops, regardless of limit types, are correctly constructed.
    @Run(test = { "testControlledCountedLoop", "testCountedLoopWithLongLimit",
            "testCountedLoopWithSwappedComparisonOperand" })
    public static void runTestSimpleCountedLoops(RunInfo info) {
        long limit = RNG.nextLong(0, 1024 * 1024); // Choice a small number to avoid tests taking too long
        int expected = testControlledCountedLoop((int) limit);
        int observed1 = testCountedLoopWithLongLimit(limit);
        int observed2 = testCountedLoopWithSwappedComparisonOperand(limit);

        Asserts.assertEQ(expected, observed1);
        Asserts.assertEQ(expected, observed2);
    }

    @Test
    @IR(failOn = { IRNode.COUNTED_LOOP, IRNode.LOOP }) // Eliminated by IR replacement
    public static int testIvReplacedCountedLoop(long limit) {
        int sum = 0;
        for (int i = 0; i < limit; i++) {
            sum += 1;
        }
        return sum;
    }

    @Test
    @IR(failOn = { IRNode.COUNTED_LOOP, IRNode.LOOP }) // Eliminated by IR replacement
    public static long testLongIvReplacedCountedLoop(long limit) {
        long sum = 0;
        for (int i = 0; i < limit; i++) {
            sum += 1;
        }
        return sum;
    }

    // Test counted loops with int and long IV types, are corrected constructed, IV replaced, and eliminated.
    @Run(test = { "testIvReplacedCountedLoop", "testLongIvReplacedCountedLoop" })
    public static void runTestIvReplacedCountedLoop(RunInfo info) {
        long limit = RNG.nextLong(0, 1024 * 1024);

        Asserts.assertEQ(limit, (long) testIvReplacedCountedLoop(limit));
        Asserts.assertEQ(limit, testLongIvReplacedCountedLoop(limit));
    }

    // Use a larger stride to avoid tests taking too long
    private static final int LARGE_STRIDE = Integer.MAX_VALUE / 1024;

    // Test counted loop deoptimizes if the long limit falls outside int range.
    @Test
    @IR(failOn = { IRNode.COUNTED_LOOP })
    public static int testCountedLoopWithOverflow(long limit) {
        int sum = 0;
        for (int i = 0; i < limit; i += LARGE_STRIDE) {
            sum += LARGE_STRIDE;

            if (i < 0) {
                return -1; // overflow detected!
            }
        }
        return sum;
    }

    @Test
    @IR(failOn = { IRNode.COUNTED_LOOP })
    public static int testCountedLoopWithUnderflow(long limit) {
        int sum = 0;
        for (int i = 0; i > limit; i -= LARGE_STRIDE) {
            sum -= LARGE_STRIDE;

            if (i > 0) {
                return 1; // underflow detected!
            }
        }
        return sum;
    }

    @Run(test = { "testCountedLoopWithOverflow", "testCountedLoopWithUnderflow" })
    public static void runTestCountedLoopWithOverflow(RunInfo info) {
        long limit = RNG.nextLong(0, 1024) * LARGE_STRIDE;

        Asserts.assertEQ((int) limit, testCountedLoopWithOverflow(limit));
        Asserts.assertEQ((int) -limit, testCountedLoopWithUnderflow(-limit));

        if (info.isTestC2Compiled("testCountedLoopWithOverflow")) {
            Asserts.assertEQ(-1, testCountedLoopWithOverflow(Integer.MAX_VALUE));
            Asserts.assertEQ(-1, testCountedLoopWithOverflow(Integer.MAX_VALUE + 1L));
            Asserts.assertEQ(-1, testCountedLoopWithOverflow(Integer.MAX_VALUE + limit));
        }

        if (info.isTestC2Compiled("testCountedLoopWithUnderflow")) {
            Asserts.assertEQ(1, testCountedLoopWithUnderflow(Integer.MIN_VALUE));
            Asserts.assertEQ(1, testCountedLoopWithUnderflow(Integer.MIN_VALUE - 1L));
            System.out.println(Integer.MIN_VALUE - limit);
            Asserts.assertEQ(1, testCountedLoopWithUnderflow(Integer.MIN_VALUE - limit));
        }
    }

    private static final long SOME_LONG = 42;

    // Test optimization is not applied if the limit is not invariant.
    // This is handled by the existing counted loop detection, but we might as well test it here, too.
    @Test
    @IR(counts = { IRNode.CONV_I2L, "1" })
    @IR(failOn = { IRNode.COUNTED_LOOP, IRNode.CONV_L2I })
    @Arguments(values = { Argument.NUMBER_42 })
    public static int testLimitNotInvariant(long limit) {
        int sum = 0;
        for (int i = 0; i < limit; i++) {
            sum += 1;
            limit = SOME_LONG;
        }
        return sum;
    }
}
