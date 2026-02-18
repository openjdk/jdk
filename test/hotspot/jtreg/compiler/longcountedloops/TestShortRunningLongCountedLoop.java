/*
 * Copyright (c) 2025, Red Hat, Inc. All rights reserved.
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

package compiler.longcountedloops;
import compiler.lib.ir_framework.*;
import compiler.whitebox.CompilerWhiteBoxTest;
import jdk.test.whitebox.WhiteBox;

import java.util.Objects;
/*
 * @test
 * @bug 8342692
 * @summary C2: long counted loop/long range checks: don't create loop-nest for short running loops
 * @library /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI compiler.longcountedloops.TestShortRunningLongCountedLoop
 */

public class TestShortRunningLongCountedLoop {
    private static volatile int volatileField;
    private final static WhiteBox wb = WhiteBox.getWhiteBox();

    public static void main(String[] args) {
        // IR rules expect a single loop so disable unrolling
        // IR rules expect strip mined loop to be enabled
        // testIntLoopUnknownBoundsShortUnswitchedLoop and testLongLoopUnknownBoundsShortUnswitchedLoop need -XX:-UseProfiledLoopPredicate
        TestFramework.runWithFlags("-XX:LoopMaxUnroll=0", "-XX:LoopStripMiningIter=1000", "-XX:+UseCountedLoopSafepoints", "-XX:-UseProfiledLoopPredicate");
    }

    // Check IR only has a counted loop when bounds are known and loop run for a short time
    @Test
    @IR(counts = { IRNode.COUNTED_LOOP, "1" })
    @IR(failOn = { IRNode.LOOP, IRNode.OUTER_STRIP_MINED_LOOP, IRNode.SHORT_RUNNING_LOOP_TRAP })
    public static int testLongLoopConstantBoundsShortLoop1() {
        int j = 0;
        for (long i = 0; i < 100; i++) {
            volatileField = 42;
            j++;
        }
        return j;
    }

    @Check(test = "testLongLoopConstantBoundsShortLoop1")
    public static void checkTestLongLoopConstantBoundsShortLoop1(int res) {
        if (res != 100) {
            throw new RuntimeException("incorrect result: " + res);
        }
    }

    // Same with stride > 1
    @Test
    @IR(counts = { IRNode.COUNTED_LOOP, "1" })
    @IR(failOn = { IRNode.LOOP, IRNode.OUTER_STRIP_MINED_LOOP, IRNode.SHORT_RUNNING_LOOP_TRAP })
    public static int testLongLoopConstantBoundsShortLoop2() {
        int j = 0;
        for (long i = 0; i < 2000; i += 20) {
            volatileField = 42;
            j++;
        }
        return j;
    }

    @Check(test = "testLongLoopConstantBoundsShortLoop2")
    public static void checkTestLongLoopConstantBoundsShortLoop2(int res) {
        if (res != 100) {
            throw new RuntimeException("incorrect result: " + res);
        }
    }

    // Same with loop going downward
    @Test
    @IR(counts = { IRNode.COUNTED_LOOP, "1" })
    @IR(failOn = { IRNode.LOOP, IRNode.OUTER_STRIP_MINED_LOOP, IRNode.SHORT_RUNNING_LOOP_TRAP })
    public static int testLongLoopConstantBoundsShortLoop3() {
        int j = 0;
        for (long i = 99; i >= 0; i--) {
            volatileField = 42;
            j++;
        }
        return j;
    }

    @Check(test = "testLongLoopConstantBoundsShortLoop3")
    public static void checkTestLongLoopConstantBoundsShortLoop3(int res) {
        if (res != 100) {
            throw new RuntimeException("incorrect result: " + res);
        }
    }

    // Same with loop going downward and stride > 1
    @Test
    @IR(counts = { IRNode.COUNTED_LOOP, "1" })
    @IR(failOn = { IRNode.LOOP, IRNode.OUTER_STRIP_MINED_LOOP, IRNode.SHORT_RUNNING_LOOP_TRAP })
    public static int testLongLoopConstantBoundsShortLoop4() {
        int j = 0;
        for (long i = 1999; i >= 0; i-=20) {
            volatileField = 42;
            j++;
        }
        return j;
    }

