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
 * (C) Copyright IBM Corp.  and others 1998-2013 - All Rights Reserved
 *
 */

#include "LETypes.h"
#include "MorphTables.h"
#include "StateTables.h"
#include "MorphStateTables.h"
#include "SubtableProcessor2.h"
#include "StateTableProcessor2.h"
#include "ContextualGlyphSubstProc2.h"
#include "LEGlyphStorage.h"
#include "LESwaps.h"
#include <stdio.h>

U_NAMESPACE_BEGIN

UOBJECT_DEFINE_RTTI_IMPLEMENTATION(ContextualGlyphSubstitutionProcessor2)

ContextualGlyphSubstitutionProcessor2::ContextualGlyphSubstitutionProcessor2(const MorphSubtableHeader2 *morphSubtableHeader)
  : StateTableProcessor2(morphSubtableHeader)
{
    contextualGlyphHeader = (const ContextualGlyphHeader2 *) morphSubtableHeader;
    le_uint32 perGlyphTableOffset = SWAPL(contextualGlyphHeader->perGlyphTableOffset);
    perGlyphTable = ((le_uint32 *) ((char *)&stateTableHeader->stHeader + perGlyphTableOffset));
    entryTable = (const ContextualGlyphStateEntry2 *) ((char *) &stateTableHeader->stHeader + entryTableOffset);
}

ContextualGlyphSubstitutionProcessor2::~ContextualGlyphSubstitutionProcessor2()
{
}

void ContextualGlyphSubstitutionProcessor2::beginStateTable()
{
    markGlyph = 0;
}

le_uint16 ContextualGlyphSubstitutionProcessor2::processStateEntry(LEGlyphStorage &glyphStorage, le_int32 &currGlyph, EntryTableIndex2 index)
{
    const ContextualGlyphStateEntry2 *entry = &entryTable[index];
    le_uint16 newState = SWAPW(entry->newStateIndex);
    le_uint16 flags = SWAPW(entry->flags);
    le_int16 markIndex = SWAPW(entry->markIndex);
    le_int16 currIndex = SWAPW(entry->currIndex);

    if (markIndex != -1) {
        le_uint32 offset = SWAPL(perGlyphTable[markIndex]);
        LEGlyphID mGlyph = glyphStorage[markGlyph];
        TTGlyphID newGlyph = lookup(offset, mGlyph);
        glyphStorage[markGlyph] = LE_SET_GLYPH(mGlyph, newGlyph);
    }

    if (currIndex != -1) {
        le_uint32 offset = SWAPL(perGlyphTable[currIndex]);
        LEGlyphID thisGlyph = glyphStorage[currGlyph];
        TTGlyphID newGlyph = lookup(offset, thisGlyph);
        glyphStorage[currGlyph] = LE_SET_GLYPH(thisGlyph, newGlyph);
    }

    if (flags & cgsSetMark) {
        markGlyph = currGlyph;
    }

    if (!(flags & cgsDontAdvance)) {
        currGlyph += dir;
    }

    return newState;
}

TTGlyphID ContextualGlyphSubstitutionProcessor2::lookup(le_uint32 offset, LEGlyphID gid)
{
    LookupTable *lookupTable = ((LookupTable *) ((char *)perGlyphTable + offset));
    le_int16 format = SWAPW(lookupTable->format);
    TTGlyphID newGlyph = 0xFFFF;

    switch (format) {
        case ltfSimpleArray: {
#ifdef TEST_FORMAT
            // Disabled pending for design review
            SimpleArrayLookupTable *lookupTable0 = (SimpleArrayLookupTable *) lookupTable;
            TTGlyphID glyphCode = (TTGlyphID) LE_GET_GLYPH(gid);
            newGlyph = SWAPW(lookupTable0->valueArray[glyphCode]);
#endif
            break;
        }
        case ltfSegmentSingle: {
#ifdef TEST_FORMAT
            // Disabled pending for design review
            SegmentSingleLookupTable *lookupTable2 = (SegmentSingleLookupTable *) lookupTable;
            const LookupSegment *segment = lookupTable2->lookupSegment(lookupTable2->segments, gid);
            if (segment != NULL) {
                newGlyph = SWAPW(segment->value);
            }
#endif
            break;
        }
        case ltfSegmentArray: {
            printf("Context Lookup Table Format4: specific interpretation needed!\n");
            break;
        }
        case ltfSingleTable:
        {
#ifdef TEST_FORMAT
            // Disabled pending for design review
            SingleTableLookupTable *lookupTable6 = (SingleTableLookupTable *) lookupTable;
            const LookupSingle *segment = lookupTable6->lookupSingle(lookupTable6->entries, gid);
            if (segment != NULL) {
                newGlyph = SWAPW(segment->value);
            }
#endif
            break;
        }
        case ltfTrimmedArray: {
            TrimmedArrayLookupTable *lookupTable8 = (TrimmedArrayLookupTable *) lookupTable;
            TTGlyphID firstGlyph = SWAPW(lookupTable8->firstGlyph);
            TTGlyphID lastGlyph  = firstGlyph + SWAPW(lookupTable8->glyphCount);
            TTGlyphID glyphCode = (TTGlyphID) LE_GET_GLYPH(gid);
            if ((glyphCode >= firstGlyph) && (glyphCode < lastGlyph)) {
                newGlyph = SWAPW(lookupTable8->valueArray[glyphCode - firstGlyph]);
            }
        }
        default:
            break;
    }
    return newGlyph;
}

void ContextualGlyphSubstitutionProcessor2::endStateTable()
{
}

U_NAMESPACE_END
