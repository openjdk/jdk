/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8289227
 * @summary BCP47 Transformed Content extension tests
 * @modules jdk.localedata
 * @run testng TExtensionTests
 */

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotSame;

import java.util.IllformedLocaleException;
import java.util.Locale;
import java.util.Map;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test BCP47 T extensions
 */
@Test
public class TExtensionTests {
    private static final String T1 = "und-latn-m0-ungegn-2007"; // two values for 'm0'
    private static final String T2 = "en-t0-und";
    private static final String T3 = "en-h0-hybrid";
    private static final String T4 = "en-latn-s0-ascii-d0-fwidth";
    private static final String T4_CANON = "en-latn-d0-fwidth-s0-ascii";
    private static final String T5 = "s0-ascii-hex-d0-fwidth"; // no source lang
    private static final String T5_CANON = "d0-fwidth-s0-ascii-hex"; // no source lang
    private static final String DUPLICATE_FIELD = "s0-dup";
    private static final String DUPLICATE = T4 + "-" + DUPLICATE_FIELD;
    private static final String INVALID_SOURCE = "aa-bb-cc";
    private static final Locale L1 = Locale.forLanguageTag("und-Cyrl-t-" + T1);
    private static final Locale L2 = Locale.forLanguageTag("ja-Kana-t-" + T2);
    private static final Locale L3 = Locale.forLanguageTag("hi-Latn-t-" + T3); // Hinglish
    private static final Locale L4 = Locale.forLanguageTag("ja-u-nu-jpan-t-" + T4 + "-x-pri-vate");
    private static final Locale L5 = Locale.forLanguageTag("ja-t-" + T5 + "-x-pri-vate");
    private static final Locale.Builder LB = new Locale.Builder();

    @DataProvider
    Object[][] data_TExtension() {
        return new Object[][]{
                {L1, T1},
                {L2, T2},
                {L3, T3},
                {L4, T4_CANON},
                {L5, T5_CANON},
        };
    }

    @DataProvider
    Object[][] data_GetDisplayName() {
        return new Object[][] {
                {L1, Locale.US,
                        "Cyrillic (Transform: Latin, Transform Rules: UN GEGN Transliteration 2007)"},
                {L2, Locale.US,
                        "Japanese (Katakana, Transform: English, Machine Translated: Unspecified Machine Translation)"},
                {L3, Locale.US,
                        "Hindi (Latin, Transform: English, Mixed-in: Hybrid)"}, // aka "Hinglish"
                {L4, Locale.US,
                        "Japanese (Transform: English (Latin), Transform Destination: To Fullwidth, Transform Source: From ASCII, Japanese Numerals, Private-Use: pri-vate)"},
                {L5, Locale.US,
                        "Japanese (Transform Destination: To Fullwidth, Transform Source: From ASCII From Hexadecimal Codes, Private-Use: pri-vate)"},

                {L1, Locale.JAPAN,
                        "\u30ad\u30ea\u30eb\u6587\u5b57 (t: \u30e9\u30c6\u30f3\u6587\u5b57\u3001m0: UNGEGN 2007)"},
                {L2, Locale.JAPAN,
                        "\u65e5\u672c\u8a9e (\u30ab\u30bf\u30ab\u30ca\u3001t: \u82f1\u8a9e\u3001t0: und)"},
                {L3, Locale.JAPAN,
                        "\u30d2\u30f3\u30c7\u30a3\u30fc\u8a9e (\u30e9\u30c6\u30f3\u6587\u5b57\u3001t: \u82f1\u8a9e\u3001h0: hybrid)"},
                {L4, Locale.JAPAN,
                        "\u65e5\u672c\u8a9e (t: \u82f1\u8a9e (\u30e9\u30c6\u30f3\u6587\u5b57)\u3001d0: \u5168\u89d2\u3001s0: ascii\u3001\u6f22\u6570\u5b57\u3001\u79c1\u7528: pri-vate)"},
                {L5, Locale.JAPAN,
                        "\u65e5\u672c\u8a9e (d0: \u5168\u89d2\u3001s0: ascii hex\u3001\u79c1\u7528: pri-vate)"},
        };
    }

