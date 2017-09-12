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
 *
 * (C) Copyright IBM Corp. 2004-2010 - All Rights Reserved
 *
 */

#include "KernTable.h"
#include "LEFontInstance.h"
#include "LEGlyphStorage.h"

#include "LESwaps.h"
#include "OpenTypeUtilities.h"

#include <stdio.h>

#define DEBUG_KERN_TABLE 0

U_NAMESPACE_BEGIN

struct PairInfo {
  le_uint32 key;   // sigh, MSVC compiler gags on union here
  le_int16  value; // fword, kern value in funits
};
#define KERN_PAIRINFO_SIZE 6
LE_CORRECT_SIZE(PairInfo, KERN_PAIRINFO_SIZE)
struct Subtable_0 {
  le_uint16 nPairs;
  le_uint16 searchRange;
  le_uint16 entrySelector;
  le_uint16 rangeShift;
};
#define KERN_SUBTABLE_0_HEADER_SIZE 8
LE_CORRECT_SIZE(Subtable_0, KERN_SUBTABLE_0_HEADER_SIZE)

// Kern table version 0 only
struct SubtableHeader {
  le_uint16 version;
  le_uint16 length;
  le_uint16 coverage;
};
#define KERN_SUBTABLE_HEADER_SIZE 6
LE_CORRECT_SIZE(SubtableHeader, KERN_SUBTABLE_HEADER_SIZE)

// Version 0 only, version 1 has different layout
struct KernTableHeader {
  le_uint16 version;
  le_uint16 nTables;
};
#define KERN_TABLE_HEADER_SIZE 4
LE_CORRECT_SIZE(KernTableHeader, KERN_TABLE_HEADER_SIZE)

#define COVERAGE_HORIZONTAL 0x1
#define COVERAGE_MINIMUM 0x2
#define COVERAGE_CROSS 0x4
#define COVERAGE_OVERRIDE 0x8

/*
 * This implementation has support for only one subtable, so if the font has
 * multiple subtables, only the first will be used.  If this turns out to
 * be a problem in practice we should add it.
 *
 * This also supports only version 0 of the kern table header, only
 * Apple supports the latter.
 *
 * This implementation isn't careful about the kern table flags, and
 * might invoke kerning when it is not supposed to.  That too I'm
 * leaving for a bug fix.
 *
 * TODO: support multiple subtables
 * TODO: respect header flags
 */
KernTable::KernTable(const LETableReference& base, LEErrorCode &success)
  : pairsSwapped(NULL), fTable(base)
{
  if(LE_FAILURE(success) || (fTable.isEmpty())) {
#if DEBUG_KERN_TABLE
    fprintf(stderr, "no kern data\n");
#endif
    return;
  }
  LEReferenceTo<KernTableHeader> header(fTable, success);

#if DEBUG_KERN_TABLE
  // dump first 32 bytes of header
  for (int i = 0; i < 64; ++i) {
    fprintf(stderr, "%0.2x ", ((const char*)header.getAlias())[i]&0xff);
    if (((i+1)&0xf) == 0) {
      fprintf(stderr, "\n");
    } else if (((i+1)&0x7) == 0) {
      fprintf(stderr, "  ");
    }
  }
#endif

  if(LE_FAILURE(success)) return;

  if (!header.isEmpty() && header->version == 0 && SWAPW(header->nTables) > 0) {
    LEReferenceTo<SubtableHeader> subhead(header, success, KERN_TABLE_HEADER_SIZE);

    if (LE_SUCCESS(success) && !subhead.isEmpty() && subhead->version == 0) {
      coverage = SWAPW(subhead->coverage);
      if (coverage & COVERAGE_HORIZONTAL) { // only handle horizontal kerning
        LEReferenceTo<Subtable_0> table(subhead, success, KERN_SUBTABLE_HEADER_SIZE);

        if(table.isEmpty() || LE_FAILURE(success)) return;

        nPairs        = SWAPW(table->nPairs);

#if 0   // some old fonts have bad values here...
        searchRange   = SWAPW(table->searchRange);
        entrySelector = SWAPW(table->entrySelector);
        rangeShift    = SWAPW(table->rangeShift);
#else
        entrySelector = OpenTypeUtilities::highBit(nPairs);
        searchRange   = (1 << entrySelector) * KERN_PAIRINFO_SIZE;
        rangeShift    = (nPairs * KERN_PAIRINFO_SIZE) - searchRange;
#endif

        if(LE_SUCCESS(success) && nPairs>0) {
          // pairsSwapped is an instance member, and table is on the stack.
          // set 'pairsSwapped' based on table.getAlias(). This will range check it.

          pairsSwapped = (PairInfo*)(fTable.getFont()->getKernPairs());
          if (pairsSwapped == NULL) {
            LEReferenceToArrayOf<PairInfo>pairs =
              LEReferenceToArrayOf<PairInfo>(fTable, // based on overall table
                                             success,
                                             (const PairInfo*)table.getAlias(),  // subtable 0 + ..
                                             KERN_SUBTABLE_0_HEADER_SIZE,  // .. offset of header size
                                             nPairs); // count
            if (LE_SUCCESS(success) && pairs.isValid()) {
              pairsSwapped =  (PairInfo*)(malloc(nPairs*sizeof(PairInfo)));
              PairInfo *p = (PairInfo*)pairsSwapped;
              for (int i = 0; LE_SUCCESS(success) && i < nPairs; i++, p++) {
                memcpy(p, pairs.getAlias(i,success), KERN_PAIRINFO_SIZE);
                p->key = SWAPL(p->key);
              }
              fTable.getFont()->setKernPairs((void*)pairsSwapped); // store it
            }
          }
        }

#if 0
        fprintf(stderr, "coverage: %0.4x nPairs: %d pairs %p\n", coverage, nPairs, pairsSwapped);
        fprintf(stderr, "  searchRange: %d entrySelector: %d rangeShift: %d\n", searchRange, entrySelector, rangeShift);
        fprintf(stderr, "[[ ignored font table entries: range %d selector %d shift %d ]]\n", SWAPW(table->searchRange), SWAPW(table->entrySelector), SWAPW(table->rangeShift));
#endif
#if DEBUG_KERN_TABLE
        fprintf(stderr, "coverage: %0.4x nPairs: %d pairs 0x%x\n", coverage, nPairs, pairsSwapped);
        fprintf(stderr,
          "  searchRange(pairs): %d entrySelector: %d rangeShift(pairs): %d\n",
          searchRange, entrySelector, rangeShift);

        if (LE_SUCCESS(success)) {
          // dump part of the pair list
          char ids[256];
          for (int i = 256; --i >= 0;) {
            LEGlyphID id = font->mapCharToGlyph(i);
            if (id < 256) {
              ids[id] = (char)i;
            }
          }
          PairInfo *p = pairsSwapped;
          for (int i = 0; i < nPairs; ++i, p++) {
            le_uint32 k = p->key;
            le_uint16 left = (k >> 16) & 0xffff;
            le_uint16 right = k & 0xffff;
            if (left < 256 && right < 256) {
              char c = ids[left];
              if (c > 0x20 && c < 0x7f) {
                fprintf(stderr, "%c/", c & 0xff);
              } else {
                fprintf(stderr, "%0.2x/", c & 0xff);
              }
              c = ids[right];
              if (c > 0x20 && c < 0x7f) {
                fprintf(stderr, "%c ", c & 0xff);
              } else {
                fprintf(stderr, "%0.2x ", c & 0xff);
              }
            }
          }
        }
#endif
      }
    }
  }
}


