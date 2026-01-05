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

/*
 * @test
 * @bug 4465509 4453725 4489667
 * @summary verify that fillPolygon completely fills area defined by drawPolygon
 * @key headful
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual PolygonFillTest
*/

import java.awt.Color;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Polygon;
import java.lang.reflect.InvocationTargetException;

public class PolygonFillTest extends Frame {
    Polygon poly;
    static String INSTRUCTIONS = """
                There should be two hourglass shapes drawn inside the window
                called "Polygon Fill Test". The outline should be blue
                and the interior should be green and there should be no gaps
                between the filled interior and the outline nor should the green
                filler spill outside the blue outline. You may need
                to use a screen magnifier to inspect the smaller shape
                on the left to verify that there are no gaps.

                If both polygons painted correctly press "Pass" otherwise press "Fail".
                """;

    public PolygonFillTest() {
        poly = new Polygon();
        poly.addPoint(0, 0);
        poly.addPoint(10, 10);
        poly.addPoint(0, 10);
        poly.addPoint(10, 0);
        setSize(300, 300);
        setTitle("Polygon Fill Test");
    }

    public void paint(Graphics g) {
        int w = getWidth();
        int h = getHeight();
        Image img = createImage(20, 20);
        Graphics g2 = img.getGraphics();
        drawPolys(g2, 20, 20, 5, 5);
        g2.dispose();
        drawPolys(g, w, h, (w / 4) - 5, (h / 2) - 5);
        g.drawImage(img, (3 * w / 4) - 40, (h / 2) - 40, 80, 80, null);
    }

    public void drawPolys(Graphics g, int w, int h, int x, int y) {
        g.setColor(Color.white);
        g.fillRect(0, 0, w, h);
        g.translate(x, y);
        g.setColor(Color.green);
        g.fillPolygon(poly);
        g.setColor(Color.blue);
        g.drawPolygon(poly);
        g.translate(-x, -y);
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        PassFailJFrame.builder()
                .title("Polygon Fill Instructions")
                .instructions(INSTRUCTIONS)
                .testUI(PolygonFillTest::new)
                .build()
                .awaitAndCheck();
    }
}
