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
#include "IndicRearrangementProcessor.h"
#include "LEGlyphStorage.h"
#include "LESwaps.h"

U_NAMESPACE_BEGIN

UOBJECT_DEFINE_RTTI_IMPLEMENTATION(IndicRearrangementProcessor)

IndicRearrangementProcessor::IndicRearrangementProcessor(const MorphSubtableHeader *morphSubtableHeader)
  : StateTableProcessor(morphSubtableHeader)
{
    indicRearrangementSubtableHeader = (const IndicRearrangementSubtableHeader *) morphSubtableHeader;
    entryTable = (const IndicRearrangementStateEntry *) ((char *) &stateTableHeader->stHeader + entryTableOffset);
}

IndicRearrangementProcessor::~IndicRearrangementProcessor()
{
}

void IndicRearrangementProcessor::beginStateTable()
{
    firstGlyph = 0;
    lastGlyph = 0;
}

ByteOffset IndicRearrangementProcessor::processStateEntry(LEGlyphStorage &glyphStorage, le_int32 &currGlyph, EntryTableIndex index)
{
    const IndicRearrangementStateEntry *entry = &entryTable[index];
    ByteOffset newState = SWAPW(entry->newStateOffset);
    IndicRearrangementFlags flags = (IndicRearrangementFlags) SWAPW(entry->flags);

    if (flags & irfMarkFirst) {
        firstGlyph = currGlyph;
    }

    if (flags & irfMarkLast) {
        lastGlyph = currGlyph;
    }

    doRearrangementAction(glyphStorage, (IndicRearrangementVerb) (flags & irfVerbMask));

    if (!(flags & irfDontAdvance)) {
        // XXX: Should handle reverse too...
        currGlyph += 1;
    }

    return newState;
}

void IndicRearrangementProcessor::endStateTable()
{
}

