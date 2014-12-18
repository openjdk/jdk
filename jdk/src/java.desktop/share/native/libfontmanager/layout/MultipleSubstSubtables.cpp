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
 * (C) Copyright IBM Corp. 1998-2008 - All Rights Reserved
 *
 */

#include "LETypes.h"
#include "LEGlyphFilter.h"
#include "OpenTypeTables.h"
#include "GlyphSubstitutionTables.h"
#include "MultipleSubstSubtables.h"
#include "GlyphIterator.h"
#include "LESwaps.h"

U_NAMESPACE_BEGIN

le_uint32 MultipleSubstitutionSubtable::process(const LETableReference &base, GlyphIterator *glyphIterator, LEErrorCode& success, const LEGlyphFilter *filter) const
{
    if (LE_FAILURE(success)) {
        return 0;
    }

    LEGlyphID glyph = glyphIterator->getCurrGlyphID();

    // If there's a filter, we only want to do the
    // substitution if the *input* glyphs doesn't
    // exist.
    //
    // FIXME: is this always the right thing to do?
    // FIXME: should this only be done for a non-zero
    //        glyphCount?
    if (filter != NULL && filter->accept(glyph, success)) {
        return 0;
    }
    if(LE_FAILURE(success)) return 0;

    le_int32 coverageIndex = getGlyphCoverage(base, glyph, success);
    le_uint16 seqCount = SWAPW(sequenceCount);
    LEReferenceToArrayOf<Offset>
        sequenceTableOffsetArrayRef(base, success, sequenceTableOffsetArray, seqCount);

    if (LE_FAILURE(success)) {
        return 0;
    }

    if (coverageIndex >= 0 && coverageIndex < seqCount) {
        Offset sequenceTableOffset = SWAPW(sequenceTableOffsetArray[coverageIndex]);
        LEReferenceTo<SequenceTable>   sequenceTable(base, success, sequenceTableOffset);
        if (LE_FAILURE(success)) {
            return 0;
        }
        le_uint16 glyphCount = SWAPW(sequenceTable->glyphCount);

        if (glyphCount == 0) {
            glyphIterator->setCurrGlyphID(0xFFFF);
            return 1;
        } else if (glyphCount == 1) {
            TTGlyphID substitute = SWAPW(sequenceTable->substituteArray[0]);

            if (filter != NULL && ! filter->accept(LE_SET_GLYPH(glyph, substitute), success)) {
                return 0;
            }

            glyphIterator->setCurrGlyphID(substitute);
            return 1;
        } else {
            // If there's a filter, make sure all of the output glyphs
            // exist.
            if (filter != NULL) {
                for (le_int32 i = 0; i < glyphCount; i += 1) {
                    TTGlyphID substitute = SWAPW(sequenceTable->substituteArray[i]);

                    if (! filter->accept(substitute, success)) {
                        return 0;
                    }
                }
            }

            LEGlyphID *newGlyphs = glyphIterator->insertGlyphs(glyphCount, success);
            if (LE_FAILURE(success)) {
                return 0;
            }

            le_int32 insert = 0, direction = 1;

            if (glyphIterator->isRightToLeft()) {
                insert = glyphCount - 1;
                direction = -1;
            }

            for (le_int32 i = 0; i < glyphCount; i += 1) {
                TTGlyphID substitute = SWAPW(sequenceTable->substituteArray[i]);

                newGlyphs[insert] = LE_SET_GLYPH(glyph, substitute);
                insert += direction;
            }

            return 1;
        }
    }

    return 0;
}

U_NAMESPACE_END
