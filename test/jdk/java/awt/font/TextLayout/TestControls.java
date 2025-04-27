/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Panel;
import java.awt.ScrollPane;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;

/*
 * @test
 * @bug 4517298
 * @summary Display special control characters using both TextLayout.draw and
 *          Graphics.drawString. In no case should a missing glyph appear.
 *          Also display the advance of the control characters, in all cases
 *          these should be 0. The space character is also displayed as a reference.
 *          Note, the character is rendered between '><' but owing to the directional
 *          properties of two of the characters, the second '<' is rendered as '>'.
 *          This is correct behavior.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual TestControls
 */

public class TestControls {
    private static String fontName = Font.DIALOG;

    public static void main(String[] args) throws Exception {
        final String INSTRUCTIONS = """
                A number of control characters are displayed, one per line.
                Each line displays the hex value of the character, the character
                between '><' as rendered by TextLayout, the character between '><'
                as rendered by drawString, and the advance of the character.
                The first line renders the space character, as a reference.
                The following lines all render the controls.
                All controls should not render (even as space) and report a zero advance.

                Pass the test if this is true.

                Note: two of the control characters have the effect of changing the '<'
                following the control character so that it renders as '>'.
                This is not an error.""";

        PassFailJFrame.builder()
                .title("TestControls Instruction")
                .instructions(INSTRUCTIONS)
                .columns(45)
                .testUI(TestControls::createUI)
                .build()
                .awaitAndCheck();
    }

    private static Frame createUI() {
        Frame f = new Frame("TestControls Test UI");
        Panel panel = new ControlPanel(fontName);
        ScrollPane sp = new ScrollPane();
        sp.add("Center", panel);
        f.add(sp);
        f.setSize(450, 400);
        return f;
    }

    static class ControlPanel extends Panel {

        static final char[] chars = {
            (char)0x0020, (char)0x0009,
            (char)0x000A, (char)0x000D, (char)0x200C, (char)0x200D, (char)0x200E,
            (char)0x200F, (char)0x2028, (char)0x2029, (char)0x202A, (char)0x202B,
            (char)0x202C, (char)0x202D, (char)0x202E, (char)0x206A, (char)0x206B,
            (char)0x206C, (char)0x206D, (char)0x206E, (char)0x206F
        };

        ControlPanel(String fontName) {
            Font font = new Font(fontName, Font.PLAIN, 24);
            System.out.println("using font: " + font);
            setFont(font);
            setForeground(Color.BLACK);
            setBackground(Color.WHITE);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(400, 750);
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        public void paint(Graphics g) {
            Graphics2D g2d = (Graphics2D)g;
            FontRenderContext frc = g2d.getFontRenderContext();
            Font font = g2d.getFont();
            FontMetrics fm = g2d.getFontMetrics();
            Insets insets = getInsets();

            String jvmString = System.getProperty("java.version");
            String osString = System.getProperty("os.name") + " / " +
                System.getProperty("os.arch") + " / " +
                System.getProperty("os.version");

            int x = insets.left + 10;
            int y = insets.top;

            y += 30;
            g2d.drawString("jvm: " + jvmString, x, y);

            y += 30;
            g2d.drawString("os: " + osString, x, y);

            y += 30;
            g2d.drawString("font: " + font.getFontName(), x, y);

            for (int i = 0; i < chars.length; ++i) {
                String s = ">" + chars[i] + "<";
                x = insets.left + 10;
                y += 30;

                g2d.drawString(Integer.toHexString(chars[i]), x, y);
                x += 100;

                new TextLayout(s, font, frc).draw(g2d, x, y);
                x += 100;

                g2d.drawString(s, x, y);
                x += 100;

                g2d.drawString(Integer.toString(fm.charWidth(chars[i])), x, y);
            }
        }
    }
}
