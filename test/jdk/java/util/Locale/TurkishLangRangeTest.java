/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8159420
 * @summary Checks the proper execution of LanguageRange.parse() and
 *          other LocaleMatcher methods when used in the locales like
 *          Turkish, because the toLowerCase() method is invoked in the
 *          parse() and other LocaleMatcher methods.
 *          e.g. "HI-Deva".toLowerCase() in the Turkish locale returns
 *          "hı-deva", where 'ı' is the LATIN SMALL LETTER DOTLESS I character
 *          which is not allowed in the language ranges/tags.
 * @compile -encoding utf-8 TurkishLangRangeTest.java
 * @run junit/othervm -Duser.language=tr -Duser.country=TR TurkishLangRangeTest
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Locale.LanguageRange;
import java.util.Locale.FilteringMode;
import java.util.LinkedHashMap;
import java.util.stream.Stream;

import static java.util.Locale.FilteringMode.EXTENDED_FILTERING;
import static java.util.Locale.FilteringMode.AUTOSELECT_FILTERING;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TurkishLangRangeTest {

    /*
     * Ensure parse() does not throw IllegalArgumentException for the Turkish Locale
     * with the given input.
     */
    @Test
    public void parseTest() {
        String ranges = "HI-Deva, ja-hIrA-JP, RKI";
        assertDoesNotThrow(() -> LanguageRange.parse(ranges));
    }

    /*
     * Ensure filter() does not return empty list for the Turkish Locale
     * with the given input.
     */
    @ParameterizedTest
    @MethodSource("modes")
    public void filterTest(FilteringMode mode) {
        String ranges = "hi-IN, itc-Ital";
        String tags = "hi-IN, itc-Ital";
        List<LanguageRange> priorityList = LanguageRange.parse(ranges);
        List<Locale> tagList = generateLocales(tags);
        String actualLocales = showLocales(Locale.filter(priorityList, tagList, mode));
        String expectedLocales = "hi-IN, itc-Ital";
        assertEquals(expectedLocales, actualLocales);
    }

    private static Stream<FilteringMode> modes() {
        return Stream.of(
                EXTENDED_FILTERING,
                AUTOSELECT_FILTERING
        );
    }

    /*
     * Ensure lookup() does not return null for the Turkish Locale with
     * the given input.
     */
    @Test
    public void lookupTest() {
        String ranges = "hi-IN, itc-Ital";
        String tags = "hi-IN, itc-Ital";
        List<LanguageRange> priorityList = LanguageRange.parse(ranges);
        List<Locale> localeList = generateLocales(tags);
        Locale actualLocale = Locale.lookup(priorityList, localeList);
        assertNotNull(actualLocale);
        String actualLocaleString = actualLocale.toLanguageTag();
        String expectedLocale = "hi-IN";
        assertEquals(expectedLocale, actualLocaleString);
    }

    /*
     * Ensure mapEquivalents() does not only return "hi-in" for the Turkish
     * Locale with the given input.
     */
    @Test
    public void mapEquivalentsTest() {
        String ranges = "HI-IN";
        List<LanguageRange> priorityList = LanguageRange.parse(ranges);
        HashMap<String, List<String>> map = new LinkedHashMap<>();
        List<String> equivalentList = new ArrayList<>();
        equivalentList.add("HI");
        equivalentList.add("HI-Deva");
        map.put("HI", equivalentList);

        List<LanguageRange> expected = new ArrayList<>();
        expected.add(new LanguageRange("hi-in"));
        expected.add(new LanguageRange("hi-deva-in"));
        List<LanguageRange> got =
                LanguageRange.mapEquivalents(priorityList, map);
        assertEquals(expected, got, getDifferences(expected, got));
    }

    private static String getDifferences(List<LanguageRange> expected,
            List<LanguageRange> got) {
        StringBuilder diffs = new StringBuilder();
        List<LanguageRange> cloneExpected = new ArrayList<>(expected);
        cloneExpected.removeAll(got);
        if (!cloneExpected.isEmpty()) {
            diffs.append("Found missing range(s): ")
                    .append(cloneExpected)
                    .append(System.lineSeparator());
        }
        List<LanguageRange> cloneGot = new ArrayList<>(got);
        cloneGot.removeAll(expected);
        if (!got.isEmpty()) {
            diffs.append("Got extra range(s): ")
                    .append(cloneGot)
                    .append(System.lineSeparator());
        }
        return diffs.toString();
    }

    private static List<Locale> generateLocales(String tags) {
        if (tags == null) {
            return null;
        }

        List<Locale> localeList = new ArrayList<>();
        if (tags.equals("")) {
            return localeList;
        }
        String[] t = tags.split(", ");
        for (String tag : t) {
            localeList.add(Locale.forLanguageTag(tag));
        }
        return localeList;
    }

    private static String showLocales(List<Locale> locales) {
        StringBuilder sb = new StringBuilder();

        Iterator<Locale> itr = locales.iterator();
        if (itr.hasNext()) {
            sb.append(itr.next().toLanguageTag());
        }
        while (itr.hasNext()) {
            sb.append(", ");
            sb.append(itr.next().toLanguageTag());
        }

        return sb.toString().trim();
    }
}
