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
 * @test id=normal
 * @bug 8323101
 * @summary Test split_thru_phi with pinned divisions/modulo that have phi as inputs.
 * @run main/othervm -Xbatch
 *                   -XX:CompileCommand=compileonly,compiler.splitif.TestSplitDivThroughPhiWithControl::*
 *                   compiler.splitif.TestSplitDivThroughPhiWithControl
 */

/*
 * @test id=fuzzer
 * @bug 8323101
 * @summary Test split_thru_phi with pinned divisions/modulo that have phi as inputs.
 * @run main/othervm -Xbatch -XX:PerMethodTrapLimit=0
 *                   -XX:CompileCommand=compileonly,compiler.splitif.TestSplitDivThroughPhiWithControl::*
 *                   compiler.splitif.TestSplitDivThroughPhiWithControl
 */

package compiler.splitif;

public class TestSplitDivThroughPhiWithControl {
    static int divisorInt = 34;
    static int iFld;
    static int x;
    static int y;
    static long divisorLong = 34L;
    static long lFld;
    static long lFld2;
    static long lFld3;
    static boolean flag;

    static int[] iArr = new int[400];

    public static void main(String[] strArr) {
        iArr[0] = 52329;
        for (int i = 0; i < 10000; i++) {
            flag = i % 3 == 0;                 // Avoid unstable if trap
            divisorInt = i % 2 == 0 ? 0 : 23;  // Avoid div by zero trap
            divisorLong = divisorInt;          // Avoid div by zero trap
            try {
                testIntDiv();
            } catch (ArithmeticException e) {
                // Expected.
            }

            try {
                testIntMod();
            } catch (ArithmeticException e) {
                // Expected.
            }

            try {
                testLongDiv(); // Currently does not trigger due to JDK-8323652
            } catch (ArithmeticException e) {
                // Expected.
            }

            try {
                testLongMod(); // Currently does not trigger due to JDK-8323652
            } catch (ArithmeticException e) {
                // Expected.
            }

            testFuzzer();
        }
    }

    static void testIntDiv() {
        int a;

        for (int j = 0; j < 100; j++) {
            y += 5;
            int sub = j - 3; // AddI
            int div = (sub / divisorInt); // DivI with AddI input

            if (flag) {
                a = y;
            } else {
                a = 2;
            }
            // Region

            // Use StoreI with AddI input. Store needs to be split through Region in Split-If which is done together
            // with AddI.
            iFld = sub;

            if (a < 3) { // If that's split in Split-If
                // Use of DivI -> after Split-If, DivI gets a Phi input that merges the split AddI nodes.
                // -> triggers assert that we should not find pinned div nodes in cannot_split_division().
                x = div;
            }
        }
    }

    // Same as testIntDiv() but with ModI
    static void testIntMod() {
        int a;

        for (int j = 0; j < 100; j++) {
            y += 5;
            int sub = j - 3;
            int mod = (sub % divisorInt);

            if (flag) {
                a = y;
            } else {
                a = 2;
            }

            iFld = sub;

            if (a < 3) {
                x = mod; // Only StoreI visited first but not mod since it's an input
            }
        }
    }

    // Same as testIntDiv() but with DivL
    static void testLongDiv() {
        long a;

        for (int j = 0; j < 100; j++) {
            y += 5;
            long sub = j - 3;
            long div = (sub / divisorLong);

            if (flag) {
                a = lFld2;
            } else {
                a = 2;
            }

            lFld = sub;

            if (a < 3) {
                lFld3 = div;
            }
        }
    }


    // Same as testIntDiv() but with ModL
    static void testLongMod() {
        long a;

        for (long j = 0; j < 100; j++) {
            lFld2 += 5;
            long sub = j - 3;
            long mod = (sub % divisorLong);

            if (flag) {
                a = lFld2;
            } else {
                a = 2;
            }

            lFld = sub;

            if (a < 3) {
                lFld3 = mod; // Only StoreI visited first but not mod since it's an input
            }
        }
    }

    // Original fuzzer crash
    static void testFuzzer() {
        int i19, i21 = 4928, i23 = 14;
        for (int i = 5; i < 100; i++) {
            i19 = i23;
            int j = 1;
            while (true) {
                try {
                    i21 = (iArr[0] / 34);
                    i23 = (j % i21);
                } catch (ArithmeticException a_e) {
                }
                iArr = iArr;
                iFld = i21;
                iArr[1] += 5;
                if (j == 1000) {
                    break;
                }
                j++;
            }
        }
    }
}
