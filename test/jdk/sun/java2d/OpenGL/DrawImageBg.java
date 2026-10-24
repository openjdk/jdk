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
 * @bug 4993274
 * @key headful
 * @requires (os.family != "mac")
 * @summary Verifies that managed image copies and transforms work properly
 * with the OGL pipeline when a background color is specified.
 * @run main/othervm -Dsun.java2d.opengl=True DrawImageBg
 * @run main/othervm DrawImageBg
 */

/*
 * @test
 * @bug 4993274
 * @key headful
 * @requires (os.family == "mac")
 * @summary Verifies that managed image copies and transforms work properly
 * with the OGL pipeline when a background color is specified.
 * @run main/othervm -Dsun.java2d.opengl=True DrawImageBg
 * @run main/othervm DrawImageBg
 */

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class DrawImageBg extends Panel {

    static volatile Frame frame;
    static volatile DrawImageBg test;

    public void paint(Graphics g) {

        Graphics2D g2d = (Graphics2D)g;
        g2d.setColor(Color.black);
        g2d.fillRect(0, 0, getWidth(), getHeight());

        BufferedImage img = getGraphicsConfiguration().createCompatibleImage(50, 50,
                                                   Transparency.BITMASK);
        Graphics2D gimg = img.createGraphics();
        gimg.setComposite(AlphaComposite.Src);
        gimg.setColor(new Color(0, 0, 0, 0));
        gimg.fillRect(0, 0, 50, 50);
        gimg.setColor(Color.red);
        gimg.fillRect(10, 10, 30, 30);
        gimg.dispose();

        g2d.drawImage(img, 10, 10, Color.blue, null);

        // draw a second time to ensure that the cached copy is used
        g2d.drawImage(img, 80, 10, Color.blue, null);
    }

    static void createUI() {
        frame = new Frame("OpenGL DrawImageBg Test");
        test = new DrawImageBg();
        frame.add(test);
        frame.setSize(300, 300);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public static void main(String[] args) throws Exception {

        BufferedImage capture = null;
        Robot robot = new Robot();
        try {
            EventQueue.invokeAndWait(DrawImageBg::createUI);
            robot.waitForIdle();
            robot.delay(3000);

            // Grab the screen region
            Point pt1 = test.getLocationOnScreen();
            Rectangle rect = new Rectangle(pt1.x+80, pt1.y, 80, 80);
            capture = robot.createScreenCapture(rect);
        } finally {
            if (frame != null) {
                EventQueue.invokeAndWait(frame::dispose);
            }
        }

        if (capture == null) {
            throw new RuntimeException("Screen capture is null");
        }

        // Test inner and outer pixels
        int pixel1 = capture.getRGB(5, 10);
        if (pixel1 != 0xff0000ff) {
            saveImage(capture);
            throw new RuntimeException(getMsg("outer", pixel1));
        }
        int pixel2 = capture.getRGB(25, 25);
        if (pixel2 != 0xffff0000) {
            saveImage(capture);
            throw new RuntimeException(getMsg("inner", pixel2));
        }
    }

    static String getMsg(String r, int p1) {
         return "Failed: Incorrect color for " + r + " pixel: got " + Integer.toHexString(p1);
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
