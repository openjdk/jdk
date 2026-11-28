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

/*
 * @test
 * @bug 8396914
 * @summary C2: assert(is_OuterStripMinedLoop()) failed: invalid node class: IfTrue
 * @run main/othervm -Xbatch -XX:-TieredCompilation -XX:CompileCommand=compileonly,${test.main.class}.* ${test.main.class}
 * @run main ${test.main.class}
 */


public class TestLSMDeadSafePoint {
    static int test(int size) {
        int[] arr = new int[size];
        int res = 0;
        int n = -7;
        short s = 2124;
        for (int i = 62; i > 8; --i) {
            res -= 5;
            n /= s;
        }
        byte acc = 0;
        for (int i = 3; i < 5; ++i) {
            for (int j = 1; j < 8; j++) {
                acc += s;
                try {
                    res = 8 / n;
                    arr[i] = 0;
                } catch (ArithmeticException a_e) { }
            }
        }
        return res + acc;
    }

    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
          test(0);
        }
    }
}
