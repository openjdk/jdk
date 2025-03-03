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

/*
 * @test
 * @bug 8350756
 * @summary Test case where the multiversion fast_loop disappears, and we should
 *          constant fold the multiversion_if, to remove the slow_loop.
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMultiversionRemoveUselessSlowLoop
 * @run main/othervm -XX:CompileCommand=compileonly,*Test*::test
 *                   -XX:-TieredCompilation -Xcomp -XX:PerMethodTrapLimit=0
 *                   compiler.loopopts.superword.TestMultiversionRemoveUselessSlowLoop
 */

public class TestMultiversionRemoveUselessSlowLoop {
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
    static void test() {
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

    public static void main(String[] args) {
        test();
    }
}
