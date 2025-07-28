/*
 * Copyright (c) 2005, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;

import javax.swing.JPanel;

/*
 * @test
 * @bug 6426360
 * @summary Display a TextLayout with strikethrough at a number of
 *          different offsets relative to the pixel grid.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual TestStrikethrough
 */

public class TestStrikethrough extends JPanel {

    public static void main(String[] args) throws Exception {
        final String INSTRUCTIONS = """
                Display text with strikethrough at a number of different positions.

                Press Fail if any line is missing a strikethrough else press Pass.""";

        PassFailJFrame.builder()
                .title("TestStrikethrough Instruction")
                .instructions(INSTRUCTIONS)
                .columns(35)
                .splitUI(TestStrikethrough::new)
                .build()
                .awaitAndCheck();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(200, 120);
    }

    @Override
    public void paint(Graphics aContext) {
        Graphics2D g2d = (Graphics2D) aContext;

        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, getWidth(), getHeight());

        Font font = new Font(Font.DIALOG, Font.PLAIN, 9);
        FontRenderContext frc = g2d.getFontRenderContext();
        String str = "Where is the strikethrough?";
        AttributedString as = new AttributedString(str);
        as.addAttribute(TextAttribute.FONT, font);
        as.addAttribute(TextAttribute.STRIKETHROUGH, TextAttribute.STRIKETHROUGH_ON);
        AttributedCharacterIterator aci = as.getIterator();
        TextLayout tl = new TextLayout(aci, frc);
        float delta = (float) (Math.ceil(tl.getAscent() + tl.getDescent() + tl.getLeading()) + .1);
        float y = delta - .1f;
        g2d.setColor(Color.BLACK);
        for (int i = 0; i < 11; ++i) {
            tl.draw(g2d, 10f, y);
            y += delta;
        }
    }
}
