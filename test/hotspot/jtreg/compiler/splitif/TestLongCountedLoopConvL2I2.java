/*
 * Copyright (c) 2024, Red Hat, Inc. All rights reserved.
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
 * @bug 8331575
 * @summary C2: crash when ConvL2I is split thru phi at LongCountedLoop
 * @run main/othervm -Xcomp -XX:CompileOnly=TestLongCountedLoopConvL2I2.* TestLongCountedLoopConvL2I2
 */

public class TestLongCountedLoopConvL2I2 {
    static int x = 34;

    public static void main(String[] strArr) {
        for (int i = 0; i < 2; i++) {
            test();
        }
    }

    static int test() {
        int a = 5, b = 6;
        long lArr[] = new long[2];

        for (long i = 159; i > 1; i -= 3) {
            a += 3;
            for (int j = 1; j < 4; j++) {
                if (a == 9) {
                    if (x == 73) {
                        try {
                            b = 10 / (int) i;
                        } catch (ArithmeticException a_e) {
                        }
                    }
                }
            }
        }
        return b;
    }
}