void IndicRearrangementProcessor::doRearrangementAction(LEGlyphStorage &glyphStorage, IndicRearrangementVerb verb) const
{
    LEGlyphID a, b, c, d;
    le_int32 ia, ib, ic, id, ix, x;
    LEErrorCode success = LE_NO_ERROR;

    switch(verb)
    {
    case irvNoAction:
        break;

    case irvxA:
        a = glyphStorage[firstGlyph];
        ia = glyphStorage.getCharIndex(firstGlyph, success);
        x = firstGlyph + 1;

        while (x <= lastGlyph) {
            glyphStorage[x - 1] = glyphStorage[x];
            ix = glyphStorage.getCharIndex(x, success);
            glyphStorage.setCharIndex(x - 1, ix, success);
            x += 1;
        }

        glyphStorage[lastGlyph] = a;
        glyphStorage.setCharIndex(lastGlyph, ia, success);
        break;

    case irvDx:
        d = glyphStorage[lastGlyph];
        id = glyphStorage.getCharIndex(lastGlyph, success);
        x = lastGlyph - 1;

        while (x >= firstGlyph) {
            glyphStorage[x + 1] = glyphStorage[x];
            ix = glyphStorage.getCharIndex(x, success);
            glyphStorage.setCharIndex(x + 1, ix, success);
            x -= 1;
        }

        glyphStorage[firstGlyph] = d;
        glyphStorage.setCharIndex(firstGlyph, id, success);
        break;

    case irvDxA:
        a = glyphStorage[firstGlyph];
        ia = glyphStorage.getCharIndex(firstGlyph, success);
        id = glyphStorage.getCharIndex(lastGlyph,  success);

        glyphStorage[firstGlyph] = glyphStorage[lastGlyph];
        glyphStorage[lastGlyph] = a;

        glyphStorage.setCharIndex(firstGlyph, id, success);
        glyphStorage.setCharIndex(lastGlyph,  ia, success);
        break;

    case irvxAB:
        a = glyphStorage[firstGlyph];
        b = glyphStorage[firstGlyph + 1];
        ia = glyphStorage.getCharIndex(firstGlyph, success);
        ib = glyphStorage.getCharIndex(firstGlyph + 1, success);
        x = firstGlyph + 2;

        while (x <= lastGlyph) {
            glyphStorage[x - 2] = glyphStorage[x];
            ix = glyphStorage.getCharIndex(x, success);
            glyphStorage.setCharIndex(x - 2, ix, success);
            x += 1;
        }

        glyphStorage[lastGlyph - 1] = a;
        glyphStorage[lastGlyph] = b;

        glyphStorage.setCharIndex(lastGlyph - 1, ia, success);
        glyphStorage.setCharIndex(lastGlyph, ib, success);
        break;

    case irvxBA:
        a = glyphStorage[firstGlyph];
        b = glyphStorage[firstGlyph + 1];
        ia = glyphStorage.getCharIndex(firstGlyph, success);
        ib = glyphStorage.getCharIndex(firstGlyph + 1, success);
        x = firstGlyph + 2;

        while (x <= lastGlyph) {
            glyphStorage[x - 2] = glyphStorage[x];
            ix = glyphStorage.getCharIndex(x, success);
            glyphStorage.setCharIndex(x - 2, ix, success);
            x += 1;
        }

        glyphStorage[lastGlyph - 1] = b;
        glyphStorage[lastGlyph] = a;

        glyphStorage.setCharIndex(lastGlyph - 1, ib, success);
        glyphStorage.setCharIndex(lastGlyph, ia, success);
        break;

    case irvCDx:
        c = glyphStorage[lastGlyph - 1];
        d = glyphStorage[lastGlyph];
        ic = glyphStorage.getCharIndex(lastGlyph - 1, success);
        id = glyphStorage.getCharIndex(lastGlyph, success);
        x = lastGlyph - 2;

        while (x >= firstGlyph) {
            glyphStorage[x + 2] = glyphStorage[x];
            ix = glyphStorage.getCharIndex(x, success);
            glyphStorage.setCharIndex(x + 2, ix, success);
            x -= 1;
        }

        glyphStorage[firstGlyph] = c;
        glyphStorage[firstGlyph + 1] = d;

        glyphStorage.setCharIndex(firstGlyph, ic, success);
        glyphStorage.setCharIndex(firstGlyph + 1, id, success);
        break;

    case irvDCx:
        c = glyphStorage[lastGlyph - 1];
        d = glyphStorage[lastGlyph];
        ic = glyphStorage.getCharIndex(lastGlyph - 1, success);
        id = glyphStorage.getCharIndex(lastGlyph, success);
        x = lastGlyph - 2;

        while (x >= firstGlyph) {
            glyphStorage[x + 2] = glyphStorage[x];
            ix = glyphStorage.getCharIndex(x, success);
            glyphStorage.setCharIndex(x + 2, ix, success);
            x -= 1;
        }

        glyphStorage[firstGlyph] = d;
        glyphStorage[firstGlyph + 1] = c;

        glyphStorage.setCharIndex(firstGlyph, id, success);
        glyphStorage.setCharIndex(firstGlyph + 1, ic, success);
        break;

    case irvCDxA:
        a = glyphStorage[firstGlyph];
        c = glyphStorage[lastGlyph - 1];
        d = glyphStorage[lastGlyph];
        ia = glyphStorage.getCharIndex(firstGlyph, success);
        ic = glyphStorage.getCharIndex(lastGlyph - 1, success);
        id = glyphStorage.getCharIndex(lastGlyph, success);
        x = lastGlyph - 2;

        while (x > firstGlyph) {
            glyphStorage[x + 1] = glyphStorage[x];
            ix = glyphStorage.getCharIndex(x, success);
            glyphStorage.setCharIndex(x + 1, ix, success);
            x -= 1;
        }

        glyphStorage[firstGlyph] = c;
        glyphStorage[firstGlyph + 1] = d;
        glyphStorage[lastGlyph] = a;

        glyphStorage.setCharIndex(firstGlyph, ic, success);
        glyphStorage.setCharIndex(firstGlyph + 1, id, success);
        glyphStorage.setCharIndex(lastGlyph, ia, success);
        break;

    case irvDCxA:
        a = glyphStorage[firstGlyph];
        c = glyphStorage[lastGlyph - 1];
        d = glyphStorage[lastGlyph];
        ia = glyphStorage.getCharIndex(firstGlyph, success);
        ic = glyphStorage.getCharIndex(lastGlyph - 1, success);
        id = glyphStorage.getCharIndex(lastGlyph, success);
        x = lastGlyph - 2;

        while (x > firstGlyph) {
            glyphStorage[x + 1] = glyphStorage[x];
            ix = glyphStorage.getCharIndex(x, success);
            glyphStorage.setCharIndex(x + 1, ix, success);
            x -= 1;
        }

        glyphStorage[firstGlyph] = d;
        glyphStorage[firstGlyph + 1] = c;
        glyphStorage[lastGlyph] = a;

        glyphStorage.setCharIndex(firstGlyph, id, success);
        glyphStorage.setCharIndex(firstGlyph + 1, ic, success);
        glyphStorage.setCharIndex(lastGlyph, ia, success);
        break;

    case irvDxAB:
        a = glyphStorage[firstGlyph];
        b = glyphStorage[firstGlyph + 1];
        d = glyphStorage[lastGlyph];
        ia = glyphStorage.getCharIndex(firstGlyph, success);
        ib = glyphStorage.getCharIndex(firstGlyph + 1, success);
        id = glyphStorage.getCharIndex(lastGlyph, success);
        x = firstGlyph + 2;

        while (x < lastGlyph) {
            glyphStorage[x - 2] = glyphStorage[x];
            ix = glyphStorage.getCharIndex(x, success);
            glyphStorage.setCharIndex(x - 2, ix, success);
            x += 1;
        }

        glyphStorage[firstGlyph] = d;
        glyphStorage[lastGlyph - 1] = a;
        glyphStorage[lastGlyph] = b;

        glyphStorage.setCharIndex(firstGlyph, id, success);
        glyphStorage.setCharIndex(lastGlyph - 1, ia, success);
        glyphStorage.setCharIndex(lastGlyph, ib, success);
        break;

    case irvDxBA:
        a = glyphStorage[firstGlyph];
        b = glyphStorage[firstGlyph + 1];
        d = glyphStorage[lastGlyph];
        ia = glyphStorage.getCharIndex(firstGlyph, success);
        ib = glyphStorage.getCharIndex(firstGlyph + 1, success);
        id = glyphStorage.getCharIndex(lastGlyph, success);
        x = firstGlyph + 2;

        while (x < lastGlyph) {
            glyphStorage[x - 2] = glyphStorage[x];
            ix = glyphStorage.getCharIndex(x, success);
            glyphStorage.setCharIndex(x - 2, ix, success);
            x += 1;
        }

        glyphStorage[firstGlyph] = d;
        glyphStorage[lastGlyph - 1] = b;
        glyphStorage[lastGlyph] = a;

        glyphStorage.setCharIndex(firstGlyph, id, success);
        glyphStorage.setCharIndex(lastGlyph - 1, ib, success);
        glyphStorage.setCharIndex(lastGlyph, ia, success);
        break;

    case irvCDxAB:
        a = glyphStorage[firstGlyph];
        b = glyphStorage[firstGlyph + 1];

        glyphStorage[firstGlyph] = glyphStorage[lastGlyph - 1];
        glyphStorage[firstGlyph + 1] = glyphStorage[lastGlyph];

        glyphStorage[lastGlyph - 1] = a;
        glyphStorage[lastGlyph] = b;

        ia = glyphStorage.getCharIndex(firstGlyph, success);
        ib = glyphStorage.getCharIndex(firstGlyph + 1, success);
        ic = glyphStorage.getCharIndex(lastGlyph - 1, success);
        id = glyphStorage.getCharIndex(lastGlyph, success);

        glyphStorage.setCharIndex(firstGlyph, ic, success);
        glyphStorage.setCharIndex(firstGlyph + 1, id, success);

        glyphStorage.setCharIndex(lastGlyph - 1, ia, success);
        glyphStorage.setCharIndex(lastGlyph, ib, success);
        break;

    case irvCDxBA:
        a = glyphStorage[firstGlyph];
        b = glyphStorage[firstGlyph + 1];

        glyphStorage[firstGlyph] = glyphStorage[lastGlyph - 1];
        glyphStorage[firstGlyph + 1] = glyphStorage[lastGlyph];

        glyphStorage[lastGlyph - 1] = b;
        glyphStorage[lastGlyph] = a;

        ia = glyphStorage.getCharIndex(firstGlyph, success);
        ib = glyphStorage.getCharIndex(firstGlyph + 1, success);
        ic = glyphStorage.getCharIndex(lastGlyph - 1, success);
        id = glyphStorage.getCharIndex(lastGlyph, success);

        glyphStorage.setCharIndex(firstGlyph, ic, success);
        glyphStorage.setCharIndex(firstGlyph + 1, id, success);

        glyphStorage.setCharIndex(lastGlyph - 1, ib, success);
        glyphStorage.setCharIndex(lastGlyph, ia, success);
        break;

    case irvDCxAB:
        a = glyphStorage[firstGlyph];
        b = glyphStorage[firstGlyph + 1];

        glyphStorage[firstGlyph] = glyphStorage[lastGlyph];
        glyphStorage[firstGlyph + 1] = glyphStorage[lastGlyph - 1];

        glyphStorage[lastGlyph - 1] = a;
        glyphStorage[lastGlyph] = b;

        ia = glyphStorage.getCharIndex(firstGlyph, success);
        ib = glyphStorage.getCharIndex(firstGlyph + 1, success);
        ic = glyphStorage.getCharIndex(lastGlyph - 1, success);
        id = glyphStorage.getCharIndex(lastGlyph, success);

        glyphStorage.setCharIndex(firstGlyph, id, success);
        glyphStorage.setCharIndex(firstGlyph + 1, ic, success);

        glyphStorage.setCharIndex(lastGlyph - 1, ia, success);
        glyphStorage.setCharIndex(lastGlyph, ib, success);
        break;

    case irvDCxBA:
        a = glyphStorage[firstGlyph];
        b = glyphStorage[firstGlyph + 1];

        glyphStorage[firstGlyph] = glyphStorage[lastGlyph];
        glyphStorage[firstGlyph + 1] = glyphStorage[lastGlyph - 1];

        glyphStorage[lastGlyph - 1] = b;
        glyphStorage[lastGlyph] = a;

        ia = glyphStorage.getCharIndex(firstGlyph, success);
        ib = glyphStorage.getCharIndex(firstGlyph + 1, success);
        ic = glyphStorage.getCharIndex(lastGlyph - 1, success);
        id = glyphStorage.getCharIndex(lastGlyph, success);

        glyphStorage.setCharIndex(firstGlyph, id, success);
        glyphStorage.setCharIndex(firstGlyph + 1, ic, success);

        glyphStorage.setCharIndex(lastGlyph - 1, ib, success);
        glyphStorage.setCharIndex(lastGlyph, ia, success);
        break;

    default:
        break;
    }
}

U_NAMESPACE_END
