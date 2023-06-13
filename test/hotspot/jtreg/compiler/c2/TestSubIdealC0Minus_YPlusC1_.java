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
 *                   -XX:CompileCommand=dontinline,compiler.c2.TestSubIdealC0Minus_YPlusC1_::test*
 *                   -XX:CompileCommand=compileonly,compiler.c2.TestSubIdealC0Minus_YPlusC1_::test*
 *                   compiler.c2.TestSubIdealC0Minus_YPlusC1_
 */
package compiler.c2;

import jdk.test.lib.Asserts;

public class TestSubIdealC0Minus_YPlusC1_ {

    private static final int I_C0_0 = 1234;
    private static final int I_C1 = 1234;
    private static final int I_C0_1 = 4321;

    private static final long L_C0_0 = 123_456_789_123L;
    private static final long L_C1 = 123_456_789_123L;
    private static final long L_C0_1 = 654_321;

    public static int testIC0EqualsC1(int x) {
        return I_C0_0 - (x + I_C1);
    }

    public static long testLC0EqualsC1(long x) {
        return L_C0_0 - (x + L_C1);
    }

    public static int testIC0NotEqualsC1(int x) {
        return I_C0_1 - (x + I_C1);
    }

    public static long testLC0NotEqualsC1(long x) {
        return L_C0_1 - (x + L_C1);
    }

    public static int testIXPlusC1IsOverflow(int x) {
        return Integer.MAX_VALUE - (x + Integer.MAX_VALUE);
    }

    public static long testLXPlusC1IsOverflow(long x) {
        return Long.MAX_VALUE - (x + Long.MAX_VALUE);
    }

    public static int testIXPlusC1IsUnderflow(int x) {
        return Integer.MIN_VALUE - (x + Integer.MIN_VALUE);
    }

    public static long testLXPlusC1IsUnderflow(long x) {
        return Long.MIN_VALUE - (x + Long.MIN_VALUE);
    }

    public static int testIC0MinusC1IsOverflow(int x) {
        return Integer.MAX_VALUE - (x + Integer.MIN_VALUE);
    }

    public static long testLC0MinusC1IsOverflow(long x) {
        return Long.MAX_VALUE - (x + Long.MIN_VALUE);
    }

    public static int testIC0MinusC1IsUnderflow(int x) {
        return Integer.MIN_VALUE - (x + Integer.MAX_VALUE);
    }

    public static long testLC0MinusC1IsUnderflow(long x) {
        return Long.MIN_VALUE - (x + Long.MAX_VALUE);
    }

    public static int testIResultIsOverflow(int x) {
        return 2147483637 - (x + 10); // Integer.MAX_VALUE == 2147483647
    }

    public static long testLResultIsOverflow(long x) {
        return 9223372036854775797L - (x + 10); // Long.MAX_VALUE == 9223372036854775807
    }

    public static int testIResultIsUnderflow(int x) {
        return -2147483637 - (x + 10); // Integer.MIN_VALUE == -2147483648
    }

    public static long testLResultIsUnderflow(long x) {
        return -9223372036854775797L - (x + 10); // Long.MIN_VALUE == -9223372036854775808
    }

    public static void main(String... args) {
        for (int i = 0; i < 50_000; i++) {
            Asserts.assertTrue(testIC0EqualsC1(10) == -10);
            Asserts.assertTrue(testIC0NotEqualsC1(100) == 2987);
            Asserts.assertTrue(testIXPlusC1IsOverflow(10) == -10);
            Asserts.assertTrue(testIXPlusC1IsUnderflow(-10) == 10);
            Asserts.assertTrue(testIC0MinusC1IsOverflow(10) == -11);
            Asserts.assertTrue(testIC0MinusC1IsUnderflow(10) == -9);
            Asserts.assertTrue(testIResultIsOverflow(-21) == Integer.MIN_VALUE);
            Asserts.assertTrue(testIResultIsUnderflow(2) == Integer.MAX_VALUE);

            Asserts.assertTrue(testLC0EqualsC1(10) == -10);
            Asserts.assertTrue(testLC0NotEqualsC1(100) == -123456134902L);
            Asserts.assertTrue(testLXPlusC1IsOverflow(10) == -10);
            Asserts.assertTrue(testLXPlusC1IsUnderflow(-10) == 10);
            Asserts.assertTrue(testLC0MinusC1IsOverflow(10) == -11);
            Asserts.assertTrue(testLC0MinusC1IsUnderflow(10) == -9);
            Asserts.assertTrue(testLResultIsOverflow(-21) == Long.MIN_VALUE);
            Asserts.assertTrue(testLResultIsUnderflow(2) == Long.MAX_VALUE);
        }
    }
}
