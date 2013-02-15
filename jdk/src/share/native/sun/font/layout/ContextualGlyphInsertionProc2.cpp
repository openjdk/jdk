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
#include "ContextualGlyphInsertionProc2.h"
#include "LEGlyphStorage.h"
#include "LESwaps.h"

U_NAMESPACE_BEGIN

UOBJECT_DEFINE_RTTI_IMPLEMENTATION(ContextualGlyphInsertionProcessor2)

ContextualGlyphInsertionProcessor2::ContextualGlyphInsertionProcessor2(const MorphSubtableHeader2 *morphSubtableHeader)
  : StateTableProcessor2(morphSubtableHeader)
{
    contextualGlyphHeader = (const ContextualGlyphInsertionHeader2 *) morphSubtableHeader;
    le_uint32 insertionTableOffset = SWAPL(contextualGlyphHeader->insertionTableOffset);
    insertionTable = ((le_uint16 *) ((char *)&stateTableHeader->stHeader + insertionTableOffset));
    entryTable = (const ContextualGlyphInsertionStateEntry2 *) ((char *) &stateTableHeader->stHeader + entryTableOffset);
}

ContextualGlyphInsertionProcessor2::~ContextualGlyphInsertionProcessor2()
{
}

void ContextualGlyphInsertionProcessor2::beginStateTable()
{
    markGlyph = 0;
}

le_uint16 ContextualGlyphInsertionProcessor2::processStateEntry(LEGlyphStorage &glyphStorage, le_int32 &currGlyph, EntryTableIndex2 index)
{
    const ContextualGlyphInsertionStateEntry2 *entry = &entryTable[index];
    le_uint16 newState = SWAPW(entry->newStateIndex);
    le_uint16 flags = SWAPW(entry->flags);
    le_int16 currIndex = SWAPW(entry->currentInsertionListIndex);
    le_int16 markIndex = SWAPW(entry->markedInsertionListIndex);
    int i = 0;

    if (markIndex > 0) {
        le_int16 count = (flags & cgiMarkedInsertCountMask) >> 5;
        if (!(flags & cgiMarkedIsKashidaLike)) {
            // extra glyph(s) will be added directly before/after the specified marked glyph
            if (!(flags & cgiMarkInsertBefore)) {
                LEGlyphID *insertGlyphs = glyphStorage.insertGlyphs(markGlyph, count + 1);
                for (i = 0; i < count; i++, markIndex++) {
                    insertGlyphs[i] = insertionTable[markIndex];
                }
                insertGlyphs[i] = glyphStorage[markGlyph];
                glyphStorage.applyInsertions();
            } else {
                LEGlyphID *insertGlyphs = glyphStorage.insertGlyphs(markGlyph, count + 1);
                insertGlyphs[0] = glyphStorage[markGlyph];
                for (i = 1; i < count + 1; i++, markIndex++) {
                    insertGlyphs[i] = insertionTable[markIndex];
                }
                glyphStorage.applyInsertions();
            }
        } else {
            // inserted as a split-vowel-like insertion
            // extra glyph(s) will be inserted some distance away from the marked glyph
            if (!(flags & cgiMarkInsertBefore)) {
                LEGlyphID *insertGlyphs = glyphStorage.insertGlyphs(markGlyph, count + 1);
                for (i = 0; i < count; i++, markIndex++) {
                    insertGlyphs[i] = insertionTable[markIndex];
                }
                insertGlyphs[i] = glyphStorage[markGlyph];
                glyphStorage.applyInsertions();
            } else {
                LEGlyphID *insertGlyphs = glyphStorage.insertGlyphs(markGlyph, count + 1);
                insertGlyphs[0] = glyphStorage[markGlyph];
                for (i = 1; i < count + 1; i++, markIndex++) {
                    insertGlyphs[i] = insertionTable[markIndex];
                }
                glyphStorage.applyInsertions();
            }
        }
    }

    if (currIndex > 0) {
        le_int16 count = flags & cgiCurrentInsertCountMask;
        if (!(flags & cgiCurrentIsKashidaLike)) {
            // extra glyph(s) will be added directly before/after the specified current glyph
            if (!(flags & cgiCurrentInsertBefore)) {
                LEGlyphID *insertGlyphs = glyphStorage.insertGlyphs(currGlyph, count + 1);
                for (i = 0; i < count; i++, currIndex++) {
                    insertGlyphs[i] = insertionTable[currIndex];
                }
                insertGlyphs[i] = glyphStorage[currGlyph];
                glyphStorage.applyInsertions();
            } else {
                LEGlyphID *insertGlyphs = glyphStorage.insertGlyphs(currGlyph, count + 1);
                insertGlyphs[0] = glyphStorage[currGlyph];
                for (i = 1; i < count + 1; i++, currIndex++) {
                    insertGlyphs[i] = insertionTable[currIndex];
                }
                glyphStorage.applyInsertions();
            }
        } else {
            // inserted as a split-vowel-like insertion
            // extra glyph(s) will be inserted some distance away from the current glyph
            if (!(flags & cgiCurrentInsertBefore)) {
                LEGlyphID *insertGlyphs = glyphStorage.insertGlyphs(currGlyph, count + 1);
                for (i = 0; i < count; i++, currIndex++) {
                    insertGlyphs[i] = insertionTable[currIndex];
                }
                insertGlyphs[i] = glyphStorage[currGlyph];
                glyphStorage.applyInsertions();
            } else {
                LEGlyphID *insertGlyphs = glyphStorage.insertGlyphs(currGlyph, count + 1);
                insertGlyphs[0] = glyphStorage[currGlyph];
                for (i = 1; i < count + 1; i++, currIndex++) {
                    insertGlyphs[i] = insertionTable[currIndex];
                }
                glyphStorage.applyInsertions();
            }
        }
    }

    if (flags & cgiSetMark) {
        markGlyph = currGlyph;
    }

    if (!(flags & cgiDontAdvance)) {
        currGlyph += dir;
    }

    return newState;
}

void ContextualGlyphInsertionProcessor2::endStateTable()
{
}

U_NAMESPACE_END
