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
 * @key headful
 * @summary  Verifies Nimbus JTree default tree cell renderer uses selected text color
 * @run main/othervm -Dawt.useSystemAAFontSettings=off -Dsun.java2d.uiScale=1.0 NimbusJTreeSelTextColor
 */

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.tree.DefaultTreeCellRenderer;

public class NimbusJTreeSelTextColor {

    private static JFrame frame;
    private static JTree tree;

    private static volatile Rectangle treeBounds;
    private static volatile int iconOffset;
    private static volatile Color foregroundColor;
    private static volatile Color backgroundColor;

    private static final String FILENAME = "image.png";

    public static void main(String[] args) throws Exception {
        try {
            SwingUtilities.invokeAndWait(NimbusJTreeSelTextColor::createUI);

            Robot robot = new Robot();
            robot.waitForIdle();
            robot.delay(500);

            SwingUtilities.invokeAndWait(NimbusJTreeSelTextColor::getTreeBounds);
            treeBounds.height /= 4; // height of one row
            treeBounds.x += iconOffset;
            treeBounds.width -= iconOffset;
            treeBounds.width -= 2; // crop selection border on the right
            BufferedImage image = robot.createScreenCapture(treeBounds);
            checkColors(image);
        } finally {
            SwingUtilities.invokeAndWait(frame::dispose);
        }
    }

    private static void createUI() {
        try {
            UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        frame = new JFrame("Nimbus Tree selected color");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        frame.getContentPane().add(createTree());

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static void checkColors(final BufferedImage image) throws IOException {
        final int y = treeBounds.height / 2;
        final int foreground = foregroundColor.getRGB();
        final int background = backgroundColor.getRGB();

        for (int x = 0; x < treeBounds.width; x++) {
            int rgb = image.getRGB(x, y);
            if (rgb != foreground && rgb != background) {
                save(image);
                throw new RuntimeException(
                        "Unexpected color found: " + Integer.toHexString(rgb)
                        + " at (" + x + ", " + y + ");"
                        + " foreground: " + Integer.toHexString(foreground) + ";"
                        + " background: " + Integer.toHexString(background)
                        + " - check " + FILENAME);
            }
        }
    }

    private static void getTreeBounds() {
        treeBounds = new Rectangle(tree.getLocationOnScreen(),
                                   tree.getSize());
    }

    private static JComponent createTree() {
        tree = new JTree();

        DefaultTreeCellRenderer cellRenderer = new DefaultTreeCellRenderer();
        iconOffset = cellRenderer.getOpenIcon().getIconWidth()
                     + cellRenderer.getIconTextGap();
        foregroundColor = (Color) UIManager.get("Tree.selectionForeground");
        backgroundColor = (Color) UIManager.get("Tree.selectionBackground");

        tree.setRootVisible(true);
        tree.setShowsRootHandles(false);
        tree.setCellRenderer(cellRenderer);
        tree.setSelectionRow(0);
        return tree;
    }

    private static void save(final BufferedImage img) throws IOException {
        ImageIO.write(img, "png", new File(FILENAME));
    }

}
