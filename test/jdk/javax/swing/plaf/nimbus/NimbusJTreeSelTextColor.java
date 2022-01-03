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
/*
 * @test
 * @bug 8266510 8271315 8273043
 * @summary  Verifies Nimbus JTree default tree cell renderer uses selected text color
 * @requires os.family != "mac"
 * @run main/othervm -Dawt.useSystemAAFontSettings=off NimbusJTreeSelTextColor
 */

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.tree.DefaultTreeCellRenderer;

import static java.awt.image.BufferedImage.TYPE_INT_RGB;

public class NimbusJTreeSelTextColor {

    private static int iconOffset;
    private static Color foregroundColor;
    private static Color backgroundColor;

    private static volatile Exception testFailed;

    private static final String FILENAME = "image.png";

    public static void main(String[] args) throws Exception {
        final boolean showFrame = args.length >= 1 && args[0].equals("-show");

        // Disable text antialiasing
        System.setProperty("awt.useSystemAAFontSettings", "off");

        SwingUtilities.invokeAndWait(() -> runTest(showFrame));

        if (testFailed != null) {
            throw testFailed;
        }
    }

    private static void runTest(final boolean showFrame) {
        try {
            UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        final JTree tree = createTree();
        Dimension size = tree.getPreferredSize();
        tree.setSize(size);

        BufferedImage im = new BufferedImage(size.width, size.height,
                                             TYPE_INT_RGB);
        Graphics g = im.getGraphics();
        tree.paint(g);
        g.dispose();

        if (showFrame) {
            showFrame(tree);
        }

        size.height /= 4; // height of one row
        size.width -= iconOffset;
        size.width -= 2; // crop selection border on the right
        checkColors(im, iconOffset, size.height / 2, size.width);
    }

    private static void showFrame(final JTree tree) {
        JFrame frame = new JFrame("Nimbus Tree selected color");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        frame.getContentPane().add(tree);

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static void checkColors(final BufferedImage image,
                                    final int iconOffset,
                                    final int y,
                                    final int width) {
        final int foreground = foregroundColor.getRGB();
        final int background = backgroundColor.getRGB();

        for (int x = iconOffset; x < width; x++) {
            int rgb = image.getRGB(x, y);
            if (rgb != foreground && rgb != background) {
                testFailed = new RuntimeException(
                        "Unexpected color found: " + Integer.toHexString(rgb)
                        + " at (" + x + ", " + y + ");"
                        + " foreground: " + Integer.toHexString(foreground) + ";"
                        + " background: " + Integer.toHexString(background)
                        + " - check " + FILENAME);
                save(image);
            }
        }
    }

    private static JTree createTree() {
        DefaultTreeCellRenderer cellRenderer = new DefaultTreeCellRenderer();
        iconOffset = cellRenderer.getOpenIcon().getIconWidth()
                     + cellRenderer.getIconTextGap();
        foregroundColor = (Color) UIManager.get("Tree.selectionForeground");
        backgroundColor = (Color) UIManager.get("Tree.selectionBackground");

        JTree tree = new JTree();
        tree.setRootVisible(true);
        tree.setShowsRootHandles(false);
        tree.setCellRenderer(cellRenderer);
        tree.setSelectionRow(0);
        return tree;
    }

    private static void save(final BufferedImage img) {
        try {
            ImageIO.write(img, "png", new File(FILENAME));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
