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
#include "IndicRearrangementProcessor2.h"
#include "LEGlyphStorage.h"
#include "LESwaps.h"

U_NAMESPACE_BEGIN

UOBJECT_DEFINE_RTTI_IMPLEMENTATION(IndicRearrangementProcessor2)

IndicRearrangementProcessor2::IndicRearrangementProcessor2(
      const LEReferenceTo<MorphSubtableHeader2> &morphSubtableHeader, LEErrorCode &success)
  : StateTableProcessor2(morphSubtableHeader, success), indicRearrangementSubtableHeader(morphSubtableHeader, success),
  entryTable(stHeader, success, entryTableOffset, LE_UNBOUNDED_ARRAY)
{
}

IndicRearrangementProcessor2::~IndicRearrangementProcessor2()
{
}

void IndicRearrangementProcessor2::beginStateTable()
{
    firstGlyph = 0;
    lastGlyph = 0;
}

le_uint16 IndicRearrangementProcessor2::processStateEntry(LEGlyphStorage &glyphStorage, le_int32 &currGlyph,
                                                          EntryTableIndex2 index, LEErrorCode &success)
{
    const IndicRearrangementStateEntry2 *entry = entryTable.getAlias(index, success);
    if (LE_FAILURE(success)) return 0; // TODO - what to return in bad state?
    le_uint16 newState = SWAPW(entry->newStateIndex); // index to the new state
    IndicRearrangementFlags  flags =  (IndicRearrangementFlags) SWAPW(entry->flags);

    if (currGlyph < 0 || currGlyph >= glyphStorage.getGlyphCount()) {
       success = LE_INDEX_OUT_OF_BOUNDS_ERROR;
       return 0;
    }

    if (flags & irfMarkFirst) {
        firstGlyph = currGlyph;
    }

    if (flags & irfMarkLast) {
        lastGlyph = currGlyph;
    }

    doRearrangementAction(glyphStorage, (IndicRearrangementVerb) (flags & irfVerbMask), success);

    if (!(flags & irfDontAdvance)) {
        currGlyph += dir;
    }

    return newState; // index to new state
}

void IndicRearrangementProcessor2::endStateTable()
{
}

void IndicRearrangementProcessor2::doRearrangementAction(LEGlyphStorage &glyphStorage, IndicRearrangementVerb verb, LEErrorCode &success) const
{
    LEGlyphID a, b, c, d;
    le_int32 ia, ib, ic, id, ix, x;

    if (LE_FAILURE(success)) return;

    if (verb == irvNoAction) {
        return;
    }
    if (firstGlyph > lastGlyph) {
        success = LE_INDEX_OUT_OF_BOUNDS_ERROR;
        return;
    }

    switch(verb)
    {
    case irvxA:
        if (firstGlyph == lastGlyph) break;
        if (firstGlyph + 1 < firstGlyph) {
            success = LE_INDEX_OUT_OF_BOUNDS_ERROR;
            break;
        }
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
        if (firstGlyph == lastGlyph) break;
        if (lastGlyph - 1 > lastGlyph) {
            success = LE_INDEX_OUT_OF_BOUNDS_ERROR;
            break;
        }
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
        if ((firstGlyph + 2 < firstGlyph) ||
            (lastGlyph - firstGlyph < 1)) { // difference == 1 is a no-op, < 1 is an error.
            success = LE_INDEX_OUT_OF_BOUNDS_ERROR;
            break;
        }
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
        if ((firstGlyph + 2 < firstGlyph) ||
            (lastGlyph - firstGlyph < 1)) {
            success = LE_INDEX_OUT_OF_BOUNDS_ERROR;
            break;
        }
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
        if ((lastGlyph - 2 > lastGlyph) ||
            (lastGlyph - firstGlyph < 1)) {
            success = LE_INDEX_OUT_OF_BOUNDS_ERROR;
            break;
        }
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
        if ((lastGlyph - 2 > lastGlyph) ||
            (lastGlyph - firstGlyph < 1)) {
            success = LE_INDEX_OUT_OF_BOUNDS_ERROR;
            break;
        }
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
        if ((lastGlyph - 2 > lastGlyph) ||
            (lastGlyph - firstGlyph < 2)) {
            success = LE_INDEX_OUT_OF_BOUNDS_ERROR;
            break;
        }
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
        if ((lastGlyph - 2 > lastGlyph) ||
            (lastGlyph - firstGlyph < 2)) {
            success = LE_INDEX_OUT_OF_BOUNDS_ERROR;
            break;
        }
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
        if ((firstGlyph + 2 < firstGlyph) ||
            (lastGlyph - firstGlyph < 2)) {
            success = LE_INDEX_OUT_OF_BOUNDS_ERROR;
            break;
        }
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
        if ((firstGlyph + 2 < firstGlyph) ||
            (lastGlyph - firstGlyph < 2)) {
            success = LE_INDEX_OUT_OF_BOUNDS_ERROR;
            break;
        }
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
        if (lastGlyph - firstGlyph < 3) {
            success = LE_INDEX_OUT_OF_BOUNDS_ERROR;
            break;
        }
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
        if (lastGlyph - firstGlyph < 3) {
            success = LE_INDEX_OUT_OF_BOUNDS_ERROR;
            break;
        }
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
        if (lastGlyph - firstGlyph < 3) {
            success = LE_INDEX_OUT_OF_BOUNDS_ERROR;
            break;
        }
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
        if (lastGlyph - firstGlyph < 3) {
            success = LE_INDEX_OUT_OF_BOUNDS_ERROR;
            break;
        }
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
