/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8329691
 * @modules java.base/sun.util.locale.provider
 *          java.base/sun.util.cldr
 * @summary Tests CLDR's `nonlikelyScript` attribute is correctly implemented
 *      with the CLDRLocaleProviderAdapter
 * @run junit NonLikelyScriptTest
 */

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import sun.util.cldr.CLDRLocaleProviderAdapter;
import sun.util.locale.provider.LocaleProviderAdapter;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class NonLikelyScriptTest {
    private static final CLDRLocaleProviderAdapter CLDR_LOCALE_PROVIDER_ADAPTER
        = (CLDRLocaleProviderAdapter) LocaleProviderAdapter.forType(LocaleProviderAdapter.Type.CLDR);
    private static final Locale AZ_ARAB = Locale.forLanguageTag("az-Arab");
    private static final Locale AZ_CYRL = Locale.forLanguageTag("az-Cyrl");
    private static final Locale AZ_LATN = Locale.forLanguageTag("az-Latn");
    private static final Locale AZ_XXXX = Locale.forLanguageTag("az-Xxxx");
    private static final Locale RU_LATN = Locale.forLanguageTag("ru-Latn");
    private static final Locale RU_CYRL = Locale.forLanguageTag("ru-Cyrl");
    private static final Locale RU_XXXX = Locale.forLanguageTag("ru-Xxxx");
    private static final Locale EN_LATN = Locale.forLanguageTag("en-Latn");
    private static final Locale EN_DSRT = Locale.forLanguageTag("en-Dsrt");
    private static final Locale EN_XXXX = Locale.forLanguageTag("en-Xxxx");
    private static final Locale ZH_HANT_MO = Locale.forLanguageTag("zh-Hant-MO");
    private static final Locale ZH_HANS_SG = Locale.forLanguageTag("zh-Hans-SG");
    private static final Locale ZH_HANS = Locale.forLanguageTag("zh-Hans");
    private static final Locale ZH_HANT = Locale.forLanguageTag("zh-Hant");
    private static final Locale ZH_XXXX = Locale.forLanguageTag("zh-Xxxx");

    private static Stream<Arguments> parentLocales() {

        return Stream.of(
            // likely script
            Arguments.of(AZ_LATN, List.of(AZ_LATN, Locale.of("az"), Locale.ROOT)),
            Arguments.of(RU_CYRL, List.of(RU_CYRL, Locale.of("ru"), Locale.ROOT)),
            Arguments.of(EN_LATN, List.of(EN_LATN, Locale.ENGLISH, Locale.ROOT)),
            Arguments.of(ZH_HANS, List.of(ZH_HANS, Locale.CHINA, Locale.CHINESE, Locale.ROOT)),
            Arguments.of(Locale.CHINA, List.of(Locale.forLanguageTag("zh-Hans-CN"), ZH_HANS, Locale.CHINA, Locale.CHINESE, Locale.ROOT)),
            Arguments.of(ZH_HANS_SG, List.of(ZH_HANS_SG, ZH_HANS, Locale.forLanguageTag("zh-SG"), Locale.CHINESE, Locale.ROOT)),

            // non-likely script, explicit (as of CLDR 45)
            Arguments.of(AZ_ARAB, List.of(AZ_ARAB, Locale.ROOT)),
            Arguments.of(AZ_CYRL, List.of(AZ_CYRL, Locale.ROOT)),
            Arguments.of(EN_DSRT, List.of(EN_DSRT, Locale.ROOT)),
            Arguments.of(ZH_HANT, List.of(ZH_HANT, Locale.ROOT)),
            Arguments.of(Locale.TAIWAN, List.of(Locale.forLanguageTag("zh-Hant-TW"), ZH_HANT, Locale.ROOT)),
            Arguments.of(ZH_HANT_MO, List.of(ZH_HANT_MO, Locale.forLanguageTag("zh-Hant-HK"), ZH_HANT, Locale.ROOT)),

            // non-likely script, implicit
            Arguments.of(AZ_XXXX, List.of(AZ_XXXX, Locale.ROOT)),
            Arguments.of(RU_LATN, List.of(RU_LATN, Locale.ROOT)),
            Arguments.of(RU_XXXX, List.of(RU_XXXX, Locale.ROOT)),
            Arguments.of(EN_XXXX, List.of(EN_XXXX, Locale.ROOT)),
            Arguments.of(ZH_XXXX, List.of(ZH_XXXX, Locale.ROOT))
        );
    }

    @ParameterizedTest
    @MethodSource("parentLocales")
    public void checkParentLocales(Locale locale, List<Locale> expected) {
        var actual = CLDR_LOCALE_PROVIDER_ADAPTER.getCandidateLocales("", locale);
        assertEquals(expected, actual);
    }
}
