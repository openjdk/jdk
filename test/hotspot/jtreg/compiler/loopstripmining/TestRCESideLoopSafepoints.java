/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8381505
 * @summary Range check elimination must preserve safepoints in long-running pre- and post-loops
 * @requires vm.compiler2.enabled
 * @library /test/lib
 * @run driver/timeout=240 compiler.loopstripmining.TestRCESideLoopSafepoints
 */

package compiler.loopstripmining;

import jdk.test.lib.Utils;
import jdk.test.lib.process.ProcessTools;

public class TestRCESideLoopSafepoints {
    public static void main(String[] args) throws Exception {
        ProcessTools.executeTestJava(
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:+SafepointTimeout",
                "-XX:+SafepointALot",
                "-XX:SafepointTimeoutDelay=" + Utils.adjustTimeout(100),
                "-XX:GuaranteedSafepointInterval=" + Utils.adjustTimeout(10),
                "-XX:-TieredCompilation",
                "-XX:-UseOnStackReplacement",
                "-XX:+UseCountedLoopSafepoints",
                "-XX:LoopStripMiningIter=1000",
                "-XX:LoopUnrollLimit=0",
                "-XX:CompileCommand=compileonly," + TestCase.class.getName() + "::test",
                "-Xcomp",
                TestCase.class.getName())
            .shouldHaveExitValue(0)
            .shouldNotContain("Timed out while spinning to reach a safepoint");
    }

    public static class TestCase {
        public static void main(String[] args) {
            test(Integer.MIN_VALUE, Integer.MIN_VALUE + 10_000, Integer.MAX_VALUE);
            test(Integer.MIN_VALUE, 0, Integer.MAX_VALUE);
            test(0, Integer.MAX_VALUE, Integer.MAX_VALUE);
        }

        private static void test(int start, int limit, int bound) {
            for (int i = start; i < limit; i++) {
                Thread.onSpinWait();
                java.lang.invoke.VarHandle.fullFence();
                if (i * 2 > bound) {
                    System.out.println(0);
                    break;
                }
            }
        }
    }
}
