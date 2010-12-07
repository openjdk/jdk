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
#include "LEScripts.h"
#include "LEGlyphFilter.h"
#include "LEGlyphStorage.h"
#include "LayoutEngine.h"
#include "OpenTypeLayoutEngine.h"
#include "ArabicLayoutEngine.h"
#include "ScriptAndLanguageTags.h"
#include "CharSubstitutionFilter.h"

#include "GlyphSubstitutionTables.h"
#include "GlyphDefinitionTables.h"
#include "GlyphPositioningTables.h"

#include "GDEFMarkFilter.h"

#include "ArabicShaping.h"
#include "CanonShaping.h"

U_NAMESPACE_BEGIN

le_bool CharSubstitutionFilter::accept(LEGlyphID glyph) const
{
    return fFontInstance->canDisplay((LEUnicode) glyph);
}

UOBJECT_DEFINE_RTTI_IMPLEMENTATION(ArabicOpenTypeLayoutEngine)

ArabicOpenTypeLayoutEngine::ArabicOpenTypeLayoutEngine(const LEFontInstance *fontInstance, le_int32 scriptCode, le_int32 languageCode,
                        le_int32 typoFlags, const GlyphSubstitutionTableHeader *gsubTable, LEErrorCode &success)
    : OpenTypeLayoutEngine(fontInstance, scriptCode, languageCode, typoFlags, gsubTable, success)
{
    fFeatureMap = ArabicShaping::getFeatureMap(fFeatureMapCount);
    fFeatureOrder = TRUE;
}

ArabicOpenTypeLayoutEngine::ArabicOpenTypeLayoutEngine(const LEFontInstance *fontInstance, le_int32 scriptCode, le_int32 languageCode,
                                                       le_int32 typoFlags, LEErrorCode &success)
    : OpenTypeLayoutEngine(fontInstance, scriptCode, languageCode, typoFlags, success)
{
    fFeatureMap = ArabicShaping::getFeatureMap(fFeatureMapCount);

    // NOTE: We don't need to set fFeatureOrder to TRUE here
    // because this constructor is only called by the constructor
    // for UnicodeArabicOpenTypeLayoutEngine, which uses a pre-built
    // GSUB table that has the features in the correct order.

    //fFeatureOrder = TRUE;
}

ArabicOpenTypeLayoutEngine::~ArabicOpenTypeLayoutEngine()
{
    // nothing to do
}

// Input: characters
// Output: characters, char indices, tags
// Returns: output character count
le_int32 ArabicOpenTypeLayoutEngine::characterProcessing(const LEUnicode chars[], le_int32 offset, le_int32 count, le_int32 max, le_bool rightToLeft,
        LEUnicode *&outChars, LEGlyphStorage &glyphStorage, LEErrorCode &success)
{
    if (LE_FAILURE(success)) {
        return 0;
    }

    if (chars == NULL || offset < 0 || count < 0 || max < 0 || offset >= max || offset + count > max) {
        success = LE_ILLEGAL_ARGUMENT_ERROR;
        return 0;
    }

    outChars = LE_NEW_ARRAY(LEUnicode, count);

    if (outChars == NULL) {
        success = LE_MEMORY_ALLOCATION_ERROR;
        return 0;
    }

    glyphStorage.allocateGlyphArray(count, rightToLeft, success);
    glyphStorage.allocateAuxData(success);

    if (LE_FAILURE(success)) {
        LE_DELETE_ARRAY(outChars);
        return 0;
    }

    CanonShaping::reorderMarks(&chars[offset], count, rightToLeft, outChars, glyphStorage);

    // Note: This processes the *original* character array so we can get context
    // for the first and last characters. This is OK because only the marks
    // will have been reordered, and they don't contribute to shaping.
    ArabicShaping::shape(chars, offset, count, max, rightToLeft, glyphStorage);

    return count;
}

