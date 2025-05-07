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
 * @bug 8349032
 * @summary Test that UCT for a Parse Predicate with a Phi does not incorrectly have a top input after Loop Unswitching.
 * @run main/othervm -Xcomp -XX:CompileCommand=compileonly,*TestParsePredicateUCTWithPhi*::test
 *                   compiler.predicates.TestParsePredicateUCTWithPhi
 */

package compiler.predicates;

public class TestParsePredicateUCTWithPhi {
    static int iFld;
    static int[][] iArrFld = new int[100][100];
    static int[] iArr = new int[100];

    public static void main(String[] strArr) {
        for (int i = 0; i < 10; i++) {
            test();
        }
    }

    static void test() {
        int i1 = 3;
        for (int i = 7; i < 28; i++) {
            int i20 = 4;
            try {
                iArr[3] = i1 / i;
            } catch (ArithmeticException a_e) {
            }
            try {
                i1 = 6 / iArrFld[i][i];
            } catch (ArithmeticException a_e) {
            }
            int i22 = 1;
            while (++i22 < 7) {
                try {
                    i20 = i20 / iArrFld[i][i];
                    i1 = 0;
                    i1 = iArr[1] / i;
                } catch (ArithmeticException a_e) {
                }
                iFld = i20;
            }
        }
    }
}
