/*
 * Copyright (c) 2000,2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;

/*
 * @test
 * @key headful
 * @bug 8342098
 * @summary Verify that the image captured from the screen using a Robot
 * and the source image are same.
 * @run main ScreenCaptureRobotTest
 */
public class ScreenCaptureRobotTest {

    private static int delay = 500;
    private static Frame frame;
    private static volatile Canvas canvas;
    private static BufferedImage realImage;
    private static BufferedImage displayImage;
    private static int difference;

    public static void main(String[] args) throws Exception {
        try {
            initializeGUI();
            doTest();
        } finally {
            EventQueue.invokeAndWait(ScreenCaptureRobotTest::disposeFrame);
        }
    }

    private static void initializeGUI() {
        frame = new Frame("ScreenCaptureRobotTest Frame");
        realImage = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration()
                .createCompatibleImage(200, 100);

        Graphics g = realImage.createGraphics();
        g.setColor(Color.yellow);
        g.fillRect(0, 0, 200, 100);
        g.setColor(Color.red);
        g.setFont(new Font("SansSerif", Font.BOLD, 30));
        g.drawString("Capture This", 10, 40);
        g.dispose();
        displayImage = realImage;

        canvas = new ImageCanvas();
        canvas.setBackground(Color.YELLOW);
        frame.setLayout(new BorderLayout());
        frame.add(canvas);
        frame.setSize(300, 200);
        frame.setLocation(100, 100);
        frame.setVisible(true);
        canvas.requestFocus();
    }

    private static void doTest() throws Exception {
        Robot robot;
        robot = new Robot();
        robot.delay(delay);
        robot.waitForIdle();

        Point pnt = canvas.getLocationOnScreen();
        Rectangle rect = new Rectangle(pnt.x + 10, pnt.y + 10, 200, 100);

        // Capturing Image using Robot
        BufferedImage capturedImage = robot.createScreenCapture(rect);

        if (!compareImages(capturedImage, realImage)) {
            String errorMessage = "FAIL : Captured Image is different from "
                    + "the actual image by " + difference + " pixels";
            System.err.println("Test failed");
            throw new RuntimeException(errorMessage);
        }
        System.out.println("Test passed");
    }

    private static boolean compareImages(BufferedImage capturedImg,
            BufferedImage realImg) {
        int capturedPixel;
        int realPixel;
        int imgWidth;
        int imgHeight;
        int toleranceLevel = 0;
        boolean result = true;

        imgWidth = capturedImg.getWidth(null);
        imgHeight = capturedImg.getHeight(null);

        // Loop through each pixel in both images
        for (int i = 0; i < (imgWidth); i++) {
            for (int j = 0; j < (imgHeight); j++) {
                // Get the RGB values of each pixel in both images
                capturedPixel = capturedImg.getRGB(i, j);
                realPixel = realImg.getRGB(i, j);
                // Compare the pixel values
                if (capturedPixel != realPixel) {
                    toleranceLevel++;
                }
            }
        }

        difference = toleranceLevel;
        if (toleranceLevel > 10) {
            result = false;
        }
        System.out.println("\nCaptured Image differs from Real Image by "
                + toleranceLevel + " Pixels\n");
        return result;
    }

    private static class ImageCanvas extends Canvas {
        @Override
        public void paint(Graphics g) {
            g.drawImage(displayImage, 10, 10, this);
        }
    }

    private static void disposeFrame() {
        if (frame != null) {
            frame.dispose();
            frame = null;
        }
    }
}
