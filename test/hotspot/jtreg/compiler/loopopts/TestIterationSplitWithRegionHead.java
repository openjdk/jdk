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
 * @requires vm.compiler2.enabled
 * @bug 8279837
 * @summary Tests infinite loop with region head in iteration split.
 * @run main/othervm -Xcomp -XX:-TieredCompilation
 *      -XX:CompileCommand=compileonly,compiler.loopopts.TestIterationSplitWithRegionHead::test
 *      -XX:CompileCommand=dontinline,compiler.loopopts.TestIterationSplitWithRegionHead::*
 *      compiler.loopopts.TestIterationSplitWithRegionHead
 */

package compiler.loopopts;

public class TestIterationSplitWithRegionHead {

    static boolean flagFalse = false;

    public static void main(String[] args) {
        test();
    }

    public static void test() {
        // 1) The loop tree is built. We find that nested loop N2 is an infinite loop and add a NeverBranch
        // to the inner loop to make it reachable. But the current loop tree does not have N2, yet. The
        // resulting loop tree is:
        //
        //   Loop: N0/N0  has_call has_sfpt
        //     Loop: N77/N121  has_call // N1 outer
        //       Loop: N77/N111  has_call sfpts={ 111 97 } // N1 inner
        //
        // 2) beautify_loops() finds that the outer loop head of N1 is shared and thus adds a new region
        // in merge_many_backedges(). As a result, the loop tree is built again. This time, the NeverBranch
        // in the inner loop of N2 allows that a loop tree can be built for it:
        //
        //   Loop: N0/N0  has_call has_sfpt
        //     Loop: N216/N213  limit_check profile_predicated predicated has_call sfpts={ 111 97 } // N1 shared loop head
        //     Loop: N196/N201  sfpts={ 201 } // N2 inner loop now discovered with the new NeverBranch
        //
        // However, a LoopNode is only added by beautify_loops() which won't be called until the next iteration of loop opts.
        // This means that we have a Region node (N196) as head in the loop tree which cannot be handled by iteration_split_impl()
        // resulting in an assertion failure.

        // Nested loop N1
        while (flagFalse) {
            while (dontInlineFalse()) {
            }
        }
        dontInlineFalse();

        // Nested loop N2
        while (flagFalse) {
            while (true) ;  // Detected as infinite inner loop by C2 -> NeverBranch added
        }
    }

    public static boolean dontInlineFalse() {
        return false;
    }
}
