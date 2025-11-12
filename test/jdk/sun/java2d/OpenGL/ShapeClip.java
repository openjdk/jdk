/*
 * Copyright (c) 2004, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 5002133
 * @key headful
 * @requires (os.family != "mac")
 * @summary Verifies that the OpenGL pipeline does not affect the color
 * buffer when setting up a complex (shape) clip region.  The test fails if
 * the circular clip region is filled with a green color (the green region
 * should not be visible at all).
 * @run main/othervm -Dsun.java2d.opengl=True ShapeClip
 * @run main/othervm ShapeClip
 */

/*
 * @test
 * @bug 5002133
 * @key headful
 * @requires (os.family == "mac")
 * @summary Verifies that the OpenGL pipeline does not affect the color
 * buffer when setting up a complex (shape) clip region.  The test fails if
 * the circular clip region is filled with a green color (the green region
 * should not be visible at all).
 * @run main/othervm -Dsun.java2d.opengl=True ShapeClip
 * @run main/othervm ShapeClip
 */

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class ShapeClip extends Panel {

    private static volatile Frame frame;
    private static volatile ShapeClip test;

    public void paint(Graphics g) {

        Graphics2D g2d = (Graphics2D)g;

        int width = getWidth();
        int height = getHeight();

        g2d.setColor(Color.black);
        g2d.fillRect(0, 0, width, height);

        g2d.setColor(Color.green);
        g2d.fillRect(0, 0, 1, 1);
        g2d.setClip(new Ellipse2D.Double(10, 10, 100, 100));
        g2d.setColor(Color.blue);
        g2d.fillRect(30, 30, 20, 20);
    }

    static void createUI() {
        test = new ShapeClip();
        frame = new Frame("OpenGL ShapeClip Test");
        frame.add(test);
        frame.setSize(200, 200);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();

        EventQueue.invokeAndWait(ShapeClip::createUI);

        robot.waitForIdle();
        robot.delay(2000);

        // Grab the screen region
        BufferedImage capture = null;
        try {
            Point pt1 = test.getLocationOnScreen();
            Rectangle rect = new Rectangle(pt1.x, pt1.y, 80, 80);
            capture = robot.createScreenCapture(rect);
        } finally {
            if (frame != null) {
                 EventQueue.invokeAndWait(frame::dispose);
            }
        }

        // Test blue rectangle
        int pixel1 = capture.getRGB(40, 40);
        if (pixel1 != 0xff0000ff) {
            saveImage(capture);
            throw new RuntimeException("Failed: Incorrect color for " +
                                       "rectangle " + Integer.toHexString(pixel1));
        }

        // Test clip region (should be same color as background)
        int pixel2 = capture.getRGB(60, 40);
        if (pixel2 != 0xff000000) {
            saveImage(capture);
            throw new RuntimeException("Failed: Incorrect color for " +
                                       "clip region " + Integer.toHexString(pixel2));
        }
    }

    static void saveImage(BufferedImage img) {
        try {
            File file = new File("capture.png");
            ImageIO.write(img, "png", file);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
