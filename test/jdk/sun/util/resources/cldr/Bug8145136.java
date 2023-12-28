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
 * @bug 8145136 8202537 8221432 8251317 8258794 8265315 8306116
 * @modules jdk.localedata
 * @summary Tests LikelySubtags is correctly reflected in JDK.
 * @run main/othervm -Djava.locale.providers=CLDR Bug8145136
 */
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class Bug8145136 {

    public static void main(String[] args) {
        /* This golden data is names of all *.xml files which contain locale
         * specific data in CLDR 44. With LikelySubtags,
         * these locales should be present in output of getAvailableLocales()
         * method.
         */
        List<String> likelySubtagsLocales = List.of(
            "", "af", "af-NA", "af-ZA", "agq", "agq-CM", "ak", "ak-GH", "am", "am-ET", "ar", "ar-001", "ar-AE",
            "ar-BH", "ar-DJ", "ar-DZ", "ar-EG", "ar-EH", "ar-ER", "ar-IL", "ar-IQ", "ar-JO", "ar-KM", "ar-KW",
            "ar-LB", "ar-LY", "ar-MA", "ar-MR", "ar-OM", "ar-PS", "ar-QA", "ar-SA", "ar-SD", "ar-SO", "ar-SS",
            "ar-SY", "ar-TD", "ar-TN", "ar-YE", "as", "as-IN", "asa", "asa-TZ", "ast", "ast-ES", "az", "az-Cyrl",
            "az-Cyrl-AZ", "az-Latn", "az-Latn-AZ", "bas", "bas-CM", "be", "be-BY", "be-TARASK", "bem", "bem-ZM",
            "bez", "bez-TZ", "bg", "bg-BG", "bm", "bm-ML", "bn", "bn-BD", "bn-IN", "bo", "bo-CN", "bo-IN", "br",
            "br-FR", "brx", "brx-IN", "bs", "bs-Cyrl", "bs-Cyrl-BA", "bs-Latn", "bs-Latn-BA", "ca", "ca-AD",
            "ca-ES", "ca-ES-VALENCIA", "ca-FR", "ca-IT", "ccp", "ccp-BD", "ccp-IN", "ce", "ce-RU", "ceb", "ceb-PH",
            "cgg", "cgg-UG", "chr", "chr-US", "ckb", "ckb-IQ", "ckb-IR", "cs", "cs-CZ", "cy", "cy-GB", "da",
            "da-DK", "da-GL", "dav", "dav-KE", "de", "de-AT", "de-BE", "de-CH", "de-DE", "de-IT", "de-LI", "de-LU",
            "dje", "dje-NE", "doi", "doi-IN", "dsb", "dsb-DE", "dua", "dua-CM", "dyo", "dyo-SN", "dz", "dz-BT",
            "ebu", "ebu-KE", "ee", "ee-GH", "ee-TG", "el", "el-CY", "el-GR", "en", "en-001", "en-150", "en-AE",
            "en-AG", "en-AI", "en-AS", "en-AT", "en-AU", "en-BB", "en-BE", "en-BI", "en-BM", "en-BS", "en-BW",
            "en-BZ", "en-CA", "en-CC", "en-CH", "en-CK", "en-CM", "en-CX", "en-CY", "en-DE", "en-DG", "en-DK",
            "en-DM", "en-ER", "en-FI", "en-FJ", "en-FK", "en-FM", "en-GB", "en-GD", "en-GG", "en-GH", "en-GI",
            "en-GM", "en-GU", "en-GY", "en-HK", "en-IE", "en-IL", "en-IM", "en-IN", "en-IO", "en-JE", "en-JM",
            "en-KE", "en-KI", "en-KN", "en-KY", "en-LC", "en-LR", "en-LS", "en-MG", "en-MH", "en-MO", "en-MP",
            "en-MS", "en-MT", "en-MU", "en-MV", "en-MW", "en-MY", "en-NA", "en-NF", "en-NG", "en-NL", "en-NR",
            "en-NU", "en-NZ", "en-PG", "en-PH", "en-PK", "en-PN", "en-PR", "en-PW", "en-RW", "en-SB", "en-SC",
            "en-SD", "en-SE", "en-SG", "en-SH", "en-SI", "en-SL", "en-SS", "en-SX", "en-SZ", "en-TC", "en-TK",
            "en-TO", "en-TT", "en-TV", "en-TZ", "en-UG", "en-UM", "en-US", "en-US-POSIX", "en-VC", "en-VG", "en-VI",
            "en-VU", "en-WS", "en-ZA", "en-ZM", "en-ZW", "eo", "eo-001", "es", "es-419", "es-AR", "es-BO", "es-BR",
            "es-BZ", "es-CL", "es-CO", "es-CR", "es-CU", "es-DO", "es-EA", "es-EC", "es-ES", "es-GQ", "es-GT",
            "es-HN", "es-IC", "es-MX", "es-NI", "es-PA", "es-PE", "es-PH", "es-PR", "es-PY", "es-SV", "es-US",
            "es-UY", "es-VE", "et", "et-EE", "eu", "eu-ES", "ewo", "ewo-CM", "fa", "fa-AF", "fa-IR", "ff",
            "ff-Adlm", "ff-Adlm-BF", "ff-Adlm-CM", "ff-Adlm-GH", "ff-Adlm-GM", "ff-Adlm-GN", "ff-Adlm-GW",
            "ff-Adlm-LR", "ff-Adlm-MR", "ff-Adlm-NE", "ff-Adlm-NG", "ff-Adlm-SL", "ff-Adlm-SN", "ff-Latn",
            "ff-Latn-BF", "ff-Latn-CM", "ff-Latn-GH", "ff-Latn-GM", "ff-Latn-GN", "ff-Latn-GW", "ff-Latn-LR",
            "ff-Latn-MR", "ff-Latn-NE", "ff-Latn-NG", "ff-Latn-SL", "ff-Latn-SN", "fi", "fi-FI", "fil", "fil-PH",
            "fo", "fo-DK", "fo-FO", "fr", "fr-BE", "fr-BF", "fr-BI", "fr-BJ", "fr-BL", "fr-CA", "fr-CD", "fr-CF",
            "fr-CG", "fr-CH", "fr-CI", "fr-CM", "fr-DJ", "fr-DZ", "fr-FR", "fr-GA", "fr-GF", "fr-GN", "fr-GP",
            "fr-GQ", "fr-HT", "fr-KM", "fr-LU", "fr-MA", "fr-MC", "fr-MF", "fr-MG", "fr-ML", "fr-MQ", "fr-MR",
            "fr-MU", "fr-NC", "fr-NE", "fr-PF", "fr-PM", "fr-RE", "fr-RW", "fr-SC", "fr-SN", "fr-SY", "fr-TD",
            "fr-TG", "fr-TN", "fr-VU", "fr-WF", "fr-YT", "fur", "fur-IT", "fy", "fy-NL", "ga", "ga-GB", "ga-IE",
            "gd", "gd-GB", "gl", "gl-ES", "gsw", "gsw-CH", "gsw-FR", "gsw-LI", "gu", "gu-IN", "guz", "guz-KE",
            "gv", "gv-IM", "ha", "ha-GH", "ha-NE", "ha-NG", "haw", "haw-US", "he", "he-IL", "hi", "hi-IN",
            "hi-Latn", "hi-Latn-IN", "hr", "hr-BA", "hr-HR", "hsb", "hsb-DE", "hu", "hu-HU", "hy", "hy-AM", "ia",
            "ia-001", "id", "id-ID", "ig", "ig-NG", "ii", "ii-CN", "is", "is-IS", "it", "it-CH", "it-IT", "it-SM",
            "it-VA", "ja", "ja-JP", "jgo", "jgo-CM", "jmc", "jmc-TZ", "jv", "jv-ID", "ka", "ka-GE", "kab", "kab-DZ",
            "kam", "kam-KE", "kde", "kde-TZ", "kea", "kea-CV", "kgp", "kgp-BR", "khq", "khq-ML", "ki", "ki-KE",
            "kk", "kk-KZ", "kkj", "kkj-CM", "kl", "kl-GL", "kln", "kln-KE", "km", "km-KH", "kn", "kn-IN", "ko",
            "ko-KP", "ko-KR", "kok", "kok-IN", "ks", "ks-Arab", "ks-Arab-IN", "ks-Deva", "ks-Deva-IN", "ksb",
            "ksb-TZ", "ksf", "ksf-CM", "ksh", "ksh-DE", "ku", "ku-TR", "kw", "kw-GB", "ky", "ky-KG", "lag",
            "lag-TZ", "lb", "lb-LU", "lg", "lg-UG", "lkt", "lkt-US", "ln", "ln-AO", "ln-CD", "ln-CF", "ln-CG", "lo",
            "lo-LA", "lrc", "lrc-IQ", "lrc-IR", "lt", "lt-LT", "lu", "lu-CD", "luo", "luo-KE", "luy", "luy-KE",
            "lv", "lv-LV", "mai", "mai-IN", "mas", "mas-KE", "mas-TZ", "mer", "mer-KE", "mfe", "mfe-MU", "mg",
            "mg-MG", "mgh", "mgh-MZ", "mgo", "mgo-CM", "mi", "mi-NZ", "mk", "mk-MK", "ml", "ml-IN", "mn", "mn-MN",
            "mni", "mni-Beng", "mni-Beng-IN", "mr", "mr-IN", "ms", "ms-BN", "ms-ID", "ms-MY", "ms-SG", "mt",
            "mt-MT", "mua", "mua-CM", "my", "my-MM", "mzn", "mzn-IR", "naq", "naq-NA", "nb", "nb-NO", "nb-SJ", "nd",
            "nd-ZW", "nds", "nds-DE", "nds-NL", "ne", "ne-IN", "ne-NP", "nl", "nl-AW", "nl-BE", "nl-BQ", "nl-CW",
            "nl-NL", "nl-SR", "nl-SX", "nmg", "nmg-CM", "nn", "nn-NO", "nnh", "nnh-CM", "no", "nus", "nus-SS",
            "nyn", "nyn-UG", "om", "om-ET", "om-KE", "or", "or-IN", "os", "os-GE", "os-RU", "pa", "pa-Arab",
            "pa-Arab-PK", "pa-Guru", "pa-Guru-IN", "pcm", "pcm-NG", "pl", "pl-PL", "ps", "ps-AF", "ps-PK", "pt",
            "pt-AO", "pt-BR", "pt-CH", "pt-CV", "pt-GQ", "pt-GW", "pt-LU", "pt-MO", "pt-MZ", "pt-PT", "pt-ST",
            "pt-TL", "qu", "qu-BO", "qu-EC", "qu-PE", "rm", "rm-CH", "rn", "rn-BI", "ro", "ro-MD", "ro-RO", "rof",
            "rof-TZ", "ru", "ru-BY", "ru-KG", "ru-KZ", "ru-MD", "ru-RU", "ru-UA", "rw", "rw-RW", "rwk", "rwk-TZ",
            "sa", "sa-IN", "sah", "sah-RU", "saq", "saq-KE", "sat", "sat-Olck", "sat-Olck-IN", "sbp", "sbp-TZ",
            "sc", "sc-IT", "sd", "sd-Arab", "sd-Arab-PK", "sd-Deva", "sd-Deva-IN", "se", "se-FI", "se-NO", "se-SE",
            "seh", "seh-MZ", "ses", "ses-ML", "sg", "sg-CF", "shi", "shi-Latn", "shi-Latn-MA", "shi-Tfng",
            "shi-Tfng-MA", "si", "si-LK", "sk", "sk-SK", "sl", "sl-SI", "smn", "smn-FI", "sn", "sn-ZW", "so",
            "so-DJ", "so-ET", "so-KE", "so-SO", "sq", "sq-AL", "sq-MK", "sq-XK", "sr", "sr-Cyrl", "sr-Cyrl-BA",
            "sr-Cyrl-ME", "sr-Cyrl-RS", "sr-Cyrl-XK", "sr-Latn", "sr-Latn-BA", "sr-Latn-ME", "sr-Latn-RS",
            "sr-Latn-XK", "su", "su-Latn", "su-Latn-ID", "sv", "sv-AX", "sv-FI", "sv-SE", "sw", "sw-CD", "sw-KE",
            "sw-TZ", "sw-UG", "ta", "ta-IN", "ta-LK", "ta-MY", "ta-SG", "te", "te-IN", "teo", "teo-KE", "teo-UG",
            "tg", "tg-TJ", "th", "th-TH", "ti", "ti-ER", "ti-ET", "tk", "tk-TM", "to", "to-TO", "tr", "tr-CY",
            "tr-TR", "tt", "tt-RU", "twq", "twq-NE", "tzm", "tzm-MA", "ug", "ug-CN", "uk", "uk-UA", "ur", "ur-IN",
            "ur-PK", "uz", "uz-Arab", "uz-Arab-AF", "uz-Cyrl", "uz-Cyrl-UZ", "uz-Latn", "uz-Latn-UZ", "vai",
            "vai-Latn", "vai-Latn-LR", "vai-Vaii", "vai-Vaii-LR", "vi", "vi-VN", "vun", "vun-TZ", "wae", "wae-CH",
            "wo", "wo-SN", "xh", "xh-ZA", "xog", "xog-UG", "yav", "yav-CM", "yi", "yi-UA", "yo", "yo-BJ", "yo-NG",
            "yrl", "yrl-BR", "yrl-CO", "yrl-VE", "yue", "yue-Hans", "yue-Hans-CN", "yue-Hant", "yue-Hant-HK", "zgh",
            "zgh-MA", "zh", "zh-Hans", "zh-Hans-CN", "zh-Hans-HK", "zh-Hans-MO", "zh-Hans-SG", "zh-Hant",
            "zh-Hant-HK", "zh-Hant-MO", "zh-Hant-TW", "zu", "zu-ZA");

        List<Locale> availableLocales = Arrays.asList(Locale.getAvailableLocales());

        List<Locale> localesNotFound = likelySubtagsLocales.stream()
                .map(Locale::forLanguageTag)
                .filter(l -> !availableLocales.contains(l))
                .collect(Collectors.toList());

        if (localesNotFound.size() > 0) {
            throw new RuntimeException("Locales " + localesNotFound
                    + " not found in Available Locales list");
        }
    }
}
