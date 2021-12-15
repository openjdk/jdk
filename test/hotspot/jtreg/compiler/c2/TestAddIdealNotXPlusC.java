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
 * @summary Generalize an addnode ideal optimization: converting "~x + c" into "(c - 1) - x"
 * @library /test/lib
 * @run main/othervm -XX:-TieredCompilation -Xbatch
 *                   -XX:CompileCommand=dontinline,compiler.c2.TestAddIdealNotXPlusC::test*
 *                   -XX:CompileCommand=compileonly,compiler.c2.TestAddIdealNotXPlusC::test*
 *                   compiler.c2.TestAddIdealNotXPlusC
 */
package compiler.c2;

import jdk.test.lib.Asserts;

public class TestAddIdealNotXPlusC {

    private static final int I_C = 1234;

    private static final long L_C = 123_456_789_123L;

    public static int testINormal(int x) {
        return ~x + I_C;
    }

    public static long testLNormal(long x) {
        return ~x + L_C;
    }

    public static int testICIsZero(int x) {
        return ~x + 0;
    }

    public static long testLCIsZero(long x) {
        return ~x + 0L;
    }

    public static int testICIsOne(int x) {
        return ~x + 1;
    }

    public static long testLCIsOne(long x) {
        return ~x + 1L;
    }

    public static int testIXIsZero(int x) {
        return ~x + I_C;
    }

    public static long testLXIsZero(long x) {
        return ~x + L_C;
    }

    public static int testICMinusOneIsUnderflow(int x) {
        return ~x + -2147483648; // Integer.MIN_VALUE == -2147483648
    }

    public static long testLCMinusOneIsUnderflow(long x) {
        return ~x + -9223372036854775808L; // Long.MIN_VALUE == -9223372036854775808L
    }

    public static int testIResultIsUnderflow(int x) {
        return ~x + -2147483638; // Integer.MIN_VALUE == -2147483648
    }

    public static long testLResultIsUnderflow(int x) {
        return ~x + -9223372036854775798L; // Long.MIN_VALUE == -9223372036854775808L
    }

    public static int testIResultIsOverflow(int x) {
        return ~x + 2147483637; // Integer.MAX_VALUE == 2147483647
    }

    public static long testLResultIsOverflow(int x) {
        return ~x + 9223372036854775797L; // Long.MAX_VALUE == 9223372036854775807L
    }

    public static void main(String... args) {
        for (int i = 0; i < 50_000; i++) {
            Asserts.assertTrue(testINormal(10) == 1223);
            Asserts.assertTrue(testICIsZero(10) == -11);
            Asserts.assertTrue(testICIsOne(10) == -10);
            Asserts.assertTrue(testIXIsZero(0) == 1233);
            Asserts.assertTrue(testICMinusOneIsUnderflow(10) == 2147483637);
            Asserts.assertTrue(testIResultIsUnderflow(10) == Integer.MAX_VALUE);
            Asserts.assertTrue(testIResultIsOverflow(-12) == Integer.MIN_VALUE);

            Asserts.assertTrue(testLNormal(10_000_000_000L) == 113_456_789_122L);
            Asserts.assertTrue(testLCIsZero(10_000_000_000L) == -10_000_000_001L);
            Asserts.assertTrue(testLCIsOne(10_000_000_000L) == -10_000_000_000L);
            Asserts.assertTrue(testLXIsZero(0) == 123_456_789_122L);
            Asserts.assertTrue(testLCMinusOneIsUnderflow(10) == 9223372036854775797L);
            Asserts.assertTrue(testLResultIsUnderflow(10) == Long.MAX_VALUE);
            Asserts.assertTrue(testLResultIsOverflow(-12) == Long.MIN_VALUE);
        }
    }
}
