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

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.tools.jlink.internal.LinkableRuntimeImage;
import jdk.tools.jlink.internal.TaskHelper;
import jdk.tools.jlink.internal.plugins.PluginsResourceBundle;
import jdk.tools.jlink.plugin.PluginException;
import jdk.test.lib.Platform;
import tests.Helper;
import tests.JImageGenerator;
import tests.JImageValidator;
import tests.Result;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @bug 8152143 8152704 8155649 8165804 8185841 8176841 8190918
 *      8179071 8202537 8221432 8222098 8251317 8258794 8265315
 *      8296248 8306116 8174269 8347146 8346948
 * @summary IncludeLocalesPlugin tests
 * @author Naoto Sato
 * @requires (vm.compMode != "Xcomp" & os.maxMemory >= 2g)
 * @library ../../lib /test/lib
 * @modules java.base/jdk.internal.jimage
 *          jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jlink.internal.plugins
 *          jdk.jlink/jdk.tools.jlink.plugin
 *          jdk.jlink/jdk.tools.jmod
 *          jdk.jlink/jdk.tools.jimage
 *          jdk.compiler
 * @build tests.*
 * @build jdk.test.lib.Platform
 * @build tools.jlink.plugins.GetAvailableLocales
 * @run junit/othervm/timeout=180 -Xmx1g IncludeLocalesPluginTest
 */

public class IncludeLocalesPluginTest {

    private static final String MODULE_NAME = "IncludeLocalesTest";
    private static Helper helper;

