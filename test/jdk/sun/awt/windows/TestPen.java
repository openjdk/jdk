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
 * @bug 4277201
 * @summary verifies that invoking a fill on a brand new Graphics object
 *          does not stroke the shape in addition to filling it
 * @key headful
 */

/*
 * This test case tests for a problem with initializing GDI graphics
 * contexts (HDCs) where a pen is left installed in the graphics object
 * even though the AWT believes that there is no Pen installed.  The
 * result is that when you try to fill a shape, GDI will both fill and
 * stroke it.
*/

import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;

public class TestPen extends Panel {

    static volatile TestPen pen;
    static volatile Frame frame;

    public TestPen() {
        setForeground(Color.black);
        setBackground(Color.white);
    }

    public Dimension getPreferredSize() {
        return new Dimension(200, 200);
    }

    public void paint(Graphics g) {
        g.setColor(Color.green);
        g.fillOval(50, 50, 100, 100);
    }

   static void createUI() {
        frame = new Frame();
        pen = new TestPen();
        frame.add(pen);
        frame.pack();
        frame.setVisible(true);
    }

    public static void main(String argv[]) throws Exception {
        try {
            EventQueue.invokeAndWait(TestPen::createUI);
            Robot robot = new Robot();
            robot.waitForIdle();
            robot.delay(2000);
            Point p = pen.getLocationOnScreen();
            Dimension d = pen.getSize();
            Rectangle r = new Rectangle(p.x + 1, p.y + 1, d.width - 2, d.height - 2);
            BufferedImage bi = robot.createScreenCapture(r);
            int blackPixel = Color.black.getRGB();
            for (int y = 0; y < bi.getHeight(); y++ ) {
                for (int x = 0; x < bi.getWidth(); x++ ) {
                    if (bi.getRGB(x, y) == blackPixel) {
                        throw new RuntimeException("Black pixel !");
                    }
                }
            }
        } finally {
            if (frame != null) {
                EventQueue.invokeAndWait(frame::dispose);
            }
        }
    }
}
