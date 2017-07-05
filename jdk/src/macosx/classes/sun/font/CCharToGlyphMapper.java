/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

import java.util.HashMap;

public class CCharToGlyphMapper extends CharToGlyphMapper {
    private static native int countGlyphs(final long nativeFontPtr);

    private Cache cache = new Cache();
    CFont fFont;
    int numGlyphs = -1;

    public CCharToGlyphMapper(CFont font) {
        fFont = font;
        missingGlyph = 0; // for getMissingGlyphCode()
    }

    public int getNumGlyphs() {
        if (numGlyphs == -1) {
            numGlyphs = countGlyphs(fFont.getNativeFontPtr());
        }
        return numGlyphs;
    }

    public boolean canDisplay(char ch) {
        int glyph = charToGlyph(ch);
        return glyph != missingGlyph;
    }

    public boolean canDisplay(int cp) {
        int glyph = charToGlyph(cp);
        return glyph != missingGlyph;
    }

    public synchronized boolean charsToGlyphsNS(int count,
                                                char[] unicodes, int[] glyphs)
    {
        charsToGlyphs(count, unicodes, glyphs);

        // The following shaping checks are from either
        // TrueTypeGlyphMapper or Type1GlyphMapper
        for (int i = 0; i < count; i++) {
            int code = unicodes[i];

            if (code >= HI_SURROGATE_START && code <= HI_SURROGATE_END && i < count - 1) {
                char low = unicodes[i + 1];

                if (low >= LO_SURROGATE_START && low <= LO_SURROGATE_END) {
                    code = (code - HI_SURROGATE_START) * 0x400 + low - LO_SURROGATE_START + 0x10000;
                    glyphs[i + 1] = INVISIBLE_GLYPH_ID;
                }
            }

            if (code < 0x0590) {
                continue;
            } else if (code <= 0x05ff) {
                // Hebrew 0x0590->0x05ff
                return true;
            } else if (code >= 0x0600 && code <= 0x06ff) {
                // Arabic
                return true;
            } else if (code >= 0x0900 && code <= 0x0d7f) {
                // if Indic, assume shaping for conjuncts, reordering:
                // 0900 - 097F Devanagari
                // 0980 - 09FF Bengali
                // 0A00 - 0A7F Gurmukhi
                // 0A80 - 0AFF Gujarati
                // 0B00 - 0B7F Oriya
                // 0B80 - 0BFF Tamil
                // 0C00 - 0C7F Telugu
                // 0C80 - 0CFF Kannada
                // 0D00 - 0D7F Malayalam
                return true;
            } else if (code >= 0x0e00 && code <= 0x0e7f) {
                // if Thai, assume shaping for vowel, tone marks
                return true;
            } else if (code >= 0x200c && code <= 0x200d) {
                // zwj or zwnj
                return true;
            } else if (code >= 0x202a && code <= 0x202e) {
                // directional control
                return true;
            } else if (code >= 0x206a && code <= 0x206f) {
                // directional control
                return true;
            } else if (code >= 0x10000) {
                i += 1; // Empty glyph slot after surrogate
                continue;
            }
        }

        return false;
    }

    public synchronized int charToGlyph(char unicode) {
        final int glyph = cache.get(unicode);
        if (glyph != 0) return glyph;

        final char[] unicodeArray = new char[] { unicode };
        final int[] glyphArray = new int[1];

        nativeCharsToGlyphs(fFont.getNativeFontPtr(), 1, unicodeArray, glyphArray);
        cache.put(unicode, glyphArray[0]);

        return glyphArray[0];
    }

    public synchronized int charToGlyph(int unicode) {
        return charToGlyph((char)unicode);
    }

    public synchronized void charsToGlyphs(int count, char[] unicodes, int[] glyphs) {
        cache.get(count, unicodes, glyphs);
    }

    public synchronized void charsToGlyphs(int count, int[] unicodes, int[] glyphs) {
        final char[] unicodeChars = new char[count];
        for (int i = 0; i < count; i++) unicodeChars[i] = (char)unicodes[i];
        cache.get(count, unicodeChars, glyphs);
    }

    // This mapper returns either the glyph code, or if the character can be
    // replaced on-the-fly using CoreText substitution; the negative unicode
    // value. If this "glyph code int" is treated as an opaque code, it will
    // strike and measure exactly as a real glyph code - whether the character
    // is present or not. Missing characters for any font on the system will
    // be returned as 0, as the getMissingGlyphCode() function above indicates.
    private static native void nativeCharsToGlyphs(final long nativeFontPtr,
                                                   int count, char[] unicodes,
                                                   int[] glyphs);

