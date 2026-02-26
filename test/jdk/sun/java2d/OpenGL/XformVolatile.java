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
 * @bug 4970836
 * @key headful
 * @requires (os.family != "mac")
 * @summary Verifies that transformed VolatileImage copies work properly with
 * the OGL pipeline.
 * @run main/othervm XformVolatile
 * @run main/othervm -Dsun.java2d.opengl=True -Dsun.java2d.opengl.fbobject=true XformVolatile
 * @run main/othervm -Dsun.java2d.opengl=True -Dsun.java2d.opengl.fbobject=false XformVolatile
 */

/*
 * @test
 * @bug 4970836
 * @key headful
 * @requires (os.family == "mac")
 * @summary Verifies that transformed VolatileImage copies work properly with
 * the OGL pipeline.
 * @run main/othervm XformVolatile
 * @run main/othervm -Dsun.java2d.opengl=True -Dsun.java2d.opengl.fbobject=true XformVolatile
 * @run main/othervm -Dsun.java2d.opengl=True -Dsun.java2d.opengl.fbobject=false XformVolatile
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
import java.awt.image.VolatileImage;
import java.io.File;
import javax.imageio.ImageIO;

public class XformVolatile extends Panel {

    private static volatile Frame frame;
    private static volatile XformVolatile test;
    private volatile VolatileImage img;

    public void paint(Graphics g) {

        Graphics2D g2d = (Graphics2D)g;

        if (img == null) {
            img = createVolatileImage(200, 200);
            Graphics2D goff = img.createGraphics();
            goff.setColor(Color.blue);
            goff.fillRect(0, 0, 200, 200);
            goff.setColor(Color.red);
            goff.fillPolygon(new int[] {10, 100, 190},
                             new int[] {190, 10, 190}, 3);
            goff.dispose();
        }

        g2d.setColor(Color.black);
        g2d.fillRect(0, 0, getWidth(), getHeight());

        g2d.rotate(Math.toRadians(3.0));
        g2d.drawImage(img, 0, 0, null);
    }

    static void createUI() {
        test = new XformVolatile();
        frame = new Frame("OpenGL XformVolatile Test");
        frame.add(test);
        frame.setSize(300, 300);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public static void main(String[] args) throws Exception {

        Robot robot = new Robot();

        EventQueue.invokeAndWait(XformVolatile::createUI);

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

        // Test inner and outer pixels
        int pixel1 = capture.getRGB(5, 175);
        if (pixel1 != 0xff0000ff) {
            saveImage(capture);
            throw new RuntimeException(getMsg("inner", pixel1));
        }
        int pixel2 = capture.getRGB(5, 188);
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
