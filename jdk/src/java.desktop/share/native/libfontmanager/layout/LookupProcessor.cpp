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
 * (C) Copyright IBM Corp. 1998-2013 - All Rights Reserved
 *
 */

#include "LETypes.h"
#include "OpenTypeUtilities.h"
#include "LEFontInstance.h"
#include "OpenTypeTables.h"
#include "ICUFeatures.h"
#include "Lookups.h"
#include "ScriptAndLanguage.h"
#include "GlyphDefinitionTables.h"
#include "GlyphIterator.h"
#include "LookupProcessor.h"
#include "LEGlyphStorage.h"
#include "LESwaps.h"

U_NAMESPACE_BEGIN

le_uint32 LookupProcessor::applyLookupTable(const LEReferenceTo<LookupTable> &lookupTable, GlyphIterator *glyphIterator,
                                         const LEFontInstance *fontInstance, LEErrorCode& success) const
{
    if (LE_FAILURE(success)) {
        return 0;
    }

    le_uint16 lookupType = SWAPW(lookupTable->lookupType);
    le_uint16 subtableCount = SWAPW(lookupTable->subTableCount);
    le_int32 startPosition = glyphIterator->getCurrStreamPosition();
    le_uint32 delta;

    for (le_uint16 subtable = 0; subtable < subtableCount; subtable += 1) {
      LEReferenceTo<LookupSubtable> lookupSubtable = lookupTable->getLookupSubtable(lookupTable, subtable, success);

        delta = applySubtable(lookupSubtable, lookupType, glyphIterator, fontInstance, success);
        if (delta > 0 && LE_FAILURE(success)) {
#if LE_TRACE
          _LETRACE("Posn #%d, type %X, applied subtable #%d/%d - %s\n", startPosition, lookupType, subtable, subtableCount, u_errorName((UErrorCode)success));
#endif
          return 1;
        }

        glyphIterator->setCurrStreamPosition(startPosition);
    }

    return 1;
}

le_int32 LookupProcessor::process(LEGlyphStorage &glyphStorage, GlyphPositionAdjustments *glyphPositionAdjustments,
                                  le_bool rightToLeft, const LEReferenceTo<GlyphDefinitionTableHeader> &glyphDefinitionTableHeader,
                              const LEFontInstance *fontInstance, LEErrorCode& success) const
{
    if (LE_FAILURE(success)) {
        return 0;
    }

    le_int32 glyphCount = glyphStorage.getGlyphCount();

    if (lookupSelectArray == NULL) {
        return glyphCount;
    }

    GlyphIterator glyphIterator(glyphStorage, glyphPositionAdjustments,
                                rightToLeft, 0, 0, glyphDefinitionTableHeader, success);
    le_int32 newGlyphCount = glyphCount;

    for (le_uint16 order = 0; order < lookupOrderCount && LE_SUCCESS(success); order += 1) {
        le_uint16 lookup = lookupOrderArray[order];
        FeatureMask selectMask = lookupSelectArray[lookup];

        if (selectMask != 0) {
          _LETRACE("Processing order#%d/%d", order, lookupOrderCount);
          const LEReferenceTo<LookupTable> lookupTable = lookupListTable->getLookupTable(lookupListTable, lookup, success);
          if (!lookupTable.isValid() ||LE_FAILURE(success) ) {
                continue;
            }
            le_uint16 lookupFlags = SWAPW(lookupTable->lookupFlags);

            glyphIterator.reset(lookupFlags, selectMask);

            while (glyphIterator.findFeatureTag()) {
                applyLookupTable(lookupTable, &glyphIterator, fontInstance, success);
                if (LE_FAILURE(success)) {
#if LE_TRACE
                    _LETRACE("Failure for lookup 0x%x - %s\n", lookup, u_errorName((UErrorCode)success));
#endif
                    return 0;
                }
            }

            newGlyphCount = glyphIterator.applyInsertions();
        }
    }

    return newGlyphCount;
}