    @Check(test = "testLongLoopConstantBoundsShortLoop4")
    public static void checkTestLongLoopConstantBoundsShortLoop4(int res) {
        if (res != 100) {
            throw new RuntimeException("incorrect result: " + res);
        }
    }

    // Check IR only has a counted loop when bounds are known but not exact and loop run for a short time
    @Test
    @IR(counts = { IRNode.COUNTED_LOOP, "1" })
    @IR(failOn = { IRNode.LOOP, IRNode.OUTER_STRIP_MINED_LOOP, IRNode.SHORT_RUNNING_LOOP_TRAP })
    public static int testLongLoopConstantBoundsShortLoop5(int start, int stop) {
        start= Integer.max(start, 0);
        stop= Integer.min(stop, 999);
        int j = 0;
        for (long i = start; i < stop; i++) {
            volatileField = 42;
            j++;
        }
        return j;
    }

    @Run(test = "testLongLoopConstantBoundsShortLoop5")
    public static void testLongLoopConstantBoundsShortLoop5_runner() {
        int res = testLongLoopConstantBoundsShortLoop5(0, 100);
        if (res != 100) {
            throw new RuntimeException("incorrect result: " + res);
        }
    }

    // Check that loop nest is created when bounds are known and loop is not short run
    @Test
    @IR(counts = { IRNode.COUNTED_LOOP, "1", IRNode.LOOP, "1"})
    @IR(failOn = { IRNode.SHORT_RUNNING_LOOP_TRAP, IRNode.OUTER_STRIP_MINED_LOOP })
    public static int testLongLoopConstantBoundsLongLoop1() {
        final long stride = Integer.MAX_VALUE / 1000;
        int j = 0;
        for (long i = 0; i < stride * 1001; i += stride) {
            volatileField = 42;
            j++;
        }
        return j;
    }

    @Check(test = "testLongLoopConstantBoundsLongLoop1")
    public static void checkTestLongLoopConstantBoundsLongLoop1(int res) {
        if (res != 1001) {
            throw new RuntimeException("incorrect result: " + res);
        }
    }

    // Same with negative stride
    @Test
    @IR(counts = { IRNode.COUNTED_LOOP, "1", IRNode.LOOP, "1"})
    @IR(failOn = { IRNode.SHORT_RUNNING_LOOP_TRAP, IRNode.OUTER_STRIP_MINED_LOOP })
    public static int testLongLoopConstantBoundsLongLoop2() {
        final long stride = Integer.MAX_VALUE / 1000;
        int j = 0;
        for (long i = stride * 1000; i >= 0; i -= stride) {
            volatileField = 42;
            j++;
        }
        return j;
    }

    @Check(test = "testLongLoopConstantBoundsLongLoop2")
    public static void checkTestLongLoopConstantBoundsLongLoop2(int res) {
        if (res != 1001) {
            throw new RuntimeException("incorrect result: " + res);
        }
    }

    // Check IR only has a counted loop when bounds are unknown but profile reports a short running loop
    @Test
    @IR(counts = { IRNode.COUNTED_LOOP, "1", IRNode.SHORT_RUNNING_LOOP_TRAP, "1", IRNode.OUTER_STRIP_MINED_LOOP, "1" })
    @IR(failOn = { IRNode.LOOP })
    public static int testLongLoopUnknownBoundsShortLoop(long start, long stop) {
        int j = 0;
        for (long i = start; i < stop; i++) {
            volatileField = 42;
            j++;
        }
        return j;
    }

    @Run(test = "testLongLoopUnknownBoundsShortLoop")
    @Warmup(10_000)
    public static void testLongLoopUnknownBoundsShortLoop_runner() {
        int res = testLongLoopUnknownBoundsShortLoop(0, 100);
        if (res != 100) {
            throw new RuntimeException("incorrect result: " + res);
        }
    }

    // same with stride > 1
    @Test
    @IR(counts = { IRNode.COUNTED_LOOP, "1", IRNode.SHORT_RUNNING_LOOP_TRAP, "1", IRNode.OUTER_STRIP_MINED_LOOP, "1" })
    @IR(failOn = { IRNode.LOOP })
    public static int testLongLoopUnknownBoundsShortLoop2(long start, long stop) {
        int j = 0;
        for (long i = start; i < stop; i+=20) {
            volatileField = 42;
            j++;
        }
        return j;
    }

