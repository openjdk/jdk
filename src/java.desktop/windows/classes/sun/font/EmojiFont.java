/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.awt.*;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

public class EmojiFont extends Font2D {

    private Font2D symbol, emoji;
    private volatile boolean init;

    public EmojiFont() {
        handle = new Font2DHandle(this);
        fullName = "Emoji.plain";
        familyName = "Emoji";
        fontRank = JRE_RANK;
        style = Font.PLAIN;
    }

    private void init() {
        if (!init) {
            synchronized (this) {
                if (!init) {
                    SunFontManager fm = SunFontManager.getInstance();
                    symbol = fm.findFont2D("Segoe UI Symbol", Font.PLAIN, FontManager.NO_FALLBACK);
                    emoji = fm.findFont2D("Segoe UI Emoji", Font.PLAIN, FontManager.NO_FALLBACK);
                    init = true;
                }
            }
        }
    }

    @Override
    public void getStyleMetrics(float pointSize, float[] metrics, int offset) {
        init();
        if (emoji != null) {
            emoji.getStyleMetrics(pointSize, metrics, offset);
        } else if (symbol != null) {
            symbol.getStyleMetrics(pointSize, metrics, offset);
        } else {
            super.getStyleMetrics(pointSize, metrics, offset);
        }
    }

    @Override
    FontStrike createStrike(FontStrikeDesc desc) {
        init();
        return new Strike(desc);
    }

    @Override
    protected int getValidatedGlyphCode(int glyphCode) {
        init();
        Font2D slot = (glyphCode & 1) == 1 ? emoji : symbol;
        if (emoji != null) {
            int result = slot.getValidatedGlyphCode(glyphCode >>> 1);
            if (result != slot.getMissingGlyphCode()) {
                return glyphCode;
            }
        }
        return getMissingGlyphCode();
    }

    @Override
    CharToGlyphMapper getMapper() {
        init();
        if (mapper == null) {
            mapper = new Mapper();
        }
        return mapper;
    }

    @Override
    public boolean hasSupplementaryChars() {
        init();
        return (emoji != null && emoji.hasSupplementaryChars()) ||
                (symbol != null && symbol.hasSupplementaryChars());
    }

    @Override
    public int getNumGlyphs() {
        init();
        return Math.max(emoji != null ? emoji.getNumGlyphs() : 0,
                symbol != null ? symbol.getNumGlyphs() : 0);
    }

    @Override
    public boolean canDisplay(int cp) {
        return (Character.isEmoji(cp) || Character.isEmojiComponent(cp)) && getMapper().canDisplay(cp);
    }

    @Override
    public SlotInfo getSlotInfoForGlyph(int glyphCode) {
        SlotInfo info = ((glyphCode & 1) == 1 ? emoji : symbol)
                .getSlotInfoForGlyph(glyphCode >>> 1);
        info.slotShift++;
        return info;
    }

    private class Strike extends FontStrike {

        private final FontStrike symbol, emoji;

        private Strike(FontStrikeDesc desc) {
            this.desc = desc;
            this.disposer = new FontStrikeDisposer(EmojiFont.this, desc);
            symbol = EmojiFont.this.symbol == null ? null :
                    EmojiFont.this.symbol.getStrike(desc);
            emoji = EmojiFont.this.emoji == null ? null :
                    EmojiFont.this.emoji.getStrike(desc);
        }

        private FontStrike getStrikeForGlyph(int glyphCode) {
            return (glyphCode & 1) == 1 ? emoji : symbol;
        }

        @Override
        public int getNumGlyphs() {
            return EmojiFont.this.getNumGlyphs();
        }

        @Override
        StrikeMetrics getFontMetrics() {
            if (strikeMetrics == null) {
                StrikeMetrics compMetrics = new StrikeMetrics();
                if (emoji != null) {
                    compMetrics.merge(emoji.getFontMetrics());
                }
                if (symbol != null) {
                    compMetrics.merge(symbol.getFontMetrics());
                }
                strikeMetrics = compMetrics;
            }
            return strikeMetrics;
        }

        @Override
        void getGlyphImagePtrs(int[] glyphCodes, long[] images, int len) {
            for (int i = 0; i < len; i++) {
                images[i] = getGlyphImagePtr(glyphCodes[i]);
            }
        }

