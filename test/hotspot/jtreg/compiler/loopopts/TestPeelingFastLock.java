/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8376219
 * @summary C2: Shared FastLock node
 *
 * @requires vm.compiler2.enabled
 *
 * @run main/othervm -Xbatch -XX:-TieredCompilation ${test.main.class}
 */


package compiler.loopopts;

public class TestPeelingFastLock {

    static int iFldStatic = 10;
    int iFld = 11;

    private static boolean gt(int lhs, int rhs) {
        return lhs > rhs;
    }

    int test() {
        int a = 1;
        int b = 2;
        int c = 3;
        int v = 4;
        int w = 5;
        int x = 6;
        int y = 7;
        int z = 8;
        int iArr[] = new int[400];

        double d = 1.5;

        int k = 0;
        for (a = 9; a < 283; a += 2) {
            for (int i = 8; i < 183; i++) { }
        }

        for (int i = 12; i < 283; i++) {
            for (int j = 0; j < 4; j++) {
                for (int jj = 0; jj < 8; jj++) {
                    switch (j) {
                        case -1, -2, -3 :
                            break;
                        case 0 :
                            iFldStatic += i;
                    }
                }
            }
            iFldStatic += i;
            for (int j = 1; gt(93, j); j += 2) {
                x += j - z;
                c -= iFld;
                k = 3;
                while ((k -= 2) > 0) {}
                switch (i % 8 + 52) {
                    case 52:
                        iArr[8] = 5;
                        for (int i20 = 1; i20 < 3; ++i20) {
                            x *= (int)d;
                            w += 5;
                        }
                        break;
                    case 53:
                    case 55:
                        synchronized(this) {
                            v *= iFldStatic;
                            v *= iFldStatic;
                        }
                        break;
                    case 56:
                    case 57:
                        try {
                            for (int count = 0; count < 32; count++) {
                                if (count < 10) {
                                    iArr[5] = a;
                                }
                            }
                            iArr[5] = a;
                            v = (a / b);
                        } catch (ArithmeticException a_e) {}
                        break;
                    default:
                        iFldStatic += iFldStatic;
                }
            }
        }
        return y + k;
    }

    public static void main(String[] strArr) {
        TestPeelingFastLock t = new TestPeelingFastLock();
        for (int i = 0; i < 10; i++) {
            t.test();
        }
    }
}
