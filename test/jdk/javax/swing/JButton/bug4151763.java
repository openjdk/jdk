/*
 * Copyright (c) 2000, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4151763
 * @summary Tests that button icon is not drawn upon button border
 * @library /java/awt/regtesthelpers /test/lib
 * @build PassFailJFrame jtreg.SkippedException
 * @run main/manual bug4151763
 */

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.UIManager;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import jtreg.SkippedException;

public class bug4151763 {
    private static final int IMAGE_SIZE = 150;
    private static final String INSTRUCTIONS = """
            Verify that image icon is NOT painted outside
            the black rectangle.

            If above is true press PASS else FAIL.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(bug4151763::createAndShowUI)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createAndShowUI() {
        try {
            UIManager.setLookAndFeel("com.sun.java.swing.plaf.motif.MotifLookAndFeel");
        } catch (Exception e) {
            throw new SkippedException("Unsupported LaF", e);
        }

        JFrame frame = new JFrame("bug4151763");
        final JButton b = new JButton(createImageIcon());
        b.setBorder(new CompoundBorder(
                           new EmptyBorder(20, 20, 20, 20),
                           new LineBorder(Color.BLACK)));
        b.setPreferredSize(new Dimension(100, 100));

        frame.setLayout(new FlowLayout());
        frame.add(b);
        frame.setSize(400, 300);
        return frame;
    }

    private static ImageIcon createImageIcon() {
        BufferedImage redImg = new BufferedImage(IMAGE_SIZE, IMAGE_SIZE,
                                                 BufferedImage.TYPE_INT_RGB);
        Graphics2D g = redImg.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, IMAGE_SIZE, IMAGE_SIZE);
        g.dispose();
        return new ImageIcon(redImg);
    }
}
