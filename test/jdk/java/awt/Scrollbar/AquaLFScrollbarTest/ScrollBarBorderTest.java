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

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.Buffer;

import static java.awt.image.BufferedImage.TYPE_INT_ARGB;
import static java.awt.image.BufferedImage.TYPE_INT_RGB;

/*
 * @test
 * @bug 8190264
 * @summary JScrollBar ignores its border when using macOS Mac OS X Aqua look and feel
 * @run main ScrollBarBorderTest
 */
public class ScrollBarBorderTest {

    // On macOS 10.12.6 using the Mac look and feel (com.apple.laf.AquaLookAndFeel)
    // the scroll bar ignores the custom border and allows the scroll thumb to move
    // beneath the border. Run with:
    // java ScrollBarBorderTest

    // If run using any other look and feel (e.g. Metal) then the right side of
    // the scroll bar stops at the border as expected. Run with:
    // java -Dswing.defaultlaf=javax.swing.plaf.metal.MetalLookAndFeel ScrollBarBorderTest

    // Java version: 1.8.0_151

    private static JScrollBar scrollBar;
    private static JPanel panel;
    private static JFrame frame;

    public void createAndShowGUI() throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                // create scroll bar
                scrollBar = new JScrollBar(Scrollbar.HORIZONTAL);
                scrollBar.setBorder(new CustomBorder());

                // create panel
                panel = new JPanel();
                panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
                panel.setBorder(new EmptyBorder(20, 20, 20, 20));
                panel.setSize(200,90);
                panel.add(new JLabel(UIManager.getLookAndFeel().toString()));
                panel.add(Box.createVerticalStrut(20));
                panel.add(scrollBar);

                // create frame
                frame = new JFrame("ScrollBarBorderTest");
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.getContentPane().add(panel);
                frame.pack();

                frame.setVisible(true);
            }
        });
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

    public void test() throws Exception {
        createAndShowGUI();
        BufferedImage image = new BufferedImage(panel.getWidth(),panel.getHeight(),TYPE_INT_ARGB);
        Graphics2D graphics2D = image.createGraphics();
        panel.paint(graphics2D);
        graphics2D.dispose();
        scrollBar.setValue(Integer.MAX_VALUE);
        BufferedImage image2 = new BufferedImage(panel.getWidth(),panel.getHeight(),TYPE_INT_ARGB);
        Graphics2D graphics2D2 = image2.createGraphics();
        panel.paint(graphics2D2);
        graphics2D2.dispose();

        for (int i = 450; i < image.getWidth(); i++) {
            for (int j = 70; j < image.getHeight(); j++) {
                int c1 = image.getRGB(i,j);
                int c2 = image2.getRGB(i,j);
                if(c1 != c2) {
                    System.out.println(i + " " + j + " " + "Color1: " + c1);
                    System.out.println(i + " " + j + " " + "Color2: " + c2);
                    throw new RuntimeException();
                }
            }
        }
    }

    public void done() throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                frame.dispose();
            }
        });
    }

    public static void main(String[] args) throws Exception {
        ScrollBarBorderTest borderTest = new ScrollBarBorderTest();

        for (UIManager.LookAndFeelInfo laf : UIManager.getInstalledLookAndFeels()) {
            try {
                SwingUtilities.invokeAndWait(() -> setLookAndFeel(laf));
                borderTest.test();
            } finally {
                borderTest.done();
            }
        }
    }

    // custom border
    private static class CustomBorder implements Border {
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            g.setColor(new Color(255, 0, 0, 100));
            g.fillRect(width - 150, y, width, height);
        }

        public Insets getBorderInsets(Component c) {
            return new Insets(0, 0, 0, 150);
        }

        public boolean isBorderOpaque() {
            return true;
        }
    }
}
