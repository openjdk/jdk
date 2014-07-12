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
 * (C) Copyright IBM Corp. 1998-2009 - All Rights Reserved
 *
 */

#include "LETypes.h"
#include "LayoutEngine.h"
#include "OpenTypeLayoutEngine.h"
#include "IndicLayoutEngine.h"
#include "ScriptAndLanguageTags.h"

#include "GlyphSubstitutionTables.h"
#include "GlyphDefinitionTables.h"
#include "GlyphPositioningTables.h"

#include "GDEFMarkFilter.h"
#include "LEGlyphStorage.h"

#include "IndicReordering.h"

U_NAMESPACE_BEGIN

UOBJECT_DEFINE_RTTI_IMPLEMENTATION(IndicOpenTypeLayoutEngine)

IndicOpenTypeLayoutEngine::IndicOpenTypeLayoutEngine(const LEFontInstance *fontInstance, le_int32 scriptCode, le_int32 languageCode,
                                                     le_int32 typoFlags, le_bool version2, const LEReferenceTo<GlyphSubstitutionTableHeader> &gsubTable, LEErrorCode &success)
    : OpenTypeLayoutEngine(fontInstance, scriptCode, languageCode, typoFlags, gsubTable, success), fMPreFixups(NULL)
{
  if ( version2 ) {
    fFeatureMap = IndicReordering::getv2FeatureMap(fFeatureMapCount);
  } else {
    fFeatureMap = IndicReordering::getFeatureMap(fFeatureMapCount);
  }
  fFeatureOrder = TRUE;
  fVersion2 = version2;
  fFilterZeroWidth = IndicReordering::getFilterZeroWidth(fScriptCode);
}

IndicOpenTypeLayoutEngine::IndicOpenTypeLayoutEngine(const LEFontInstance *fontInstance, le_int32 scriptCode, le_int32 languageCode, le_int32 typoFlags, LEErrorCode &success)
    : OpenTypeLayoutEngine(fontInstance, scriptCode, languageCode, typoFlags, success), fMPreFixups(NULL)
{
    fFeatureMap = IndicReordering::getFeatureMap(fFeatureMapCount);
    fFeatureOrder = TRUE;
    fVersion2 =  FALSE;
}

IndicOpenTypeLayoutEngine::~IndicOpenTypeLayoutEngine()
{
    // nothing to do
}

// Input: characters, tags
// Output: glyphs, char indices
le_int32 IndicOpenTypeLayoutEngine::glyphProcessing(const LEUnicode chars[], le_int32 offset, le_int32 count, le_int32 max, le_bool rightToLeft,
                    LEGlyphStorage &glyphStorage, LEErrorCode &success)
{
    if (LE_FAILURE(success)) {
        return 0;
    }

    if (chars == NULL || offset < 0 || count < 0 || max < 0 || offset >= max || offset + count > max) {
        success = LE_ILLEGAL_ARGUMENT_ERROR;
        return 0;
    }

    _LETRACE("IOTLE::gp, calling parent");
    le_int32 retCount = OpenTypeLayoutEngine::glyphProcessing(chars, offset, count, max, rightToLeft, glyphStorage, success);

    if (LE_FAILURE(success)) {
        return 0;
    }

    if (fVersion2) {
      _LETRACE("IOTLE::gp, v2 final,");
      IndicReordering::finalReordering(glyphStorage,retCount);
      _LETRACE("IOTLE::gp, v2 pres");
      IndicReordering::applyPresentationForms(glyphStorage,retCount);
      _LETRACE("IOTLE::gp, parent gsub");
      OpenTypeLayoutEngine::glyphSubstitution(count,max, rightToLeft, glyphStorage, success);
    } else {
      _LETRACE("IOTLE::gp, adjust mpres");
      IndicReordering::adjustMPres(fMPreFixups, glyphStorage, success);
    }
    return retCount;
}

// Input: characters
// Output: characters, char indices, tags
// Returns: output character count
le_int32 IndicOpenTypeLayoutEngine::characterProcessing(const LEUnicode chars[], le_int32 offset, le_int32 count, le_int32 max, le_bool rightToLeft,
        LEUnicode *&outChars, LEGlyphStorage &glyphStorage, LEErrorCode &success)
{
    if (LE_FAILURE(success)) {
        return 0;
    }

    _LETRACE("IOTLE: charProc");

    if (chars == NULL || offset < 0 || count < 0 || max < 0 || offset >= max || offset + count > max) {
        success = LE_ILLEGAL_ARGUMENT_ERROR;
        return 0;
    }

    le_int32 worstCase = count * IndicReordering::getWorstCaseExpansion(fScriptCode);

    outChars = LE_NEW_ARRAY(LEUnicode, worstCase);

    if (outChars == NULL) {
        success = LE_MEMORY_ALLOCATION_ERROR;
        return 0;
    }

    glyphStorage.allocateGlyphArray(worstCase, rightToLeft, success);
    glyphStorage.allocateAuxData(success);

    if (LE_FAILURE(success)) {
        LE_DELETE_ARRAY(outChars);
        return 0;
    }

    // NOTE: assumes this allocates featureTags...
    // (probably better than doing the worst case stuff here...)

    le_int32 outCharCount;
    if (fVersion2) {
        _LETRACE("v2process");
        outCharCount = IndicReordering::v2process(&chars[offset], count, fScriptCode, outChars, glyphStorage, success);
    } else {
        _LETRACE("reorder");
        outCharCount = IndicReordering::reorder(&chars[offset], count, fScriptCode, outChars, glyphStorage, &fMPreFixups, success);
    }

    if (LE_FAILURE(success)) {
        LE_DELETE_ARRAY(outChars);
        return 0;
    }

    glyphStorage.adoptGlyphCount(outCharCount);
    return outCharCount;
}

U_NAMESPACE_END
