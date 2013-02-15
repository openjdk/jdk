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

#ifndef __LESCRIPTS_H
#define __LESCRIPTS_H

#include "LETypes.h"

/**
 * \file
 * \brief C++ API: Constants for Unicode script values
 */

U_NAMESPACE_BEGIN

/**
 * Constants for Unicode script values, generated using
 * ICU4J's <code>UScript</code> class.
 *
 * @stable ICU 2.2
 */

enum ScriptCodes {
    zyyyScriptCode =  0,
    zinhScriptCode =  1,
    qaaiScriptCode = zinhScriptCode,  /* manually added alias, for API stability */
    arabScriptCode =  2,
    armnScriptCode =  3,
    bengScriptCode =  4,
    bopoScriptCode =  5,
    cherScriptCode =  6,
    coptScriptCode =  7,
    cyrlScriptCode =  8,
    dsrtScriptCode =  9,
    devaScriptCode = 10,
    ethiScriptCode = 11,
    georScriptCode = 12,
    gothScriptCode = 13,
    grekScriptCode = 14,
    gujrScriptCode = 15,
    guruScriptCode = 16,
    haniScriptCode = 17,
    hangScriptCode = 18,
    hebrScriptCode = 19,
    hiraScriptCode = 20,
    kndaScriptCode = 21,
    kanaScriptCode = 22,
    khmrScriptCode = 23,
    laooScriptCode = 24,
    latnScriptCode = 25,
    mlymScriptCode = 26,
    mongScriptCode = 27,
    mymrScriptCode = 28,
    ogamScriptCode = 29,
    italScriptCode = 30,
    oryaScriptCode = 31,
    runrScriptCode = 32,
    sinhScriptCode = 33,
    syrcScriptCode = 34,
    tamlScriptCode = 35,
    teluScriptCode = 36,
    thaaScriptCode = 37,
    thaiScriptCode = 38,
    tibtScriptCode = 39,
/**
 * @stable ICU 2.6
 */

    cansScriptCode = 40,
/**
 * @stable ICU 2.2
 */

    yiiiScriptCode = 41,
    tglgScriptCode = 42,
    hanoScriptCode = 43,
    buhdScriptCode = 44,
    tagbScriptCode = 45,
/**
 * @stable ICU 2.6
 */

    braiScriptCode = 46,
    cprtScriptCode = 47,
    limbScriptCode = 48,
    linbScriptCode = 49,
    osmaScriptCode = 50,
    shawScriptCode = 51,
    taleScriptCode = 52,
    ugarScriptCode = 53,
/**
 * @stable ICU 3.0
 */

    hrktScriptCode = 54,
/**
 * @stable ICU 3.4
 */

    bugiScriptCode = 55,
    glagScriptCode = 56,
    kharScriptCode = 57,
    syloScriptCode = 58,
    taluScriptCode = 59,
    tfngScriptCode = 60,
    xpeoScriptCode = 61,
/**
 * @stable ICU 3.6
 */

    baliScriptCode = 62,
    batkScriptCode = 63,
    blisScriptCode = 64,
    brahScriptCode = 65,
    chamScriptCode = 66,
    cirtScriptCode = 67,
    cyrsScriptCode = 68,
    egydScriptCode = 69,
    egyhScriptCode = 70,
    egypScriptCode = 71,
    geokScriptCode = 72,
    hansScriptCode = 73,
    hantScriptCode = 74,
    hmngScriptCode = 75,
    hungScriptCode = 76,
    indsScriptCode = 77,
    javaScriptCode = 78,
    kaliScriptCode = 79,
    latfScriptCode = 80,
    latgScriptCode = 81,
    lepcScriptCode = 82,
    linaScriptCode = 83,
    mandScriptCode = 84,
    mayaScriptCode = 85,
    meroScriptCode = 86,
    nkooScriptCode = 87,
    orkhScriptCode = 88,
    permScriptCode = 89,
    phagScriptCode = 90,
    phnxScriptCode = 91,
    plrdScriptCode = 92,
    roroScriptCode = 93,
    saraScriptCode = 94,
    syreScriptCode = 95,
    syrjScriptCode = 96,
    syrnScriptCode = 97,
    tengScriptCode = 98,
    vaiiScriptCode = 99,
    vispScriptCode = 100,
    xsuxScriptCode = 101,
    zxxxScriptCode = 102,
    zzzzScriptCode = 103,
/**
 * @stable ICU 3.8
 */

    cariScriptCode = 104,
    jpanScriptCode = 105,
    lanaScriptCode = 106,
    lyciScriptCode = 107,
    lydiScriptCode = 108,
    olckScriptCode = 109,
    rjngScriptCode = 110,
    saurScriptCode = 111,
    sgnwScriptCode = 112,
    sundScriptCode = 113,
    moonScriptCode = 114,
    mteiScriptCode = 115,
/**
 * @stable ICU 4.0
 */

    armiScriptCode = 116,
    avstScriptCode = 117,
    cakmScriptCode = 118,
    koreScriptCode = 119,
    kthiScriptCode = 120,
    maniScriptCode = 121,
    phliScriptCode = 122,
    phlpScriptCode = 123,
    phlvScriptCode = 124,
    prtiScriptCode = 125,
    samrScriptCode = 126,
    tavtScriptCode = 127,
    zmthScriptCode = 128,
    zsymScriptCode = 129,
/**
 * @stable ICU 4.4
 */

    bamuScriptCode = 130,
    lisuScriptCode = 131,
    nkgbScriptCode = 132,
    sarbScriptCode = 133,
/**
 * @stable ICU 4.6
 */

    bassScriptCode = 134,
    duplScriptCode = 135,
    elbaScriptCode = 136,
    granScriptCode = 137,
    kpelScriptCode = 138,
    lomaScriptCode = 139,
    mendScriptCode = 140,
    mercScriptCode = 141,
    narbScriptCode = 142,
    nbatScriptCode = 143,
    palmScriptCode = 144,
    sindScriptCode = 145,
    waraScriptCode = 146,
/**
 * @stable ICU 4.8
 */

    afakScriptCode = 147,
    jurcScriptCode = 148,
    mrooScriptCode = 149,
    nshuScriptCode = 150,
    shrdScriptCode = 151,
    soraScriptCode = 152,
    takrScriptCode = 153,
    tangScriptCode = 154,
    woleScriptCode = 155,
/**
 * @stable ICU 49
 */

    khojScriptCode = 156,
    tirhScriptCode = 157,

    scriptCodeCount = 158
};

U_NAMESPACE_END
#endif
