/*
 * Copyright (c) 2003, 2026, Oracle and/or its affiliates. All rights reserved.
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
    @bug 4217441 4533872 4900935 8020037 8032012 8041791 8042589 8054307 8133167
    @summary toLowerCase should lower-case Greek Sigma correctly depending
             on the context (final/non-final).  Also it should handle
             Locale specific (lt, tr, and az) lowercasings and supplementary
             characters correctly.
    @run junit ToLowerCase
*/

import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ToLowerCase {
    private static final Locale TURKISH = Locale.of("tr");
    private static final Locale LITHUANIAN = Locale.of("lt");
    private static final Locale AZERI = Locale.of("az");
    private static final Locale GREEK = Locale.of("el");

    @ParameterizedTest
    @MethodSource
    void testSimpleCases(String source, Locale locale, String expected) {
        test(source, locale, expected);
    }

    private static Stream<Arguments> testSimpleCases() {
        return Stream.of(
            // Greek Sigma final/non-final tests
            Arguments.of("\u03A3", Locale.US, "\u03C3"),
            Arguments.of("LAST\u03A3", Locale.US, "last\u03C2"),
            Arguments.of("MID\u03A3DLE", Locale.US, "mid\u03C3dle"),
            Arguments.of("WORD1 \u03A3 WORD3", Locale.US, "word1 \u03C3 word3"),
            Arguments.of("WORD1 LAST\u03A3 WORD3", Locale.US, "word1 last\u03C2 word3"),
            Arguments.of("WORD1 MID\u03A3DLE WORD3", Locale.US, "word1 mid\u03C3dle word3"),
            Arguments.of("\u0399\u0395\u03a3\u03a5\u03a3 \u03a7\u03a1\u0399\u03a3\u03a4\u039f\u03a3", Locale.US,
                 "\u03b9\u03b5\u03c3\u03c5\u03c2 \u03c7\u03c1\u03b9\u03c3\u03c4\u03bf\u03c2"), // "IESUS XRISTOS"

            // Final_Cased (Unicode 4.0) -> Final_Sigma (Unicode 5.0) specific tests
            // ':' is a `Case_Ignorable` and a word boundary between letters, which
            // should not end the final cased letter search in 5.0 spec
            Arguments.of("A:\u03A3", Locale.ROOT, "a:\u03C2"),
            Arguments.of("A:\u03A3", Locale.US, "a:\u03C2"),
            Arguments.of("A:\u03A3", GREEK, "a:\u03C2"),
            Arguments.of("A\u03A3:B", Locale.ROOT, "a\u03C3:b"),
            Arguments.of("A\u03A3:B", Locale.US, "a\u03C3:b"),
            Arguments.of("A\u03A3:B", GREEK, "a\u03C3:b"),
            Arguments.of("A1\u03A3", Locale.ROOT, "a1\u03C3"),
            Arguments.of("A1\u03A3", Locale.US, "a1\u03C3"),
            Arguments.of("A1\u03A3", GREEK, "a1\u03C3"),
            Arguments.of("A\u03A31B", Locale.ROOT, "a\u03C21b"),
            Arguments.of("A\u03A31B", Locale.US, "a\u03C21b"),
            Arguments.of("A\u03A31B", GREEK, "a\u03C21b"),
            // U+10780 is supplementary, Cased, and Case_Ignorable.
            Arguments.of("\uD801\uDF80\u03A3", Locale.ROOT, "\uD801\uDF80\u03C2"),
            Arguments.of("\uD801\uDF80\u03A3", Locale.US, "\uD801\uDF80\u03C2"),
            Arguments.of("\uD801\uDF80\u03A3", GREEK, "\uD801\uDF80\u03C2"),
            Arguments.of("\u03A3\uD801\uDF80", Locale.ROOT, "\u03C3\uD801\uDF80"),
            Arguments.of("\u03A3\uD801\uDF80", Locale.US, "\u03C3\uD801\uDF80"),
            Arguments.of("\u03A3\uD801\uDF80", GREEK, "\u03C3\uD801\uDF80"),

            // Explicit dot above for I's and J's whenever there are more accents above (Lithuanian)
            Arguments.of("I", LITHUANIAN, "i"),
            Arguments.of("I\u0300", LITHUANIAN, "i\u0307\u0300"), // "I" followed by COMBINING GRAVE ACCENT (cc==230)
            Arguments.of("I\u0316", LITHUANIAN, "i\u0316"), // "I" followed by COMBINING GRAVE ACCENT BELOW (cc!=230)
            Arguments.of("J", LITHUANIAN, "j"),
            Arguments.of("J\u0300", LITHUANIAN, "j\u0307\u0300"), // "J" followed by COMBINING GRAVE ACCENT (cc==230)
            Arguments.of("J\u0316", LITHUANIAN, "j\u0316"), // "J" followed by COMBINING GRAVE ACCENT BELOW (cc!=230)
            Arguments.of("\u012E", LITHUANIAN, "\u012F"),
            Arguments.of("\u012E\u0300", LITHUANIAN, "\u012F\u0307\u0300"), // "I (w/ OGONEK)" followed by COMBINING GRAVE ACCENT (cc==230)
            Arguments.of("\u012E\u0316", LITHUANIAN, "\u012F\u0316"), // "I (w/ OGONEK)" followed by COMBINING GRAVE ACCENT BELOW (cc!=230)
            Arguments.of("\u00CC", LITHUANIAN, "i\u0307\u0300"),
            Arguments.of("\u00CD", LITHUANIAN, "i\u0307\u0301"),
            Arguments.of("\u0128", LITHUANIAN, "i\u0307\u0303"),
            Arguments.of("I\u0300", Locale.US, "i\u0300"), // "I" followed by COMBINING GRAVE ACCENT (cc==230)
            Arguments.of("J\u0300", Locale.US, "j\u0300"), // "J" followed by COMBINING GRAVE ACCENT (cc==230)
            Arguments.of("\u012E\u0300", Locale.US, "\u012F\u0300"), // "I (w/ OGONEK)" followed by COMBINING GRAVE ACCENT (cc==230)
            Arguments.of("\u00CC", Locale.US, "\u00EC"),
            Arguments.of("\u00CD", Locale.US, "\u00ED"),
            Arguments.of("\u0128", Locale.US, "\u0129"),

            // I-dot tests
            Arguments.of("\u0130", TURKISH, "i"),
            Arguments.of("\u0130", AZERI, "i"),
            Arguments.of("\u0130", LITHUANIAN, "\u0069\u0307"),
            Arguments.of("\u0130", Locale.US, "\u0069\u0307"),
            Arguments.of("\u0130", Locale.JAPAN, "\u0069\u0307"),
            Arguments.of("\u0130", Locale.ROOT, "\u0069\u0307"),

            // Remove dot_above in the sequence I + dot_above (Turkish and Azeri)
            Arguments.of("I\u0307", TURKISH, "i"),
            Arguments.of("I\u0307", AZERI, "i"),
            Arguments.of("J\u0307", TURKISH, "j\u0307"),
            Arguments.of("J\u0307", AZERI, "j\u0307"),

            // Unless an I is before a dot_above, it turns into a dotless i (Turkish and Azeri)
            Arguments.of("I", TURKISH, "\u0131"),
            Arguments.of("I", AZERI, "\u0131"),
            Arguments.of("I", Locale.US, "i"),
            Arguments.of("IABC", TURKISH, "\u0131abc"),
            Arguments.of("IABC", AZERI, "\u0131abc"),
            Arguments.of("IABC", Locale.US, "iabc"),

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
            Arguments.of("\uD801\uDC00\uD801\uDC01\uD801\uDC02", Locale.US, "\uD801\uDC28\uD801\uDC29\uD801\uDC2A"),
            Arguments.of("\uD801\uDC00A\uD801\uDC01B\uD801\uDC02C", Locale.US, "\uD801\uDC28a\uD801\uDC29b\uD801\uDC2Ac"),
            // invalid code point tests:
            Arguments.of("\uD800\uD800\uD801A\uDC00\uDC00\uDC00B", Locale.US, "\uD800\uD800\uD801a\uDC00\uDC00\uDC00b"),

            // lower/uppercase + surrogates
            Arguments.of("a\uD801\uDC1c", Locale.ROOT, "a\uD801\uDC44"),
            Arguments.of("A\uD801\uDC1c", Locale.ROOT, "a\uD801\uDC44"),
            Arguments.of("a\uD801\uDC00\uD801\uDC01\uD801\uDC02", Locale.US, "a\uD801\uDC28\uD801\uDC29\uD801\uDC2A"),
            Arguments.of("A\uD801\uDC00\uD801\uDC01\uD801\uDC02", Locale.US, "a\uD801\uDC28\uD801\uDC29\uD801\uDC2A"));
    }

    @Test
    void testBMPAndSupp1() {
        // test bmp + supp1
        StringBuilder src = new StringBuilder(0x20000);
        StringBuilder exp = new StringBuilder(0x20000);
        for (int cp = 0; cp < 0x20000; cp++) {
            if (cp >= Character.MIN_HIGH_SURROGATE && cp <= Character.MAX_HIGH_SURROGATE) {
                continue;
            }
            if (cp == 0x0130) {
                // Although UnicodeData.txt has the lower case char as \u0069, it should be
                // handled with the rules in SpecialCasing.txt, i.e., \u0069\u0307 in
                // non Turkic locales.
                continue;
            }
            int lowerCase = Character.toLowerCase(cp);
            if (lowerCase == -1) {    //Character.ERROR
                continue;
            }
            src.appendCodePoint(cp);
            exp.appendCodePoint(lowerCase);
        }
        test(src.toString(), Locale.US, exp.toString());

    }

    @Test
    void testLatin1() {
        // test latin1
        var src = new StringBuilder(0x100);
        var exp = new StringBuilder(0x100);
        for (int cp = 0; cp < 0x100; cp++) {
            int lowerCase = Character.toLowerCase(cp);
            if (lowerCase == -1) {    //Character.ERROR
                continue;
            }
            src.appendCodePoint(cp);
            exp.appendCodePoint(lowerCase);
        }
        test(src.toString(), Locale.US, exp.toString());
    }

    @Test
    void testNonLatin1ToLatin1() {
        // test non-latin1 -> latin1
        var src = new StringBuilder(0x100).append("abc");
        var exp = new StringBuilder(0x100).append("abc");
        for (int cp = 0x100; cp < 0x10000; cp++) {
            int lowerCase  = Character.toLowerCase(cp);
            if (lowerCase < 0x100 && cp != '\u0130') {
                src.appendCodePoint(cp);
                exp.appendCodePoint(lowerCase);
            }
        }
        test(src.toString(), Locale.US, exp.toString());
    }

    private static void test(String in, Locale locale, String expected) {
        assertEquals(expected, in.toLowerCase(locale));

        for (String[] ss :  new String[][] {
                                new String[] {"abc",      "abc"},
                                new String[] {"aBc",      "abc"},
                                new String[] {"ABC",      "abc"},
                                new String[] {"ab\u4e00", "ab\u4e00"},
                                new String[] {"aB\u4e00", "ab\u4e00"},
                                new String[] {"AB\u4e00", "ab\u4e00"},
                                new String[] {"ab\uD800\uDC00", "ab\uD800\uDC00"},
                                new String[] {"aB\uD800\uDC00", "ab\uD800\uDC00"},
                                new String[] {"AB\uD800\uDC00", "ab\uD800\uDC00"},
                                new String[] {"ab\uD801\uDC1C", "ab\uD801\uDC44"},
                                new String[] {"aB\uD801\uDC1C", "ab\uD801\uDC44"},
                                new String[] {"AB\uD801\uDC1C", "ab\uD801\uDC44"},

                            }) {
            assertEquals(ss[1] + " " + expected,
                (ss[0] + " " + in).toLowerCase(locale));
            assertEquals(expected + " " + ss[1],
                (in + " " + ss[0]).toLowerCase(locale));
        }
    }
}