/*
 * Process the glyph positions.  The positions array has two floats for each
 * glyph, plus a trailing pair to mark the end of the last glyph.
 */
void KernTable::process(LEGlyphStorage& storage, LEErrorCode &success)
{
  if(LE_FAILURE(success)) return;

  if (pairsSwapped) {
    success = LE_NO_ERROR;

    le_uint32 key = storage[0]; // no need to mask off high bits
    float adjust = 0;

    for (int i = 1, e = storage.getGlyphCount(); LE_SUCCESS(success)&&  i < e; ++i) {
      key = key << 16 | (storage[i] & 0xffff);

      // argh, to do a binary search, we need to have the pair list in sorted order
      // but it is not in sorted order on win32 platforms because of the endianness difference
      // so either I have to swap the element each time I examine it, or I have to swap
      // all the elements ahead of time and store them in the font

      const PairInfo* p = pairsSwapped;
      const PairInfo* tp = (const PairInfo*)(p + (rangeShift/KERN_PAIRINFO_SIZE)); /* rangeshift is in original table bytes */
      if (key > tp->key) {
        p = tp;
      }

#if DEBUG_KERN_TABLE
      fprintf(stderr, "binary search for %0.8x\n", key);
#endif

      le_uint32 probe = searchRange;
      while (probe > 1) {
        probe >>= 1;
        tp = (const PairInfo*)(p + (probe/KERN_PAIRINFO_SIZE));
        le_uint32 tkey = tp->key;
#if DEBUG_KERN_TABLE
        fprintf(stdout, "   %.3d (%0.8x)\n", (tp - pairsSwapped), tkey);
#endif
        if (tkey <= key) {
          if (tkey == key) {
            le_int16 value = SWAPW(tp->value);
#if DEBUG_KERN_TABLE
            fprintf(stdout, "binary found kerning pair %x:%x at %d, value: 0x%x (%g)\n",
                    storage[i-1], storage[i], i, value & 0xffff, font->xUnitsToPoints(value));
            fflush(stdout);
#endif
            // Have to undo the device transform.
            // REMIND either find a way to do this only if there is a
            // device transform, or a faster way, such as moving the
            // entire kern table up to Java.
            LEPoint pt;
            pt.fX = fTable.getFont()->xUnitsToPoints(value);
            pt.fY = 0;

            fTable.getFont()->getKerningAdjustment(pt);
            adjust += pt.fX;
            break;
          }
          p = tp;
        }
      }

      storage.adjustPosition(i, adjust, 0, success);
    }
    storage.adjustPosition(storage.getGlyphCount(), adjust, 0, success);
  }
}

U_NAMESPACE_END

