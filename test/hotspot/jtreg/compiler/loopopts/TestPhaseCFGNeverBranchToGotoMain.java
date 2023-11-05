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
 * @bug 8296389
 * @summary Peeling of Irreducible loop can lead to NeverBranch being visited from either side
 * @run main/othervm -Xcomp -XX:-TieredCompilation -XX:PerMethodTrapLimit=0
 *      -XX:CompileCommand=compileonly,TestPhaseCFGNeverBranchToGotoMain::test
 *      TestPhaseCFGNeverBranchToGotoMain
 */

/*
 * @test
 * @bug 8296389
 * @compile TestPhaseCFGNeverBranchToGoto.jasm
 * @summary Peeling of Irreducible loop can lead to NeverBranch being visited from either side
 * @run main/othervm -Xcomp -XX:-TieredCompilation -XX:PerMethodTrapLimit=0
 *      -XX:CompileCommand=compileonly,TestPhaseCFGNeverBranchToGoto::test
 *      TestPhaseCFGNeverBranchToGoto
 */


public class TestPhaseCFGNeverBranchToGotoMain {
    public static void main (String[] args) {
        test(false, false);
    }

    public static void test(boolean flag1, boolean flag2) {
        if (flag1) { // runtime check, avoid infinite loop
            int a = 77;
            int b = 0;
            do { // empty loop
                a--;
                b++;
            } while (a > 0);
            // a == 0, b == 77 - after first loop-opts phase
            int p = 0;
            for (int i = 0; i < 4; i++) {
                if ((i % 2) == 0) {
                    p = 1;
                }
            }
            // p == 1 - after second loop-opts phase (via unrolling)
            // in first loop-opts phase we have 2x unrolling, 4x after second
            int x = 1;
            if (flag2) {
                x = 3;
            } // have region here, so that NeverBranch does not get removed
            do { // infinite loop
                do {
                    // NeverBranch inserted here, during loop-opts 1
                    x *= 2;
                    if (p == 0) { // reason for peeling in loop-opts 1
                        // after we know that p == 1, we lose this exit
                        break;
                    }
                    // once we know that b == 77, we lose exit
            } while (b == 77);
            // after we lost both exits above, this loop gets cut off
            int y = 5;
                do {
                    y *= 3;
                } while (b == 77);
            } while (true);
        }
    }
}
