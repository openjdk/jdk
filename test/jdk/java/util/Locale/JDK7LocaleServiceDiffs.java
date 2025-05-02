/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8001562
 * @summary Verify that getAvailableLocales() in locale sensitive services
 * classes return compatible set of locales as in JDK7.
 * @modules jdk.localedata
 * @run junit JDK7LocaleServiceDiffs
 */

import java.text.BreakIterator;
import java.text.Collator;
import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class JDK7LocaleServiceDiffs {

    static final List<String> jdk7availTags = List.of(
            "ar", "ar-AE", "ar-BH", "ar-DZ", "ar-EG", "ar-IQ", "ar-JO", "ar-KW",
            "ar-LB", "ar-LY", "ar-MA", "ar-OM", "ar-QA", "ar-SA", "ar-SD", "ar-SY",
            "ar-TN", "ar-YE", "be", "be-BY", "bg", "bg-BG", "ca", "ca-ES", "cs",
            "cs-CZ", "da", "da-DK", "de", "de-AT", "de-CH", "de-DE", "de-LU", "el",
            "el-CY", "el-GR", "en", "en-AU", "en-CA", "en-GB", "en-IE", "en-IN",
            "en-MT", "en-NZ", "en-PH", "en-SG", "en-US", "en-ZA", "es", "es-AR",
            "es-BO", "es-CL", "es-CO", "es-CR", "es-DO", "es-EC", "es-ES", "es-GT",
            "es-HN", "es-MX", "es-NI", "es-PA", "es-PE", "es-PR", "es-PY", "es-SV",
            "es-US", "es-UY", "es-VE", "et", "et-EE", "fi", "fi-FI", "fr", "fr-BE",
            "fr-CA", "fr-CH", "fr-FR", "fr-LU", "ga", "ga-IE", "he", "he-IL",
            "hi-IN", "hr", "hr-HR", "hu", "hu-HU", "id", "id-ID", "is", "is-IS",
            "it", "it-CH", "it-IT", "ja", "ja-JP",
            "ja-JP-u-ca-japanese-x-lvariant-JP", "ko", "ko-KR", "lt", "lt-LT", "lv",
            "lv-LV", "mk", "mk-MK", "ms", "ms-MY", "mt", "mt-MT", "nl", "nl-BE",
            "nl-NL", "no", "no-NO", "no-NO-x-lvariant-NY", "pl", "pl-PL", "pt",
            "pt-BR", "pt-PT", "ro", "ro-RO", "ru", "ru-RU", "sk", "sk-SK", "sl",
            "sl-SI", "sq", "sq-AL", "sr", "sr-BA", "sr-CS", "sr-Latn", "sr-Latn-BA",
            "sr-Latn-ME", "sr-Latn-RS", "sr-ME", "sr-RS", "sv", "sv-SE", "th",
            "th-TH", "th-TH-u-nu-thai-x-lvariant-TH", "tr", "tr-TR", "uk", "uk-UA",
            "vi", "vi-VN", "zh", "zh-CN", "zh-HK", "zh-SG", "zh-TW");
    static List<Locale> jdk7availLocs;

    static {
        jdk7availLocs = jdk7availTags.stream()
                .map(Locale::forLanguageTag)
                .collect(Collectors.toList());
    }

    /**
     * This test compares the locales returned by getAvailableLocales() from a
     * locale sensitive service to the available JDK7 locales. If the locales from
     * a locale sensitive service are found to not contain a JDK7 available tag,
     * the test will fail.
     */
    @ParameterizedTest
    @MethodSource("serviceProvider")
    public void compatibleLocalesTest(Class<?> c, List<Locale> locs) {
        diffLocale(c, locs);
    }

    static void diffLocale(Class<?> c, List<Locale> locs) {
        String diff = "";

        System.out.printf("Only in target locales (%s.getAvailableLocales()): ", c.getSimpleName());
        for (Locale l : locs) {
            if (!jdk7availLocs.contains(l)) {
                diff += "\"" + l.toLanguageTag() + "\", ";
            }
        }
        System.out.println(diff);
        diff = "";

        System.out.printf("Only in JDK7 (%s.getAvailableLocales()): ", c.getSimpleName());
        for (Locale l : jdk7availLocs) {
            if (!locs.contains(l)) {
                diff += "\"" + l.toLanguageTag() + "\", ";
            }
        }
        System.out.println(diff);

        if (diff.length() > 0) {
            throw new RuntimeException("Above locale(s) were not included in the target available locales");
        }
    }

    private static Stream<Arguments> serviceProvider() {
        return Stream.of(
                Arguments.of(BreakIterator.class, Arrays.asList(BreakIterator.getAvailableLocales())),
                Arguments.of(Collator.class, Arrays.asList(Collator.getAvailableLocales())),
                Arguments.of(DateFormat.class, Arrays.asList(DateFormat.getAvailableLocales())),
                Arguments.of(DateFormatSymbols.class, Arrays.asList(DateFormatSymbols.getAvailableLocales())),
                Arguments.of(DecimalFormatSymbols.class, Arrays.asList(DecimalFormatSymbols.getAvailableLocales())),
                Arguments.of(NumberFormat.class, Arrays.asList(NumberFormat.getAvailableLocales())),
                Arguments.of(Locale.class, Arrays.asList(Locale.getAvailableLocales()))
        );
    }
}
