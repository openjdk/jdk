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
 * @bug 4088173
 * @summary This interactive test verifies that the XOR mode is not affecting
 *          the clearRect() call. The correct output looks like:
 *
 *          \      /
 *           \    /
 *                     The backgound is blue.
 *                     The lines outside the central rectangle are green.
 *                     The central rectangle is also blue (the result of clearRect())
 *           /    \
 *          /      \
 *
 * @key headful
 * @run main XORClearRect
 */

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.Frame;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Robot;

public class XORClearRect extends Panel {

    public static void main(String args[]) throws Exception {
        EventQueue.invokeAndWait(XORClearRect::createUI);
        try {
             Robot robot = new Robot();
             robot.waitForIdle();
             robot.delay(2000);
             Point p = frame.getLocationOnScreen();
             int pix = robot.getPixelColor(p.x + 100, p.y + 100).getRGB();
             if (pix != Color.blue.getRGB()) {
                 throw new RuntimeException("Not blue");
             }
        } finally {
            if (frame != null) {
                EventQueue.invokeAndWait(frame::dispose);
            }
        }
    }

    static volatile Frame frame;

    static void createUI() {
        frame = new Frame("XORClearRect");
        frame.setBackground(Color.blue);
        XORClearRect xor = new XORClearRect();
        frame.add(xor);
        frame.setSize(200,200);
        frame.setVisible(true);
    }

    public XORClearRect() {
       setBackground(Color.blue);
    }

    public void paint(Graphics g) {
        g.setColor(Color.green);
        g.drawLine(0,0,200,200);
        g.drawLine(0,200,200,0);
        g.setXORMode(Color.blue);
        g.clearRect(50,50,100,100); //expecting the rectangle to be filled
                                    // with the background color (blue)
    }
}
