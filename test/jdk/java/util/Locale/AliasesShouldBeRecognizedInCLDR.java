/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8179071 8202537 8231273 8251317
 * @summary Test that language aliases of CLDR supplemental metadata are handled correctly.
 * @modules jdk.localedata
 * @run junit AliasesShouldBeRecognizedInCLDR
 */

/*
 * This fix is dependent on a particular version of CLDR data.
 */

import java.time.Month;
import java.time.format.TextStyle;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AliasesShouldBeRecognizedInCLDR {

    /*
     * Deprecated and Legacy tags.
     * As of CLDR 38, language aliases for some legacy tags have been removed.
     */
    private static final Set<String> LegacyAliases = Set.of(
            "zh-guoyu", "zh-min-nan", "i-klingon", "i-tsu",
            "sgn-CH-DE", "mo", "i-tay", "scc",
            "i-hak", "sgn-BE-FR", "i-lux", "tl", "zh-hakka", "i-ami", "aa-SAAHO",
            "zh-xiang", "i-pwn", "sgn-BE-NL", "jw", "sh", "i-bnn");

    // Ensure the display name for the given tag's January is correct
    @ParameterizedTest
    @MethodSource("shortJanuaryNames")
    public void janDisplayNameTest(String tag, String expected) {
        Locale target = Locale.forLanguageTag(tag);
        Month day = Month.JANUARY;
        TextStyle style = TextStyle.SHORT;
        String actual = day.getDisplayName(style, target);
        assertEquals(expected, actual);
    }

    // Expected month format data for locales after language aliases replacement.
    private static Stream<Arguments> shortJanuaryNames() {
        return Stream.of(
                Arguments.of("pa-PK", "\u0a1c\u0a28"),
                Arguments.of("uz-AF", "yan"),
                Arguments.of("sr-ME", "\u0458\u0430\u043d"),
                Arguments.of("scc", "\u0458\u0430\u043d"),
                Arguments.of("sh", "jan"),
                Arguments.of("ha-Latn-NE", "Jan"),
                Arguments.of("i-lux", "Jan.")
        );
    }

    // getAvailableLocales() should not contain any deprecated or Legacy language tags
    @Test
    public void invalidTagsTest() {
        Set<String> invalidTags = new HashSet<>();
        Arrays.stream(Locale.getAvailableLocales())
                .map(Locale::toLanguageTag)
                .forEach(tag -> {if(LegacyAliases.contains(tag)) {invalidTags.add(tag);}});
        assertTrue(invalidTags.isEmpty(),
                "Deprecated and Legacy tags found  " + invalidTags + " in AvailableLocales ");
    }
}
