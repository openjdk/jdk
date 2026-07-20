/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package compiler.loopopts.superword;

import compiler.lib.ir_framework.*;

/*
 * @test
 * @bug 8350756
 * @summary Test case where the multiversion fast_loop disappears, and we should
 *          constant fold the multiversion_if, to remove the slow_loop.
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMultiversionRemoveUselessSlowLoop
 */

public class TestMultiversionRemoveUselessSlowLoop {

    public static void main(String[] args) {
        TestFramework framework = new TestFramework(TestMultiversionRemoveUselessSlowLoop.class);
        // No traps means we cannot use the predicates version for SuperWord / AutoVectorization,
        // and instead use multiversioning directly.
        framework.addFlags("-XX:-TieredCompilation", "-XX:PerMethodTrapLimit=0");
        framework.setDefaultWarmup(0); // simulates Xcomp
        framework.start();
    }

    public static final int SIZE = 20;
    public static final int[] a = new int[SIZE];
    public static final int[] b = new int[SIZE];
    public static final int SIZE2 = 10_000;
    public static final int[] a2 = new int[SIZE2];
    public static final int[] b2 = new int[SIZE2];

    @Test
    @IR(counts = {"pre .* multiversion_fast",  "= 2", // regular pre-main-post for both loops
                  "main .* multiversion_fast", "= 2",
                  "post .* multiversion_fast", "= 2",
                  "multiversion_delayed_slow", "= 2", // both have the delayed slow_loop
                  "multiversion",              "= 8", // nothing unexpected
                  IRNode.OPAQUE_MULTIVERSIONING, "= 2"}, // Both multiversion_if are still here
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"},
        phase = CompilePhase.PHASEIDEALLOOP1)
    @IR(counts = {"pre .* multiversion_fast",  "= 2",
                  "main .* multiversion_fast", "= 1", // The first main loop is fully unrolled
                  "post .* multiversion_fast", "= 3", // the second loop is vectorized, and has a vectorized post loop
                  "multiversion_delayed_slow", "= 1", // As a consequence of the first main loop being removed, we constant fold the multiversion_if
                  "multiversion",              "= 7", // nothing unexpected
                  IRNode.OPAQUE_MULTIVERSIONING, "= 1"}, // The multiversion_if of the first loop was constant folded, because the main loop disappeared.
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"},
        phase = CompilePhase.PHASEIDEALLOOP_ITERATIONS)
    @IR(counts = {"pre .* multiversion_fast.*", ">= 1", // In some cases, the pre loop of the first loop also disappears because it only has a single iteration
                  "pre .* multiversion_fast.*", "<= 2", // but not in other cases the pre loop of the first loop remains.
                  "main .* multiversion_fast", "= 1",
                  "post .* multiversion_fast", "= 3",
                  "multiversion_delayed_slow", "= 0", // The second loop's multiversion_if was also not used, so it is constant folded after loop opts.
                  "multiversion",              ">= 5", // nothing unexpected
                  "multiversion",              "<= 6", // nothing unexpected
                  IRNode.OPAQUE_MULTIVERSIONING, "= 0"}, // After loop-opts, we also constant fold the multiversion_if of the second loop, as it is unused.
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"},
        phase = CompilePhase.PRINT_IDEAL)
    public static void testIR() {
        // This loop is short, and the multiversion_fast main loop eventuall is fully unrolled.
        for (int i = 0; i < SIZE; i++) {
            a[i] = b[i];
        }
        // We take this second loop with a larger limit so that loop opts keeps going once the loop
        // above is fully optimized. It also gives us a reference where the main loop of the
        // multiverion fast_loop does not disappear.
        for (int i = 0; i < SIZE2; i++) {
            a2[i] = b2[i];
        }
    }

    static long instanceCount;
    static int iFld;
    static int iFld1;

    // The inner loop is Multiversioned, then PreMainPost and Unroll.
    // Eventually, both the fast and slow loops (pre main and post) disappear,
    // and leave us with a simple if-diamond using the multiversion_if.
    //
    // Verification code in PhaseIdealLoop::conditional_move finds this diamond
    // and expects a Bool but gets an OpaqueMultiversioning instead.
    //
    // If we let the multiversion_if constant fold soon after the main fast loop
    // disappears, then this issue does not occur any more.
    @Test
    public static void testCrash() {
        boolean b2 = true;
        for (int i = 0; i < 1000; i++) {
            for (int i21 = 82; i21 > 9; --i21) {
                if (b2)
                    break;
                iFld1 = iFld;
                b2 = true;
            }
            instanceCount = iFld1;
        }
    }

    class Unloaded {
        static void unloaded() {}
    }
    static int f;

    // The outer loop is eventually Multiversioned, then PreMainPost and Unroll.
    // Then the loops disappear during IGVN, and in the next loop-opts phase, the
    // OpaqueMultiversioning is marked useless, but then we already run
    // PhaseIdealLoop::conditional_move before the next IGVN round, and find a
    // useless OpaqueMultiversioning instead of a BoolNode.
    @Test
    @Arguments(values = { Argument.NUMBER_42 })
    static void testCrash2(int y) {
        int x = 53446;
        for (int i = 12; i < 376; i++) {
            if (x != 0) {
                // Uncommon trap because the class is not yet loaded.
                Unloaded.unloaded();
            }
            for (int k = 1; k < 4; k++) {
                y += 1;
            }
            x += f;
        }
    }
}
