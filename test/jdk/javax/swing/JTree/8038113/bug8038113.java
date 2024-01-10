/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import static java.awt.image.BufferedImage.TYPE_INT_RGB;

/* @test
 * @key headful
 * @bug 8038113 8258979
 * @summary [macosx] JTree icon is not rendered in high resolution on Retina,
 *          collapsed icon is not rendered for GTK LAF as well.
 * @run main bug8038113
 */

public class bug8038113 {
    public static void main(String[] args) throws Exception {
        for (UIManager.LookAndFeelInfo laf :
                UIManager.getInstalledLookAndFeels()) {
            if (!laf.getName().contains("Motif")) {
                System.out.println("Testing LAF: " + laf.getName());
                SwingUtilities.invokeAndWait(() -> test(laf));
            }
        }
    }

    public static void test(UIManager.LookAndFeelInfo laf) {
        setLookAndFeel(laf);
        final JTree tree = new JTree();
        final BasicTreeUI treeUI = (BasicTreeUI) tree.getUI();

        Icon collapsedIcon = treeUI.getCollapsedIcon();
        Icon expandedIcon = treeUI.getExpandedIcon();
        BufferedImage img1 = paintToImage(expandedIcon);
        BufferedImage img2 = paintToImage(collapsedIcon);

        if (!isImgRendered(img1)) {
            try {
                ImageIO.write(img1, "png", new File("Expanded_Icon_" + laf.getName() + ".png"));
            } catch (IOException ignored) {
            }
            throw new RuntimeException("Test Failed, Expanded not rendered for: "+laf.getName());
        }

        if (!isImgRendered(img2)) {
            try {
                ImageIO.write(img2, "png", new File("Collapsed_Icon_" + laf.getName() + ".png"));
            } catch (IOException ignored) {
            }
            throw new RuntimeException("Test Failed, Collapsed Icon not rendered for: "+laf.getName());
        }
        System.out.println("Test Passed");
    }

    private static void setLookAndFeel(UIManager.LookAndFeelInfo laf) {
        try {
            UIManager.setLookAndFeel(laf.getClassName());
        } catch (UnsupportedLookAndFeelException ignored) {
            System.out.println("Unsupported LAF: " + laf.getClassName());
        } catch (ClassNotFoundException | InstantiationException
                 | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static BufferedImage paintToImage(Icon content) {
        BufferedImage im = new BufferedImage(content.getIconWidth(),
                content.getIconHeight(), TYPE_INT_RGB);
        Graphics2D g = (Graphics2D) im.getGraphics();
        g.setBackground(Color.WHITE);
        g.clearRect(0, 0, content.getIconWidth(), content.getIconHeight());
        content.paintIcon(new JLabel(), g, 0, 0);
        g.dispose();
        return im;
    }

    private static boolean isImgRendered(BufferedImage img) {
        Color white = new Color(255, 255, 255);
        for (int x = 0; x < img.getWidth(); ++x) {
            for (int y = 0; y < img.getHeight(); ++y) {
                if (img.getRGB(x, y) != white.getRGB()) {
                    return true;
                }
            }
        }
        return false;
    }
}
