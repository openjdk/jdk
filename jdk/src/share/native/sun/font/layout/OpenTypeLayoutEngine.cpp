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
 * (C) Copyright IBM Corp. 1998-2010 - All Rights Reserved
 *
 */

#include "LETypes.h"
#include "LEScripts.h"
#include "LELanguages.h"

#include "LayoutEngine.h"
#include "CanonShaping.h"
#include "OpenTypeLayoutEngine.h"
#include "ScriptAndLanguageTags.h"
#include "CharSubstitutionFilter.h"

#include "GlyphSubstitutionTables.h"
#include "GlyphDefinitionTables.h"
#include "GlyphPositioningTables.h"

#include "LEGlyphStorage.h"
#include "GlyphPositionAdjustments.h"

#include "GDEFMarkFilter.h"

#include "KernTable.h"

U_NAMESPACE_BEGIN

UOBJECT_DEFINE_RTTI_IMPLEMENTATION(OpenTypeLayoutEngine)

#define ccmpFeatureTag LE_CCMP_FEATURE_TAG
#define ligaFeatureTag LE_LIGA_FEATURE_TAG
#define cligFeatureTag LE_CLIG_FEATURE_TAG
#define kernFeatureTag LE_KERN_FEATURE_TAG
#define markFeatureTag LE_MARK_FEATURE_TAG
#define mkmkFeatureTag LE_MKMK_FEATURE_TAG
#define loclFeatureTag LE_LOCL_FEATURE_TAG
#define caltFeatureTag LE_CALT_FEATURE_TAG

// 'dlig' not used at the moment
#define dligFeatureTag 0x646C6967

// 'palt'
#define paltFeatureTag 0x70616C74

#define ccmpFeatureMask 0x80000000UL
#define ligaFeatureMask 0x40000000UL
#define cligFeatureMask 0x20000000UL
#define kernFeatureMask 0x10000000UL
#define paltFeatureMask 0x08000000UL
#define markFeatureMask 0x04000000UL
#define mkmkFeatureMask 0x02000000UL
#define loclFeatureMask 0x01000000UL
#define caltFeatureMask 0x00800000UL

#define minimalFeatures     (ccmpFeatureMask | markFeatureMask | mkmkFeatureMask | loclFeatureMask | caltFeatureMask)
#define ligaFeatures        (ligaFeatureMask | cligFeatureMask | minimalFeatures)
#define kernFeatures        (kernFeatureMask | paltFeatureMask | minimalFeatures)
#define kernAndLigaFeatures (ligaFeatures    | kernFeatures)

static const FeatureMap featureMap[] =
{
    {ccmpFeatureTag, ccmpFeatureMask},
    {ligaFeatureTag, ligaFeatureMask},
    {cligFeatureTag, cligFeatureMask},
        {kernFeatureTag, kernFeatureMask},
    {paltFeatureTag, paltFeatureMask},
    {markFeatureTag, markFeatureMask},
    {mkmkFeatureTag, mkmkFeatureMask},
    {loclFeatureTag, loclFeatureMask},
    {caltFeatureTag, caltFeatureMask}
};

static const le_int32 featureMapCount = LE_ARRAY_SIZE(featureMap);

OpenTypeLayoutEngine::OpenTypeLayoutEngine(const LEFontInstance *fontInstance, le_int32 scriptCode, le_int32 languageCode,
                        le_int32 typoFlags, const GlyphSubstitutionTableHeader *gsubTable, LEErrorCode &success)
    : LayoutEngine(fontInstance, scriptCode, languageCode, typoFlags, success), fFeatureMask(minimalFeatures),
      fFeatureMap(featureMap), fFeatureMapCount(featureMapCount), fFeatureOrder(FALSE),
      fGSUBTable(gsubTable), fGDEFTable(NULL), fGPOSTable(NULL), fSubstitutionFilter(NULL)
{
    static const le_uint32 gdefTableTag = LE_GDEF_TABLE_TAG;
    static const le_uint32 gposTableTag = LE_GPOS_TABLE_TAG;
    const GlyphPositioningTableHeader *gposTable = (const GlyphPositioningTableHeader *) getFontTable(gposTableTag);

    // todo: switch to more flags and bitfield rather than list of feature tags?
    switch (typoFlags & ~0x80000000L) {
    case 0: break; // default
    case 1: fFeatureMask = kernFeatures; break;
    case 2: fFeatureMask = ligaFeatures; break;
    case 3: fFeatureMask = kernAndLigaFeatures; break;
    default: break;
    }

    if (typoFlags & 0x80000000L) {
        fSubstitutionFilter = new CharSubstitutionFilter(fontInstance);
    }

    setScriptAndLanguageTags();

    fGDEFTable = (const GlyphDefinitionTableHeader *) getFontTable(gdefTableTag);

// JK patch, 2008-05-30 - see Sinhala bug report and LKLUG font
//    if (gposTable != NULL && gposTable->coversScriptAndLanguage(fScriptTag, fLangSysTag)) {
    if (gposTable != NULL && gposTable->coversScript(fScriptTag)) {
        fGPOSTable = gposTable;
    }
}

