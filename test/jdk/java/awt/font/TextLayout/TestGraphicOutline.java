/*
 * Copyright (c) 2003, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GraphicAttribute;
import java.awt.font.ShapeGraphicAttribute;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;

import javax.swing.JPanel;

/*
 * @test
 * @bug 4915565 4920820 4920952
 * @summary Display graphics (circles) embedded in text, and draw both the outline (top)
 *          and black box bounds (bottom) of the result. The circles should each display at a
 *          different height. The outline and frames should approximately (within a pixel
 *          or two) surround each character.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual TestGraphicOutline
 */

public class TestGraphicOutline {

    public static void main(String[] args) throws Exception {
        final String INSTRUCTIONS = """
                Display graphics (circles) embedded in text, and draw both the
                outline (top) and black box bounds (bottom) of the result.

                The circles should each display at a different height.
                The outline and frames should approximately (within a pixel or two)
                surround each character.

                Pass the test if these conditions hold.

                'Black box bounds' is a term that refers to the bounding rectangles
                of each glyph, see the TextLayout API getBlackBoxBounds. It does not
                mean that the rendered outlines in the test are supposed to be black.
                The color of the outlines does not matter and is not part of the test
                conditions. Since there is no API for embedded graphics to return an
                outline that matches the shape of the graphics, the outlines of the
                graphics are their visual bounding boxes, which are rectangles.

                This is not an error. These outlines, as stated, should surround each
                character's graphic.""";

        PassFailJFrame.builder()
                .title("TestGraphicOutline Instruction")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(TestGraphicsPanel::new)
                .build()
                .awaitAndCheck();
    }

    private static final class TestGraphicsPanel extends JPanel {

        TextLayout tl;

        public TestGraphicsPanel() {
            setBackground(Color.white);
            setPreferredSize(new Dimension(650, 300));
            setName("2D Text");
        }

        @Override
        public void paint(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            int w = getSize().width;
            int h = getSize().height;

            g2.setColor(getBackground());
            g2.fillRect(0, 0, w, h);

            Font f1 = new Font(Font.SANS_SERIF, Font.BOLD, 60);
            Font f2 = new Font(Font.SERIF, Font.ITALIC, 80);
            String str = "The Starry Night ok?";

            AttributedString ats = new AttributedString(str);

            Shape s = new Ellipse2D.Float(0, -10, 12, 12);
            GraphicAttribute iga1 = new ShapeGraphicAttribute(s, GraphicAttribute.TOP_ALIGNMENT, false);
            GraphicAttribute iga2 = new ShapeGraphicAttribute(s, GraphicAttribute.HANGING_BASELINE, false);
            GraphicAttribute iga3 = new ShapeGraphicAttribute(s, GraphicAttribute.CENTER_BASELINE, false);
            GraphicAttribute iga4 = new ShapeGraphicAttribute(s, GraphicAttribute.ROMAN_BASELINE, false);
            GraphicAttribute iga5 = new ShapeGraphicAttribute(s, GraphicAttribute.BOTTOM_ALIGNMENT, false);

            ats.addAttribute(TextAttribute.CHAR_REPLACEMENT, iga1, 1, 2);
            ats.addAttribute(TextAttribute.CHAR_REPLACEMENT, iga2, 3, 4);
            ats.addAttribute(TextAttribute.CHAR_REPLACEMENT, iga3, 7, 8);
            ats.addAttribute(TextAttribute.CHAR_REPLACEMENT, iga4, 10, 11);
            ats.addAttribute(TextAttribute.CHAR_REPLACEMENT, iga5, 14, 15);
            ats.addAttribute(TextAttribute.FONT, f1, 0, 20);
            ats.addAttribute(TextAttribute.FONT, f2, 4, 10);
            AttributedCharacterIterator iter = ats.getIterator();

            FontRenderContext frc = g2.getFontRenderContext();
            tl = new TextLayout(iter, frc);
            Rectangle2D bounds = tl.getBounds();
            float sw = (float) bounds.getWidth();
            float sh = (float) bounds.getHeight();

            g2.translate((w - sw) / 2f, h / 2f - sh + tl.getAscent() - 2);

            g2.setColor(Color.blue);
            tl.draw(g2, 0, 0);
            g2.draw(bounds);

            g2.setColor(Color.black);
            Shape shape = tl.getOutline(null);
            g2.draw(shape);

            g2.translate(0, sh + 5);

            g2.setColor(Color.blue);
            tl.draw(g2, 0, 0);
            g2.draw(bounds);

            g2.setColor(Color.red);
            shape = tl.getBlackBoxBounds(0, tl.getCharacterCount());
            g2.draw(shape);
        }
    }
}
