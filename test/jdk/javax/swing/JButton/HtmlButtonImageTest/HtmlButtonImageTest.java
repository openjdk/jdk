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
 * @bug 8015854
 * @requires (os.family == "mac")
 * @summary Tests HTML image as JButton text for unwanted padding on macOS Aqua LAF
 * @run main HtmlButtonImageTest
 */

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import static java.awt.image.BufferedImage.TYPE_INT_ARGB;

public final class HtmlButtonImageTest {
    private static JButton button;
    private static Path testDir;
    private static BufferedImage image;

    private static final int BUTTON_HEIGHT = 37;
    private static final int BUTTON_WIDTH = 37;
    private static final int SQUARE_HEIGHT = 19;
    private static final int SQUARE_WIDTH = 19;
    private static final int centerX = BUTTON_WIDTH / 2;
    private static final int centerY = BUTTON_HEIGHT / 2;
    private static final int minX = centerX - (SQUARE_WIDTH / 2);
    private static final int minY = centerY - (SQUARE_HEIGHT / 2);
    private static final int maxX = centerX + (SQUARE_WIDTH / 2);
    private static final int maxY = centerY + (SQUARE_HEIGHT / 2);

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel("com.apple.laf.AquaLookAndFeel");
        testDir = Path.of(System.getProperty("test.classes", "."));
        generateRedSquare();

        SwingUtilities.invokeAndWait(HtmlButtonImageTest::createButton);
        SwingUtilities.invokeAndWait(HtmlButtonImageTest::paintButton);

        testImageCentering(image.getRGB(centerX, centerY),
                image.getRGB(minX, minY),
                image.getRGB(minX, maxY),
                image.getRGB(maxX, minY),
                image.getRGB(maxX, maxY));
    }

    private static void generateRedSquare() throws IOException {
        BufferedImage bImg = new BufferedImage(SQUARE_WIDTH, SQUARE_HEIGHT,
                TYPE_INT_ARGB);
        Graphics2D cg = bImg.createGraphics();
        cg.setColor(Color.RED);
        cg.fillRect(0, 0, SQUARE_WIDTH, SQUARE_HEIGHT);
        ImageIO.write(bImg, "png", new File(testDir + "/red_square.png"));
    }

    private static void createButton() {
        button = new JButton();
        button.setSize(new Dimension(BUTTON_WIDTH, BUTTON_HEIGHT));
        button.setText("<html><img src='"
                + testDir.resolve("red_square.png").toUri() + "'></html>");
    }

    private static void paintButton() {
        image = new BufferedImage(BUTTON_HEIGHT, BUTTON_WIDTH, TYPE_INT_ARGB);
        Graphics2D graphics2D = image.createGraphics();
        button.paint(graphics2D);
        graphics2D.dispose();
    }

    private static boolean checkRedColor(int rgb) {
        return (rgb == Color.RED.getRGB());
    }

    private static void testImageCentering(int... colors) throws IOException {
        for (int c : colors) {
            if (!checkRedColor(c)) {
                ImageIO.write(image, "png",
                        new File(testDir + "/fail_image.png"));
                throw new RuntimeException("HTML image not centered in button");
            }
        }
        System.out.println("Passed");
    }
}
