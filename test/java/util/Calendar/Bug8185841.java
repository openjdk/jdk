/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8185841
 * @summary Test that Region dependent Bundles are added/removed correctly.
 * @modules jdk.localedata
 */

 /*
This test is dependent on a particular version of CLDR.
 */
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class Bug8185841 {
    // Golden data for Region dependent Bundles in CLDR29.

    private static final Set<String> expectedBundles
            = Set.of("CalendarData_af_NA.class", "CalendarData_af_ZA.class", "CalendarData_agq_CM.class",
                    "CalendarData_ak_GH.class", "CalendarData_am_ET.class", "CalendarData_ar_AE.class",
                    "CalendarData_ar_BH.class", "CalendarData_ar_DJ.class", "CalendarData_ar_DZ.class",
                    "CalendarData_ar_EG.class", "CalendarData_ar_EH.class", "CalendarData_ar_ER.class",
                    "CalendarData_ar_IL.class", "CalendarData_ar_IQ.class", "CalendarData_ar_JO.class",
                    "CalendarData_ar_KM.class", "CalendarData_ar_KW.class", "CalendarData_ar_LB.class",
                    "CalendarData_ar_LY.class", "CalendarData_ar_MA.class", "CalendarData_ar_MR.class",
                    "CalendarData_ar_OM.class", "CalendarData_ar_PS.class", "CalendarData_ar_QA.class",
                    "CalendarData_ar_SA.class", "CalendarData_ar_SD.class", "CalendarData_ar_SO.class",
                    "CalendarData_ar_SS.class", "CalendarData_ar_SY.class", "CalendarData_ar_TD.class",
                    "CalendarData_ar_TN.class", "CalendarData_ar_YE.class", "CalendarData_as_IN.class",
                    "CalendarData_asa_TZ.class", "CalendarData_ast_ES.class", "CalendarData_az_AZ.class",
                    "CalendarData_az_Cyrl_AZ.class", "CalendarData_bas_CM.class", "CalendarData_be_BY.class",
                    "CalendarData_bem_ZM.class", "CalendarData_bez_TZ.class", "CalendarData_bg_BG.class",
                    "CalendarData_bm_ML.class", "CalendarData_bn_BD.class", "CalendarData_bn_IN.class",
                    "CalendarData_bo_CN.class", "CalendarData_bo_IN.class", "CalendarData_br_FR.class",
                    "CalendarData_brx_IN.class", "CalendarData_bs_BA.class", "CalendarData_bs_Cyrl_BA.class",
                    "CalendarData_ca_AD.class", "CalendarData_ca_ES.class", "CalendarData_ca_FR.class",
                    "CalendarData_ca_IT.class", "CalendarData_ce_RU.class", "CalendarData_cgg_UG.class",
                    "CalendarData_chr_US.class", "CalendarData_ckb_IQ.class", "CalendarData_ckb_IR.class",
                    "CalendarData_cs_CZ.class", "CalendarData_cu_RU.class", "CalendarData_cy_GB.class",
                    "CalendarData_da_DK.class", "CalendarData_da_GL.class", "CalendarData_dav_KE.class",
                    "CalendarData_de_AT.class", "CalendarData_de_BE.class", "CalendarData_de_CH.class",
                    "CalendarData_de_DE.class", "CalendarData_de_LI.class", "CalendarData_de_LU.class",
                    "CalendarData_dje_NE.class", "CalendarData_dsb_DE.class", "CalendarData_dua_CM.class",
                    "CalendarData_dyo_SN.class", "CalendarData_dz_BT.class", "CalendarData_ebu_KE.class",
                    "CalendarData_ee_GH.class", "CalendarData_ee_TG.class", "CalendarData_el_CY.class",
                    "CalendarData_el_GR.class", "CalendarData_en_AG.class", "CalendarData_en_AI.class",
                    "CalendarData_en_AS.class", "CalendarData_en_AT.class", "CalendarData_en_AU.class",
                    "CalendarData_en_BB.class", "CalendarData_en_BE.class", "CalendarData_en_BI.class",
                    "CalendarData_en_BM.class", "CalendarData_en_BS.class", "CalendarData_en_BW.class",
                    "CalendarData_en_BZ.class", "CalendarData_en_CA.class", "CalendarData_en_CC.class",
                    "CalendarData_en_CH.class", "CalendarData_en_CK.class", "CalendarData_en_CM.class",
                    "CalendarData_en_CX.class", "CalendarData_en_CY.class", "CalendarData_en_DE.class",
                    "CalendarData_en_DG.class", "CalendarData_en_DK.class", "CalendarData_en_DM.class",
                    "CalendarData_en_ER.class", "CalendarData_en_FI.class", "CalendarData_en_FJ.class",
                    "CalendarData_en_FK.class", "CalendarData_en_FM.class", "CalendarData_en_GB.class",
                    "CalendarData_en_GD.class", "CalendarData_en_GG.class", "CalendarData_en_GH.class",
                    "CalendarData_en_GI.class", "CalendarData_en_GM.class", "CalendarData_en_GU.class",
                    "CalendarData_en_GY.class", "CalendarData_en_HK.class", "CalendarData_en_IE.class",
                    "CalendarData_en_IL.class", "CalendarData_en_IM.class", "CalendarData_en_IN.class",
                    "CalendarData_en_IO.class", "CalendarData_en_JE.class", "CalendarData_en_JM.class",
                    "CalendarData_en_KE.class", "CalendarData_en_KI.class", "CalendarData_en_KN.class",
                    "CalendarData_en_KY.class", "CalendarData_en_LC.class", "CalendarData_en_LR.class",
                    "CalendarData_en_LS.class", "CalendarData_en_MG.class", "CalendarData_en_MH.class",
                    "CalendarData_en_MO.class", "CalendarData_en_MP.class", "CalendarData_en_MS.class",
                    "CalendarData_en_MT.class", "CalendarData_en_MU.class", "CalendarData_en_MW.class",
                    "CalendarData_en_MY.class", "CalendarData_en_NA.class", "CalendarData_en_NF.class",
                    "CalendarData_en_NG.class", "CalendarData_en_NL.class", "CalendarData_en_NR.class",
                    "CalendarData_en_NU.class", "CalendarData_en_NZ.class", "CalendarData_en_PG.class",
                    "CalendarData_en_PH.class", "CalendarData_en_PK.class", "CalendarData_en_PN.class",
                    "CalendarData_en_PR.class", "CalendarData_en_PW.class", "CalendarData_en_RW.class",
                    "CalendarData_en_SB.class", "CalendarData_en_SC.class", "CalendarData_en_SD.class",
                    "CalendarData_en_SE.class", "CalendarData_en_SG.class", "CalendarData_en_SH.class",
                    "CalendarData_en_SI.class", "CalendarData_en_SL.class", "CalendarData_en_SS.class",
                    "CalendarData_en_SX.class", "CalendarData_en_SZ.class", "CalendarData_en_TC.class",
                    "CalendarData_en_TK.class", "CalendarData_en_TO.class", "CalendarData_en_TT.class",
                    "CalendarData_en_TV.class", "CalendarData_en_TZ.class", "CalendarData_en_UG.class",
                    "CalendarData_en_UM.class", "CalendarData_en_VC.class", "CalendarData_en_VG.class",
                    "CalendarData_en_VI.class", "CalendarData_en_VU.class", "CalendarData_en_WS.class",
                    "CalendarData_en_ZA.class", "CalendarData_en_ZM.class", "CalendarData_en_ZW.class",
                    "CalendarData_es_AR.class", "CalendarData_es_BO.class", "CalendarData_es_BR.class",
                    "CalendarData_es_CL.class", "CalendarData_es_CO.class", "CalendarData_es_CR.class",
                    "CalendarData_es_CU.class", "CalendarData_es_DO.class", "CalendarData_es_EA.class",
                    "CalendarData_es_EC.class", "CalendarData_es_ES.class", "CalendarData_es_GQ.class",
                    "CalendarData_es_GT.class", "CalendarData_es_HN.class", "CalendarData_es_IC.class",
                    "CalendarData_es_MX.class", "CalendarData_es_NI.class", "CalendarData_es_PA.class",
                    "CalendarData_es_PE.class", "CalendarData_es_PH.class", "CalendarData_es_PR.class",
                    "CalendarData_es_PY.class", "CalendarData_es_SV.class", "CalendarData_es_US.class",
                    "CalendarData_es_UY.class", "CalendarData_es_VE.class", "CalendarData_et_EE.class",
                    "CalendarData_eu_ES.class", "CalendarData_ewo_CM.class", "CalendarData_fa_AF.class",
                    "CalendarData_fa_IR.class", "CalendarData_ff_CM.class", "CalendarData_ff_GN.class",
                    "CalendarData_ff_MR.class", "CalendarData_ff_SN.class", "CalendarData_fi_FI.class",
                    "CalendarData_fil_PH.class", "CalendarData_fo_DK.class", "CalendarData_fo_FO.class",
                    "CalendarData_fr_BE.class", "CalendarData_fr_BF.class", "CalendarData_fr_BI.class",
                    "CalendarData_fr_BJ.class", "CalendarData_fr_BL.class", "CalendarData_fr_CA.class",
                    "CalendarData_fr_CD.class", "CalendarData_fr_CF.class", "CalendarData_fr_CG.class",
                    "CalendarData_fr_CH.class", "CalendarData_fr_CI.class", "CalendarData_fr_CM.class",
                    "CalendarData_fr_DJ.class", "CalendarData_fr_DZ.class", "CalendarData_fr_FR.class",
                    "CalendarData_fr_GA.class", "CalendarData_fr_GF.class", "CalendarData_fr_GN.class",
                    "CalendarData_fr_GP.class", "CalendarData_fr_GQ.class", "CalendarData_fr_HT.class",
                    "CalendarData_fr_KM.class", "CalendarData_fr_LU.class", "CalendarData_fr_MA.class",
                    "CalendarData_fr_MC.class", "CalendarData_fr_MF.class", "CalendarData_fr_MG.class",
                    "CalendarData_fr_ML.class", "CalendarData_fr_MQ.class", "CalendarData_fr_MR.class",
                    "CalendarData_fr_MU.class", "CalendarData_fr_NC.class", "CalendarData_fr_NE.class",
                    "CalendarData_fr_PF.class", "CalendarData_fr_PM.class", "CalendarData_fr_RE.class",
                    "CalendarData_fr_RW.class", "CalendarData_fr_SC.class", "CalendarData_fr_SN.class",
                    "CalendarData_fr_SY.class", "CalendarData_fr_TD.class", "CalendarData_fr_TG.class",
                    "CalendarData_fr_TN.class", "CalendarData_fr_VU.class", "CalendarData_fr_WF.class",
                    "CalendarData_fr_YT.class", "CalendarData_fur_IT.class", "CalendarData_fy_NL.class",
                    "CalendarData_ga_IE.class", "CalendarData_gd_GB.class", "CalendarData_gl_ES.class",
                    "CalendarData_gsw_CH.class", "CalendarData_gsw_FR.class", "CalendarData_gsw_LI.class",
                    "CalendarData_gu_IN.class", "CalendarData_guz_KE.class", "CalendarData_gv_IM.class",
                    "CalendarData_ha_GH.class", "CalendarData_ha_NE.class", "CalendarData_ha_NG.class",
                    "CalendarData_haw_US.class", "CalendarData_hi_IN.class", "CalendarData_hr_BA.class",
                    "CalendarData_hr_HR.class", "CalendarData_hsb_DE.class", "CalendarData_hu_HU.class",
                    "CalendarData_hy_AM.class", "CalendarData_ig_NG.class", "CalendarData_ii_CN.class",
                    "CalendarData_in_ID.class", "CalendarData_is_IS.class", "CalendarData_it_CH.class",
                    "CalendarData_it_IT.class", "CalendarData_it_SM.class", "CalendarData_iw_IL.class",
                    "CalendarData_ja_JP.class", "CalendarData_jgo_CM.class", "CalendarData_jmc_TZ.class",
                    "CalendarData_ka_GE.class", "CalendarData_kab_DZ.class", "CalendarData_kam_KE.class",
                    "CalendarData_kde_TZ.class", "CalendarData_kea_CV.class", "CalendarData_khq_ML.class",
                    "CalendarData_ki_KE.class", "CalendarData_kk_KZ.class", "CalendarData_kkj_CM.class",
                    "CalendarData_kl_GL.class", "CalendarData_kln_KE.class", "CalendarData_km_KH.class",
                    "CalendarData_kn_IN.class", "CalendarData_ko_KP.class", "CalendarData_ko_KR.class",
                    "CalendarData_kok_IN.class", "CalendarData_ks_IN.class", "CalendarData_ksb_TZ.class",
                    "CalendarData_ksf_CM.class", "CalendarData_ksh_DE.class", "CalendarData_kw_GB.class",
                    "CalendarData_ky_KG.class", "CalendarData_lag_TZ.class", "CalendarData_lb_LU.class",
                    "CalendarData_lg_UG.class", "CalendarData_lkt_US.class", "CalendarData_ln_AO.class",
                    "CalendarData_ln_CD.class", "CalendarData_ln_CF.class", "CalendarData_ln_CG.class",
                    "CalendarData_lo_LA.class", "CalendarData_lrc_IQ.class", "CalendarData_lrc_IR.class",
                    "CalendarData_lt_LT.class", "CalendarData_lu_CD.class", "CalendarData_luo_KE.class",
                    "CalendarData_luy_KE.class", "CalendarData_lv_LV.class", "CalendarData_mas_KE.class",
                    "CalendarData_mas_TZ.class", "CalendarData_mer_KE.class", "CalendarData_mfe_MU.class",
                    "CalendarData_mg_MG.class", "CalendarData_mgh_MZ.class", "CalendarData_mgo_CM.class",
                    "CalendarData_mk_MK.class", "CalendarData_ml_IN.class", "CalendarData_mn_MN.class",
                    "CalendarData_mr_IN.class", "CalendarData_ms_BN.class", "CalendarData_ms_MY.class",
                    "CalendarData_ms_SG.class", "CalendarData_mt_MT.class", "CalendarData_mua_CM.class",
                    "CalendarData_my_MM.class", "CalendarData_mzn_IR.class", "CalendarData_naq_NA.class",
                    "CalendarData_nb_NO.class", "CalendarData_nb_SJ.class", "CalendarData_nd_ZW.class",
                    "CalendarData_ne_IN.class", "CalendarData_ne_NP.class", "CalendarData_nl_AW.class",
                    "CalendarData_nl_BE.class", "CalendarData_nl_BQ.class", "CalendarData_nl_CW.class",
                    "CalendarData_nl_NL.class", "CalendarData_nl_SR.class", "CalendarData_nl_SX.class",
                    "CalendarData_nmg_CM.class", "CalendarData_nnh_CM.class", "CalendarData_nus_SS.class",
                    "CalendarData_nyn_UG.class", "CalendarData_om_ET.class", "CalendarData_om_KE.class",
                    "CalendarData_or_IN.class", "CalendarData_os_GE.class", "CalendarData_os_RU.class",
                    "CalendarData_pa_Arab_PK.class", "CalendarData_pa_IN.class", "CalendarData_pa_PK.class",
                    "CalendarData_pl_PL.class", "CalendarData_ps_AF.class", "CalendarData_pt_AO.class",
                    "CalendarData_pt_BR.class", "CalendarData_pt_CV.class", "CalendarData_pt_GQ.class",
                    "CalendarData_pt_GW.class", "CalendarData_pt_MO.class", "CalendarData_pt_MZ.class",
                    "CalendarData_pt_PT.class", "CalendarData_pt_ST.class", "CalendarData_pt_TL.class",
                    "CalendarData_qu_BO.class", "CalendarData_qu_EC.class", "CalendarData_qu_PE.class",
                    "CalendarData_rm_CH.class", "CalendarData_rn_BI.class", "CalendarData_ro_MD.class",
                    "CalendarData_ro_RO.class", "CalendarData_rof_TZ.class", "CalendarData_ru_BY.class",
                    "CalendarData_ru_KG.class", "CalendarData_ru_KZ.class", "CalendarData_ru_MD.class",
                    "CalendarData_ru_RU.class", "CalendarData_ru_UA.class", "CalendarData_rw_RW.class",
                    "CalendarData_rwk_TZ.class", "CalendarData_sah_RU.class", "CalendarData_saq_KE.class",
                    "CalendarData_sbp_TZ.class", "CalendarData_se_FI.class", "CalendarData_se_NO.class",
                    "CalendarData_se_SE.class", "CalendarData_seh_MZ.class", "CalendarData_ses_ML.class",
                    "CalendarData_sg_CF.class", "CalendarData_shi_Latn_MA.class", "CalendarData_shi_MA.class",
                    "CalendarData_si_LK.class", "CalendarData_sk_SK.class", "CalendarData_sl_SI.class",
                    "CalendarData_smn_FI.class", "CalendarData_sn_ZW.class", "CalendarData_so_DJ.class",
                    "CalendarData_so_ET.class", "CalendarData_so_KE.class", "CalendarData_so_SO.class",
                    "CalendarData_sq_AL.class", "CalendarData_sq_MK.class", "CalendarData_sq_XK.class",
                    "CalendarData_sr_BA.class", "CalendarData_sr_Latn_BA.class", "CalendarData_sr_Latn_ME.class",
                    "CalendarData_sr_Latn_RS.class", "CalendarData_sr_Latn_XK.class", "CalendarData_sr_ME.class",
                    "CalendarData_sr_RS.class", "CalendarData_sr_XK.class", "CalendarData_sv_AX.class",
                    "CalendarData_sv_FI.class", "CalendarData_sv_SE.class", "CalendarData_sw_CD.class",
                    "CalendarData_sw_KE.class", "CalendarData_sw_TZ.class", "CalendarData_sw_UG.class",
                    "CalendarData_ta_IN.class", "CalendarData_ta_LK.class", "CalendarData_ta_MY.class",
                    "CalendarData_ta_SG.class", "CalendarData_te_IN.class", "CalendarData_teo_KE.class",
                    "CalendarData_teo_UG.class", "CalendarData_th_TH.class", "CalendarData_ti_ER.class",
                    "CalendarData_ti_ET.class", "CalendarData_tk_TM.class", "CalendarData_to_TO.class",
                    "CalendarData_tr_CY.class", "CalendarData_tr_TR.class", "CalendarData_twq_NE.class",
                    "CalendarData_tzm_MA.class", "CalendarData_ug_CN.class", "CalendarData_uk_UA.class",
                    "CalendarData_ur_IN.class", "CalendarData_ur_PK.class", "CalendarData_uz_AF.class",
                    "CalendarData_uz_Arab_AF.class", "CalendarData_uz_Cyrl_UZ.class", "CalendarData_uz_UZ.class",
                    "CalendarData_vai_LR.class", "CalendarData_vai_Latn_LR.class", "CalendarData_vi_VN.class",
                    "CalendarData_vun_TZ.class", "CalendarData_wae_CH.class", "CalendarData_xog_UG.class",
                    "CalendarData_yav_CM.class", "CalendarData_yo_BJ.class", "CalendarData_yo_NG.class",
                    "CalendarData_yue_HK.class", "CalendarData_zgh_MA.class", "CalendarData_zh_CN.class",
                    "CalendarData_zh_HK.class", "CalendarData_zh_Hant_HK.class", "CalendarData_zh_Hant_TW.class",
                    "CalendarData_zh_MO.class", "CalendarData_zh_SG.class", "CalendarData_zh_TW.class", "CalendarData_zu_ZA.class");

    private static Set<String> removedBundles = Set.of(
            "CalendarData_az_Latn_AZ.class", "CalendarData_bs_Latn_BA.class",
            "CalendarData_pa_Guru_IN.class", "CalendarData_shi_Tfng_MA.class",
            "CalendarData_sr_Cyrl_BA.class", "CalendarData_sr_Cyrl_ME.class",
            "CalendarData_sr_Cyrl_RS.class", "CalendarData_sr_Cyrl_XK.class",
            "CalendarData_uz_Latn_UZ.class", "CalendarData_vai_Vaii_LR.class",
            "CalendarData_zh_Hans_CN.class", "CalendarData_zh_Hans_HK.class",
            "CalendarData_zh_Hans_MO.class", "CalendarData_zh_Hans_SG.class");

    private static Set<String> addedBundles = Set.of(
            "CalendarData_az_AZ.class", "CalendarData_bs_BA.class",
            "CalendarData_pa_IN.class", "CalendarData_pa_PK.class",
            "CalendarData_shi_MA.class", "CalendarData_sr_BA.class",
            "CalendarData_sr_ME.class", "CalendarData_sr_RS.class",
            "CalendarData_sr_XK.class", "CalendarData_uz_UZ.class",
            "CalendarData_uz_AF.class", "CalendarData_vai_LR.class",
            "CalendarData_zh_CN.class", "CalendarData_zh_HK.class",
            "CalendarData_zh_MO.class", "CalendarData_zh_SG.class", "CalendarData_zh_TW.class");

    private static Set<String> retrievedBundles = Collections.EMPTY_SET;

    public static void main(String[] args) throws Exception {
        FileSystem fs = FileSystems.newFileSystem(URI.create("jrt:/"),
                Collections.emptyMap());
        Path path = fs.getPath("/", "modules", "jdk.localedata", "sun/util/resources/cldr/ext");
        retrievedBundles = Files.walk(path)
                                .map(p -> p.getFileName().toString())
                                .filter(p -> p.startsWith("CalendarData_"))
                                .collect(Collectors.toSet());
        if (!retrievedBundles.equals(expectedBundles)) {
            checkAddedBundles();
            checkRemovedBundles();
            Set<String> retrievedBundlesSet = new HashSet<>(retrievedBundles);
            retrievedBundlesSet.removeAll(expectedBundles);
            throw new RuntimeException("Unexpected "
                    + " bundles " + retrievedBundlesSet + " are present in jdk.localedata module ");

        }
    }

    /**
     * This method checks that bundles which have been additionally generated
     * are present in jdk.localedata module.
     */
    private static void checkAddedBundles() {
        Set<String> addedBundlesSet = new HashSet<>(addedBundles);
        addedBundlesSet.removeAll(retrievedBundles);
        if (!addedBundlesSet.isEmpty()) {
            throw new RuntimeException("expected CalendarData"
                    + " bundles " + addedBundlesSet + " are not present in jdk.localedata module ");
        }

    }

    /**
     * This method checks that bundles which have been removed are not present
     * in jdk.localedata module.
     */
    private static void checkRemovedBundles() {
        Set<String> unexpectedBundles = removedBundles.stream().
                filter(retrievedBundles::contains).collect(Collectors.toSet());
        if (!unexpectedBundles.isEmpty()) {
            throw new RuntimeException("Unexpected CalendarData"
                    + " bundles " + unexpectedBundles + " are present in jdk.localedata module ");
        }
    }
}
