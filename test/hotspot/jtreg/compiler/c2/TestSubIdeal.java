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
 * @bug 8277882
 * @summary New subnode ideal optimization: converting "c0 - (x + c1)" into "(c0 - c1) - x"
 * @library /test/lib
 * @run main/othervm -XX:-TieredCompilation -Xbatch
 *                   -XX:CompileCommand=dontinline,compiler.c2.TestSubIdeal::test*
 *                   -XX:CompileCommand=compileonly,compiler.c2.TestSubIdeal::test*
 *                   compiler.c2.TestSubIdeal
 */
package compiler.c2;

import jdk.test.lib.Asserts;

public class TestSubIdeal {

    private static final int C0_0 = 1234;
    private static final int C1 = 1234;
    private static final int C0_1 = 4321;

    public static int testC0EqualsC1(int x) {
        return C0_0 - (x + C1);
    }

    public static int testC0NotEqualsC1(int x) {
        return C0_1 - (x + C1);
    }

    public static int testXPlusC1IsOverflow(int x) {
        return Integer.MAX_VALUE - (x + Integer.MAX_VALUE);
    }

    public static int testXPlusC1IsUnderflow(int x) {
        return Integer.MIN_VALUE - (x + Integer.MIN_VALUE);
    }

    public static int testC0MinusC1IsOverflow(int x) {
        return Integer.MAX_VALUE - (x + Integer.MIN_VALUE);
    }

    public static int testC0MinusC1IsUnderflow(int x) {
        return Integer.MIN_VALUE - (x + Integer.MAX_VALUE);
    }

    public static int testResultIsOverflow(int x) {
        return 2147483637 - (x + 10); // Integer.MAX_VALUE == 2147483647
    }

    public static int testResultIsUnderflow(int x) {
        return -2147483637 - (x + 10); // Integer.MIN_VALUE == -2147483648
    }

    public static void main(String... args) {
        for (int i = 0; i < 50_000; i++) {
            Asserts.assertTrue(testC0EqualsC1(10) == -10);
            Asserts.assertTrue(testC0NotEqualsC1(100) == 2987);
            Asserts.assertTrue(testXPlusC1IsOverflow(10) == -10);
            Asserts.assertTrue(testXPlusC1IsUnderflow(-10) == 10);
            Asserts.assertTrue(testC0MinusC1IsOverflow(10) == -11);
            Asserts.assertTrue(testC0MinusC1IsUnderflow(10) == -9);
            Asserts.assertTrue(testResultIsOverflow(-21) == Integer.MIN_VALUE);
            Asserts.assertTrue(testResultIsUnderflow(2) == Integer.MAX_VALUE);
        }
    }
}
