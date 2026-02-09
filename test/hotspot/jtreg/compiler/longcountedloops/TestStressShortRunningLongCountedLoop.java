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

/*
 * @test
 * @bug 8342692
 * @summary C2: long counted loop/long range checks: don't create loop-nest for short running loops
 * @library /test/lib /
 * @run driver compiler.longcountedloops.TestStressShortRunningLongCountedLoop
 */

public class TestStressShortRunningLongCountedLoop {
    private static volatile int volatileField;

    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:LoopMaxUnroll=0", "-XX:+IgnoreUnrecognizedVMOptions", "-XX:+StressShortRunningLongLoop");
        TestFramework.runWithFlags("-XX:LoopMaxUnroll=0",  "-XX:+IgnoreUnrecognizedVMOptions", "-XX:-StressShortRunningLongLoop");
    }

    @Test
    @IR(applyIf = { "StressShortRunningLongLoop", "true" }, counts = { IRNode.COUNTED_LOOP, "1", IRNode.SHORT_RUNNING_LOOP_TRAP, "1", IRNode.OUTER_STRIP_MINED_LOOP, "1" })
    @IR(applyIf = { "StressShortRunningLongLoop", "true" }, failOn = { IRNode.LOOP })
    @IR(applyIf = { "StressShortRunningLongLoop", "false" }, counts = { IRNode.COUNTED_LOOP, "1", IRNode.LOOP, "1", IRNode.OUTER_STRIP_MINED_LOOP, "1" })
    @IR(applyIf = { "StressShortRunningLongLoop", "false" }, failOn = { IRNode.SHORT_RUNNING_LOOP_TRAP })
    public static int testLongLoopUnknownBoundsShortLoop(long start, long stop) {
        int j = 0;
        for (long i = start; i < stop; i++) {
            volatileField = 42;
            j++;
        }
        return j;
    }

    @Run(test = "testLongLoopUnknownBoundsShortLoop")
    @Warmup(0)
    public static void testLongLoopUnknownBoundsShortLoop_runner() {
        int res = testLongLoopUnknownBoundsShortLoop(0, 100);
        if (res != 100) {
            throw new RuntimeException("incorrect result: " + res);
        }
    }
}
