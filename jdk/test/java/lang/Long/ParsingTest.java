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

/**
 * There are eight methods in java.lang.Long which transform strings
 * into a long or Long value:
 *
 * public Long(String s)
 * public static Long decode(String nm)
 * public static long parseLong(CharSequence s, int radix, int beginIndex, int endIndex)
 * public static long parseLong(CharSequence s, int radix, int beginIndex)
 * public static long parseLong(String s, int radix)
 * public static long parseLong(String s)
 * public static Long valueOf(String s, int radix)
 * public static Long valueOf(String s)
 *
 * Besides decode, all the methods and constructor call down into
 * parseLong(CharSequence, int, int, int) to do the actual work.  Therefore, the
 * behavior of parseLong(CharSequence, int, int, int) will be tested here.
 */

public class ParsingTest {

    public static void main(String... argv) {
        check("+100", +100L);
        check("-100", -100L);

        check("+0", 0L);
        check("-0", 0L);
        check("+00000", 0L);
        check("-00000", 0L);

        check("0", 0L);
        check("1", 1L);
        check("9", 9L);

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

        check("test-00000", 0L, 4, 10);
        check("test-12345", -12345L, 4, 10);
        check("xx12345yy", 12345L, 2, 7);
        check("xx123456789012345yy", 123456789012345L, 2, 17);

        checkNumberFormatException("100", 10, 3);
        checkNumberFormatException("", 10, 0);
        checkNumberFormatException("+1000000", 10, 8);
        checkNumberFormatException("-1000000", 10, 8);

        checkNumberFormatException("", 10, 0, 0);
        checkNumberFormatException("+-6", 10, 0, 3);
        checkNumberFormatException("1000000", 10, 7, 7);
        checkNumberFormatException("1000000", Character.MAX_RADIX + 1, 0, 2);
        checkNumberFormatException("1000000", Character.MIN_RADIX - 1, 0, 2);

        checkIndexOutOfBoundsException("", 10, 1, 1);
        checkIndexOutOfBoundsException("1000000", 10, 10, 4);
        checkIndexOutOfBoundsException("1000000", Character.MAX_RADIX + 1, 10, 2);
        checkIndexOutOfBoundsException("1000000", Character.MIN_RADIX - 1, 10, 2);
        checkIndexOutOfBoundsException("1000000", Character.MAX_RADIX + 1, -1, 2);
        checkIndexOutOfBoundsException("1000000", Character.MIN_RADIX - 1, -1, 2);
        checkIndexOutOfBoundsException("-1", 10, 0, 3);
        checkIndexOutOfBoundsException("-1", 10, 2, 3);
        checkIndexOutOfBoundsException("-1", 10, -1, 2);

        checkNull(10, 0, 1);
        checkNull(10, -1, 0);
        checkNull(10, 0, 0);
        checkNull(10, 0, -1);
        checkNull(-1, -1, -1);
    }

    private static void check(String val, long expected) {
        long n = Long.parseLong(val);
        if (n != expected)
            throw new RuntimeException("Long.parseLong failed. String:" +
                                       val + " Result:" + n);
    }

    private static void checkFailure(String val) {
        long n = 0L;
        try {
            n = Long.parseLong(val);
            System.err.println("parseLong(" + val + ") incorrectly returned " + n);
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
        long n = 0;
        try {
            n = Long.parseLong(val, radix, start, end);
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
        long n = 0;
        try {
            n = Long.parseLong(val, radix, start, end);
            System.err.println("parseInt(" + val + ", " + radix + ", " + start + ", " + end +
                    ") incorrectly returned " + n);
            throw new RuntimeException();
        } catch (IndexOutOfBoundsException ioob) {
            ; // Expected
        }
    }

    private static void checkNull(int radix, int start, int end) {
        long n = 0;
        try {
            n = Long.parseLong(null, 10, start, end);
            System.err.println("parseInt(null, " + radix + ", " + start + ", " + end +
                    ") incorrectly returned " + n);
            throw new RuntimeException();
        } catch (NullPointerException npe) {
            ; // Expected
        }
    }

    private static void check(String val, long expected, int start, int end) {
        long n = Long.parseLong(val, 10, start, end);
        if (n != expected)
            throw new RuntimeException("Long.parseLong failed. String:" +
                    val + ", start: " + start + ", end: " + end + " Result:" + n);
    }
}
