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
 * @test
 * @bug 8306042
 * @summary CCP missed optimization opportunity. Due to missing notification through Casts.
 * @library /test/lib
 * @run main/othervm -Xcomp -XX:-TieredCompilation
 *                   -XX:CompileOnly=compiler.ccp.TestShiftCastAndNotification::test
 *                   compiler.ccp.TestShiftCastAndNotification
 */

package compiler.ccp;

public class TestShiftCastAndNotification {
    static int N;
    static int iArrFld[] = new int[1];
    static int test() {
        int x = 1;
        int sval = 4;
        long useless[] = new long[N];
        for (double d1 = 63; d1 > 2; d1 -= 2) {
            for (double d2 = 3; 1 < d2; d2--) {
                x <<= sval; // The LShiftI
            }
            // CastII probably somewhere in the loop structure
            x &= 3; // The AndI
            for (int i = 1; i < 3; i++) {
                try {
                    x = iArrFld[0];
                    sval = 0;
                } catch (ArithmeticException a_e) {
                }
            }
        }
        return x;
    }
    public static void main(String[] args) {
        test();
    }
}
