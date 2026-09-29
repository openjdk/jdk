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

package compiler.loopopts;

import compiler.lib.generators.Generator;
import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;

import java.lang.foreign.*;

import static compiler.lib.generators.Generators.*;

/*
 * @test
 * @bug 8336759
 * @key randomness
 * @summary Test long limits in int counted loops are speculatively converted
 *          to int for counted loop optimizations - IR shape verification.
 * @requires vm.compiler2.enabled
 * @requires (vm.opt.StressLongCountedLoop == null | vm.opt.StressLongCountedLoop != 0)
 * @library /test/lib /
 * @run driver compiler.loopopts.TestIntCountedLoopLongLimit
 */
public class TestIntCountedLoopLongLimit {

    private static final Generator<Long> SMALL_UNIFORMS = G.uniformLongs(0, 1024 * 1024 - 1);
    private static final int LARGE_STRIDE = Integer.MAX_VALUE / 1024 / 1024;
    private static volatile long SOME_LONG = 42;

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Test
    @IR(counts = { IRNode.COUNTED_LOOP, "2" })
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

    @Run(test = { "testControlledCountedLoop", "testCountedLoopWithLongLimit",
            "testCountedLoopWithSwappedComparisonOperand" })
    public static void runTestSimpleCountedLoops() {
        long limit = SMALL_UNIFORMS.next();
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

    @Run(test = { "testIvReplacedCountedLoop", "testLongIvReplacedCountedLoop" })
    public static void runTestIvReplacedCountedLoop() {
        long limit = SMALL_UNIFORMS.next();

        Asserts.assertEQ(limit, (long) testIvReplacedCountedLoop(limit));
        Asserts.assertEQ(limit, testLongIvReplacedCountedLoop(limit));
    }

    @Test
    @IR(counts = { IRNode.COUNTED_LOOP, "2" })
    @IR(failOn = { IRNode.LOOP })
    public static int testCountedLoopWithOverflow(int init, long limit) {
        int sum = 0;
        for (int i = init; i < limit; i += LARGE_STRIDE) {
            sum += LARGE_STRIDE;

            if (i < 0) {
                return -1; // overflow detected!
            }
        }
        return sum;
    }

    @Test
    @IR(counts = { IRNode.COUNTED_LOOP, "2" })
    @IR(failOn = { IRNode.LOOP })
    public static int testCountedLoopWithUnderflow(int init, long limit) {
        int sum = 0;
        for (int i = init; i > limit; i -= LARGE_STRIDE) {
            sum -= LARGE_STRIDE;

            if (i > 0) {
                return 1; // underflow detected!
            }
        }
        return sum;
    }

    @Run(test = { "testCountedLoopWithOverflow", "testCountedLoopWithUnderflow" })
    public static void runTestCountedLoopWithOverflow() {
        long trips = SMALL_UNIFORMS.next();
        int init = G.uniformInts(0, 10).next() * LARGE_STRIDE;
        long limit = init + trips * LARGE_STRIDE; // within int range, no over/underflow

        Asserts.assertEQ((int) (trips * LARGE_STRIDE), testCountedLoopWithOverflow(init, limit));
        Asserts.assertEQ((int) -(trips * LARGE_STRIDE), testCountedLoopWithUnderflow(-init, -limit));
    }

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

    @Test
    @IR(counts = { IRNode.COUNTED_LOOP, ">=2" })
    @IR(failOn = { IRNode.LOOP })
    public static int testMemorySegmentSizeLimit(MemorySegment segment) {
        int sum = 0;
        for (int i = 0; i < segment.byteSize(); i++) {
            sum += segment.get(ValueLayout.JAVA_BYTE, i);
        }
        return sum;
    }

    @Test
    @IR(counts = { IRNode.COUNTED_LOOP, "2" })
    @IR(failOn = { IRNode.LOOP })
    public static int testWithConstantLongLimit() {
        int sum = 0;
        for (int i = 0; i < 1024L; i++) {
            sum += i;
        }
        return sum;
    }

    @Run(test = { "testMemorySegmentSizeLimit" })
    public static void runTestMemorySegmentSizeLimit() {
        MemorySegment segment = Arena.ofAuto().allocate(1024);
        segment.fill((byte) 1);

        Asserts.assertEQ(1024, testMemorySegmentSizeLimit(segment));
    }
}
