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

#define dligFeatureTag LE_DLIG_FEATURE_TAG
#define rligFeatureTag LE_RLIG_FEATURE_TAG
#define paltFeatureTag LE_PALT_FEATURE_TAG

#define hligFeatureTag LE_HLIG_FEATURE_TAG
#define smcpFeatureTag LE_SMCP_FEATURE_TAG
#define fracFeatureTag LE_FRAC_FEATURE_TAG
#define afrcFeatureTag LE_AFRC_FEATURE_TAG
#define zeroFeatureTag LE_ZERO_FEATURE_TAG
#define swshFeatureTag LE_SWSH_FEATURE_TAG
#define cswhFeatureTag LE_CSWH_FEATURE_TAG
#define saltFeatureTag LE_SALT_FEATURE_TAG
#define naltFeatureTag LE_NALT_FEATURE_TAG
#define rubyFeatureTag LE_RUBY_FEATURE_TAG
#define ss01FeatureTag LE_SS01_FEATURE_TAG
#define ss02FeatureTag LE_SS02_FEATURE_TAG
#define ss03FeatureTag LE_SS03_FEATURE_TAG
#define ss04FeatureTag LE_SS04_FEATURE_TAG
#define ss05FeatureTag LE_SS05_FEATURE_TAG
#define ss06FeatureTag LE_SS06_FEATURE_TAG
#define ss07FeatureTag LE_SS07_FEATURE_TAG

#define ccmpFeatureMask 0x80000000UL
#define ligaFeatureMask 0x40000000UL
#define cligFeatureMask 0x20000000UL
#define kernFeatureMask 0x10000000UL
#define paltFeatureMask 0x08000000UL
#define markFeatureMask 0x04000000UL
#define mkmkFeatureMask 0x02000000UL
#define loclFeatureMask 0x01000000UL
#define caltFeatureMask 0x00800000UL

#define dligFeatureMask 0x00400000UL
#define rligFeatureMask 0x00200000UL
#define hligFeatureMask 0x00100000UL
#define smcpFeatureMask 0x00080000UL
#define fracFeatureMask 0x00040000UL
#define afrcFeatureMask 0x00020000UL
#define zeroFeatureMask 0x00010000UL
#define swshFeatureMask 0x00008000UL
#define cswhFeatureMask 0x00004000UL
#define saltFeatureMask 0x00002000UL
#define naltFeatureMask 0x00001000UL
#define rubyFeatureMask 0x00000800UL
#define ss01FeatureMask 0x00000400UL
#define ss02FeatureMask 0x00000200UL
#define ss03FeatureMask 0x00000100UL
#define ss04FeatureMask 0x00000080UL
#define ss05FeatureMask 0x00000040UL
#define ss06FeatureMask 0x00000020UL
#define ss07FeatureMask 0x00000010UL

#define minimalFeatures     (ccmpFeatureMask | markFeatureMask | mkmkFeatureMask | loclFeatureMask | caltFeatureMask)

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
    {caltFeatureTag, caltFeatureMask},
    {hligFeatureTag, hligFeatureMask},
    {smcpFeatureTag, smcpFeatureMask},
    {fracFeatureTag, fracFeatureMask},
    {afrcFeatureTag, afrcFeatureMask},
    {zeroFeatureTag, zeroFeatureMask},
    {swshFeatureTag, swshFeatureMask},
    {cswhFeatureTag, cswhFeatureMask},
    {saltFeatureTag, saltFeatureMask},
    {naltFeatureTag, naltFeatureMask},
    {rubyFeatureTag, rubyFeatureMask},
    {ss01FeatureTag, ss01FeatureMask},
    {ss02FeatureTag, ss02FeatureMask},
    {ss03FeatureTag, ss03FeatureMask},
    {ss04FeatureTag, ss04FeatureMask},
    {ss05FeatureTag, ss05FeatureMask},
    {ss06FeatureTag, ss06FeatureMask},
    {ss07FeatureTag, ss07FeatureMask}
};

static const le_int32 featureMapCount = LE_ARRAY_SIZE(featureMap);

