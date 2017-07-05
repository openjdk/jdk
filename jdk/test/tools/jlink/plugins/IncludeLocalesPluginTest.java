/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Path;
import java.util.List;

import jdk.tools.jlink.plugin.Plugin;
import jdk.tools.jlink.plugin.PluginException;
import jdk.tools.jlink.internal.PluginRepository;
import jdk.tools.jlink.internal.TaskHelper;
import jdk.tools.jlink.internal.plugins.PluginsResourceBundle;
import tests.Helper;
import tests.JImageGenerator;
import tests.JImageValidator;
import tests.Result;

/*
 * @test
 * @summary IncludeLocalesPlugin tests
 * @author Naoto Sato
 * @library ../../lib
 * @modules java.base/jdk.internal.jimage
 *          jdk.jdeps/com.sun.tools.classfile
 *          jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jlink.internal.plugins
 *          jdk.jlink/jdk.tools.jmod
 *          jdk.jlink/jdk.tools.jimage
 *          jdk.compiler
 * @build tests.*
 * @build tools.jlink.plugins.GetAvailableLocales
 * @run main/othervm -verbose:gc -Xmx1g IncludeLocalesPluginTest
 */
public class IncludeLocalesPluginTest {

    private final static String moduleName = "IncludeLocalesTest";
    private static Helper helper;
    private final static int INCLUDE_LOCALES_OPTION = 0;
    private final static int EXPECTED_LOCATIONS     = 1;
    private final static int UNEXPECTED_PATHS       = 2;
    private final static int AVAILABLE_LOCALES      = 3;
    private final static int ERROR_MESSAGE          = 4;

