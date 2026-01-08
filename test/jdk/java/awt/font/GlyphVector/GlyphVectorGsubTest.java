/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8353230
 * @summary Regression test for TrueType font GSUB substitutions.
 */

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.TextAttribute;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.Map;

public class GlyphVectorGsubTest {

    /**
     * <p>Font created for this test which contains two GSUB substitutions: a
     * "liga" ligature for "a" + "b" which requires that the ligature support
     * be enabled, and a "ccmp" ligature for an emoji sequence which does not
     * require that ligatures be explicitly enabled.
     *
     * <p>The following FontForge Python script was used to generate this font:
     *
     * <pre>
     * import fontforge
     * import base64
     *
     * def draw(glyph, width, height):
     *   pen = glyph.glyphPen()
     *   pen.moveTo((100, 100))
     *   pen.lineTo((100, 100 + height))
     *   pen.lineTo((100 + width, 100 + height))
     *   pen.lineTo((100 + width, 100))
     *   pen.closePath()
     *   glyph.draw(pen)
     *   pen = None
     *
     * font = fontforge.font()
     * font.encoding = 'UnicodeFull'
     * font.design_size = 16
     * font.em = 2048
     * font.ascent = 1638
     * font.descent = 410
     * font.familyname = 'Test'
     * font.fontname = 'Test'
     * font.fullname = 'Test'
     * font.copyright = ''
     * font.autoWidth(0, 0, 2048)
     *
     * font.addLookup('ligatures', 'gsub_ligature', (), (('liga',(('latn',('dflt')),)),))
     * font.addLookupSubtable('ligatures', 'sub1')
     *
     * font.addLookup('sequences', 'gsub_ligature', (), (('ccmp',(('latn',('dflt')),)),))
     * font.addLookupSubtable('sequences', 'sub2')
     *
     * space = font.createChar(0x20)
     * space.width = 600
     *
     * # create glyphs: a, b, ab
     *
     * for char in list('ab'):
     *   glyph = font.createChar(ord(char))
     *   draw(glyph, 400, 100)
     *   glyph.width = 600
     *
     * ab = font.createChar(-1, 'ab')
     * ab.addPosSub('sub1', ('a', 'b'))
     * draw(ab, 400, 400)
     * ab.width = 600
     *
     * # create glyphs for "woman" emoji sequence
     *
     * components = []
     * woman = '\U0001F471\U0001F3FD\u200D\u2640\uFE0F'
     * for char in list(woman):
     *   glyph = font.createChar(ord(char))
     *   draw(glyph, 400, 800)
     *   glyph.width = 600
     *   components.append(glyph.glyphname)
     *
     * del components[-1] # remove last
     * seq = font.createChar(-1, 'seq')
     * seq.addPosSub('sub2', components)
     * draw(seq, 400, 1200)
     * seq.width = 600
     *
     * # save font to file
     *
     * ttf = 'test.ttf'     # TrueType
     * t64 = 'test.ttf.txt' # TrueType Base64
     *
     * font.generate(ttf)
     *
     * with open(ttf, 'rb') as f1:
     *   encoded = base64.b64encode(f1.read())
     *   with open(t64, 'wb') as f2:
     *     f2.write(encoded)
     * </pre>
     */
    private static final String TTF_BYTES = "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";

    public static void main(String[] args) throws Exception {

        byte[] ttfBytes = Base64.getDecoder().decode(TTF_BYTES);
        ByteArrayInputStream ttfStream = new ByteArrayInputStream(ttfBytes);
        Font f1 = Font.createFont(Font.TRUETYPE_FONT, ttfStream).deriveFont(80f);

        // Test emoji sequence, using "ccmp" feature and ZWJ (zero-width joiner):
        // - person with blonde hair
        // - emoji modifier fitzpatrick type 4
        // - zero-width joiner
        // - female sign
        // - variation selector 16
        // Does not require the use of the TextAttribute.LIGATURES_ON attribute.
        char[] text1 = "\ud83d\udc71\ud83c\udffd\u200d\u2640\ufe0f".toCharArray();
        FontRenderContext frc = new FontRenderContext(null, true, true);
        GlyphVector gv1 = f1.layoutGlyphVector(frc, text1, 0, text1.length, 0);
        checkOneGlyph(gv1, text1, 12);

        // Test regular ligature, using "liga" feature: "ab" -> replacement
        // Requires the use of the TextAttribute.LIGATURES_ON attribute.
        char[] text2 = "ab".toCharArray();
        Font f2 = f1.deriveFont(Map.of(TextAttribute.LIGATURES, TextAttribute.LIGATURES_ON));
        GlyphVector gv2 = f2.layoutGlyphVector(frc, text2, 0, text2.length, 0);
        checkOneGlyph(gv2, text2, 11);
    }

    private static void checkOneGlyph(GlyphVector gv, char[] text, int expectedCode) {
        int glyphs = gv.getNumGlyphs();
        if (glyphs != 1) {
            throw new RuntimeException("Unexpected number of glyphs for text " +
                new String(text) + ": " + glyphs);
        }
        int code = gv.getGlyphCode(0);
        if (code != expectedCode) {
            throw new RuntimeException("Unexpected glyph code for text " +
                new String(text) + ": " + expectedCode + " != " + code);
        }
    }
}
