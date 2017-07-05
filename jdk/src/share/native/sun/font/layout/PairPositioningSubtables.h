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

#ifndef __PAIRPOSITIONINGSUBTABLES_H
#define __PAIRPOSITIONINGSUBTABLES_H

/**
 * \file
 * \internal
 */

#include "LETypes.h"
#include "LEFontInstance.h"
#include "OpenTypeTables.h"
#include "GlyphPositioningTables.h"
#include "ValueRecords.h"
#include "GlyphIterator.h"

U_NAMESPACE_BEGIN

// NOTE: ValueRecord has a variable size
struct PairValueRecord
{
    TTGlyphID     secondGlyph;
    ValueRecord valueRecord1;
//  ValueRecord valueRecord2;
};

struct PairSetTable
{
    le_uint16       pairValueCount;
    PairValueRecord pairValueRecordArray[ANY_NUMBER];
};
LE_VAR_ARRAY(PairSetTable, pairValueRecordArray)

struct PairPositioningSubtable : GlyphPositioningSubtable
{
    ValueFormat valueFormat1;
    ValueFormat valueFormat2;

    le_uint32  process(const LEReferenceTo<PairPositioningSubtable> &base, GlyphIterator *glyphIterator, const LEFontInstance *fontInstance, LEErrorCode &success) const;
};

struct PairPositioningFormat1Subtable : PairPositioningSubtable
{
    le_uint16   pairSetCount;
    Offset      pairSetTableOffsetArray[ANY_NUMBER];

    le_uint32  process(const LEReferenceTo<PairPositioningFormat1Subtable> &base, GlyphIterator *glyphIterator, const LEFontInstance *fontInstance, LEErrorCode &success) const;

private:
    const PairValueRecord *findPairValueRecord(TTGlyphID glyphID, const PairValueRecord *records,
        le_uint16 recordCount, le_uint16 recordSize) const;
};
LE_VAR_ARRAY(PairPositioningFormat1Subtable, pairSetTableOffsetArray)

// NOTE: ValueRecord has a variable size
struct Class2Record
{
    ValueRecord valueRecord1;
//  ValueRecord valurRecord2;
};

struct Class1Record
{
    Class2Record class2RecordArray[ANY_NUMBER];
};
LE_VAR_ARRAY(Class1Record, class2RecordArray)

struct PairPositioningFormat2Subtable : PairPositioningSubtable
{
    Offset       classDef1Offset;
    Offset       classDef2Offset;
    le_uint16    class1Count;
    le_uint16    class2Count;
    Class1Record class1RecordArray[ANY_NUMBER];

    le_uint32  process(const LEReferenceTo<PairPositioningFormat2Subtable> &base, GlyphIterator *glyphIterator, const LEFontInstance *fontInstance, LEErrorCode &success) const;
};
LE_VAR_ARRAY(PairPositioningFormat2Subtable, class1RecordArray)

U_NAMESPACE_END
#endif


