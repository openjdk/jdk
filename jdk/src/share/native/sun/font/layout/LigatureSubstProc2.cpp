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
 * (C) Copyright IBM Corp and Others. 1998-2013 - All Rights Reserved
 *
 */

#include "LETypes.h"
#include "MorphTables.h"
#include "StateTables.h"
#include "MorphStateTables.h"
#include "SubtableProcessor2.h"
#include "StateTableProcessor2.h"
#include "LigatureSubstProc2.h"
#include "LEGlyphStorage.h"
#include "LESwaps.h"

U_NAMESPACE_BEGIN

#define ExtendedComplement(m) ((le_int32) (~((le_uint32) (m))))
#define SignBit(m) ((ExtendedComplement(m) >> 1) & (le_int32)(m))
#define SignExtend(v,m) (((v) & SignBit(m))? ((v) | ExtendedComplement(m)): (v))

UOBJECT_DEFINE_RTTI_IMPLEMENTATION(LigatureSubstitutionProcessor2)

LigatureSubstitutionProcessor2::LigatureSubstitutionProcessor2(const MorphSubtableHeader2 *morphSubtableHeader)
  : StateTableProcessor2(morphSubtableHeader)
{
    ligatureSubstitutionHeader = (const LigatureSubstitutionHeader2 *) morphSubtableHeader;
    ligActionOffset = SWAPL(ligatureSubstitutionHeader->ligActionOffset);
    componentOffset = SWAPL(ligatureSubstitutionHeader->componentOffset);
    ligatureOffset = SWAPL(ligatureSubstitutionHeader->ligatureOffset);

    entryTable = (const LigatureSubstitutionStateEntry2 *) ((char *) &stateTableHeader->stHeader + entryTableOffset);
}

LigatureSubstitutionProcessor2::~LigatureSubstitutionProcessor2()
{
}

void LigatureSubstitutionProcessor2::beginStateTable()
{
    m = -1;
}

le_uint16 LigatureSubstitutionProcessor2::processStateEntry(LEGlyphStorage &glyphStorage, le_int32 &currGlyph, EntryTableIndex2 index)
{
    const LigatureSubstitutionStateEntry2 *entry = &entryTable[index];
    le_uint16 nextStateIndex = SWAPW(entry->nextStateIndex);
    le_uint16 flags = SWAPW(entry->entryFlags);
    le_uint16 ligActionIndex = SWAPW(entry->ligActionIndex);

    if (flags & lsfSetComponent) {
        if (++m >= nComponents) {
            m = 0;
        }
        componentStack[m] = currGlyph;
    }

    ByteOffset actionOffset = flags & lsfPerformAction;

    if (actionOffset != 0) {
        const LigatureActionEntry *ap = (const LigatureActionEntry *) ((char *) &ligatureSubstitutionHeader->stHeader + ligActionOffset) + ligActionIndex;
        const TTGlyphID *ligatureTable = (const TTGlyphID *) ((char *) &ligatureSubstitutionHeader->stHeader + ligatureOffset);
        LigatureActionEntry action;
        le_int32 offset, i = 0;
        le_int32 stack[nComponents];
        le_int16 mm = -1;

        const le_uint16 *componentTable = (const le_uint16 *)((char *) &ligatureSubstitutionHeader->stHeader + componentOffset);

        do {
            le_uint32 componentGlyph = componentStack[m--]; // pop off

            action = SWAPL(*ap++);

            if (m < 0) {
                m = nComponents - 1;
            }

            offset = action & lafComponentOffsetMask;
            if (offset != 0) {

                i += SWAPW(componentTable[LE_GET_GLYPH(glyphStorage[componentGlyph]) + (SignExtend(offset, lafComponentOffsetMask))]);

                if (action & (lafLast | lafStore))  {
                    TTGlyphID ligatureGlyph = SWAPW(ligatureTable[i]);
                    glyphStorage[componentGlyph] = LE_SET_GLYPH(glyphStorage[componentGlyph], ligatureGlyph);
                    stack[++mm] = componentGlyph;
                    i = 0;
                } else {
                    glyphStorage[componentGlyph] = LE_SET_GLYPH(glyphStorage[componentGlyph], 0xFFFF);
                }
            }
        } while (!(action & lafLast));

        while (mm >= 0) {
            if (++m >= nComponents) {
                m = 0;
            }

            componentStack[m] = stack[mm--];
        }
    }

    if (!(flags & lsfDontAdvance)) {
        currGlyph += dir;
    }

    return nextStateIndex;
}

void LigatureSubstitutionProcessor2::endStateTable()
{
}

U_NAMESPACE_END
