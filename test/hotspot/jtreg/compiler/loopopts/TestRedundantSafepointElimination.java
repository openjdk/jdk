/*
 * Copyright (c) 2025 Alibaba Group Holding Limited. All Rights Reserved.
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

/*
 * @test
 * @bug 8347499
 * @summary Tests that redundant safepoints can be eliminated in loops.
 * @library /test/lib /
 * @run main compiler.loopopts.TestRedundantSafepointElimination
 */
public class TestRedundantSafepointElimination {
    public static void main(String[] args) {
        TestFramework.run();
    }

    static int someInts0 = 1;
    static int someInts1 = 2;

    @DontInline
    private void empty() {}

    // Test for a top-level counted loop.
    // There should be a non-call safepoint in the loop.
    @Test
    @IR(counts = {IRNode.SAFEPOINT, "1"},
        phase = CompilePhase.AFTER_LOOP_OPTS)
    public int testTopLevelCountedLoop() {
        int sum = 0;
        for (int i = 0; i < 100000; i++) {
            sum += someInts0;
        }
        return sum;
    }

    // Test for a top-level counted loop with a call that dominates
    // the tail of the loop.
    // There should be no safepoint in the loop, because the call is
    // guaranteed to have a safepoint.
    @Test
    @IR(counts = {IRNode.SAFEPOINT, "0"},
        phase = CompilePhase.AFTER_LOOP_OPTS)
    public int testTopLevelCountedLoopWithDomCall() {
        int sum = 0;
        for (int i = 0; i < 100000; i++) {
            empty();
            sum += someInts0;
        }
        return sum;
    }

    // Test for a top-level uncounted loop.
    // There should be a non-call safepoint in the loop.
    @Test
    @IR(counts = {IRNode.SAFEPOINT, "1"},
        phase = CompilePhase.AFTER_LOOP_OPTS)
    public int testTopLevelUncountedLoop() {
        int sum = 0;
        for (int i = 0; i < 100000; i += someInts0) {
            sum += someInts1;
        }
        return sum;
    }

    // Test for a top-level uncounted loop with a call that dominates
    // the tail of the loop.
    // There should be no safepoint in the loop, because the call is
    // guaranteed to have a safepoint.
    // Before JDK-8347499, this test would fail due to C2 exiting
    // prematurely when encountering the local non-call safepoint.
    @Test
    @IR(counts = {IRNode.SAFEPOINT, "0"},
        phase = CompilePhase.AFTER_LOOP_OPTS)
    public int testTopLevelUncountedLoopWithDomCall() {
        int sum = 0;
        for (int i = 0; i < 100000; i += someInts0) {
            empty();
            sum += someInts1;
        }
        return sum;
    }

    // Test for nested loops, where the outer loop has a call that
    // dominates its own tail.
    // There should be only one safepoint in the inner loop.
    // Before JDK-8347499, this test would fail due to C2 exiting
    // prematurely when encountering the local non-call safepoint.
    @Test
    @IR(counts = {IRNode.SAFEPOINT, "1"},
        phase = CompilePhase.AFTER_LOOP_OPTS)
    public int testOuterLoopWithDomCall() {
        int sum = 0;
        for (int i = 0; i < 100; i += someInts0) {
            empty();
            for (int j = 0; j < 1000; j++) {
                sum += someInts1;
            }
        }
        return sum;
    }

    // Test for nested loops, where both the outer and inner loops
    // have a call that dominates their tails.
    // There should be no safepoint in both loops, because calls
    // within them are guaranteed to have a safepoint.
    // Before JDK-8347499, this test would fail due to C2 exiting
    // prematurely when encountering the local non-call safepoint.
    @Test
    @IR(counts = {IRNode.SAFEPOINT, "0"},
        phase = CompilePhase.AFTER_LOOP_OPTS)
    public int testOuterAndInnerLoopWithDomCall() {
        int sum = 0;
        for (int i = 0; i < 100; i += someInts0) {
            empty();
            for (int j = 0; j < 1000; j++) {
                empty();
                sum += someInts1;
            }
        }
        return sum;
    }

    // Test for nested loops, where the outer loop has a local
    // non-call safepoint.
    // There should be a safepoint in both loops.
    @Test
    @IR(counts = {IRNode.SAFEPOINT, "2"},
        phase = CompilePhase.AFTER_LOOP_OPTS)
    public int testOuterLoopWithLocalNonCallSafepoint() {
        int sum = 0;
        for (int i = 0; i < 100; i += someInts0) {
            for (int j = 0; j < 1000; j++) {
                sum += someInts1;
            }
        }
        return sum;
    }

    // Test for nested loops, where the outer loop has no local
    // safepoints, and it must preserve a non-local safepoint.
    // There should be two safepoints in the loop tree.
    @Test
    @IR(counts = {IRNode.SAFEPOINT, "2"},
        phase = CompilePhase.AFTER_LOOP_OPTS)
    public void testLoopNeedsToPreserveSafepoint() {
        int i = 0, stop;
        while (i < 1000) {
            stop = i + 10;
            while (i < stop) {
                i += 1;
            }
        }
    }
}
