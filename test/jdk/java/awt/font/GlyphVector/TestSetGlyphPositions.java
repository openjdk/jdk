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
import java.awt.font.GlyphVector;
import java.awt.font.FontRenderContext;
import java.awt.geom.Point2D;

import javax.swing.JPanel;

/*
 * @test
 * @bug 4180379
 * @summary set the positions of glyphs in the GlyphVector to other than
 *          their default x, y positions, and verify that the rendered glyphs are
 *          in the new positions, not the default positions.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual TestSetGlyphPositions
 */

public class TestSetGlyphPositions extends JPanel {
    GlyphVector gv = null;

    public static void main(String[] args) throws Exception {
        final String INSTRUCTIONS = """
            'TopLeft text and >' should appear towards the top left of the frame,
            and '< and BottomRight text' should appear towards the bottom right.

            There should be some space between the '>' and '<' symbols, both vertically
            and horizontally.

            Pass the test if this is true.""";

        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(45)
                .testUI(TestSetGlyphPositions::new)
                .build()
                .awaitAndCheck();
    }

    public TestSetGlyphPositions() {
        setBackground(Color.WHITE);
        setSize(550, 150);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(550, 150);
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        Graphics2D g2d = (Graphics2D) g;

        if (gv == null) {
            Font font = new Font(Font.DIALOG, Font.PLAIN, 36);
            FontRenderContext frc = g2d.getFontRenderContext();
            String str = "TopLeft><BottomRight";

            gv = font.createGlyphVector(frc, str);
            for (int i = str.indexOf("<"); i < gv.getNumGlyphs(); ++i) {
                Point2D loc = gv.getGlyphPosition(i);
                loc.setLocation(loc.getX() + 50, loc.getY() + 50);
                gv.setGlyphPosition(i, loc);
            }
        }
        g2d.drawGlyphVector(gv, 50f, 50f);
    }
}
