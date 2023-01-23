/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8292088
 * @requires vm.compiler2.enabled
 * @summary Test that OuterStripMinedLoop and its CountedLoop are both removed after the removal of Opaque1 and 2 nodes
            which allows the loop backedge to be optimized out.
 * @run main/othervm -XX:LoopMaxUnroll=0 -Xcomp
 *                   -XX:CompileCommand=compileonly,compiler.c2.TestRemoveSingleIterationLoop::test*
 *                   -XX:CompileCommand=dontinline,compiler.c2.TestRemoveSingleIterationLoop::dontInline
 *                   compiler.c2.TestRemoveSingleIterationLoop
 * @run main/othervm -XX:LoopMaxUnroll=2 -Xcomp
 *                   -XX:CompileCommand=compileonly,compiler.c2.TestRemoveSingleIterationLoop::test*
 *                   -XX:CompileCommand=dontinline,compiler.c2.TestRemoveSingleIterationLoop::dontInline
 *                   compiler.c2.TestRemoveSingleIterationLoop
 */
package compiler.c2;

public class TestRemoveSingleIterationLoop {
    static int N = 400;
    static int x = 3;
    static int y = 3;
    static volatile int[] iArr = new int[N];

    public static void main(String[] args) {
        testKnownLimit();
        testUnknownLimit();
        testKnownLimit2();
        testFuzzer();
    }

    // Upper limit is known (i.e. type of iv phi for i is [-9999..499]). Unroll = 0 to trigger.
    private static void testKnownLimit() {
        int i = -10000;
        int limit = 500;

        // These two loops are only required to make sure that we only know that 'i' is 5 after the second loop after CCP.
        int a = 2;
        for (; a < 4; a *= 2);
        for (int b = 2; b < a; b++) {
            i = 5;
        }

        if (i < limit + 1) { // Required such that C2 knows that we are always entering the first loop.
            // Loop L1
            for (; i < limit; i++) {
                // IV_PHI_i = iv phi of i for this loop with type:
                // - Before CPP: [-10000..499]
                // - After CPP:  [5..499]
                y = 3;
            }

            int j = 6;
            // C2 parses the following loop as:
            // Loop head
            // body (where we do j--)
            // Loop exit check where we already applied j--: j > i - 1
            while (j > i - 1) {
                // IV_PHI_j = iv phi of j for this loop with type:
                // - Before CPP: [-9998..7]
                j--;
                iArr[23] = 3;
                // At this point i = IV_PHI_i + 1 because i was incremented once more before exiting loop L1.
                // In PhaseIdealLoop::reorg_offsets(), we identify such direct (pre-incremented) usages of an iv phi and
                // add an Opaque2 node to prevent loop-fallout uses. This lowers the register pressure. We replace the
                // direct phi usage in CmpI (annotated with types before CCP):
                //
                //      Phi               Phi (IV_PHI_i)   # [-10000..499]
                //       |                  |
                //       |                AddI (+1)        # [-9999..500]
                //       |                 |
                //       |              Opaque2            # int
                //       |      ====>      |
                //       |                AddI (-1)        # int
                //       |                 |
                //       |               CastII            # [-10000..499]
                //       |                 |
                //      CmpI              CmpI             # j > i - 1 = IV_PHI_j - 1 > CastII (actually IV_PHI_i) = [-10000..5] > [-10000..499]
                //
                //
                // After CCP, the type of the iv phi IV_PHI_i improves to [5..499] while the type of the CastII does not
                // because the Opaque2 node blocks the type update. When removing the Opaque2 node, we find that the
                // loop only runs for a single time and does not take the backedge:
                //
                //     [-10000..5] > [5..499] is false
                //
                // However, we only remove Opaque2 nodes in macro expansion where we also adjust the strip mined loop:
                // We copy the bool node from the CountedLoopEnd to the OuterStripMinedLoopEnd node and adjust the
                // loop exit check of the CountedLoopEnd in such a way that C2 is not able to prove that the loop
                // is only run once. But the OuterStripMinedLoop can now be removed due to the removed Opaque2 nodes
                // and we end up with a CountedLoop node that is strip mined but has no OuterStripMined loop. This
                // results in an assertion failure later when reshaping the graph.
            }
        }
    }

    // Upper limit is not known (i.e. type of iv phi for i is [-9999..max-1]). Unroll = 0 to trigger.
    private static void testUnknownLimit() {
        int i = -10000;
        int limit = x;

        // These two loops are required to make sure that we only know after CCP that 'i' is 1 after the second loop.
        int a = 2;
        for (; a < 4; a *= 2);
        for (int b = 2; b < a; b++) {
            i = 0;
        }
        if (i + 1 < limit) {
            i++;
            for (; i < limit; i++) {
                y = 3;
            }
            int t = 2;
            while (t > i - 1) {
                t--;
                iArr[23] = 3;
            }
        }
    }

    // Upper limit is known. Unroll = 2 to trigger.
    static int testKnownLimit2() {
        int i = -10000, j;
        int[][] iArr1 = new int[N][N];

        // These two loops are required to make sure that we only know after CCP that 'i' is 1 after the second loop.
        int a = 2;
        for (; a < 4; a *= 2);
        for (int b = 2; b < a; b++) {
            i = 1;
        }

        while (++i < 318) {
            dontInline();
        }

        for (j = 6; j > i; j--) {
            // Type of i before CCP: -9999..317
            // Type of i after CCP: 2..317
            iArr[1] = 25327;
        }

        // This loop is required to trigger the assertion failure.
        for (int y = 0; y < iArr1.length; y++) {
            dontInline(iArr1[2]);
        }
        return i;
    }

    // Reduced original fuzzer test. Unroll = 2 to trigger.
    static int testFuzzer() {
        int i = 1, j, iArr1[][] = new int[N][N];
        while (++i < 318) {
            dontInline();
            for (j = 5; j > i; j--) {
                iArr[1] = 25327;
            }
        }

        // Some more code that is required to trigger the assertion failure.
        for (int y = 0; y < iArr1.length; y++) {
            dontInline(iArr1[2]);
        }
        return i;
    }

    static void dontInline() {}

    static void dontInline(int[] iArr) {}
}
