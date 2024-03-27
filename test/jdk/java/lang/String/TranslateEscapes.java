/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8223780 8263261
 * @summary This exercises String#translateEscapes patterns and limits.
 * @compile TranslateEscapes.java
 * @run main TranslateEscapes
 */

public class TranslateEscapes {
    public static void main(String... arg) {
        test1();
        test2();
        test3();
        test4();
        test5();
    }

    /*
     * Standard escapes.
     */
    static void test1() {
        verifyEscape("b", '\b');
        verifyEscape("f", '\f');
        verifyEscape("n", '\n');
        verifyEscape("r", '\r');
        verifyEscape("s", '\s');
        verifyEscape("t", '\t');
        verifyEscape("'", '\'');
        verifyEscape("\"", '\"');
        verifyEscape("\\", '\\');
    }

    /*
     * Octal escapes.
     */
    static void test2() {
        verifyOctalEscape("0", 0);
        verifyOctalEscape("3", 03);
        verifyOctalEscape("7", 07);
        verifyOctalEscape("07", 07);
        verifyOctalEscape("17", 017);
        verifyOctalEscape("27", 027);
        verifyOctalEscape("37", 037);
        verifyOctalEscape("377", 0377);

        verifyOctalEscape("777", 077);
        verifyOctalEscape("78", 07);
    }

    /*
     * Exceptions.
     */
    static void test3() {
        exceptionThrown("+");
        exceptionThrown("q");
    }

    /*
     * Escape line terminator.
     */
    static void test4() {
        verifyLineTerminator("\n");
        verifyLineTerminator("\r\n");
        verifyLineTerminator("\r");
    }

    /*
     * Unicode escapes.
     */
    static void test5() {
        verifyUnicodeEscape("\\u0000", "\u0000");
        verifyUnicodeEscape("\\u2022", "\u2022");
        verifyUnicodeEscape("\\ud83c\\udf09", "\ud83c\udf09");
        verifyUnicodeEscape("\\uuuuu2022", "\uuuuu2022");

        verifyIllegalUnicodeEscape("\\u000x");
        verifyIllegalUnicodeEscape("\\u000");
        verifyIllegalUnicodeEscape("\\u00");
        verifyIllegalUnicodeEscape("\\u0");
        verifyIllegalUnicodeEscape("\\u");
    }

    static void verifyEscape(String string, char ch) {
        String escapes = "\\" + string;
        if (escapes.translateEscapes().charAt(0) != ch) {
            System.err.format("\"%s\" does not escape \"%s\"'%n", string, escapes);
            throw new RuntimeException();
        }
    }

    static void verifyUnicodeEscape(String string1, String string2) {
        if (!string1.translateEscapes().equals(string2)) {
            System.err.format("\"%s\" does not unicode escape \"%s\"%n", string1, string2);
            throw new RuntimeException();
        }
    }

    static void verifyIllegalUnicodeEscape(String string) {
        try {
            string.translateEscapes();
            System.err.format("\"%s\" should be an error%n", string);
            throw new RuntimeException();
        } catch (IllegalArgumentException ex) {
        }
    }

    static void verifyOctalEscape(String string, int octal) {
        String escapes = "\\" + string;
        if (escapes.translateEscapes().charAt(0) != octal) {
            System.err.format("\"%s\" not octal %o%n", string, octal);
            throw new RuntimeException();
        }
    }

    static void exceptionThrown(String string) {
        String escapes = "\\" + string;
        try {
            escapes.translateEscapes();
            System.err.format("escape not thrown for %s%n", string);
            throw new RuntimeException();

        } catch (IllegalArgumentException ex) {
            // okay
        }
    }

    static void verifyLineTerminator(String string) {
        String escapes = "\\" + string;
        if (!escapes.translateEscapes().isEmpty()) {
            System.err.format("escape for line terminator not handled %s%n",
                              string.replace("\n", "\\n").replace("\r", "\\r"));
            throw new RuntimeException();
        }
    }
}
