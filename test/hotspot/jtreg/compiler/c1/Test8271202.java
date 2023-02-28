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
 * @bug 8271202
 * @requires vm.debug == true & vm.compiler1.enabled
 * @run main/othervm -Xbatch -XX:TieredStopAtLevel=1 -XX:+DeoptimizeALot
 *                   Test8271202
 */

public class Test8271202 {
    public static void main(String[] strArr) {
        try {
            test();
        } catch (Exception e) {
            // Expected
        }
    }

    static void test() {
        long l6 = 10L;
        int counter = 0;
        int i2, i26, i29, iArr[] = new int[400];
        boolean b3 = true;
        for (int smallinvoc = 0; smallinvoc < 139; smallinvoc++) {
        }
        for (i2 = 13; i2 < 1000; i2++) {
            for (i26 = 2; i26 < 114; l6 += 2) {
                // Infinite loop
                if (b3) {
                    for (i29 = 1; i29 < 2; i29++) {
                        try {
                            iArr[i26] = 0;
                        } catch (ArithmeticException a_e) {
                        }
                    }
                }
                counter++;
                if (counter == 100000) {
                    throw new RuntimeException("expected");
                }
            }
        }
    }
}

