/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.font.TextLayout;

import javax.swing.JPanel;

/*
 * @test
 * @bug 4427483
 * @summary Arabic text followed by newline should have no missing glyphs
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual ArabicBox
 */
public final class ArabicBox {

    private static final String TEXT =
            "\u0627\u0644\u0639\u0631\u0628\u064A\u0629\n";

    private static final String FONT_NAME = Font.DIALOG;

    private static final String INSTRUCTIONS = """
            In the below panel, you should see the following text:

            """
            + TEXT + """
            (It's \u2018Arabic\u2019 in Arabic.)

            If there are no 'box glyphs' for missing glyphs,
            press Pass; otherwise, press Fail.""";

    public static void main(String[] args) throws Exception {
        final Font font = new Font(FONT_NAME, Font.PLAIN, 24);
        System.out.println("asked for " + FONT_NAME + " and got: " + font.getFontName());

        PassFailJFrame.builder()
                      .title("Arabic Box")
                      .instructions(INSTRUCTIONS)
                      .rows(7)
                      .columns(40)
                      .splitUIBottom(() -> createPanel(font))
                      .build()
                      .awaitAndCheck();
    }

    private static JPanel createPanel(Font font) {
        return new TextPanel(font);
    }

    private static final class TextPanel extends JPanel {
        private TextLayout layout;

        private TextPanel(Font font) {
            setForeground(Color.black);
            setBackground(Color.white);
            setFont(font);
            setPreferredSize(new Dimension(300, 150));
        }

        @Override
        public void paint(Graphics g) {
            super.paint(g);
            Graphics2D g2d = (Graphics2D)g;
            if (layout == null) {
                Font font = g2d.getFont();
                FontRenderContext frc = g2d.getFontRenderContext();

                layout = new TextLayout(TEXT, font, frc);
                System.out.println(layout.getBounds());
            }

            layout.draw(g2d, 10, 50);
            g2d.drawString(TEXT, 10, 100);
        }
    }
}
