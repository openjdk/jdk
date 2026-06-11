
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

/**
 * @test
 * @bug 8383815
 * @summary C2: assert(false) failed: malformed IfNode with 1 outputs
 * @run main/othervm -XX:CompileCommand=compileonly,${test.main.class}*::* -XX:-TieredCompilation -Xbatch -XX:PerMethodTrapLimit=0 ${test.main.class}
 * @run main ${test.main.class}
 */

package compiler.integerArithmetic;

public class TestDivByZeroInLiveCFGPath {
    static long lFld;
    static int iArr[] = new int[400];

    public static void main(String[] strArr) {
        for (int i = 0; i < 10; i++) {
            test();
        }
    }

    static void test() {
        int x;
        for (int i = 9; i < 100; ++i) {
            int j = 100;
            while (--j > 0) {
                iArr[1] = (int) lFld;
            }
            try {
                iArr[1] = (5 / j);
                x = (i / iArr[8]);
            } catch (ArithmeticException a_e) {
            }
        }

        for (int i = 18; i < 50; i++) {
            iArr[2] += lFld;
        }
    }
}

