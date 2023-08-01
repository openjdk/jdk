/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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


/* @test
 * @bug 8302173
 * @requires (os.family == "mac")
 * @summary Tests Aqua button icon positioning with HTML text
 * @run main HtmlButtonWithTextAndIcon
 */

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import static java.awt.image.BufferedImage.TYPE_INT_ARGB;

public class HtmlButtonWithTextAndIcon {
    private static Path testDir;
    private static JButton button;
    private static BufferedImage image;
    private static final int OFFSET = 1;
    private static final int BUTTON_WIDTH = 59;
    private static final int BUTTON_HEIGHT = 28;
    private static final int ICON_WIDTH = 16;
    private static final int ICON_HEIGHT = 16;
    private static final int centerX = 16;
    private static final int centerY = 13;
    private static final int minX = centerX - (ICON_WIDTH / 2) + OFFSET;
    private static final int minY = centerY - (ICON_HEIGHT / 2) + OFFSET;
    private static final int maxX = centerX + (ICON_WIDTH / 2) - OFFSET;
    private static final int maxY = centerY + (ICON_HEIGHT / 2) - OFFSET;


    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel("com.apple.laf.AquaLookAndFeel");
        testDir = Path.of(System.getProperty("test.classes", "."));

        SwingUtilities.invokeAndWait(HtmlButtonWithTextAndIcon::createButton);
        SwingUtilities.invokeAndWait(HtmlButtonWithTextAndIcon::paintButton);

        testIconPosition(image.getRGB(centerX, centerY),
                image.getRGB(minX, minY),
                image.getRGB(minX, maxY),
                image.getRGB(maxX, minY),
                image.getRGB(maxX, maxY));

        System.out.println("PASSED");
    }

    private static void createButton() {
        button = new JButton("<html><nobr><u>Test</u>" +
                "</nobr></html>", new TestIcon());
        button.setSize(new Dimension(BUTTON_WIDTH, BUTTON_HEIGHT));
        button.setHorizontalAlignment(JButton.LEFT);
    }

    private static void paintButton() {
        image = new BufferedImage(BUTTON_WIDTH, BUTTON_HEIGHT, TYPE_INT_ARGB);
        Graphics2D graphics2D = image.createGraphics();
        button.paint(graphics2D);
        graphics2D.dispose();
    }

    private static void testIconPosition(int... colors) throws IOException {
        for (int c : colors) {
            if (c != Color.RED.getRGB()) {
                ImageIO.write(image, "png", new File(testDir + "/failImage.png"));
                throw new RuntimeException("Icon position incorrect");
            }
        }
    }

    public static class TestIcon implements Icon {

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            g.setColor(Color.RED);
            g.fillRect(x, y, ICON_WIDTH, ICON_HEIGHT);
        }

        @Override
        public int getIconWidth() {
            return ICON_WIDTH;
        }

        @Override
        public int getIconHeight() {
            return ICON_HEIGHT;
        }
    }

}
