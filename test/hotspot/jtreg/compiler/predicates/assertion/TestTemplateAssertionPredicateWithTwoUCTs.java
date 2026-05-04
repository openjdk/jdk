/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8342287
 * @summary Test that a fail path projection of a Template Assertion Predicate is not treated as success path projection.
 * @run main/othervm -XX:-TieredCompilation -Xbatch
 *                   -XX:CompileCommand=compileonly,compiler.predicates.assertion.TestTemplateAssertionPredicateWithTwoUCTs::test
 *                   compiler.predicates.assertion.TestTemplateAssertionPredicateWithTwoUCTs
 */

package compiler.predicates.assertion;

public class TestTemplateAssertionPredicateWithTwoUCTs {
    static int iFld;

    public static void main(String[] strArr) {
        for (int i = 0; i < 1000; i++) {
            test();
        }
    }

    static void test() {
        int[][] lArr = new int[100][1];
        for (int i14 = 5; i14 < 273; ++i14) {
            int i16 = 1;
            while (++i16 < 94) {
                lArr[i16][0] += 1;
                switch (i14) {
                    case 11:
                    case 2:
                    case 13:
                        iFld = 34;
                }
            }
        }
    }
}
