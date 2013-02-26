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
 * (C) Copyright IBM Corp. 1998-2013 - All Rights Reserved
 *
 */

#include "LETypes.h"
#include "MorphTables.h"
#include "StateTables.h"
#include "MorphStateTables.h"
#include "SubtableProcessor.h"
#include "StateTableProcessor.h"
#include "LigatureSubstProc.h"
#include "LEGlyphStorage.h"
#include "LESwaps.h"

U_NAMESPACE_BEGIN

#define ExtendedComplement(m) ((le_int32) (~((le_uint32) (m))))
#define SignBit(m) ((ExtendedComplement(m) >> 1) & (le_int32)(m))
#define SignExtend(v,m) (((v) & SignBit(m))? ((v) | ExtendedComplement(m)): (v))

UOBJECT_DEFINE_RTTI_IMPLEMENTATION(LigatureSubstitutionProcessor)

LigatureSubstitutionProcessor::LigatureSubstitutionProcessor(const MorphSubtableHeader *morphSubtableHeader)
  : StateTableProcessor(morphSubtableHeader)
{
    ligatureSubstitutionHeader = (const LigatureSubstitutionHeader *) morphSubtableHeader;
    ligatureActionTableOffset = SWAPW(ligatureSubstitutionHeader->ligatureActionTableOffset);
    componentTableOffset = SWAPW(ligatureSubstitutionHeader->componentTableOffset);
    ligatureTableOffset = SWAPW(ligatureSubstitutionHeader->ligatureTableOffset);

    entryTable = (const LigatureSubstitutionStateEntry *) ((char *) &stateTableHeader->stHeader + entryTableOffset);
}

LigatureSubstitutionProcessor::~LigatureSubstitutionProcessor()
{
}

void LigatureSubstitutionProcessor::beginStateTable()
{
    m = -1;
}

ByteOffset LigatureSubstitutionProcessor::processStateEntry(LEGlyphStorage &glyphStorage, le_int32 &currGlyph, EntryTableIndex index)
{
    const LigatureSubstitutionStateEntry *entry = &entryTable[index];
    ByteOffset newState = SWAPW(entry->newStateOffset);
    le_int16 flags = SWAPW(entry->flags);

    if (flags & lsfSetComponent) {
        if (++m >= nComponents) {
            m = 0;
        }

        componentStack[m] = currGlyph;
    } else if ( m == -1) {
        // bad font- skip this glyph.
        currGlyph++;
        return newState;
    }

    ByteOffset actionOffset = flags & lsfActionOffsetMask;

    if (actionOffset != 0) {
        const LigatureActionEntry *ap = (const LigatureActionEntry *) ((char *) &ligatureSubstitutionHeader->stHeader + actionOffset);
        LigatureActionEntry action;
        le_int32 offset, i = 0;
        le_int32 stack[nComponents];
        le_int16 mm = -1;

        do {
            le_uint32 componentGlyph = componentStack[m--];

            action = SWAPL(*ap++);

            if (m < 0) {
                m = nComponents - 1;
            }

            offset = action & lafComponentOffsetMask;
            if (offset != 0) {
                const le_int16 *offsetTable = (const le_int16 *)((char *) &ligatureSubstitutionHeader->stHeader + 2 * SignExtend(offset, lafComponentOffsetMask));
                const le_int16 *tableEnd = (const le_int16 *)((char *) &ligatureSubstitutionHeader + 1 * SWAPW(ligatureSubstitutionHeader->length));

                // Check if the font is internally consistent
                if(tableEnd < (const le_int16*)&ligatureSubstitutionHeader  // stated end wrapped around?
                   || offsetTable > tableEnd) { // offset past end of stated length?
                  currGlyph++;
                  LE_DEBUG_BAD_FONT("off end of ligature substitution header");
                  return newState; // get out! bad font
                }

                if(componentGlyph > glyphStorage.getGlyphCount()) {
                  LE_DEBUG_BAD_FONT("preposterous componentGlyph");
                  currGlyph++;
                  return newState; // get out! bad font
                }
                i += SWAPW(offsetTable[LE_GET_GLYPH(glyphStorage[componentGlyph])]);

                if (action & (lafLast | lafStore))  {
                    const TTGlyphID *ligatureOffset = (const TTGlyphID *) ((char *) &ligatureSubstitutionHeader->stHeader + i);
                    TTGlyphID ligatureGlyph = SWAPW(*ligatureOffset);

                    glyphStorage[componentGlyph] = LE_SET_GLYPH(glyphStorage[componentGlyph], ligatureGlyph);
                    if(mm==nComponents) {
                      LE_DEBUG_BAD_FONT("exceeded nComponents");
                      mm--; // don't overrun the stack.
                    }
                    stack[++mm] = componentGlyph;
                    i = 0;
                } else {
                    glyphStorage[componentGlyph] = LE_SET_GLYPH(glyphStorage[componentGlyph], 0xFFFF);
                }
            }
#if LE_ASSERT_BAD_FONT
            if(m<0) {
              LE_DEBUG_BAD_FONT("m<0")
            }
#endif
        } while (!(action & lafLast)  && (m>=0) ); // stop if last bit is set, or if run out of items

        while (mm >= 0) {
            if (++m >= nComponents) {
                m = 0;
            }

            componentStack[m] = stack[mm--];
        }
    }

    if (!(flags & lsfDontAdvance)) {
        // should handle reverse too!
        currGlyph += 1;
    }

    return newState;
}

void LigatureSubstitutionProcessor::endStateTable()
{
}

U_NAMESPACE_END
