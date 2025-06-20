/*
 * Copyright (c) 2006, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;

import javax.swing.JPanel;

/*
 * @test
 * @bug 6320502
 * @summary Display laid out text which substitutes invisible glyphs correctly.
 * @library /java/awt/regtesthelpers /test/lib
 * @build PassFailJFrame jtreg.SkippedException
 * @run main/manual TestGASPHint
 */

public class TestGASPHint extends JPanel {
    private static final String text = "\u0905\u0901\u0917\u094d\u0930\u0947\u091c\u093c\u0940";
    private static final Font font = getPhysicalFontForText(text, Font.PLAIN, 36);

    public static void main(String[] args) throws Exception {
        if (font == null) {
            throw new jtreg.SkippedException("No Devanagari font found. Test Skipped");
        }

        final String INSTRUCTIONS = """
                A short piece of Devanagari text should appear without any
                artifacts. In particular there should be no "empty rectangles"
                representing the missing glyph.

                If the above condition is true, press Pass, else Fail.""";

        PassFailJFrame.builder()
                .title("TestGASPHint Instruction")
                .instructions(INSTRUCTIONS)
                .columns(32)
                .splitUI(TestGASPHint::new)
                .build()
                .awaitAndCheck();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(200, 200);
    }

    @Override
    public void paint(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;

        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, getWidth(), getHeight());

        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                             RenderingHints.VALUE_TEXT_ANTIALIAS_GASP);

        g2d.setFont(font);
        g2d.setColor(Color.BLACK);
        g2d.drawString(text, 10, 50);
    }

    /*
     * Searches the available system fonts for a font which can display all the
     * glyphs in the input text correctly. Returns null, if not found.
     */
    private static Font getPhysicalFontForText(String text, int style, int size) {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        String[] names = ge.getAvailableFontFamilyNames();

        for (String n : names) {
            switch (n.toLowerCase()) {
                case "dialog":
                case "dialoginput":
                case "serif":
                case "sansserif":
                case "monospaced":
                    break;
                default:
                    Font f = new Font(n, style, size);
                    if (f.canDisplayUpTo(text) == -1) {
                        return f;
                    }
            }
        }
        return null;
    }
}
