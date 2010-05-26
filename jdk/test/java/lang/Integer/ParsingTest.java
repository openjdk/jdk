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
 * @bug 5017980 6576055
 * @summary Test parsing methods
 * @author Joseph D. Darcy
 */


/**
 * There are six methods in java.lang.Integer which transform strings
 * into an int or Integer value:
 *
 * public Integer(String s)
 * public static Integer decode(String nm)
 * public static int parseInt(String s, int radix)
 * public static int parseInt(String s)
 * public static Integer valueOf(String s, int radix)
 * public static Integer valueOf(String s)
 *
 * Besides decode, all the methods and constructor call down into
 * parseInt(String, int) to do the actual work.  Therefore, the
 * behavior of parseInt(String, int) will be tested here.
 */

public class ParsingTest {
    public static void main(String... argv) {
        check("+100", +100);
        check("-100", -100);

        check("+0", 0);
        check("-0", 0);
        check("+00000", 0);
        check("-00000", 0);

        check("0", 0);
        check("1", 1);
        check("9", 9);

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

    private static void check(String val, int expected) {
        int n = Integer.parseInt(val);
        if (n != expected)
            throw new RuntimeException("Integer.parsedInt failed. String:" +
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
}
