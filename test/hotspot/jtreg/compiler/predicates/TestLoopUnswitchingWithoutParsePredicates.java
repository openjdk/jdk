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
 * @bug 8314106
 * @summary Test that we do not try to copy a Parse Predicate to an unswitched loop if they do not exist anymore.
 * @run main/othervm -Xbatch -XX:-TieredCompilation
 *                   -XX:CompileCommand=compileonly,compiler.predicates.TestLoopUnswitchingWithoutParsePredicates::*
 *                   compiler.predicates.TestLoopUnswitchingWithoutParsePredicates
 */

package compiler.predicates;

public class TestLoopUnswitchingWithoutParsePredicates {
    static byte byFld;
    static byte byArrFld[] = new byte[400];

    public static void main(String[] strArr) {
        for (int i = 0; i < 1000;i++) {
            test(i);
        }
    }

    static void test(int i2) {
        int i10, i11 = 0, i12, i13, i14;
        double dArr[] = new double[400];
        for (i10 = 7; i10 < 307; i10++) {
            byArrFld[i10] = 58;
            for (i12 = 1; i12 < 3; i12++) {
                for (i14 = 1; i14 < 2; i14++) {
                    byFld &= i14;
                    switch (i2) {
                        case 4:
                            dArr[1] = i14;
                        case 2:
                            i13 = i11;
                    }
                }
            }
        }
    }
}

