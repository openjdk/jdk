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
 * @bug 6277977 6319663 8369335
 * @key headful
 * @requires (os.family != "mac")
 * @summary Verifies that blending operations do not inadvertantly leave
 * non-opaque alpha values in the framebuffer.  Note that this test is
 * intended to run on GraphicsConfigs that support a stored alpha channel
 * (to verify the bug at hand), but it is also a useful for testing the
 * compositing results on any configuration.
 * @run main/othervm  -Dsun.java2d.opengl=True OpaqueDest
 * @run main/othervm  OpaqueDest
 */

/*
 * @test
 * @bug 6277977 6319663
 * @key headful
 * @requires (os.family == "mac")
 * @summary Verifies that blending operations do not inadvertantly leave
 * non-opaque alpha values in the framebuffer.  Note that this test is
 * intended to run on GraphicsConfigs that support a stored alpha channel
 * (to verify the bug at hand), but it is also a useful for testing the
 * compositing results on any configuration.
 * @run main/othervm -Dsun.java2d.opengl=True OpaqueDest
 * @run main/othervm  OpaqueDest
 */

import java.awt.AlphaComposite;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.File;
import javax.imageio.ImageIO;

public class OpaqueDest extends Canvas {

    private static volatile Frame frame;
    private static volatile OpaqueDest test;
    private static final int W = 100, H = 100;

    public void paint(Graphics g) {

        Graphics2D g2d = (Graphics2D)g;

        g2d.setColor(Color.red);
        g2d.fillRect(0, 0, getWidth(), getHeight());

        // This will clear the rectangle to black
        g2d.setComposite(AlphaComposite.Clear);
        g2d.fillRect(10, 10, 80, 80);

        // If everything is working properly, then this will fill the
        // rectangle with red again.  Before this bug was fixed, the previous
        // Clear operation would leave zero values in the destination's
        // alpha channel (if present), and therefore a SrcIn operation
        // would result in all-black.
        g2d.setComposite(AlphaComposite.SrcIn);
        g2d.fillRect(10, 10, 80, 80);
    }

    public Dimension getPreferredSize() {
        return new Dimension(W, H);
    }

    static void createUI() {
        test = new OpaqueDest();
        frame = new Frame("OpenGL OpaqueDest Test");
        Panel p = new Panel();
        p.add(test);
        frame.add(p);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();

        EventQueue.invokeAndWait(OpaqueDest::createUI);

        robot.waitForIdle();
        robot.delay(2000);

        BufferedImage capture = null;
        try {
            GraphicsConfiguration gc = frame.getGraphicsConfiguration();
            if (gc.getColorModel() instanceof IndexColorModel) {
                System.out.println("IndexColorModel detected: " +
                                   "test considered PASSED");
                return;
            }
            Point pt1 = test.getLocationOnScreen();
            Rectangle rect = new Rectangle(pt1.x, pt1.y, W, H);
            capture = robot.createScreenCapture(rect);
        } finally {
            if (frame != null) {
                 EventQueue.invokeAndWait(frame::dispose);
            }
        }


        // Test all pixels (every one should be red)
        for (int y = 0; y < W; y++) {
            for (int x = 0; x < H; x++) {
                int actual = capture.getRGB(x, y);
                    int expected = 0xffff0000;
                if (!similar(actual, expected)) {
                    String msg = "Test failed at x="+x+" y="+y+
                                 " (expected="+
                                 Integer.toHexString(expected) +
                                 " actual="+
                                 Integer.toHexString(actual) +
                                 ")";
                    if ( ( x== 0) || ( x == W) || ( y == 0) || ( y == H)) {
                        System.err.println(msg);
                    } else {
                        saveImage(capture);
                        throw new RuntimeException(msg);
                    }
                }
            }
        }
    }

    static boolean similar(int p1, int p2) {
        int a1 = (p1 >> 24) & 0xff;
        int r1 = (p1 >> 16) & 0xff;
        int g1 = (p1 >> 8) & 0xff;
        int b1 = p1 & 0xff;
        int a2 = (p2 >> 24) & 0xff;
        int r2 = (p2 >> 16) & 0xff;
        int g2 = (p2 >> 8) & 0xff;
        int b2 = p2 & 0xff;

        int allowedDiff = 0x01; // tiny rounding error allowed.
        return
            (Math.abs(a1 - a2) <= allowedDiff) &&
            (Math.abs(r1 - r2) <= allowedDiff) &&
            (Math.abs(g1 - g2) <= allowedDiff) &&
            (Math.abs(b1 - b2) <= allowedDiff);
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
