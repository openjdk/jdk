/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8038436 8158504 8065555 8167143 8167273 8189272 8287340
 * @summary Test for changes in 8038436
 * @modules java.base/sun.util.locale.provider
 *          java.base/sun.util.spi
 *          jdk.localedata
 * @compile -XDignore.symbol.file Bug8038436.java
 * @run main/othervm  -Djava.locale.providers=COMPAT Bug8038436  availlocs
 */

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import sun.util.locale.provider.LocaleProviderAdapter;

public class Bug8038436 {
    public static void main(String[] args) {

        switch (args[0]) {

        case "availlocs":
            availableLocalesTests();
            break;
        default:
            throw new RuntimeException("no test was specified.");
        }

    }


    static final String[] bipLocs = (", ar, ar_JO, ar_LB, ar_SY, be, be_BY, bg, " +
        "bg_BG, ca, ca_ES, cs, cs_CZ, da, da_DK, de, de_AT, de_CH, de_DE, " +
        "de_LU, el, el_CY, el_GR, en, en_AU, en_CA, en_GB, en_IE, en_IN, " +
        "en_MT, en_NZ, en_PH, en_SG, en_US, en_ZA, es, es_AR, es_BO, es_CL, " +
        "es_CO, es_CR, es_DO, es_EC, es_ES, es_GT, es_HN, es_MX, es_NI, " +
        "es_PA, es_PE, es_PR, es_PY, es_SV, es_US, es_UY, es_VE, et, et_EE, " +
        "fi, fi_FI, fr, fr_BE, fr_CA, fr_CH, fr_FR, ga, ga_IE, he, he_IL, " +
        "hi_IN, hr, hr_HR, hu, hu_HU, id, id_ID, is, is_IS, it, it_CH, it_IT, " +
        "ja, ja_JP, ko, ko_KR, lt, lt_LT, lv, lv_LV, mk, mk_MK, ms, ms_MY, mt, " +
        "mt_MT, nb, nb_NO, nl, nl_BE, nl_NL, nn_NO, no, no_NO, no_NO_NY, pl, pl_PL, pt, pt_BR, " +
        "pt_PT, ro, ro_RO, ru, ru_RU, sk, sk_SK, sl, sl_SI, sq, sq_AL, sr, " +
        "sr_BA, sr_CS, sr_ME, sr_ME_#Latn, sr_RS, sr__#Latn, sv, sv_SE, th, th_TH, " +
        "tr, tr_TR, uk, uk_UA, vi, vi_VN, zh, zh_CN, zh_CN_#Hans, zh_HK, " +
        "zh_HK_#Hant, zh_SG, zh_SG_#Hans, zh_TW, zh_TW_#Hant, ").split(",\\s*");

