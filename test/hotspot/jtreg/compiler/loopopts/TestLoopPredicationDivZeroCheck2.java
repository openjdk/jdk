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
 * @run main/othervm -XX:CompileCommand=compileonly,*TestLoopPredicationDivZeroCheck2*::* -XX:-TieredCompilation -Xbatch TestLoopPredicationDivZeroCheck2
 */

/*
 * Loop predication will try to move 3 / y (input to the range check for bArr[x / 30]) before outside loop but it may
 * not as y must be zero-checked. See TestLoopPredicationDivZeroCheck for a more detailed explanation.
 */
public class TestLoopPredicationDivZeroCheck2 {
    static volatile long lFld;
    static int iFld;

    public static void main(String[] strArr) {
        test();
    }

    static void test() {
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
