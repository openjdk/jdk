/*
 * Copyright (c) 2006, 2007, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 5017980 6576055 8041972
 * @summary Test parsing methods
 * @author Joseph D. Darcy
 */

import java.lang.IllegalArgumentException;
import java.lang.IndexOutOfBoundsException;
import java.lang.NullPointerException;
import java.lang.RuntimeException;

/**
 * There are eight methods in java.lang.Integer which transform strings
 * into an int or Integer value:
 *
 * public Integer(String s)
 * public static Integer decode(String nm)
 * public static int parseInt(CharSequence s, int radix, int beginIndex, int endIndex)
 * public static int parseInt(CharSequence s, int radix, int beginIndex)
 * public static int parseInt(String s, int radix)
 * public static int parseInt(String s)
 * public static Integer valueOf(String s, int radix)
 * public static Integer valueOf(String s)
 *
 * Besides decode, all the methods and constructor call down into
 * parseInt(CharSequence, int, int, int) to do the actual work.  Therefore, the
 * behavior of parseInt(CharSequence, int, int, int) will be tested here.
 *
 */

public class ParsingTest {

    public static void main(String... argv) {
        check("+100", +100);
        check("-100", -100);

        check("+0", 0);
        check("-0", 0);
        check("+00000", 0);
        check("-00000", 0);

        check("+00000", 0, 0, 6);
        check("-00000", 0, 0, 6);

        check("0", 0);
        check("1", 1);
        check("9", 9);

        checkFailure("");
        checkFailure("\u0000");
        checkFailure("\u002f");
        checkFailure("+");
        checkFailure("-");
        checkFailure("++");
        checkFailure("+-");
        checkFailure("-+");
        checkFailure("--");
        checkFailure("++100");
        checkFailure("--100");
        checkFailure("+-6");
        checkFailure("-+6");
        checkFailure("*100");

        check("test-00000", 0, 4, 10);
        check("test-12345", -12345, 4, 10);
        check("xx12345yy", 12345, 2, 7);

        checkNumberFormatException("", 10, 0);
        checkNumberFormatException("100", 10, 3);
        checkNumberFormatException("+1000000", 10, 8);
        checkNumberFormatException("-1000000", 10, 8);

        checkNumberFormatException("", 10, 0, 0);
        checkNumberFormatException("+-6", 10, 0, 3);
        checkNumberFormatException("1000000", 10, 7);
        checkNumberFormatException("1000000", 10, 7, 7);
        checkNumberFormatException("1000000", Character.MAX_RADIX + 1, 0, 2);
        checkNumberFormatException("1000000", Character.MIN_RADIX - 1, 0, 2);

        checkIndexOutOfBoundsException("1000000", 10, 8);
        checkIndexOutOfBoundsException("1000000", 10, -1);
        checkIndexOutOfBoundsException("1000000", 10, 10, 4);
        checkIndexOutOfBoundsException("1000000", Character.MAX_RADIX + 1, -1, 2);
        checkIndexOutOfBoundsException("1000000", Character.MIN_RADIX - 1, -1, 2);
        checkIndexOutOfBoundsException("1000000", Character.MAX_RADIX + 1, 10, 2);
        checkIndexOutOfBoundsException("1000000", Character.MIN_RADIX - 1, 10, 2);
        checkIndexOutOfBoundsException("-1", 10, 0, 3);
        checkIndexOutOfBoundsException("-1", 10, 2, 3);
        checkIndexOutOfBoundsException("-1", 10, -1, 2);

        checkNull(10, 0, 1);
        checkNull(10, -1, 0);
        checkNull(10, 0, 0);
        checkNull(10, 0, -1);
        checkNull(-1, -1, -1);
    }

    private static void check(String val, int expected) {
        int n = Integer.parseInt(val);
        if (n != expected)
            throw new RuntimeException("Integer.parseInt failed. String:" +
                                                val + " Result:" + n);
    }

    private static void checkFailure(String val) {
        int n = 0;
        try {
            n = Integer.parseInt(val);
            System.err.println("parseInt(" + val + ") incorrectly returned " + n);
            throw new RuntimeException();
        } catch (NumberFormatException nfe) {
            ; // Expected
        }
    }

    private static void checkNumberFormatException(String val, int radix, int start) {
        int n = 0;
        try {
            n = Integer.parseInt(val, radix, start);
            System.err.println("parseInt(" + val + ", " + radix + ", " + start +
                    ") incorrectly returned " + n);
            throw new RuntimeException();
        } catch (NumberFormatException nfe) {
            ; // Expected
        }
    }

    private static void checkNumberFormatException(String val, int radix, int start, int end) {
        int n = 0;
        try {
            n = Integer.parseInt(val, radix, start, end);
            System.err.println("parseInt(" + val + ", " + radix + ", " + start + ", " + end +
                    ") incorrectly returned " + n);
            throw new RuntimeException();
        } catch (NumberFormatException nfe) {
            ; // Expected
        }
    }

    private static void checkIndexOutOfBoundsException(String val, int radix, int start) {
        int n = 0;
        try {
            n = Integer.parseInt(val, radix, start);
            System.err.println("parseInt(" + val + ", " + radix + ", " + start +
                    ") incorrectly returned " + n);
            throw new RuntimeException();
        } catch (IndexOutOfBoundsException ioob) {
            ; // Expected
        }
    }

    private static void checkIndexOutOfBoundsException(String val, int radix, int start, int end) {
        int n = 0;
        try {
            n = Integer.parseInt(val, radix, start, end);
            System.err.println("parseInt(" + val + ", " + radix + ", " + start + ", " + end +
                    ") incorrectly returned " + n);
            throw new RuntimeException();
        } catch (IndexOutOfBoundsException ioob) {
            ; // Expected
        }
    }

    private static void checkNull(int radix, int start, int end) {
        int n = 0;
        try {
            n = Integer.parseInt(null, 10, start, end);
            System.err.println("parseInt(null, " + radix + ", " + start + ", " + end +
                    ") incorrectly returned " + n);
            throw new RuntimeException();
        } catch (NullPointerException npe) {
            ; // Expected
        }
    }

    private static void check(String val, int expected, int start, int end) {
        int n = Integer.parseInt(val, 10, start, end);
        if (n != expected)
            throw new RuntimeException("Integer.parsedInt failed. String:" +
                    val + ", start: " + start + ", end: " + end + " Result:" + n);
    }
}