    @Run(test = "testLongLoopUnknownBoundsShortLoop2")
    @Warmup(10_000)
    public static void testLongLoopUnknownBoundsShortLoop2_runner() {
        int res = testLongLoopUnknownBoundsShortLoop2(0, 2000);
        if (res != 100) {
            throw new RuntimeException("incorrect result: " + res);
        }
    }

    // same with negative stride
    @Test
    @IR(counts = { IRNode.COUNTED_LOOP, "1", IRNode.SHORT_RUNNING_LOOP_TRAP, "1", IRNode.OUTER_STRIP_MINED_LOOP, "1" })
    @IR(failOn = { IRNode.LOOP })
    public static int testLongLoopUnknownBoundsShortLoop3(long start, long stop) {
        int j = 0;
        for (long i = start; i >= stop; i--) {
            volatileField = 42;
            j++;
        }
        return j;
    }

    @Run(test = "testLongLoopUnknownBoundsShortLoop3")
    @Warmup(10_000)
    public static void testLongLoopUnknownBoundsShortLoop3_runner() {
        int res = testLongLoopUnknownBoundsShortLoop3(99, 0);
        if (res != 100) {
            throw new RuntimeException("incorrect result: " + res);
        }
    }

    // same with negative stride > 1
    @Test
    @IR(counts = { IRNode.COUNTED_LOOP, "1", IRNode.SHORT_RUNNING_LOOP_TRAP, "1", IRNode.OUTER_STRIP_MINED_LOOP, "1" })
    @IR(failOn = { IRNode.LOOP })
    public static int testLongLoopUnknownBoundsShortLoop4(long start, long stop) {
        int j = 0;
        for (long i = start; i >= stop; i -= 20) {
            volatileField = 42;
            j++;
        }
        return j;
    }

    @Run(test = "testLongLoopUnknownBoundsShortLoop4")
    @Warmup(10_000)
    public static void testLongLoopUnknownBoundsShortLoop4_runner() {
        int res = testLongLoopUnknownBoundsShortLoop4(1999, 0);
        if (res != 100) {
            throw new RuntimeException("incorrect result: " + res);
        }
    }

    // Check that loop nest is created when bounds are not known but profile reports loop is not short run
    @Test
    @IR(counts = { IRNode.COUNTED_LOOP, "1", IRNode.OUTER_STRIP_MINED_LOOP, "1", IRNode.LOOP,  "1"})
    @IR(failOn = { IRNode.SHORT_RUNNING_LOOP_TRAP })
    public static int testLongLoopUnknownBoundsLongLoop1(long start, long stop, long range) {
        int j = 0;
        for (long i = start; i < stop; i++) {
            volatileField = 42;
            Objects.checkIndex(i * (1024 * 1024), range); // max number of iteration of inner loop is roughly Integer.MAX_VALUE / 1024 / 1024
            j++;
        }
        return j;
    }

    @Run(test = "testLongLoopUnknownBoundsLongLoop1")
    @Warmup(10_000)
    public static void testLongLoopUnknownBoundsLongLoop1_runner() {
        int res = testLongLoopUnknownBoundsLongLoop1(0, 3000, Long.MAX_VALUE);
        if (res != 3000) {
            throw new RuntimeException("incorrect result: " + res);
        }
    }

    // same with negative stride
    @Test
    @IR(counts = { IRNode.COUNTED_LOOP, "1", IRNode.OUTER_STRIP_MINED_LOOP, "1", IRNode.LOOP,  "1"})
    @IR(failOn = { IRNode.SHORT_RUNNING_LOOP_TRAP })
    public static int testLongLoopUnknownBoundsLongLoop2(long start, long stop, long range) {
        int j = 0;
        for (long i = start; i >= stop; i--) {
            volatileField = 42;
            Objects.checkIndex(i * (1024 * 1024), range); // max number of iteration of inner loop is roughly Integer.MAX_VALUE / 1024 / 1024
            j++;
        }
        return j;
    }

