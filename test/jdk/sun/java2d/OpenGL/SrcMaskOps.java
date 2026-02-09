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
 * @bug 4942939 4970674
 * @key headful
 * @requires (os.family != "mac")
 * @summary Verifies that OGLMaskFill, OGLMaskBlit, and OGLTextRenderer
 * operations work properly for non-SrcOver composites.
 * @run main/othervm -Dsun.java2d.opengl=True SrcMaskOps
 * @run main/othervm SrcMaskOps
 */

/*
 * @test
 * @bug 4942939 4970674
 * @key headful
 * @requires (os.family == "mac")
 * @summary Verifies that OGLMaskFill, OGLMaskBlit, and OGLTextRenderer
 * operations work properly for non-SrcOver composites.
 * @run main/othervm -Dsun.java2d.opengl=True SrcMaskOps
 * @run main/othervm SrcMaskOps
 */

import java.awt.AlphaComposite;
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
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class SrcMaskOps extends Panel {

    static volatile Frame frame;
    static volatile SrcMaskOps test;

    static final int SRX = 50;
    static final int SRY = 50;
    static final int GPX = 90;
    static final int GPY = 50;
    static final int DTX = 120;
    static final int DTY = 70;

    public void paint(Graphics g) {

        Graphics2D g2d = (Graphics2D)g;

        g2d.setColor(Color.white);
        g2d.fillRect(0, 0, getWidth(), getHeight());

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                             RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setComposite(AlphaComposite.Src);

        g2d.setColor(Color.blue);
        g2d.drawRect(SRX, SRY, 20, 20);

        g2d.setPaint(new GradientPaint(0.0f, 0.0f, Color.red,
                                       100.0f, 100.f, Color.red, true));
        g2d.drawRect(GPX, GPY, 20, 20);

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                             RenderingHints.VALUE_ANTIALIAS_OFF);

        g2d.setColor(Color.red);
        Font font = new Font(Font.DIALOG, Font.PLAIN, 20);
        g2d.setFont(font);
        g2d.drawString("HELLO", DTX, DTY);
    }

    static void createUI() {
        frame = new Frame("OpenGL SrcMaskOps Test");
        test = new SrcMaskOps();
        frame.add(test);
        frame.setSize(300, 300);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public static void main(String[] args) throws Exception {

        Robot robot = new Robot();
        BufferedImage capture = null;
        try {
            EventQueue.invokeAndWait(SrcMaskOps::createUI);
            robot.waitForIdle();
            robot.delay(3000);

            // Grab the screen region
            Point pt1 = test.getLocationOnScreen();
            Rectangle rect = new Rectangle(pt1.x, pt1.y, 300, 300);
            capture = robot.createScreenCapture(rect);
        } finally {
            if (frame != null) {
                EventQueue.invokeAndWait(frame::dispose);
            }
        }

        // Test solid rectangle
        int pixel1, pixel2;
        pixel1 = capture.getRGB(SRX, SRY);
        pixel2 = capture.getRGB(SRX+2, SRY+2);
        if (!similar(pixel1, 0xff0000ff) || !similar(pixel2, 0xffffffff)) {
            saveImage(capture);
            throw new RuntimeException(getMsg("solid rectangle", pixel1, pixel2));
        }

        // Test GradientPaint rectangle
        pixel1 = capture.getRGB(GPX, GPY);
        pixel2 = capture.getRGB(GPX+2, GPY+2);
        if (!similar(pixel1, 0xffff0000) || !similar(pixel2, 0xffffffff)) {
            saveImage(capture);
            throw new RuntimeException(getMsg("GradientPaint rectangle", pixel1, pixel2));
        }

        // Test solid text
        pixel1 = capture.getRGB(DTX+2, DTY-5);
        pixel2 = capture.getRGB(DTX+5, DTY-5);
        if (!similar(pixel1, 0xffff0000) || !similar(pixel2, 0xffffffff)) {
            saveImage(capture);
            throw new RuntimeException(getMsg("solid text", pixel1, pixel2));
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

        int allowedDiff = 0x10;
        return
            (Math.abs(a1 - a2) <= allowedDiff) &&
            (Math.abs(r1 - r2) <= allowedDiff) &&
            (Math.abs(g1 - g2) <= allowedDiff) &&
            (Math.abs(b1 - b2) <= allowedDiff);
    }

    static String getMsg(String r, int p1, int p2) {
         return "Failed: Incorrect color[s] for " + r + " got " +
                Integer.toHexString(p1) + " and " + Integer.toHexString(p2);
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