void OpenTypeLayoutEngine::reset()
{
    // NOTE: if we're called from
    // the destructor, LayoutEngine;:reset()
    // will have been called already by
    // LayoutEngine::~LayoutEngine()
    LayoutEngine::reset();
}

OpenTypeLayoutEngine::OpenTypeLayoutEngine(const LEFontInstance *fontInstance, le_int32 scriptCode, le_int32 languageCode,
                       le_int32 typoFlags, LEErrorCode &success)
    : LayoutEngine(fontInstance, scriptCode, languageCode, typoFlags, success), fFeatureOrder(FALSE),
      fGSUBTable(NULL), fGDEFTable(NULL), fGPOSTable(NULL), fSubstitutionFilter(NULL)
{
    setScriptAndLanguageTags();
}

OpenTypeLayoutEngine::~OpenTypeLayoutEngine()
{
    if (fTypoFlags & 0x80000000L) {
        delete fSubstitutionFilter;
    }

    reset();
}

LETag OpenTypeLayoutEngine::getScriptTag(le_int32 scriptCode)
{
    if (scriptCode < 0 || scriptCode >= scriptCodeCount) {
        return 0xFFFFFFFF;
    }
    return scriptTags[scriptCode];
}

LETag OpenTypeLayoutEngine::getV2ScriptTag(le_int32 scriptCode)
{
        switch (scriptCode) {
                case bengScriptCode :    return bng2ScriptTag;
                case devaScriptCode :    return dev2ScriptTag;
                case gujrScriptCode :    return gjr2ScriptTag;
                case guruScriptCode :    return gur2ScriptTag;
                case kndaScriptCode :    return knd2ScriptTag;
                case mlymScriptCode :    return mlm2ScriptTag;
                case oryaScriptCode :    return ory2ScriptTag;
                case tamlScriptCode :    return tml2ScriptTag;
                case teluScriptCode :    return tel2ScriptTag;
                default:                 return nullScriptTag;
        }
}

LETag OpenTypeLayoutEngine::getLangSysTag(le_int32 languageCode)
{
    if (languageCode < 0 || languageCode >= languageCodeCount) {
        return 0xFFFFFFFF;
    }

    return languageTags[languageCode];
}

void OpenTypeLayoutEngine::setScriptAndLanguageTags()
{
    fScriptTag  = getScriptTag(fScriptCode);
    fScriptTagV2 = getV2ScriptTag(fScriptCode);
    fLangSysTag = getLangSysTag(fLanguageCode);
}

le_int32 OpenTypeLayoutEngine::characterProcessing(const LEUnicode chars[], le_int32 offset, le_int32 count, le_int32 max, le_bool rightToLeft,
                LEUnicode *&outChars, LEGlyphStorage &glyphStorage, LEErrorCode &success)
{
    if (LE_FAILURE(success)) {
        return 0;
    }

    if (offset < 0 || count < 0 || max < 0 || offset >= max || offset + count > max) {
        success = LE_ILLEGAL_ARGUMENT_ERROR;
        return 0;
    }

    // This is the cheapest way to get mark reordering only for Hebrew.
    // We could just do the mark reordering for all scripts, but most
    // of them probably don't need it... Another option would be to
    // add a HebrewOpenTypeLayoutEngine subclass, but the only thing it
    // would need to do is mark reordering, so that seems like overkill.
    if (fScriptCode == hebrScriptCode) {
        outChars = LE_NEW_ARRAY(LEUnicode, count);

        if (outChars == NULL) {
            success = LE_MEMORY_ALLOCATION_ERROR;
            return 0;
        }

    if (LE_FAILURE(success)) {
            LE_DELETE_ARRAY(outChars);
        return 0;
    }

        CanonShaping::reorderMarks(&chars[offset], count, rightToLeft, outChars, glyphStorage);
    }

    if (LE_FAILURE(success)) {
        return 0;
    }

    glyphStorage.allocateGlyphArray(count, rightToLeft, success);
    glyphStorage.allocateAuxData(success);

    for (le_int32 i = 0; i < count; i += 1) {
        glyphStorage.setAuxData(i, fFeatureMask, success);
    }

    return count;
}

