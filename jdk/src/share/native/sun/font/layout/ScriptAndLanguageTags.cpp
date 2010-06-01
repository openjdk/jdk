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
 * (C) Copyright IBM Corp. 1998-2004. All Rights Reserved.
 *
 * WARNING: THIS FILE IS MACHINE GENERATED. DO NOT HAND EDIT IT UNLESS
 * YOU REALLY KNOW WHAT YOU'RE DOING.
 */

#include "LETypes.h"
#include "ScriptAndLanguageTags.h"
#include "OpenTypeLayoutEngine.h"

U_NAMESPACE_BEGIN

const LETag OpenTypeLayoutEngine::scriptTags[] = {
    zyyyScriptTag, /* 'zyyy' (COMMON) */
    qaaiScriptTag, /* 'qaai' (INHERITED) */
    arabScriptTag, /* 'arab' (ARABIC) */
    armnScriptTag, /* 'armn' (ARMENIAN) */
    bengScriptTag, /* 'beng' (BENGALI) */
    bopoScriptTag, /* 'bopo' (BOPOMOFO) */
    cherScriptTag, /* 'cher' (CHEROKEE) */
    qaacScriptTag, /* 'qaac' (COPTIC) */
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
    laooScriptTag, /* 'laoo' (LAO) */
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
    yiiiScriptTag, /* 'yiii' (YI) */
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
    hrktScriptTag  /* 'hrkt' (KATAKANA_OR_HIRAGANA) */
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
    zhtLanguageTag  /* 'ZHT' (Chinese (Traditional)) */
};

U_NAMESPACE_END
