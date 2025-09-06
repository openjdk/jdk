/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 * @bug 8361381
 * @summary GlyphLayout behavior differs on JDK 11+ compared to JDK 8
 */

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.text.BreakIterator;
import java.util.Locale;

public class KhmerLineBreakTest {
    static String khmer = "បានស្នើសុំនៅតែត្រូវបានបដិសេធ";
    /*

    This is part of the output we get from `ExtendedTextSourceLabel::createCharinfo()`
    when running with `-Dsun.java2d.debugfonts=true`. It's a listing of the 28 code points
    of the `khmer` string defined above and displays their x-position during rendering as
    well as their advance. Code points with zero advance belong to the glyph cluster which
    is started by the first preceding code point with a non-zero advance. There should be no
    breaks at characters with zero advance, because this would break a glyph cluster.

     0 ch: 1794 x: 0.0       xa: 68.115234
     1 ch: 17b6 x: 68.115234 xa: 0.0
     2 ch: 1793 x: 68.115234 xa: 45.410156
     3 ch: 179f x: 113.52539 xa: 90.82031
     4 ch: 17d2 x: 204.3457  xa: 0.0
     5 ch: 1793 x: 204.3457  xa: 0.0
     6 ch: 17be x: 204.3457  xa: 0.0
     7 ch: 179f x: 204.3457  xa: 68.115234
     8 ch: 17bb x: 272.46094 xa: 0.0
     9 ch: 17c6 x: 272.46094 xa: 0.0
    10 ch: 1793 x: 272.46094 xa: 90.82031
    11 ch: 17c5 x: 363.28125 xa: 0.0
    12 ch: 178f x: 363.28125 xa: 68.115234
    13 ch: 17c2 x: 431.39648 xa: 0.0
    14 ch: 178f x: 431.39648 xa: 68.115234
    15 ch: 17d2 x: 499.51172 xa: 0.0
    16 ch: 179a x: 499.51172 xa: 0.0
    17 ch: 17bc x: 499.51172 xa: 0.0
    18 ch: 179c x: 499.51172 xa: 22.705078
    19 ch: 1794 x: 522.2168  xa: 68.115234
    20 ch: 17b6 x: 590.33203 xa: 0.0
    21 ch: 1793 x: 590.33203 xa: 45.410156
    22 ch: 1794 x: 635.7422  xa: 45.410156
    23 ch: 178a x: 681.15234 xa: 45.410156
    24 ch: 17b7 x: 726.5625  xa: 0.0
    25 ch: 179f x: 726.5625  xa: 90.82031
    26 ch: 17c1 x: 817.3828  xa: 0.0
    27 ch: 1792 x: 817.3828  xa: 45.410156

     */
    static boolean[] possibleBreak = new boolean[]
            { true, false, true, true, false, false, false, true, false, false,
              true, false, true, false, true, false, false, false, true, true,
              false, true, true, true, false, true, false, true, true /* */ };
    static Locale locale = new Locale.Builder().setLanguage("km").setRegion("KH").build();
    static BreakIterator breakIterator = BreakIterator.getLineInstance(locale);
    static FontRenderContext frc = new FontRenderContext(null, true, true);

    public static void main(String[] args) {
        Font[] allFonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();
        for (int i=0; i < allFonts.length; i++) {
            if (allFonts[i].canDisplayUpTo(khmer) == -1) {
                Font font = allFonts[i].deriveFont(Font.PLAIN, 60f);
                System.out.println("Trying font: " + font.getFontName());
                AttributedString attrStr = new AttributedString(khmer);
                attrStr.addAttribute(TextAttribute.FONT, font);
                AttributedCharacterIterator it = attrStr.getIterator();
                for (int width = 200; width < 400; width += 10) {
                    LineBreakMeasurer measurer = new LineBreakMeasurer(it, breakIterator, frc);
                    System.out.print(width + " : ");
                    while (measurer.getPosition() < it.getEndIndex()) {
                        int nextOffset = measurer.nextOffset(width);
                        System.out.print(nextOffset + " ");
                        if (!possibleBreak[nextOffset]) {
                            System.out.println();
                            throw new RuntimeException("Invalid break at offset " + nextOffset + " (width = " + width + " font = " + font.getFontName() + ")");
                        }
                        measurer.setPosition(nextOffset);
                    }
                    System.out.println();
                }
                System.out.println("OK");
            }
        }
    }
}