// Input: characters, tags
// Output: glyphs, char indices
le_int32 OpenTypeLayoutEngine::glyphProcessing(const LEUnicode chars[], le_int32 offset, le_int32 count, le_int32 max, le_bool rightToLeft,
                                               LEGlyphStorage &glyphStorage, LEErrorCode &success)
{
    if (LE_FAILURE(success)) {
        return 0;
    }

    if (chars == NULL || offset < 0 || count < 0 || max < 0 || offset >= max || offset + count > max) {
        success = LE_ILLEGAL_ARGUMENT_ERROR;
        return 0;
    }

    mapCharsToGlyphs(chars, offset, count, rightToLeft, rightToLeft, glyphStorage, success);

    if (LE_FAILURE(success)) {
        return 0;
    }

    if (fGSUBTable != NULL) {
        if (fScriptTagV2 != nullScriptTag && fGSUBTable->coversScriptAndLanguage(fScriptTagV2,fLangSysTag)) {
            count = fGSUBTable->process(glyphStorage, rightToLeft, fScriptTagV2, fLangSysTag, fGDEFTable, fSubstitutionFilter,
                                    fFeatureMap, fFeatureMapCount, fFeatureOrder, success);

        } else {
        count = fGSUBTable->process(glyphStorage, rightToLeft, fScriptTag, fLangSysTag, fGDEFTable, fSubstitutionFilter,
                                    fFeatureMap, fFeatureMapCount, fFeatureOrder, success);
    }
    }

    return count;
}
// Input: characters, tags
// Output: glyphs, char indices
le_int32 OpenTypeLayoutEngine::glyphSubstitution(le_int32 count, le_int32 max, le_bool rightToLeft,
                                               LEGlyphStorage &glyphStorage, LEErrorCode &success)
{
    if (LE_FAILURE(success)) {
        return 0;
    }

    if ( count < 0 || max < 0 ) {
        success = LE_ILLEGAL_ARGUMENT_ERROR;
        return 0;
    }

    if (fGSUBTable != NULL) {
        if (fScriptTagV2 != nullScriptTag && fGSUBTable->coversScriptAndLanguage(fScriptTagV2,fLangSysTag)) {
            count = fGSUBTable->process(glyphStorage, rightToLeft, fScriptTagV2, fLangSysTag, fGDEFTable, fSubstitutionFilter,
                                    fFeatureMap, fFeatureMapCount, fFeatureOrder, success);

        } else {
        count = fGSUBTable->process(glyphStorage, rightToLeft, fScriptTag, fLangSysTag, fGDEFTable, fSubstitutionFilter,
                                    fFeatureMap, fFeatureMapCount, fFeatureOrder, success);
        }
    }

    return count;
}
le_int32 OpenTypeLayoutEngine::glyphPostProcessing(LEGlyphStorage &tempGlyphStorage, LEGlyphStorage &glyphStorage, LEErrorCode &success)
{
    if (LE_FAILURE(success)) {
        return 0;
    }

    glyphStorage.adoptGlyphArray(tempGlyphStorage);
    glyphStorage.adoptCharIndicesArray(tempGlyphStorage);
    glyphStorage.adoptAuxDataArray(tempGlyphStorage);
    glyphStorage.adoptGlyphCount(tempGlyphStorage);

    return glyphStorage.getGlyphCount();
}

le_int32 OpenTypeLayoutEngine::computeGlyphs(const LEUnicode chars[], le_int32 offset, le_int32 count, le_int32 max, le_bool rightToLeft, LEGlyphStorage &glyphStorage, LEErrorCode &success)
{
    LEUnicode *outChars = NULL;
    LEGlyphStorage fakeGlyphStorage;
    le_int32 outCharCount, outGlyphCount, fakeGlyphCount;

    if (LE_FAILURE(success)) {
        return 0;
    }

    if (chars == NULL || offset < 0 || count < 0 || max < 0 || offset >= max || offset + count > max) {
        success = LE_ILLEGAL_ARGUMENT_ERROR;
        return 0;
    }

    outCharCount = characterProcessing(chars, offset, count, max, rightToLeft, outChars, fakeGlyphStorage, success);

    if (LE_FAILURE(success)) {
        return 0;
    }

    if (outChars != NULL) {
        fakeGlyphCount = glyphProcessing(outChars, 0, outCharCount, outCharCount, rightToLeft, fakeGlyphStorage, success);
        LE_DELETE_ARRAY(outChars); // FIXME: a subclass may have allocated this, in which case this delete might not work...
        //adjustGlyphs(outChars, 0, outCharCount, rightToLeft, fakeGlyphs, fakeGlyphCount);
    } else {
        fakeGlyphCount = glyphProcessing(chars, offset, count, max, rightToLeft, fakeGlyphStorage, success);
        //adjustGlyphs(chars, offset, count, rightToLeft, fakeGlyphs, fakeGlyphCount);
    }

    if (LE_FAILURE(success)) {
        return 0;
    }

    outGlyphCount = glyphPostProcessing(fakeGlyphStorage, glyphStorage, success);

    return outGlyphCount;
}

