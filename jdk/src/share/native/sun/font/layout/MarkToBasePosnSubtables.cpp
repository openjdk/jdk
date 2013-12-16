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
#include "LEFontInstance.h"
#include "OpenTypeTables.h"
#include "AnchorTables.h"
#include "MarkArrays.h"
#include "GlyphPositioningTables.h"
#include "AttachmentPosnSubtables.h"
#include "MarkToBasePosnSubtables.h"
#include "GlyphIterator.h"
#include "LESwaps.h"

U_NAMESPACE_BEGIN

LEGlyphID MarkToBasePositioningSubtable::findBaseGlyph(GlyphIterator *glyphIterator) const
{
    if (glyphIterator->prev()) {
        return glyphIterator->getCurrGlyphID();
    }

    return 0xFFFF;
}

le_int32 MarkToBasePositioningSubtable::process(const LETableReference &base, GlyphIterator *glyphIterator, const LEFontInstance *fontInstance, LEErrorCode &success) const
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
    LEReferenceTo<MarkArray> markArray(base, success,  (const MarkArray *) ((char *) this + SWAPW(markArrayOffset)));
    if(LE_FAILURE(success)) return 0;
    le_int32 markClass = markArray->getMarkClass(markArray, markGlyph, markCoverage, fontInstance, markAnchor, success);
    le_uint16 mcCount = SWAPW(classCount);

    if (markClass < 0 || markClass >= mcCount || LE_FAILURE(success)) {
        // markGlyph isn't in the mark array or its
        // mark class is too big. The table is mal-formed!
        return 0;
    }

    // FIXME: We probably don't want to find a base glyph before a previous ligature...
    GlyphIterator baseIterator(*glyphIterator, (le_uint16) (lfIgnoreMarks /*| lfIgnoreLigatures*/));
    LEGlyphID baseGlyph = findBaseGlyph(&baseIterator);
    le_int32 baseCoverage = getBaseCoverage(base, (LEGlyphID) baseGlyph, success);
    LEReferenceTo<BaseArray> baseArray(base, success, (const BaseArray *) ((char *) this + SWAPW(baseArrayOffset)));
    if(LE_FAILURE(success)) return 0;
    le_uint16 baseCount = SWAPW(baseArray->baseRecordCount);

    if (baseCoverage < 0 || baseCoverage >= baseCount) {
        // The base glyph isn't covered, or the coverage
        // index is too big. The latter means that the
        // table is mal-formed...
        return 0;
    }
    LEReferenceTo<BaseRecord> baseRecord(base, success, &baseArray->baseRecordArray[baseCoverage * mcCount]);
    if( LE_FAILURE(success) ) { return 0; }
    LEReferenceToArrayOf<Offset> baseAnchorTableOffsetArray(base, success, &(baseRecord->baseAnchorTableOffsetArray[0]), markClass+1);

    if( LE_FAILURE(success) ) { return 0; }
    Offset anchorTableOffset = SWAPW(baseRecord->baseAnchorTableOffsetArray[markClass]);
    if (anchorTableOffset <= 0) {
        // this means the table is mal-formed...
        glyphIterator->setCurrGlyphBaseOffset(baseIterator.getCurrStreamPosition());
        return 0;
    }

    LEReferenceTo<AnchorTable> anchorTable(baseArray, success, anchorTableOffset);
    LEPoint baseAnchor, markAdvance, pixels;


    anchorTable->getAnchor(anchorTable, baseGlyph, fontInstance, baseAnchor, success);

    fontInstance->getGlyphAdvance(markGlyph, pixels);
    fontInstance->pixelsToUnits(pixels, markAdvance);

    float anchorDiffX = baseAnchor.fX - markAnchor.fX;
    float anchorDiffY = baseAnchor.fY - markAnchor.fY;

    _LETRACE("Offset: (%.2f, %.2f) glyph 0x%X", anchorDiffX, anchorDiffY, markGlyph);

    glyphIterator->setCurrGlyphBaseOffset(baseIterator.getCurrStreamPosition());

    if (glyphIterator->isRightToLeft()) {
        // FIXME: need similar patch to below; also in MarkToLigature and MarkToMark
        // (is there a better way to approach this for all the cases?)
        glyphIterator->setCurrGlyphPositionAdjustment(anchorDiffX, anchorDiffY, -markAdvance.fX, -markAdvance.fY);
    } else {
        LEPoint baseAdvance;

        fontInstance->getGlyphAdvance(baseGlyph, pixels);

        //JK: adjustment needs to account for non-zero advance of any marks between base glyph and current mark
        GlyphIterator gi(baseIterator, (le_uint16)0); // copy of baseIterator that won't ignore marks
        gi.next(); // point beyond the base glyph
        while (gi.getCurrStreamPosition() < glyphIterator->getCurrStreamPosition()) { // for all intervening glyphs (marks)...
            LEGlyphID otherMark = gi.getCurrGlyphID();
            LEPoint px;
            fontInstance->getGlyphAdvance(otherMark, px); // get advance, in case it's non-zero
            pixels.fX += px.fX; // and add that to the base glyph's advance
            pixels.fY += px.fY;
            gi.next();
        }
        // end of JK patch
        fontInstance->pixelsToUnits(pixels, baseAdvance);

        glyphIterator->setCurrGlyphPositionAdjustment(anchorDiffX - baseAdvance.fX, anchorDiffY - baseAdvance.fY, -markAdvance.fX, -markAdvance.fY);
    }

    return 1;
}

U_NAMESPACE_END
