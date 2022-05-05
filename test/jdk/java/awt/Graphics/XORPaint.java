/*
 * Copyright (c) 2005, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 5106309
 * @key headful
 * @summary Verifies that XOR mode works properly for all pipelines
 * (for both simple Colors and complex Paints).
 *
 * @requires (os.family == "windows")
 * @run main/othervm XORPaint
 * @run main/othervm -Dsun.java2d.d3d=True -Dsun.java2d.uiScale=1 XORPaint
 * @run main/othervm -Dsun.java2d.d3d=True -Dsun.java2d.uiScale=1.25 XORPaint
 * @run main/othervm -Dsun.java2d.d3d=True -Dsun.java2d.uiScale=1.5 XORPaint
 * @run main/othervm -Dsun.java2d.d3d=True -Dsun.java2d.uiScale=1.75 XORPaint
 * @run main/othervm -Dsun.java2d.d3d=True -Dsun.java2d.uiScale=2 XORPaint
 * @run main/othervm -Dsun.java2d.d3d=false -Dsun.java2d.uiScale=1 XORPaint
 * @run main/othervm -Dsun.java2d.d3d=false -Dsun.java2d.uiScale=1.25 XORPaint
 * @run main/othervm -Dsun.java2d.d3d=false -Dsun.java2d.uiScale=1.5 XORPaint
 * @run main/othervm -Dsun.java2d.d3d=false -Dsun.java2d.uiScale=1.75 XORPaint
 * @run main/othervm -Dsun.java2d.d3d=false -Dsun.java2d.uiScale=2 XORPaint
 */

/*
 * @test
 * @bug 5106309
 * @key headful
 * @summary Verifies that XOR mode works properly for all pipelines
 * (for both simple Colors and complex Paints).
 *
 * @requires (os.family == "mac")
 * @run main/othervm XORPaint
 * @run main/othervm -Dsun.java2d.opengl=True -Dsun.java2d.uiScale=1 XORPaint
 * @run main/othervm -Dsun.java2d.opengl=True -Dsun.java2d.uiScale=2 XORPaint
 * @run main/othervm -Dsun.java2d.metal=True -Dsun.java2d.uiScale=1 XORPaint
 * @run main/othervm -Dsun.java2d.metal=True -Dsun.java2d.uiScale=2 XORPaint
 */

/*
 * @test
 * @bug 5106309
 * @key headful
 * @summary Verifies that XOR mode works properly for all pipelines
 * (for both simple Colors and complex Paints).
 *
 * @requires (os.family == "linux")
 * @run main/othervm XORPaint
 * @run main/othervm -Dsun.java2d.xrender=True -Dsun.java2d.uiScale=1 XORPaint
 * @run main/othervm -Dsun.java2d.xrender=True -Dsun.java2d.uiScale=2 XORPaint
 * @run main/othervm -Dsun.java2d.xrender=false -Dsun.java2d.uiScale=1 XORPaint
 * @run main/othervm -Dsun.java2d.xrender=false -Dsun.java2d.uiScale=2 XORPaint
 */

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

public class XORPaint extends Panel {

    private static final int WHITE = 0xffffffff;
    private static final int BLUE  = 0xff0000ff;

    public void paint(Graphics g) {
        Graphics2D g2d = (Graphics2D)g;

        g2d.setColor(Color.white);
        g2d.fillRect(0, 0, getWidth(), getHeight());

        // render the tests without antialiasing
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                             RenderingHints.VALUE_ANTIALIAS_OFF);
        renderTests(g2d, "This is non-AA text");

