/*
 * Copyright (c) 2023, Red Hat, Inc. All rights reserved.
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
 * @bug 8319793
 * @summary Replacing a test with a dominating test can cause an array access CastII to float above a range check that guards it
 * @run main/othervm -Xbatch -XX:-TieredCompilation TestArrayAccessCastIIAboveRC
 */

public class TestArrayAccessCastIIAboveRC {
    static int N = 400;
    static int iArrFld[] = new int[N];

    static void test() {
        float fArr[] = new float[N];
        int i9, i10, i12;
        long lArr1[] = new long[N];
        for (i9 = 7; i9 < 43; i9++) {
            try {
                i10 = 7 % i9;
                iArrFld[i9 + 1] = i9 / i10;
            } catch (ArithmeticException a_e) {
            }
            for (i12 = 1; 7 > i12; i12++)
                lArr1[i9 - 1] = 42;
            iArrFld[i12] = 4;
            fArr[i9 - 1] = 0;
        }
    }

    public static void main(String[] args) {
        for (int i = 0; i < 50_000; ++i) {
            test();
        }
    }
}