    @DataProvider
    Object[][] data_Fields() {
        return new Object[][]{
                {L1, Map.of("m0", "ungegn-2007")},
                {L2, Map.of("t0", "und")},
                {L3, Map.of("h0", "hybrid")},
                {L4, Map.of("s0", "ascii", "d0", "fwidth")},
                {L5, Map.of("s0", "ascii-hex", "d0", "fwidth")},
        };
    }

    @Test(dataProvider="data_TExtension")
    public void test_GetExtension(Locale locale, String expected) {
        assertEquals(locale.getExtension(Locale.TRANSFORMED_CONTENT_EXTENSION), expected);
        assertEquals(locale.getExtension('T'), expected);
    }

    @Test(dataProvider="data_TExtension")
    public void test_SetExtension(Locale locale, String t_extension) {
        var l =LB.clear().setExtension(Locale.TRANSFORMED_CONTENT_EXTENSION,
                t_extension).build();
        assertEquals(l.getExtension(Locale.TRANSFORMED_CONTENT_EXTENSION),
                locale.getExtension(Locale.TRANSFORMED_CONTENT_EXTENSION));
        l =LB.clear().setExtension('T', t_extension).build();
        assertEquals(l.getExtension('T'),
                locale.getExtension('T'));
    }

    @Test(dataProvider="data_GetDisplayName")
    public void test_GetDisplayName(Locale locale, Locale inLocale, String expected) {
        assertEquals(locale.getDisplayName(inLocale), expected);
    }

    @Test
    public void test_FieldOrder() {
        // order of the fields does NOT matter
        var l = Locale.forLanguageTag("ja-u-nu-jpan-t-en-Latn-d0-fwidth-s0-ascii-x-pri-vate");
        assertEquals(l, L4);
    }

    @Test
    public void test_SubtagOrderInField() {
        // order of the subtags in a field DOES matter
        var l = Locale.forLanguageTag("und-Cyrl-t-und-latn-m0-2007-ungegn");
        assertNotSame(l, L1);
    }

    @Test
    public void test_FieldDuplicatesForLanguageTag() {
        // Locale.forLanguageTag() should ignore the t extension
        assertEquals(Locale.forLanguageTag("en-t-" + DUPLICATE), Locale.ENGLISH);
    }

    @Test
    public void test_FieldDuplicatesSetExtension() {
        // Locale.Builder.setExtension() should throw IllformedLocaleException
        try {
            LB.clear().setLocale(Locale.ENGLISH).setExtension('t', DUPLICATE).build();
            throw new RuntimeException("Duplicated fields should throw an exception.");
        } catch (IllformedLocaleException ile) {
            assertEquals(ile.getErrorIndex(), DUPLICATE.indexOf(DUPLICATE_FIELD));
            // success
            System.out.println("IllformedLocaleException thrown correctly: " + ile.getMessage());
        }
    }

    @Test
    public void test_InvalidSourceLangForLanguageTag() {
        // Locale.forLanguageTag() should ignore the t extension
        assertEquals(Locale.forLanguageTag("en-t-" + INVALID_SOURCE), Locale.ENGLISH);
    }

    @Test
    public void test_InvalidSourceLangSetExtension() {
        // Locale.Builder.setExtension() should throw IllformedLocaleException
        try {
            LB.clear().setLocale(Locale.ENGLISH).setExtension('t', INVALID_SOURCE).build();
            throw new RuntimeException("Invalid source language tag should throw an exception");
        } catch (IllformedLocaleException ile) {
            // success
            System.out.println("IllformedLocaleException thrown correctly: " + ile.getMessage());
        }
    }

    @Test(dataProvider="data_TExtension")
    public void test_getTransformedContentSource(Locale locale, String ext) {
        var expected = ext.replaceFirst("-?[a-zA-Z]\\d-.*", "");
        assertEquals(locale.getTransformedContentSource(), expected.isEmpty() ? null : Locale.forLanguageTag(expected));
    }

    @Test(dataProvider="data_Fields")
    public void test_getTransformedContentFields(Locale locale, Map<String, String> fields) {
        assertEquals(locale.getTransformedContentFields(), fields);
    }
}