    // Test data should include:
    //  - --include-locales command line option
    //  - --add-modules command line option values
    //  - List of required resources in the result image
    //  - List of resources that should not exist in the result image
    //  - List of available locales in the result image
    //  - Error message
    private static Stream<Arguments> testData() {
        return Stream.of(
            // without --include-locales option: should include all locales
            Arguments.of(
                "",
                "jdk.localedata",
                List.of(
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_en_001.class",
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_ja.class",
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_th.class",
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_zh.class"),
                List.of(),
                Arrays.stream(Locale.getAvailableLocales())
                        // "(root)" for Locale.ROOT rather than ""
                        .map(loc -> loc.equals(Locale.ROOT) ? "(root)" : loc.toString())
                        .collect(Collectors.toList()),
                ""),

            // Asterisk works exactly the same as above
            Arguments.of(
                "--include-locales=*",
                "jdk.localedata",
                List.of(
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_en_001.class",
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_ja.class",
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_th.class",
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_zh.class"),
                List.of(),
                Arrays.stream(Locale.getAvailableLocales())
                        // "(root)" for Locale.ROOT rather than ""
                        .map(loc -> loc.equals(Locale.ROOT) ? "(root)" : loc.toString())
                        .collect(Collectors.toList()),
                ""),

            // World English/Spanish in Latin America
            Arguments.of(
                "--include-locales=en-001,es-419",
                "jdk.localedata",
                List.of(
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_en_001.class",
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_en_150.class",
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_en_AT.class",
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_es.class",
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_es_419.class",
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_es_AR.class"),
                List.of(
                        "/jdk.localedata/sun/text/resources/ext/LineBreakIteratorData_th",
                        "/jdk.localedata/sun/text/resources/ext/thai_dict",
                        "/jdk.localedata/sun/text/resources/ext/WordBreakIteratorData_th",
                        "/jdk.localedata/sun/text/resources/ext/BreakIteratorInfo_th.class",
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_ja.class",
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_th.class"),
                List.of(
                        "(root)", "en", "en_001", "en_150", "en_AG", "en_AI",
                        "en_AT", "en_AU", "en_BB", "en_BE", "en_BM", "en_BS", "en_BW", "en_BZ",
                        "en_CC", "en_CH", "en_CK", "en_CM", "en_CX", "en_CY", "en_CZ", "en_DE",
                        "en_DG", "en_DK", "en_DM", "en_ER", "en_ES", "en_FI", "en_FJ", "en_FK", "en_FM", "en_FR",
                        "en_GB", "en_GD", "en_GG", "en_GH", "en_GI", "en_GM", "en_GS", "en_GY", "en_HK", "en_HU", "en_ID",
                        "en_IE", "en_IL", "en_IM", "en_IN", "en_IO", "en_IT", "en_JE", "en_JM", "en_KE",
                        "en_KI", "en_KN", "en_KY", "en_LC", "en_LR", "en_LS", "en_MG", "en_MO",
                        "en_MS", "en_MT", "en_MU", "en_MV", "en_MW", "en_MY", "en_NA", "en_NF", "en_NG",
                        "en_NL", "en_NO", "en_NR", "en_NU", "en_NZ", "en_PG", "en_PK", "en_PL", "en_PN", "en_PT",
                        "en_PW", "en_RO", "en_RW", "en_SB", "en_SC", "en_SD", "en_SE", "en_SG", "en_SH",
                        "en_SI", "en_SK", "en_SL", "en_SS", "en_SX", "en_SZ", "en_TC", "en_TK", "en_TO",
                        "en_TT", "en_TV", "en_TZ", "en_UG", "en_US", "en_US_#Latn", "en_US_POSIX", "en_VC", "en_VG", "en_VU", "en_WS",
                        "en_ZA", "en_ZM", "en_ZW", "es", "es_419", "es_AR", "es_BO", "es_BR", "es_BZ",
                        "es_CL", "es_CO", "es_CR", "es_CU", "es_DO", "es_EC", "es_GT", "es_HN",
                        "es_MX", "es_NI", "es_PA", "es_PE", "es_PR", "es_PY", "es_SV", "es_US",
                        "es_UY", "es_VE",
                        // CLDR's "hi-Latn" falls back to "en-001", "hi-Latn"/"hi-Latn-IN" are added
                        // here. Since Locale.Matcher cannot handle such exceptional inheritance,
                        // allowing to include "hi"/"hi-IN" resource files.
                        "hi", "hi__#Latn", "hi_IN", "hi_IN_#Latn"),
                ""),

            // All English and Japanese locales
            Arguments.of(
                "--include-locales=en,ja",
                "jdk.localedata",
                List.of(
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_en_001.class",
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_ja.class"),
                List.of(
                        "/jdk.localedata/sun/text/resources/ext/LineBreakIteratorData_th",
                        "/jdk.localedata/sun/text/resources/ext/thai_dict",
                        "/jdk.localedata/sun/text/resources/ext/WordBreakIteratorData_th",
                        "/jdk.localedata/sun/text/resources/ext/BreakIteratorInfo_th.class",
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_th.class",
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_zh.class"),
                List.of(
                        "(root)", "en", "en_001", "en_150", "en_AE", "en_AG", "en_AI", "en_AS", "en_AT",
                        "en_AU", "en_BB", "en_BE", "en_BI", "en_BM", "en_BS", "en_BW", "en_BZ",
                        "en_CA", "en_CC", "en_CH", "en_CK", "en_CM", "en_CX", "en_CY", "en_CZ", "en_DE",
                        "en_DG", "en_DK", "en_DM", "en_ER", "en_ES", "en_FI", "en_FJ", "en_FK", "en_FM", "en_FR",
                        "en_GB", "en_GD", "en_GG", "en_GH", "en_GI", "en_GM", "en_GS", "en_GU", "en_GY",
                        "en_HK", "en_HU", "en_ID", "en_IE", "en_IL", "en_IM", "en_IN", "en_IO", "en_IT", "en_JE", "en_JM",
                        "en_KE", "en_KI", "en_KN", "en_KY", "en_LC", "en_LR", "en_LS", "en_MG",
                        "en_MH", "en_MO", "en_MP", "en_MS", "en_MT", "en_MU", "en_MV", "en_MW", "en_MY",
                        "en_NA", "en_NF", "en_NG", "en_NL", "en_NO", "en_NR", "en_NU", "en_NZ", "en_PG",
                        "en_PH", "en_PK", "en_PL", "en_PN", "en_PR", "en_PT", "en_PW", "en_RO", "en_RW", "en_SB", "en_SC",
                        "en_SD", "en_SE", "en_SG", "en_SH", "en_SI", "en_SK", "en_SL", "en_SS", "en_SX",
                        "en_SZ", "en_TC", "en_TK", "en_TO", "en_TT", "en_TV", "en_TZ", "en_UG",
                        "en_UM", "en_US", "en_US_#Latn", "en_US_POSIX", "en_VC", "en_VG", "en_VI", "en_VU",
                        "en_WS", "en_ZA", "en_ZM", "en_ZW", "ja", "ja_JP", "ja_JP_#Jpan",
                        "ja_JP_JP_#u-ca-japanese"),
                ""),

            // All locales in Austria
            Arguments.of(
                "--include-locales=*-AT",
                "jdk.localedata",
                List.of(
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_de.class",
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_de_AT.class",
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_en_001.class",
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_en_150.class",
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_en_AT.class"),
                List.of(
                        "/jdk.localedata/sun/text/resources/ext/LineBreakIteratorData_th",
                        "/jdk.localedata/sun/text/resources/ext/thai_dict",
                        "/jdk.localedata/sun/text/resources/ext/WordBreakIteratorData_th",
                        "/jdk.localedata/sun/text/resources/ext/BreakIteratorInfo_th.class",
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_ja.class",
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_th.class"),
                List.of(
                        "(root)", "en", "en_001", "en_150", "en_AT", "en_US", "en_US_#Latn", "en_US_POSIX",
                        "de", "de_AT"),
                ""),

            // All locales in India
            Arguments.of(
                "--include-locales=*-IN",
                "jdk.localedata",
                List.of(
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_en_001.class",
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_en_IN.class"),
                List.of(
                        "/jdk.localedata/sun/text/resources/ext/LineBreakIteratorData_th",
                        "/jdk.localedata/sun/text/resources/ext/thai_dict",
                        "/jdk.localedata/sun/text/resources/ext/WordBreakIteratorData_th",
                        "/jdk.localedata/sun/text/resources/ext/BreakIteratorInfo_th.class",
                        "/jdk.localedata/sun/text/resources/ext/BreakIteratorResources_th.class",
                        "/jdk.localedata/sun/util/resources/cldr/ext/CalendarData_as_IN.class",
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_ja.class",
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_th.class",
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_zh.class"),
                List.of(
                        "(root)", "as", "as_IN", "as_IN_#Beng", "bgc", "bgc_IN", "bgc_IN_#Deva", "bho", "bho_IN", "bho_IN_#Deva",
                        "bn", "bn_IN", "bo", "bo_IN", "brx", "brx_IN", "brx_IN_#Deva", "ccp", "ccp_IN", "doi", "doi_IN",
                        "doi_IN_#Deva", "en", "en_001", "en_IN", "en_US", "en_US_#Latn", "en_US_POSIX", "gu", "gu_IN",
                        "gu_IN_#Gujr", "hi", "hi__#Latn", "hi_IN", "hi_IN_#Deva", "hi_IN_#Latn", "kn", "kn_IN", "kn_IN_#Knda",
                        "kok", "kok__#Deva", "kok__#Latn", "kok_IN", "kok_IN_#Deva", "kok_IN_#Latn", "ks", "ks__#Arab",
                        "ks__#Deva", "ks_IN", "ks_IN_#Arab", "ks_IN_#Deva", "kxv", "kxv_IN", "kxv_IN_#Deva", "kxv_IN_#Latn",
                        "kxv_IN_#Orya", "kxv_IN_#Telu", "kxv__#Deva", "kxv__#Latn", "kxv__#Orya", "kxv__#Telu",
                        "mai", "mai_IN", "mai_IN_#Deva", "mni", "mni__#Beng", "mni_IN", "mni_IN_#Beng", "ml", "ml_IN",
                        "ml_IN_#Mlym", "mr", "mr_IN", "mr_IN_#Deva", "ne", "ne_IN", "or", "or_IN", "or_IN_#Orya", "pa",
                        "pa__#Guru", "pa_IN", "pa_IN_#Guru", "raj", "raj_IN", "raj_IN_#Deva", "sa", "sa_IN", "sa_IN_#Deva",
                        "sat", "sat__#Olck", "sat_IN", "sat_IN_#Olck", "sd", "sd__#Deva", "sd_IN", "sd_IN_#Deva", "ta", "ta_IN",
                        "ta_IN_#Taml", "te", "te_IN", "te_IN_#Telu", "ur_IN", "ur", "xnr", "xnr_IN", "xnr_IN_#Deva"),
                ""),

            // Thai
            Arguments.of(
                "--include-locales=th",
                "jdk.localedata",
                List.of(
                        "/jdk.localedata/sun/text/resources/ext/LineBreakIteratorData_th",
                        "/jdk.localedata/sun/text/resources/ext/thai_dict",
                        "/jdk.localedata/sun/text/resources/ext/WordBreakIteratorData_th",
                        "/jdk.localedata/sun/text/resources/ext/BreakIteratorInfo_th.class",
                        "/jdk.localedata/sun/text/resources/ext/BreakIteratorResources_th.class"),
                List.of(
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_en_001.class",
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_ja.class",
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_zh.class"),
                List.of(
                        "(root)", "en", "en_US", "en_US_#Latn", "en_US_POSIX", "th", "th_TH",
                        "th_TH_#Thai", "th_TH_TH_#u-nu-thai"),
                ""),

            // Hong Kong
            Arguments.of(
                "--include-locales=zh-HK",
                "jdk.localedata",
                List.of(
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_zh.class"),
                List.of(
                        "/jdk.localedata/sun/text/resources/ext/LineBreakIteratorData_th",
                        "/jdk.localedata/sun/text/resources/ext/thai_dict",
                        "/jdk.localedata/sun/text/resources/ext/WordBreakIteratorData_th",
                        "/jdk.localedata/sun/text/resources/ext/BreakIteratorInfo_th.class",
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_en_001.class",
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_ja.class",
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_th.class"),
                List.of(
                        "(root)", "en", "en_US", "en_US_#Latn", "en_US_POSIX", "zh", "zh__#Hans", "zh__#Hant",
                        "zh_HK", "zh_HK_#Hans", "zh_HK_#Hant"),
                ""),

            // Simplified Chinese
            Arguments.of(
                "--include-locales=zh-Hans",
                "jdk.localedata",
                List.of(
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_zh.class"),
                List.of(
                        "/jdk.localedata/sun/text/resources/ext/LineBreakIteratorData_th",
                        "/jdk.localedata/sun/text/resources/ext/thai_dict",
                        "/jdk.localedata/sun/text/resources/ext/WordBreakIteratorData_th",
                        "/jdk.localedata/sun/text/resources/ext/BreakIteratorInfo_th.class",
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_en_001.class",
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_ja.class",
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_th.class"),
                List.of(
                        "(root)", "en", "en_US", "en_US_#Latn", "en_US_POSIX", "zh", "zh__#Latn", "zh__#Hans", "zh_CN",
                        "zh_CN_#Latn", "zh_CN_#Hans", "zh_HK", "zh_HK_#Hans", "zh_MO", "zh_MO_#Hans", "zh_MY_#Hans", "zh_SG",
                        "zh_SG_#Hans"),
                ""),

            // Norwegian
            Arguments.of(
                "--include-locales=nb,nn,no",
                "jdk.localedata",
                List.of(
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_nb.class",
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_nn.class",
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_no.class"),
                List.of(
                        "/jdk.localedata/sun/text/resources/ext/LineBreakIteratorData_th",
                        "/jdk.localedata/sun/text/resources/ext/thai_dict",
                        "/jdk.localedata/sun/text/resources/ext/WordBreakIteratorData_th",
                        "/jdk.localedata/sun/text/resources/ext/BreakIteratorInfo_th.class",
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_en_001.class",
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_ja.class",
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_th.class"),
                List.of(
                        "(root)", "en", "en_US", "en_US_#Latn", "en_US_POSIX", "nb", "nb_NO",
                        "nb_NO_#Latn", "nb_SJ", "nn", "nn_NO", "nn_NO_#Latn", "no", "no_NO", "no_NO_NY",
                        "no_NO_#Latn"),
                ""),

            // Hebrew/Indonesian/Yiddish
            Arguments.of(
                "--include-locales=he,id,yi",
                "jdk.localedata",
                List.of(
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_he.class",
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_id.class",
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_yi.class"),
                List.of(
                        "/jdk.localedata/sun/text/resources/ext/LineBreakIteratorData_th",
                        "/jdk.localedata/sun/text/resources/ext/thai_dict",
                        "/jdk.localedata/sun/text/resources/ext/WordBreakIteratorData_th",
                        "/jdk.localedata/sun/text/resources/ext/BreakIteratorInfo_th.class",
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_en_001.class",
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_ja.class",
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_th.class"),
                List.of(
                        "(root)", "en", "en_US", "en_US_#Latn", "en_US_POSIX", "id", "id_ID",
                        "id_ID_#Latn", "he", "he_IL", "he_IL_#Hebr", "yi", "yi_UA", "yi_UA_#Hebr"),
                ""),

            // Langtag including extensions. Should be ignored.
            Arguments.of(
                "--include-locales=en,ja-u-nu-thai",
                "jdk.localedata",
                List.of(
                        "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_en_001.class"),
                List.of(),
                List.of(
                        "(root)", "en", "en_001", "en_150", "en_AE", "en_AG", "en_AI", "en_AS", "en_AT",
                        "en_AU", "en_BB", "en_BE", "en_BI", "en_BM", "en_BS", "en_BW", "en_BZ",
                        "en_CA", "en_CC", "en_CH", "en_CK", "en_CM", "en_CX", "en_CY", "en_CZ", "en_DE",
                        "en_DG", "en_DK", "en_DM", "en_ER", "en_ES", "en_FI", "en_FJ", "en_FK", "en_FM", "en_FR",
                        "en_GB", "en_GD", "en_GG", "en_GH", "en_GI", "en_GM", "en_GS", "en_GU", "en_GY",
                        "en_HK", "en_HU", "en_ID", "en_IE", "en_IL", "en_IM", "en_IN", "en_IO", "en_IT", "en_JE", "en_JM",
                        "en_KE", "en_KI", "en_KN", "en_KY", "en_LC", "en_LR", "en_LS", "en_MG",
                        "en_MH", "en_MO", "en_MP", "en_MS", "en_MT", "en_MU", "en_MV", "en_MW", "en_MY",
                        "en_NA", "en_NF", "en_NG", "en_NL", "en_NO", "en_NR", "en_NU", "en_NZ", "en_PG",
                        "en_PH", "en_PK", "en_PL", "en_PN", "en_PR", "en_PT", "en_PW", "en_RO", "en_RW", "en_SB", "en_SC",
                        "en_SD", "en_SE", "en_SG", "en_SH", "en_SI", "en_SK", "en_SL", "en_SS", "en_SX",
                        "en_SZ", "en_TC", "en_TK", "en_TO", "en_TT", "en_TV", "en_TZ", "en_UG",
                        "en_UM", "en_US", "en_US_#Latn", "en_US_POSIX", "en_VC", "en_VG", "en_VI", "en_VU",
                        "en_WS", "en_ZA", "en_ZM", "en_ZW"),
                ""),

            // Error case: No matching locales
            Arguments.of(
                "--include-locales=xyz",
                "jdk.localedata",
                null,
                null,
                null,
                new PluginException(String.format(
                        PluginsResourceBundle.getMessage("include-locales.nomatchinglocales"), "xyz"))
                        .getMessage()),

            // Error case: Invalid argument
            Arguments.of(
                "--include-locales=en,zh_HK",
                "jdk.localedata",
                null,
                null,
                null,
                new PluginException(String.format(
                        PluginsResourceBundle.getMessage("include-locales.invalidtag"), "zh_hk"))
                        .getMessage()),

            // Error case: jdk.localedata is not added
            Arguments.of(
                "--include-locales=en-US",
                "java.base",
                null,
                null,
                null,
                new PluginException(
                        PluginsResourceBundle.getMessage("include-locales.localedatanotfound"))
                        .getMessage())
        );
    }

    @BeforeAll
    public static void setup() throws IOException {
        boolean isLinkableRuntime = LinkableRuntimeImage.isLinkableRuntime();
        System.out.println("Running test on " +
                           (isLinkableRuntime ? "enabled" : "disabled") +
                           " capability of linking from the run-time image.");
        System.out.println("Default module-path, 'jmods', " +
                           (Helper.jdkHasPackagedModules() ? "" : "NOT ") +
                           "present.");

        helper = Helper.newHelper(isLinkableRuntime);
        assertNotNull(helper, "Helper could not be initialized");
    }

    @ParameterizedTest
    @MethodSource("testData")
    public void launch(String optIncludeLocales, String optAddModules, List<String> requiredRes,
                       List<String> shouldNotExistRes, List<String> availableLocs, String errorMsg) throws Exception {
        // create image for each test data
        Result result;
        if (optIncludeLocales.isEmpty()) {
            System.out.println("Invoking jlink with no --include-locales option");
            result = JImageGenerator.getJLinkTask()
                .output(helper.createNewImageDir(MODULE_NAME))
                .addMods(optAddModules)
                .call();
        } else {
            System.out.println("Invoking jlink with \"" + optIncludeLocales + "\"");
            result = JImageGenerator.getJLinkTask()
                .output(helper.createNewImageDir(MODULE_NAME))
                .addMods(optAddModules)
                .option(optIncludeLocales)
                .call();
        }

        if (errorMsg.isEmpty()) {
            Path image = result.assertSuccess();

            // test locale data entries
            testLocaleDataEntries(image, requiredRes, shouldNotExistRes);

            // test available locales
            testAvailableLocales(image, availableLocs);
        } else {
            result.assertFailure(new TaskHelper(TaskHelper.JLINK_BUNDLE)
                .getMessage("error.prefix") + " " +errorMsg);
            System.out.println("\tExpected failure: " + result.getMessage());
        }
    }

    private static void testLocaleDataEntries(Path image, List<String> expectedLocations,
                        List<String> unexpectedPaths) throws Exception {
        System.out.println("testLocaleDataEntries:");
        try {
            JImageValidator.validate(
                image.resolve("lib").resolve("modules"),
                expectedLocations, unexpectedPaths);
        } catch (Exception e) {
            fail("\tFailed with: " + e);
        }
    }

    private static void testAvailableLocales(Path image, List<String> availableLocales) throws Exception {
        System.out.println("testAvailableLocales:");
        Path launcher = image.resolve("bin/java" + (Platform.isWindows() ? ".exe" : ""));
        List<String> args = new ArrayList<>(availableLocales.size() + 2);
        args.add(launcher.toString());
        args.add("GetAvailableLocales");
        args.addAll(availableLocales);
        Process proc = new ProcessBuilder(args).inheritIO().start();

        int len = Math.min(10, args.size());
        String command = args.subList(0, len).stream().collect(Collectors.joining(" "))
                         + (len < availableLocales.size() ? " ..." : "");

        int status = proc.waitFor();
        assertTrue(status == 0, "\tExit " + status + "\t" + command);
        System.out.println("\tDone\t" + command);
        System.out.println();
    }
}
