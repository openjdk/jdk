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
#include "PairPositioningSubtables.h"
#include "ValueRecords.h"
#include "GlyphIterator.h"
#include "OpenTypeUtilities.h"
#include "LESwaps.h"

U_NAMESPACE_BEGIN

le_uint32 PairPositioningSubtable::process(const LEReferenceTo<PairPositioningSubtable> &base, GlyphIterator *glyphIterator, const LEFontInstance *fontInstance, LEErrorCode &success) const
{
    switch(SWAPW(subtableFormat))
    {
    case 0:
        return 0;

    case 1:
    {
      const LEReferenceTo<PairPositioningFormat1Subtable> subtable(base, success, (const PairPositioningFormat1Subtable *) this);

      if(LE_SUCCESS(success))
      return subtable->process(subtable, glyphIterator, fontInstance, success);
      else
        return 0;
    }

    case 2:
    {
      const LEReferenceTo<PairPositioningFormat2Subtable> subtable(base, success, (const PairPositioningFormat2Subtable *) this);

      if(LE_SUCCESS(success))
      return subtable->process(subtable, glyphIterator, fontInstance, success);
      else
        return 0;
    }
    default:
      return 0;
    }
}

le_uint32 PairPositioningFormat1Subtable::process(const LEReferenceTo<PairPositioningFormat1Subtable> &base, GlyphIterator *glyphIterator, const LEFontInstance *fontInstance, LEErrorCode &success) const
{
    LEGlyphID firstGlyph = glyphIterator->getCurrGlyphID();
    le_int32 coverageIndex = getGlyphCoverage(base, firstGlyph, success);
    GlyphIterator tempIterator(*glyphIterator);

    LEReferenceToArrayOf<Offset> pairSetTableOffsetArrayRef(base, success, pairSetTableOffsetArray, SWAPW(pairSetCount));

    if (LE_SUCCESS(success) && coverageIndex >= 0 && glyphIterator->next() && (le_uint32)coverageIndex < pairSetTableOffsetArrayRef.getCount()) {
        Offset pairSetTableOffset = SWAPW(pairSetTableOffsetArray[coverageIndex]);
        LEReferenceTo<PairSetTable> pairSetTable(base, success, pairSetTableOffset);
        if( LE_FAILURE(success) ) return 0;
        le_uint16 pairValueCount = SWAPW(pairSetTable->pairValueCount);
        LEReferenceTo<PairValueRecord> pairValueRecordArray(pairSetTable, success, pairSetTable->pairValueRecordArray);
        if( LE_FAILURE(success) ) return 0;
        le_int16 valueRecord1Size = ValueRecord::getSize(SWAPW(valueFormat1));
        le_int16 valueRecord2Size = ValueRecord::getSize(SWAPW(valueFormat2));
        le_int16 recordSize = sizeof(PairValueRecord) - sizeof(ValueRecord) + valueRecord1Size + valueRecord2Size;
        LEGlyphID secondGlyph = glyphIterator->getCurrGlyphID();
        LEReferenceTo<PairValueRecord> pairValueRecord;

        if (pairValueCount != 0) {
          pairValueRecord = findPairValueRecord((TTGlyphID) LE_GET_GLYPH(secondGlyph), pairValueRecordArray, pairValueCount, recordSize, success);
        }

        if (pairValueRecord.isEmpty() || LE_FAILURE(success)) {
            return 0;
        }

        if (valueFormat1 != 0) {
          pairValueRecord->valueRecord1.adjustPosition(SWAPW(valueFormat1), base, tempIterator, fontInstance, success);
        }

        if (valueFormat2 != 0) {
          LEReferenceTo<ValueRecord> valueRecord2(base, success, ((char *) &pairValueRecord->valueRecord1 + valueRecord1Size));
          if(LE_SUCCESS(success)) {
            valueRecord2->adjustPosition(SWAPW(valueFormat2), base, *glyphIterator, fontInstance, success);
          }
        }

        // back up glyphIterator so second glyph can be
        // first glyph in the next pair
        glyphIterator->prev();
        return 1;
    }

    return 0;
}

