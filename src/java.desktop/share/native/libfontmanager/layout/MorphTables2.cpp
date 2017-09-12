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
 * (C) Copyright IBM Corp. and others 1998 - 2013 - All Rights Reserved
 *
 */

#include "LETypes.h"
#include "LayoutTables.h"
#include "MorphTables.h"
#include "SubtableProcessor2.h"
#include "IndicRearrangementProcessor2.h"
#include "ContextualGlyphSubstProc2.h"
#include "LigatureSubstProc2.h"
#include "NonContextualGlyphSubstProc2.h"
#include "ContextualGlyphInsertionProc2.h"
#include "LEGlyphStorage.h"
#include "LESwaps.h"

U_NAMESPACE_BEGIN

void MorphTableHeader2::process(const LEReferenceTo<MorphTableHeader2> &base, LEGlyphStorage &glyphStorage,
                                le_int32 typoFlags, LEErrorCode &success) const
{
  if(LE_FAILURE(success)) return;

  le_uint32 chainCount = SWAPL(this->nChains);
  LEReferenceTo<ChainHeader2> chainHeader(base, success, &chains[0]);
  /* chainHeader and subtableHeader are implemented as a moving pointer rather than an array dereference
   * to (slightly) reduce code churn. However, must be careful to preincrement them the 2nd time through.
   * We don't want to increment them at the end of the loop, as that would attempt to dereference
   * out of range memory.
   */
  le_uint32 chain;

  for (chain = 0; LE_SUCCESS(success) && (chain < chainCount); chain++) {
        if (chain>0) {
          le_uint32 chainLength = SWAPL(chainHeader->chainLength);
          if (chainLength & 0x03) { // incorrect alignment for 32 bit tables
              success = LE_MEMORY_ALLOCATION_ERROR; // as good a choice as any
              return;
          }
          chainHeader.addOffset(chainLength, success); // Don't increment the first time
        }
        FeatureFlags flag = SWAPL(chainHeader->defaultFlags);
        le_uint32 nFeatureEntries = SWAPL(chainHeader->nFeatureEntries);
        le_uint32 nSubtables = SWAPL(chainHeader->nSubtables);
        LEReferenceTo<MorphSubtableHeader2> subtableHeader(chainHeader,
              success, (const MorphSubtableHeader2 *)&chainHeader->featureTable[nFeatureEntries]);
        le_uint32 subtable;
        if(LE_FAILURE(success)) break; // malformed table

        if (typoFlags != 0) {
           le_uint32 featureEntry;
           LEReferenceToArrayOf<FeatureTableEntry> featureTableRef(chainHeader, success, &chainHeader->featureTable[0], nFeatureEntries);
           if(LE_FAILURE(success)) break;
            // Feature subtables
            for (featureEntry = 0; featureEntry < nFeatureEntries; featureEntry++) {
                const FeatureTableEntry &featureTableEntry = featureTableRef(featureEntry, success);
                le_int16 featureType = SWAPW(featureTableEntry.featureType);
                le_int16 featureSetting = SWAPW(featureTableEntry.featureSetting);
                le_uint32 enableFlags = SWAPL(featureTableEntry.enableFlags);
                le_uint32 disableFlags = SWAPL(featureTableEntry.disableFlags);
                switch (featureType) {
                    case ligaturesType:
                        if ((typoFlags & LE_Ligatures_FEATURE_ENUM ) && (featureSetting ^ 0x1)){
                            flag &= disableFlags;
                            flag |= enableFlags;
                        } else {
                            if (((typoFlags & LE_RLIG_FEATURE_FLAG) && featureSetting == requiredLigaturesOnSelector) ||
                                ((typoFlags & LE_CLIG_FEATURE_FLAG) && featureSetting == contextualLigaturesOnSelector) ||
                                ((typoFlags & LE_HLIG_FEATURE_FLAG) && featureSetting == historicalLigaturesOnSelector) ||
                                ((typoFlags & LE_LIGA_FEATURE_FLAG) && featureSetting == commonLigaturesOnSelector)) {
                                flag &= disableFlags;
                                flag |= enableFlags;
                            }
                        }
                        break;
                    case letterCaseType:
                        if ((typoFlags & LE_SMCP_FEATURE_FLAG) && featureSetting == smallCapsSelector) {
                            flag &= disableFlags;
                            flag |= enableFlags;
                        }
                        break;
                    case verticalSubstitutionType:
                        break;
                    case linguisticRearrangementType:
                        break;
                    case numberSpacingType:
                        break;
                    case smartSwashType:
                        if ((typoFlags & LE_SWSH_FEATURE_FLAG) && (featureSetting ^ 0x1)){
                            flag &= disableFlags;
                            flag |= enableFlags;
                        }
                        break;
                    case diacriticsType:
                        break;
                    case verticalPositionType:
                        break;
                    case fractionsType:
                        if (((typoFlags & LE_FRAC_FEATURE_FLAG) && featureSetting == diagonalFractionsSelector) ||
                            ((typoFlags & LE_AFRC_FEATURE_FLAG) && featureSetting == verticalFractionsSelector)) {
                            flag &= disableFlags;
                            flag |= enableFlags;
                        } else {
                            flag &= disableFlags;
                        }
                        break;
                    case typographicExtrasType:
                        if ((typoFlags & LE_ZERO_FEATURE_FLAG) && featureSetting == slashedZeroOnSelector) {
                            flag &= disableFlags;
                            flag |= enableFlags;
                        }
                        break;
                    case mathematicalExtrasType:
                        break;
                    case ornamentSetsType:
                        break;
                    case characterAlternativesType:
                        break;
                    case designComplexityType:
                        if (((typoFlags & LE_SS01_FEATURE_FLAG) && featureSetting == designLevel1Selector) ||
                            ((typoFlags & LE_SS02_FEATURE_FLAG) && featureSetting == designLevel2Selector) ||
                            ((typoFlags & LE_SS03_FEATURE_FLAG) && featureSetting == designLevel3Selector) ||
                            ((typoFlags & LE_SS04_FEATURE_FLAG) && featureSetting == designLevel4Selector) ||
                            ((typoFlags & LE_SS05_FEATURE_FLAG) && featureSetting == designLevel5Selector) ||
                            ((typoFlags & LE_SS06_FEATURE_FLAG) && featureSetting == designLevel6Selector) ||
                            ((typoFlags & LE_SS07_FEATURE_FLAG) && featureSetting == designLevel7Selector)) {

                            flag &= disableFlags;
                            flag |= enableFlags;
                        }
                        break;
                    case styleOptionsType:
                        break;
                    case characterShapeType:
                        break;
                    case numberCaseType:
                        break;
                    case textSpacingType:
                        break;
                    case transliterationType:
                        break;
                    case annotationType:
                        if ((typoFlags & LE_NALT_FEATURE_FLAG) && featureSetting == circleAnnotationSelector) {
                            flag &= disableFlags;
                            flag |= enableFlags;
                        }
                        break;
                    case kanaSpacingType:
                        break;
                    case ideographicSpacingType:
                        break;
                    case rubyKanaType:
                        if ((typoFlags & LE_RUBY_FEATURE_FLAG) && featureSetting == rubyKanaOnSelector) {
                            flag &= disableFlags;
                            flag |= enableFlags;
                        }
                        break;
                    case cjkRomanSpacingType:
                        break;
                    default:
                        break;
                }
            }
        }

        for (subtable = 0;  LE_SUCCESS(success) && subtable < nSubtables; subtable++) {
            if(subtable>0)  {
              le_uint32 length = SWAPL(subtableHeader->length);
              if (length & 0x03) { // incorrect alignment for 32 bit tables
                  success = LE_MEMORY_ALLOCATION_ERROR; // as good a choice as any
                  return;
              }
              subtableHeader.addOffset(length, success); // Don't addOffset for the last entry.
              if (LE_FAILURE(success)) break;
            }
            le_uint32 coverage = SWAPL(subtableHeader->coverage);
            FeatureFlags subtableFeatures = SWAPL(subtableHeader->subtableFeatures);
            // should check coverage more carefully...
            if (((coverage & scfIgnoreVt2) || !(coverage & scfVertical2)) && (subtableFeatures & flag) != 0) {
              subtableHeader->process(subtableHeader, glyphStorage, success);
            }
        }
    }
}