OpenTypeLayoutEngine::OpenTypeLayoutEngine(const LEFontInstance *fontInstance, le_int32 scriptCode, le_int32 languageCode,
                     le_int32 typoFlags, const LEReferenceTo<GlyphSubstitutionTableHeader> &gsubTable, LEErrorCode &success)
    : LayoutEngine(fontInstance, scriptCode, languageCode, typoFlags, success), fFeatureMask(minimalFeatures),
      fFeatureMap(featureMap), fFeatureMapCount(featureMapCount), fFeatureOrder(FALSE),
      fGSUBTable(gsubTable),
      fGDEFTable(fontInstance, LE_GDEF_TABLE_TAG, success),
      fGPOSTable(fontInstance, LE_GPOS_TABLE_TAG, success), fSubstitutionFilter(NULL)
{
    applyTypoFlags();

    setScriptAndLanguageTags();

// JK patch, 2008-05-30 - see Sinhala bug report and LKLUG font
//    if (gposTable != NULL && gposTable->coversScriptAndLanguage(fScriptTag, fLangSysTag)) {
    if (!fGPOSTable.isEmpty()&& !fGPOSTable->coversScript(fGPOSTable, fScriptTag, success)) {
      fGPOSTable.clear(); // already loaded
    }
}

void OpenTypeLayoutEngine::applyTypoFlags() {
    const le_int32& typoFlags = fTypoFlags;
    const LEFontInstance *fontInstance = fFontInstance;

    switch (typoFlags & (LE_SS01_FEATURE_FLAG
                         | LE_SS02_FEATURE_FLAG
                         | LE_SS03_FEATURE_FLAG
                         | LE_SS04_FEATURE_FLAG
                         | LE_SS05_FEATURE_FLAG
                         | LE_SS06_FEATURE_FLAG
                         | LE_SS07_FEATURE_FLAG)) {
        case LE_SS01_FEATURE_FLAG:
            fFeatureMask |= ss01FeatureMask;
            break;
        case LE_SS02_FEATURE_FLAG:
            fFeatureMask |= ss02FeatureMask;
            break;
        case LE_SS03_FEATURE_FLAG:
            fFeatureMask |= ss03FeatureMask;
            break;
        case LE_SS04_FEATURE_FLAG:
            fFeatureMask |= ss04FeatureMask;
            break;
        case LE_SS05_FEATURE_FLAG:
            fFeatureMask |= ss05FeatureMask;
            break;
        case LE_SS06_FEATURE_FLAG:
            fFeatureMask |= ss06FeatureMask;
            break;
        case LE_SS07_FEATURE_FLAG:
            fFeatureMask |= ss07FeatureMask;
            break;
    }

    if (typoFlags & LE_Kerning_FEATURE_FLAG) {
      fFeatureMask |= (kernFeatureMask | paltFeatureMask);
      // Convenience.
    }
    if (typoFlags & LE_Ligatures_FEATURE_FLAG) {
      fFeatureMask |= (ligaFeatureMask | cligFeatureMask);
      // Convenience TODO: should add: .. dligFeatureMask | rligFeatureMask ?
    }
    if (typoFlags & LE_CLIG_FEATURE_FLAG) fFeatureMask |= cligFeatureMask;
    if (typoFlags & LE_DLIG_FEATURE_FLAG) fFeatureMask |= dligFeatureMask;
    if (typoFlags & LE_HLIG_FEATURE_FLAG) fFeatureMask |= hligFeatureMask;
    if (typoFlags & LE_LIGA_FEATURE_FLAG) fFeatureMask |= ligaFeatureMask;
    if (typoFlags & LE_RLIG_FEATURE_FLAG) fFeatureMask |= rligFeatureMask;
    if (typoFlags & LE_SMCP_FEATURE_FLAG) fFeatureMask |= smcpFeatureMask;
    if (typoFlags & LE_FRAC_FEATURE_FLAG) fFeatureMask |= fracFeatureMask;
    if (typoFlags & LE_AFRC_FEATURE_FLAG) fFeatureMask |= afrcFeatureMask;
    if (typoFlags & LE_ZERO_FEATURE_FLAG) fFeatureMask |= zeroFeatureMask;
    if (typoFlags & LE_SWSH_FEATURE_FLAG) fFeatureMask |= swshFeatureMask;
    if (typoFlags & LE_CSWH_FEATURE_FLAG) fFeatureMask |= cswhFeatureMask;
    if (typoFlags & LE_SALT_FEATURE_FLAG) fFeatureMask |= saltFeatureMask;
    if (typoFlags & LE_RUBY_FEATURE_FLAG) fFeatureMask |= rubyFeatureMask;
    if (typoFlags & LE_NALT_FEATURE_FLAG) {
      // Mutually exclusive with ALL other features. http://www.microsoft.com/typography/otspec/features_ko.htm
      fFeatureMask = naltFeatureMask;
    }

    if (typoFlags & LE_CHAR_FILTER_FEATURE_FLAG) {
      // This isn't a font feature, but requests a Char Substitution Filter
      fSubstitutionFilter = new CharSubstitutionFilter(fontInstance);
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
      fGSUBTable(), fGDEFTable(), fGPOSTable(), fSubstitutionFilter(NULL)
{
  applyTypoFlags();
  setScriptAndLanguageTags();
}

OpenTypeLayoutEngine::~OpenTypeLayoutEngine()
{
    if (fTypoFlags & LE_CHAR_FILTER_FEATURE_FLAG) {
        delete fSubstitutionFilter;
        fSubstitutionFilter = NULL;
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

    if (fGSUBTable.isValid()) {
      if (fScriptTagV2 != nullScriptTag && fGSUBTable->coversScriptAndLanguage(fGSUBTable, fScriptTagV2, fLangSysTag, success)) {
          count = fGSUBTable->process(fGSUBTable, glyphStorage, rightToLeft, fScriptTagV2, fLangSysTag, fGDEFTable, fSubstitutionFilter,
                                    fFeatureMap, fFeatureMapCount, fFeatureOrder, success);

        } else {
          count = fGSUBTable->process(fGSUBTable, glyphStorage, rightToLeft, fScriptTag, fLangSysTag, fGDEFTable, fSubstitutionFilter,
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

    if (fGSUBTable.isValid()) {
       if (fScriptTagV2 != nullScriptTag && fGSUBTable->coversScriptAndLanguage(fGSUBTable,fScriptTagV2,fLangSysTag,success)) {
          count = fGSUBTable->process(fGSUBTable, glyphStorage, rightToLeft, fScriptTagV2, fLangSysTag, fGDEFTable, fSubstitutionFilter,
                                    fFeatureMap, fFeatureMapCount, fFeatureOrder, success);

        } else {
          count = fGSUBTable->process(fGSUBTable, glyphStorage, rightToLeft, fScriptTag, fLangSysTag, fGDEFTable, fSubstitutionFilter,
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
    le_int32 outCharCount, outGlyphCount;

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
        // le_int32 fakeGlyphCount =
        glyphProcessing(outChars, 0, outCharCount, outCharCount, rightToLeft, fakeGlyphStorage, success);
        LE_DELETE_ARRAY(outChars); // FIXME: a subclass may have allocated this, in which case this delete might not work...
        //adjustGlyphs(outChars, 0, outCharCount, rightToLeft, fakeGlyphs, fakeGlyphCount);
    } else {
        // le_int32 fakeGlyphCount =
        glyphProcessing(chars, offset, count, max, rightToLeft, fakeGlyphStorage, success);
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
    _LETRACE("OTLE::adjustGPOS");
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

    if (!fGPOSTable.isEmpty()) {
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

        if (!fGPOSTable.isEmpty()) {
            if (fScriptTagV2 != nullScriptTag &&
                fGPOSTable->coversScriptAndLanguage(fGPOSTable, fScriptTagV2,fLangSysTag,success)) {
              _LETRACE("OTLE::process [0]");
              fGPOSTable->process(fGPOSTable, glyphStorage, adjustments, reverse, fScriptTagV2, fLangSysTag,
                                  fGDEFTable, success, fFontInstance, fFeatureMap, fFeatureMapCount, fFeatureOrder);

            } else {
              _LETRACE("OTLE::process [1]");
              fGPOSTable->process(fGPOSTable, glyphStorage, adjustments, reverse, fScriptTag, fLangSysTag,
                                  fGDEFTable, success, fFontInstance, fFeatureMap, fFeatureMapCount, fFeatureOrder);
            }
        } else if (fTypoFlags & LE_Kerning_FEATURE_FLAG) { /* kerning enabled */
          _LETRACE("OTLE::kerning");
          LETableReference kernTable(fFontInstance, LE_KERN_TABLE_TAG, success);
          KernTable kt(kernTable, success);
          kt.process(glyphStorage, success);
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
            _LETRACE("OTLE GPOS: #%d, (%.2f,%.2f)", i, xPlacement, yPlacement);
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