le_uint32 PairPositioningFormat2Subtable::process(const LEReferenceTo<PairPositioningFormat2Subtable> &base, GlyphIterator *glyphIterator, const LEFontInstance *fontInstance, LEErrorCode &success) const
{
    LEGlyphID firstGlyph = glyphIterator->getCurrGlyphID();
    le_int32 coverageIndex = getGlyphCoverage(base, firstGlyph, success);

    if (LE_FAILURE(success)) {
        return 0;
    }

    GlyphIterator tempIterator(*glyphIterator);

    if (coverageIndex >= 0 && glyphIterator->next()) {
        LEGlyphID secondGlyph = glyphIterator->getCurrGlyphID();
        const LEReferenceTo<ClassDefinitionTable> classDef1(base, success, SWAPW(classDef1Offset));
        const LEReferenceTo<ClassDefinitionTable> classDef2(base, success, SWAPW(classDef2Offset));
        le_int32 class1 = classDef1->getGlyphClass(classDef1, firstGlyph, success);
        le_int32 class2 = classDef2->getGlyphClass(classDef2, secondGlyph, success);
        le_int16 valueRecord1Size = ValueRecord::getSize(SWAPW(valueFormat1));
        le_int16 valueRecord2Size = ValueRecord::getSize(SWAPW(valueFormat2));
        le_int16 class2RecordSize = valueRecord1Size + valueRecord2Size;
        le_int16 class1RecordSize = class2RecordSize * SWAPW(class2Count);
        const LEReferenceTo<Class1Record> class1Record(base, success, (const Class1Record *) ((char *) class1RecordArray + (class1RecordSize * class1)));
        const LEReferenceTo<Class2Record> class2Record(base, success, (const Class2Record *) ((char *) class1Record->class2RecordArray + (class2RecordSize * class2)));

        if( LE_SUCCESS(success) ) {
          if (valueFormat1 != 0) {
            class2Record->valueRecord1.adjustPosition(SWAPW(valueFormat1), base, tempIterator, fontInstance, success);
          }
          if (valueFormat2 != 0) {
            const LEReferenceTo<ValueRecord> valueRecord2(base, success, ((char *) &class2Record->valueRecord1) + valueRecord1Size);
            LEReferenceTo<PairPositioningFormat2Subtable> thisRef(base, success, this);
            if(LE_SUCCESS(success)) {
              valueRecord2->adjustPosition(SWAPW(valueFormat2), thisRef, *glyphIterator, fontInstance, success);
            }
          }
        }

        // back up glyphIterator so second glyph can be
        // first glyph in the next pair
        glyphIterator->prev();
        return 1;
    }

    return 0;
}

LEReferenceTo<PairValueRecord>
PairPositioningFormat1Subtable::findPairValueRecord(TTGlyphID glyphID, LEReferenceTo<PairValueRecord>& records,
                                                    le_uint16 recordCount,
                                                    le_uint16 recordSize, LEErrorCode &success) const
{
#if 1
        // The OpenType spec. says that the ValueRecord table is
        // sorted by secondGlyph. Unfortunately, there are fonts
        // around that have an unsorted ValueRecord table.
        LEReferenceTo<PairValueRecord> record(records);

        for(le_int32 r = 0; r < recordCount; r += 1) {
          if (r > 0) {
            record.addOffset(recordSize, success);
          }
          if(LE_FAILURE(success)) return LEReferenceTo<PairValueRecord>();
          if (SWAPW(record->secondGlyph) == glyphID) {
            return record;
          }
        }
#else
  #error dead code - not updated.
    le_uint8 bit = OpenTypeUtilities::highBit(recordCount);
    le_uint16 power = 1 << bit;
    le_uint16 extra = (recordCount - power) * recordSize;
    le_uint16 probe = power * recordSize;
    const PairValueRecord *record = records;
    const PairValueRecord *trial = (const PairValueRecord *) ((char *) record + extra);

    if (SWAPW(trial->secondGlyph) <= glyphID) {
        record = trial;
    }

    while (probe > recordSize) {
        probe >>= 1;
        trial = (const PairValueRecord *) ((char *) record + probe);

        if (SWAPW(trial->secondGlyph) <= glyphID) {
            record = trial;
        }
    }

    if (SWAPW(record->secondGlyph) == glyphID) {
        return record;
    }
#endif

    return LEReferenceTo<PairValueRecord>();
}

U_NAMESPACE_END
