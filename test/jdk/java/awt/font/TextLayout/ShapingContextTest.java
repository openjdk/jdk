/*
 * Copyright (c) 2026, JetBrains s.r.o.. All rights reserved.
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

/*
 * @test
 * @summary JBR-10592 Checks that a shortened text is shaped the same way as a full text
 * @run junit/othervm -Dsun.font.layout.ffm=true ${test.main.class}
 * @run junit/othervm -Dsun.font.layout.ffm=false ${test.main.class}
 */

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SuppressWarnings("UnnecessaryUnicodeEscape")
public class ShapingContextTest {
    // The shape of each letter depends on both neighbours
    private static final String DUAL_JOINING_LETTERS = "\u0628\u064a\u062a";
    // This is used to add characters to the context without breaking the joining
    private static final char FATHA = '\u064e';

    private static final FontRenderContext FRC = new FontRenderContext(null, true, true);
    private static final char[] LONG_TEXT = DUAL_JOINING_LETTERS.repeat(20).toCharArray();
    private static final int START = 30;
    private static final int LIMIT = 36;
    private static final int CONTEXT = 5;
    private static final char[] UNCUT_TEXT = Arrays.copyOfRange(LONG_TEXT, START - CONTEXT, LIMIT + CONTEXT);

    private static Font font;

    @BeforeAll
    static void findFont() {
        font = Stream.concat(
                Stream.of(Font.DIALOG),
                Arrays.stream(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames())
        )
                .map(fontFamily -> new Font(fontFamily, Font.PLAIN, 20))
                .filter(f -> f.canDisplayUpTo(UNCUT_TEXT, 0, UNCUT_TEXT.length) == -1)
                // Some fonts like AAT fonts with morx on macOS or Arial on Windows are not joining letters
                .filter(f -> !glyphsWithPrefix(f, "\u0628").equals(glyphsWithPrefix(f, "")))
                .findFirst()
                .orElse(null);
        assumeTrue(font != null, "No font to test joining letters");
        System.out.println("Using font " + font.getFontName());
    }

    @Test
    void cutIsShapedAsUncut() {
        String uncut = glyphsUncut(font);
        String cut = glyphs(font, LONG_TEXT, START, LIMIT);
        assertEquals(uncut, cut, "Context should properly affect joining symbols");
    }

    @Test
    void checkHarfBuzzContextLength() {
        String oneCharLeft = String.valueOf(FATHA).repeat(CONTEXT - 1);
        String noCharLeft = String.valueOf(FATHA).repeat(CONTEXT);
        String errorMessage = "It seems that HarfBuzz context length is not " + CONTEXT + " code points. " +
                "Update HB_CONTEXT_LENGTH in SunLayoutEngine and this test.";
        assertNotEquals(glyphsWithPrefix(font, oneCharLeft), glyphsWithPrefix(font, "\u0628" + oneCharLeft), errorMessage);
        assertEquals(glyphsWithPrefix(font, noCharLeft), glyphsWithPrefix(font, "\u0628" + noCharLeft), errorMessage);
    }

    private static String glyphsUncut(Font font) {
        return glyphs(font, UNCUT_TEXT, CONTEXT, UNCUT_TEXT.length - CONTEXT);
    }

    private static String glyphsWithPrefix(Font font, String prefix) {
        char[] text = (prefix + DUAL_JOINING_LETTERS).toCharArray();
        return glyphs(font, text, prefix.length(), text.length);
    }

    /**
     * Lays out {@code text[start, limit)} and lists glyphs in the resulting part.
     */
    private static String glyphs(Font font, char[] text, int start, int limit) {
        GlyphVector gv = font.layoutGlyphVector(FRC, text, start, limit, Font.LAYOUT_RIGHT_TO_LEFT);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < gv.getNumGlyphs(); ++i) {
            int glyphCharIndex = gv.getGlyphCharIndex(i);
            sb.append(gv.getGlyphCode(i)).append('@').append(glyphCharIndex).append(' ');
        }
        return sb.toString().trim();
    }
}
