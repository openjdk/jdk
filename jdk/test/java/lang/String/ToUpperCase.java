/*
 * Copyright (c) 2000, 2003, Oracle and/or its affiliates. All rights reserved.
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
    @test
    @bug 4219630 4304573 4533872 4900935
    @summary toUpperCase should upper-case German sharp s correctly even if
             it's the only character in the string. should also uppercase
             all of the 1:M char mappings correctly.  Also it should handle
             Locale specific (lt, tr, and az) uppercasings and supplementary
             characters correctly.
*/

import java.util.Locale;

public class ToUpperCase {

    public static void main(String[] args) {
        Locale turkish = new Locale("tr", "TR");
        Locale lt = new Locale("lt"); // Lithanian
        Locale az = new Locale("az"); // Azeri

        test("\u00DF", turkish, "SS");
        test("a\u00DF", turkish, "ASS");
        test("i", turkish, "\u0130");
        test("i", az, "\u0130");
        test("\u0131", turkish, "I");
        test("\u00DF", Locale.GERMANY, "SS");
        test("a\u00DF", Locale.GERMANY, "ASS");
        test("i", Locale.GERMANY, "I");

        // test some of the 1:M uppercase mappings
        test("abc\u00DF", Locale.US, "ABC\u0053\u0053");
        test("\u0149abc", Locale.US, "\u02BC\u004EABC");
        test("\u0149abc", turkish, "\u02BC\u004EABC");
        test("\u1F52", Locale.US, "\u03A5\u0313\u0300");
        test("\u0149\u1F52", Locale.US, "\u02BC\u004E\u03A5\u0313\u0300");
        test("\u1F54ZZZ", Locale.US, "\u03A5\u0313\u0301ZZZ");
        test("\u1F54ZZZ", turkish, "\u03A5\u0313\u0301ZZZ");
        test("a\u00DF\u1F56", Locale.US, "ASS\u03A5\u0313\u0342");
        test("\u1FAD", turkish, "\u1F6D\u0399");
        test("i\u1FC7", turkish, "\u0130\u0397\u0342\u0399");
        test("i\u1FC7", az, "\u0130\u0397\u0342\u0399");
        test("i\u1FC7", Locale.US, "I\u0397\u0342\u0399");
        test("\uFB04", Locale.US, "\u0046\u0046\u004C");
        test("\uFB17AbCdEfi", turkish, "\u0544\u053DABCDEF\u0130");
        test("\uFB17AbCdEfi", az, "\u0544\u053DABCDEF\u0130");

        // Remove DOT ABOVE after "i" in Lithuanian
        test("i\u0307", lt, "I");
        test("\u0307", lt, "\u0307");
        test("\u0307i", lt, "\u0307I");
        test("j\u0307", lt, "J");
        test("abci\u0307def", lt, "ABCIDEF");
        test("a\u0307", lt, "A\u0307");
        test("abc\u0307def", lt, "ABC\u0307DEF");
        test("i\u0307", Locale.US, "I\u0307");
        test("i\u0307", turkish, "\u0130\u0307");

        // Supplementary character tests
        //
        // U+10400 ("\uD801\uDC00"): DESERET CAPITAL LETTER LONG I
        // U+10401 ("\uD801\uDC01"): DESERET CAPITAL LETTER LONG E
        // U+10402 ("\uD801\uDC02"): DESERET CAPITAL LETTER LONG A
        // U+10428 ("\uD801\uDC28"): DESERET SMALL LETTER LONG I
        // U+10429 ("\uD801\uDC29"): DESERET SMALL LETTER LONG E
        // U+1042A ("\uD801\uDC2A"): DESERET SMALL LETTER LONG A
        //
        // valid code point tests:
        test("\uD801\uDC28\uD801\uDC29\uD801\uDC2A", Locale.US, "\uD801\uDC00\uD801\uDC01\uD801\uDC02");
        test("\uD801\uDC28a\uD801\uDC29b\uD801\uDC2Ac", Locale.US, "\uD801\uDC00A\uD801\uDC01B\uD801\uDC02C");
        // invalid code point tests:
        test("\uD800\uD800\uD801a\uDC00\uDC00\uDC00b", Locale.US, "\uD800\uD800\uD801A\uDC00\uDC00\uDC00B");
    }

    static void test(String in, Locale locale, String expected) {
        String result = in.toUpperCase(locale);
        if (!result.equals(expected)) {
            System.err.println("input: " + in + ", locale: " + locale +
                    ", expected: " + expected + ", actual: " + result);
            throw new RuntimeException();
        }
   }
}
