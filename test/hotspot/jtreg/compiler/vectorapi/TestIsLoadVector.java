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

package compiler.vectorapi;

/*
 * @test
 * @bug 8333099
 * @summary We should check for is_LoadVector before checking for equality between vector types
 * @run main/othervm -XX:CompileCommand=compileonly,compiler.vectorapi.TestIsLoadVector::test -Xcomp compiler.vectorapi.TestIsLoadVector
 */

public class TestIsLoadVector {
    static int[] iArrFld = new int[400];

    static void test() {
        int i13, i16 = 3;
        short s = 50;
        for (int i = 0; i < 4; i++) {
            for (int i12 = 0; i12 < 8; ++i12) {
                for (int i14 = 2; 82 > i14; i14++) {
                    s <<= 90;
                    do {
                        try {
                            i13 = 0;
                        } catch (ArithmeticException a_e) {
                        }
                    } while (++i16 < 2);
                }
            }
        }
        int i18 = 1;
        while (++i18 < 41) {
            iArrFld[i18] >>= s;
        }
    }

    public static void main(String[] strArr) {
        for (int i = 0; i < 100; i++) {
            test();
        }
    }
}