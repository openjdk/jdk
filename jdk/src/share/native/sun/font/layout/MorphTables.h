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
 * (C) Copyright IBM Corp. 1998-2004 - All Rights Reserved
 *
 */

#ifndef __MORPHTABLES_H
#define __MORPHTABLES_H

/**
 * \file
 * \internal
 */

#include "LETypes.h"
#include "LayoutTables.h"

U_NAMESPACE_BEGIN

class LEGlyphStorage;

typedef le_uint32 FeatureFlags;

typedef le_int16 FeatureType;
typedef le_int16 FeatureSetting;

struct FeatureTableEntry
{
    FeatureType     featureType;
    FeatureSetting  featureSetting;
    FeatureFlags    enableFlags;
    FeatureFlags    disableFlags;
};

struct ChainHeader
{
    FeatureFlags        defaultFlags;
    le_uint32           chainLength;
    le_int16           nFeatureEntries;
    le_int16           nSubtables;
    FeatureTableEntry   featureTable[ANY_NUMBER];
};

struct MorphTableHeader
{
    le_int32    version;
    le_uint32   nChains;
    ChainHeader chains[ANY_NUMBER];

    void process(LEGlyphStorage &glyphStorage) const;
};

typedef le_int16 SubtableCoverage;
typedef le_uint32 SubtableCoverage2;

enum SubtableCoverageFlags
{
    scfVertical = 0x8000,
    scfReverse  = 0x4000,
    scfIgnoreVt = 0x2000,
    scfReserved = 0x1FF8,
    scfTypeMask = 0x0007
};

enum MorphSubtableType
{
    mstIndicRearrangement               = 0,
    mstContextualGlyphSubstitution      = 1,
    mstLigatureSubstitution             = 2,
    mstReservedUnused                   = 3,
    mstNonContextualGlyphSubstitution   = 4,
    mstContextualGlyphInsertion         = 5
};

struct MorphSubtableHeader
{
    le_int16           length;
    SubtableCoverage    coverage;
    FeatureFlags        subtableFeatures;

    void process(LEGlyphStorage &glyphStorage) const;
};

enum SubtableCoverageFlags2
{
    scfVertical2 = 0x80000000,
    scfReverse2  = 0x40000000,
    scfIgnoreVt2 = 0x20000000,
    scfReserved2 = 0x1FFFFF00,
    scfTypeMask2 = 0x000000FF
};

struct MorphSubtableHeader2
{
    le_uint32           length;
    SubtableCoverage2    coverage;
    FeatureFlags        subtableFeatures;

    void process(LEGlyphStorage &glyphStorage) const;
};

struct ChainHeader2
{
    FeatureFlags        defaultFlags;
    le_uint32           chainLength;
    le_uint32           nFeatureEntries;
    le_uint32           nSubtables;
    FeatureTableEntry   featureTable[ANY_NUMBER];
};

struct MorphTableHeader2
{
    le_int32    version;
    le_uint32   nChains;
    ChainHeader2 chains[ANY_NUMBER];

    void process(LEGlyphStorage &glyphStorage, le_int32 typoFlags) const;
};

/*
 * AAT Font Features
 * source: https://developer.apple.com/fonts/registry/
 * (plus addition from ATS/SFNTLayoutTypes.h)
 */

enum {

   allTypographicFeaturesType = 0,

      allTypeFeaturesOnSelector            = 0,
      allTypeFeaturesOffSelector           = 1,

   ligaturesType = 1,

