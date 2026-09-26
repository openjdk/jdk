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
import compiler.lib.ir_framework.Warmup;
import jdk.test.lib.Asserts;

import java.util.Arrays;

/*
 * @test
 * @bug 8381505
 * @summary Keep only the pre- and post-loop safepoints required by range check elimination
 * @library /test/lib /
 * @requires vm.compiler2.enabled
 * @run driver ${test.main.class}
 */

public class TestRCESideLoopSafepointNodes {
    private static final int[] VALUES = new int[256];

    public static void main(String[] args) {
        runWithStripMiningIter(1);
        runWithStripMiningIter(1000);
    }

    private static void runWithStripMiningIter(int iterations) {
        TestFramework.runWithFlags("-XX:-TieredCompilation",
                                   "-XX:+UseCountedLoopSafepoints",
                                   "-XX:LoopStripMiningIter=" + iterations,
                                   "-XX:LoopUnrollLimit=0",
                                   "-DTest=test,testRejectedRCE");
        TestFramework.runWithFlags("-XX:-TieredCompilation",
                                   "-XX:+UseCountedLoopSafepoints",
                                   "-XX:LoopStripMiningIter=" + iterations,
                                   "-XX:LoopUnrollLimit=1000",
                                   "-XX:LoopMaxUnroll=4",
                                   "-DTest=testDelayedRCE,testSunkStores");
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "3"})
    @IR(applyIf = {"LoopStripMiningIter", "1"},
        counts = {IRNode.SAFEPOINT, "3"},
        failOn = {IRNode.OUTER_STRIP_MINED_LOOP})
    @IR(applyIf = {"LoopStripMiningIter", "> 1"},
        counts = {IRNode.OUTER_STRIP_MINED_LOOP, "1",
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

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "3", IRNode.SAFEPOINT, "3"})
    public static int testDelayedRCE(int start, int limit, int initialBound, int bound) {
        int sum = 0;
        int b0 = initialBound, b1 = initialBound, b2 = initialBound;
        for (int i = start; i < limit; i++) {
            Thread.onSpinWait();
            java.lang.invoke.VarHandle.fullFence();
            if (i * 2 > b0) {
                break;
            }
            sum += VALUES[i & 255];
            // The bound becomes invariant only after unrolling exposes the assignments.
            b0 = b1;
            b1 = b2;
            b2 = bound;
        }
        return sum;
    }

    @Run(test = "testDelayedRCE")
    @Warmup(1)
    public static void testDelayedRCERunner() {
        Arrays.fill(VALUES, 1);
        Asserts.assertEQ(testDelayedRCE(Integer.MIN_VALUE, Integer.MIN_VALUE + 100,
                                      Integer.MAX_VALUE, Integer.MAX_VALUE), 100);
        Asserts.assertEQ(testDelayedRCE(0, 100, Integer.MAX_VALUE, 50), 26);
    }

    private static class Cell {
        int value;
    }

    @Test
    @IR(counts = {IRNode.SAFEPOINT, "1"})
    private static int testSunkStores(Cell first, Cell second, int limit) {
        int sum = 0;
        for (int i = 0; i < limit; i++) {
            sum += VALUES[i & 255];
            first.value = i;
            second.value = -i;
        }
        return sum;
    }

    @Run(test = "testSunkStores")
    private static void testSunkStoresRunner() {
        Arrays.fill(VALUES, 1);
        Cell first = new Cell();
        Cell second = new Cell();
        Asserts.assertEQ(testSunkStores(first, second, 100), 100);
        Asserts.assertEQ(first.value, 99);
        Asserts.assertEQ(second.value, -99);
        Asserts.assertEQ(testSunkStores(first, first, 101), 101);
        Asserts.assertEQ(first.value, -100);
    }
}
