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
#include "GlyphPositioningTables.h"
#include "SinglePositioningSubtables.h"
#include "ValueRecords.h"
#include "GlyphIterator.h"
#include "LESwaps.h"

U_NAMESPACE_BEGIN

le_uint32 SinglePositioningSubtable::process(GlyphIterator *glyphIterator, const LEFontInstance *fontInstance) const
{
    switch(SWAPW(subtableFormat))
    {
    case 0:
        return 0;

    case 1:
    {
        const SinglePositioningFormat1Subtable *subtable = (const SinglePositioningFormat1Subtable *) this;

        return subtable->process(glyphIterator, fontInstance);
    }

    case 2:
    {
        const SinglePositioningFormat2Subtable *subtable = (const SinglePositioningFormat2Subtable *) this;

        return subtable->process(glyphIterator, fontInstance);
    }

    default:
        return 0;
    }
}

le_uint32 SinglePositioningFormat1Subtable::process(GlyphIterator *glyphIterator, const LEFontInstance *fontInstance) const
{
    LEGlyphID glyph = glyphIterator->getCurrGlyphID();
    le_int32 coverageIndex = getGlyphCoverage(glyph);

    if (coverageIndex >= 0) {
        valueRecord.adjustPosition(SWAPW(valueFormat), (const char *) this, *glyphIterator, fontInstance);

        return 1;
    }

    return 0;
}

le_uint32 SinglePositioningFormat2Subtable::process(GlyphIterator *glyphIterator, const LEFontInstance *fontInstance) const
{
    LEGlyphID glyph = glyphIterator->getCurrGlyphID();
    le_int16 coverageIndex = (le_int16) getGlyphCoverage(glyph);

    if (coverageIndex >= 0) {
        valueRecordArray[0].adjustPosition(coverageIndex, SWAPW(valueFormat), (const char *) this, *glyphIterator, fontInstance);

        return 1;
    }

    return 0;
}

U_NAMESPACE_END
