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
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Panel;
import java.awt.Polygon;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.lang.reflect.InvocationTargetException;

/*
 * @test
 * @bug 4411814 4298688 4205762 4524760 4067534
 * @summary Check that Graphics rendering primitives function
 *          correctly when fed small and degenerate shapes
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual SmallPrimitives
 */


public class SmallPrimitives extends Panel {

    static String INSTRUCTIONS = """
            In the borderless frame next to this window there should be a
            set of tiny narrow blue polygons painted next to the green rectangles.
            If rectangle is vertical the corresponding polygon is painted to the right of it,
            if rectangle is horizontal the polygon is painted below it.
            The length of the polygon should be roughly the same as the length of the
            green rectangle next to it. If size is significantly different or any of the
            polygons is not painted press "Fail" otherwise press "Pass".
            Note: one may consider using screen magnifier to compare sizes.
            """;

    public void paint(Graphics g) {
        Dimension d = getSize();
        Polygon p;
        GeneralPath gp;

        g.setColor(Color.white);
        g.fillRect(0, 0, d.width, d.height);

        // Reposition for horizontal tests (below)
        g.translate(0, 20);

        // Reference shapes
        g.setColor(Color.green);
        g.fillRect(10, 7, 11, 1);
        g.fillRect(10, 17, 11, 2);
        g.fillRect(10, 27, 11, 1);
        g.fillRect(10, 37, 11, 1);
        g.fillRect(10, 47, 11, 2);
        g.fillRect(10, 57, 11, 2);
        g.fillRect(10, 67, 11, 1);
        g.fillRect(10, 77, 11, 2);
        g.fillRect(10, 87, 11, 1);
        g.fillRect(10, 97, 11, 1);
        g.fillRect(10, 107, 11, 1);
        g.fillRect(10, 117, 6, 1); g.fillRect(20, 117, 6, 1);

        // Potentially problematic test shapes
        g.setColor(Color.blue);
        g.drawRect(10, 10, 10, 0);
        g.drawRect(10, 20, 10, 1);
        g.drawRoundRect(10, 30, 10, 0, 0, 0);
        g.drawRoundRect(10, 40, 10, 0, 4, 4);
        g.drawRoundRect(10, 50, 10, 1, 0, 0);
        g.drawRoundRect(10, 60, 10, 1, 4, 4);
        g.drawOval(10, 70, 10, 0);
        g.drawOval(10, 80, 10, 1);
        p = new Polygon();
        p.addPoint(10, 90);
        p.addPoint(20, 90);
        g.drawPolyline(p.xpoints, p.ypoints, p.npoints);
        p = new Polygon();
        p.addPoint(10, 100);
        p.addPoint(20, 100);
        g.drawPolygon(p.xpoints, p.ypoints, p.npoints);
        ((Graphics2D) g).draw(new Line2D.Double(10, 110, 20, 110));
        gp = new GeneralPath();
        gp.moveTo(10, 120);
        gp.lineTo(15, 120);
        gp.moveTo(20, 120);
        gp.lineTo(25, 120);
        ((Graphics2D) g).draw(gp);

        // Polygon limit tests
        p = new Polygon();
        trypoly(g, p);
        p.addPoint(10, 120);
        trypoly(g, p);

        // Reposition for vertical tests (below)
        g.translate(20, -20);

        // Reference shapes
        g.setColor(Color.green);
        g.fillRect(7, 10, 1, 11);
        g.fillRect(17, 10, 2, 11);
        g.fillRect(27, 10, 1, 11);
        g.fillRect(37, 10, 1, 11);
        g.fillRect(47, 10, 2, 11);
        g.fillRect(57, 10, 2, 11);
        g.fillRect(67, 10, 1, 11);
        g.fillRect(77, 10, 2, 11);
        g.fillRect(87, 10, 1, 11);
        g.fillRect(97, 10, 1, 11);
        g.fillRect(107, 10, 1, 11);
        g.fillRect(117, 10, 1, 6); g.fillRect(117, 20, 1, 6);

        // Potentially problematic test shapes
        g.setColor(Color.blue);
        g.drawRect(10, 10, 0, 10);
        g.drawRect(20, 10, 1, 10);
        g.drawRoundRect(30, 10, 0, 10, 0, 0);
        g.drawRoundRect(40, 10, 0, 10, 4, 4);
        g.drawRoundRect(50, 10, 1, 10, 0, 0);
        g.drawRoundRect(60, 10, 1, 10, 4, 4);
        g.drawOval(70, 10, 0, 10);
        g.drawOval(80, 10, 1, 10);
        p = new Polygon();
        p.addPoint(90, 10);
        p.addPoint(90, 20);
        g.drawPolyline(p.xpoints, p.ypoints, p.npoints);
        p = new Polygon();
        p.addPoint(100, 10);
        p.addPoint(100, 20);
        g.drawPolygon(p.xpoints, p.ypoints, p.npoints);
        ((Graphics2D) g).draw(new Line2D.Double(110, 10, 110, 20));
        gp = new GeneralPath();
        gp.moveTo(120, 10);
        gp.lineTo(120, 15);
        gp.moveTo(120, 20);
        gp.lineTo(120, 25);
        ((Graphics2D) g).draw(gp);

        // Polygon limit tests
        p = new Polygon();
        trypoly(g, p);
        p.addPoint(110, 10);
        trypoly(g, p);

        // Reposition for oval tests
        g.translate(0, 20);

        for (int i = 0, xy = 8; i < 11; i++) {
            g.setColor(Color.green);
            g.fillRect(xy, 5, i, 1);
            g.fillRect(5, xy, 1, i);
            g.setColor(Color.blue);
            g.fillOval(xy, 8, i, 1);
            g.fillOval(8, xy, 1, i);
            xy += i + 2;
        }

        g.translate(10, 10);
        for (int i = 0, xy = 9; i < 6; i++) {
            g.setColor(Color.green);
            g.fillRect(xy, 5, i, 2);
            g.fillRect(5, xy, 2, i);
            g.setColor(Color.blue);
            g.fillOval(xy, 8, i, 2);
            g.fillOval(8, xy, 2, i);
            xy += i + 2;
        }
    }

    public static void trypoly(Graphics g, Polygon p) {
        g.drawPolygon(p);
        g.drawPolygon(p.xpoints, p.ypoints, p.npoints);
        g.drawPolyline(p.xpoints, p.ypoints, p.npoints);
        g.fillPolygon(p);
        g.fillPolygon(p.xpoints, p.ypoints, p.npoints);
    }

    public Dimension getPreferredSize() {
        return new Dimension(150, 150);
    }

    public static Frame createFrame() {
        Frame f = new Frame();
        SmallPrimitives sp = new SmallPrimitives();
        sp.setLocation(0, 0);
        f.add(sp);
        f.setUndecorated(true);
        f.pack();
        return f;
    }

    public static void main(String argv[]) throws InterruptedException,
            InvocationTargetException {
        PassFailJFrame.builder()
                .title("Small Primitives Instructions")
                .instructions(INSTRUCTIONS)
                .columns(60)
                .testUI(SmallPrimitives::createFrame)
                .build()
                .awaitAndCheck();
    }
}
