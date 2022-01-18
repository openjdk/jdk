/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.JScrollBar;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.border.Border;

import static java.awt.image.BufferedImage.TYPE_INT_ARGB;

/*
 * @test
 * @bug 8190264
 * @summary JScrollBar ignores its border when using macOS Mac OS X Aqua look and feel
 * @run main ScrollBarBorderTest
 */
public class ScrollBarBorderTest {
    private static JScrollBar scrollBar;
    public static final int BORDER_WIDTH = 150;
    public static final int WIDTH = BORDER_WIDTH + 200;
    public static final int HEIGHT = 20;

    public static void main(String[] args) throws Exception {
        for (UIManager.LookAndFeelInfo laf : UIManager.getInstalledLookAndFeels()) {
            SwingUtilities.invokeAndWait(() -> setLookAndFeel(laf));
            SwingUtilities.invokeAndWait(ScrollBarBorderTest::testHorizontal);
            SwingUtilities.invokeAndWait(ScrollBarBorderTest::testVertical);
        }
    }

    public static void testHorizontal() {
        // create scroll bar
        scrollBar = new JScrollBar(JScrollBar.HORIZONTAL);
        scrollBar.setSize(WIDTH, HEIGHT);
        scrollBar.setBorder(new HorizontalCustomBorder());

        // paint image with thumb set to default value
        BufferedImage image1 = new BufferedImage(WIDTH, HEIGHT, TYPE_INT_ARGB);
        Graphics2D graphics2D = image1.createGraphics();
        scrollBar.paint(graphics2D);
        graphics2D.dispose();

        // paint image with thumb set to max value
        scrollBar.setValue(Integer.MAX_VALUE);
        BufferedImage image2 = new BufferedImage(WIDTH, HEIGHT, TYPE_INT_ARGB);
        Graphics2D graphics2D2 = image2.createGraphics();
        scrollBar.paint(graphics2D2);
        graphics2D2.dispose();

        // check border for thumb
        for (int x = WIDTH - BORDER_WIDTH; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                int c1 = image1.getRGB(x, y);
                int c2 = image2.getRGB(x, y);
                if (c1 != c2) {
                    System.out.println(x + " " + y + " " + "Color1: "
                                       + Integer.toHexString(c1));
                    System.out.println(x + " " + y + " " + "Color2: "
                                       + Integer.toHexString(c2));
                    saveImage(image1, "himage1.png");
                    saveImage(image2, "himage2.png");
                    throw new RuntimeException("Horizontal border has a thumb in it");
                }
            }
        }
    }

    public static void testVertical() {
        // create scroll bar
        scrollBar = new JScrollBar(JScrollBar.VERTICAL);
        scrollBar.setSize(HEIGHT, WIDTH);
        scrollBar.setBorder(new VerticalCustomBorder());

        // paint image with thumb set to 0
        scrollBar.setValue(0);
        BufferedImage image1 = new BufferedImage(HEIGHT, WIDTH, TYPE_INT_ARGB);
        Graphics2D graphics2D = image1.createGraphics();
        scrollBar.paint(graphics2D);
        graphics2D.dispose();

        // paint image with thumb set to max value
        scrollBar.setValue(Integer.MAX_VALUE);
        BufferedImage image2 = new BufferedImage(HEIGHT, WIDTH, TYPE_INT_ARGB);
        Graphics2D graphics2D2 = image2.createGraphics();
        scrollBar.paint(graphics2D2);
        graphics2D2.dispose();

        // check border for thumb
        for (int y = WIDTH - BORDER_WIDTH; y < WIDTH; y++) {
            for (int x = 0; x < HEIGHT; x++) {
                int c1 = image1.getRGB(x, y);
                int c2 = image2.getRGB(x, y);
                if (c1 != c2) {
                    System.out.println(x + " " + y + " " + "Color1: "
                                       + Integer.toHexString(c1));
                    System.out.println(x + " " + y + " " + "Color2: "
                                       + Integer.toHexString(c2));
                    saveImage(image1, "vimage1.png");
                    saveImage(image2, "vimage2.png");
                    throw new RuntimeException("Vertical border has a thumb in it");
                }
            }
        }
    }

    private static void setLookAndFeel(UIManager.LookAndFeelInfo laf) {
        try {
            UIManager.setLookAndFeel(laf.getClassName());
            System.out.println(laf.getName());
        } catch (UnsupportedLookAndFeelException ignored){
            System.out.println("Unsupported LookAndFeel: " + laf.getClassName());
        } catch (ClassNotFoundException | InstantiationException |
                IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static void saveImage(BufferedImage image, String filename) {
        try {
            ImageIO.write(image, "png", new File(filename));
        } catch (IOException e) {
            // Don't propagate the exception
            e.printStackTrace();
        }
    }

    // custom border
    private static class HorizontalCustomBorder implements Border {
        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            g.setColor(new Color(255, 0, 0, 100));
            g.fillRect(width - BORDER_WIDTH, y, width, height);
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(0, 0, 0, BORDER_WIDTH);
        }

        @Override
        public boolean isBorderOpaque() {
            return true;
        }
    }

    // custom border
    private static class VerticalCustomBorder implements Border {
        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            g.setColor(new Color(255, 0, 0, 100));
            g.fillRect(x, height - BORDER_WIDTH, width, height);
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(0, 0, BORDER_WIDTH, 0);
        }

        @Override
        public boolean isBorderOpaque() {
            return true;
        }
    }
}
