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
 * (C) Copyright IBM Corp. 1998-2004 - All Rights Reserved
 *
 */

#include "LETypes.h"
#include "OpenTypeTables.h"
#include "Lookups.h"
#include "CoverageTables.h"
#include "LESwaps.h"

U_NAMESPACE_BEGIN

const LEReferenceTo<LookupTable> LookupListTable::getLookupTable(const LEReferenceTo<LookupListTable> &base, le_uint16 lookupTableIndex, LEErrorCode &success) const
{
  LEReferenceToArrayOf<Offset> lookupTableOffsetArrayRef(base, success, (const Offset*)&lookupTableOffsetArray, SWAPW(lookupCount));

  if(LE_FAILURE(success) || lookupTableIndex>lookupTableOffsetArrayRef.getCount()) {
    return LEReferenceTo<LookupTable>();
  } else {
    return LEReferenceTo<LookupTable>(base, success, SWAPW(lookupTableOffsetArrayRef.getObject(lookupTableIndex, success)));
  }
}

const LEReferenceTo<LookupSubtable> LookupTable::getLookupSubtable(const LEReferenceTo<LookupTable> &base, le_uint16 subtableIndex, LEErrorCode &success) const
{
  LEReferenceToArrayOf<Offset> subTableOffsetArrayRef(base, success, (const Offset*)&subTableOffsetArray, SWAPW(subTableCount));

  if(LE_FAILURE(success) || subtableIndex>subTableOffsetArrayRef.getCount()) {
    return LEReferenceTo<LookupSubtable>();
  } else {
    return LEReferenceTo<LookupSubtable>(base, success, SWAPW(subTableOffsetArrayRef.getObject(subtableIndex, success)));
  }
}

le_int32 LookupSubtable::getGlyphCoverage(const LEReferenceTo<LookupSubtable> &base, Offset tableOffset, LEGlyphID glyphID, LEErrorCode &success) const
{
  const LEReferenceTo<CoverageTable> coverageTable(base, success, SWAPW(tableOffset));

  if(LE_FAILURE(success)) return 0;

  return coverageTable->getGlyphCoverage(coverageTable, glyphID, success);
}

U_NAMESPACE_END