    static final String[] dfpLocs = bipLocs;
    static final String[] datefspLocs = bipLocs;
    static final String[] decimalfspLocs = bipLocs;
    static final String[] calnpLocs = bipLocs;
    static final String[] cpLocs = (", ar, be, bg, ca, cs, da, el, es, et, fi, " +
        "fr, he, hi, hr, hu, is, ja, ko, lt, lv, mk, nb, nb_NO, nn_NO, no, pl, ro, ru, sk, sl, " +
        "sq, sr, sr__#Latn, sv, th, tr, uk, vi, zh, zh_HK, zh_HK_#Hant, " +
        "zh_TW, zh_TW_#Hant, ").split(",\\s*");
    static final String[] nfpLocs = (", ar, ar_AE, ar_BH, ar_DZ, ar_EG, ar_IQ, " +
        "ar_JO, ar_KW, ar_LB, ar_LY, ar_MA, ar_OM, ar_QA, ar_SA, ar_SD, ar_SY, " +
        "ar_TN, ar_YE, be, be_BY, bg, bg_BG, ca, ca_ES, cs, cs_CZ, da, da_DK, " +
        "de, de_AT, de_CH, de_DE, de_LU, el, el_CY, el_GR, en, en_AU, " +
        "en_CA, en_GB, en_IE, en_IN, en_MT, en_NZ, en_PH, en_SG, en_US, en_ZA, " +
        "es, es_AR, es_BO, es_CL, es_CO, es_CR, es_CU, es_DO, es_EC, es_ES, " +
        "es_GT, es_HN, es_MX, es_NI, es_PA, es_PE, es_PR, es_PY, es_SV, es_US, " +
        "es_UY, es_VE, et, et_EE, fi, fi_FI, fr, fr_BE, fr_CA, fr_CH, fr_FR, " +
        "fr_LU, ga, ga_IE, he, he_IL, hi, hi_IN, hr, hr_HR, hu, hu_HU, id, " +
        "id_ID, is, is_IS, it, it_CH, it_IT, ja, ja_JP, " +
        "ja_JP_JP_#u-ca-japanese, ko, ko_KR, lt, lt_LT, lv, lv_LV, " +
        "mk, mk_MK, ms, ms_MY, mt, mt_MT, nb, nb_NO, nl, nl_BE, nl_NL, nn_NO, " +
        "no, no_NO, no_NO_NY, pl, pl_PL, pt, pt_BR, pt_PT, ro, ro_RO, ru, ru_RU, " +
        "sk, sk_SK, sl, sl_SI, sq, sq_AL, sr, sr_BA, sr_BA_#Latn, sr_CS, sr_ME, " +
        "sr_ME_#Latn, sr_RS, sr_RS_#Latn, sr__#Latn, sv, sv_SE, th, " +
        "th_TH, th_TH_TH_#u-nu-thai, tr, tr_TR, uk, uk_UA, vi, " +
        "vi_VN, zh, zh_CN, zh_CN_#Hans, zh_HK, zh_HK_#Hant, zh_SG, zh_SG_#Hans, " +
        "zh_TW, zh_TW_#Hant, ").split(",\\s*");
    static final String[] currencynpLocs = (", ar_AE, ar_BH, ar_DZ, ar_EG, ar_IQ, " +
        "ar_JO, ar_KW, ar_LB, ar_LY, ar_MA, ar_OM, ar_QA, ar_SA, ar_SD, ar_SY, " +
        "ar_TN, ar_YE, be_BY, bg_BG, ca_ES, cs_CZ, da_DK, de, de_AT, de_CH, " +
        "de_DE, de_LU, el_CY, el_GR, en_AU, en_CA, en_GB, en_IE, en_IN, " +
        "en_MT, en_NZ, en_PH, en_SG, en_US, en_ZA, es, es_AR, es_BO, es_CL, " +
        "es_CO, es_CR, es_CU, es_DO, es_EC, es_ES, es_GT, es_HN, es_MX, es_NI, " +
        "es_PA, es_PE, es_PR, es_PY, es_SV, es_US, es_UY, es_VE, et_EE, fi_FI, " +
        "fr, fr_BE, fr_CA, fr_CH, fr_FR, fr_LU, ga_IE, he_IL, hi_IN, hr_HR, " +
        "hu_HU, id_ID, is_IS, it, it_CH, it_IT, ja, ja_JP, ko, ko_KR, lt_LT, " +
        "lv_LV, mk_MK, ms_MY, mt_MT, nb,  nb_NO, nl_BE, nl_NL, nn_NO, no_NO, pl_PL, pt, pt_BR, " +
        "pt_PT, ro_RO, ru_RU, sk_SK, sl_SI, sq_AL, sr_BA, sr_BA_#Latn, sr_CS, " +
        "sr_ME, sr_ME_#Latn, sr_RS, sr_RS_#Latn, sv, sv_SE, th_TH, tr_TR, uk_UA, " +
        "vi_VN, zh_CN, zh_CN_#Hans, zh_HK, zh_HK_#Hant, zh_SG, zh_SG_#Hans, " +
        "zh_TW, zh_TW_#Hant, ").split(",\\s*");
    static final String[] lnpLocs = (", ar, be, bg, ca, cs, da, de, el, el_CY, " +
        "en, en_MT, en_PH, en_SG, es, es_US, et, fi, fr, ga, he, hi, hr, hu, " +
        "id, is, it, ja, ko, lt, lv, mk, ms, mt, nb, nb_NO, nl, nn_NO, no, no_NO_NY, pl, pt, pt_BR, " +
        "pt_PT, ro, ru, sk, sl, sq, sr, sr__#Latn, sv, th, tr, uk, vi, zh, " +
        "zh_HK, zh_HK_#Hant, zh_SG, zh_SG_#Hans, zh_TW, zh_TW_#Hant, ").split(",\\s*");
    static final String[] tznpLocs = (", de, en, en_CA, en_GB, en_IE, es, fr, hi, " +
        "it, ja, ko, nb,  nb_NO, nn_NO, pt_BR, sv, zh_CN, zh_CN_#Hans, zh_HK, zh_HK_#Hant, " +
        "zh_TW, zh_TW_#Hant, ").split(",\\s*");
    static final String[] caldpLocs = (", ar, be, bg, ca, cs, da, de, el, el_CY, " +
        "en, en_GB, en_IE, en_MT, es, es_ES, es_US, et, fi, fr, fr_CA, he, hi, " +
        "hr, hu, id_ID, is, it, ja, ko, lt, lv, mk, ms_MY, mt, mt_MT, nb, nb_NO, nl, nn_NO, no, " +
        "pl, pt, pt_BR, pt_PT, ro, ru, sk, sl, sq, sr, sr_BA_#Latn, sr_ME_#Latn, sr_RS_#Latn, " +
        "sv, th, tr, uk, vi, zh, ").split(",\\s*");
    static final String[] calpLocs = caldpLocs;

