/*
 * Copyright (c) 2000, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Color;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Panel;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.NumericShaper;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.util.HashMap;

/*
 * @test
 * @bug 4210199
 * @summary Draw a string with mixed ASCII digits and different scripts, applying
 *          different kinds of numeric shapers. Verify that the proper digits are affected.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual ShaperTest
 */

public class ShaperTest extends Panel {
    private static final String fontName = Font.DIALOG;
    private final TextLayout[][] layouts;
    private final String[] titles;
    private static final String text =
            "-123 (English) 456.00 (Arabic) \u0641\u0642\u0643 -789 (Thai) \u0e01\u0e33 01.23";

    public static void main(String[] args) throws Exception {
        final String INSTRUCTIONS = """
                A line of text containing mixed numeric and other text is drawn four times
                (Depending on the font/platform, some of the other text may not be visible).

                There are four runs of digits, '-123' at the front of the text, '456.00' after
                English text, '-789' after Arabic text, and '01.23' after Thai text.

                In the first line, all four runs of digits should be present as ASCII digits.

                In the second line, all four runs of digits should be Arabic digits
                (they may not be visible if the font does not support Arabic).

                In the third line, the initial run of digits (-123) and the one following the
                Arabic text (-789) should be Arabic, while the others should be ASCII.

                In the fourth line, only the digits following the Arabic text (-789) should be Arabic,
                and the others should be ASCII.

                Pass the test if this is true.""";

        PassFailJFrame.builder()
                .title("ShaperTest Instruction")
                .instructions(INSTRUCTIONS)
                .columns(45)
                .testUI(ShaperTest::createUI)
                .logArea(8)
                .build()
                .awaitAndCheck();
    }

    private static Frame createUI() {
        Frame frame = new Frame("ShaperTest Test UI");
        frame.add(new ShaperTest());
        frame.setSize(600, 400);
        return frame;
    }

    void dumpChars(char[] chars) {
        for (int i = 0; i < chars.length; ++i) {
            char c = chars[i];
            if (c < 0x7f) {
                System.out.print(c);
            } else {
                String n = Integer.toHexString(c);
                n = "0000".substring(n.length()) + n;
                System.out.print("0x" + n);
            }
        }
    }

    private ShaperTest() {
        setBackground(Color.WHITE);
        setForeground(Color.BLACK);

        Font textfont = new Font(fontName, Font.PLAIN, 12);
        PassFailJFrame.log("asked for: " + fontName + " and got: " + textfont.getFontName());
        setFont(textfont);

        Font font = new Font(fontName, Font.PLAIN, 18);
        PassFailJFrame.log("asked for: " + fontName + " and got: " + font.getFontName());

        FontRenderContext frc = new FontRenderContext(null, false, false);

        layouts = new TextLayout[5][2];

        HashMap<AttributedCharacterIterator.Attribute, Object> map = new HashMap<>();
        map.put(TextAttribute.FONT, font);
        layouts[0][0] = new TextLayout(text, map, frc);
        AttributedCharacterIterator iter = new AttributedString(text, map).getIterator();
        layouts[0][1] = new LineBreakMeasurer(iter, frc).nextLayout(Float.MAX_VALUE);

        NumericShaper arabic = NumericShaper.getShaper(NumericShaper.ARABIC);
        map.put(TextAttribute.NUMERIC_SHAPING, arabic);
        layouts[1][0] = new TextLayout(text, map, frc);
        iter = new AttributedString(text, map).getIterator();
        layouts[1][1] = new LineBreakMeasurer(iter, frc).nextLayout(Float.MAX_VALUE);

        NumericShaper contextualArabic = NumericShaper.getContextualShaper(NumericShaper.ARABIC, NumericShaper.ARABIC);
        map.put(TextAttribute.NUMERIC_SHAPING, contextualArabic);
        layouts[2][0] = new TextLayout(text, map, frc);
        iter = new AttributedString(text, map).getIterator();
        layouts[2][1] = new LineBreakMeasurer(iter, frc).nextLayout(Float.MAX_VALUE);

        NumericShaper contextualArabicASCII = NumericShaper.getContextualShaper(NumericShaper.ARABIC);
        map.put(TextAttribute.NUMERIC_SHAPING, contextualArabicASCII);
        layouts[3][0] = new TextLayout(text, map, frc);
        iter = new AttributedString(text, map).getIterator();
        layouts[3][1] = new LineBreakMeasurer(iter, frc).nextLayout(Float.MAX_VALUE);

        NumericShaper contextualAll = NumericShaper.getContextualShaper(NumericShaper.ALL_RANGES);
        map.put(TextAttribute.NUMERIC_SHAPING, contextualAll);
        layouts[4][0] = new TextLayout(text, map, frc);
        iter = new AttributedString(text, map).getIterator();
        layouts[4][1] = new LineBreakMeasurer(iter, frc).nextLayout(Float.MAX_VALUE);

        titles = new String[]{
                "plain -- all digits ASCII",
                "Arabic -- all digits Arabic",
                "contextual Arabic default Arabic -- only leading digits and digits following Arabic text are Arabic",
                "contextual Arabic default ASCII -- only digits following Arabic text are Arabic",
                "contextual all default ASCII -- leading digits english, others correspond to context"
        };
    }

    @Override
    public void paint(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;

        float x = 5;
        float y = 5;

        for (int i = 0; i < layouts.length; ++i) {
            y += 18;
            g2d.drawString(titles[i], x, y);
            y += 4;

            for (int j = 0; j < 2; ++j) {
                y += layouts[i][j].getAscent();
                layouts[i][j].draw(g2d, x, y);
                y += layouts[i][j].getDescent() + layouts[i][j].getLeading();
            }
        }
    }
}
