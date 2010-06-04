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

le_uint32 PairPositioningSubtable::process(GlyphIterator *glyphIterator, const LEFontInstance *fontInstance) const
{
    switch(SWAPW(subtableFormat))
    {
    case 0:
        return 0;

    case 1:
    {
        const PairPositioningFormat1Subtable *subtable = (const PairPositioningFormat1Subtable *) this;

        return subtable->process(glyphIterator, fontInstance);
    }

    case 2:
    {
        const PairPositioningFormat2Subtable *subtable = (const PairPositioningFormat2Subtable *) this;

        return subtable->process(glyphIterator, fontInstance);
    }

    default:
        return 0;
    }
}

le_uint32 PairPositioningFormat1Subtable::process(GlyphIterator *glyphIterator, const LEFontInstance *fontInstance) const
{
    LEGlyphID firstGlyph = glyphIterator->getCurrGlyphID();
    le_int32 coverageIndex = getGlyphCoverage(firstGlyph);
    GlyphIterator tempIterator(*glyphIterator);

    if (coverageIndex >= 0 && glyphIterator->next()) {
        Offset pairSetTableOffset = SWAPW(pairSetTableOffsetArray[coverageIndex]);
        PairSetTable *pairSetTable = (PairSetTable *) ((char *) this + pairSetTableOffset);
        le_uint16 pairValueCount = SWAPW(pairSetTable->pairValueCount);
        le_int16 valueRecord1Size = ValueRecord::getSize(SWAPW(valueFormat1));
        le_int16 valueRecord2Size = ValueRecord::getSize(SWAPW(valueFormat2));
        le_int16 recordSize = sizeof(PairValueRecord) - sizeof(ValueRecord) + valueRecord1Size + valueRecord2Size;
        LEGlyphID secondGlyph = glyphIterator->getCurrGlyphID();
        const PairValueRecord *pairValueRecord = NULL;

        if (pairValueCount != 0) {
            pairValueRecord = findPairValueRecord((TTGlyphID) LE_GET_GLYPH(secondGlyph), pairSetTable->pairValueRecordArray, pairValueCount, recordSize);
        }

        if (pairValueRecord == NULL) {
            return 0;
        }

        if (valueFormat1 != 0) {
            pairValueRecord->valueRecord1.adjustPosition(SWAPW(valueFormat1), (char *) this, tempIterator, fontInstance);
        }

        if (valueFormat2 != 0) {
            const ValueRecord *valueRecord2 = (const ValueRecord *) ((char *) &pairValueRecord->valueRecord1 + valueRecord1Size);

            valueRecord2->adjustPosition(SWAPW(valueFormat2), (char *) this, *glyphIterator, fontInstance);
        }

        return 2;
    }

    return 0;
}

le_uint32 PairPositioningFormat2Subtable::process(GlyphIterator *glyphIterator, const LEFontInstance *fontInstance) const
{
    LEGlyphID firstGlyph = glyphIterator->getCurrGlyphID();
    le_int32 coverageIndex = getGlyphCoverage(firstGlyph);
    GlyphIterator tempIterator(*glyphIterator);

    if (coverageIndex >= 0 && glyphIterator->next()) {
        LEGlyphID secondGlyph = glyphIterator->getCurrGlyphID();
        const ClassDefinitionTable *classDef1 = (const ClassDefinitionTable *) ((char *) this + SWAPW(classDef1Offset));
        const ClassDefinitionTable *classDef2 = (const ClassDefinitionTable *) ((char *) this + SWAPW(classDef2Offset));
        le_int32 class1 = classDef1->getGlyphClass(firstGlyph);
        le_int32 class2 = classDef2->getGlyphClass(secondGlyph);
        le_int16 valueRecord1Size = ValueRecord::getSize(SWAPW(valueFormat1));
        le_int16 valueRecord2Size = ValueRecord::getSize(SWAPW(valueFormat2));
        le_int16 class2RecordSize = valueRecord1Size + valueRecord2Size;
        le_int16 class1RecordSize = class2RecordSize * SWAPW(class2Count);
        const Class1Record *class1Record = (const Class1Record *) ((char *) class1RecordArray + (class1RecordSize * class1));
        const Class2Record *class2Record = (const Class2Record *) ((char *) class1Record->class2RecordArray + (class2RecordSize * class2));


        if (valueFormat1 != 0) {
            class2Record->valueRecord1.adjustPosition(SWAPW(valueFormat1), (char *) this, tempIterator, fontInstance);
        }

        if (valueFormat2 != 0) {
            const ValueRecord *valueRecord2 = (const ValueRecord *) ((char *) &class2Record->valueRecord1 + valueRecord1Size);

            valueRecord2->adjustPosition(SWAPW(valueFormat2), (const char *) this, *glyphIterator, fontInstance);
        }

        return 2;
    }

    return 0;
}

const PairValueRecord *PairPositioningFormat1Subtable::findPairValueRecord(TTGlyphID glyphID, const PairValueRecord *records, le_uint16 recordCount, le_uint16 recordSize) const
{
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

    return NULL;
}

U_NAMESPACE_END
