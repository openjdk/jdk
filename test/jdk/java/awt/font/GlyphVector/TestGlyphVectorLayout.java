/*
 * Copyright (c) 2002, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.GlyphVector;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;

import javax.swing.JPanel;

/*
 * @test
 * @bug 4615017
 * @summary Display two GlyphVectors, and ensure they are of the same length.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual TestGlyphVectorLayout
 */

public class TestGlyphVectorLayout extends JPanel {
    private final Font font;
    private final FontRenderContext frc;
    private final String text;

    private GlyphVector aftergv;
    private Rectangle pbounds;
    private Rectangle2D vbounds;

    public static void main(String[] args) throws Exception {
        final String INSTRUCTIONS = """
                Two lines of text should appear, the top one with boxes
                (red and blue) around it.
                The two lines should be of the same length, and the boxes around the
                top line should 'fit' the text with no empty space between the end
                of the text and the box.

                Pass the test if this is true.""";

        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(45)
                .testUI(TestGlyphVectorLayout::new)
                .build()
                .awaitAndCheck();
    }

    private TestGlyphVectorLayout() {
        setBackground(Color.WHITE);
        font = new Font(Font.DIALOG, Font.PLAIN, 24);
        frc = new FontRenderContext(null, false, false);
        text = "this is a test of glyph vector";
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(550, 150);
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        Graphics2D g2d = (Graphics2D) g;

        float x = 50;
        float y = 50;
        AffineTransform oldtx = g2d.getTransform();
        g2d.translate(x, y);
        g2d.scale(1.5, 1.5);

        g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                             RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                             RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

        g2d.setColor(Color.BLACK);

        GlyphVector gv = font.createGlyphVector(frc, text); // new each time
        g2d.drawGlyphVector(gv, 0, 0);

        if (vbounds == null) {
            vbounds = gv.getVisualBounds();
            pbounds = gv.getPixelBounds(g2d.getFontRenderContext(), 0, 0);
            aftergv = gv;
        }
        g2d.drawGlyphVector(aftergv, 0, 30);

        g2d.setColor(Color.BLUE);
        g2d.draw(vbounds);

        g2d.setTransform(oldtx);
        g2d.setColor(Color.RED);
        g2d.draw(pbounds);
    }
}
