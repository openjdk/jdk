/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
 *
 * @test
 * @bug 8176841 8202537 8354548
 * @summary Tests the display names for BCP 47 U extensions
 * @modules jdk.localedata
 * @run junit DisplayNameTests
 */

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test Locale.getDisplayName() with BCP47 U extensions. Note that the
 * result may change depending on the CLDR releases.
 */
public class DisplayNameTests {
    private static final Locale loc1 = Locale.forLanguageTag("en-Latn-US-u" +
                                                             "-ca-japanese" +
                                                             "-cf-account" +
                                                             "-co-pinyin" +
                                                             "-cu-jpy" +
                                                             "-em-emoji" +
                                                             "-fw-wed" +
                                                             "-hc-h23" +
                                                             "-lb-loose" +
                                                             "-lw-breakall" +
                                                             "-ms-uksystem" +
                                                             "-nu-roman" +
                                                             "-rg-gbzzzz" +
                                                             "-sd-gbsct" +
                                                             "-ss-standard" +
                                                             "-tz-jptyo" +
                                                             "-va-posix");
    private static final Locale loc2 = Locale.of("ja", "JP", "JP");
    private static final Locale loc3 = new Locale.Builder()
                                            .setRegion("US")
                                            .setScript("Latn")
                                            .setUnicodeLocaleKeyword("ca", "japanese")
                                            .build();
    private static final Locale loc4 = new Locale.Builder()
                                            .setRegion("US")
                                            .setUnicodeLocaleKeyword("ca", "japanese")
                                            .build();
    private static final Locale loc5 = new Locale.Builder()
                                            .setUnicodeLocaleKeyword("ca", "japanese")
                                            .build();
    private static final Locale loc6 = Locale.forLanguageTag( "zh-CN-u-ca-dddd-nu-ddd-cu-ddd-fw-moq-tz-unknown-rg-twzz");

    static Object[][] locales() {
        return new Object[][] {
            // Locale for display, Test Locale, Expected output,
            {Locale.US, loc1,
            "English (Latin, United States, Japanese Calendar, Accounting Currency Format, Pinyin Sort Order, Currency: Japanese Yen, Emoji Presentation For Emoji, First day of week: Wednesday, 24 Hour System (0–23), Loose Line Break Style, Allow Line Breaks In All Words, Imperial Measurement System, Roman Numerals, Region For Supplemental Data: United Kingdom, Region Subdivision: gbsct, Suppress Sentence Breaks After Standard Abbreviations, Time Zone: Japan Time, POSIX Compliant Locale)"},
            {Locale.JAPAN, loc1,
            "英語 (ラテン文字、アメリカ合衆国、和暦、会計通貨フォーマット、ピンイン順、通貨: 日本円、絵文字表示方法: emoji、fw: wed、24時間制(0〜23)、禁則処理(弱)、単語途中の改行: breakall、ヤード・ポンド法、ローマ数字、rg: イギリス、sd: gbsct、略語の後の文分割: standard、タイムゾーン: 日本時間、ロケールのバリアント: posix)"},
            {Locale.forLanguageTag("hi-IN"), loc1,
            "अंग्रेज़ी (लैटिन, संयुक्त राज्य, जापानी पंचांग, लेखांकन मुद्रा प्रारूप, पिनयिन वर्गीकरण क्रम, मुद्रा: जापानी येन, इमोजी का प्रज़ेंटेशन: emoji, fw: wed, 24 घंटों की प्रणाली (0–23), ढीली पंक्ति विच्छेद शैली, शब्दों के बीच पंक्ति विच्छेद: breakall, इम्पीरियल मापन प्रणाली, रोमन संख्याएँ, rg: यूनाइटेड किंगडम, sd: gbsct, संक्षेपण के बाद वाक्य विच्छेद: standard, समय क्षेत्र: जापान समय, स्थानीय प्रकार: posix)"},

            // cases where no localized types are available. fall back to "key: type"
            {Locale.US, Locale.forLanguageTag("en-u-ca-unknown"), "English (Calendar: unknown)"},

            // cases with variant, w/o language, script
            {Locale.US, loc2, "Japanese (Japan, JP, Japanese Calendar)"},
            {Locale.US, loc3, "Latin (United States, Japanese Calendar)"},
            {Locale.US, loc4, "United States (Japanese Calendar)"},
            {Locale.US, loc5, ""},

            // non localizable cases
            {loc6, loc6, "中文 (中国，日历：dddd，货币：ddd，fw：moq，数字：ddd，rg：twzz，时区：unknown)"},
            {Locale.US, loc6, "Chinese (China, Calendar: dddd, Currency: ddd, First day of week: moq, Numbers: ddd, Region For Supplemental Data: twzz, Time Zone: unknown)"},
        };
    }

    @MethodSource("locales")
    @ParameterizedTest
    void test_locales(Locale inLocale, Locale testLocale, String expected) {
        String result = testLocale.getDisplayName(inLocale);
        assertEquals(expected, result);
    }
}
