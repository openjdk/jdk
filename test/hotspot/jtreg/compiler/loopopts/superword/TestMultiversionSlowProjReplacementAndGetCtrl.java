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

/*
 * @test
 * @bug 8369898
 * @summary Bug in PhaseIdealLoop::create_new_if_for_multiversion, that messed up the
 *          _loop_or_ctrl data structure while doing SuperWord for a first loop, and
 *          then get_ctrl asserted for a second loop that was also SuperWord-ed in the
 *          same loop-opts-phase.
 * @run main/othervm
 *      -XX:CompileCommand=compileonly,*TestMultiversionSlowProjReplacementAndGetCtrl::test
 *      -XX:CompileCommand=exclude,*TestMultiversionSlowProjReplacementAndGetCtrl::dontinline
 *      -XX:-TieredCompilation
 *      -Xbatch
 *      compiler.loopopts.superword.TestMultiversionSlowProjReplacementAndGetCtrl
 * @run main compiler.loopopts.superword.TestMultiversionSlowProjReplacementAndGetCtrl
 */

package compiler.loopopts.superword;

public class TestMultiversionSlowProjReplacementAndGetCtrl {
    static final int N = 400;

    static void dontinline() {}

    static long test() {
        int x = 0;
        int arrayI[] = new int[N];
        byte[] arrayB = new byte[N];
        dontinline();
        // CallStaticJava for dontinline
        // -> memory Proj
        // -> it is used in both the k-indexed and j-indexed loops by their loads/stores.
        for (int k = 8; k < 92; ++k) {
            // Loop here is multiversioned, and eventually we insert an aliasing runtime check.
            // This means that a StoreN (with mem input Proj from above) has its ctrl changed
            // from the old multiversion_if_proj to a new region. We have to be careful to update
            // the _loop_or_ctrl side-table so that get_ctrl for StoreN is sane.
            //
            // Below is some nested loop material I could not reduce further. Maybe because
            // of loop-opts phase timing. Because we have to SuperWord the k-indexed loop
            // above in the same loop-opts-phase as the j-indexed loop below, so that they
            // have a shared _loop_or_ctrl data structure.
            int y = 6;
            while (--y > 0) {}
            for (long i = 1; i < 6; i++) {
                // I suspect that it is the two array references below that are SuperWord-ed,
                // and since we do not manage to statically prove they cannot overlap, we add
                // a speculative runtime check, i.e. multiversioning in this case.
                arrayI[0] += 1;
                arrayI[k] = 0;
                try {
                    x = 2 / k % y;
                } catch (ArithmeticException a_e) {
                }
            }
        }
        long sum = 0;
        for (int j = 0; j < arrayB.length; j++) {
            // Load below has mem input from Proj below dontinline
            // We look up to the mem input (Proj), and down to uses
            // that are Stores, checking in_bb on them, which calls
            // get_ctrl on that StoreN from the other loop above.
            sum += arrayB[j];
        }
        return sum;
    }

    public static void main(String[] strArr) {
        for (int i = 0; i < 1_000; i++) {
            test();
        }
    }
}
