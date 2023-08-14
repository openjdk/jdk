/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8258970
 * @key headful
 * @requires (os.family == "linux")
 * @summary Verifies if disabled password field foreground color grayed out
 * @run main TestDisabledPasswordFieldForegroundColor
 */

import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPasswordField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.lang.Math;

public class TestDisabledPasswordFieldForegroundColor {

    private static JFrame frame;
    private static JPasswordField passwordField;

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel("com.sun.java.swing.plaf.gtk.GTKLookAndFeel");
        Robot robot = new Robot();
        robot.setAutoDelay(1000);
        try {
            SwingUtilities.invokeAndWait(() -> {
                createAndShowUI();
            });

            robot.waitForIdle();
            robot.delay(500);
            Point pt = passwordField.getLocationOnScreen();
            BufferedImage enabledImg =
                    robot.createScreenCapture(new Rectangle(pt.x, pt.y,
                            passwordField.getWidth(), passwordField.getHeight()));
            passwordField.setEnabled(false);
            robot.waitForIdle();
            robot.delay(500);
            BufferedImage disabledImg =
                    robot.createScreenCapture(new Rectangle(pt.x, pt.y,
                            passwordField.getWidth(), passwordField.getHeight()));
            boolean passed = compareImage(enabledImg,disabledImg);

            if (!passed) {
                ImageIO.write(enabledImg, "png", new File("JPasswordFieldEnabledImg.png"));
                ImageIO.write(disabledImg, "png", new File("JPasswordFieldDisabledImg.png"));
                throw new RuntimeException("Disabled JPasswordField foreground color not grayed out");
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
        frame = new JFrame("Test Disabled Password Field Foreground Color");
        passwordField = new JPasswordField("passwordpassword");
        passwordField.setEnabled(true);
        frame.add(passwordField);
        frame.pack();
        frame.setSize(150, 100);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

    /*
    * Compare JPasswordField enabled and disabled state image and if both images
    * width and height are equal but pixel's RGB values are not equal,
    * method returns true; false otherwise.
    */

    private static boolean compareImage(BufferedImage img1, BufferedImage img2) {
        int tolerance = 5;
        if (img1.getWidth() == img2.getWidth()
                && img1.getHeight() == img2.getHeight()) {
            for (int x = 10; x < img1.getWidth()/2; ++x) {
                for (int y = 10; y < img1.getHeight()-10; ++y) {
                    Color c1 = new Color(img1.getRGB(x, y));
                    Color c2 = new Color(img2.getRGB(x, y));

                    if (Math.abs(c1.getRed() - c2.getRed()) > tolerance ||
                            Math.abs(c1.getRed() - c2.getRed()) > tolerance ||
                            Math.abs(c1.getRed() - c2.getRed()) > tolerance) {
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
