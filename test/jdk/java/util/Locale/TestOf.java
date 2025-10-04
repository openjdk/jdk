/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8282819
 * @summary Unit tests for Locale.of() method. Those tests check the equality
 *      of obtained objects with ones that are gotten from other means with both
 *      well-formed and ill-formed arguments. Also checks the possible NPEs
 *      for error cases.
 * @run junit TestOf
 */

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SuppressWarnings("deprecation")
public class TestOf {

    static Object[][] data_1Arg() {
        return new Object[][]{
                // well-formed
                {Locale.ENGLISH, "en"},
                {Locale.JAPANESE, "ja"},

                // ill-formed
                {Locale.ROOT, ""},
                {new Locale("a"), "a"},
                {new Locale("xxxxxxxxxx"), "xxxxxxxxxx"},
        };
    }

    static Object[][] data_2Args() {
        return new Object[][]{
                // well-formed
                {Locale.US, "en", "US"},
                {Locale.JAPAN, "ja", "JP"},

                // ill-formed
                {new Locale("", "US"), "", "US"},
                {new Locale("a", "b"), "a", "b"},
                {new Locale("xxxxxxxxxx", "yyyyyyyyyy"), "xxxxxxxxxx", "yyyyyyyyyy"},
        };
    }

    static Object[][] data_3Args() {
        return new Object[][]{
                // well-formed
                {Locale.forLanguageTag("en-US-POSIX"), "en", "US", "POSIX"},
                {Locale.forLanguageTag("ja-JP-POSIX"), "ja", "JP", "POSIX"},

                // ill-formed
                {new Locale("", "", "POSIX"), "", "", "POSIX"},
                {new Locale("a", "b", "c"), "a", "b", "c"},
                {new Locale("xxxxxxxxxx", "yyyyyyyyyy", "zzzzzzzzzz"),
                        "xxxxxxxxxx", "yyyyyyyyyy", "zzzzzzzzzz"},
                {new Locale("ja", "JP", "JP"), "ja", "JP", "JP"},
                {new Locale("th", "TH", "TH"), "th", "TH", "TH"},
                {new Locale("no", "NO", "NY"), "no", "NO", "NY"},
        };
    }

    @MethodSource("data_1Arg")
    @ParameterizedTest
    void test_1Arg(Locale expected, String lang) {
        assertEquals(expected, Locale.of(lang));
    }

    @MethodSource("data_2Args")
    @ParameterizedTest
    void test_2Args(Locale expected, String lang, String ctry) {
        assertEquals(expected, Locale.of(lang, ctry));
    }

    @MethodSource("data_3Args")
    @ParameterizedTest
    void test_3Args(Locale expected, String lang, String ctry, String vrnt) {
        assertEquals(expected, Locale.of(lang, ctry, vrnt));
    }

    @Test
    void test_NPE() {
        assertThrows(NullPointerException.class, () -> Locale.of(null));
        assertThrows(NullPointerException.class, () -> Locale.of("", null));
        assertThrows(NullPointerException.class, () -> Locale.of("", "", null));
    }
}