      requiredLigaturesOnSelector          = 0,
      requiredLigaturesOffSelector         = 1,
      commonLigaturesOnSelector            = 2,
      commonLigaturesOffSelector           = 3,
      rareLigaturesOnSelector              = 4,
      rareLigaturesOffSelector             = 5,
      logosOnSelector                      = 6,
      logosOffSelector                     = 7,
      rebusPicturesOnSelector              = 8,
      rebusPicturesOffSelector             = 9,
      diphthongLigaturesOnSelector         = 10,
      diphthongLigaturesOffSelector        = 11,
      squaredLigaturesOnSelector           = 12,
      squaredLigaturesOffSelector          = 13,
      abbrevSquaredLigaturesOnSelector     = 14,
      abbrevSquaredLigaturesOffSelector    = 15,
      symbolLigaturesOnSelector            = 16,
      symbolLigaturesOffSelector           = 17,
      contextualLigaturesOnSelector        = 18,
      contextualLigaturesOffSelector       = 19,
      historicalLigaturesOnSelector        = 20,
      historicalLigaturesOffSelector       = 21,

   cursiveConnectionType = 2,

      unconnectedSelector                  = 0,
      partiallyConnectedSelector           = 1,
      cursiveSelector                      = 2,

   letterCaseType = 3,

      upperAndLowerCaseSelector            = 0,
      allCapsSelector                      = 1,
      allLowerCaseSelector                 = 2,
      smallCapsSelector                    = 3,
      initialCapsSelector                  = 4,
      initialCapsAndSmallCapsSelector      = 5,

   verticalSubstitutionType = 4,

      substituteVerticalFormsOnSelector    = 0,
      substituteVerticalFormsOffSelector   = 1,

   linguisticRearrangementType = 5,

      linguisticRearrangementOnSelector    = 0,
      linguisticRearrangementOffSelector   = 1,

   numberSpacingType = 6,

      monospacedNumbersSelector            = 0,
      proportionalNumbersSelector          = 1,

   /*
   appleReserved1Type = 7,
   */

   smartSwashType = 8,

      wordInitialSwashesOnSelector         = 0,
      wordInitialSwashesOffSelector        = 1,
      wordFinalSwashesOnSelector           = 2,
      wordFinalSwashesOffSelector          = 3,
      lineInitialSwashesOnSelector         = 4,
      lineInitialSwashesOffSelector        = 5,
      lineFinalSwashesOnSelector           = 6,
      lineFinalSwashesOffSelector          = 7,
      nonFinalSwashesOnSelector            = 8,
      nonFinalSwashesOffSelector           = 9,

   diacriticsType = 9,

      showDiacriticsSelector               = 0,
      hideDiacriticsSelector               = 1,
      decomposeDiacriticsSelector          = 2,

   verticalPositionType = 10,

      normalPositionSelector               = 0,
      superiorsSelector                    = 1,
      inferiorsSelector                    = 2,
      ordinalsSelector                     = 3,

   fractionsType = 11,

      noFractionsSelector                  = 0,
      verticalFractionsSelector            = 1,
      diagonalFractionsSelector            = 2,

   /*
   appleReserved2Type = 12,
   */

   overlappingCharactersType = 13,

      preventOverlapOnSelector             = 0,
      preventOverlapOffSelector            = 1,

   typographicExtrasType = 14,

      hyphensToEmDashOnSelector            = 0,
      hyphensToEmDashOffSelector           = 1,
      hyphenToEnDashOnSelector             = 2,
      hyphenToEnDashOffSelector            = 3,
      unslashedZeroOnSelector              = 4,
      slashedZeroOffSelector               = 4,
      unslashedZeroOffSelector             = 5,
      slashedZeroOnSelector                = 5,
      formInterrobangOnSelector            = 6,
      formInterrobangOffSelector           = 7,
      smartQuotesOnSelector                = 8,
      smartQuotesOffSelector               = 9,
      periodsToEllipsisOnSelector          = 10,
      periodsToEllipsisOffSelector         = 11,

   mathematicalExtrasType = 15,

      hyphenToMinusOnSelector              = 0,
      hyphenToMinusOffSelector             = 1,
      asteriskToMultiplyOnSelector         = 2,
      asteriskToMultiplyOffSelector        = 3,
      slashToDivideOnSelector              = 4,
      slashToDivideOffSelector             = 5,
      inequalityLigaturesOnSelector        = 6,
      inequalityLigaturesOffSelector       = 7,
      exponentsOnSelector                  = 8,
      exponentsOffSelector                 = 9,

