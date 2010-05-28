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
 * (C) Copyright IBM Corp. 1998-2005 - All Rights Reserved
 *
 */

#include "LETypes.h"
#include "OpenTypeUtilities.h"
#include "LEFontInstance.h"
#include "OpenTypeTables.h"
#include "Features.h"
#include "Lookups.h"
#include "ScriptAndLanguage.h"
#include "GlyphDefinitionTables.h"
#include "GlyphIterator.h"
#include "LookupProcessor.h"
#include "LEGlyphStorage.h"
#include "LESwaps.h"

U_NAMESPACE_BEGIN

le_uint32 LookupProcessor::applyLookupTable(const LookupTable *lookupTable, GlyphIterator *glyphIterator,
                                         const LEFontInstance *fontInstance) const
{
    le_uint16 lookupType = SWAPW(lookupTable->lookupType);
    le_uint16 subtableCount = SWAPW(lookupTable->subTableCount);
    le_int32 startPosition = glyphIterator->getCurrStreamPosition();
    le_uint32 delta;

    for (le_uint16 subtable = 0; subtable < subtableCount; subtable += 1) {
        const LookupSubtable *lookupSubtable = lookupTable->getLookupSubtable(subtable);

        delta = applySubtable(lookupSubtable, lookupType, glyphIterator, fontInstance);

        if (delta > 0) {
            return 1;
        }

        glyphIterator->setCurrStreamPosition(startPosition);
    }

    return 1;
}

le_int32 LookupProcessor::process(LEGlyphStorage &glyphStorage, GlyphPositionAdjustments *glyphPositionAdjustments,
                              le_bool rightToLeft, const GlyphDefinitionTableHeader *glyphDefinitionTableHeader,
                              const LEFontInstance *fontInstance) const
{
    le_int32 glyphCount = glyphStorage.getGlyphCount();

    if (lookupSelectArray == NULL) {
        return glyphCount;
    }

    GlyphIterator glyphIterator(glyphStorage, glyphPositionAdjustments,
                                rightToLeft, 0, 0, glyphDefinitionTableHeader);
    le_int32 newGlyphCount = glyphCount;

    for (le_uint16 order = 0; order < lookupOrderCount; order += 1) {
        le_uint16 lookup = lookupOrderArray[order];
        FeatureMask selectMask = lookupSelectArray[lookup];

        if (selectMask != 0) {
            const LookupTable *lookupTable = lookupListTable->getLookupTable(lookup);
            le_uint16 lookupFlags = SWAPW(lookupTable->lookupFlags);

            glyphIterator.reset(lookupFlags, selectMask);

            while (glyphIterator.findFeatureTag()) {
                le_uint32 delta = 1;

                while (glyphIterator.next(delta)) {
                    delta = applyLookupTable(lookupTable, &glyphIterator, fontInstance);
                }
            }

            newGlyphCount = glyphIterator.applyInsertions();
        }
    }

    return newGlyphCount;
}

le_uint32 LookupProcessor::applySingleLookup(le_uint16 lookupTableIndex, GlyphIterator *glyphIterator,
                                          const LEFontInstance *fontInstance) const
{
    const LookupTable *lookupTable = lookupListTable->getLookupTable(lookupTableIndex);
    le_uint16 lookupFlags = SWAPW(lookupTable->lookupFlags);
    GlyphIterator tempIterator(*glyphIterator, lookupFlags);
    le_uint32 delta = applyLookupTable(lookupTable, &tempIterator, fontInstance);

    return delta;
}

le_int32 LookupProcessor::selectLookups(const FeatureTable *featureTable, FeatureMask featureMask, le_int32 order)
{
    le_uint16 lookupCount = featureTable? SWAPW(featureTable->lookupCount) : 0;
    le_int32  store = order;

    for (le_uint16 lookup = 0; lookup < lookupCount; lookup += 1) {
        le_uint16 lookupListIndex = SWAPW(featureTable->lookupListIndexArray[lookup]);

        lookupSelectArray[lookupListIndex] |= featureMask;
        lookupOrderArray[store++] = lookupListIndex;
    }

    return store - order;
}

