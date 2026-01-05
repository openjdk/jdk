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

/*
 * @test
 * @bug 4094059
 * @summary drawing to a subclass of canvas didn't draw to the correct location.
 * @key headful
 * @run main LineLocationTest
 */

import java.awt.AWTException;
import java.awt.Canvas;
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
import java.lang.reflect.InvocationTargetException;

public class LineLocationTest extends Frame {
    private DrawScreen screen;

    public void initialize() {
        setSize(400, 400);
        setLocationRelativeTo(null);
        setTitle("Line Location Test");
        Panel p = new Panel();
        screen = new DrawScreen();
        p.add(screen);
        p.setLocation(50, 50);
        p.setSize(300, 300);
        add(p);
        setBackground(Color.white);
        setForeground(Color.blue);
        setVisible(true);
    }

    public void requestCoordinates(Rectangle r) {
        Point location = screen.getLocationOnScreen();
        Dimension size = screen.getSize();
        r.setBounds(location.x, location.y, size.width, size.height);
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException, AWTException {
        LineLocationTest me = new LineLocationTest();
        EventQueue.invokeAndWait(me::initialize);
        try {
            Robot robot = new Robot();
            robot.delay(1000);
            Rectangle coords = new Rectangle();
            EventQueue.invokeAndWait(() -> {
                me.requestCoordinates(coords);
            });
            BufferedImage capture = robot.createScreenCapture(coords);
            robot.delay(2000);
            for (int y = 0; y < capture.getHeight(); y++) {
                for (int x = 0; x < capture.getWidth(); x++) {
                    int blue = Color.blue.getRGB();
                    if (capture.getRGB(x, y) == blue) {
                        throw new RuntimeException("Blue detected at " + x + ", " + y);
                    }
                }
            }
        } finally {
            EventQueue.invokeAndWait(me::dispose);
        }
    }
}

class DrawScreen extends Canvas {
    public Dimension getPreferredSize() {
        return new Dimension(300, 300);
    }

    public void paint(Graphics g) {
        g.setColor(Color.blue);
        g.drawLine(5, -3145583, 50, -3145583);
    }
}
