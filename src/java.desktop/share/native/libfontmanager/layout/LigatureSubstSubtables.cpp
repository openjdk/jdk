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
 *
 * (C) Copyright IBM Corp. 1998-2006 - All Rights Reserved
 *
 */

#include "LETypes.h"
#include "LEGlyphFilter.h"
#include "OpenTypeTables.h"
#include "GlyphSubstitutionTables.h"
#include "LigatureSubstSubtables.h"
#include "GlyphIterator.h"
#include "LESwaps.h"

U_NAMESPACE_BEGIN

le_uint32 LigatureSubstitutionSubtable::process(const LETableReference &base, GlyphIterator *glyphIterator, LEErrorCode &success, const LEGlyphFilter *filter) const
{
    LEGlyphID glyph = glyphIterator->getCurrGlyphID();
    le_int32 coverageIndex = getGlyphCoverage(base, glyph, success);

    if (LE_FAILURE(success)) {
      return 0;
    }

    LEReferenceToArrayOf<Offset> ligSetTableOffsetArrayRef(base, success, ligSetTableOffsetArray, SWAPW(ligSetCount));

    if (coverageIndex >= 0 && LE_SUCCESS(success) && (le_uint32)coverageIndex < ligSetTableOffsetArrayRef.getCount()) {
        Offset ligSetTableOffset = SWAPW(ligSetTableOffsetArray[coverageIndex]);
        LEReferenceTo<LigatureSetTable>   ligSetTable(base, success, ligSetTableOffset);

        if( LE_FAILURE(success) ) { return 0; }
        le_uint16 ligCount = SWAPW(ligSetTable->ligatureCount);

        LEReferenceToArrayOf<Offset> ligatureTableOffsetArray(base, success, ligSetTable->ligatureTableOffsetArray, ligCount);
        for (le_uint16 lig = 0; LE_SUCCESS(success) && lig < ligCount; lig += 1) {
            Offset ligTableOffset = SWAPW(ligSetTable->ligatureTableOffsetArray[lig]);
            LEReferenceTo<LigatureTable>   ligTable(ligSetTable, success, ligTableOffset);
            if(LE_FAILURE(success)) { return 0; }
            le_uint16 compCount = SWAPW(ligTable->compCount) - 1;
            LEReferenceToArrayOf<TTGlyphID>
                componentArrayRef(base, success, ligTable->componentArray, compCount);
            if (LE_FAILURE(success)) { return 0; }
            le_int32 startPosition = glyphIterator->getCurrStreamPosition();
            TTGlyphID ligGlyph = SWAPW(ligTable->ligGlyph);
            le_uint16 comp;

            for (comp = 0; comp < compCount; comp += 1) {
                if (! glyphIterator->next()) {
                    break;
                }

                if (LE_GET_GLYPH(glyphIterator->getCurrGlyphID()) != SWAPW(ligTable->componentArray[comp])) {
                    break;
                }
            }

            if (comp == compCount && (filter == NULL || filter->accept(LE_SET_GLYPH(glyph, ligGlyph), success))) {
                GlyphIterator tempIterator(*glyphIterator);
                TTGlyphID deletedGlyph = tempIterator.ignoresMarks()? 0xFFFE : 0xFFFF;

                while (comp > 0) {
                    tempIterator.setCurrGlyphID(deletedGlyph);
                    tempIterator.prev();

                    comp -= 1;
                }

                tempIterator.setCurrGlyphID(ligGlyph);

                return compCount + 1;
            }

            glyphIterator->setCurrStreamPosition(startPosition);
        }

    }

    return 0;
}

U_NAMESPACE_END
