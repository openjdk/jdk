/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.text.AttributedString;

import javax.swing.JPanel;

/*
 * @test
 * @bug 4221422
 * @summary Display several TextLayouts with various selections.
 *          All the selections should be between non-italic and italic text,
 *          and the top and bottom of the selection region should be horizontal.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual TestSelection
 */

public final class TestSelection extends JPanel {
    private static final float MARGIN = 20;

    public static void main(String[] args) throws Exception {
        final String INSTRUCTIONS = """
                Several TextLayouts are displayed along with selections.
                The selection regions should have horizontal top and bottom segments.

                If above condition is true, press Pass else Fail.""";

        PassFailJFrame.builder()
                .title("TestSelection Instruction")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .splitUI(TestSelection::new)
                .build()
                .awaitAndCheck();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(300, 300);
    }

    private float drawSelectionAndLayout(Graphics2D g2d,
                                         TextLayout layout,
                                         float y,
                                         int selStart,
                                         int selLimit) {
        Color selectionColor = Color.PINK;
        Color textColor = Color.BLACK;

        y += layout.getAscent();

        g2d.translate(MARGIN, y);
        Shape hl = layout.getLogicalHighlightShape(selStart, selLimit);
        g2d.setColor(selectionColor);
        g2d.fill(hl);
        g2d.setColor(textColor);
        layout.draw(g2d, 0, 0);
        g2d.translate(-MARGIN, -y);

        y += layout.getDescent() + layout.getLeading() + 10;
        return y;
    }

    @Override
    public void paint(Graphics g) {
        String text = "Hello world";

        Graphics2D g2d = (Graphics2D) g;
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, getWidth(), getHeight());

        AttributedString attrStr = new AttributedString(text);

        FontRenderContext frc = g2d.getFontRenderContext();

        final int midPoint = text.indexOf('w');
        final int selStart = midPoint / 2;
        final int selLimit = text.length() - selStart;
        final Font italic = new Font(Font.SANS_SERIF, Font.ITALIC, 24);

        float y = MARGIN;

        attrStr.addAttribute(TextAttribute.FONT, italic, 0, midPoint);
        TextLayout layout = new TextLayout(attrStr.getIterator(), frc);

        y = drawSelectionAndLayout(g2d, layout, y, selStart - 1, selLimit);
        y = drawSelectionAndLayout(g2d, layout, y, selStart, selLimit);
        y = drawSelectionAndLayout(g2d, layout, y, selStart + 1, selLimit);

        attrStr = new AttributedString(text);
        attrStr.addAttribute(TextAttribute.FONT,
                             italic, midPoint, text.length());
        layout = new TextLayout(attrStr.getIterator(), frc);

        y = drawSelectionAndLayout(g2d, layout, y, selStart, selLimit);

        attrStr = new AttributedString(text);
        attrStr.addAttribute(TextAttribute.FONT, italic, 0, midPoint);
        attrStr.addAttribute(TextAttribute.SIZE, 48f, midPoint, text.length());
        layout = new TextLayout(attrStr.getIterator(), frc);

        y = drawSelectionAndLayout(g2d, layout, y, selStart, selLimit);
    }
}