void ArabicOpenTypeLayoutEngine::adjustGlyphPositions(const LEUnicode chars[], le_int32 offset, le_int32 count, le_bool reverse,
                                                      LEGlyphStorage &glyphStorage, LEErrorCode &success)
{
    if (LE_FAILURE(success)) {
        return;
    }

    if (chars == NULL || offset < 0 || count < 0) {
        success = LE_ILLEGAL_ARGUMENT_ERROR;
        return;
    }

    if (fGPOSTable != NULL) {
        OpenTypeLayoutEngine::adjustGlyphPositions(chars, offset, count, reverse, glyphStorage, success);
    } else if (fGDEFTable != NULL) {
        GDEFMarkFilter filter(fGDEFTable);

        adjustMarkGlyphs(glyphStorage, &filter, success);
    } else {
        GlyphDefinitionTableHeader *gdefTable = (GlyphDefinitionTableHeader *) CanonShaping::glyphDefinitionTable;
        GDEFMarkFilter filter(gdefTable);

        adjustMarkGlyphs(&chars[offset], count, reverse, glyphStorage, &filter, success);
    }
}

UnicodeArabicOpenTypeLayoutEngine::UnicodeArabicOpenTypeLayoutEngine(const LEFontInstance *fontInstance, le_int32 scriptCode, le_int32 languageCode, le_int32 typoFlags, LEErrorCode &success)
    : ArabicOpenTypeLayoutEngine(fontInstance, scriptCode, languageCode, typoFlags, success)
{
    fGSUBTable = (const GlyphSubstitutionTableHeader *) CanonShaping::glyphSubstitutionTable;
    fGDEFTable = (const GlyphDefinitionTableHeader *) CanonShaping::glyphDefinitionTable;

    fSubstitutionFilter = new CharSubstitutionFilter(fontInstance);
}

UnicodeArabicOpenTypeLayoutEngine::~UnicodeArabicOpenTypeLayoutEngine()
{
    delete fSubstitutionFilter;
}

// "glyphs", "indices" -> glyphs, indices
le_int32 UnicodeArabicOpenTypeLayoutEngine::glyphPostProcessing(LEGlyphStorage &tempGlyphStorage, LEGlyphStorage &glyphStorage, LEErrorCode &success)
{
    if (LE_FAILURE(success)) {
        return 0;
    }

    // FIXME: we could avoid the memory allocation and copy if we
    // made a clone of mapCharsToGlyphs which took the fake glyphs
    // directly.
    le_int32 tempGlyphCount = tempGlyphStorage.getGlyphCount();
    LEUnicode *tempChars = LE_NEW_ARRAY(LEUnicode, tempGlyphCount);

    if (tempChars == NULL) {
        success = LE_MEMORY_ALLOCATION_ERROR;
        return 0;
    }

    for (le_int32 i = 0; i < tempGlyphCount; i += 1) {
        tempChars[i] = (LEUnicode) LE_GET_GLYPH(tempGlyphStorage[i]);
    }

    glyphStorage.adoptCharIndicesArray(tempGlyphStorage);

    ArabicOpenTypeLayoutEngine::mapCharsToGlyphs(tempChars, 0, tempGlyphCount, FALSE, TRUE, glyphStorage, success);

    LE_DELETE_ARRAY(tempChars);

    return tempGlyphCount;
}

void UnicodeArabicOpenTypeLayoutEngine::mapCharsToGlyphs(const LEUnicode chars[], le_int32 offset, le_int32 count, le_bool reverse, le_bool /*mirror*/, LEGlyphStorage &glyphStorage, LEErrorCode &success)
{
    if (LE_FAILURE(success)) {
        return;
    }

    if (chars == NULL || offset < 0 || count < 0) {
        success = LE_ILLEGAL_ARGUMENT_ERROR;
        return;
    }

    le_int32 i, dir = 1, out = 0;

    if (reverse) {
        out = count - 1;
        dir = -1;
    }

    glyphStorage.allocateGlyphArray(count, reverse, success);

    for (i = 0; i < count; i += 1, out += dir) {
        glyphStorage[out] = (LEGlyphID) chars[offset + i];
    }
}

void UnicodeArabicOpenTypeLayoutEngine::adjustGlyphPositions(const LEUnicode chars[], le_int32 offset, le_int32 count, le_bool reverse,
                                                      LEGlyphStorage &glyphStorage, LEErrorCode &success)
{
    if (LE_FAILURE(success)) {
        return;
    }

    if (chars == NULL || offset < 0 || count < 0) {
        success = LE_ILLEGAL_ARGUMENT_ERROR;
        return;
    }

    GDEFMarkFilter filter(fGDEFTable);

    adjustMarkGlyphs(&chars[offset], count, reverse, glyphStorage, &filter, success);
}

U_NAMESPACE_END