le_uint32 LookupProcessor::applySingleLookup(le_uint16 lookupTableIndex, GlyphIterator *glyphIterator,
                                          const LEFontInstance *fontInstance, LEErrorCode& success) const
{
    if (LE_FAILURE(success)) {
        return 0;
    }

    const LEReferenceTo<LookupTable> lookupTable = lookupListTable->getLookupTable(lookupListTable, lookupTableIndex, success);
    if (!lookupTable.isValid()) {
        success = LE_INTERNAL_ERROR;
        return 0;
    }
    le_uint16 lookupFlags = SWAPW(lookupTable->lookupFlags);
    GlyphIterator tempIterator(*glyphIterator, lookupFlags);
    le_uint32 delta = applyLookupTable(lookupTable, &tempIterator, fontInstance, success);

    return delta;
}

le_int32 LookupProcessor::selectLookups(const LEReferenceTo<FeatureTable> &featureTable, FeatureMask featureMask, le_int32 order, LEErrorCode &success)
{
  le_uint16 lookupCount = featureTable.isValid()? SWAPW(featureTable->lookupCount) : 0;
    le_uint32  store = (le_uint32)order;

    LEReferenceToArrayOf<le_uint16> lookupListIndexArray(featureTable, success, featureTable->lookupListIndexArray, lookupCount);

    for (le_uint16 lookup = 0; LE_SUCCESS(success) && lookup < lookupCount; lookup += 1) {
      le_uint16 lookupListIndex = SWAPW(lookupListIndexArray.getObject(lookup,success));
      if (lookupListIndex >= lookupSelectCount) {
        continue;
      }
      if (store >= lookupOrderCount) {
        continue;
      }

      lookupSelectArray[lookupListIndex] |= featureMask;
      lookupOrderArray[store++] = lookupListIndex;
    }

    return store - order;
}