   ornamentSetsType = 16,

      noOrnamentsSelector                  = 0,
      dingbatsSelector                     = 1,
      piCharactersSelector                 = 2,
      fleuronsSelector                     = 3,
      decorativeBordersSelector            = 4,
      internationalSymbolsSelector         = 5,
      mathSymbolsSelector                  = 6,

   characterAlternativesType = 17,

      noAlternatesSelector                 = 0,

   designComplexityType = 18,

      designLevel1Selector                 = 0,
      designLevel2Selector                 = 1,
      designLevel3Selector                 = 2,
      designLevel4Selector                 = 3,
      designLevel5Selector                 = 4,
      designLevel6Selector                 = 5,
      designLevel7Selector                 = 6,

   styleOptionsType = 19,

      noStyleOptionsSelector               = 0,
      displayTextSelector                  = 1,
      engravedTextSelector                 = 2,
      illuminatedCapsSelector              = 3,
      titlingCapsSelector                  = 4,
      tallCapsSelector                     = 5,

   characterShapeType = 20,

      traditionalCharactersSelector        = 0,
      simplifiedCharactersSelector         = 1,
      jis1978CharactersSelector            = 2,
      jis1983CharactersSelector            = 3,
      jis1990CharactersSelector            = 4,
      traditionalAltOneSelector            = 5,
      traditionalAltTwoSelector            = 6,
      traditionalAltThreeSelector          = 7,
      traditionalAltFourSelector           = 8,
      traditionalAltFiveSelector           = 9,
      expertCharactersSelector             = 10,

   numberCaseType = 21,

      lowerCaseNumbersSelector             = 0,
      upperCaseNumbersSelector             = 1,

   textSpacingType = 22,

      proportionalTextSelector             = 0,
      monospacedTextSelector               = 1,
      halfWidthTextSelector                = 2,
      normallySpacedTextSelector           = 3,

   transliterationType = 23,

      noTransliterationSelector            = 0,
      hanjaToHangulSelector                = 1,
      hiraganaToKatakanaSelector           = 2,
      katakanaToHiraganaSelector           = 3,
      kanaToRomanizationSelector           = 4,
      romanizationToHiraganaSelector       = 5,
      romanizationToKatakanaSelector       = 6,
      hanjaToHangulAltOneSelector          = 7,
      hanjaToHangulAltTwoSelector          = 8,
      hanjaToHangulAltThreeSelector        = 9,

   annotationType = 24,

      noAnnotationSelector                 = 0,
      boxAnnotationSelector                = 1,
      roundedBoxAnnotationSelector         = 2,
      circleAnnotationSelector             = 3,
      invertedCircleAnnotationSelector     = 4,
      parenthesisAnnotationSelector        = 5,
      periodAnnotationSelector             = 6,
      romanNumeralAnnotationSelector       = 7,
      diamondAnnotationSelector            = 8,

   kanaSpacingType = 25,

      fullWidthKanaSelector                = 0,
      proportionalKanaSelector             = 1,

   ideographicSpacingType = 26,

      fullWidthIdeographsSelector          = 0,
      proportionalIdeographsSelector       = 1,

   cjkRomanSpacingType = 103,

      halfWidthCJKRomanSelector            = 0,
      proportionalCJKRomanSelector         = 1,
      defaultCJKRomanSelector              = 2,
      fullWidthCJKRomanSelector            = 3,

   rubyKanaType = 28,

      rubyKanaOnSelector                = 2,
      rubyKanaOffSelector               = 3,

/* The following types are provided for compatibility; note that
   their use is deprecated. */

   adobeCharacterSpacingType = 100,        /* prefer 22 */
   adobeKanaSpacingType = 101,             /* prefer 25 */
   adobeKanjiSpacingType = 102,            /* prefer 26 */
   adobeSquareLigatures = 104,             /* prefer 1 */

   lastFeatureType = -1
};

U_NAMESPACE_END
#endif

