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

package compiler.ccp;

/*
 * @test
 * @bug 8374180
 * @summary Test that _widen is set correctly in XorI::add_ring() to ensure monotonicity.
 * @run main/othervm -XX:CompileCommand=compileonly,${test.main.class}::* -Xcomp ${test.main.class}
 */
public class TestWrongXorIWiden {
    static byte byFld;

    public static void main(String[] strArr) {
        test();
    }

    static void test() {
        int k, i17 = 0;
        long lArr[] = new long[400];
        for (int i = 9; i < 54; ++i) {
            for (int j = 7; j > 1; j--) {
                for (k = 1; k < 2; k++) {
                    i17 >>= i;
                }
                byFld += j ^ i17;
                for (int a = 1; a < 2; a++) {
                    i17 = k;
                }
            }
        }
    }
}