LookupProcessor::LookupProcessor(const char *baseAddress,
        Offset scriptListOffset, Offset featureListOffset, Offset lookupListOffset,
        LETag scriptTag, LETag languageTag, const FeatureMap *featureMap, le_int32 featureMapCount, le_bool orderFeatures)
    : lookupListTable(NULL), featureListTable(NULL), lookupSelectArray(NULL),
      lookupOrderArray(NULL), lookupOrderCount(0)
{
    const ScriptListTable *scriptListTable = NULL;
    const LangSysTable *langSysTable = NULL;
    le_uint16 featureCount = 0;
    le_uint16 lookupListCount = 0;
    le_uint16 requiredFeatureIndex;

    if (scriptListOffset != 0) {
        scriptListTable = (const ScriptListTable *) (baseAddress + scriptListOffset);
        langSysTable = scriptListTable->findLanguage(scriptTag, languageTag);

        if (langSysTable != 0) {
            featureCount = SWAPW(langSysTable->featureCount);
        }
    }

    if (featureListOffset != 0) {
        featureListTable = (const FeatureListTable *) (baseAddress + featureListOffset);
    }

    if (lookupListOffset != 0) {
        lookupListTable = (const LookupListTable *) (baseAddress + lookupListOffset);
        lookupListCount = SWAPW(lookupListTable->lookupCount);
    }

    if (langSysTable == NULL || featureListTable == NULL || lookupListTable == NULL ||
        featureCount == 0 || lookupListCount == 0) {
        return;
    }

    requiredFeatureIndex = SWAPW(langSysTable->reqFeatureIndex);

    lookupSelectArray = LE_NEW_ARRAY(FeatureMask, lookupListCount);

    for (int i = 0; i < lookupListCount; i += 1) {
        lookupSelectArray[i] = 0;
    }

    le_int32 count, order = 0;
    le_int32 featureReferences = 0;
    const FeatureTable *featureTable = NULL;
    LETag featureTag;

    const FeatureTable *requiredFeatureTable = NULL;
    LETag requiredFeatureTag = 0x00000000U;

    // Count the total number of lookups referenced by all features. This will
    // be the maximum number of entries in the lookupOrderArray. We can't use
    // lookupListCount because some lookups might be referenced by more than
    // one feature.
    for (le_int32 feature = 0; feature < featureCount; feature += 1) {
        le_uint16 featureIndex = SWAPW(langSysTable->featureIndexArray[feature]);

        featureTable = featureListTable->getFeatureTable(featureIndex, &featureTag);
        featureReferences += SWAPW(featureTable->lookupCount);
    }

    if (requiredFeatureIndex != 0xFFFF) {
        requiredFeatureTable = featureListTable->getFeatureTable(requiredFeatureIndex, &requiredFeatureTag);
        featureReferences += SWAPW(featureTable->lookupCount);
    }

    lookupOrderArray = LE_NEW_ARRAY(le_uint16, featureReferences);

    for (le_int32 f = 0; f < featureMapCount; f += 1) {
        FeatureMap fm = featureMap[f];
        count = 0;

        // If this is the required feature, add its lookups
        if (requiredFeatureTag == fm.tag) {
            count += selectLookups(requiredFeatureTable, fm.mask, order);
        }

        if (orderFeatures) {
            // If we added lookups from the required feature, sort them
            if (count > 1) {
                OpenTypeUtilities::sort(lookupOrderArray, order);
            }

            for (le_uint16 feature = 0; feature < featureCount; feature += 1) {
                le_uint16 featureIndex = SWAPW(langSysTable->featureIndexArray[feature]);

                // don't add the required feature to the list more than once...
                // TODO: Do we need this check? (Spec. says required feature won't be in feature list...)
                if (featureIndex == requiredFeatureIndex) {
                    continue;
                }

                featureTable = featureListTable->getFeatureTable(featureIndex, &featureTag);

                if (featureTag == fm.tag) {
                    count += selectLookups(featureTable, fm.mask, order + count);
                }
            }

            if (count > 1) {
                OpenTypeUtilities::sort(&lookupOrderArray[order], count);
            }

            order += count;
        } else {
            for (le_uint16 feature = 0; feature < featureCount; feature += 1) {
                le_uint16 featureIndex = SWAPW(langSysTable->featureIndexArray[feature]);

                // don't add the required feature to the list more than once...
                // NOTE: This check is commented out because the spec. says that
                // the required feature won't be in the feature list, and because
                // any duplicate entries will be removed below.
#if 0
                if (featureIndex == requiredFeatureIndex) {
                    continue;
                }
#endif

                featureTable = featureListTable->getFeatureTable(featureIndex, &featureTag);

                if (featureTag == fm.tag) {
                    order += selectLookups(featureTable, fm.mask, order);
                }
            }
        }
    }

    if (!orderFeatures && (order > 1)) {
        OpenTypeUtilities::sort(lookupOrderArray, order);

        // If there's no specified feature order,
        // we will apply the lookups in the order
        // that they're in the font. If a particular
        // lookup may be referenced by more than one feature,
        // it will apprear in the lookupOrderArray more than
        // once, so remove any duplicate entries in the sorted array.
        le_int32 out = 1;

        for (le_int32 in = 1; in < order; in += 1) {
            if (lookupOrderArray[out - 1] != lookupOrderArray[in]) {
                if (out != in) {
                    lookupOrderArray[out] = lookupOrderArray[in];
                }

                out += 1;
            }
        }

        order = out;
    }

    lookupOrderCount = order;
}

LookupProcessor::LookupProcessor()
{
}

LookupProcessor::~LookupProcessor()
{
    LE_DELETE_ARRAY(lookupOrderArray);
    LE_DELETE_ARRAY(lookupSelectArray);
}

U_NAMESPACE_END