    private final static Object[][] testData = {
        // without --include-locales option: should include all locales
        {
            "",
            List.of(
                "/jdk.localedata/sun/text/resources/ext/FormatData_en_GB.class",
                "/jdk.localedata/sun/text/resources/ext/FormatData_ja.class",
                "/jdk.localedata/sun/text/resources/ext/FormatData_th.class",
                "/jdk.localedata/sun/text/resources/ext/FormatData_zh.class",
                "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_en_001.class",
                "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_ja.class",
                "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_th.class",
                "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_zh.class"),
            List.of(),
            " af af_NA af_ZA agq agq_CM ak ak_GH am am_ET ar ar_001 ar_AE ar_BH " +
            "ar_DJ ar_DZ ar_EG ar_EH ar_ER ar_IL ar_IQ ar_JO ar_KM ar_KW ar_LB " +
            "ar_LY ar_MA ar_MR ar_OM ar_PS ar_QA ar_SA ar_SD ar_SO ar_SS ar_SY " +
            "ar_TD ar_TN ar_YE as as_IN asa asa_TZ ast ast_ES az az_AZ_#Cyrl " +
            "az_AZ_#Latn az__#Cyrl az__#Latn bas bas_CM be be_BY bem bem_ZM bez " +
            "bez_TZ bg bg_BG bm bm_ML_#Latn bm__#Latn bn bn_BD bn_IN bo bo_CN " +
            "bo_IN br br_FR brx brx_IN bs bs_BA_#Cyrl bs_BA_#Latn bs__#Cyrl " +
            "bs__#Latn ca ca_AD ca_ES ca_ES_VALENCIA ca_FR ca_IT cgg cgg_UG chr " +
            "chr_US cs cs_CZ cy cy_GB da da_DK da_GL dav dav_KE de de_AT de_BE " +
            "de_CH de_DE de_GR de_LI de_LU dje dje_NE dsb dsb_DE dua dua_CM dyo " +
            "dyo_SN dz dz_BT ebu ebu_KE ee ee_GH ee_TG el el_CY el_GR en en_001 " +
            "en_150 en_AG en_AI en_AS en_AU en_BB en_BE en_BM en_BS en_BW en_BZ " +
            "en_CA en_CC en_CK en_CM en_CX en_DG en_DM en_ER en_FJ en_FK en_FM " +
            "en_GB en_GD en_GG en_GH en_GI en_GM en_GU en_GY en_HK en_IE en_IM " +
            "en_IN en_IO en_JE en_JM en_KE en_KI en_KN en_KY en_LC en_LR en_LS " +
            "en_MG en_MH en_MO en_MP en_MS en_MT en_MU en_MW en_MY en_NA en_NF " +
            "en_NG en_NR en_NU en_NZ en_PG en_PH en_PK en_PN en_PR en_PW en_RW " +
            "en_SB en_SC en_SD en_SG en_SH en_SL en_SS en_SX en_SZ en_TC en_TK " +
            "en_TO en_TT en_TV en_TZ en_UG en_UM en_US en_US_POSIX en_VC en_VG " +
            "en_VI en_VU en_WS en_ZA en_ZM en_ZW eo eo_001 es es_419 es_AR es_BO " +
            "es_CL es_CO es_CR es_CU es_DO es_EA es_EC es_ES es_GQ es_GT es_HN " +
            "es_IC es_MX es_NI es_PA es_PE es_PH es_PR es_PY es_SV es_US es_UY " +
            "es_VE et et_EE eu eu_ES ewo ewo_CM fa fa_AF fa_IR ff ff_CM ff_GN " +
            "ff_MR ff_SN fi fi_FI fil fil_PH fo fo_FO fr fr_BE fr_BF fr_BI fr_BJ " +
            "fr_BL fr_CA fr_CD fr_CF fr_CG fr_CH fr_CI fr_CM fr_DJ fr_DZ fr_FR " +
            "fr_GA fr_GF fr_GN fr_GP fr_GQ fr_HT fr_KM fr_LU fr_MA fr_MC fr_MF " +
            "fr_MG fr_ML fr_MQ fr_MR fr_MU fr_NC fr_NE fr_PF fr_PM fr_RE fr_RW " +
            "fr_SC fr_SN fr_SY fr_TD fr_TG fr_TN fr_VU fr_WF fr_YT fur fur_IT fy " +
            "fy_NL ga ga_IE gd gd_GB gl gl_ES gsw gsw_CH gsw_FR gsw_LI gu gu_IN " +
            "guz guz_KE gv gv_IM ha ha_GH_#Latn ha_NE_#Latn ha_NG_#Latn ha__#Latn " +
            "haw haw_US hi hi_IN hr hr_BA hr_HR hsb hsb_DE hu hu_HU hy hy_AM ig " +
            "ig_NG ii ii_CN in in_ID is is_IS it it_CH it_IT it_SM iw iw_IL ja " +
            "ja_JP ja_JP_JP_#u-ca-japanese jgo jgo_CM ji ji_001 jmc jmc_TZ ka " +
            "ka_GE kab kab_DZ kam kam_KE kde kde_TZ kea kea_CV khq khq_ML ki " +
            "ki_KE kk kk_KZ_#Cyrl kk__#Cyrl kkj kkj_CM kl kl_GL kln kln_KE km " +
            "km_KH kn kn_IN ko ko_KP ko_KR kok kok_IN ks ks_IN_#Arab ks__#Arab " +
            "ksb ksb_TZ ksf ksf_CM ksh ksh_DE kw kw_GB ky ky_KG_#Cyrl ky__#Cyrl " +
            "lag lag_TZ lb lb_LU lg lg_UG lkt lkt_US ln ln_AO ln_CD ln_CF ln_CG " +
            "lo lo_LA lt lt_LT lu lu_CD luo luo_KE luy luy_KE lv lv_LV mas " +
            "mas_KE mas_TZ mer mer_KE mfe mfe_MU mg mg_MG mgh mgh_MZ mgo mgo_CM " +
            "mk mk_MK ml ml_IN mn mn_MN_#Cyrl mn__#Cyrl mr mr_IN ms ms_BN_#Latn " +
            "ms_MY ms_MY_#Latn ms_SG_#Latn ms__#Latn mt mt_MT mua mua_CM my " +
            "my_MM naq naq_NA nb nb_NO nb_SJ nd nd_ZW ne ne_IN ne_NP nl nl_AW " +
            "nl_BE nl_BQ nl_CW nl_NL nl_SR nl_SX nmg nmg_CM nn nn_NO nnh nnh_CM " +
            "no no_NO no_NO_NY nus nus_SD nyn nyn_UG om om_ET om_KE or or_IN os " +
            "os_GE os_RU pa pa_IN_#Guru pa_PK_#Arab pa__#Arab pa__#Guru pl pl_PL " +
            "ps ps_AF pt pt_AO pt_BR pt_CV pt_GW pt_MO pt_MZ pt_PT pt_ST pt_TL qu " +
            "qu_BO qu_EC qu_PE rm rm_CH rn rn_BI ro ro_MD ro_RO rof rof_TZ ru " +
            "ru_BY ru_KG ru_KZ ru_MD ru_RU ru_UA rw rw_RW rwk rwk_TZ sah sah_RU " +
            "saq saq_KE sbp sbp_TZ se se_FI se_NO se_SE seh seh_MZ ses ses_ML sg " +
            "sg_CF shi shi_MA_#Latn shi_MA_#Tfng shi__#Latn shi__#Tfng si si_LK " +
            "sk sk_SK sl sl_SI smn smn_FI sn sn_ZW so so_DJ so_ET so_KE so_SO sq " +
            "sq_AL sq_MK sq_XK sr sr_BA sr_BA_#Cyrl sr_BA_#Latn sr_CS sr_ME " +
            "sr_ME_#Cyrl sr_ME_#Latn sr_RS sr_RS_#Cyrl sr_RS_#Latn sr_XK_#Cyrl " +
            "sr_XK_#Latn sr__#Cyrl sr__#Latn sv sv_AX sv_FI sv_SE sw sw_CD sw_KE " +
            "sw_TZ sw_UG ta ta_IN ta_LK ta_MY ta_SG te te_IN teo teo_KE teo_UG " +
            "th th_TH th_TH_TH_#u-nu-thai ti ti_ER ti_ET to to_TO tr tr_CY tr_TR " +
            "twq twq_NE tzm tzm_MA_#Latn tzm__#Latn ug ug_CN_#Arab ug__#Arab uk " +
            "uk_UA ur ur_IN ur_PK uz uz_AF_#Arab uz_UZ_#Cyrl uz_UZ_#Latn " +
            "uz__#Arab uz__#Cyrl uz__#Latn vai vai_LR_#Latn vai_LR_#Vaii " +
            "vai__#Latn vai__#Vaii vi vi_VN vun vun_TZ wae wae_CH xog xog_UG yav " +
            "yav_CM yo yo_BJ yo_NG zgh zgh_MA zh zh_CN zh_CN_#Hans zh_HK " +
            "zh_HK_#Hans zh_HK_#Hant zh_MO_#Hans zh_MO_#Hant zh_SG zh_SG_#Hans " +
            "zh_TW zh_TW_#Hant zh__#Hans zh__#Hant zu zu_ZA",
            "",
        },

        // All English/Japanese locales
        {
            "--include-locales=en,ja",
            List.of(
                "/jdk.localedata/sun/text/resources/ext/FormatData_en_GB.class",
                "/jdk.localedata/sun/text/resources/ext/FormatData_ja.class",
                "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_en_001.class",
                "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_ja.class"),
            List.of(
                "/jdk.localedata/sun/text/resources/LineBreakIteratorData_th",
                "/jdk.localedata/sun/text/resources/thai_dict",
                "/jdk.localedata/sun/text/resources/WordBreakIteratorData_th",
                "/jdk.localedata/sun/text/resources/ext/BreakIteratorInfo_th.class",
                "/jdk.localedata/sun/text/resources/ext/BreakIteratorRules_th.class",
                "/jdk.localedata/sun/text/resources/ext/FormatData_th.class",
                "/jdk.localedata/sun/text/resources/ext/FormatData_zh.class",
                "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_th.class",
                "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_zh.class"),
            " en en_001 en_150 en_AG en_AI en_AS en_AU en_BB en_BE en_BM en_BS " +
            "en_BW en_BZ en_CA en_CC en_CK en_CM en_CX en_DG en_DM en_ER en_FJ " +
            "en_FK en_FM en_GB en_GD en_GG en_GH en_GI en_GM en_GU en_GY en_HK " +
            "en_IE en_IM en_IN en_IO en_JE en_JM en_KE en_KI en_KN en_KY en_LC " +
            "en_LR en_LS en_MG en_MH en_MO en_MP en_MS en_MT en_MU en_MW en_MY " +
            "en_NA en_NF en_NG en_NR en_NU en_NZ en_PG en_PH en_PK en_PN en_PR " +
            "en_PW en_RW en_SB en_SC en_SD en_SG en_SH en_SL en_SS en_SX en_SZ " +
            "en_TC en_TK en_TO en_TT en_TV en_TZ en_UG en_UM en_US en_US_POSIX " +
            "en_VC en_VG en_VI en_VU en_WS en_ZA en_ZM en_ZW ja ja_JP ja_JP_JP_#u-ca-japanese",
            "",
        },

        // All locales in India
        {
            "--include-locales=*-IN",
            List.of(
                "/jdk.localedata/sun/text/resources/ext/FormatData_en_IN.class",
                "/jdk.localedata/sun/text/resources/ext/FormatData_hi_IN.class",
                "/jdk.localedata/sun/util/resources/cldr/ext/CalendarData_as_IN.class",
                "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_en_001.class",
                "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_en_IN.class",
                "/jdk.localedata/sun/util/resources/cldr/ext/CalendarData_kok_IN.class",
                "/jdk.localedata/sun/util/resources/cldr/ext/CalendarData_ks_Arab_IN.class"),
            List.of(
                "/jdk.localedata/sun/text/resources/LineBreakIteratorData_th",
                "/jdk.localedata/sun/text/resources/thai_dict",
                "/jdk.localedata/sun/text/resources/WordBreakIteratorData_th",
                "/jdk.localedata/sun/text/resources/ext/BreakIteratorInfo_th.class",
                "/jdk.localedata/sun/text/resources/ext/BreakIteratorRules_th.class",
                "/jdk.localedata/sun/text/resources/ext/FormatData_en_GB.class",
                "/jdk.localedata/sun/text/resources/ext/FormatData_ja.class",
                "/jdk.localedata/sun/text/resources/ext/FormatData_th.class",
                "/jdk.localedata/sun/text/resources/ext/FormatData_zh.class",
                "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_ja.class",
                "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_th.class",
                "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_zh.class"),
            " as_IN bn_IN bo_IN brx_IN en en_IN en_US en_US_POSIX gu_IN hi_IN kn_IN " +
            "kok_IN ks_IN_#Arab ml_IN mr_IN ne_IN or_IN pa_IN_#Guru ta_IN te_IN ur_IN",
            "",
        },

        // Thai
        {"--include-locales=th",
            List.of(
                "/jdk.localedata/sun/text/resources/LineBreakIteratorData_th",
                "/jdk.localedata/sun/text/resources/thai_dict",
                "/jdk.localedata/sun/text/resources/WordBreakIteratorData_th",
                "/jdk.localedata/sun/text/resources/ext/BreakIteratorInfo_th.class",
                "/jdk.localedata/sun/text/resources/ext/BreakIteratorRules_th.class",
                "/jdk.localedata/sun/text/resources/ext/FormatData_th.class"),
            List.of(
                "/jdk.localedata/sun/text/resources/ext/FormatData_en_GB.class",
                "/jdk.localedata/sun/text/resources/ext/FormatData_ja.class",
                "/jdk.localedata/sun/text/resources/ext/FormatData_zh.class",
                "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_en_001.class",
                "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_ja.class",
                "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_zh.class"),
            " en en_US en_US_POSIX th th_TH th_TH_TH_#u-nu-thai",
            "",
        },

        // Hong Kong
        {"--include-locales=zh-HK",
            List.of(
                "/jdk.localedata/sun/text/resources/ext/FormatData_zh.class",
                "/jdk.localedata/sun/text/resources/ext/FormatData_zh_HK.class",
                "/jdk.localedata/sun/text/resources/ext/FormatData_zh_TW.class",
                "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_zh.class"),
            List.of(
                "/jdk.localedata/sun/text/resources/LineBreakIteratorData_th",
                "/jdk.localedata/sun/text/resources/thai_dict",
                "/jdk.localedata/sun/text/resources/WordBreakIteratorData_th",
                "/jdk.localedata/sun/text/resources/ext/BreakIteratorInfo_th.class",
                "/jdk.localedata/sun/text/resources/ext/BreakIteratorRules_th.class",
                "/jdk.localedata/sun/text/resources/ext/FormatData_en_GB.class",
                "/jdk.localedata/sun/text/resources/ext/FormatData_ja.class",
                "/jdk.localedata/sun/text/resources/ext/FormatData_th.class",
                "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_en_001.class",
                "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_ja.class",
                "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_th.class"),
            " en en_US en_US_POSIX zh_HK zh_HK_#Hans zh_HK_#Hant",
            "",
        },

        // Norwegian
        {"--include-locales=nb,nn,no",
            List.of(
                "/jdk.localedata/sun/text/resources/ext/FormatData_no.class",
                "/jdk.localedata/sun/text/resources/ext/FormatData_no_NO.class",
                "/jdk.localedata/sun/text/resources/ext/FormatData_no_NO_NY.class",
                "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_nb.class",
                "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_nn.class"),
            List.of(
                "/jdk.localedata/sun/text/resources/LineBreakIteratorData_th",
                "/jdk.localedata/sun/text/resources/thai_dict",
                "/jdk.localedata/sun/text/resources/WordBreakIteratorData_th",
                "/jdk.localedata/sun/text/resources/ext/BreakIteratorInfo_th.class",
                "/jdk.localedata/sun/text/resources/ext/BreakIteratorRules_th.class",
                "/jdk.localedata/sun/text/resources/ext/FormatData_en_GB.class",
                "/jdk.localedata/sun/text/resources/ext/FormatData_ja.class",
                "/jdk.localedata/sun/text/resources/ext/FormatData_th.class",
                "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_en_001.class",
                "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_ja.class",
                "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_th.class"),
            " en en_US en_US_POSIX nb nb_NO nb_SJ nn nn_NO no no_NO no_NO_NY",
            "",
        },

        // Hebrew/Indonesian/Yiddish
        {"--include-locales=he,id,yi",
            List.of(
                "/jdk.localedata/sun/text/resources/ext/FormatData_in.class",
                "/jdk.localedata/sun/text/resources/ext/FormatData_in_ID.class",
                "/jdk.localedata/sun/text/resources/ext/FormatData_iw.class",
                "/jdk.localedata/sun/text/resources/ext/FormatData_iw_IL.class",
                "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_in.class",
                "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_iw.class",
                "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_ji.class"),
            List.of(
                "/jdk.localedata/sun/text/resources/LineBreakIteratorData_th",
                "/jdk.localedata/sun/text/resources/thai_dict",
                "/jdk.localedata/sun/text/resources/WordBreakIteratorData_th",
                "/jdk.localedata/sun/text/resources/ext/BreakIteratorInfo_th.class",
                "/jdk.localedata/sun/text/resources/ext/BreakIteratorRules_th.class",
                "/jdk.localedata/sun/text/resources/ext/FormatData_en_GB.class",
                "/jdk.localedata/sun/text/resources/ext/FormatData_ja.class",
                "/jdk.localedata/sun/text/resources/ext/FormatData_th.class",
                "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_en_001.class",
                "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_ja.class",
                "/jdk.localedata/sun/text/resources/cldr/ext/FormatData_th.class"),
            " en en_US en_US_POSIX in in_ID iw iw_IL ji ji_001",
            "",
        },

        // Error case: No matching locales
        {"--include-locales=xyz",
            null,
            null,
            null,
            new PluginException(String.format(
                PluginsResourceBundle.getMessage("include-locales.nomatchinglocales"), "xyz"))
                .getMessage(),
        },

        // Error case: Invalid argument
        {"--include-locales=en,zh_HK",
            null,
            null,
            null,
            new PluginException(String.format(
                PluginsResourceBundle.getMessage("include-locales.invalidtag"), "zh_HK"))
                .getMessage(),
        },
    };

