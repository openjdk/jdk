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
 * @bug 5104584 8237244 8369335
 * @key headful
 * @requires (os.family != "mac")
 * @summary Verifies that scaling an image works properly when the
 * source parameters are outside the source bounds.
 * @run main/othervm -Dsun.java2d.opengl=True ScaleParamsOOB
 * @run main/othervm ScaleParamsOOB
 */

/*
 * @test
 * @bug 5104584 8237244
 * @key headful
 * @requires (os.family == "mac")
 * @summary Verifies that scaling an image works properly when the
 * source parameters are outside the source bounds.
 * @run main/othervm -Dsun.java2d.opengl=True ScaleParamsOOB
 * @run main/othervm ScaleParamsOOB
 */


import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class ScaleParamsOOB extends Panel {

    private static final int TOLERANCE = 12;

    private static volatile ScaleParamsOOB test;
    private static volatile Frame frame;

    private BufferedImage img;

    public void paint(Graphics g) {

        Graphics2D g2d = (Graphics2D)g;
        g2d.setColor(Color.black);
        g2d.fillRect(0, 0, getWidth(), getHeight());

        BufferedImage img = getGraphicsConfiguration().createCompatibleImage(40, 40);
        Graphics2D gimg = img.createGraphics();
        gimg.setColor(Color.red);
        gimg.fillRect(0, 0, 40, 40);
        gimg.dispose();

        // first time will be a sw->surface blit
        g2d.drawImage(img,
                      10, 10, 90, 90,
                      -60, -60, 100, 100,
                      null);

        // second time will be a texture->surface blit
        g2d.drawImage(img,
                      110, 10, 190, 90,
                      -60, -60, 100, 100,
                      null);
    }

    public Dimension getPreferredSize() {
        return new Dimension(300, 200);
    }

    private static void testRegion(BufferedImage bi,
                                   Rectangle wholeRegion,
                                   Rectangle affectedRegion)
    {
        int x1 = wholeRegion.x;
        int y1 = wholeRegion.y;
        int x2 = x1 + wholeRegion.width;
        int y2 = y1 + wholeRegion.height;

        int ax1 = affectedRegion.x;
        int ay1 = affectedRegion.y;
        int ax2 = ax1 + affectedRegion.width;
        int ay2 = ay1 + affectedRegion.height;

        for (int y = y1; y < y2; y++) {
            for (int x = x1; x < x2; x++) {
                int actual = bi.getRGB(x, y);
                int expected = 0;
                if (affectedRegion.contains(x, y)) {
                    expected = Color.red.getRGB();
                } else {
                    expected = Color.black.getRGB();
                }
                int alpha = (actual >> 24) & 0xFF;
                int red = (actual >> 16) & 0xFF;
                int green = (actual >> 8) & 0xFF;
                int blue = (actual) & 0xFF;

                int standardAlpha = (expected >> 24) & 0xFF;
                int standardRed = (expected >> 16) & 0xFF;
                int standardGreen = (expected >> 8) & 0xFF;
                int standardBlue = (expected) & 0xFF;

                if ((Math.abs(alpha - standardAlpha) > TOLERANCE) ||
                    (Math.abs(red - standardRed) > TOLERANCE) ||
                    (Math.abs(green - standardGreen) > TOLERANCE) ||
                    (Math.abs(blue - standardBlue) > TOLERANCE)) {

                    String msg = ("Test failed at x="+x+" y="+y+
                                   " (expected="+
                                   Integer.toHexString(expected) +
                                   " actual="+
                                   Integer.toHexString(actual) +
                                   ")");
                    // log edge pixel differences, but don't fail the test.
                    if ((x == ax1) || (x == ax2) || (y == ay1) || (y == ay2)) {
                        System.err.println(msg);
                    } else {
                        saveImage(bi);
                        throw new RuntimeException(msg);
                    }
                }
            }
        }
    }

    private static void createAndShowGUI() {
        test = new ScaleParamsOOB();
        frame = new Frame("OpenGL ScaleParamsOOB Test");
        frame.setAlwaysOnTop(true);
        frame.add(test);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();

        EventQueue.invokeAndWait(() -> createAndShowGUI());

        robot.waitForIdle();
        robot.delay(2000);

        // Grab the screen region
        BufferedImage capture = null;
        try {
            Point pt1 = test.getLocationOnScreen();
            Rectangle rect = new Rectangle(pt1.x, pt1.y, 200, 200);
            capture = robot.createScreenCapture(rect);
        } finally {
            if (frame != null) {
                EventQueue.invokeAndWait(frame::dispose);
            }
        }

        // Test background color
        int pixel = capture.getRGB(5, 5);
        if (pixel != 0xff000000) {
            saveImage(capture);
            throw new RuntimeException("Failed: Incorrect color for " +
                                       "background: " + Integer.toHexString(pixel));
        }

        // Test pixels
        testRegion(capture,
                   new Rectangle(5, 5, 90, 90),
                   new Rectangle(40, 40, 20, 20));
        testRegion(capture,
                   new Rectangle(105, 5, 90, 90),
                   new Rectangle(140, 40, 20, 20));
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
