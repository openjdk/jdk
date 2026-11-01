/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

/**
 * @test
 * @bug 8378201
 * @key headful
 * @summary Verifies that WINDOW and FLIP_BACKBUFFER surfaces sharing the same X
 *          Window render and flip correctly
 * @run main/othervm FlipCoexistTest
 * @run main/othervm -Dsun.java2d.opengl=True FlipCoexistTest
 */
public final class FlipCoexistTest {

    private static final int SIZE = 200;
    private static final int TOLERANCE = 10;

    public static void main(String[] args) throws Exception {
        Frame f = new Frame("FlipCoexistTest");
        try {
            f.setUndecorated(true);
            f.setSize(SIZE, SIZE);
            f.setLocation(100, 100);
            f.setVisible(true);

            Robot robot = new Robot();
            robot.waitForIdle();
            robot.delay(1000);

            int w = f.getWidth();
            int h = f.getHeight();

            // Fill window RED via direct render (WINDOW surface)
            Graphics g = f.getGraphics();
            g.setColor(Color.RED);
            g.fillRect(0, 0, w, h);
            g.dispose();
            robot.waitForIdle();
            robot.delay(500);

            // Request flip if available, blit is also useful to cover
            f.createBufferStrategy(2);
            BufferStrategy bs = f.getBufferStrategy();

            // Render BLUE to back buffer, do not flip yet
            Graphics bg = bs.getDrawGraphics();
            bg.setColor(Color.BLUE);
            bg.fillRect(0, 0, w, h);
            bg.dispose();

            // Paint small GREEN rect via direct render
            g = f.getGraphics();
            g.setColor(Color.GREEN);
            g.fillRect(0, 0, 10, 10);
            g.dispose();
            robot.waitForIdle();
            robot.delay(500);

            // GREEN rect must be visible
            check(robot, f, 5, 5, Color.GREEN, "small rect");

            // RED must survive the context round-trip
            check(robot, f, w / 2, h / 2, Color.RED, "survived");

            // Show back buffer, BLUE must appear
            bs.show();

            robot.waitForIdle();
            robot.delay(500);
            check(robot, f, w / 2, h / 2, Color.BLUE, "flip");
        } finally {
            f.dispose();
        }
    }

    private static void check(Robot robot, Frame frame, int x, int y, Color exp,
                              String desc)
    {
        Point loc = frame.getLocationOnScreen();
        Color c = robot.getPixelColor(loc.x + x, loc.y + y);
        if (!isAlmostEqual(c, exp)) {
            saveImage(robot, frame, desc);
            throw new RuntimeException("%s: %s != %s".formatted(desc, exp, c));
        }
    }

    private static void saveImage(Robot r, Frame f, String name) {
        try {
            Rectangle rect = f.getBounds();
            BufferedImage img = r.createScreenCapture(rect);
            ImageIO.write(img, "png", new File(name + ".png"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean isAlmostEqual(Color c1, Color c2) {
        return Math.abs(c1.getRed() - c2.getRed()) <= TOLERANCE
                && Math.abs(c1.getGreen() - c2.getGreen()) <= TOLERANCE
                && Math.abs(c1.getBlue() - c2.getBlue()) <= TOLERANCE;
    }
}
