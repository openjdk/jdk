/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4154572
 * @summary Tests that the area behind a TitledBorder's title string is transparent,
 * allowing the component's background to show through
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual TransparentTitleTest
 */

import java.awt.GridLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Color;
import java.awt.image.BufferedImage;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.border.TitledBorder;
import javax.swing.border.LineBorder;

public class TransparentTitleTest {
    private static final String INSTRUCTIONS = """
            If all panels are correctly painted such that the title of the
            border allows the underlying panel image (green rectangle)
            to show through the background of the text,
            then this test passes; else it fails.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("TransparentTitleTest Instructions")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(TransparentTitleTest::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createTestUI() {
        JFrame frame = new JFrame("TransparentTitleTest");

        frame.setLayout(new GridLayout(3, 6, 5, 5));

        frame.add(new ImagePanel(TitledBorder.TOP, TitledBorder.LEFT));
        frame.add(new ImagePanel(TitledBorder.TOP, TitledBorder.CENTER));
        frame.add(new ImagePanel(TitledBorder.TOP, TitledBorder.RIGHT));
        frame.add(new ImagePanel(TitledBorder.ABOVE_TOP, TitledBorder.LEFT));
        frame.add(new ImagePanel(TitledBorder.ABOVE_TOP, TitledBorder.CENTER));
        frame.add(new ImagePanel(TitledBorder.ABOVE_TOP, TitledBorder.RIGHT));
        frame.add(new ImagePanel(TitledBorder.BELOW_TOP, TitledBorder.LEFT));
        frame.add(new ImagePanel(TitledBorder.BELOW_TOP, TitledBorder.CENTER));
        frame.add(new ImagePanel(TitledBorder.BELOW_TOP, TitledBorder.RIGHT));
        frame.add(new ImagePanel(TitledBorder.BOTTOM, TitledBorder.LEFT));
        frame.add(new ImagePanel(TitledBorder.BOTTOM, TitledBorder.CENTER));
        frame.add(new ImagePanel(TitledBorder.BOTTOM, TitledBorder.RIGHT));
        frame.add(new ImagePanel(TitledBorder.ABOVE_BOTTOM, TitledBorder.LEFT));
        frame.add(new ImagePanel(TitledBorder.ABOVE_BOTTOM, TitledBorder.CENTER));
        frame.add(new ImagePanel(TitledBorder.ABOVE_BOTTOM, TitledBorder.RIGHT));
        frame.add(new ImagePanel(TitledBorder.BELOW_BOTTOM, TitledBorder.LEFT));
        frame.add(new ImagePanel(TitledBorder.BELOW_BOTTOM, TitledBorder.CENTER));
        frame.add(new ImagePanel(TitledBorder.BELOW_BOTTOM, TitledBorder.RIGHT));

        frame.pack();
        return frame;
    }
}

class ImagePanel extends JPanel {

    private final ImageIcon imageIcon;

    private static final BufferedImage bufferedImage =
            new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);

    static {
        Graphics g = bufferedImage.getGraphics();
        g.setColor(Color.GREEN);
        g.fillRect(0, 0, 128, 128);
    }

    public ImagePanel(int titlePos, int titleJust) {
        imageIcon = new ImageIcon(bufferedImage);

        TitledBorder b = new TitledBorder(new LineBorder(Color.black,3), "title text");
        b.setTitlePosition(titlePos);
        b.setTitleJustification(titleJust);
        b.setTitleColor(Color.black);
        setBorder(b);
    }

    public Dimension getPreferredSize() {
        return new Dimension(imageIcon.getIconWidth(), imageIcon.getIconHeight());
    }

    public void paintComponent(Graphics g) {
        imageIcon.paintIcon(this, g, 0, 0);
    }
}