    @Run(test = "testLongLoopUnknownBoundsLongLoop2")
    @Warmup(10_000)
    public static void testLongLoopUnknownBoundsLongLoop2_runner() {
        int res = testLongLoopUnknownBoundsLongLoop2(2999, 0, Long.MAX_VALUE);
        if (res != 3000) {
            throw new RuntimeException("incorrect result: " + res);
        }
    }

    // Check IR has a loop nest when bounds are unknown, profile reports a short running loop but trap is taken
    @Test
    @IR(counts = { IRNode.COUNTED_LOOP, "1", IRNode.LOOP, "1", IRNode.OUTER_STRIP_MINED_LOOP, "1" })
    @IR(failOn = { IRNode.SHORT_RUNNING_LOOP_TRAP })
    public static int testLongLoopUnknownBoundsShortLoopFailedSpeculation(long start, long stop, long range) {
        int j = 0;
        for (long i = start; i < stop; i++) {
            volatileField = 42;
            Objects.checkIndex(i * (1024 * 1024), range); // max number of iteration of inner loop is roughly Integer.MAX_VALUE / 1024 / 1024
            j++;
        }
        return j;
    }

    @Run(test = "testLongLoopUnknownBoundsShortLoopFailedSpeculation")
    @Warmup(1)
    public static void testLongLoopUnknownBoundsShortLoopFailedSpeculation_runner(RunInfo info) {
        if (info.isWarmUp()) {
            for (int i = 0; i < 10_0000; i++) {
                int res = testLongLoopUnknownBoundsShortLoopFailedSpeculation(0, 100, Long.MAX_VALUE);
                if (res != 100) {
                    throw new RuntimeException("incorrect result: " + res);
                }
            }
            wb.enqueueMethodForCompilation(info.getTest(), CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION);
            if (!wb.isMethodCompiled(info.getTest())) {
                throw new RuntimeException("Should be compiled now");
            }
            for (int i = 0; i < 10; i++) {
                int res = testLongLoopUnknownBoundsShortLoopFailedSpeculation(0, 10_000, Long.MAX_VALUE);
                if (res != 10_000) {
                    throw new RuntimeException("incorrect result: " + res);
                }
            }
        } else {
            int res = testLongLoopUnknownBoundsShortLoopFailedSpeculation(0, 100, Long.MAX_VALUE);
            if (res != 100) {
                throw new RuntimeException("incorrect result: " + res);
            }
        }
    }

    // Check IR has a loop nest when bounds are known, is short running loop but trap was taken
    @Test
    @IR(counts = { IRNode.COUNTED_LOOP, "1"  })
    @IR(failOn = { IRNode.LOOP, IRNode.OUTER_STRIP_MINED_LOOP, IRNode.SHORT_RUNNING_LOOP_TRAP })
    public static int testLongLoopKnownBoundsShortLoopFailedSpeculation() {
        return testLongLoopKnownBoundsShortLoopFailedSpeculationHelper(0, 100);
    }

    @ForceInline
    private static int testLongLoopKnownBoundsShortLoopFailedSpeculationHelper(long start, long stop) {
        int j = 0;
        for (long i = start; i < stop; i++) {
            volatileField = 42;
            j++;
        }
        return j;
    }

    @Run(test = "testLongLoopKnownBoundsShortLoopFailedSpeculation")
    @Warmup(1)
    public static void testLongLoopKnownBoundsShortLoopFailedSpeculation_runner(RunInfo info) {
        if (info.isWarmUp()) {
            for (int i = 0; i < 10_0000; i++) {
                int res = testLongLoopKnownBoundsShortLoopFailedSpeculationHelper(0, 100);
                if (res != 100) {
                    throw new RuntimeException("incorrect result: " + res);
                }
            }
            for (int i = 0; i < 10; i++) {
                int res = testLongLoopKnownBoundsShortLoopFailedSpeculationHelper(0, 10_000);
                if (res != 10_000) {
                    throw new RuntimeException("incorrect result: " + res);
                }
            }
            for (int i = 0; i < 10_0000; i++) {
                int res = testLongLoopKnownBoundsShortLoopFailedSpeculation();
                if (res != 100) {
                    throw new RuntimeException("incorrect result: " + res);
                }
            }
        } else {
            int res = testLongLoopKnownBoundsShortLoopFailedSpeculation();
            if (res != 100) {
                throw new RuntimeException("incorrect result: " + res);
            }
        }
    }

