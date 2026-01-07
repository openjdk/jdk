/*
 * Copyright (c) 2025 IBM Corporation. All rights reserved.
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
 * @bug 8373508
 * @summary C2: sinking CreateEx out of loop breaks the graph
 * @run main/othervm -Xbatch -XX:-TieredCompilation ${test.main.class}
 * @run main ${test.main.class}
 */

package compiler.loopopts;

public class TestCreateExSunkOutOfLoop2 {
    boolean b;

    boolean getB() {
        return b;
    }

    void test() {
        int x = -845;

        for (int i = 5; i > 1; i -= 2) {
            try {
                try {
                    for (Object temp = new byte[x]; ; ) {
                        // infinite loop
                    }
                } finally {
                    int zeroLimit = 2;
                    for (; zeroLimit < 4; zeroLimit *= 2) { }

                    int zero = 34;
                    for (int peel = 2; peel < zeroLimit; peel++) {
                        zero = 0;
                    }

                    if (zero == 0) {
                        // nop
                    }

                    int flagLimit = 2;
                    for (; flagLimit < 4; flagLimit *= 2) { }

                    boolean flag = getB();
                    for (int peel = 2; peel < flagLimit; peel++) {
                        if (flag) {
                            break;
                        }
                    }
                }
            } catch (Throwable t) {
                // ignored
            }
        }
    }

    public static void main(String[] strArr) {
        TestCreateExSunkOutOfLoop2 t = new TestCreateExSunkOutOfLoop2();
        for (int i = 0; i < 10_000; ++i) {
            t.test();
        }
    }
}
