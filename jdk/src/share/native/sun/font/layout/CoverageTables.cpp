/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

/*
 *
 * (C) Copyright IBM Corp. 1998-2004 - All Rights Reserved
 *
 */

#include "LETypes.h"
#include "OpenTypeTables.h"
#include "OpenTypeUtilities.h"
#include "CoverageTables.h"
#include "LESwaps.h"

U_NAMESPACE_BEGIN

le_int32 CoverageTable::getGlyphCoverage(LEGlyphID glyphID) const
{
    switch(SWAPW(coverageFormat))
    {
    case 0:
        return -1;

    case 1:
    {
        const CoverageFormat1Table *f1Table = (const CoverageFormat1Table *) this;

        return f1Table->getGlyphCoverage(glyphID);
    }

    case 2:
    {
        const CoverageFormat2Table *f2Table = (const CoverageFormat2Table *) this;

        return f2Table->getGlyphCoverage(glyphID);
    }

    default:
        return -1;
    }
}

le_int32 CoverageFormat1Table::getGlyphCoverage(LEGlyphID glyphID) const
{
    TTGlyphID ttGlyphID = (TTGlyphID) LE_GET_GLYPH(glyphID);
    le_uint16 count = SWAPW(glyphCount);
    le_uint8 bit = OpenTypeUtilities::highBit(count);
    le_uint16 power = 1 << bit;
    le_uint16 extra = count - power;
    le_uint16 probe = power;
    le_uint16 index = 0;

    if (SWAPW(glyphArray[extra]) <= ttGlyphID) {
        index = extra;
    }

    while (probe > (1 << 0)) {
        probe >>= 1;

        if (SWAPW(glyphArray[index + probe]) <= ttGlyphID) {
            index += probe;
        }
    }

    if (SWAPW(glyphArray[index]) == ttGlyphID) {
        return index;
    }

    return -1;
}

le_int32 CoverageFormat2Table::getGlyphCoverage(LEGlyphID glyphID) const
{
    TTGlyphID ttGlyphID = (TTGlyphID) LE_GET_GLYPH(glyphID);
    le_uint16 count = SWAPW(rangeCount);
    le_int32 rangeIndex =
        OpenTypeUtilities::getGlyphRangeIndex(ttGlyphID, rangeRecordArray, count);

    if (rangeIndex < 0) {
        return -1;
    }

    TTGlyphID firstInRange = SWAPW(rangeRecordArray[rangeIndex].firstGlyph);
    le_uint16  startCoverageIndex = SWAPW(rangeRecordArray[rangeIndex].rangeValue);

    return startCoverageIndex + (ttGlyphID - firstInRange);
}

U_NAMESPACE_END
