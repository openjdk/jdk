/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @key headful
 * @bug 6430601 8198613
 * @summary Verifies that copyArea() works properly when the
 *          destination parameters are outside the destination bounds.
 * @run main/othervm CopyAreaOOB
 */

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.awt.image.MultiResolutionImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.imageio.ImageIO;

public class CopyAreaOOB extends Canvas {
    private static Frame frame;
    private static Robot robot;
    private static BufferedImage captureImg;

    private static StringBuffer errorLog = new StringBuffer();

    private static final Point OFF_FRAME_LOC = new Point(50, 50);
    private static final int SIZE = 400;

    public static void main(String[] args) throws Exception {
        try {
            robot = new Robot();

            // added to move mouse pointer away from test UI
            // so that it is not captured in the screenshot
            robot.mouseMove(OFF_FRAME_LOC.x, OFF_FRAME_LOC.y);
            robot.waitForIdle();
            robot.delay(100);

            createTestUI();
            robot.delay(1000);

            if (!errorLog.isEmpty()) {
                saveImages();
                throw new RuntimeException("Test failed: \n" + errorLog.toString());
            }
        } finally {
            if (frame != null) {
                frame.dispose();
            }
        }
    }

    private static void createTestUI() {
        CopyAreaOOB canvas = new CopyAreaOOB();
        frame = new Frame();
        frame.setUndecorated(true);
        frame.add(canvas);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public void paint(Graphics g) {
        int w = getWidth();
        int h = getHeight();

        Graphics2D g2d = (Graphics2D)g;
        g2d.setColor(Color.black);
        g2d.fillRect(0, 0, w, h);

        g2d.setColor(Color.green);
        g2d.fillRect(0, 0, w, 10);

        g2d.setColor(Color.red);
        g2d.fillRect(0, 10, 50, h - 10);

        // copy the region such that part of it goes below the bottom of the
        // destination surface
        g2d.copyArea(0, 10, 50, h - 10, 60, 10);

        Toolkit.getDefaultToolkit().sync();

        robot.delay(500);

        Point pt1 = this.getLocationOnScreen();
        Rectangle rect = new Rectangle(pt1.x, pt1.y, SIZE, SIZE);
        captureImg = robot.createScreenCapture(rect);

        // Test pixels
        testRegion("green", 0, 0, 400, 10, 0xff00ff00);
        testRegion("original-red", 0, 10, 50, 400, 0xffff0000);
        testRegion("background", 50, 10, 60, 400, 0xff000000);
        testRegion("in-between", 60, 10, 110, 20, 0xff000000);
        testRegion("copied-red", 60, 20, 110, 400, 0xffff0000);
        testRegion("background", 110, 10, 400, 400, 0xff000000);
    }

    public Dimension getPreferredSize() {
        return new Dimension(SIZE, SIZE);
    }

    private static void testRegion(String region,
                                   int x1, int y1, int x2, int y2,
                                   int expected) {
        System.out.print("Test region: " + region);
        for (int y = y1; y < y2; y++) {
            for (int x = x1; x < x2; x++) {
                int actual = captureImg.getRGB(x, y);
                if (actual != expected) {
                    System.out.print(" Status: FAILED\n");
                    errorLog.append("Test failed for " + region
                                                + " region at x: " + x + " y: " + y
                                                + " (expected: "
                                                + Integer.toHexString(expected)
                                                + " actual: "
                                                + Integer.toHexString(actual) + ")\n");
                    return;
                }
            }
        }
        System.out.print(" Status: PASSED\n");
    }

    private static void saveImages() {
        GraphicsConfiguration ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
                                                      .getDefaultScreenDevice()
                                                      .getDefaultConfiguration();

        MultiResolutionImage mrImage = robot.createMultiResolutionScreenCapture(ge.getBounds());
        List<Image> variants = mrImage.getResolutionVariants();
        RenderedImage screenCapture = (RenderedImage) variants.get(variants.size() - 1);

        try {
            ImageIO.write(screenCapture, "png", new File("fullscreen.png"));
            ImageIO.write(captureImg, "png", new File("canvas.png"));
        } catch (IOException e) {
            System.err.println("Can't write image " + e);
        }
    }
}
