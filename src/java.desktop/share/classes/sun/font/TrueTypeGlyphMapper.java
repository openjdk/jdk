/*
 * Copyright (c) 2003, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.ByteBuffer;
import java.util.Locale;

public class TrueTypeGlyphMapper extends CharToGlyphMapper {

    TrueTypeFont font;
    CMap cmap;
    int numGlyphs;

    public TrueTypeGlyphMapper(TrueTypeFont font) {
        this.font = font;
        try {
            cmap = CMap.initialize(font);
        } catch (Exception e) {
            cmap = null;
        }
        if (cmap == null) {
            handleBadCMAP();
        }
        missingGlyph = 0; /* standard for TrueType fonts */
        ByteBuffer buffer = font.getTableBuffer(TrueTypeFont.maxpTag);
        if (buffer != null && buffer.capacity() >= 6) {
            numGlyphs = buffer.getChar(4); // offset 4 bytes in MAXP table.
        } else {
            handleBadCMAP();
        }
    }

    public int getNumGlyphs() {
        return numGlyphs;
    }

    private char getGlyphFromCMAP(int charCode) {
        try {
            char glyphCode = cmap.getGlyph(charCode);
            if (glyphCode < numGlyphs ||
                glyphCode >= FileFontStrike.INVISIBLE_GLYPHS) {
                return glyphCode;
            } else {
                if (FontUtilities.isLogging()) {
                    FontUtilities.logWarning(font + " out of range glyph id=" +
                             Integer.toHexString((int)glyphCode) +
                             " for char " + Integer.toHexString(charCode));
                }
                return (char)missingGlyph;
            }
        } catch(Exception e) {
            handleBadCMAP();
            return (char) missingGlyph;
        }
    }

    private char getGlyphFromCMAP(int charCode, int variationSelector) {
        if (variationSelector == 0) {
            return getGlyphFromCMAP(charCode);
        }
        try {
            char glyphCode = cmap.getVariationGlyph(charCode,
                                                    variationSelector);
            if (glyphCode < numGlyphs ||
                glyphCode >= FileFontStrike.INVISIBLE_GLYPHS) {
                return glyphCode;
            } else {
                if (FontUtilities.isLogging()) {
                    FontUtilities.logWarning(font + " out of range glyph id=" +
                         Integer.toHexString((int)glyphCode) +
                         " for char " + Integer.toHexString(charCode) +
                         " for vs " + Integer.toHexString(variationSelector));
                }
                return (char)missingGlyph;
            }
        } catch (Exception e) {
             handleBadCMAP();
             return (char) missingGlyph;
        }
    }

    private void handleBadCMAP() {
        if (FontUtilities.isLogging()) {
            FontUtilities.logSevere("Null Cmap for " + font +
                                    "substituting for this font");
        }

        SunFontManager.getInstance().deRegisterBadFont(font);
        /* The next line is not really a solution, but might
         * reduce the exceptions until references to this font2D
         * are gone.
         */
        cmap = CMap.theNullCmap;
    }

    public int charToGlyph(char unicode) {
        int glyph = getGlyphFromCMAP(unicode);
        return glyph;
    }

    public int charToGlyph(int unicode) {
        int glyph = getGlyphFromCMAP(unicode);
        return glyph;
    }

    @Override
    public int charToVariationGlyph(int unicode, int variationSelector) {
        int glyph = getGlyphFromCMAP(unicode, variationSelector);
        return glyph;
    }

    /* A pretty good heuristic is that the cmap we are using
     * supports 32 bit character codes.
     */
    boolean hasSupplementaryChars() {
        return
            cmap instanceof CMap.CMapFormat8 ||
            cmap instanceof CMap.CMapFormat10 ||
            cmap instanceof CMap.CMapFormat12;
    }
}
