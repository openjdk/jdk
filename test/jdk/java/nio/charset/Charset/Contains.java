/*
 * Copyright (c) 2010, 2023, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @summary Unit test for charset containment
 * @bug 6798572 8167252
 * @modules jdk.charsets
 * @run junit Contains
 */

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Contains {

    /**
     * Tests the containment of some charsets against themselves.
     * This test takes both true and false for 'cont'.
     */
    @ParameterizedTest
    @MethodSource("charsets")
    public void charsetsTest(Charset containerCs, Charset cs, boolean cont){
        shouldContain(containerCs, cs, cont);
    }

    /**
     * Tests UTF charsets with other charsets. In this case, each UTF charset
     * should contain every single charset they are tested against. 'cont' is
     * always true.
     */
    @ParameterizedTest
    @MethodSource("utfCharsets")
    public void UTFCharsetsTest(Charset containerCs, Charset cs, boolean cont){
        shouldContain(containerCs, cs, cont);
    }

    /**
     * Tests the assertion in the contains() method: "Every charset contains itself."
     */
    @Test
    public void containsSelfTest() {
        for (var entry : Charset.availableCharsets().entrySet()) {
            Charset charset = entry.getValue();
            boolean contains = charset.contains(charset);
            assertTrue(contains, String.format("Charset(%s).contains(Charset(%s)) returns %s",
                    charset.name(), charset.name(), contains));
        }
    }

    /**
     * Helper method that checks if a charset should contain another charset.
     */
    static void shouldContain(Charset containerCs, Charset cs, boolean cont){
        assertEquals((containerCs.contains(cs)), cont, String.format("%s %s %s",
                containerCs.name(), (cont ? " contains " : " does not contain "), cs.name()));
    }

    private static Stream<Arguments> utfCharsets() {
        String[] utfNames = {
                "utf-16",
                "utf-8",
                "utf-16le",
                "utf-16be",
                "x-utf-16le-bom"
        };

        String[] charsetNames = {
                "US-ASCII",
                "UTF-8",
                "UTF-16",
                "UTF-16BE",
                "UTF-16LE",
                "x-UTF-16LE-BOM",
                "GBK",
                "GB18030",
                "ISO-8859-1",
                "ISO-8859-15",
                "ISO-8859-2",
                "ISO-8859-3",
                "ISO-8859-4",
                "ISO-8859-5",
                "ISO-8859-6",
                "ISO-8859-7",
                "ISO-8859-8",
                "ISO-8859-9",
                "ISO-8859-13",
                "JIS_X0201",
                "x-JIS0208",
                "JIS_X0212-1990",
                "GB2312",
                "EUC-KR",
                "x-EUC-TW",
                "EUC-JP",
                "x-euc-jp-linux",
                "KOI8-R",
                "TIS-620",
                "x-ISCII91",
                "windows-1251",
                "windows-1252",
                "windows-1253",
                "windows-1254",
                "windows-1255",
                "windows-1256",
                "windows-1257",
                "windows-1258",
                "windows-932",
                "x-mswin-936",
                "x-windows-949",
                "x-windows-950",
                "windows-31j",
                "Big5",
                "Big5-HKSCS",
                "x-MS950-HKSCS",
                "ISO-2022-JP",
                "ISO-2022-KR",
                "x-ISO-2022-CN-CNS",
                "x-ISO-2022-CN-GB",
                "Big5-HKSCS",
                "x-Johab",
                "Shift_JIS"
        };

        // All charsets in utfNames should contain
        // all charsets in charsetNames
        return Arrays.stream(utfNames).flatMap(cs1 -> Arrays.stream(charsetNames)
                .map(cs2 -> Arguments.of(Charset.forName(cs1), Charset.forName(cs2), true)));
    }

    private static Stream<Arguments> charsets() {
        Charset us_ascii = Charset.forName("US-ASCII");
        Charset iso_8859_1 = Charset.forName("ISO-8859-1");
        Charset iso_8859_15 = Charset.forName("ISO-8859-15");
        Charset utf_8 = Charset.forName("UTF-8");
        Charset utf_16be = Charset.forName("UTF-16BE");
        Charset cp1252 = Charset.forName("CP1252");

        return Stream.of(
            Arguments.of(us_ascii, us_ascii, true),
            Arguments.of(us_ascii, iso_8859_1, false),
            Arguments.of(us_ascii, iso_8859_15, false),
            Arguments.of(us_ascii, utf_8, false),
            Arguments.of(us_ascii, utf_16be, false),
            Arguments.of(us_ascii, cp1252, false),

            Arguments.of(iso_8859_1, us_ascii, true),
            Arguments.of(iso_8859_1, iso_8859_1, true),
            Arguments.of(iso_8859_1, iso_8859_15, false),
            Arguments.of(iso_8859_1, utf_8, false),
            Arguments.of(iso_8859_1, utf_16be, false),
            Arguments.of(iso_8859_1, cp1252, false),

            Arguments.of(iso_8859_15, us_ascii, true),
            Arguments.of(iso_8859_15, iso_8859_1, false),
            Arguments.of(iso_8859_15, iso_8859_15, true),
            Arguments.of(iso_8859_15, utf_8, false),
            Arguments.of(iso_8859_15, utf_16be, false),
            Arguments.of(iso_8859_15, cp1252, false),

            Arguments.of(utf_8, us_ascii, true),
            Arguments.of(utf_8, iso_8859_1, true),
            Arguments.of(utf_8, iso_8859_15, true),
            Arguments.of(utf_8, utf_8, true),
            Arguments.of(utf_8, utf_16be, true),
            Arguments.of(utf_8, cp1252, true),

            Arguments.of(utf_16be, us_ascii, true),
            Arguments.of(utf_16be, iso_8859_1, true),
            Arguments.of(utf_16be, iso_8859_15, true),
            Arguments.of(utf_16be, utf_8, true),
            Arguments.of(utf_16be, utf_16be, true),
            Arguments.of(utf_16be, cp1252, true),

            Arguments.of(cp1252, us_ascii, true),
            Arguments.of(cp1252, iso_8859_1, false),
            Arguments.of(cp1252, iso_8859_15, false),
            Arguments.of(cp1252, utf_8, false),
            Arguments.of(cp1252, utf_16be, false),
            Arguments.of(cp1252, cp1252, true)
        );
    }
}
