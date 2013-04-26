/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 *
 */

/*
 *
 * (C) Copyright IBM Corp. 1998-2013. All Rights Reserved.
 *
 * WARNING: THIS FILE IS MACHINE GENERATED. DO NOT HAND EDIT IT UNLESS
 * YOU REALLY KNOW WHAT YOU'RE DOING.
 *
 * Generated on: 10/26/2010 02:53:33 PM PDT
 */

#include "LETypes.h"
#include "ScriptAndLanguageTags.h"
#include "OpenTypeLayoutEngine.h"

U_NAMESPACE_BEGIN

const LETag OpenTypeLayoutEngine::scriptTags[] = {
    zyyyScriptTag, /* 'zyyy' (COMMON) */
    zinhScriptTag, /* 'zinh' (INHERITED) */
    arabScriptTag, /* 'arab' (ARABIC) */
    armnScriptTag, /* 'armn' (ARMENIAN) */
    bengScriptTag, /* 'beng' (BENGALI) */
    bopoScriptTag, /* 'bopo' (BOPOMOFO) */
    cherScriptTag, /* 'cher' (CHEROKEE) */
    coptScriptTag, /* 'copt' (COPTIC) */
    cyrlScriptTag, /* 'cyrl' (CYRILLIC) */
    dsrtScriptTag, /* 'dsrt' (DESERET) */
    devaScriptTag, /* 'deva' (DEVANAGARI) */
    ethiScriptTag, /* 'ethi' (ETHIOPIC) */
    georScriptTag, /* 'geor' (GEORGIAN) */
    gothScriptTag, /* 'goth' (GOTHIC) */
    grekScriptTag, /* 'grek' (GREEK) */
    gujrScriptTag, /* 'gujr' (GUJARATI) */
    guruScriptTag, /* 'guru' (GURMUKHI) */
    haniScriptTag, /* 'hani' (HAN) */
    hangScriptTag, /* 'hang' (HANGUL) */
    hebrScriptTag, /* 'hebr' (HEBREW) */
    hiraScriptTag, /* 'hira' (HIRAGANA) */
    kndaScriptTag, /* 'knda' (KANNADA) */
    kanaScriptTag, /* 'kana' (KATAKANA) */
    khmrScriptTag, /* 'khmr' (KHMER) */
    laooScriptTag, /* 'lao ' (LAO) */
    latnScriptTag, /* 'latn' (LATIN) */
    mlymScriptTag, /* 'mlym' (MALAYALAM) */
    mongScriptTag, /* 'mong' (MONGOLIAN) */
    mymrScriptTag, /* 'mymr' (MYANMAR) */
    ogamScriptTag, /* 'ogam' (OGHAM) */
    italScriptTag, /* 'ital' (OLD_ITALIC) */
    oryaScriptTag, /* 'orya' (ORIYA) */
    runrScriptTag, /* 'runr' (RUNIC) */
    sinhScriptTag, /* 'sinh' (SINHALA) */
    syrcScriptTag, /* 'syrc' (SYRIAC) */
    tamlScriptTag, /* 'taml' (TAMIL) */
    teluScriptTag, /* 'telu' (TELUGU) */
    thaaScriptTag, /* 'thaa' (THAANA) */
    thaiScriptTag, /* 'thai' (THAI) */
    tibtScriptTag, /* 'tibt' (TIBETAN) */
    cansScriptTag, /* 'cans' (CANADIAN_ABORIGINAL) */
    yiiiScriptTag, /* 'yi  ' (YI) */
    tglgScriptTag, /* 'tglg' (TAGALOG) */
    hanoScriptTag, /* 'hano' (HANUNOO) */
    buhdScriptTag, /* 'buhd' (BUHID) */
    tagbScriptTag, /* 'tagb' (TAGBANWA) */
    braiScriptTag, /* 'brai' (BRAILLE) */
    cprtScriptTag, /* 'cprt' (CYPRIOT) */
    limbScriptTag, /* 'limb' (LIMBU) */
    linbScriptTag, /* 'linb' (LINEAR_B) */
    osmaScriptTag, /* 'osma' (OSMANYA) */
    shawScriptTag, /* 'shaw' (SHAVIAN) */
    taleScriptTag, /* 'tale' (TAI_LE) */
    ugarScriptTag, /* 'ugar' (UGARITIC) */
    hrktScriptTag, /* 'hrkt' (KATAKANA_OR_HIRAGANA) */
    bugiScriptTag, /* 'bugi' (BUGINESE) */
    glagScriptTag, /* 'glag' (GLAGOLITIC) */
    kharScriptTag, /* 'khar' (KHAROSHTHI) */
    syloScriptTag, /* 'sylo' (SYLOTI_NAGRI) */
    taluScriptTag, /* 'talu' (NEW_TAI_LUE) */
    tfngScriptTag, /* 'tfng' (TIFINAGH) */
    xpeoScriptTag, /* 'xpeo' (OLD_PERSIAN) */
    baliScriptTag, /* 'bali' (BALINESE) */
    batkScriptTag, /* 'batk' (BATAK) */
    blisScriptTag, /* 'blis' (BLIS) */
    brahScriptTag, /* 'brah' (BRAHMI) */
    chamScriptTag, /* 'cham' (CHAM) */
    cirtScriptTag, /* 'cirt' (CIRT) */
    cyrsScriptTag, /* 'cyrs' (CYRS) */
    egydScriptTag, /* 'egyd' (EGYD) */
    egyhScriptTag, /* 'egyh' (EGYH) */
    egypScriptTag, /* 'egyp' (EGYPTIAN_HIEROGLYPHS) */
    geokScriptTag, /* 'geok' (GEOK) */
    hansScriptTag, /* 'hans' (HANS) */
    hantScriptTag, /* 'hant' (HANT) */
    hmngScriptTag, /* 'hmng' (HMNG) */
    hungScriptTag, /* 'hung' (HUNG) */
    indsScriptTag, /* 'inds' (INDS) */
    javaScriptTag, /* 'java' (JAVANESE) */
    kaliScriptTag, /* 'kali' (KAYAH_LI) */
    latfScriptTag, /* 'latf' (LATF) */
    latgScriptTag, /* 'latg' (LATG) */
    lepcScriptTag, /* 'lepc' (LEPCHA) */
    linaScriptTag, /* 'lina' (LINA) */
    mandScriptTag, /* 'mand' (MANDAIC) */
    mayaScriptTag, /* 'maya' (MAYA) */
    meroScriptTag, /* 'mero' (MERO) */
    nkooScriptTag, /* 'nko ' (NKO) */
    orkhScriptTag, /* 'orkh' (OLD_TURKIC) */
    permScriptTag, /* 'perm' (PERM) */
    phagScriptTag, /* 'phag' (PHAGS_PA) */
    phnxScriptTag, /* 'phnx' (PHOENICIAN) */
    plrdScriptTag, /* 'plrd' (PLRD) */
    roroScriptTag, /* 'roro' (RORO) */
    saraScriptTag, /* 'sara' (SARA) */
    syreScriptTag, /* 'syre' (SYRE) */
    syrjScriptTag, /* 'syrj' (SYRJ) */
    syrnScriptTag, /* 'syrn' (SYRN) */
    tengScriptTag, /* 'teng' (TENG) */
    vaiiScriptTag, /* 'vai ' (VAI) */
    vispScriptTag, /* 'visp' (VISP) */
    xsuxScriptTag, /* 'xsux' (CUNEIFORM) */
    zxxxScriptTag, /* 'zxxx' (ZXXX) */
    zzzzScriptTag, /* 'zzzz' (UNKNOWN) */
    cariScriptTag, /* 'cari' (CARIAN) */
    jpanScriptTag, /* 'jpan' (JPAN) */
    lanaScriptTag, /* 'lana' (TAI_THAM) */
    lyciScriptTag, /* 'lyci' (LYCIAN) */
    lydiScriptTag, /* 'lydi' (LYDIAN) */
    olckScriptTag, /* 'olck' (OL_CHIKI) */
    rjngScriptTag, /* 'rjng' (REJANG) */
    saurScriptTag, /* 'saur' (SAURASHTRA) */
    sgnwScriptTag, /* 'sgnw' (SGNW) */
    sundScriptTag, /* 'sund' (SUNDANESE) */
    moonScriptTag, /* 'moon' (MOON) */
    mteiScriptTag, /* 'mtei' (MEETEI_MAYEK) */
    armiScriptTag, /* 'armi' (IMPERIAL_ARAMAIC) */
    avstScriptTag, /* 'avst' (AVESTAN) */
    cakmScriptTag, /* 'cakm' (CAKM) */
    koreScriptTag, /* 'kore' (KORE) */
    kthiScriptTag, /* 'kthi' (KAITHI) */
    maniScriptTag, /* 'mani' (MANI) */
    phliScriptTag, /* 'phli' (INSCRIPTIONAL_PAHLAVI) */
    phlpScriptTag, /* 'phlp' (PHLP) */
    phlvScriptTag, /* 'phlv' (PHLV) */
    prtiScriptTag, /* 'prti' (INSCRIPTIONAL_PARTHIAN) */
    samrScriptTag, /* 'samr' (SAMARITAN) */
    tavtScriptTag, /* 'tavt' (TAI_VIET) */
    zmthScriptTag, /* 'zmth' (ZMTH) */
    zsymScriptTag, /* 'zsym' (ZSYM) */
    bamuScriptTag, /* 'bamu' (BAMUM) */
    lisuScriptTag, /* 'lisu' (LISU) */
    nkgbScriptTag, /* 'nkgb' (NKGB) */
    sarbScriptTag, /* 'sarb' (OLD_SOUTH_ARABIAN) */
    bassScriptTag, /* 'bass' (BASS) */
    duplScriptTag, /* 'dupl' (DUPL) */
    elbaScriptTag, /* 'elba' (ELBA) */
    granScriptTag, /* 'gran' (GRAN) */
    kpelScriptTag, /* 'kpel' (KPEL) */
    lomaScriptTag, /* 'loma' (LOMA) */
    mendScriptTag, /* 'mend' (MEND) */
    mercScriptTag, /* 'merc' (MERC) */
    narbScriptTag, /* 'narb' (NARB) */
    nbatScriptTag, /* 'nbat' (NBAT) */
    palmScriptTag, /* 'palm' (PALM) */
    sindScriptTag, /* 'sind' (SIND) */
    waraScriptTag, /* 'wara' (WARA) */
    afakScriptTag, /* 'afak' (AFAK) */
    jurcScriptTag, /* 'jurc' (JURC) */
    mrooScriptTag, /* 'mroo' (MROO) */
    nshuScriptTag, /* 'nshu' (NSHU) */
    shrdScriptTag, /* 'shrd' (SHARADA) */
    soraScriptTag, /* 'sora' (SORA_SOMPENG) */
    takrScriptTag, /* 'takr' (TAKRI) */
    tangScriptTag, /* 'tang' (TANG) */
    woleScriptTag, /* 'wole' (WOLE) */
    khojScriptTag, /* 'khoj' (KHOJ) */
    tirhScriptTag  /* 'tirh' (TIRH) */
};

