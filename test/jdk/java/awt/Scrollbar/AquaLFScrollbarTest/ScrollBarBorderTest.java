/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.reflect.InvocationTargetException;
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

    public static void test() {
        // create scroll bar
        scrollBar = new JScrollBar(JScrollBar.HORIZONTAL);
        scrollBar.setBorder(new CustomBorder());
        scrollBar.setSize(WIDTH, HEIGHT);

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
        for (int i = WIDTH - BORDER_WIDTH; i < WIDTH; i++) {
            for (int j = 0; j < HEIGHT; j++) {
                int c1 = image1.getRGB(i,j);
                int c2 = image2.getRGB(i,j);
                if(c1 != c2) {
                    System.out.println(i + " " + j + " " + "Color1: "
                                       + Integer.toHexString(c1));
                    System.out.println(i + " " + j + " " + "Color2: "
                                       + Integer.toHexString(c2));
                    saveImage(image1, "image1.png");
                    saveImage(image2, "image2.png");
                    throw new RuntimeException("Border has a thumb in it");
                }
            }
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

    public static void main(String[] args) throws Exception {
        for (UIManager.LookAndFeelInfo laf : UIManager.getInstalledLookAndFeels()) {
            try {
                SwingUtilities.invokeAndWait(() -> setLookAndFeel(laf));
                SwingUtilities.invokeAndWait(ScrollBarBorderTest::test);
            } catch (InvocationTargetException e) {
                throw new RuntimeException("Border has a thumb in it");
            }
        }
    }

    // custom border
    private static class CustomBorder implements Border {
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            g.setColor(new Color(255, 0, 0, 100));
            g.fillRect(width - BORDER_WIDTH, y, width, height);
        }

        public Insets getBorderInsets(Component c) {
            return new Insets(0, 0, 0, 150);
        }

        public boolean isBorderOpaque() {
            return true;
        }
    }
}
