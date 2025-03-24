/*
 * Copyright (c) 2000, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/*
 * @test
 * @key headful
 * @bug 8342098
 * @summary Verify that the image captured from the screen using a Robot
 * and the source image are same.
 * @requires os.family == "mac"
 * @run main/othervm ScreenCaptureRobotTest
 */

/*
 * @test
 * @key headful
 * @bug 8342098
 * @summary Verify that the image captured from the screen using a Robot
 * and the source image are same.
 * @requires os.family != "mac"
 * @run main/othervm -Dsun.java2d.uiScale=1 ScreenCaptureRobotTest
 */
public class ScreenCaptureRobotTest {

    private static final int IMAGE_WIDTH = 200;
    private static final int IMAGE_HEIGHT = 100;
    private static final int OFFSET = 10;

    private static Frame frame;
    private static Canvas canvas;

    private static BufferedImage realImage;

    private static volatile Point point;

    public static void main(String[] args) throws Exception {
        try {
            EventQueue.invokeAndWait(ScreenCaptureRobotTest::initializeGUI);
            doTest();
        } finally {
            EventQueue.invokeAndWait(ScreenCaptureRobotTest::disposeFrame);
        }
    }

    private static void initializeGUI() {
        frame = new Frame("ScreenCaptureRobotTest Frame");
        realImage = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice()
                .getDefaultConfiguration()
                .createCompatibleImage(IMAGE_WIDTH, IMAGE_HEIGHT);

        Graphics g = realImage.createGraphics();
        g.setColor(Color.YELLOW);
        g.fillRect(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);
        g.setColor(Color.RED);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        g.drawString("Capture This", 10, 40);
        g.dispose();

        canvas = new ImageCanvas();
        canvas.setBackground(Color.YELLOW);
        canvas.setPreferredSize(new Dimension(IMAGE_WIDTH + (OFFSET * 2),
                IMAGE_HEIGHT + (OFFSET * 2)));
        frame.setLayout(new BorderLayout());
        frame.add(canvas);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static void doTest() throws Exception {
        Robot robot = new Robot();
        robot.mouseMove(0, 0);
        robot.waitForIdle();
        robot.delay(500);

        EventQueue.invokeAndWait(() -> point = canvas.getLocationOnScreen());

        Rectangle rect = new Rectangle(point.x + OFFSET, point.y + OFFSET,
                IMAGE_WIDTH, IMAGE_HEIGHT);

        BufferedImage capturedImage = robot.createScreenCapture(rect);

        if (!compareImages(capturedImage, realImage)) {
            String errorMessage = "FAIL : Captured Image is different from "
                    + "the real image";
            saveImage(capturedImage, "CapturedImage.png");
            saveImage(realImage, "RealImage.png");
            throw new RuntimeException(errorMessage);
        }
    }

    private static boolean compareImages(BufferedImage capturedImg,
            BufferedImage realImg) {
        int capturedPixel;
        int realPixel;
        int imgWidth = capturedImg.getWidth();
        int imgHeight = capturedImg.getHeight();

        if (imgWidth != IMAGE_WIDTH || imgHeight != IMAGE_HEIGHT) {
            System.out.println("Captured and real images are different in size");
            return false;
        }

        for (int i = 0; i < imgWidth; i++) {
            for (int j = 0; j < imgHeight; j++) {
                capturedPixel = capturedImg.getRGB(i, j);
                realPixel = realImg.getRGB(i, j);
                if (capturedPixel != realPixel) {
                    System.out.println("Captured pixel ("
                            + Integer.toHexString(capturedPixel) + ") at "
                            + "(" + i + ", " + j + ") is not equal to real pixel ("
                            + Integer.toHexString(realPixel) + ")");
                    return false;
                }
            }
        }
        return true;
    }

    private static class ImageCanvas extends Canvas {
        @Override
        public void paint(Graphics g) {
            g.drawImage(realImage, OFFSET, OFFSET, this);
        }
    }

    private static void saveImage(BufferedImage image, String fileName) {
        try {
            ImageIO.write(image, "png", new File(fileName));
        } catch (IOException ignored) {
            System.err.println(ignored.getMessage());
        }
    }

    private static void disposeFrame() {
        if (frame != null) {
            frame.dispose();
        }
    }
}
