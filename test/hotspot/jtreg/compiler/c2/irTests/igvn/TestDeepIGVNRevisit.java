/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
package compiler.c2.irTests.igvn;

import compiler.lib.ir_framework.*;

/*
 * @test
 * @bug 8375442
 * @summary Test deep IGVN revisit for RangeCheck elimination. Other deep-revisit node types
 *          (If, Load, CmpP, CountedLoopEnd, LongCountedLoopEnd) benefit in large methods
 *          but require graph complexity beyond this test.
 * @library /test/lib /
 * @run driver ${test.main.class}
 */
public class TestDeepIGVNRevisit {
    static boolean c1, c2, c3, c4;
    static volatile int volatileField;

    public static void main(String[] args) {
        TestFramework tf = new TestFramework();
        tf.setDefaultWarmup(0);
        tf.addFlags("-XX:+IgnoreUnrecognizedVMOptions",
                    "-XX:+AlwaysIncrementalInline",
                    "-XX:-PartialPeelLoop",
                    "-XX:-LoopUnswitching");
        tf.addScenarios(
            new Scenario(1, "-XX:-StressIGVN", "-XX:+UseDeepIGVNRevisit"),
            new Scenario(2, "-XX:+StressIGVN", "-XX:+UseDeepIGVNRevisit"),
            new Scenario(3, "-XX:-StressIGVN", "-XX:-UseDeepIGVNRevisit"),
            new Scenario(4, "-XX:+StressIGVN", "-XX:-UseDeepIGVNRevisit"));
        tf.start();
    }

    static void lateInline() {}

    // Deferred calls create separate LoadRange nodes for the two arr[idx]
    // accesses. After inlining, LoadRanges CSE but RangeCheck#2 is already
    // processed. Deep revisit re-processes it with matching range pointers.
    @Setup
    static Object[] setupRangeCheck() {
        return new Object[] { new int[100], 42 };
    }

    @Test
    @Arguments(setup = "setupRangeCheck")
    @IR(phase = CompilePhase.ITER_GVN2,
        applyIf = {"UseDeepIGVNRevisit", "true"},
        counts = {IRNode.RANGE_CHECK, "1"})
    @IR(phase = CompilePhase.ITER_GVN2,
        applyIf = {"UseDeepIGVNRevisit", "false"},
        counts = {IRNode.RANGE_CHECK, "2"})
    static int testRangeCheck(int[] arr, int idx) {
        int r = arr[idx];              // RangeCheck #1
        if (c1) { lateInline(); }
        if (c2) { lateInline(); }
        if (c3) { lateInline(); }
        if (c4) { lateInline(); }
        volatileField = r;
        r += arr[idx];                 // RangeCheck #2
        return r;
    }
}
