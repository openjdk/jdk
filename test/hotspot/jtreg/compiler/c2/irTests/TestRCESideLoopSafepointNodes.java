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

package compiler.c2.irTests;

import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.Run;
import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.TestFramework;

/*
 * @test
 * @bug 8381505
 * @summary Keep only the pre- and post-loop safepoints required by range check elimination
 * @library /test/lib /
 * @requires vm.compiler2.enabled
 * @run driver compiler.c2.irTests.TestRCESideLoopSafepointNodes
 */

public class TestRCESideLoopSafepointNodes {
    public static void main(String[] args) {
        runWithStripMiningIter(1);
        runWithStripMiningIter(1000);
    }

    private static void runWithStripMiningIter(int iterations) {
        TestFramework.runWithFlags("-XX:-TieredCompilation",
                                   "-XX:+UseCountedLoopSafepoints",
                                   "-XX:LoopStripMiningIter=" + iterations,
                                   "-XX:LoopUnrollLimit=0");
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "3"})
    @IR(applyIf = {"LoopStripMiningIter", "1"},
        counts = {IRNode.SAFEPOINT, "3"},
        failOn = {IRNode.OUTER_STRIP_MINED_LOOP})
    @IR(applyIf = {"LoopStripMiningIter", "> 1"},
        counts = {IRNode.OUTER_STRIP_MINED_LOOP, "3",
                  IRNode.SAFEPOINT, "3"})
    private static void test(int start, int limit, int bound) {
        for (int i = start; i < limit; i++) {
            Thread.onSpinWait();
            java.lang.invoke.VarHandle.fullFence();
            if (i * 2 > bound) {
                break;
            }
        }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "3"})
    @IR(applyIf = {"LoopStripMiningIter", "1"},
        counts = {IRNode.SAFEPOINT, "1"},
        failOn = {IRNode.OUTER_STRIP_MINED_LOOP})
    @IR(applyIf = {"LoopStripMiningIter", "> 1"},
        counts = {IRNode.OUTER_STRIP_MINED_LOOP, "1",
                  IRNode.SAFEPOINT, "1"})
    private static void testRejectedRCE(int start, int limit, int bound) {
        for (int i = start; i < limit; i++) {
            Thread.onSpinWait();
            java.lang.invoke.VarHandle.fullFence();
            if (Integer.compareUnsigned(i * 2, bound) > 0) {
                break;
            }
        }
    }

    @Run(test = "test")
    private static void testRunner() {
        test(Integer.MIN_VALUE, Integer.MIN_VALUE + 10_000, Integer.MAX_VALUE);
    }

    @Run(test = "testRejectedRCE")
    private static void testRejectedRCERunner() {
        testRejectedRCE(0, 10_000, -1);
    }
}