        @Override
        long getGlyphImagePtr(int glyphCode) {
            FontStrike strike = getStrikeForGlyph(glyphCode);
            return strike.getGlyphImagePtr(glyphCode >>> 1);
        }

        @Override
        void getGlyphImageBounds(int glyphCode, Point2D.Float pt, Rectangle result) {
            FontStrike strike = getStrikeForGlyph(glyphCode);
            strike.getGlyphImageBounds(glyphCode >>> 1, pt, result);
        }

        @Override
        Point2D.Float getGlyphMetrics(int glyphCode) {
            FontStrike strike = getStrikeForGlyph(glyphCode);
            return strike.getGlyphMetrics(glyphCode >>> 1);
        }

        @Override
        Point2D.Float getCharMetrics(char ch) {
            return getGlyphMetrics(getMapper().charToGlyph(ch));
        }

        @Override
        float getGlyphAdvance(int glyphCode) {
            FontStrike strike = getStrikeForGlyph(glyphCode);
            return strike.getGlyphAdvance(glyphCode >>> 1);
        }

        @Override
        float getCodePointAdvance(int cp) {
            return getGlyphAdvance(getMapper().charToGlyph(cp));
        }

        @Override
        Rectangle2D.Float getGlyphOutlineBounds(int glyphCode) {
            FontStrike strike = getStrikeForGlyph(glyphCode);
            return strike.getGlyphOutlineBounds(glyphCode >>> 1);
        }

        @Override
        GeneralPath getGlyphOutline(int glyphCode, float x, float y) {
            FontStrike strike = getStrikeForGlyph(glyphCode);
            GeneralPath path = strike.getGlyphOutline(glyphCode >>> 1, x, y);
            return path != null ? path : new GeneralPath();
        }

        @Override
        GlyphRenderData getGlyphRenderData(int glyphCode, float x, float y) {
            FontStrike strike = getStrikeForGlyph(glyphCode);
            return strike.getGlyphRenderData(glyphCode >>> 1, x, y);
        }
    }

    class Mapper extends CharToGlyphMapper {

        private final CharToGlyphMapper symbol, emoji;

        private Mapper() {
            symbol = EmojiFont.this.symbol == null ? null :
                    EmojiFont.this.symbol.getMapper();
            emoji = EmojiFont.this.emoji == null ? null :
                    EmojiFont.this.emoji.getMapper();
            if (EmojiFont.this.emoji != null) {
                missingGlyph = compositeGlyphCode(true, EmojiFont.this.emoji.getMissingGlyphCode());
            } else if (EmojiFont.this.symbol != null) {
                missingGlyph = compositeGlyphCode(false, EmojiFont.this.symbol.getMissingGlyphCode());
            }
        }

        private int compositeGlyphCode(boolean slot, int glyphCode) {
            return slot ? (glyphCode << 1) | 1 : glyphCode << 1;
        }

        private boolean getSlot(int unicode, int variationSelector) {
            return switch (variationSelector) {
                case 0xFE0F -> true;
                case 0xFE0E -> false;
                default -> Character.isEmojiPresentation(unicode);
            };
        }

        @Override
        public int getNumGlyphs() {
            return EmojiFont.this.getNumGlyphs();
        }

        @Override
        public int charToVariationGlyph(int unicode, int variationSelector) {
            boolean slot = getSlot(unicode, variationSelector);
            CharToGlyphMapper mapper = slot ? emoji : symbol;
            if (mapper != null) {
                int glyph = mapper.charToGlyph(unicode);
                if (glyph != mapper.getMissingGlyphCode()) {
                    return compositeGlyphCode(slot, glyph);
                }
            }
            mapper = slot ? symbol : emoji;
            if (mapper != null) {
                int glyph = mapper.charToGlyph(unicode);
                if (glyph != mapper.getMissingGlyphCode()) {
                    return compositeGlyphCode(!slot, glyph);
                }
            }
            return missingGlyph;
        }

        @Override
        public int charToGlyph(int unicode) {
            return charToVariationGlyph(unicode, 0);
        }

        @Override
        public int charToGlyph(char unicode) {
            return charToGlyph((int) unicode);
        }
    }
}
