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
 * @bug 8360510
 * @summary Test that StressDuplicateBackedge correctly clones Template Assertion Predicates to the inner counted loop.
 * @run main/othervm -Xbatch -XX:+IgnoreUnrecognizedVMOptions -XX:+StressDuplicateBackedge
 *                   compiler.predicates.assertion.TestStressDuplicateBackedgeWithAssertionPredicate
 * @run main compiler.predicates.assertion.TestStressDuplicateBackedgeWithAssertionPredicate
 */

package compiler.predicates.assertion;

public class TestStressDuplicateBackedgeWithAssertionPredicate {
    static int[] iArr = new int[100];
    static int iFld;
    static long lFld;

    public static void main(String[] strArr) {
        for (int i = 0; i < 10000; i++) {
            test();
        }
    }

    static void test() {
        // 5) Once the inner empty loop is removed (step 4), we can apply the "duplicate backedge
        //    optimization" to the initial outer counted loop which is now the only loop left. Note
        //    that we can do that even though it is a counted loop: This is stressed with
        //    StressDuplicateLoopBackedge.
        // 6) We do the following "duplicate loop backedge" transformation with current mainline:
        //
        //                                     Template Assertion
        //      Template Assertion                 Predicates
        //          Predicates                         |
        //              |              ====>          ...
        //             ...                             |
        //              |                             Loop       # Outer Non-Counted Loop (newly added)
        //          CountedLoop                       |
        //                                        CountedLoop    # Inner Counted Loop (old)
        //
        // 7) After the transformation, the Template Assertion Predicates are still at the Outer Non-Counted Loop.
        //    As a result, we find them to be useless in the next predicate elimination call with
        //    EliminateUselessPredicates because they cannot be found from the Inner Counted Loop (we stop at
        //    Loop which is not a predicate). However, we have verification code in place that checks that we
        //    can only find useless Template Assertion Predicates if the associated counted loop node is dead.
        //    This is not the case and we crash with an assertion failure.
        //
        //    The fix is to move the Template Assertion Predicates to the Inner Counted Loop again.
        for (int i = 0; i < 100; i++) {
            // 3) Loop Predication will hoist this range checkout out of the loop with Template
            //    Assertion Predicates.
            iArr[i] = 34;

            // 1) We need an inner empty loop to make sure the outer counter loop is not strip mined.
            //    Otherwise, we cannot apply the duplicate backedge optimization to the outer loop.
            // 4) Found to be empty and removed.
            for (int j = 0; j < 10; j++) {}

            // 2) We need some region inside the outer loop, otherwise, we cannot apply the duplicate
            //    backedge optimization.
            if (i == 3) {
                lFld = 34;
            } else {
                iFld = 2;
            }
        }
    }
}
