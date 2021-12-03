/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8277529
 * @summary RangeCheck should not be moved out of a loop if a node on the data input chain for the bool is dependent
 *          on the projection into the loop (after the predicates).
 * @run main/othervm -Xbatch -XX:CompileCommand=compileonly,compiler.loopopts.TestDepBetweenLoopAndPredicate::test*
 *                   compiler.loopopts.TestDepBetweenLoopAndPredicate
 */

package compiler.loopopts;

public class TestDepBetweenLoopAndPredicate {
    static int x, y, z;
    static boolean flag;
    static int[] iArrFld = new int[25];
    static int[] iArrFld2 = new int[5];
    static int limit = 5;

    public static void main(String[] args) {
        for (int i = 0; i < 10000; i++) {
            flag = !flag;
            test();
        }

        for (int i = 0; i < 5000; i++) {
            flag = !flag;
            test2();
            test3();
            test4();
            test5();
        }
    }

    public static void test()  {
        int[] iArr = new int[20];
        System.arraycopy(iArrFld, x, iArr, y, 18);

        if (flag) {
            return;
        }

        for (int i = 0; i < limit; i++) {
            iArr[19]++;
        }
    }

    public static void test2() {
        for (int i = 0; i < limit; i++) {
            int[] iArr = new int[20];
            System.arraycopy(iArrFld, x, iArr, y, 18);

            if (flag) {
                return;
            }

            for (int j = i; j < limit; j++) {
                x = iArrFld[iArr[19]]; // No new offset node created
                iArr[19]++;
            }
        }
    }

    public static void test3() {
        for (int i = 0; i < limit; i++) {
            int[] iArr = new int[20];
            System.arraycopy(iArrFld, x, iArr, y, 18);

            if (flag) {
                return;
            }

            for (int j = i + 1; j < limit; j++) {
                x = iArrFld[iArr[19]]; // New offset node created
                iArr[19]++;
            }
        }
    }

    public static void test4() {
        for (int i = 0; i < limit; i++) {
            int[] iArr = new int[20];
            System.arraycopy(iArrFld, x, iArr, y, 18);

            if (flag) {
                return;
            }

            for (int j = i + 1 + z; j < limit; j++) {
                x = iArrFld[iArr[19]]; // New offset node created
                iArr[19]++;
            }
        }
    }

    public static void test5() {
        for (int i = 0; i < limit; i++) {
            int[] iArr = new int[20];
            System.arraycopy(iArrFld, x, iArr, y, 18);

            if (flag) {
                return;
            }

            for (int j = i + 1 + z; j < limit; j++) {
                x = iArrFld[iArr[19]]; // New offset node created
                iArr[19]++;
                y += iArrFld2[3]; // Range check removed because not dependent on projection into the loop
            }
        }
    }
}
