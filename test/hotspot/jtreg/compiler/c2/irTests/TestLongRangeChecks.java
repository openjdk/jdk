/*
 * Copyright (c) 2021, 2022, 2025 Red Hat, Inc. All rights reserved.
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package compiler.c2.irTests;

import compiler.lib.ir_framework.*;
import java.util.Objects;

/*
 * @test
 * @bug 8259609 8276116 8311932
 * @summary C2: optimize long range checks in long counted loops
 * @library /test/lib /
 * @requires vm.compiler2.enabled
 * @run driver compiler.c2.irTests.TestLongRangeChecks
 */

public class TestLongRangeChecks {
    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:-ShortRunningLongLoop", "-XX:+TieredCompilation", "-XX:-UseCountedLoopSafepoints", "-XX:LoopUnrollLimit=0");
        TestFramework.runWithFlags("-XX:-ShortRunningLongLoop", "-XX:+TieredCompilation", "-XX:+UseCountedLoopSafepoints", "-XX:LoopStripMiningIter=1", "-XX:LoopUnrollLimit=0");
        TestFramework.runWithFlags("-XX:-ShortRunningLongLoop", "-XX:+TieredCompilation", "-XX:+UseCountedLoopSafepoints", "-XX:LoopStripMiningIter=1000", "-XX:LoopUnrollLimit=0");
        TestFramework.runWithFlags("-XX:+TieredCompilation", "-XX:-UseCountedLoopSafepoints", "-XX:LoopUnrollLimit=0");
        TestFramework.runWithFlags("-XX:+TieredCompilation", "-XX:+UseCountedLoopSafepoints", "-XX:LoopStripMiningIter=1", "-XX:LoopUnrollLimit=0");
        TestFramework.runWithFlags("-XX:+TieredCompilation", "-XX:+UseCountedLoopSafepoints", "-XX:LoopStripMiningIter=1000", "-XX:LoopUnrollLimit=0");
    }


    @Test
    @IR(applyIf = { "ShortRunningLongLoop", "false" }, counts = { IRNode.LOOP, "1" })
    @IR(applyIf = { "ShortRunningLongLoop", "true" }, failOn = IRNode.LOOP)
    @IR(failOn = { IRNode.COUNTED_LOOP})
    public static void testStridePosScalePos(long start, long stop, long length, long offset) {
        final long scale = 1;
        final long stride = 1;

        // Loop is first transformed into a loop nest, long range
        // check into an int range check, the range check is hoisted
        // and the inner counted loop becomes empty so is optimized
        // out.
        for (long i = start; i < stop; i += stride) {
            Objects.checkIndex(scale * i + offset, length);
        }
    }

    @Run(test = "testStridePosScalePos")
    private void testStridePosScalePos_runner() {
        testStridePosScalePos(0, 100, 100, 0);
    }

    @Test
    @IR(applyIf = { "ShortRunningLongLoop", "false" }, counts = { IRNode.LOOP, "1" })
    @IR(applyIf = { "ShortRunningLongLoop", "true" }, failOn = IRNode.LOOP)
    @IR(failOn = { IRNode.COUNTED_LOOP})
    public static void testStridePosScalePosInIntLoop1(int start, int stop, long length, long offset) {
        final long scale = 2;
        final int stride = 1;

        // Same but with int loop
        for (int i = start; i < stop; i += stride) {
            Objects.checkIndex(scale * i + offset, length);
        }
    }

    @Run(test = "testStridePosScalePosInIntLoop1")
    private void testStridePosScalePosInIntLoop1_runner() {
        testStridePosScalePosInIntLoop1(0, 100, 200, 0);
    }

    @Test
    @IR(applyIf = { "ShortRunningLongLoop", "false" }, counts = { IRNode.LOOP, "1" })
    @IR(applyIf = { "ShortRunningLongLoop", "true" }, failOn = IRNode.LOOP)
    @IR(failOn = { IRNode.COUNTED_LOOP})
    public static void testStridePosScalePosInIntLoop2(int start, int stop, long length, long offset) {
        final int scale = 2;
        final int stride = 1;

        // Same but with int loop
        for (int i = start; i < stop; i += stride) {
            Objects.checkIndex(scale * i + offset, length);
        }
    }

    @Run(test = "testStridePosScalePosInIntLoop2")
    private void testStridePosScalePosInIntLoop2_runner() {
        testStridePosScalePosInIntLoop2(0, 100, 200, 0);
    }

    @Test
    @IR(applyIf = { "ShortRunningLongLoop", "false" }, counts = { IRNode.LOOP, "1" })
    @IR(applyIf = { "ShortRunningLongLoop", "true" }, failOn = IRNode.LOOP)
    @IR(failOn = { IRNode.COUNTED_LOOP})
    public static void testStrideNegScaleNeg(long start, long stop, long length, long offset) {
        final long scale = -1;
        final long stride = 1;
        for (long i = stop; i > start; i -= stride) {
            Objects.checkIndex(scale * i + offset, length);
        }
    }

    @Run(test = "testStrideNegScaleNeg")
    private void testStrideNegScaleNeg_runner() {
        testStrideNegScaleNeg(0, 100, 100, 100);
    }

    @Test
    @IR(applyIf = { "ShortRunningLongLoop", "false" }, counts = { IRNode.LOOP, "1" })
    @IR(applyIf = { "ShortRunningLongLoop", "true" }, failOn = IRNode.LOOP)
    @IR(failOn = { IRNode.COUNTED_LOOP})
    public static void testStrideNegScaleNegInIntLoop1(int start, int stop, long length, long offset) {
        final long scale = -2;
        final int stride = 1;

        for (int i = stop; i > start; i -= stride) {
            Objects.checkIndex(scale * i + offset, length);
        }
    }

    @Run(test = "testStrideNegScaleNegInIntLoop1")
    private void testStrideNegScaleNegInIntLoop1_runner() {
        testStrideNegScaleNegInIntLoop1(0, 100, 200, 200);
    }

    @Test
    @IR(applyIf = { "ShortRunningLongLoop", "false" }, counts = { IRNode.LOOP, "1" })
    @IR(applyIf = { "ShortRunningLongLoop", "true" }, failOn = IRNode.LOOP)
    @IR(failOn = { IRNode.COUNTED_LOOP})
    public static void testStrideNegScaleNegInIntLoop2(int start, int stop, long length, long offset) {
        final int scale = -2;
        final int stride = 1;

        for (int i = stop; i > start; i -= stride) {
            Objects.checkIndex(scale * i + offset, length);
        }
    }

    @Run(test = "testStrideNegScaleNegInIntLoop2")
    private void testStrideNegScaleNegInIntLoop2_runner() {
        testStrideNegScaleNegInIntLoop2(0, 100, 200, 200);
    }

    @Test
    @IR(applyIf = { "ShortRunningLongLoop", "false" }, counts = { IRNode.LOOP, "1" })
    @IR(applyIf = { "ShortRunningLongLoop", "true" }, failOn = IRNode.LOOP)
    @IR(failOn = { IRNode.COUNTED_LOOP})
    public static void testStrideNegScalePos(long start, long stop, long length, long offset) {
        final long scale = 1;
        final long stride = 1;
        for (long i = stop-1; i >= start; i -= stride) {
            Objects.checkIndex(scale * i + offset, length);
        }
    }

    @Run(test = "testStrideNegScalePos")
    private void testStrideNegScalePos_runner() {
        testStrideNegScalePos(0, 100, 100, 0);
    }

    @Test
    @IR(applyIf = { "ShortRunningLongLoop", "false" }, counts = { IRNode.LOOP, "1" })
    @IR(applyIf = { "ShortRunningLongLoop", "true" }, failOn = IRNode.LOOP)
    @IR(failOn = { IRNode.COUNTED_LOOP})
    public static void testStrideNegScalePosInIntLoop1(int start, int stop, long length, long offset) {
        final long scale = 2;
        final int stride = 1;
        for (int i = stop-1; i >= start; i -= stride) {
            Objects.checkIndex(scale * i + offset, length);
        }
    }

    @Run(test = "testStrideNegScalePosInIntLoop1")
    private void testStrideNegScalePosInIntLoop1_runner() {
        testStrideNegScalePosInIntLoop1(0, 100, 200, 0);
    }

    @Test
    @IR(applyIf = { "ShortRunningLongLoop", "false" }, counts = { IRNode.LOOP, "1" })
    @IR(applyIf = { "ShortRunningLongLoop", "true" }, failOn = IRNode.LOOP)
    @IR(failOn = { IRNode.COUNTED_LOOP})
    public static void testStrideNegScalePosInIntLoop2(int start, int stop, long length, long offset) {
        final int scale = 2;
        final int stride = 1;
        for (int i = stop-1; i >= start; i -= stride) {
            Objects.checkIndex(scale * i + offset, length);
        }
    }

    @Run(test = "testStrideNegScalePosInIntLoop2")
    private void testStrideNegScalePosInIntLoop2_runner() {
        testStrideNegScalePosInIntLoop2(0, 100, 200, 0);
    }

    @Test
    @IR(applyIf = { "ShortRunningLongLoop", "false" }, counts = { IRNode.LOOP, "1" })
    @IR(applyIf = { "ShortRunningLongLoop", "true" }, failOn = IRNode.LOOP)
    @IR(failOn = { IRNode.COUNTED_LOOP})
    public static void testStridePosScaleNeg(long start, long stop, long length, long offset) {
        final long scale = -1;
        final long stride = 1;
        for (long i = start; i < stop; i += stride) {
            Objects.checkIndex(scale * i + offset, length);
        }
    }

    @Run(test = "testStridePosScaleNeg")
    private void testStridePosScaleNeg_runner() {
        testStridePosScaleNeg(0, 100, 100, 99);
    }

    @Test
    @IR(applyIf = { "ShortRunningLongLoop", "false" }, counts = { IRNode.LOOP, "1" })
    @IR(applyIf = { "ShortRunningLongLoop", "true" }, failOn = IRNode.LOOP)
    @IR(failOn = { IRNode.COUNTED_LOOP})
    public static void testStridePosScaleNegInIntLoop1(int start, int stop, long length, long offset) {
        final long scale = -2;
        final int stride = 1;
        for (int i = start; i < stop; i += stride) {
            Objects.checkIndex(scale * i + offset, length);
        }
    }

    @Run(test = "testStridePosScaleNegInIntLoop1")
    private void testStridePosScaleNegInIntLoop1_runner() {
        testStridePosScaleNegInIntLoop1(0, 100, 200, 198);
    }

    @Test
    @IR(applyIf = { "ShortRunningLongLoop", "false" }, counts = { IRNode.LOOP, "1" })
    @IR(applyIf = { "ShortRunningLongLoop", "true" }, failOn = IRNode.LOOP)
    @IR(failOn = { IRNode.COUNTED_LOOP})
    public static void testStridePosScaleNegInIntLoop2(int start, int stop, long length, long offset) {
        final int scale = -2;
        final int stride = 1;
        for (int i = start; i < stop; i += stride) {
            Objects.checkIndex(scale * i + offset, length);
        }
    }

    @Run(test = "testStridePosScaleNegInIntLoop2")
    private void testStridePosScaleNegInIntLoop2_runner() {
        testStridePosScaleNegInIntLoop2(0, 100, 200, 198);
    }

    @Test
    @IR(counts = { IRNode.LONG_COUNTED_LOOP, "1" })
    @IR(failOn = { IRNode.COUNTED_LOOP, IRNode.LOOP })
    public static void testStridePosScalePosShortLoop(long start, long stop, long length, long offset) {
        final long scale = 1;
        final long stride = 1;

        // Loop runs for too few iterations. Transforming it wouldn't pay off.
        for (long i = start; i < stop; i += stride) {
            Objects.checkIndex(scale * i + offset, length);
        }
    }

    @Run(test = "testStridePosScalePosShortLoop")
    private void testStridePosScalePosShortLoop_runner() {
        testStridePosScalePosShortLoop(0, 2, 2, 0);
    }

    @Test
    @IR(counts = { IRNode.COUNTED_LOOP, "1" })
    @IR(failOn = { IRNode.LOOP })
    public static void testStridePosScalePosInIntLoopShortLoop1(int start, int stop, long length, long offset) {
        final long scale = 2;
        final int stride = 1;

        // Same but with int loop
        for (int i = start; i < stop; i += stride) {
            Objects.checkIndex(scale * i + offset, length);
        }
    }

    @Run(test = "testStridePosScalePosInIntLoopShortLoop1")
    private void testStridePosScalePosInIntLoopShortLoop1_runner() {
        testStridePosScalePosInIntLoopShortLoop1(0, 2, 4, 0);
    }

    @Test
    @IR(counts = { IRNode.COUNTED_LOOP, "1" })
    @IR(failOn = { IRNode.LOOP })
    public static void testStridePosScalePosInIntLoopShortLoop2(long length, long offset) {
        final long scale = 2;
        final int stride = 1;

        // Same but with int loop
        for (int i = 0; i < 3; i += stride) {
            Objects.checkIndex(scale * i + offset, length);
        }
    }

    @Run(test = "testStridePosScalePosInIntLoopShortLoop2")
    private void testStridePosScalePosInIntLoopShortLoop2_runner() {
        testStridePosScalePosInIntLoopShortLoop2(6, 0);
    }
}
