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
 * @bug 8234315
 * @key headful
 * @requires (os.family == "linux")
 * @summary Verifies if disabled menu foreground color grayed out
 * @run main TestDisabledMenuForegroundColor
 */

import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class TestDisabledMenuForegroundColor {

    private static JFrame frame;
    private static JMenuBar menuBar;
    private static JMenu fileMenu;

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel("com.sun.java.swing.plaf.gtk.GTKLookAndFeel");
        Robot robot = new Robot();
        robot.setAutoDelay(100);
        try {
            SwingUtilities.invokeAndWait(() -> {
                createAndShowUI();
            });

            robot.waitForIdle();
            robot.delay(1000);
            Point pt = fileMenu.getLocationOnScreen();
            BufferedImage enabledImg =
                    robot.createScreenCapture(new Rectangle(pt.x, pt.y,
                                              fileMenu.getWidth(),
                                              fileMenu.getHeight()));
            fileMenu.setEnabled(false);
            robot.waitForIdle();
            robot.delay(1000);
            BufferedImage disabledImg =
                    robot.createScreenCapture(new Rectangle(pt.x, pt.y,
                                              fileMenu.getWidth(),
                                              fileMenu.getHeight()));
            boolean passed = compareImage(enabledImg,disabledImg);

            if (!passed) {
                ImageIO.write(enabledImg, "png", new File("JMenuEnabledImg.png"));
                ImageIO.write(disabledImg, "png", new File("JMenuDisabledImg.png"));
                throw new RuntimeException("Disabled JMenu foreground color not grayed out");
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
        frame = new JFrame("Test Disabled Menu Foreground Color");
        menuBar  = new JMenuBar();
        fileMenu = new JMenu("File");
        fileMenu.setEnabled(true);
        menuBar.add(fileMenu);
        frame.setJMenuBar(menuBar);
        frame.pack();
        frame.setSize(250, 200);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

    /*
    * Compare JMenu enabled and disabled state image and if both images
    * width and height are equal but pixel's RGB values are not equal,
    * method returns true; false otherwise.
    */

    private static boolean compareImage(BufferedImage img1, BufferedImage img2) {
        if (img1.getWidth() == img2.getWidth()
                && img1.getHeight() == img2.getHeight()) {
            for (int x = 1; x < img1.getWidth()-1; ++x) {
                for (int y = 1; y < img1.getHeight()-1; ++y) {
                    if (img1.getRGB(x, y) != img2.getRGB(x, y)) {
                        return true;
                    }
                }
            }
            return false;
        } else {
            return false;
        }
    }
}

