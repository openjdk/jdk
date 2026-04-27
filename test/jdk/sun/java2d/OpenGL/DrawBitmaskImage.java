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
 * @bug 6248561 6264014
 * @key headful
 * @requires (os.family != "mac")
 * @summary Verifies that bitmask image copies work properly with the
 * OGL pipeline when a SrcOver composite with extra alpha is involved.
 * @run main/othervm -Dsun.java2d.opengl=True DrawBitmaskImage
 * @run main/othervm DrawBitmaskImage
 */

/*
 * @test
 * @bug 6248561 6264014
 * @key headful
 * @requires (os.family == "mac")
 * @summary Verifies that bitmask image copies work properly with the
 * OGL pipeline when a SrcOver composite with extra alpha is involved.
 * @run main/othervm -Dsun.java2d.opengl=True DrawBitmaskImage
 * @run main/othervm DrawBitmaskImage
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
import java.awt.Transparency;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.File;
import javax.imageio.ImageIO;

public class DrawBitmaskImage extends Panel {

    static final int TESTW = 200, TESTH = 200;
    private static volatile DrawBitmaskImage test;
    private static volatile Frame frame;

    public void paint(Graphics g) {

        Graphics2D g2d = (Graphics2D)g;
        g2d.setColor(Color.black);
        g2d.fillRect(0, 0, getWidth(), getHeight());
        g2d.setComposite(AlphaComposite.SrcOver.derive(0.50f));

        BufferedImage img = getGraphicsConfiguration().createCompatibleImage(50, 50,
                                                        Transparency.BITMASK);
        Graphics2D gimg = img.createGraphics();
        gimg.setComposite(AlphaComposite.Src);
        gimg.setColor(new Color(0, 0, 0, 0));
        gimg.fillRect(0, 0, 50, 50);
        gimg.setColor(Color.red);
        gimg.fillRect(10, 10, 30, 30);
        gimg.dispose();


        g2d.drawImage(img, 10, 10, null);

        // draw a second time to ensure that the cached copy is used
        g2d.drawImage(img, 80, 10, null);
    }

    public Dimension getPreferredSize() {
         return new Dimension(TESTW, TESTH);
    }

    static void createUI() {
        test = new DrawBitmaskImage();
        frame = new Frame("OpenGL DrawBitmaskImage Test");
        Panel p = new Panel();
        p.add(test);
        frame.add(p);
        frame.setSize(TESTW+100, TESTH+100);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();

        EventQueue.invokeAndWait(DrawBitmaskImage::createUI);

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
            Rectangle rect = new Rectangle(pt1.x, pt1.y, TESTW, TESTH);
            capture = robot.createScreenCapture(rect);
        } finally {
            if (frame != null) {
                 EventQueue.invokeAndWait(frame::dispose);
            }
        }

        // Test background color
        int pixel = capture.getRGB(5, 10);
        if (pixel != 0xff000000) {
            saveImage(capture);
            throw new RuntimeException("Failed: Incorrect color for " +
                                       "background (actual=" +
                                       Integer.toHexString(pixel) + ")");
        }

        // Test pixels (allow for small error in the actual red value)
        pixel = capture.getRGB(25, 25);
        System.out.println("pixel1 is " + Integer.toHexString(pixel));

        if ((pixel < 0xff7e0000) || (pixel > 0xff900000)) {
            saveImage(capture);
            throw new RuntimeException("Failed: Incorrect color for " +
                                       "first pixel (actual=" +
                                       Integer.toHexString(pixel) + ")");
        }

        pixel = capture.getRGB(95, 25);
        System.out.println("pixel2 is " + Integer.toHexString(pixel));
        if ((pixel < 0xff7e0000) || (pixel > 0xff900000)) {
            saveImage(capture);
            throw new RuntimeException("Failed: Incorrect color for " +
                                       "second pixel (actual=" +
                                       Integer.toHexString(pixel) + ")");
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
