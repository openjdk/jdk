/*
 * Copyright (c) 2003, 2018, Oracle and/or its affiliates. All rights reserved.
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
 */

package sun.font;

/* remember that the API requires a Font use a
 * consistent glyph id. for a code point, and this is a
 * problem if a particular strike uses native scaler sometimes
 * and the JDK scaler others. That needs to be dealt with somewhere, but
 * here we can just always get the same glyph code without
 * needing a strike.
 *
 * The C implementation would cache the results of anything up
 * to the maximum surrogate pair code point.
 * This implementation will not cache as much, since the storage
 * requirements are not justifiable. Even so it still can use up
 * to 216*256*4 bytes of storage per composite font. If an app
 * calls canDisplay on this range for all 20 composite fonts that's
 * over 1Mb of cached data. May need to employ WeakReferences if
 * this appears to cause problems.
 */

public class CompositeGlyphMapper extends CharToGlyphMapper {

    public static final int NBLOCKS = 216;
    public static final int BLOCKSZ = 256;
    public static final int MAXUNICODE = NBLOCKS*BLOCKSZ;


    CompositeFont font;
    CharToGlyphMapper[] slotMappers;
    int[][] glyphMaps;
    private boolean hasExcludes;

    public CompositeGlyphMapper(CompositeFont compFont) {
        font = compFont;
        initMapper();
        /* This is often false which saves the overhead of a
         * per-mapped char method call.
         */
        hasExcludes = compFont.exclusionRanges != null &&
                      compFont.maxIndices != null;
    }

    private void initMapper() {
        if (missingGlyph == CharToGlyphMapper.UNINITIALIZED_GLYPH) {
            if (glyphMaps == null) {
                glyphMaps = new int[NBLOCKS][];
            }
            slotMappers = new CharToGlyphMapper[font.numSlots];
            /* This requires that slot 0 is never empty. */
            missingGlyph = font.getSlotFont(0).getMissingGlyphCode();
            missingGlyph = font.compositeGlyphCode(0, missingGlyph);
        }
    }

    private int getCachedGlyphCode(int unicode) {
        if (unicode >= MAXUNICODE) {
            return UNINITIALIZED_GLYPH; // don't cache surrogates
        }
        int[] gmap;
        if ((gmap = glyphMaps[unicode >> 8]) == null) {
            return UNINITIALIZED_GLYPH;
        }
        return gmap[unicode & 0xff];
    }

    private void setCachedGlyphCode(int unicode, int glyphCode) {
        if (unicode >= MAXUNICODE) {
            return;     // don't cache surrogates
        }
        int index0 = unicode >> 8;
        if (glyphMaps[index0] == null) {
            glyphMaps[index0] = new int[BLOCKSZ];
            for (int i=0;i<BLOCKSZ;i++) {
                glyphMaps[index0][i] = UNINITIALIZED_GLYPH;
            }
        }
        glyphMaps[index0][unicode & 0xff] = glyphCode;
    }

    private CharToGlyphMapper getSlotMapper(int slot) {
        CharToGlyphMapper mapper = slotMappers[slot];
        if (mapper == null) {
            mapper = font.getSlotFont(slot).getMapper();
            slotMappers[slot] = mapper;
        }
        return mapper;
    }

    protected int convertToGlyph(int unicode) {
        return convertToGlyph(unicode, 0);
    }

    protected int convertToGlyph(int unicode, int variationSelector) {

        for (int slot = 0; slot < font.numSlots; slot++) {
            if (!hasExcludes || !font.isExcludedChar(slot, unicode)) {
                CharToGlyphMapper mapper = getSlotMapper(slot);
                int glyphCode = mapper.charToVariationGlyph(unicode, variationSelector);
                if (glyphCode != mapper.getMissingGlyphCode()) {
                    glyphCode = font.compositeGlyphCode(slot, glyphCode);
                    if (variationSelector == 0) {
                        setCachedGlyphCode(unicode, glyphCode);
                    }
                    return glyphCode;
                }
            }
        }
        return missingGlyph;
    }

    @Override
    public int charToVariationGlyph(int unicode, int variationSelector) {
        if (variationSelector == 0) {
            return charToGlyph(unicode);
        } else {
            int glyph = convertToGlyph(unicode, variationSelector);
            // Fallback to base glyph if variation was not found.
            return glyph != missingGlyph ? glyph : charToGlyph(unicode);
        }
    }

    public int getNumGlyphs() {
        int numGlyphs = 0;
        /* The number of glyphs in a composite is affected by
         * exclusion ranges and duplicates (ie the same code point is
         * mapped by two different fonts) and also whether or not to
         * count fallback fonts. A nearly correct answer would be very
         * expensive to generate. A rough ballpark answer would
         * just count the glyphs in all the slots. However this would
         * initialize mappers for all slots when they aren't necessarily
         * needed. For now just use the first slot as JDK 1.4 did.
         */
        for (int slot=0; slot<1 /*font.numSlots*/; slot++) {
           CharToGlyphMapper mapper = slotMappers[slot];
           if (mapper == null) {
               mapper = font.getSlotFont(slot).getMapper();
               slotMappers[slot] = mapper;
           }
           numGlyphs += mapper.getNumGlyphs();
        }
        return numGlyphs;
    }

    public int charToGlyph(int unicode) {

        int glyphCode = getCachedGlyphCode(unicode);
        if (glyphCode == UNINITIALIZED_GLYPH) {
            glyphCode = convertToGlyph(unicode);
        }
        return glyphCode;
    }

    public int charToGlyph(int unicode, int prefSlot) {
        if (prefSlot >= 0) {
            CharToGlyphMapper mapper = getSlotMapper(prefSlot);
            int glyphCode = mapper.charToGlyph(unicode);
            if (glyphCode != mapper.getMissingGlyphCode()) {
                return font.compositeGlyphCode(prefSlot, glyphCode);
            }
        }
        return charToGlyph(unicode);
    }

    public int charToGlyph(char unicode) {

        int glyphCode  = getCachedGlyphCode(unicode);
        if (glyphCode == UNINITIALIZED_GLYPH) {
            glyphCode = convertToGlyph(unicode);
        }
        return glyphCode;
    }

}
