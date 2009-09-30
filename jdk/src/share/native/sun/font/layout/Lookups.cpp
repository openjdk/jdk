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
#include "Lookups.h"
#include "CoverageTables.h"
#include "LESwaps.h"

U_NAMESPACE_BEGIN

const LookupTable *LookupListTable::getLookupTable(le_uint16 lookupTableIndex) const
{
    if (lookupTableIndex >= SWAPW(lookupCount)) {
        return 0;
    }

    Offset lookupTableOffset = lookupTableOffsetArray[lookupTableIndex];

    return (const LookupTable *) ((char *) this + SWAPW(lookupTableOffset));
}

const LookupSubtable *LookupTable::getLookupSubtable(le_uint16 subtableIndex) const
{
    if (subtableIndex >= SWAPW(subTableCount)) {
        return 0;
    }

    Offset subtableOffset = subTableOffsetArray[subtableIndex];

    return (const LookupSubtable *) ((char *) this + SWAPW(subtableOffset));
}

le_int32 LookupSubtable::getGlyphCoverage(Offset tableOffset, LEGlyphID glyphID) const
{
    const CoverageTable *coverageTable = (const CoverageTable *) ((char *) this + SWAPW(tableOffset));

    return coverageTable->getGlyphCoverage(glyphID);
}

U_NAMESPACE_END
