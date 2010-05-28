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
 * (C) Copyright IBM Corp. 2002-2004 - All Rights Reserved
 *
 */

#include "LETypes.h"
#include "LEGlyphStorage.h"
#include "MPreFixups.h"

U_NAMESPACE_BEGIN

struct FixupData
{
    le_int32 fBaseIndex;
    le_int32 fMPreIndex;
};

MPreFixups::MPreFixups(le_int32 charCount)
    : fFixupData(NULL), fFixupCount(0)
{
    fFixupData = LE_NEW_ARRAY(FixupData, charCount);
}

MPreFixups::~MPreFixups()
{
    LE_DELETE_ARRAY(fFixupData);
    fFixupData = NULL;
}

void MPreFixups::add(le_int32 baseIndex, le_int32 mpreIndex)
{
    // NOTE: don't add the fixup data if the mpre is right
    // before the base consonant glyph.
    if (baseIndex - mpreIndex > 1) {
        fFixupData[fFixupCount].fBaseIndex = baseIndex;
        fFixupData[fFixupCount].fMPreIndex = mpreIndex;

        fFixupCount += 1;
    }
}

void MPreFixups::apply(LEGlyphStorage &glyphStorage)
{
    for (le_int32 fixup = 0; fixup < fFixupCount; fixup += 1) {
        le_int32 baseIndex = fFixupData[fixup].fBaseIndex;
        le_int32 mpreIndex = fFixupData[fixup].fMPreIndex;
        le_int32 mpreLimit = mpreIndex + 1;

        while (glyphStorage[baseIndex] == 0xFFFF || glyphStorage[baseIndex] == 0xFFFE) {
            baseIndex -= 1;
        }

        while (glyphStorage[mpreLimit] == 0xFFFF || glyphStorage[mpreLimit] == 0xFFFE) {
            mpreLimit += 1;
        }

        if (mpreLimit == baseIndex) {
            continue;
        }

        LEErrorCode success = LE_NO_ERROR;
        le_int32   mpreCount = mpreLimit - mpreIndex;
        le_int32   moveCount = baseIndex - mpreLimit;
        le_int32   mpreDest  = baseIndex - mpreCount;
        LEGlyphID *mpreSave  = LE_NEW_ARRAY(LEGlyphID, mpreCount);
        le_int32  *indexSave = LE_NEW_ARRAY(le_int32, mpreCount);
        le_int32   i;

        for (i = 0; i < mpreCount; i += 1) {
            mpreSave[i]  = glyphStorage[mpreIndex + i];
            indexSave[i] = glyphStorage.getCharIndex(mpreIndex + i, success); //charIndices[mpreIndex + i];
        }

        for (i = 0; i < moveCount; i += 1) {
            LEGlyphID glyph = glyphStorage[mpreLimit + i];
            le_int32 charIndex = glyphStorage.getCharIndex(mpreLimit + i, success);

            glyphStorage[mpreIndex + i] = glyph;
            glyphStorage.setCharIndex(mpreIndex + i, charIndex, success);
        }

        for (i = 0; i < mpreCount; i += 1) {
            glyphStorage[mpreDest + i] = mpreSave[i];
            glyphStorage.setCharIndex(mpreDest, indexSave[i], success);
        }

        LE_DELETE_ARRAY(indexSave);
        LE_DELETE_ARRAY(mpreSave);
    }
}

U_NAMESPACE_END