    public static void main(String[] args) throws Exception {
        helper = Helper.newHelper();
        if (helper == null) {
            System.err.println("Test not run");
            return;
        }

        helper.generateDefaultModules();

        for (Object[] data : testData) {
            // create image for each test data
            Result result = JImageGenerator.getJLinkTask()
                    .modulePath(helper.defaultModulePath())
                    .output(helper.createNewImageDir(moduleName))
                    .addMods("jdk.localedata")
                    .option((String)data[INCLUDE_LOCALES_OPTION])
                    .call();

            String errorMsg = (String)data[ERROR_MESSAGE];
            if (errorMsg.isEmpty()) {
                Path image = result.assertSuccess();

                // test locale data entries
                testLocaleDataEntries(image,
                    (List<String>)data[EXPECTED_LOCATIONS],
                    (List<String>)data[UNEXPECTED_PATHS]);

                // test available locales
                testAvailableLocales(image, (String)data[AVAILABLE_LOCALES]);
            } else {
                result.assertFailure(new TaskHelper(TaskHelper.JLINK_BUNDLE)
                    .getMessage("error.prefix") + " " +errorMsg);
            }
        }
    }

    private static void testLocaleDataEntries(Path image, List<String> expectedLocations,
                        List<String> unexpectedPaths) throws Exception {
        JImageValidator.validate(
            image.resolve("lib").resolve("modules"),
            expectedLocations, unexpectedPaths);
    }

    private static void testAvailableLocales(Path image, String availableLocales) throws Exception {
        Path launcher = image.resolve("bin/java" +
            (System.getProperty("os.name").startsWith("Windows") ? ".exe" : ""));
        System.out.print(launcher);
        ProcessBuilder pb = new ProcessBuilder(launcher.toString(),
            "GetAvailableLocales", availableLocales);
        int ret = pb.start().waitFor();
        System.out.println(" Return code: " + ret);
    }
}