LookupProcessor::LookupProcessor(const LETableReference &baseAddress,
        Offset scriptListOffset, Offset featureListOffset, Offset lookupListOffset,
        LETag scriptTag, LETag languageTag, const FeatureMap *featureMap, le_int32 featureMapCount, le_bool orderFeatures,
        LEErrorCode& success)
    : lookupListTable(), featureListTable(), lookupSelectArray(NULL), lookupSelectCount(0),
      lookupOrderArray(NULL), lookupOrderCount(0), fReference(baseAddress)
{
  LEReferenceTo<ScriptListTable> scriptListTable;
  LEReferenceTo<LangSysTable> langSysTable;
    le_uint16 featureCount = 0;
    le_uint16 lookupListCount = 0;
    le_uint16 requiredFeatureIndex;

    if (LE_FAILURE(success)) {
        return;
    }

    if (scriptListOffset != 0) {
      scriptListTable = LEReferenceTo<ScriptListTable>(baseAddress, success, scriptListOffset);
      langSysTable = scriptListTable->findLanguage(scriptListTable, scriptTag, languageTag, success);

      if (langSysTable.isValid() && LE_SUCCESS(success)) {
        featureCount = SWAPW(langSysTable->featureCount);
      }
    }

    if (featureListOffset != 0) {
      featureListTable = LEReferenceTo<FeatureListTable>(baseAddress, success, featureListOffset);
    }

    if (lookupListOffset != 0) {
      lookupListTable = LEReferenceTo<LookupListTable>(baseAddress,success, lookupListOffset);
      if(LE_SUCCESS(success) && lookupListTable.isValid()) {
        lookupListCount = SWAPW(lookupListTable->lookupCount);
      }
    }

    if (langSysTable.isEmpty() || featureListTable.isEmpty() || lookupListTable.isEmpty() ||
        featureCount == 0 || lookupListCount == 0) {
        return;
    }

    if(langSysTable.isValid()) {
      requiredFeatureIndex = SWAPW(langSysTable->reqFeatureIndex);
    }

    lookupSelectArray = LE_NEW_ARRAY(FeatureMask, lookupListCount);
    if (lookupSelectArray == NULL) {
        success = LE_MEMORY_ALLOCATION_ERROR;
        return;
    }

    for (int i = 0; i < lookupListCount; i += 1) {
        lookupSelectArray[i] = 0;
    }

    lookupSelectCount = lookupListCount;

    le_int32 count, order = 0;
    le_uint32 featureReferences = 0;
    LEReferenceTo<FeatureTable> featureTable;
    LETag featureTag;

    LEReferenceTo<FeatureTable> requiredFeatureTable;
    LETag requiredFeatureTag = 0x00000000U;

    // Count the total number of lookups referenced by all features. This will
    // be the maximum number of entries in the lookupOrderArray. We can't use
    // lookupListCount because some lookups might be referenced by more than
    // one feature.
    if(featureListTable.isValid() && LE_SUCCESS(success)) {
      LEReferenceToArrayOf<le_uint16> featureIndexArray(langSysTable, success, langSysTable->featureIndexArray, featureCount);

      for (le_uint32 feature = 0; LE_SUCCESS(success)&&(feature < featureCount); feature += 1) {
        le_uint16 featureIndex = SWAPW(featureIndexArray.getObject(feature, success));

        featureTable = featureListTable->getFeatureTable(featureListTable, featureIndex,  &featureTag, success);
        if (!featureTable.isValid() || LE_FAILURE(success)) {
          continue;
        }
        featureReferences += SWAPW(featureTable->lookupCount);
      }
    }

    if (!featureTable.isValid() || LE_FAILURE(success)) {
        success = LE_INTERNAL_ERROR;
        return;
    }

    if (requiredFeatureIndex != 0xFFFF) {
      requiredFeatureTable = featureListTable->getFeatureTable(featureListTable, requiredFeatureIndex, &requiredFeatureTag, success);
      featureReferences += SWAPW(requiredFeatureTable->lookupCount);
    }

    lookupOrderArray = LE_NEW_ARRAY(le_uint16, featureReferences);
    if (lookupOrderArray == NULL) {
        success = LE_MEMORY_ALLOCATION_ERROR;
        return;
    }
    lookupOrderCount = featureReferences;

    for (le_int32 f = 0; f < featureMapCount; f += 1) {
        FeatureMap fm = featureMap[f];
        count = 0;

        // If this is the required feature, add its lookups
        if (requiredFeatureTag == fm.tag) {
          count += selectLookups(requiredFeatureTable, fm.mask, order, success);
        }

        if (orderFeatures) {
            // If we added lookups from the required feature, sort them
            if (count > 1) {
                OpenTypeUtilities::sort(lookupOrderArray, order);
            }

            for (le_uint16 feature = 0; feature < featureCount; feature += 1) {
              LEReferenceToArrayOf<le_uint16> featureIndexArray(langSysTable, success, langSysTable->featureIndexArray, featureCount);
              if (LE_FAILURE(success)) { continue; }
              le_uint16 featureIndex = SWAPW(featureIndexArray.getObject(feature,success));

                // don't add the required feature to the list more than once...
                // TODO: Do we need this check? (Spec. says required feature won't be in feature list...)
                if (featureIndex == requiredFeatureIndex) {
                    continue;
                }

                featureTable = featureListTable->getFeatureTable(featureListTable, featureIndex, &featureTag, success);

                if (featureTag == fm.tag) {
                  count += selectLookups(featureTable, fm.mask, order + count, success);
                }
            }

            if (count > 1) {
                OpenTypeUtilities::sort(&lookupOrderArray[order], count);
            }

            order += count;
        } else if(langSysTable.isValid()) {
          LEReferenceToArrayOf<le_uint16> featureIndexArray(langSysTable, success, langSysTable->featureIndexArray, featureCount);
          for (le_uint16 feature = 0; LE_SUCCESS(success)&& (feature < featureCount); feature += 1) {
            le_uint16 featureIndex = SWAPW(featureIndexArray.getObject(feature,success));

                // don't add the required feature to the list more than once...
                // NOTE: This check is commented out because the spec. says that
                // the required feature won't be in the feature list, and because
                // any duplicate entries will be removed below.
#if 0
                if (featureIndex == requiredFeatureIndex) {
                    continue;
                }
#endif

                featureTable = featureListTable->getFeatureTable(featureListTable, featureIndex, &featureTag, success);

                if (featureTag == fm.tag) {
                  order += selectLookups(featureTable, fm.mask, order, success);
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
        lookupOrderArray = NULL;
        lookupSelectArray = NULL;
}

LookupProcessor::~LookupProcessor()
{
    LE_DELETE_ARRAY(lookupOrderArray);
    LE_DELETE_ARRAY(lookupSelectArray);
}

U_NAMESPACE_END
