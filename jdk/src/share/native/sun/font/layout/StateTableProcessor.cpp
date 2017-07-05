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
#include "MorphTables.h"
#include "StateTables.h"
#include "MorphStateTables.h"
#include "SubtableProcessor.h"
#include "StateTableProcessor.h"
#include "LEGlyphStorage.h"
#include "LESwaps.h"

U_NAMESPACE_BEGIN

StateTableProcessor::StateTableProcessor()
{
}

StateTableProcessor::StateTableProcessor(const MorphSubtableHeader *morphSubtableHeader)
  : SubtableProcessor(morphSubtableHeader)
{
    stateTableHeader = (const MorphStateTableHeader *) morphSubtableHeader;

    stateSize = SWAPW(stateTableHeader->stHeader.stateSize);
    classTableOffset = SWAPW(stateTableHeader->stHeader.classTableOffset);
    stateArrayOffset = SWAPW(stateTableHeader->stHeader.stateArrayOffset);
    entryTableOffset = SWAPW(stateTableHeader->stHeader.entryTableOffset);

    classTable = (const ClassTable *) ((char *) &stateTableHeader->stHeader + classTableOffset);
    firstGlyph = SWAPW(classTable->firstGlyph);
    lastGlyph  = firstGlyph + SWAPW(classTable->nGlyphs);
}

StateTableProcessor::~StateTableProcessor()
{
}

void StateTableProcessor::process(LEGlyphStorage &glyphStorage)
{
    // Start at state 0
    // XXX: How do we know when to start at state 1?
    ByteOffset currentState = stateArrayOffset;

    // XXX: reverse?
    le_int32 currGlyph = 0;
    le_int32 glyphCount = glyphStorage.getGlyphCount();

    beginStateTable();

    while (currGlyph <= glyphCount) {
        ClassCode classCode = classCodeOOB;
        if (currGlyph == glyphCount) {
            // XXX: How do we handle EOT vs. EOL?
            classCode = classCodeEOT;
        } else {
            TTGlyphID glyphCode = (TTGlyphID) LE_GET_GLYPH(glyphStorage[currGlyph]);

            if (glyphCode == 0xFFFF) {
                classCode = classCodeDEL;
            } else if ((glyphCode >= firstGlyph) && (glyphCode < lastGlyph)) {
                classCode = classTable->classArray[glyphCode - firstGlyph];
            }
        }

        const EntryTableIndex *stateArray = (const EntryTableIndex *) ((char *) &stateTableHeader->stHeader + currentState);
        EntryTableIndex entryTableIndex = stateArray[(le_uint8)classCode];

        currentState = processStateEntry(glyphStorage, currGlyph, entryTableIndex);
    }

    endStateTable();
}

U_NAMESPACE_END
