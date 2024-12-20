/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8314116
 * @summary We fail to remove a Template Assertion Predicate of a dying loop causing an assert. This will only be fixed
 *          completely with JDK-8288981 and 8314116 just mitigates the problem.
 * @requires vm.compiler2.enabled
 * @run main/othervm -Xbatch -XX:-TieredCompilation
 *                   -XX:CompileCommand=compileonly,compiler.predicates.assertion.TestTemplateAssertionPredicateNotRemoved::*
 *                   compiler.predicates.assertion.TestTemplateAssertionPredicateNotRemoved
 * @run main/othervm -Xbatch -XX:-TieredCompilation -XX:LoopMaxUnroll=0
 *                   -XX:CompileCommand=compileonly,compiler.predicates.assertion.TestTemplateAssertionPredicateNotRemoved::*
 *                   compiler.predicates.assertion.TestTemplateAssertionPredicateNotRemoved
 */

package compiler.predicates.assertion;

public class TestTemplateAssertionPredicateNotRemoved {
    static int[] iArrFld = new int[10];
    static long x;
    public static void main(String[] strArr) {
        for (int i = 0; i < 10000; i++) {
            test(i);
            test2(i);
        }
    }

    static void test(int i1) {
        int i5, i6 = 8;

        for (int i3 = 100; i3 > 3; --i3) {
            for (i5 = 1; i5 < 5; i5++) {
                switch (i1) {
                    case 1:
                    case 4:
                    case 47:
                        i6 = 4;
                }
                iArrFld[i5] = 23;
            }
        }
    }

    static void test2(int i1) {
        int i3, i4 = 70, i5, i6 = 8, iArr1[] = new int[10];
        double d1 = 0.41007;
        for (i3 = 100; i3 > 3; --i3) {
            i4 += 3;
            for (i5 = 1; i5 < 5; i5++) {
                switch (i1) {
                    case 1:
                    case 4:
                    case 47:
                        i6 = 34;
                }
                x /= 34;
                iArrFld[i5] = 23;
            }
        }

    }
}
