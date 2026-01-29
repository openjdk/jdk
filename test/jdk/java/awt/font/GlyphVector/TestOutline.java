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
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.font.GlyphVector;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import javax.swing.JPanel;

/*
 * @test
 * @bug 4255072
 * @summary Display the outline of a GlyphVector that has overlapping 'O' characters in it.
 *          The places where the strokes of the characters cross should be filled in.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual TestOutline
 */

public final class TestOutline extends JPanel {
    Shape outline;

    public static void main(String[] args) throws Exception {
        final String INSTRUCTIONS = """
                Two overlapping 'O' characters should appear. Pass the test if
                the places where the strokes of the characters cross is filled in.
                Fail it if these places are not filled in.""";

        PassFailJFrame.builder()
                .title("TestOutline Instruction")
                .instructions(INSTRUCTIONS)
                .columns(35)
                .splitUI(TestOutline::new)
                .build()
                .awaitAndCheck();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(200, 250);
    }

    @Override
    public void paint(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, getWidth(), getHeight());

        if (outline == null) {
            outline = createLayout(g2d);
        }

        g2d.setColor(Color.BLACK);
        g2d.fill(outline);
    }

    private Shape createLayout(Graphics2D g2d) {
        Font font = new Font(Font.DIALOG, Font.PLAIN, 144);
        GlyphVector gv = font.createGlyphVector(g2d.getFontRenderContext(), "OO");
        gv.performDefaultLayout();
        Point2D pt = gv.getGlyphPosition(1);
        double delta = -pt.getX() / 2.0;
        pt.setLocation(pt.getX() + delta, pt.getY());
        gv.setGlyphPosition(1, pt);

        pt = gv.getGlyphPosition(2);
        pt.setLocation(pt.getX() + delta, pt.getY());
        gv.setGlyphPosition(2, pt);

        Rectangle2D bounds = gv.getLogicalBounds();
        Rectangle d = getBounds();
        float x = (float) ((d.width - bounds.getWidth()) / 2 + bounds.getX());
        float y = (float) ((d.height - bounds.getHeight()) / 2 - bounds.getY());
        System.out.println("loc: " + x + ", " + y);
        return gv.getOutline(x, y);
    }
}
