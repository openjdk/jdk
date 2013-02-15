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

void MorphTableHeader2::process(LEGlyphStorage &glyphStorage, le_int32 typoFlags) const
{
    const ChainHeader2 *chainHeader = chains;
    le_uint32 chainCount = SWAPL(this->nChains);
    le_uint32 chain;

    for (chain = 0; chain < chainCount; chain++) {
        FeatureFlags flag = SWAPL(chainHeader->defaultFlags);
        le_uint32 chainLength = SWAPL(chainHeader->chainLength);
        le_uint32 nFeatureEntries = SWAPL(chainHeader->nFeatureEntries);
        le_uint32 nSubtables = SWAPL(chainHeader->nSubtables);
        const MorphSubtableHeader2 *subtableHeader =
            (const MorphSubtableHeader2 *)&chainHeader->featureTable[nFeatureEntries];
        le_uint32 subtable;

        if (typoFlags != 0) {
           le_uint32 featureEntry;

            // Feature subtables
            for (featureEntry = 0; featureEntry < nFeatureEntries; featureEntry++) {
                FeatureTableEntry featureTableEntry = chains->featureTable[featureEntry];
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

        for (subtable = 0; subtable < nSubtables; subtable++) {
            le_uint32 length = SWAPL(subtableHeader->length);
            le_uint32 coverage = SWAPL(subtableHeader->coverage);
            FeatureFlags subtableFeatures = SWAPL(subtableHeader->subtableFeatures);
            // should check coverage more carefully...
            if (((coverage & scfIgnoreVt2) || !(coverage & scfVertical2)) && (subtableFeatures & flag) != 0) {
                subtableHeader->process(glyphStorage);
            }
            subtableHeader = (const MorphSubtableHeader2 *) ((char *)subtableHeader + length);
        }
        chainHeader = (const ChainHeader2 *)((char *)chainHeader + chainLength);
    }
}

void MorphSubtableHeader2::process(LEGlyphStorage &glyphStorage) const
{
    SubtableProcessor2 *processor = NULL;

    switch (SWAPL(coverage) & scfTypeMask2)
    {
    case mstIndicRearrangement:
        processor = new IndicRearrangementProcessor2(this);
        break;

    case mstContextualGlyphSubstitution:
        processor = new ContextualGlyphSubstitutionProcessor2(this);
        break;

    case mstLigatureSubstitution:
        processor = new LigatureSubstitutionProcessor2(this);
        break;

    case mstReservedUnused:
        break;

    case mstNonContextualGlyphSubstitution:
        processor = NonContextualGlyphSubstitutionProcessor2::createInstance(this);
        break;


    case mstContextualGlyphInsertion:
        processor = new ContextualGlyphInsertionProcessor2(this);
        break;

    default:
        break;
    }

    if (processor != NULL) {
        processor->process(glyphStorage);
        delete processor;
    }
}

U_NAMESPACE_END
