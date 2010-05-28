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
 * (C) Copyright IBM Corp. 2004-2005 - All Rights Reserved
 *
 */

#include "KernTable.h"
#include "LEFontInstance.h"
#include "LEGlyphStorage.h"

#include "LESwaps.h"

#include <stdio.h>

#define DEBUG 0

U_NAMESPACE_BEGIN

struct PairInfo {
  le_uint32 key;   // sigh, MSVC compiler gags on union here
  le_int16  value; // fword, kern value in funits
};
#define KERN_PAIRINFO_SIZE 6

struct Subtable_0 {
  le_uint16 nPairs;
  le_uint16 searchRange;
  le_uint16 entrySelector;
  le_uint16 rangeShift;
};
#define KERN_SUBTABLE_0_HEADER_SIZE 8

// Kern table version 0 only
struct SubtableHeader {
  le_uint16 version;
  le_uint16 length;
  le_uint16 coverage;
};
#define KERN_SUBTABLE_HEADER_SIZE 6

// Version 0 only, version 1 has different layout
struct KernTableHeader {
  le_uint16 version;
  le_uint16 nTables;
};
#define KERN_TABLE_HEADER_SIZE 4

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
KernTable::KernTable(const LEFontInstance* font, const void* tableData)
  : pairs(0), font(font)
{
  const KernTableHeader* header = (const KernTableHeader*)tableData;
  if (header == 0) {
#if DEBUG
    fprintf(stderr, "no kern data\n");
#endif
    return;
  }

#if DEBUG
  // dump first 32 bytes of header
  for (int i = 0; i < 64; ++i) {
    fprintf(stderr, "%0.2x ", ((const char*)tableData)[i]&0xff);
    if (((i+1)&0xf) == 0) {
      fprintf(stderr, "\n");
    } else if (((i+1)&0x7) == 0) {
      fprintf(stderr, "  ");
    }
  }
#endif

  if (header->version == 0 && SWAPW(header->nTables) > 0) {
    const SubtableHeader* subhead = (const SubtableHeader*)((char*)tableData + KERN_TABLE_HEADER_SIZE);
    if (subhead->version == 0) {
      coverage = SWAPW(subhead->coverage);
      if (coverage & COVERAGE_HORIZONTAL) { // only handle horizontal kerning
        const Subtable_0* table = (const Subtable_0*)((char*)subhead + KERN_SUBTABLE_HEADER_SIZE);
        nPairs = SWAPW(table->nPairs);
        searchRange = SWAPW(table->searchRange) / KERN_PAIRINFO_SIZE ;
        entrySelector = SWAPW(table->entrySelector);
        rangeShift = SWAPW(table->rangeShift) / KERN_PAIRINFO_SIZE;

        pairs = (PairInfo*)font->getKernPairs();
        if (pairs == NULL) {
            char *pairData = (char*)table + KERN_SUBTABLE_0_HEADER_SIZE;
            char *pptr = pairData;
            pairs =  (PairInfo*)(malloc(nPairs*sizeof(PairInfo)));
            PairInfo *p = (PairInfo*)pairs;
            for (int i = 0; i < nPairs; i++, pptr += KERN_PAIRINFO_SIZE, p++) {
              memcpy(p, pptr, KERN_PAIRINFO_SIZE);
              p->key = SWAPL(p->key);
            }
            font->setKernPairs((void*)pairs);
        }

#if DEBUG
        fprintf(stderr, "coverage: %0.4x nPairs: %d pairs 0x%x\n", coverage, nPairs, pairs);
        fprintf(stderr,
          "  searchRange(pairs): %d entrySelector: %d rangeShift(pairs): %d\n",
          searchRange, entrySelector, rangeShift);

        {
          // dump part of the pair list
          char ids[256];
          for (int i = 256; --i >= 0;) {
            LEGlyphID id = font->mapCharToGlyph(i);
            if (id < 256) {
              ids[id] = (char)i;
            }
          }
          PairInfo *p = pairs;
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
void KernTable::process(LEGlyphStorage& storage)
{
  if (pairs) {
    LEErrorCode success = LE_NO_ERROR;

    le_uint32 key = storage[0]; // no need to mask off high bits
    float adjust = 0;
    for (int i = 1, e = storage.getGlyphCount(); i < e; ++i) {
      key = key << 16 | (storage[i] & 0xffff);

      // argh, to do a binary search, we need to have the pair list in sorted order
      // but it is not in sorted order on win32 platforms because of the endianness difference
      // so either I have to swap the element each time I examine it, or I have to swap
      // all the elements ahead of time and store them in the font

      const PairInfo* p = pairs;
      const PairInfo* tp = (const PairInfo*)(p + rangeShift);
      if (key > tp->key) {
        p = tp;
      }

#if DEBUG
      fprintf(stderr, "binary search for %0.8x\n", key);
#endif

      le_uint32 probe = searchRange;
      while (probe > 1) {
        probe >>= 1;
        tp = (const PairInfo*)(p + probe);
        le_uint32 tkey = tp->key;
#if DEBUG
        fprintf(stdout, "   %.3d (%0.8x)\n", (tp - pairs), tkey);
#endif
        if (tkey <= key) {
          if (tkey == key) {
            le_int16 value = SWAPW(tp->value);
#if DEBUG
            fprintf(stdout, "binary found kerning pair %x:%x at %d, value: 0x%x (%g)\n",
                    storage[i-1], storage[i], i, value & 0xffff, font->xUnitsToPoints(value));
            fflush(stdout);
#endif
            // Have to undo the device transform.
            // REMIND either find a way to do this only if there is a
            // device transform, or a faster way, such as moving the
            // entire kern table up to Java.
            LEPoint pt;
            pt.fX = font->xUnitsToPoints(value);
            pt.fY = 0;

            font->getKerningAdjustment(pt);
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

