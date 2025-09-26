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
 * @bug 6244071
 * @key headful
 * @requires (os.family != "mac")
 * @summary Verifies that copying a subregion from a VolatileImage works
 * properly with the OGL pipeline.
 * @run main/othervm  VolatileSubRegion
 * @run main/othervm -Dsun.java2d.opengl=True -Dsun.java2d.opengl.fbobject=true VolatileSubRegion
 * @run main/othervm -Dsun.java2d.opengl=True -Dsun.java2d.opengl.fbobject=false VolatileSubRegion
 */

/*
 * @test
 * @bug 6244071
 * @key headful
 * @requires (os.family == "mac")
 * @summary Verifies that copying a subregion from a VolatileImage works
 * properly with the OGL pipeline.
 * @run main/othervm VolatileSubRegion
 * @run main/othervm -Dsun.java2d.opengl=True -Dsun.java2d.opengl.fbobject=true VolatileSubRegion
 * @run main/othervm -Dsun.java2d.opengl=True -Dsun.java2d.opengl.fbobject=false VolatileSubRegion
 */

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
import java.awt.image.VolatileImage;
import java.io.File;
import javax.imageio.ImageIO;

public class VolatileSubRegion extends Panel {

    private VolatileImage img;

    public void paint(Graphics g) {

        Graphics2D g2d = (Graphics2D)g;

        if (img == null) {
            img = createVolatileImage(200, 200);
            Graphics2D goff = img.createGraphics();
            goff.setColor(Color.green);
            goff.fillRect(50, 0, 100, 50);
            goff.setColor(Color.blue);
            goff.fillRect(0, 0, 200, 200);
            goff.setColor(Color.red);
            goff.fillRect(50, 50, 100, 100);
            goff.setColor(Color.yellow);
            goff.fillRect(50, 150, 100, 50);
            goff.dispose();
        }

        g2d.setColor(Color.white);
        g2d.fillRect(0, 0, getWidth(), getHeight());

        g2d.drawImage(img,
                      50, 50, 200, 200,
                      50, 50, 200, 200,
                      null);

    }


    private static volatile VolatileSubRegion test;
    private static volatile Frame frame;

    static void createUI() {
        test = new VolatileSubRegion();
        frame = new Frame("OpenGL VolatileSubRegion Test");
        frame.add(test);
        frame.setSize(300, 300);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();

        EventQueue.invokeAndWait(VolatileSubRegion::createUI);

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
            Rectangle rect = new Rectangle(pt1.x, pt1.y, 200, 200);
            capture = robot.createScreenCapture(rect);
        } finally {
            if (frame != null) {
                 EventQueue.invokeAndWait(frame::dispose);
            }
        }

        // Test pixels
        int pixel1 = capture.getRGB(49, 50);
        if (pixel1 != 0xffffffff) {
            saveImage(capture);
            throw new RuntimeException(getMsg("background pixel", pixel1));
        }
        int pixel2 = capture.getRGB(50, 50);
        if (pixel2 != 0xffff0000) {
            saveImage(capture);
            throw new RuntimeException(getMsg("red region", pixel2));
        }
        int pixel3 = capture.getRGB(50, 150);
        if (pixel3 != 0xffffff00) {
            saveImage(capture);
            throw new RuntimeException(getMsg("yellow region", pixel3));
        }
    }

    static String getMsg(String r, int p1) {
         return "Failed: Incorrect color for " + r + " : got " + Integer.toHexString(p1);
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
