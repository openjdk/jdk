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
 * HanLayoutEngine.cpp: OpenType processing for Han fonts.
 *
 * (C) Copyright IBM Corp. 1998-2005 - All Rights Reserved.
 */

#include "LETypes.h"
#include "LEScripts.h"
#include "LELanguages.h"

#include "LayoutEngine.h"
#include "OpenTypeLayoutEngine.h"
#include "HanLayoutEngine.h"
#include "ScriptAndLanguageTags.h"
#include "LEGlyphStorage.h"
#include "OpenTypeTables.h"

U_NAMESPACE_BEGIN

UOBJECT_DEFINE_RTTI_IMPLEMENTATION(HanOpenTypeLayoutEngine)

#define loclFeatureTag LE_LOCL_FEATURE_TAG
#define smplFeatureTag LE_SMPL_FEATURE_TAG
#define tradFeatureTag LE_TRAD_FEATURE_TAG

#define loclFeatureMask 0x80000000UL
#define smplFeatureMask 0x40000000UL
#define tradFeatureMask 0x20000000UL

static const FeatureMap featureMap[] =
{
    {loclFeatureTag, loclFeatureMask},
    {smplFeatureTag, smplFeatureMask},
    {tradFeatureTag, tradFeatureMask}
};

static const le_int32 featureMapCount = LE_ARRAY_SIZE(featureMap);

#define features (loclFeatureMask)

HanOpenTypeLayoutEngine::HanOpenTypeLayoutEngine(const LEFontInstance *fontInstance, le_int32 scriptCode, le_int32 languageCode,
                        le_int32 typoFlags, const GlyphSubstitutionTableHeader *gsubTable)
    : OpenTypeLayoutEngine(fontInstance, scriptCode, languageCode, typoFlags, gsubTable)
{
    fFeatureMap      = featureMap;
    fFeatureMapCount = featureMapCount;
}

HanOpenTypeLayoutEngine::~HanOpenTypeLayoutEngine()
{
    // nothing to do
}

le_int32 HanOpenTypeLayoutEngine::characterProcessing(const LEUnicode chars[], le_int32 offset, le_int32 count, le_int32 max, le_bool /*rightToLeft*/,
        LEUnicode *&/*outChars*/, LEGlyphStorage &glyphStorage, LEErrorCode &success)
{
    if (LE_FAILURE(success)) {
        return 0;
    }

    if (chars == NULL || offset < 0 || count < 0 || max < 0 || offset >= max || offset + count > max) {
        success = LE_ILLEGAL_ARGUMENT_ERROR;
        return 0;
    }

    glyphStorage.allocateGlyphArray(count, FALSE, success);
    glyphStorage.allocateAuxData(success);

    if (LE_FAILURE(success)) {
        return 0;
    }

    // FIXME: do we want to add the 'trad' feature for 'ZHT' and the
    // 'smpl' feature for 'ZHS'? If we do this, we can remove the exact
    // flag from the language tag lookups, so we can use these features
    // with the default LangSys...
    for (le_int32 i = 0; i < count; i += 1) {
        glyphStorage.setAuxData(i, features, success);
    }

    return count;
}

U_NAMESPACE_END