    // Check range check can be eliminated by predication
    @Test
    @IR(counts = { IRNode.PREDICATE_TRAP, "1" })
    @IR(failOn = { IRNode.COUNTED_LOOP, IRNode.LOOP, IRNode.OUTER_STRIP_MINED_LOOP, IRNode.SHORT_RUNNING_LOOP_TRAP })
    public static void testLongLoopConstantBoundsPredication(long range) {
        for (long i = 0; i < 100; i++) {
            Objects.checkIndex(i, range);
        }
    }

    @Run(test = "testLongLoopConstantBoundsPredication")
    public static void testLongLoopConstantBoundsPredication_runner() {
        testLongLoopConstantBoundsPredication(100);
    }

    @Test
    @IR(counts = { IRNode.SHORT_RUNNING_LOOP_TRAP, "1", IRNode.PREDICATE_TRAP, "1" })
    @IR(failOn = { IRNode.COUNTED_LOOP, IRNode.LOOP, IRNode.OUTER_STRIP_MINED_LOOP })
    public static void testLongLoopUnknownBoundsShortLoopPredication(long start, long stop, long range) {
        for (long i = start; i < stop; i++) {
            Objects.checkIndex(i, range);
        }
    }

    @Run(test = "testLongLoopUnknownBoundsShortLoopPredication")
    @Warmup(10_000)
    public static void testLongLoopUnknownBoundsShortLoopPredication_runner() {
        testLongLoopUnknownBoundsShortLoopPredication(0, 100, 100);
    }

    // If scale too large, transformation can't happen
    static final long veryLargeScale = Integer.MAX_VALUE / 99;
    @Test
    @IR(counts = { IRNode.LOOP, "1", IRNode.PREDICATE_TRAP, "2"})
    @IR(failOn = { IRNode.COUNTED_LOOP, IRNode.OUTER_STRIP_MINED_LOOP, IRNode.SHORT_RUNNING_LOOP_TRAP })
    public static void testLongLoopConstantBoundsLargeScale(long range) {
        for (long i = 0; i < 100; i++) {
            Objects.checkIndex(veryLargeScale * i, range);
        }
    }

    @Run(test = "testLongLoopConstantBoundsLargeScale")
    public static void testLongLoopConstantBoundsLargeScale_runner() {
        testLongLoopConstantBoundsLargeScale(veryLargeScale * 100);
    }

    @Test
    @IR(counts = { IRNode.LOOP, "1", IRNode.PREDICATE_TRAP, "2"})
    @IR(failOn = { IRNode.COUNTED_LOOP, IRNode.OUTER_STRIP_MINED_LOOP, IRNode.SHORT_RUNNING_LOOP_TRAP })
    public static void testLongLoopUnknownBoundsShortLoopLargeScale(long start, long stop, long range) {
        for (long i = start; i < stop; i++) {
            Objects.checkIndex(veryLargeScale * i, range);
        }
    }

    @Run(test = "testLongLoopUnknownBoundsShortLoopLargeScale")
    @Warmup(10_000)
    public static void testLongLoopUnknownBoundsShortLoopLargeScale_runner() {
        testLongLoopUnknownBoundsShortLoopLargeScale(0, 100, veryLargeScale * 100);
    }

    // Check IR only has a counted loop when bounds are known and loop run for a short time (int loop case)
    @Test
    @IR(counts = { IRNode.COUNTED_LOOP, "1", IRNode.PREDICATE_TRAP, "1" })
    @IR(failOn = { IRNode.LOOP, IRNode.OUTER_STRIP_MINED_LOOP, IRNode.SHORT_RUNNING_LOOP_TRAP })
    public static void testIntLoopConstantBoundsShortLoop1(long range) {
        for (int i = 0; i < 100; i++) {
            Objects.checkIndex(i, range);
            volatileField = 42;
        }
    }

