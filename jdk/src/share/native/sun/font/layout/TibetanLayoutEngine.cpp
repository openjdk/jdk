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
 * Developed at DIT - Government of Bhutan
 *
 * Contact person: Pema Geyleg - <pema_geyleg@druknet.bt>
 *
 * This file is a modification of the ICU file KhmerReordering.cpp
 * by Jens Herden and Javier Sola who have given all their possible rights to IBM and the Governement of Bhutan
 * A first module for Dzongkha was developed by Karunakar under Panlocalisation funding.
 * Assistance for this module has been received from Namgay Thinley, Christopher Fynn and Javier Sola
 *
 */


#include "OpenTypeLayoutEngine.h"
#include "TibetanLayoutEngine.h"
#include "LEGlyphStorage.h"
#include "TibetanReordering.h"

U_NAMESPACE_BEGIN

UOBJECT_DEFINE_RTTI_IMPLEMENTATION(TibetanOpenTypeLayoutEngine)

TibetanOpenTypeLayoutEngine::TibetanOpenTypeLayoutEngine(const LEFontInstance *fontInstance, le_int32 scriptCode, le_int32 languageCode,
                    le_int32 typoFlags, const GlyphSubstitutionTableHeader *gsubTable, LEErrorCode &success)
    : OpenTypeLayoutEngine(fontInstance, scriptCode, languageCode, typoFlags, gsubTable, success)
{
    fFeatureMap   = TibetanReordering::getFeatureMap(fFeatureMapCount);
    fFeatureOrder = TRUE;
}

TibetanOpenTypeLayoutEngine::TibetanOpenTypeLayoutEngine(const LEFontInstance *fontInstance, le_int32 scriptCode, le_int32 languageCode,
                                                     le_int32 typoFlags, LEErrorCode &success)
    : OpenTypeLayoutEngine(fontInstance, scriptCode, languageCode, typoFlags, success)
{
    fFeatureMap   = TibetanReordering::getFeatureMap(fFeatureMapCount);
    fFeatureOrder = TRUE;
}

TibetanOpenTypeLayoutEngine::~TibetanOpenTypeLayoutEngine()
{
    // nothing to do
}

// Input: characters
// Output: characters, char indices, tags
// Returns: output character count
le_int32 TibetanOpenTypeLayoutEngine::characterProcessing(const LEUnicode chars[], le_int32 offset, le_int32 count, le_int32 max, le_bool rightToLeft,
        LEUnicode *&outChars, LEGlyphStorage &glyphStorage, LEErrorCode &success)
{
    if (LE_FAILURE(success)) {
        return 0;
    }

    if (chars == NULL || offset < 0 || count < 0 || max < 0 || offset >= max || offset + count > max) {
        success = LE_ILLEGAL_ARGUMENT_ERROR;
        return 0;
    }

    le_int32 worstCase = count * 3;  // worst case is 3 for Khmer  TODO check if 2 is enough

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
    le_int32 outCharCount = TibetanReordering::reorder(&chars[offset], count, fScriptCode, outChars, glyphStorage);

    glyphStorage.adoptGlyphCount(outCharCount);
    return outCharCount;
}

U_NAMESPACE_END
