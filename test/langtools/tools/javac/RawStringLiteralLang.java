/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Unit tests for Raw String Literal language changes
 * @compile --enable-preview -source 13 -encoding utf8 RawStringLiteralLang.java
 * @run main/othervm --enable-preview RawStringLiteralLang
 */

public class RawStringLiteralLang {
    public static void main(String... args) {
        test1();
        test2();
    }

    /*
     * Test raw string functionality.
     */
    static void test1() {
        EQ(`abc`, "abc");
        EQ(`can't`, "can\'t");
        EQ(``can`t``, "can`t");
        EQ(`can\\'t`, "can\\\\'t");
        EQ(``can\\`t``, "can\\\\`t");
        EQ(`\t`, "\\t");
        EQ(`•`, "\u2022");

        LENGTH("abc``def", 8);
        EQ("abc`\u0020`def", "abc` `def");
    }

    /*
     * Test multi-line string functionality.
     */
    static void test2() {
        EQ(`abc
def
ghi`, "abc\ndef\nghi");
        EQ(`abc
def
ghi
`, "abc\ndef\nghi\n");
        EQ(`
abc
def
ghi`, "\nabc\ndef\nghi");
        EQ(`
abc
def
ghi
`, "\nabc\ndef\nghi\n");
    }

    /*
     * Raise an exception if the string is not the expected length.
     */
    static void LENGTH(String rawString, int length) {
        if (rawString == null || rawString.length() != length) {
            System.err.println("Failed LENGTH");
            System.err.println(rawString + " " + length);
            throw new RuntimeException("Failed LENGTH");
        }
    }

    /*
     * Raise an exception if the two input strings are not equal.
     */
    static void EQ(String input, String expected) {
        if (input == null || expected == null || !expected.equals(input)) {
            System.err.println("Failed EQ");
            System.err.println();
            System.err.println("Input:");
            System.err.println(input.replaceAll(" ", "."));
            System.err.println();
            System.err.println("Expected:");
            System.err.println(expected.replaceAll(" ", "."));
            throw new RuntimeException();
        }
    }
}
