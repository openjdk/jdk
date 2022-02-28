/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8015854
 * @requires (os.family == "mac")
 * @summary Tests HTML image as JButton text for unwanted padding on Aqua LAF on MacOS
 * @run main HtmlButtonImageTest
 */

import java.awt.Point;
import java.awt.Robot;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Graphics2D;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.io.IOException;
import javax.imageio.ImageIO;
import java.io.File;
import java.awt.image.BufferedImage;

public final class HtmlButtonImageTest {
    private static JFrame frame;
    private static Point point;
    private static JButton button;
    private static Path testDir;
    private static Robot robot;

    public static final int BUTTON_HEIGHT = 37;
    public static final int BUTTON_WIDTH = 37;
    public static final int SQUARE_HEIGHT = 19;
    public static final int SQUARE_WIDTH = 19;
    public static final int PIXEL_BUFFER = 1;

    public static void main(String[] args) throws Exception {
        robot = new Robot();
        robot.setAutoDelay(100);
        robot.setAutoWaitForIdle(true);

        try {
            UIManager.setLookAndFeel("com.apple.laf.AquaLookAndFeel");

            // store path to source directory to locate image
            testDir = Path.of(System.getProperty("test.classes", "."));

            // generate red_square.png image to use as JButton text
            generateImage();

            SwingUtilities.invokeAndWait(() -> createAndShowGUI());

            robot.waitForIdle();

            // retrieve color of pixels at each edge of square image by starting at the center of the button
            setupCenterCoord();
            robot.mouseMove(point.x, point.y);

            // store each pixel color on the edge of each side of the red square
            Color leftClr = robot.getPixelColor(point.x - (SQUARE_WIDTH/2) + PIXEL_BUFFER, point.y);
            Color rightClr = robot.getPixelColor(point.x + (SQUARE_WIDTH/2) - PIXEL_BUFFER, point.y);
            Color topClr = robot.getPixelColor(point.x, point.y - (SQUARE_HEIGHT/2) + PIXEL_BUFFER);
            Color botClr = robot.getPixelColor(point.x, point.y + (SQUARE_HEIGHT/2) - PIXEL_BUFFER);

            testImageCentering(leftClr, rightClr, topClr, botClr);
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if(frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static void createAndShowGUI() {
        frame = new JFrame();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new FlowLayout());

        // create JButton of size 37x37 text set to a 19x19 image of a red square loaded through html tags
        button = new JButton();
        button.setFocusPainted(false);
        button.setPreferredSize(new Dimension(BUTTON_WIDTH, BUTTON_HEIGHT));
        button.setText("<html><img src='" + testDir.resolve("red_square.png").toUri() + "'></html>");

        frame.add(button);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static void setupCenterCoord() throws InterruptedException, InvocationTargetException {
        // adjust coordinates to be the center of the button
        SwingUtilities.invokeAndWait(() -> {
            point = button.getLocationOnScreen();
        });
        point.x += BUTTON_WIDTH / 2;
        point.y += BUTTON_HEIGHT / 2;
    }

    private static boolean checkRedness(Color c) {
        // checks for redness since anti-aliasing causes edges to be not exactly 255,0,0 rgb values
        if (c.getRed() > 250 && c.getBlue() < 10 && c.getGreen() < 10) {
            return true;
        }
        return false;
    }

    private static void testImageCentering(Color... colors) throws IOException {
        // check if all colors at each edge of square are red
        for (Color c : colors) {
            if (!checkRedness(c)) {
                // capture image of button when test fails for troubleshooting
                BufferedImage failImg = robot.createScreenCapture(new Rectangle(point.x - BUTTON_WIDTH / 2,
                        point.y - BUTTON_HEIGHT / 2, BUTTON_WIDTH, BUTTON_HEIGHT));
                ImageIO.write(failImg, "png", new File(testDir + "/fail_square.png"));
                throw new RuntimeException("HTML image not centered in button");
            }
        }
        System.out.println("-- Passed");
    }

    private static void generateImage() throws IOException {
        BufferedImage bImg = new BufferedImage(SQUARE_WIDTH, SQUARE_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D cg = bImg.createGraphics();
        // paint a red square onto cg
        cg.setColor(Color.RED);
        cg.fillRect(0, 0, SQUARE_WIDTH, SQUARE_HEIGHT);
        ImageIO.write(bImg, "png", new File(testDir + "/red_square.png"));
    }
}