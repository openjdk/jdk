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
#include "LEFontInstance.h"
#include "OpenTypeTables.h"
#include "AnchorTables.h"
#include "MarkArrays.h"
#include "GlyphPositioningTables.h"
#include "AttachmentPosnSubtables.h"
#include "MarkToMarkPosnSubtables.h"
#include "GlyphIterator.h"
#include "LESwaps.h"

U_NAMESPACE_BEGIN

LEGlyphID MarkToMarkPositioningSubtable::findMark2Glyph(GlyphIterator *glyphIterator) const
{
    if (glyphIterator->findMark2Glyph()) {
        return glyphIterator->getCurrGlyphID();
    }

    return 0xFFFF;
}

le_int32 MarkToMarkPositioningSubtable::process(const LETableReference &base, GlyphIterator *glyphIterator, const LEFontInstance *fontInstance, LEErrorCode &success) const
{
    LEGlyphID markGlyph = glyphIterator->getCurrGlyphID();
    le_int32 markCoverage = getGlyphCoverage(base, (LEGlyphID) markGlyph, success);

    if (LE_FAILURE(success)) {
        return 0;
    }

    if (markCoverage < 0) {
        // markGlyph isn't a covered mark glyph
        return 0;
    }

    LEPoint markAnchor;
    LEReferenceTo<MarkArray> markArray(base, success, SWAPW(markArrayOffset));
    if(LE_FAILURE(success)) {
      return 0;
    }
    le_int32 markClass = markArray->getMarkClass(markArray, markGlyph, markCoverage, fontInstance, markAnchor, success);
    le_uint16 mcCount = SWAPW(classCount);

    if (markClass < 0 || markClass >= mcCount) {
        // markGlyph isn't in the mark array or its
        // mark class is too big. The table is mal-formed!
        return 0;
    }

    GlyphIterator mark2Iterator(*glyphIterator);
    LEGlyphID mark2Glyph = findMark2Glyph(&mark2Iterator);
    le_int32 mark2Coverage = getBaseCoverage(base, (LEGlyphID) mark2Glyph, success);
    LEReferenceTo<Mark2Array>  mark2Array(base, success, (const Mark2Array *) ((char *) this + SWAPW(baseArrayOffset)));
    if(LE_FAILURE(success)) return 0;
    le_uint16 mark2Count = SWAPW(mark2Array->mark2RecordCount);

    if (mark2Coverage < 0 || mark2Coverage >= mark2Count) {
        // The mark2 glyph isn't covered, or the coverage
        // index is too big. The latter means that the
        // table is mal-formed...
        return 0;
    }

    LEReferenceTo<Mark2Record> mark2Record(base, success, &mark2Array->mark2RecordArray[mark2Coverage * mcCount]);
    if(LE_FAILURE(success)) return 0;
    Offset anchorTableOffset = SWAPW(mark2Record->mark2AnchorTableOffsetArray[markClass]);
    LEReferenceTo<AnchorTable> anchorTable(mark2Array, success, anchorTableOffset);
    if(LE_FAILURE(success)) return 0;
    LEPoint mark2Anchor, markAdvance, pixels;

    if (anchorTableOffset == 0) {
        // this seems to mean that the marks don't attach...
        return 0;
    }

    anchorTable->getAnchor(anchorTable, mark2Glyph, fontInstance, mark2Anchor, success);

    fontInstance->getGlyphAdvance(markGlyph, pixels);
    fontInstance->pixelsToUnits(pixels, markAdvance);

    float anchorDiffX = mark2Anchor.fX - markAnchor.fX;
    float anchorDiffY = mark2Anchor.fY - markAnchor.fY;

    _LETRACE("Offset: (%.2f, %.2f) glyph 0x%X mark2 0x%X", anchorDiffX, anchorDiffY, markGlyph, mark2Glyph);

    glyphIterator->setCurrGlyphBaseOffset(mark2Iterator.getCurrStreamPosition());

    if (glyphIterator->isRightToLeft()) {
        glyphIterator->setCurrGlyphPositionAdjustment(anchorDiffX, anchorDiffY, -markAdvance.fX, -markAdvance.fY);
    } else {
        LEPoint mark2Advance;

        fontInstance->getGlyphAdvance(mark2Glyph, pixels);
        fontInstance->pixelsToUnits(pixels, mark2Advance);

        glyphIterator->setCurrGlyphPositionAdjustment(anchorDiffX - mark2Advance.fX, anchorDiffY - mark2Advance.fY, -markAdvance.fX, -markAdvance.fY);
    }

    return 1;
}

U_NAMESPACE_END
