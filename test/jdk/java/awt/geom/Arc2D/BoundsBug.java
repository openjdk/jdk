/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 4197746
 * @summary Verifies that the getBounds2D method of Arc2D returns the
 *          correct result.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual BoundsBug
 */

import java.awt.Color;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Panel;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

public class BoundsBug {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
            This test displays three figures and draws the outline of their
            bounding boxes. The bounding boxes should correctly encompass
            the 3 figures.

            This test also paints two highlight rectangles at the ends of the
            angular extents of the arc. The two highlights should correctly
            appear at the outer circumference of the arc where the radii lines
            from its center intersect that circumference.
            """;

        PassFailJFrame.builder()
            .title("Test Instructions")
            .instructions(INSTRUCTIONS)
            .columns(40)
            .testUI(initialize())
            .build()
            .awaitAndCheck();
    }
    private static Frame initialize() {
        Frame f = new Frame("BoundsBug");
        ArcPanel panel = new ArcPanel();
        f.add(panel);
        f.setSize(300, 250);
        return f;
    }
}

class ArcPanel extends Panel {
    protected void drawPoint(Graphics2D g2, Point2D p) {
        g2.setColor(Color.green);
        g2.fill(new Rectangle2D.Double(p.getX() - 5, p.getY() - 5, 10, 10));
    }

    protected void drawShapeAndBounds(Graphics2D g2, Shape s) {
        g2.setColor(Color.orange);
        g2.fill(s);
        g2.setColor(Color.black);
        g2.draw(s);

        Rectangle2D r = s.getBounds2D();
        g2.setColor(Color.gray);
        g2.draw(r);
    }

    @Override
    public void paint(Graphics g) {
        Graphics2D g2 = (Graphics2D)g;
        g2.setColor(Color.white);
        g2.fill(g.getClipBounds());
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);

        // Create some interesting shapes.
        Ellipse2D ellipse = new Ellipse2D.Float(20, 40, 60, 80);
        Arc2D arc = new Arc2D.Float(60, 40, 100, 120,
            -30, -40, Arc2D.PIE);
        GeneralPath path = new GeneralPath(GeneralPath.WIND_EVEN_ODD);
        path.moveTo(0, 0);
        path.lineTo(75, -25);
        path.lineTo(25, 75);
        path.lineTo(0, 25);
        path.lineTo(100, 50);
        path.lineTo(50, 0);
        path.lineTo(25, 50);
        path.closePath();
        // Now draw them and their bounds rectangles.
        drawShapeAndBounds(g2, ellipse);
        drawShapeAndBounds(g2, arc);
        drawPoint(g2, arc.getStartPoint());
        drawPoint(g2, arc.getEndPoint());
        g2.translate(180, 65);
        drawShapeAndBounds(g2, path);
    }
}
