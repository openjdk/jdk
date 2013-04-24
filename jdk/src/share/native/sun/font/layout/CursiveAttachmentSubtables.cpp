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
 * (C) Copyright IBM Corp. 1998 - 2005 - All Rights Reserved
 *
 */

#include "LETypes.h"
#include "OpenTypeTables.h"
#include "GlyphPositioningTables.h"
#include "CursiveAttachmentSubtables.h"
#include "AnchorTables.h"
#include "GlyphIterator.h"
#include "OpenTypeUtilities.h"
#include "LESwaps.h"

U_NAMESPACE_BEGIN

le_uint32 CursiveAttachmentSubtable::process(const LEReferenceTo<CursiveAttachmentSubtable> &base, GlyphIterator *glyphIterator, const LEFontInstance *fontInstance, LEErrorCode &success) const
{
    LEGlyphID glyphID       = glyphIterator->getCurrGlyphID();
    le_int32  coverageIndex = getGlyphCoverage(base, glyphID, success);
    le_uint16 eeCount       = SWAPW(entryExitCount);

    if (coverageIndex < 0 || coverageIndex >= eeCount) {
        glyphIterator->setCursiveGlyph();
        return 0;
    }

    LEPoint entryAnchor, exitAnchor;
    Offset entryOffset = SWAPW(entryExitRecords[coverageIndex].entryAnchor); // TODO
    Offset exitOffset  = SWAPW(entryExitRecords[coverageIndex].exitAnchor);

    if (entryOffset != 0) {
        const AnchorTable *entryAnchorTable = (const AnchorTable *) ((char *) this + entryOffset);

        entryAnchorTable->getAnchor(glyphID, fontInstance, entryAnchor);
        glyphIterator->setCursiveEntryPoint(entryAnchor);
    } else {
        //glyphIterator->clearCursiveEntryPoint();
    }

    if (exitOffset != 0) {
        const AnchorTable *exitAnchorTable = (const AnchorTable *) ((char *) this + exitOffset);

        exitAnchorTable->getAnchor(glyphID, fontInstance, exitAnchor);
        glyphIterator->setCursiveExitPoint(exitAnchor);
    } else {
        //glyphIterator->clearCursiveExitPoint();
    }

    return 1;
}

U_NAMESPACE_END
