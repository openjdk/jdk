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

import javax.imageio.ImageIO;
import java.awt.AWTException;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Label;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

/*
 * @test
 * @bug 4191297
 * @summary Tests that unreferenced GDI resources are correctly
 *          destroyed when no longer needed.
 * @key headful
 * @run main GDIResourceExhaustionTest
 */

public class GDIResourceExhaustionTest extends Frame {
    public void initUI() {
        setSize(200, 200);
        setUndecorated(true);
        setLocationRelativeTo(null);
        Panel labelPanel = new Panel();
        Label label = new Label("Red label");
        label.setBackground(Color.red);
        labelPanel.add(label);
        labelPanel.setLocation(20, 50);
        add(labelPanel);
        setVisible(true);
    }

    public void paint(Graphics graphics) {
        super.paint(graphics);
        for (int rgb = 0; rgb <= 0xfff; rgb++) {
            graphics.setColor(new Color(rgb));
            graphics.fillRect(0, 0, 5, 5);
        }
    }

    public void requestCoordinates(Rectangle r) {
        Insets insets = getInsets();
        Point location = getLocationOnScreen();
        Dimension size = getSize();
        r.x = location.x + insets.left;
        r.y = location.y + insets.top;
        r.width = size.width - (insets.left + insets.right);
        r.height = size.height - (insets.top + insets.bottom);
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException, AWTException, IOException {
        GDIResourceExhaustionTest test = new GDIResourceExhaustionTest();
        try {
            EventQueue.invokeAndWait(test::initUI);
            Robot robot = new Robot();
            robot.delay(2000);
            Rectangle coords = new Rectangle();
            EventQueue.invokeAndWait(() -> {
                test.requestCoordinates(coords);
            });
            robot.mouseMove(coords.x - 50, coords.y - 50);
            robot.waitForIdle();
            robot.delay(5000);
            BufferedImage capture = robot.createScreenCapture(coords);
            robot.delay(500);
            boolean redFound = false;
            int redRGB = Color.red.getRGB();
            for (int y = 0; y < capture.getHeight(); y++) {
                for (int x = 0; x < capture.getWidth(); x++) {
                    if (capture.getRGB(x, y) == redRGB) {
                        redFound = true;
                        break;
                    }
                    if (redFound) {
                        break;
                    }
                }
            }
            if (!redFound) {
                File errorImage = new File("screenshot.png");
                ImageIO.write(capture, "png", errorImage);
                throw new RuntimeException("Red label is not detected, possibly GDI resources exhausted");
            }
        } finally {
            EventQueue.invokeAndWait(test::dispose);
        }
    }
}