    private class Cache {
        private static final int FIRST_LAYER_SIZE = 256;
        private static final int SECOND_LAYER_SIZE = 16384; // 16384 = 128x128

        private final int[] firstLayerCache = new int[FIRST_LAYER_SIZE];
        private SparseBitShiftingTwoLayerArray secondLayerCache;
        private HashMap<Integer, Integer> generalCache;

        Cache() {
            // <rdar://problem/5331678> need to prevent getting '-1' stuck in the cache
            firstLayerCache[1] = 1;
        }

        public int get(final char index) {
            if (index < FIRST_LAYER_SIZE) {
                // catch common glyphcodes
                return firstLayerCache[index];
            }

            if (index < SECOND_LAYER_SIZE) {
                // catch common unicodes
                if (secondLayerCache == null) return 0;
                return secondLayerCache.get(index);
            }

            if (generalCache == null) return 0;
            final Integer value = generalCache.get(new Integer(index));
            if (value == null) return 0;
            return value.intValue();
        }

        public void put(final char index, final int value) {
            if (index < FIRST_LAYER_SIZE) {
                // catch common glyphcodes
                firstLayerCache[index] = value;
                return;
            }

            if (index < SECOND_LAYER_SIZE) {
                // catch common unicodes
                if (secondLayerCache == null) {
                    secondLayerCache = new SparseBitShiftingTwoLayerArray(SECOND_LAYER_SIZE, 7); // 128x128
                }
                secondLayerCache.put(index, value);
                return;
            }

            if (generalCache == null) {
                generalCache = new HashMap<Integer, Integer>();
            }

            generalCache.put(new Integer(index), new Integer(value));
        }

        private class SparseBitShiftingTwoLayerArray {
            final int[][] cache;
            final int shift;
            final int secondLayerLength;

            public SparseBitShiftingTwoLayerArray(final int size,
                                                  final int shift)
            {
                this.shift = shift;
                this.cache = new int[1 << shift][];
                this.secondLayerLength = size >> shift;
            }

            public int get(final char index) {
                final int firstIndex = index >> shift;
                final int[] firstLayerRow = cache[firstIndex];
                if (firstLayerRow == null) return 0;
                return firstLayerRow[index - (firstIndex * (1 << shift))];
            }

            public void put(final char index, final int value) {
                final int firstIndex = index >> shift;
                int[] firstLayerRow = cache[firstIndex];
                if (firstLayerRow == null) {
                    cache[firstIndex] = firstLayerRow = new int[secondLayerLength];
                }
                firstLayerRow[index - (firstIndex * (1 << shift))] = value;
            }
        }

        public void get(int count, char[] indicies, int[] values){
            int missed = 0;
            for(int i = 0; i < count; i++){
                char code = indicies[i];

                final int value = get(code);
                if(value != 0){
                    values[i] = value;
                }else{
                    // zero this element out, because the caller does not
                    // promise to keep it clean
                    values[i] = 0;
                    missed++;
                }
            }

            if (missed == 0) return; // horray! everything is already cached!

            final char[] filteredCodes = new char[missed]; // all index codes requested (partially filled)
            final int[] filteredIndicies = new int[missed]; // local indicies into filteredCodes array (totally filled)

            // scan, mark, and store the index codes again to send into native
            int j = 0;
            int dupes = 0;
            for (int i = 0; i < count; i++){
                if (values[i] != 0L) continue; // already filled

                final char code = indicies[i];

                // we have already promised to fill this code - this is a dupe
                if (get(code) == -1){
                    filteredIndicies[j] = -1;
                    dupes++;
                    j++;
                    continue;
                }

                // this is a code we have not obtained before
                // mark this one as "promise to get" in the global cache with a -1
                final int k = j - dupes;
                filteredCodes[k] = code;
                put(code, -1);
                filteredIndicies[j] = k;
                j++;
            }

            final int filteredRunLen = j - dupes;
            final int[] filteredValues = new int[filteredRunLen];

            // bulk call to fill in the distinct values
            nativeCharsToGlyphs(fFont.getNativeFontPtr(), filteredRunLen, filteredCodes, filteredValues);

            // scan the requested list, and fill in values from our
            // distinct code list which has been filled from "getDistinct"
            j = 0;
            for (int i = 0; i < count; i++){
                if (values[i] != 0L && values[i] != -1L) continue; // already placed

                final int k = filteredIndicies[j]; // index into filteredImages array
                final char code = indicies[i];
                if(k == -1L){
                    // we should have already filled the cache with this value
                    values[i] = get(code);
                }else{
                    // fill the particular code request, and store in the cache
                    final int ptr = filteredValues[k];
                    values[i] = ptr;
                    put(code, ptr);
                }

                j++;
            }
        }
    }
}
