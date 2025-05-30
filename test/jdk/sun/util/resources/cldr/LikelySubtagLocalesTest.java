/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8145136 8202537 8221432 8251317 8258794 8265315 8306116 8346948
 * @modules jdk.localedata
 * @summary Tests LikelySubtags is correctly reflected in Locale.getAvailableLocales().
 * @run junit LikelySubtagLocalesTest
 */
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LikelySubtagLocalesTest {

    public static final List<Locale> AVAILABLE_LOCALES = Arrays.asList(Locale.getAvailableLocales());

    /* Samples of locales that do not exist as .xml CLDR source files, but derived from
     * LikelySubtags. These locales should be present in output of getAvailableLocales()
     * method.
     */
    private static Stream<String> likelySubtagLocales() {
        return Stream.of(
            "ar-Arab-EG",
            "de-Latn-DE",
            "en-Latn-US",
            "es-Latn-ES",
            "fr-Latn-FR",
            "he-Hebr-IL",
            "hi-Deva-IN",
            "ja-Jpan-JP",
            "ko-Kore-KR");
    }

    @ParameterizedTest
    @MethodSource("likelySubtagLocales")
    public void testLikelySubtagLocales(String likelySubtag) {
        var l = Locale.forLanguageTag(likelySubtag);
        assertTrue(likelySubtag.equals(l.toLanguageTag()), likelySubtag + " != " + l.toLanguageTag());
        assertTrue(AVAILABLE_LOCALES.contains(l), l.getDisplayName() +
            " not found in Available Locales list");
    }
}