    /*
     * Validate whether JRE's *Providers return supported locales list based on
     * their actual resource bundle exsistence. The above golden data
     * are manually extracted, so they need to be updated if new locale
     * data resource bundle were added.
     */
    private static void availableLocalesTests() {
        LocaleProviderAdapter jre = LocaleProviderAdapter.forJRE();

        checkAvailableLocales("BreakIteratorProvider",
            jre.getBreakIteratorProvider().getAvailableLocales(), bipLocs);
        checkAvailableLocales("CollatorProvider",
            jre.getCollatorProvider().getAvailableLocales(), cpLocs);
        checkAvailableLocales("DateFormatProvider",
            jre.getDateFormatProvider().getAvailableLocales(), dfpLocs);
        checkAvailableLocales("DateFormatSymbolsProvider",
            jre.getDateFormatSymbolsProvider().getAvailableLocales(), datefspLocs);
        checkAvailableLocales("DecimalFormatSymbolsProvider",
            jre.getDecimalFormatSymbolsProvider().getAvailableLocales(), decimalfspLocs);
        checkAvailableLocales("NumberFormatProvider",
            jre.getNumberFormatProvider().getAvailableLocales(), nfpLocs);
        checkAvailableLocales("CurrencyNameProvider",
            jre.getCurrencyNameProvider().getAvailableLocales(), currencynpLocs);
        checkAvailableLocales("LocaleNameProvider",
            jre.getLocaleNameProvider().getAvailableLocales(), lnpLocs);
        checkAvailableLocales("TimeZoneNameProvider",
            jre.getTimeZoneNameProvider().getAvailableLocales(), tznpLocs);
        checkAvailableLocales("CalendarDataProvider",
            jre.getCalendarDataProvider().getAvailableLocales(), caldpLocs);
        checkAvailableLocales("CalendarNameProvider",
            jre.getCalendarNameProvider().getAvailableLocales(), calnpLocs);
        checkAvailableLocales("CalendarProvider",
            jre.getCalendarProvider().getAvailableLocales(), calpLocs);
    }

    private static void checkAvailableLocales(String testName, Locale[] got, String[] expected) {
        System.out.println("Testing available locales for " + testName);
        List<String> gotList = Arrays.stream(got)
            .map(Locale::toString)
            .sorted()
            .toList();
        List<String> expectedList = Arrays.stream(expected)
            .toList();

        if (!gotList.equals(expectedList)) {
            throw new RuntimeException("\n" + gotList + "\n is not equal to \n" + expectedList);
        }
    }
}
