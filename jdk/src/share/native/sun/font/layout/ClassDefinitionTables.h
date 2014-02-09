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

#ifndef __CLASSDEFINITIONTABLES_H
#define __CLASSDEFINITIONTABLES_H

/**
 * \file
 * \internal
 */

#include "LETypes.h"
#include "OpenTypeTables.h"

U_NAMESPACE_BEGIN

struct ClassDefinitionTable
{
    le_uint16 classFormat;

    le_int32  getGlyphClass(const LETableReference &base, LEGlyphID glyphID, LEErrorCode &success) const;
    le_bool   hasGlyphClass(const LETableReference &base, le_int32 glyphClass, LEErrorCode &success) const;

#if LE_ENABLE_RAW
  le_int32 getGlyphClass(LEGlyphID glyphID) const {
    LETableReference base((const le_uint8*)this);
    LEErrorCode ignored = LE_NO_ERROR;
    return getGlyphClass(base,glyphID,ignored);
  }

  le_bool hasGlyphClass(le_int32 glyphClass) const {
    LETableReference base((const le_uint8*)this);
    LEErrorCode ignored = LE_NO_ERROR;
    return hasGlyphClass(base,glyphClass,ignored);
  }
#endif
};

struct ClassDefFormat1Table : ClassDefinitionTable
{
    TTGlyphID  startGlyph;
    le_uint16  glyphCount;
    le_uint16  classValueArray[ANY_NUMBER];

    le_int32 getGlyphClass(const LETableReference &base, LEGlyphID glyphID, LEErrorCode &success) const;
    le_bool  hasGlyphClass(const LETableReference &base, le_int32 glyphClass, LEErrorCode &success) const;
};
LE_VAR_ARRAY(ClassDefFormat1Table, classValueArray)


struct ClassRangeRecord
{
    TTGlyphID start;
    TTGlyphID end;
    le_uint16 classValue;
};

struct ClassDefFormat2Table : ClassDefinitionTable
{
    le_uint16        classRangeCount;
    GlyphRangeRecord classRangeRecordArray[ANY_NUMBER];

    le_int32 getGlyphClass(const LETableReference &base, LEGlyphID glyphID, LEErrorCode &success) const;
    le_bool hasGlyphClass(const LETableReference &base, le_int32 glyphClass, LEErrorCode &success) const;
};
LE_VAR_ARRAY(ClassDefFormat2Table, classRangeRecordArray)

U_NAMESPACE_END
#endif
