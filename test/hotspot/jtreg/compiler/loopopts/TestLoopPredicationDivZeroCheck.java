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

/**
 * @test
 * @bug 8331717
 * @summary C2: Crash with SIGFPE
 *
 * @run main/othervm -XX:CompileCommand=compileonly,*TestLoopPredicationDivZeroCheck*::* -XX:-TieredCompilation -Xbatch TestLoopPredicationDivZeroCheck
 */

public class TestLoopPredicationDivZeroCheck {
    static int iArr[] = new int[100];
    static volatile long lFld;
    static int iFld;

    public static void main(String[] strArr) {
        for (int i = 0; i < 10000; i++) {
            test();
        }
        for (int i = 0; i < 10000; i++) {
            test2();
        }
    }

    /*
     * The division 2 / i4 requires a non-zero check. As the result is an array access, it will be the input to a range
     * check. Loop predication will try to move the range check and the division to right before the loop as the division
     * appears to be invariant (i4 is always 0). However, the division is not truly invariant as it requires the zero
     * check for i4 that can throw an exception. The bug fixed in 8331717 caused the division to still be moved before the
     * for loop with the range check.
     */
    static void test() {
        int i1 = 0;

        for (int i4 : iArr) {
            i4 = i1;
            try {
                iArr[0] = 1 / i4;
                i4 = iArr[2 / i4];
           } catch (ArithmeticException a_e) {
           }
       }
    }

    /*
     * Loop predication will try to move 3 / y (input to the range check for bArr[x / 30]) before its containing for loop
     * but it may not as y must be zero-checked. The same problem as above occurred before the fix in 8331717.
     */
    static void test2() {
        int x = 0;
        int y = iFld;
        long lArr[] = new long[400];
        boolean bArr[] = new boolean[400];
        for (int i = 0; i < 10000; i++) {
            for (int j = 1; j < 13; j++) {
                for (int k = 1; k < 2; k++) {
                    lFld = 0;
                    lArr[1] = 7;
                    try {
                        x = 3 / y;
                    } catch (ArithmeticException a_e) {
                    }
                    bArr[x / 30] = true;
                }
            }
        }
    }
}
