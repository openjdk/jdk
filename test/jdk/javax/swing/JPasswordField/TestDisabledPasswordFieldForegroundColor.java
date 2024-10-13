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
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JFormattedTextField;
import javax.swing.JPasswordField;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.lang.Math;

public class TestDisabledPasswordFieldForegroundColor {

    private static JFrame frame;
    private static JPasswordField passwordField;
    private static JTextField textField;
    private static JFormattedTextField formattedTextField;
    private static JSpinner spinner;
    private static Robot robot;
    private static BufferedImage enabledImg;
    private static BufferedImage disabledImg;

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel("com.sun.java.swing.plaf.gtk.GTKLookAndFeel");
        boolean testFail = false;
        robot = new Robot();
        robot.setAutoDelay(100);
        try {
            SwingUtilities.invokeAndWait(
                    TestDisabledPasswordFieldForegroundColor::createAndShowUI);

            robot.waitForIdle();
            robot.delay(1000);

            if (!testComponent(passwordField, 20, 10)) {
                System.out.println("Disabled JPasswordField foreground color not grayed out");
                ImageIO.write(enabledImg, "png", new File("JPasswordFieldEnabledImg.png"));
                ImageIO.write(disabledImg, "png", new File("JPasswordFieldDisabledImg.png"));
                testFail = true;
            }

            if (!testComponent(textField, 20, 10)) {
                System.out.println("Disabled JTextField foreground color not grayed out");
                ImageIO.write(enabledImg, "png", new File("JTextFieldEnabledImg.png"));
                ImageIO.write(disabledImg, "png", new File("JTextFieldDisabledImg.png"));
                testFail = true;
            }

            if (!testComponent(formattedTextField, 20, 10)) {
                System.out.println("Disabled JFormattedTextField foreground color not grayed out");
                ImageIO.write(enabledImg, "png", new File("JFormattedTextFieldEnabledImg.png"));
                ImageIO.write(disabledImg, "png", new File("JFormattedTextFieldDisabledImg.png"));
                testFail = true;
            }

            if (!testComponent(spinner, 10, 5)) {
                System.out.println("Disabled JSpinner foreground color not grayed out");
                ImageIO.write(enabledImg, "png", new File("JSpinnerTextFieldEnabledImg.png"));
                ImageIO.write(disabledImg, "png", new File("JSpinnerTextFieldDisabledImg.png"));
                testFail = true;
            }

            if (testFail) {
                throw new RuntimeException("Disabled Component foreground color not grayed out");
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
        frame = new JFrame("Test Disabled Component Foreground Color");
        frame.getContentPane().setLayout(new FlowLayout(FlowLayout.CENTER,5,5));
        passwordField = new JPasswordField("passwordpassword");
        passwordField.setEnabled(true);
        textField = new JTextField("TextField");
        textField.setEnabled(true);
        formattedTextField = new JFormattedTextField("FormattedTextField");
        formattedTextField.setEnabled(true);
        SpinnerNumberModel model = new SpinnerNumberModel(5, 0, 10, 1);
        spinner = new JSpinner(model);
        spinner.setEnabled(true);

        frame.getContentPane().add(passwordField);
        frame.getContentPane().add(textField);
        frame.getContentPane().add(formattedTextField);
        frame.getContentPane().add(spinner);
        frame.pack();
        frame.setSize(500, 150);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

    private static boolean testComponent(JComponent c, int xOffset, int yOffset)
            throws Exception {
        Point pt = c.getLocationOnScreen();
        enabledImg = robot.createScreenCapture(new Rectangle(pt.x, pt.y,
                        c.getWidth(), c.getHeight()));
        c.setEnabled(false);
        robot.waitForIdle();
        robot.delay(500);
        disabledImg = robot.createScreenCapture(new Rectangle(pt.x, pt.y,
                        c.getWidth(), c.getHeight()));
        return compareImage(enabledImg, disabledImg, xOffset, yOffset);
    }

    /*
    * Compare enabled and disabled state image and if both images
    * width and height are equal but pixel's RGB values are not equal,
    * method returns true; false otherwise.
    */

    private static boolean compareImage(BufferedImage img1, BufferedImage img2,
                                        int xOffset, int yOffset) {
        int tolerance = 5;
        if (img1.getWidth() == img2.getWidth()
                && img1.getHeight() == img2.getHeight()) {
            for (int x = xOffset; x < img1.getWidth() / 2; ++x) {
                for (int y = yOffset; y < img1.getHeight() - 5; ++y) {
                    Color c1 = new Color(img1.getRGB(x, y));
                    Color c2 = new Color(img2.getRGB(x, y));

                    if (Math.abs(c1.getRed() - c2.getRed()) > tolerance ||
                            Math.abs(c1.getGreen() - c2.getGreen()) > tolerance ||
                            Math.abs(c1.getBlue() - c2.getBlue()) > tolerance) {
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