void MorphSubtableHeader2::process(const LEReferenceTo<MorphSubtableHeader2> &base, LEGlyphStorage &glyphStorage, LEErrorCode &success) const
{
    SubtableProcessor2 *processor = NULL;

    if (LE_FAILURE(success)) return;

    switch (SWAPL(coverage) & scfTypeMask2)
    {
    case mstIndicRearrangement:
        processor = new IndicRearrangementProcessor2(base, success);
        break;

    case mstContextualGlyphSubstitution:
        processor = new ContextualGlyphSubstitutionProcessor2(base, success);
        break;

    case mstLigatureSubstitution:
        processor = new LigatureSubstitutionProcessor2(base, success);
        break;

    case mstReservedUnused:
        break;

    case mstNonContextualGlyphSubstitution:
        processor = NonContextualGlyphSubstitutionProcessor2::createInstance(base, success);
        break;


    case mstContextualGlyphInsertion:
        processor = new ContextualGlyphInsertionProcessor2(base, success);
        break;

    default:
        return;
        break; /*NOTREACHED*/
    }

    if (processor != NULL) {
      processor->process(glyphStorage, success);
        delete processor;
    } else {
      if(LE_SUCCESS(success)) {
        success = LE_MEMORY_ALLOCATION_ERROR; // because ptr is null and we didn't break out.
      }
    }
}

U_NAMESPACE_END
