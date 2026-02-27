/*
 * Copyright (c) 2014, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Color;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.SplashScreen;
import java.awt.TextField;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

/*
 * @test
 * @key headful
 * @bug 8043869 8075244 8078082 8145173 8151787 8212213
 * @summary Tests the HiDPI splash screen support for windows and MAC
 * @run main MultiResolutionSplashTest GENERATE_IMAGES
 * @run main/othervm -splash:splash1.png MultiResolutionSplashTest TEST_SPLASH 0
 * @run main/othervm -splash:splash2 MultiResolutionSplashTest TEST_SPLASH 1
 * @run main/othervm -splash:splash3. MultiResolutionSplashTest TEST_SPLASH 2
 * @run main/othervm -splash:splash1.png MultiResolutionSplashTest TEST_FOCUS
 */
public class MultiResolutionSplashTest {

    private static final int IMAGE_WIDTH = 300;
    private static final int IMAGE_HEIGHT = 200;

    private static final boolean isMac = System.getProperty("os.name")
                                               .contains("OS X");

    private static final ImageInfo[] tests = {
        new ImageInfo("splash1", ".png", Color.BLUE,   Color.GREEN),
        new ImageInfo("splash2", "",     Color.WHITE,  Color.BLACK),
        new ImageInfo("splash3", ".",    Color.YELLOW, Color.RED)
    };

    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "GENERATE_IMAGES":
                generateImages();
                break;
            case "TEST_SPLASH":
                testSplash(tests[Integer.parseInt(args[1])]);
                break;
            case "TEST_FOCUS":
                testFocus();
                break;
            default:
                throw new RuntimeException("Unknown test: " + args[0]);
        }
    }

    static void testSplash(ImageInfo test) throws Exception {
        SplashScreen splashScreen = SplashScreen.getSplashScreen();
        if (splashScreen == null) {
            throw new RuntimeException("Splash screen is not shown!");
        }

        final Rectangle splashBounds = splashScreen.getBounds();
        final double scaleFactor = getScreenScaleFactor();

        final Robot robot = new Robot();

        BufferedImage splashCapture = robot.createScreenCapture(splashBounds);

        if (splashBounds.width != IMAGE_WIDTH) {
            throw new RuntimeException(
                    "SplashScreen#getBounds has wrong width");
        }
        if (splashBounds.height != IMAGE_HEIGHT) {
            throw new RuntimeException(
                    "SplashScreen#getBounds has wrong height");
        }

        Color splashScreenColor =
                new Color(splashCapture.getRGB(splashBounds.width / 2,
                                               splashBounds.height / 2));
        Color testColor = (1 < scaleFactor) ? test.color2x : test.color1x;

        if (!compare(testColor, splashScreenColor)) {
            String captureFileName = "splashscreen-%1.2f-%s.png"
                                     .formatted(scaleFactor, test.name1x);
            saveImageNoError(splashCapture, new File(captureFileName));
            throw new RuntimeException(
                    "Image with wrong resolution is used for splash screen! "
                    + "Refer to " + captureFileName);
        }
    }

    static void testFocus() throws Exception {
        Robot robot = new Robot();
        robot.setAutoWaitForIdle(true);
        robot.setAutoDelay(50);

        Frame frame = new Frame();
        frame.setSize(100, 100);
        String test = "123";
        TextField textField = new TextField(test);
        textField.selectAll();
        frame.add(textField);
        frame.setVisible(true);
        robot.waitForIdle();
        robot.delay(1000);

        robot.keyPress(KeyEvent.VK_A);
        robot.keyRelease(KeyEvent.VK_A);
        robot.keyPress(KeyEvent.VK_B);
        robot.keyRelease(KeyEvent.VK_B);
        robot.waitForIdle();

        frame.dispose();

        if (!textField.getText().equals("ab")) {
            throw new RuntimeException("Focus is lost! " +
                "Expected 'ab' got " + "'" + textField.getText() + "'.");
        }
    }

    static boolean compare(Color c1, Color c2) {
        return compare(c1.getRed(), c2.getRed())
                && compare(c1.getGreen(), c2.getGreen())
                && compare(c1.getBlue(), c2.getBlue());
    }

    static boolean compare(int n, int m) {
        return Math.abs(n - m) <= 50;
    }

    static void generateImages() throws Exception {
        for (ImageInfo test : tests) {
            generateImage(test.name1x, test.color1x, 1.0);

            // Ensure the second image uses scale greater than 1.0
            double scale = getAdjustedScaleFactor();
            generateImage(test.name2x, test.color2x, scale);
        }
    }

    static void generateImage(final String name,
                              final Color color,
                              final double scale) throws Exception {
        File file = new File(name);
        if (file.exists()) {
            return;
        }

        final int width = (int) (scale * IMAGE_WIDTH);
        final int height = (int) (scale * IMAGE_HEIGHT);
        BufferedImage image = new BufferedImage(width,
                                                height,
                                                BufferedImage.TYPE_INT_RGB);
        Graphics g = image.getGraphics();
        g.setColor(color);
        g.fillRect(0, 0, width, height);

        saveImage(image, file);
    }

    private static void saveImage(BufferedImage image,
                                  File file) throws IOException {
        ImageIO.write(image, "png", file);
    }

    private static void saveImageNoError(BufferedImage image,
                                         File file) {
        try {
            saveImage(image, file);
        } catch (IOException ignored) {
        }
    }

    static double getScreenScaleFactor() {
        return GraphicsEnvironment
               .getLocalGraphicsEnvironment()
               .getDefaultScreenDevice()
               .getDefaultConfiguration()
               .getDefaultTransform()
               .getScaleX();
    }

    // Ensure the second image uses scale greater than 1.0
    static double getAdjustedScaleFactor() {
        double scale = getScreenScaleFactor();
        return scale < 1.25 ? 2.0 : scale;
    }

    static class ImageInfo {
        final String name1x;
        final String name2x;
        final Color color1x;
        final Color color2x;

        public ImageInfo(String baseName, String ext,
                         Color color1x, Color color2x) {
            this.name1x = baseName + ext;
            this.name2x = createName2x(baseName, ext);
            this.color1x = color1x;
            this.color2x = color2x;
        }

        private static String createName2x(String baseName, String ext) {
            double scale = getAdjustedScaleFactor();
            if (!isMac && (((int) (scale * 100)) % 100 != 0)) {
                return baseName + "@" + ((int) (scale * 100)) + "pct" + ext;
            } else {
                return baseName + "@" + ((int) scale) + "x" + ext;
            }
        }
    }
}
