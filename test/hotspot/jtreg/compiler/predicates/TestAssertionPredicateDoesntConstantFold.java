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
 */

/*
 * @test
 * @bug 8325494
 * @summary C2: Broken graph after not skipping CastII node anymore for Assertion Predicates after JDK-8309902
 * @run main/othervm -XX:-TieredCompilation -Xcomp -XX:CompileOnly=TestAssertionPredicateDoesntConstantFold::test TestAssertionPredicateDoesntConstantFold
 *
 */

public class TestAssertionPredicateDoesntConstantFold {
    static boolean bFld;
    static int iArrFld[];
    static long lArrFld[];

    public static void main(String[] strArr) {
        try {
            test();
        } catch (NullPointerException npe) {}
    }

    static long test() {
        int i6 = 1, i7, i11;
        do {
            for (i7 = 1; i7 < 9; ++i7) {
                for (i11 = 2; i6 < i11; i11 -= 2) {
                    if (bFld) {
                        break;
                    }

                    lArrFld[i11 + 1] = 6;
                    iArrFld[i11 % 20] = 3;
                }
            }
        } while (++i6 < 8);

        return i6;
    }
}

