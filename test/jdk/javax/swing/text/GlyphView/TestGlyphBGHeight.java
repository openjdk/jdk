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
 * @summary Verifies if Background is painted taller than needed for styled text.
 * @run main TestGlyphBGHeight
 */

import java.io.File;
import java.awt.Graphics;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JTextPane;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

public class TestGlyphBGHeight {

    private static final int WIDTH = 100;
    private static final int HEIGHT = 100;
    private static final int FONTSIZE = 32;
    private static final Color EMPTY_PIXEL = new Color(0xFFFFFFFF);

    static BufferedImage createImage() throws Exception {
        final BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics g = img.getGraphics();
        g.setColor(EMPTY_PIXEL);
        g.fillRect(0, 0, WIDTH, HEIGHT);
        return img;
    }

    public static void main(String[] args) throws Exception {

        final BufferedImage img = createImage();
        final JTextPane textPane = new JTextPane();
        final StyledDocument doc = textPane.getStyledDocument();

        Style style = textPane.addStyle("superscript", null);
        StyleConstants.setSuperscript(style, true);
        StyleConstants.setFontSize(style, FONTSIZE);
        StyleConstants.setBackground(style, Color.YELLOW);
        try {
            doc.insertString(doc.getLength(), "hello", style);
        } catch (Exception e) {}

        textPane.setSize(WIDTH, HEIGHT);
        textPane.setBackground(Color.RED);

        textPane.paint(img.getGraphics());

        ImageIO.write(img, "png", new File("AppTest.png"));

        BufferedImage bimg = img.getSubimage(0, FONTSIZE + 20, WIDTH, 1);
        ImageIO.write(bimg, "png", new File("AppTest1.png"));
        for (int x = 10; x < WIDTH / 2; x++) {
            int col = bimg.getRGB(x, 0);
            System.out.println(Integer.toHexString(col));
            if (col == Color.YELLOW.getRGB()) {
                throw new RuntimeException(" Background is painted taller than needed for styled text");
            }
        }
    }
}