const LETag OpenTypeLayoutEngine::languageTags[] = {
    nullLanguageTag, /* '' (null) */
    araLanguageTag, /* 'ARA' (Arabic) */
    asmLanguageTag, /* 'ASM' (Assamese) */
    benLanguageTag, /* 'BEN' (Bengali) */
    farLanguageTag, /* 'FAR' (Farsi) */
    gujLanguageTag, /* 'GUJ' (Gujarati) */
    hinLanguageTag, /* 'HIN' (Hindi) */
    iwrLanguageTag, /* 'IWR' (Hebrew) */
    jiiLanguageTag, /* 'JII' (Yiddish) */
    janLanguageTag, /* 'JAN' (Japanese) */
    kanLanguageTag, /* 'KAN' (Kannada) */
    kokLanguageTag, /* 'KOK' (Konkani) */
    korLanguageTag, /* 'KOR' (Korean) */
    kshLanguageTag, /* 'KSH' (Kashmiri) */
    malLanguageTag, /* 'MAL' (Malayalam (Traditional)) */
    marLanguageTag, /* 'MAR' (Marathi) */
    mlrLanguageTag, /* 'MLR' (Malayalam (Reformed)) */
    mniLanguageTag, /* 'MNI' (Manipuri) */
    oriLanguageTag, /* 'ORI' (Oriya) */
    sanLanguageTag, /* 'SAN' (Sanscrit) */
    sndLanguageTag, /* 'SND' (Sindhi) */
    snhLanguageTag, /* 'SNH' (Sinhalese) */
    syrLanguageTag, /* 'SYR' (Syriac) */
    tamLanguageTag, /* 'TAM' (Tamil) */
    telLanguageTag, /* 'TEL' (Telugu) */
    thaLanguageTag, /* 'THA' (Thai) */
    urdLanguageTag, /* 'URD' (Urdu) */
    zhpLanguageTag, /* 'ZHP' (Chinese (Phonetic)) */
    zhsLanguageTag, /* 'ZHS' (Chinese (Simplified)) */
    zhtLanguageTag, /* 'ZHT' (Chinese (Traditional)) */
    afkLanguageTag, /* 'AFK' (Afrikaans) */
    belLanguageTag, /* 'BEL' (Belarussian) */
    bgrLanguageTag, /* 'BGR' (Bulgarian) */
    catLanguageTag, /* 'CAT' (Catalan) */
    cheLanguageTag, /* 'CHE' (Chechen) */
    copLanguageTag, /* 'COP' (Coptic) */
    csyLanguageTag, /* 'CSY' (Czech) */
    danLanguageTag, /* 'DAN' (Danish) */
    deuLanguageTag, /* 'DEU' (German) */
    dznLanguageTag, /* 'DZN' (Dzongkha) */
    ellLanguageTag, /* 'ELL' (Greek) */
    engLanguageTag, /* 'ENG' (English) */
    espLanguageTag, /* 'ESP' (Spanish) */
    etiLanguageTag, /* 'ETI' (Estonian) */
    euqLanguageTag, /* 'EUQ' (Basque) */
    finLanguageTag, /* 'FIN' (Finnish) */
    fraLanguageTag, /* 'FRA' (French) */
    gaeLanguageTag, /* 'GAE' (Gaelic) */
    hauLanguageTag, /* 'HAU' (Hausa) */
    hrvLanguageTag, /* 'HRV' (Croation) */
    hunLanguageTag, /* 'HUN' (Hungarian) */
    hyeLanguageTag, /* 'HYE' (Armenian) */
    indLanguageTag, /* 'IND' (Indonesian) */
    itaLanguageTag, /* 'ITA' (Italian) */
    khmLanguageTag, /* 'KHM' (Khmer) */
    mngLanguageTag, /* 'MNG' (Mongolian) */
    mtsLanguageTag, /* 'MTS' (Maltese) */
    nepLanguageTag, /* 'NEP' (Nepali) */
    nldLanguageTag, /* 'NLD' (Dutch) */
    pasLanguageTag, /* 'PAS' (Pashto) */
    plkLanguageTag, /* 'PLK' (Polish) */
    ptgLanguageTag, /* 'PTG' (Portuguese) */
    romLanguageTag, /* 'ROM' (Romanian) */
    rusLanguageTag, /* 'RUS' (Russian) */
    skyLanguageTag, /* 'SKY' (Slovak) */
    slvLanguageTag, /* 'SLV' (Slovenian) */
    sqiLanguageTag, /* 'SQI' (Albanian) */
    srbLanguageTag, /* 'SRB' (Serbian) */
    sveLanguageTag, /* 'SVE' (Swedish) */
    tibLanguageTag, /* 'TIB' (Tibetan) */
    trkLanguageTag, /* 'TRK' (Turkish) */
    welLanguageTag  /* 'WEL' (Welsh) */
};

U_NAMESPACE_END