    @Run(test = "testIntLoopConstantBoundsShortLoop1")
    public static void testIntLoopConstantBoundsShortLoop1_runner() {
        testIntLoopConstantBoundsShortLoop1(100);
    }

    // Check IR only has a counted loop when bounds are unknown but profile reports a short running loop (int loop case)
    @Test
    @IR(counts = { IRNode.COUNTED_LOOP, "1", IRNode.SHORT_RUNNING_LOOP_TRAP, "1", IRNode.PREDICATE_TRAP, "1", IRNode.OUTER_STRIP_MINED_LOOP, "1" })
    @IR(failOn = { IRNode.LOOP })
    public static void testIntLoopUnknownBoundsShortLoop(int start, int stop, long range) {
        for (int i = start; i < stop; i++) {
            Objects.checkIndex(i, range);
            volatileField = 42;
        }
    }

    @Run(test = "testIntLoopUnknownBoundsShortLoop")
    @Warmup(10_000)
    public static void testIntLoopUnknownBoundsShortLoop_runner() {
        testIntLoopUnknownBoundsShortLoop(0, 100, 100);
    }

    // Same with unswitched loop
    @Test
    @IR(counts = { IRNode.COUNTED_LOOP, "2", IRNode.SHORT_RUNNING_LOOP_TRAP, "1", IRNode.PREDICATE_TRAP, "1", IRNode.OUTER_STRIP_MINED_LOOP, "2" })
    @IR(failOn = { IRNode.LOOP })
    public static void testIntLoopUnknownBoundsShortUnswitchedLoop(int start, int stop, long range, boolean flag) {
        for (int i = start; i < stop; i++) {
            if (flag) {
                Objects.checkIndex(i, range);
                volatileField = 42;
            } else {
                Objects.checkIndex(i, range);
                volatileField = 42;
            }
        }
    }

    @Run(test = "testIntLoopUnknownBoundsShortUnswitchedLoop")
    @Warmup(10_000)
    public static void testIntLoopUnknownBoundsShortUnswitchedLoop_runner() {
        testIntLoopUnknownBoundsShortUnswitchedLoop(0, 100, 100, true);
        testIntLoopUnknownBoundsShortUnswitchedLoop(0, 100, 100, false);
    }

    @Test
    @IR(counts = { IRNode.COUNTED_LOOP, "2", IRNode.SHORT_RUNNING_LOOP_TRAP, "1", IRNode.PREDICATE_TRAP, "1", IRNode.OUTER_STRIP_MINED_LOOP, "2" })
    @IR(failOn = { IRNode.LOOP })
    public static void testLongLoopUnknownBoundsShortUnswitchedLoop(long start, long stop, long range, boolean flag) {
        for (long i = start; i < stop; i++) {
            if (flag) {
                Objects.checkIndex(i, range);
                volatileField = 42;
            } else {
                Objects.checkIndex(i, range);
                volatileField = 42;
            }
        }
    }

    @Run(test = "testLongLoopUnknownBoundsShortUnswitchedLoop")
    @Warmup(10_000)
    public static void testLongLoopUnknownBoundsShortUnswitchedLoop_runner() {
        testLongLoopUnknownBoundsShortUnswitchedLoop(0, 100, 100, true);
        testLongLoopUnknownBoundsShortUnswitchedLoop(0, 100, 100, false);
    }

    @Test
    @IR(counts = { IRNode.COUNTED_LOOP, "1", IRNode.SHORT_RUNNING_LOOP_TRAP, "1", IRNode.OUTER_STRIP_MINED_LOOP, "1" })
    @IR(failOn = { IRNode.LOOP })
    public static int testLongLoopUnknownBoundsAddLimitShortLoop(int stop1, long stop2) {
        int j = 0;
        for (long i = 0; i < stop1 + stop2; i++) {
            volatileField = 42;
            j++;
        }
        return j;
    }

    @Run(test = "testLongLoopUnknownBoundsAddLimitShortLoop")
    @Warmup(10_000)
    public static void testLongLoopUnknownBoundsAddLimitShortLoop_runner() {
        int res = testLongLoopUnknownBoundsAddLimitShortLoop(100, 0);
        if (res != 100) {
            throw new RuntimeException("incorrect result: " + res);
        }
    }
}
