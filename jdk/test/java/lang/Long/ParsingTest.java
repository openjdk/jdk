/*
 * Copyright 2006-2007 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test
 * @bug 5017980 6576055
 * @summary Test parsing methods
 * @author Joseph D. Darcy
 */


/**
 * There are six methods in java.lang.Long which transform strings
 * into a long or Long value:
 *
 * public Long(String s)
 * public static Long decode(String nm)
 * public static long parseLong(String s, int radix)
 * public static long parseLong(String s)
 * public static Long valueOf(String s, int radix)
 * public static Long valueOf(String s)
 *
 * Besides decode, all the methods and constructor call down into
 * parseLong(String, int) to do the actual work.  Therefore, the
 * behavior of parseLong(String, int) will be tested here.
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
    }

    private static void check(String val, long expected) {
        long n = Long.parseLong(val);
        if (n != expected)
            throw new RuntimeException("Long.parsedLong failed. String:" +
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
}
