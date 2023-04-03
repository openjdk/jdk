/*
 * Copyright (c) 2003, 2005, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Font;
import java.awt.Rectangle;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/*
 * performance:
 * it seems expensive that when using a composite font for
 * every char you have to find which "slot" can display it.
 * Just the fact that you need to check at all ..
 * A composite glyph code ducks this by encoding the slot into the
 * glyph code, but you still need to get from char to glyph code.
 */
public final class CompositeStrike extends FontStrike {

    private CompositeFont compFont;
    private FontStrike[] strikes;
    int numGlyphs = 0;

    CompositeStrike(CompositeFont font2D, FontStrikeDesc desc) {
        this.compFont = font2D;
        this.desc = desc;
        this.disposer = new FontStrikeDisposer(compFont, desc);
        if (desc.style != compFont.style) {
            algoStyle = true;
            if ((desc.style & Font.BOLD) == Font.BOLD &&
                ((compFont.style & Font.BOLD) == 0)) {
                boldness = 1.33f;
            }
            if ((desc.style & Font.ITALIC) == Font.ITALIC &&
                (compFont.style & Font.ITALIC) == 0) {
                italic = 0.7f;
            }
        }
        strikes = new FontStrike[compFont.numSlots];
    }

    /* do I need this (see Strike::compositeStrikeForGlyph) */
    FontStrike getStrikeForGlyph(int glyphCode) {
        return getStrikeForSlot(compFont.decodeSlot(glyphCode));
    }

    FontStrike getStrikeForSlot(int slot) {
        if (slot >= strikes.length) {
            slot = 0;
        }
        FontStrike strike = strikes[slot];
        if (strike == null) {
            strike =
                compFont.getSlotFont(slot).getStrike(desc);

            strikes[slot] = strike;
        }
        return strike;
    }

    public int getNumGlyphs() {
        return compFont.getNumGlyphs();
    }

    StrikeMetrics getFontMetrics() {
        if (strikeMetrics == null) {
            StrikeMetrics compMetrics = new StrikeMetrics();
            for (int s=0; s<compFont.numMetricsSlots; s++) {
                compMetrics.merge(getStrikeForSlot(s).getFontMetrics());
            }
            strikeMetrics = compMetrics;
        }
        return strikeMetrics;
    }


    /* Performance tweak: Slot 0 can often return all the glyphs
     * Note slot zero doesn't need to be masked.
     * Could go a step further and support getting a run of glyphs.
     * This would help many locales a little.
     *
     * Note that if a client constructs an invalid a composite glyph that
     * references an invalid slot, that the behaviour is currently
     * that this slot index falls through to CompositeFont.getSlotFont(int)
     * which will substitute a default font, from which to obtain the
     * strike. If its an invalid glyph code for a valid slot, then the
     * physical font for that slot will substitute the missing glyph.
     */
    void getGlyphImagePtrs(int[] glyphCodes, long[] images, int  len) {
        FontStrike strike = getStrikeForSlot(0);
        int numptrs = strike.getSlot0GlyphImagePtrs(glyphCodes, images, len,
                compFont.slotMask, compFont.slotShift);
        if (numptrs == len) {
            return;
        }
        for (int i=numptrs; i< len; i++) {
            strike = getStrikeForGlyph(glyphCodes[i]);
            images[i] = strike.getGlyphImagePtr(compFont.decodeGlyphCode(glyphCodes[i]));
        }
    }


    long getGlyphImagePtr(int glyphCode) {
        FontStrike strike = getStrikeForGlyph(glyphCode);
        return strike.getGlyphImagePtr(compFont.decodeGlyphCode(glyphCode));
    }

    void getGlyphImageBounds(int glyphCode, Point2D.Float pt, Rectangle result) {
        FontStrike strike = getStrikeForGlyph(glyphCode);
        strike.getGlyphImageBounds(compFont.decodeGlyphCode(glyphCode), pt, result);
    }

    Point2D.Float getGlyphMetrics(int glyphCode) {
        FontStrike strike = getStrikeForGlyph(glyphCode);
        return strike.getGlyphMetrics(compFont.decodeGlyphCode(glyphCode));
    }

    Point2D.Float getCharMetrics(char ch) {
        return getGlyphMetrics(compFont.getMapper().charToGlyph(ch));
    }

    float getGlyphAdvance(int glyphCode) {
        FontStrike strike = getStrikeForGlyph(glyphCode);
        return strike.getGlyphAdvance(compFont.decodeGlyphCode(glyphCode));
    }

    /* REMIND where to cache?
     * The glyph advance is already cached by physical strikes and that's a lot
     * of the work.
     * Also FontDesignMetrics maintains a latin char advance cache, so don't
     * cache advances here as apps tend to hold onto metrics objects when
     * performance is sensitive to it. Revisit this assumption later.
     */
    float getCodePointAdvance(int cp) {
        return getGlyphAdvance(compFont.getMapper().charToGlyph(cp));
    }

    Rectangle2D.Float getGlyphOutlineBounds(int glyphCode) {
        FontStrike strike = getStrikeForGlyph(glyphCode);
        return strike.getGlyphOutlineBounds(compFont.decodeGlyphCode(glyphCode));
    }

    GeneralPath getGlyphOutline(int glyphCode, float x, float y) {

        FontStrike strike = getStrikeForGlyph(glyphCode);
        GeneralPath path = strike.getGlyphOutline(compFont.decodeGlyphCode(glyphCode), x, y);
        if (path == null) {
            return new GeneralPath();
        } else {
            return path;
        }
    }

    GlyphRenderData getGlyphRenderData(int glyphCode, float x, float y) {
        FontStrike strike = getStrikeForGlyph(glyphCode);
        return strike.getGlyphRenderData(compFont.decodeGlyphCode(glyphCode), x, y);
    }
}
