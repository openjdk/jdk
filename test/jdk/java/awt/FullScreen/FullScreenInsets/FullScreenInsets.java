/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

import javax.imageio.ImageIO;
import java.awt.AWTException;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/*
 * @test
 * @key headful
 * @bug 8003173 7019055
 * @summary Full-screen windows should have the proper insets.
 * @requires (os.family != "linux")
 */

public final class FullScreenInsets {

    private static boolean passed = true;
    private static Robot robot = null;
    private static int deviceCount = 0;
    private static final float TOLERANCE = 2;

    public static void main(final String[] args) throws IOException, AWTException {
        final GraphicsEnvironment ge = GraphicsEnvironment
                .getLocalGraphicsEnvironment();
        final GraphicsDevice[] devices = ge.getScreenDevices();
        System.out.println("No. of Screen Devices: "+ devices.length + "\n");

        final Window wGreen = new Frame();
        wGreen.setBackground(Color.GREEN);
        wGreen.setSize(300, 300);
        wGreen.setVisible(true);
        sleep();
        final Insets iGreen = wGreen.getInsets();
        final Dimension sGreen = wGreen.getSize();

        final Window wRed = new Frame();
        wRed.setBackground(Color.RED);
        wRed.setSize(300, 300);
        wRed.setVisible(true);
        sleep();
        final Insets iRed = wRed.getInsets();
        final Dimension sRed = wRed.getSize();

        for (final GraphicsDevice device : devices) {
            if (!device.isFullScreenSupported()) {
                continue;
            }
            Rectangle expectedBounds = device.getDefaultConfiguration().getBounds();
            System.out.println("Testing on Screen Device# "+ deviceCount++);
            device.setFullScreenWindow(wGreen);
            sleep();
            testWindowBounds(expectedBounds, wGreen);
            testColor(wGreen, Color.GREEN, "GREEN_" + deviceCount + ".png");

            device.setFullScreenWindow(wRed);
            sleep();
            testWindowBounds(expectedBounds, wRed);
            testColor(wRed, Color.RED, "RED_" + deviceCount + ".png");

            device.setFullScreenWindow(null);
            sleep();
            testInsets(wGreen.getInsets(), iGreen);
            testInsets(wRed.getInsets(), iRed);
            testSize(wGreen.getSize(), sGreen);
            testSize(wRed.getSize(), sRed);
        }
        wGreen.dispose();
        wRed.dispose();
        if (!passed) {
            throw new RuntimeException("Test failed");
        }
    }

    private static void testSize(final Dimension actual, final Dimension exp) {
        if (!exp.equals(actual)) {
            System.out.println(" Wrong window size:" +
                               " Expected: " + exp + " Actual: " + actual);
            passed = false;
        }
    }

    private static void testInsets(final Insets actual, final Insets exp) {
        if (!actual.equals(exp)) {
            System.out.println(" Wrong window insets:" +
                               " Expected: " + exp + " Actual: " + actual);
            passed = false;
        }
    }

    private static void testWindowBounds(final Rectangle expectedSize, final Window w) {
        if (w.getWidth() != expectedSize.getWidth()
                || w.getHeight() != expectedSize.getHeight()) {
            System.out.println(" Wrong window bounds:" +
                    " Expected: width = " + expectedSize.getWidth()
                    + ", height = " + expectedSize.getHeight() + " Actual: "
                    + w.getSize());
            passed = false;
        }
    }

    private static void testColor(final Window w, final Color color, final String filename)
            throws IOException {
        final Robot r;
        float[] expectedRGB = color.getRGBColorComponents(null);
        try {
            r = new Robot(w.getGraphicsConfiguration().getDevice());
        } catch (AWTException e) {
            e.printStackTrace();
            passed = false;
            return;
        }
        final BufferedImage bimg = r.createScreenCapture(w.getBounds());
        // vertical scan - at the far right side
        int x = bimg.getWidth() - 1;
        for (int y= 0; y < bimg.getHeight() - 1; y++) {
            float[] actualRGB = new Color(bimg.getRGB(x, y))
                    .getRGBColorComponents(null);
            if (checkColor(actualRGB, expectedRGB)) {
                System.out.println(
                        "Vertical Scan: Incorrect pixel at " + x + "x" + y + " : " +
                                Integer.toHexString(bimg.getRGB(x, y)) +
                                " ,expected : " + Integer.toHexString(
                                color.getRGB()));
                passed = false;
                break;
            }
        }
        // horizontal scan - at the far bottom side
        int y = bimg.getHeight() - 1;
        for (x= 0; x < bimg.getWidth() - 1; x++) {
            float[] actualRGB = new Color(bimg.getRGB(x, y))
                    .getRGBColorComponents(null);
            if (checkColor(actualRGB, expectedRGB)) {
                System.out.println(
                        "Horizontal Scan: Incorrect pixel at " + x + "x" + y + " : " +
                                Integer.toHexString(bimg.getRGB(x, y)) +
                                " ,expected : " + Integer.toHexString(
                                color.getRGB()));
                passed = false;
                break;
            }
        }
        if (!passed) {
            ImageIO.write(bimg, "png", new File(filename));
        }
    }

    private static boolean checkColor(float[] actualRGB, float[] expectedRGB) {
        return (Math.abs(actualRGB[0] - expectedRGB[0]) > TOLERANCE
                || Math.abs(actualRGB[1] - expectedRGB[1]) > TOLERANCE
                || Math.abs(actualRGB[2] - expectedRGB[2]) > TOLERANCE);
    }

    private static void sleep() {
        if (robot == null) {
            try {
                robot = new Robot();
            } catch(AWTException ae) {
                ae.printStackTrace();
                throw new RuntimeException("Cannot create Robot.");
            }
        }
        robot.waitForIdle();
        try {
            Thread.sleep(2000);
        } catch (InterruptedException ignored) {
        }
    }
}
