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
#include "OpenTypeUtilities.h"
#include "ClassDefinitionTables.h"
#include "LESwaps.h"

U_NAMESPACE_BEGIN

le_int32 ClassDefinitionTable::getGlyphClass(const LETableReference& base, LEGlyphID glyphID, LEErrorCode &success) const
{
  LEReferenceTo<ClassDefinitionTable> thisRef(base, success);
  if (LE_FAILURE(success)) return 0;

  switch(SWAPW(classFormat)) {
    case 0:
        return 0;

    case 1:
    {
      const LEReferenceTo<ClassDefFormat1Table> f1Table(thisRef, success);
      return f1Table->getGlyphClass(f1Table, glyphID, success);
    }

    case 2:
    {
      const LEReferenceTo<ClassDefFormat2Table> f2Table(thisRef, success);
      return  f2Table->getGlyphClass(f2Table, glyphID, success);
    }

    default:
        return 0;
  }
}

le_bool ClassDefinitionTable::hasGlyphClass(const LETableReference &base, le_int32 glyphClass, LEErrorCode &success) const
{
    LEReferenceTo<ClassDefinitionTable> thisRef(base, success);
    if (LE_FAILURE(success)) return 0;

    switch(SWAPW(classFormat)) {
    case 0:
        return 0;

    case 1:
    {
      const LEReferenceTo<ClassDefFormat1Table> f1Table(thisRef, success);
      return f1Table->hasGlyphClass(f1Table, glyphClass, success);
    }

    case 2:
    {
      const LEReferenceTo<ClassDefFormat2Table> f2Table(thisRef, success);
      return f2Table->hasGlyphClass(f2Table, glyphClass, success);
    }

    default:
        return 0;
    }
}

le_int32 ClassDefFormat1Table::getGlyphClass(const LETableReference& base, LEGlyphID glyphID, LEErrorCode &success) const
{
    if(LE_FAILURE(success)) return 0;

    le_uint16 count = SWAPW(glyphCount);
    LEReferenceToArrayOf<le_uint16> classValueArrayRef(base, success, &classValueArray[0], count);
    TTGlyphID ttGlyphID  = (TTGlyphID) LE_GET_GLYPH(glyphID);
    TTGlyphID firstGlyph = SWAPW(startGlyph);
    TTGlyphID lastGlyph  = firstGlyph + count;

    if (LE_SUCCESS(success) && ttGlyphID >= firstGlyph && ttGlyphID < lastGlyph) {
      return SWAPW( classValueArrayRef(ttGlyphID - firstGlyph, success) );
    }

    return 0;
}

le_bool ClassDefFormat1Table::hasGlyphClass(const LETableReference &base, le_int32 glyphClass, LEErrorCode &success) const
{
    if(LE_FAILURE(success)) return 0;
    le_uint16 count = SWAPW(glyphCount);
    LEReferenceToArrayOf<le_uint16> classValueArrayRef(base, success, &classValueArray[0], count);
    int i;

    for (i = 0; LE_SUCCESS(success)&& (i < count); i += 1) {
      if (SWAPW(classValueArrayRef(i,success)) == glyphClass) {
            return TRUE;
        }
    }

    return FALSE;
}

le_int32 ClassDefFormat2Table::getGlyphClass(const LETableReference& base, LEGlyphID glyphID, LEErrorCode &success) const
{
    if(LE_FAILURE(success)) return 0;
    TTGlyphID ttGlyph    = (TTGlyphID) LE_GET_GLYPH(glyphID);
    le_uint16 rangeCount = SWAPW(classRangeCount);
    LEReferenceToArrayOf<GlyphRangeRecord> classRangeRecordArrayRef(base, success, &classRangeRecordArray[0], rangeCount);
    le_int32  rangeIndex =
      OpenTypeUtilities::getGlyphRangeIndex(ttGlyph, classRangeRecordArrayRef, success);

    if (rangeIndex < 0 || LE_FAILURE(success)) {
        return 0;
    }

    return SWAPW(classRangeRecordArrayRef(rangeIndex, success).rangeValue);
}

le_bool ClassDefFormat2Table::hasGlyphClass(const LETableReference &base, le_int32 glyphClass, LEErrorCode &success) const
{
    if(LE_FAILURE(success)) return 0;
    le_uint16 rangeCount = SWAPW(classRangeCount);
    LEReferenceToArrayOf<GlyphRangeRecord> classRangeRecordArrayRef(base, success, &classRangeRecordArray[0], rangeCount);
    int i;

    for (i = 0; i < rangeCount && LE_SUCCESS(success); i += 1) {
      if (SWAPW(classRangeRecordArrayRef(i,success).rangeValue) == glyphClass) {
            return TRUE;
        }
    }

    return FALSE;
}

U_NAMESPACE_END
