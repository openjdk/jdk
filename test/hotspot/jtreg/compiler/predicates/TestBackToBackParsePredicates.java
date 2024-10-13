/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/*
 * @test
 * @bug 8316105
 * @library /test/lib
 * @summary Test that back to back Parse Predicates with same deopt reason are not grouped together
 * @run main/othervm -Xbatch compiler.predicates.TestBackToBackParsePredicates
 */

package compiler.predicates;

import jdk.test.lib.Asserts;

public class TestBackToBackParsePredicates {
    static long lFld;

    public static void main(String[] strArr2) {
        for (int i = -350; i <= 0; i++) {
            lFld = 30;
            test(i);
            check();
        }
        lFld = 30;
        test(1);
        check();
    }

    // Inlined
    static void foo() {
        for (int i12 = 1; i12 < 5; i12++) { // Loop A
            lFld += 1; // StoreL
        }
    }

    static void test(int x) {
        foo();

        // After fully unrolling loop A and after next round of IGVN:
        // We wrongly treat two back to back Loop Limit Check Parse Predicates as single Predicate Block. We therefore
        // keep the Loop Parse Predicate of loop A:
        //
        // Loop Parse Predicate (of A)
        // Loop Limit Check Parse Predicate (of A)  |
        //    -> StoreL of lFld pinned here         | Wrongly treated as single Predicate Block
        // Loop Limit Check Parse Predicate (of B)  |
        for (int i = 7; i < 212; i++) { // Loop B
            for (int j = 1; j < 80; j++) {
                switch (x % 8) {
                    case 0:
                    case 2:
                        break;
                    case 6:
                    case 7:
                }
            }
        }
    }

    static void check() {
        Asserts.assertEQ(34L, lFld);
    }
}
