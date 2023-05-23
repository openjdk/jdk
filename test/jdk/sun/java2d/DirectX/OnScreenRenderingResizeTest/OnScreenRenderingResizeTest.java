/*
 * Copyright (c) 2007, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6664068 6666931
 * @summary Tests that resizing a window to which a tight loop is rendering
 *          doesn't produce artifacts or crashes
 * @run main/othervm OnScreenRenderingResizeTest
 * @run main/othervm -Dsun.java2d.d3d=false OnScreenRenderingResizeTest
 */

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.EventQueue;
import java.awt.image.BufferedImage;
import java.awt.image.VolatileImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

public class OnScreenRenderingResizeTest {

    private static volatile boolean nocheck = false;

    private static int FRAME_W;
    private static int FRAME_H;
    private static int IMAGE_W;
    private static int IMAGE_H;
    private static final int tolerance = 12;
    private static long RUN_TIME = 1000*20;

    private static final Color renderColor = Color.green;
    private static final Color bgColor = Color.white;

    private static Frame frame;

    private static void createAndShowGUI() {
        frame = new Frame() {
            public void paint(Graphics g) {}
            public void update(Graphics g) {}
        };
        frame.setBackground(bgColor);
        frame.setUndecorated(true);
        frame.setAlwaysOnTop(true);
        frame.pack();
        frame.setVisible(true);
    }

    public static void main(String[] args) throws Exception {

        for (String arg : args) {
            if ("-inf".equals(arg)) {
                System.err.println("Test will run indefinitely");
                RUN_TIME = Long.MAX_VALUE;
            } else  if ("-nocheck".equals(arg)) {
                System.err.println("Test will not check rendering results");
                nocheck = true;
            } else {
                System.err.println("Usage: OnScreenRenderingResizeTest" +
                                   " [-inf][-nocheck]");
            }
        }

        try {
            EventQueue.invokeAndWait(new Runnable() {
                public void run() {
                    createAndShowGUI();
                }
            });
            Thread.sleep(2000);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        try {
            GraphicsConfiguration gc = frame.getGraphicsConfiguration();
            Rectangle gcBounds = gc.getBounds();
            FRAME_W = (gcBounds.width / 4);
            FRAME_H = (gcBounds.height / 4);
            IMAGE_W = (gcBounds.width / 8);
            IMAGE_H = (gcBounds.height / 8);
            frame.setBounds(gcBounds.width / 4, gcBounds.height / 4,
                            FRAME_W, FRAME_H);

            BufferedImage output =
                new BufferedImage(IMAGE_W, IMAGE_H,
                                  BufferedImage.TYPE_INT_RGB);
            output.setAccelerationPriority(0.0f);
            Graphics g = output.getGraphics();
            g.setColor(renderColor);
            g.fillRect(0, 0, IMAGE_W, IMAGE_H);

            int maxW = gcBounds.width / 2;
            int maxH = gcBounds.height / 2;
            int minW = FRAME_W;
            int minH = FRAME_H;
            int incW = 10, incH = 10, cnt = 0;
            Robot robot = null;
            if (!nocheck && gc.getColorModel().getPixelSize() > 8) {
                try {
                    robot = new Robot();
                    robot.setAutoDelay(100);
                    robot.mouseMove(0,0);
                } catch (AWTException ex) {
                    System.err.println("Robot creation failed, continuing.");
                }
            } else {
                System.err.println("No screen rendering checks.");
            }
            VolatileImage vi = gc.
                createCompatibleVolatileImage(IMAGE_W, IMAGE_H);
            vi.validate(gc);
            long timeStarted = System.currentTimeMillis();
            while ((System.currentTimeMillis() - timeStarted) < RUN_TIME) {

                if (++cnt > 100) {
                    int w = frame.getWidth() + incW;
                    int h = frame.getHeight() + incH;
                    if (w < minW || w > maxW ) {
                        incW = -incW;
                    }
                    if (h < minH || h > maxH ) {
                        incH = -incH;
                    }
                    frame.setSize(w, h);
                    if (robot != null) {
                        robot.waitForIdle();
                    }
                    cnt = 0;
                }
                // try to put the device into non-default state, for example,
                // this operation below will set the transform
                vi.validate(gc);
                Graphics2D vig = (Graphics2D)vi.getGraphics();
                vig.rotate(30.0f, IMAGE_W/2, IMAGE_H/2);
                vig.drawImage(output, 0, 0,
                              IMAGE_W, IMAGE_H, null);

                frame.getGraphics().
                    drawImage(output, 0, 0, null);
                if (cnt == 90 && robot != null) {
                    robot.waitForIdle();
                    // area where we blit and should be either white or green
                    Point p = frame.getLocationOnScreen();
                    p.translate(10, 10);
                    BufferedImage bi =
                        robot.createScreenCapture(
                            new Rectangle(p.x, p.y,
                                          (IMAGE_W / 2), (IMAGE_H / 2)));
                    int accepted1[] = {Color.white.getRGB(),
                                       Color.green.getRGB()};
                    checkBI(bi, accepted1);

                    // the area where we didn't render should stay white
                    robot.waitForIdle();
                    p = frame.getLocationOnScreen();
                    p.translate(10, IMAGE_H + 10);
                    bi = robot.createScreenCapture(
                        new Rectangle(p.x, p.y,
                                      frame.getWidth() - 20,
                                      frame.getHeight() - 20 - (IMAGE_H)));
                    int accepted2[] = { Color.white.getRGB() };
                    checkBI(bi, accepted2);
                }
                Thread.yield();
            }
        } finally {
            frame.dispose();
        }
        System.out.println("Test Passed");
    }

    private static void checkBI(BufferedImage bi, int accepted[]) {
        for (int x = 0; x < bi.getWidth(); x++) {
            for (int y = 0; y < bi.getHeight(); y++) {
                int actual = bi.getRGB(x, y);
                int alpha = (actual >> 24) & 0xFF;
                int red = (actual >> 16) & 0xFF;
                int green = (actual >> 8) & 0xFF;
                int blue = (actual) & 0xFF;
                boolean found = false;
                for (int acc : accepted) {
                    int accAlpha = (acc >> 24) & 0xFF;
                    int accRed = (acc >> 16) & 0xFF;
                    int accGreen = (acc >> 8) & 0xFF;
                    int accBlue = (acc) & 0xFF;
                    if (!(Math.abs(alpha - accAlpha) > tolerance ||
                          Math.abs(red - accRed) > tolerance ||
                          Math.abs(green - accGreen) > tolerance ||
                          Math.abs(blue - accBlue) > tolerance)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    try {
                        String name = "OnScreenRenderingResizeTest.png";
                        ImageIO.write(bi, "png", new File(name));
                        System.out.println("Screen shot file: " + name);
                    } catch (IOException ex) {}
                    throw new
                        RuntimeException("Test failed at " + x + "-" + y +
                                         " rgb=0x" + Integer.
                                         toHexString(actual));
                }
            }
        }
    }
}
