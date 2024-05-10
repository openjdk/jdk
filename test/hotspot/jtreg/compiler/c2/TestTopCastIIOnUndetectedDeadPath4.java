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
 */

/*
 * @test id=JDK-8293941
 * @bug 8319372 8293941
 * @summary Tests that CastII are not dying anymore and breaking the graph due to control that is not removed
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -XX:-RangeCheckElimination
 *                   -Xcomp -XX:CompileOnly=compiler.c2.TestTopCastIIOnUndetectedDeadPath4::*
 *                   compiler.c2.TestTopCastIIOnUndetectedDeadPath4
 */

/*
 * @test id=JDK-8314111
 * @bug 8319372 8314111
 * @summary Tests that CastII are not dying anymore and breaking the graph due to control that is not removed
 * @run main/othervm -Xcomp -XX:CompileOnly=compiler.c2.TestTopCastIIOnUndetectedDeadPath4::test*
 *                   compiler.c2.TestTopCastIIOnUndetectedDeadPath4
 */

/*
 * @test id=NoFlags
 * @summary Tests that CastII are not dying anymore and breaking the graph due to control that is not removed
 * @run main/othervm compiler.c2.TestTopCastIIOnUndetectedDeadPath4
 */

package compiler.c2;

public class TestTopCastIIOnUndetectedDeadPath4 {

    static boolean bFld;
    static int iArrFld[];
    static long lArrFld[];
    static double dArrFld[][];

    public static void main(String[] strArr) {
        for (int i = 0; i < 5000; i++) {
            test8293941();
            test8314111_1();
            test8314111_2();
        }
    }

    static void test8293941() {
        int i16;
        boolean b = false;
        for (double d1 = 31.2; d1 < 72; d1++) {
            for (i16 = (int) d1; i16 < 2; ++i16) {
                iArrFld[i16] >>= 5;
                dArrFld[i16 - 1][i16] = 3;
                if (b) {
                    break;
                }
                lArrFld[i16] = 4;
            }
            switch (0) {
                case 5:
                    b = b;
            }
        }
    }

    static void test8314111_1() {
        int i, i1 = 0, i28, i30 = 0, iArr[] = new int[10];
        boolean bArr[] = new boolean[10];
        i = 1;
        while (++i < 5) {
            try {
                i1 = iArr[i - 1];
                i1 = 2 / i;
            } catch (ArithmeticException a_e) {
            }
            if (bFld) {
                switch (i) {
                    case 4:
                        for (i28 = 3; 100 > i28; i28++) {
                            i1 -= i28;
                        }
                        if ((i30 -= 3) > 0) {
                            switch (i30) {
                                case 4:
                                    bArr[i - 1] = bFld;
                                    iArr[i] = 6;
                            }
                        }
                }
            }
        }
    }

    static void test8314111_2() {
        int iArr[] = new int[1000];
        boolean bArr[] = new boolean[1000];
        int x = 0;
        int i = 1;
        while (++i < 5) {
            try {
                x = iArr[i - 1];
                x = 2 / i;
            } catch (ArithmeticException a_e) {
            }
            if (bFld) {
                x++;
                bArr[i - 1] = false;
                iArr[i] = 0;
            }
        }
    }
}

class Foo {
    public static void empty() {
    }
}
