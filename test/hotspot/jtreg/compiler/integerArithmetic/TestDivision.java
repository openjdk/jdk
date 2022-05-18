/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package compiler.integerArithmetic;

import jdk.test.lib.Asserts;

/*
 * @test TestDivision
 * @bug 8284742
 * @summary Tests to verify compiled code correctly handles integral divisions.
 * @library /test/lib
 *
 * @run main/othervm -Xcomp -XX:-TieredCompilation
 *      -XX:CompileCommand=compileonly,*.TestDivision::divide
 *      -XX:CompileCommand=compileonly,*.TestDivision::remainder
 *      compiler.integerArithmetic.TestDivision
 */
public class TestDivision {
    public static void main(String[] args) {
        Asserts.assertEquals(divide(19, 7), 19 / 7);
        Asserts.assertEquals(remainder(19, 7), 19 % 7);
        Asserts.assertEquals(divide(19L, 7L), 19L / 7L);
        Asserts.assertEquals(remainder(19L, 7L), 19L % 7L);

        Asserts.assertEquals(divide(19, -7), 19 / -7);
        Asserts.assertEquals(remainder(19, -7), 19 % -7);
        Asserts.assertEquals(divide(19L, -7L), 19L / -7L);
        Asserts.assertEquals(remainder(19L, -7L), 19L % -7L);

        Asserts.assertEquals(divide(-19, 7), -19 / 7);
        Asserts.assertEquals(remainder(-19, 7), -19 % 7);
        Asserts.assertEquals(divide(-19L, 7L), -19L / 7L);
        Asserts.assertEquals(remainder(-19L, 7L), -19L % 7L);

        Asserts.assertEquals(divide(-19, -7), -19 / -7);
        Asserts.assertEquals(remainder(-19, -7), -19 % -7);
        Asserts.assertEquals(divide(-19L, -7L), -19L / -7L);
        Asserts.assertEquals(remainder(-19L, -7L), -19L % -7L);

        Asserts.assertEquals(divide(Integer.MIN_VALUE, -1), Integer.MIN_VALUE / -1);
        Asserts.assertEquals(remainder(Integer.MIN_VALUE, -1), Integer.MIN_VALUE % -1);
        Asserts.assertEquals(divide(Long.MIN_VALUE, -1), Long.MIN_VALUE / -1L);
        Asserts.assertEquals(remainder(Long.MIN_VALUE, -1), Long.MIN_VALUE % -1L);

        try {
            divide(19, 0);
            Asserts.fail();
        } catch (ArithmeticException e) {}
        try {
            remainder(19, 0);
            Asserts.fail();
        } catch (ArithmeticException e) {}
        try {
            divide(19L, 0L);
            Asserts.fail();
        } catch (ArithmeticException e) {}
        try {
            remainder(19L, 0L);
            Asserts.fail();
        } catch (ArithmeticException e) {}
    }

    static int divide(int x, int y) {
        return x / y;
    }

    static int remainder(int x, int y) {
        return x % y;
    }

    static long divide(long x, long y) {
        return x / y;
    }

    static long remainder(long x, long y) {
        return x % y;
    }
}
