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
 * @bug 8303564
 * @summary C2: "Bad graph detected in build_loop_late" after a CMove is wrongly split thru phi
 * @run main/othervm -XX:-BackgroundCompilation TestWrongCMovSplitIf
 */

public class TestWrongCMovSplitIf {
    private static int[] field1 = new int[1];
    private static int field3;

    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            test(true);
            test(false);
            testHelper(1000, false);
        }
    }

    private static void test(boolean flag) {
        int i;
        for (i = 0; i < 2; i++) {
            for (int j = 0; j < 10; j++) {
                for (int k = 0; k < 10; k++) {

                }
            }
        }
        field3 = testHelper(i, flag);
        for (int j = 0; j < 10; j++) {
            for (int k = 0; k < 10; k++) {
                for (int l = 0; l < 10; l++) {
                    for (int m = 0; m < 10; m++) {

                    }

                }
            }
        }
    }

    private static int testHelper(int i, boolean flag) {
        int stop = 1000;
        if (i == 2) {
            if (flag) {
                stop = 2;
            } else {
                stop = 1;
            }
        }
        int f = 0;
        for (int j = 0; j < stop; j++) {
            f += field1[0];
        }
        return f;
    }
}