        // now do the above tests again, this time with antialiasing
        g2d.translate(0, 100);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                             RenderingHints.VALUE_ANTIALIAS_ON);
        renderTests(g2d, "This is AA text");
    }

    private void renderTests(Graphics2D g2d, String text) {
        g2d.setFont(new Font("Dialog", Font.PLAIN, 12));
        g2d.setColor(Color.blue);
        g2d.setXORMode(Color.white);

        // fill a rectangle once and make sure it is blue
        g2d.fillRect(5, 5, 20, 20);

        // fill another rectangle twice and make sure it is reversible
        // (should produce the background color)
        g2d.fillRect(35, 5, 20, 20);
        g2d.fillRect(35, 5, 20, 20);

        // draw a string once and make sure it is blue
        g2d.drawString(text, 5, 50);

        // draw another string twice and make sure it is reversible
        // (should produce the background color)
        g2d.drawString(text, 5, 70);
        g2d.drawString(text, 5, 70);

        g2d.setPaint(new GradientPaint(0.0f, 0.0f, Color.blue,
                                       100.0f, 100.f, Color.blue, true));
        g2d.fillRect(70, 5, 20, 20);
    }

    /*
     * Not great having to allow any tolerance but some of
     * the scaling down for screen captures seems to introduce
     * tiny rounding errors that aren't consistent.
     * Allow a very small tolerance for this
     */
    private static boolean pixelsMatch(int p1, int p2) {
        // note : ignoring alpha
        int tol = 1;
        int r1 = p1 & 0x00ff0000 >> 16;
        int g1 = p1 & 0x0000ff00 >> 8;
        int b1 = p1 & 0x000000ff;
        int r2 = p2 & 0x00ff0000 >> 16;
        int g2 = p2 & 0x0000ff00 >> 8;
        int b2 = p2 & 0x000000ff;
        int rd = r2 - r1; if (rd < 0) rd = -rd;
        int gd = g2 - g1; if (gd < 0) gd = -gd;
        int bd = b2 - b1; if (bd < 0) bd = -bd;
        return (rd <= tol && gd <= tol && bd <= tol);
    }

    private static void testPixel(BufferedImage capture,
                                  int x, int y, int expectedPixel,
                                  String testDesc, boolean expectedRes)
    {
        int pixel = capture.getRGB(x, y);
        if (expectedRes) {
            if (!pixelsMatch(pixel, expectedPixel)) {
                try {
                    ImageIO.write(capture, "png", new File("capture.png"));
                } catch (IOException e) {
                    System.err.println("can't write image " + e);
                }
                throw new RuntimeException(
                        "Failed: Incorrect color for " + testDesc
                                + " at (" + x + ", " + y + ") "
                                + "(expected: " + Integer
                                .toHexString(expectedPixel) + " actual: "
                                + Integer.toHexString(pixel) + ")");
            }
        } else {
            if (pixelsMatch(pixel, expectedPixel)) {
                try {
                    ImageIO.write(capture, "png", new File("capture.png"));
                } catch (IOException e) {
                    System.err.println("can't write image " + e);
                }
                throw new RuntimeException(
                        "Failed: Incorrect color for " + testDesc +
                                " at (" + x + ", " + y + ") " +
                                " : 0x" + Integer.toHexString(pixel));
            }
        }
    }

    private static void testPixels(BufferedImage capture,
                                   int yoff, String type)
    {
        testPixel(capture, 10,  yoff+10,   BLUE, "solid rect"+type , true);
        testPixel(capture, 40, yoff+10,  WHITE, "erased solid rect"+type, true);

        testPixel(capture, 80, yoff+10,   BLUE, "GradientPaint rect"+type, true);
        testPixel(capture, 5,  yoff+61, WHITE, "erased text"+type, true);
    }

    public static void main(String[] args) throws Exception {

        final Frame frame = new Frame("XORPaint Test");
        final XORPaint xorPanel = new XORPaint();
        EventQueue.invokeAndWait(() -> {
            frame.add(xorPanel);
            frame.setUndecorated(true);
            frame.pack();
            frame.setSize(250, 250);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });

        Toolkit.getDefaultToolkit().sync();
        Robot robot = new Robot();
        robot.waitForIdle();
        robot.delay(2000);
        Point pt1 = xorPanel.getLocationOnScreen();
        Rectangle rect = new Rectangle(pt1.x, pt1.y, 200, 200);
        BufferedImage capture = robot.createScreenCapture(rect);

        EventQueue.invokeAndWait(() -> frame.dispose());

        // Make sure we have a white background, for starters
        testPixel(capture, 180, 180, WHITE, "background", true);

        // Test the non-AA primitives
        testPixels(capture, 0, " (non-AA)");

        // Test the AA primitives
        testPixels(capture, 100, " (AA)");
    }
}
