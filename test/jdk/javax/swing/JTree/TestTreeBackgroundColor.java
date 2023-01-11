/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8287912
 * @key headful
 * @requires (os.family == "linux")
 * @summary Verifies if tree background color is red when
 * setOpaque(false) method is called for tree component
 * @run main TestTreeBackgroundColor
 */

import java.awt.Color;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class TestTreeBackgroundColor {

    private static JFrame frame;
    private static JPanel panel;
    private static JTree tree;

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel("com.sun.java.swing.plaf.gtk.GTKLookAndFeel");
        Robot robot = new Robot();
        robot.setAutoDelay(100);
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    createAndShowUI();
                }
            });

            robot.waitForIdle();
            robot.delay(1000);
            Point pt = tree.getLocationOnScreen();
            BufferedImage img =
                    robot.createScreenCapture(new Rectangle(pt.x, pt.y,
                            tree.getWidth(),
                            tree.getHeight()));

            boolean passed = false;
            for (int x = img.getWidth()/2; x < img.getWidth() - 1; ++x) {
                Color c = new Color(img.getRGB(x, img.getHeight()/4));
                if (!c.equals(Color.RED)) {
                    passed = false;
                    break;
                } else {
                    passed = true;
                }
            }
            if (!passed) {
                ImageIO.write(img, "png", new File("TreeBackgroundColorFail.png"));
                throw new RuntimeException("Test Case Failed : Tree Background Color is Not Red");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static void createAndShowUI() {
        frame = new JFrame("Test Tree Background Color");
        panel = new JPanel();
        tree = new JTree();
        panel.setBackground(Color.red);
        panel.setLayout(new GridLayout(1, 1));
        tree.setOpaque(false);
        panel.add(tree);
        frame.getContentPane().add(panel);
        frame.pack();
        frame.setSize(250, 200);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}

