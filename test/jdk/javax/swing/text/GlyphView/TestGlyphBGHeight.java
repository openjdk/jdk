/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8017266
 * @key headful
 * @summary Verifies if Background is painted taller than needed for styled text.
 * @run main TestGlyphBGHeight
 */

import java.io.File;
import java.awt.Graphics2D;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.Robot;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JTextPane;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.SwingUtilities;

public class TestGlyphBGHeight {

    static JFrame frame;

    public static void main(String[] args) throws Exception {
        int width = 100;
        int height = 100;

        try {
            Robot robot = new Robot();
            SwingUtilities.invokeAndWait(() -> {
                frame = new JFrame("TestGlyphBGHeight");
                frame.setSize(width, height);
                frame.getContentPane().setLayout(new BorderLayout());

                final JTextPane comp = new JTextPane();
                final StyledDocument doc = comp.getStyledDocument();

                Style style = comp.addStyle("superscript", null);
                StyleConstants.setSuperscript(style, true);
                StyleConstants.setFontSize(style, 32);
                StyleConstants.setBackground(style, Color.YELLOW);
                try {
                    doc.insertString(doc.getLength(), "hello", style);
                } catch (Exception e) {}

                comp.setDocument(doc);
                comp.setBackground(Color.RED);

                frame.getContentPane().add(comp, BorderLayout.CENTER);

                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            });
            robot.waitForIdle();
            robot.delay(1000);

            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = (Graphics2D) img.getGraphics();
            frame.paint(g2d);
            ImageIO.write(img, "png", new File("AppTest.png"));
            g2d.dispose();

            BufferedImage bimg = img.getSubimage(0, 80, width, 1);
            ImageIO.write(bimg, "png", new File("AppTest1.png"));
            robot.waitForIdle();
            robot.delay(1000);
            for (int x = 10; x < width / 2; x++) {
                Color col = new Color(bimg.getRGB(x, 0));
                System.out.println(Integer.toHexString(bimg.getRGB(x, 0)));
                if (col.equals(Color.YELLOW)) {
                    throw new RuntimeException(" Background is painted taller than needed for styled text");
                }
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }
}