// apply GPOS table, if any
void OpenTypeLayoutEngine::adjustGlyphPositions(const LEUnicode chars[], le_int32 offset, le_int32 count, le_bool reverse,
                                                LEGlyphStorage &glyphStorage, LEErrorCode &success)
{
    if (LE_FAILURE(success)) {
        return;
    }

    if (chars == NULL || offset < 0 || count < 0) {
        success = LE_ILLEGAL_ARGUMENT_ERROR;
        return;
    }

    le_int32 glyphCount = glyphStorage.getGlyphCount();
    if (glyphCount == 0) {
        return;
    }

    if (fGPOSTable != NULL) {
        GlyphPositionAdjustments *adjustments = new GlyphPositionAdjustments(glyphCount);
        le_int32 i;

        if (adjustments == NULL) {
            success = LE_MEMORY_ALLOCATION_ERROR;
            return;
        }

#if 0
        // Don't need to do this if we allocate
        // the adjustments array w/ new...
        for (i = 0; i < glyphCount; i += 1) {
            adjustments->setXPlacement(i, 0);
            adjustments->setYPlacement(i, 0);

            adjustments->setXAdvance(i, 0);
            adjustments->setYAdvance(i, 0);

            adjustments->setBaseOffset(i, -1);
        }
#endif

        if (fGPOSTable != NULL) {
            if (fScriptTagV2 != nullScriptTag && fGPOSTable->coversScriptAndLanguage(fScriptTagV2,fLangSysTag)) {
                fGPOSTable->process(glyphStorage, adjustments, reverse, fScriptTagV2, fLangSysTag, fGDEFTable, success, fFontInstance,
                            fFeatureMap, fFeatureMapCount, fFeatureOrder);

            } else {
                fGPOSTable->process(glyphStorage, adjustments, reverse, fScriptTag, fLangSysTag, fGDEFTable, success, fFontInstance,
                                fFeatureMap, fFeatureMapCount, fFeatureOrder);
            }
        } else if ( fTypoFlags & 0x1 ) {
            static const le_uint32 kernTableTag = LE_KERN_TABLE_TAG;
            KernTable kt(fFontInstance, getFontTable(kernTableTag));
            kt.process(glyphStorage);
        }

        float xAdjust = 0, yAdjust = 0;

        for (i = 0; i < glyphCount; i += 1) {
            float xAdvance   = adjustments->getXAdvance(i);
            float yAdvance   = adjustments->getYAdvance(i);
            float xPlacement = 0;
            float yPlacement = 0;


#if 0
            // This is where separate kerning adjustments
            // should get applied.
            xAdjust += xKerning;
            yAdjust += yKerning;
#endif

            for (le_int32 base = i; base >= 0; base = adjustments->getBaseOffset(base)) {
                xPlacement += adjustments->getXPlacement(base);
                yPlacement += adjustments->getYPlacement(base);
            }

            xPlacement = fFontInstance->xUnitsToPoints(xPlacement);
            yPlacement = fFontInstance->yUnitsToPoints(yPlacement);
            glyphStorage.adjustPosition(i, xAdjust + xPlacement, -(yAdjust + yPlacement), success);

            xAdjust += fFontInstance->xUnitsToPoints(xAdvance);
            yAdjust += fFontInstance->yUnitsToPoints(yAdvance);
        }

        glyphStorage.adjustPosition(glyphCount, xAdjust, -yAdjust, success);

        delete adjustments;
    } else {
        // if there was no GPOS table, maybe there's non-OpenType kerning we can use
        LayoutEngine::adjustGlyphPositions(chars, offset, count, reverse, glyphStorage, success);
    }

    LEGlyphID zwnj  = fFontInstance->mapCharToGlyph(0x200C);

    if (zwnj != 0x0000) {
        for (le_int32 g = 0; g < glyphCount; g += 1) {
            LEGlyphID glyph = glyphStorage[g];

            if (glyph == zwnj) {
                glyphStorage[g] = LE_SET_GLYPH(glyph, 0xFFFF);
            }
        }
    }

#if 0
    // Don't know why this is here...
    LE_DELETE_ARRAY(fFeatureTags);
    fFeatureTags = NULL;
#endif
}

U_NAMESPACE_END
