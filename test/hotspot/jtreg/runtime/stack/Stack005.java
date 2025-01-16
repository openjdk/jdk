/*
 * Copyright (c) 2000, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @key stress
 *
 * @summary converted from VM testbase nsk/stress/stack/stack005.
 * VM testbase keywords: [stress, quick, stack, nonconcurrent]
 * VM testbase readme:
 * DESCRIPTION
 *     This test provokes multiple stack overflows in the same thread
 *     by invoking final recursive method for the given fixed depth of
 *     recursion (though, for a large depth).
 *     This test makes measures a number of recursive invocations
 *     before 1st StackOverflowError, and then tries to reproduce
 *     such StackOverflowError 100 times -- each time by trying to
 *     invoke the same recursive method for the given fixed depth
 *     of invocations (which is 200 times that depth just measured).
 *     The test is deemed passed, if VM have not crashed.
 * COMMENTS
 *     This test crashes all HS versions (2.0, 1.3, 1.4) on all
 *     platforms (Win32, Solaris, Linux) in all execution modes
 *     (-Xint, -Xmixed, -Xcomp) in 100% of executions in which
 *     I had tryied it.
 *     See the bug:
 *     4366625 (P4/S4) multiple stack overflow causes HS crash
 *
 * @requires vm.opt.DeoptimizeALot != true
 * @run main/othervm/timeout=900 Stack005
 */

public class Stack005 {
    public static void main(String[] args) {
        Stack005 test = new Stack005();
        int depth;
        for (depth = 100; ; depth += 100) {
            try {
                test.recurse(depth);
            } catch (StackOverflowError | OutOfMemoryError soe) {
                break;
            }
        }
        System.out.println("Max. depth: " + depth);
        for (int i = 0; i < 100; i++) {
            try {
                test.recurse(200 * depth);
                System.out.println("?");
            } catch (StackOverflowError | OutOfMemoryError err) {
                // OK.
            }
        }
    }

    final void recurse(int depth) {
        if (depth > 0) {
            recurse(depth - 1);
        }
    }
}
