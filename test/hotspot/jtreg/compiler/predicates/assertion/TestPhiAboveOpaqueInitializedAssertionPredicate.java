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
 * @bug 8355674
 * @summary Check that we do not introduce a Phi above a OpaqueInitializedAssertionPredicateNode during Partial Peeling.
 * @run main/othervm -Xcomp -XX:CompileOnly=compiler.predicates.assertion.TestPhiAboveOpaqueInitializedAssertionPredicate::test
 *                   compiler.predicates.assertion.TestPhiAboveOpaqueInitializedAssertionPredicate
 * @run main         compiler.predicates.assertion.TestPhiAboveOpaqueInitializedAssertionPredicate
 */

package compiler.predicates.assertion;

public class TestPhiAboveOpaqueInitializedAssertionPredicate {
    public static void main(String[] o) {
        for (int i = 0; i < 1000; i++) {
            test();
        }
    }

    static void test() {
        int e;
        int h = 8;
        int[][] iArr = new int[100][100];
        for (float j = 8; j < 100 ; ++j) {
            for (int f = 5; f > 1; f -= 2) {
                for (int g = 1; 3 > g; ++g) {
                    try {
                        e = iArr[(int) j][g] / h;
                    } catch (ArithmeticException k) {
                    }
                    h += g;
                }
            }
        }
    }
}
