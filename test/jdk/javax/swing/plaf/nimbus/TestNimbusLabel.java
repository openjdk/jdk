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
 * @bug 7175396
 * @key headful
 * @summary  Verifies the text on label is painted red for Nimbus LaF.
 * @run main TestNimbusLabel
 */

import java.io.File;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class TestNimbusLabel
{
    private static JLabel label;
    private static JFrame frame;
    private static boolean passed = false;
    private static int colorTolerance = 5;

    private static boolean checkPixel(Color color) {
        int red1 = color.getRed();
        int blue1 = color.getBlue();
        int green1 = color.getGreen();
        int red2 = Color.red.getRed();
        int blue2 = Color.red.getBlue();
        int green2 = Color.red.getGreen();
        if ((Math.abs(red1 - red2) < colorTolerance) &&
                (Math.abs(green1 - green2) < colorTolerance) &&
                (Math.abs(blue1 - blue2) < colorTolerance)) {
            return true;
        }
        return false;
    }

    public static void main(String[] args) throws Exception {

        try {
            UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
            Robot robot = new Robot();

            SwingUtilities.invokeAndWait(() -> {
                frame = new JFrame();
                UIManager.getDefaults().put("Label.foreground",
                                            java.awt.Color.red);
                label =
                    new JLabel("<html><body>Can You Read This?</body></html>");

                frame.getContentPane().add(label);
                frame.setUndecorated(true);
                frame.setLocationRelativeTo(null);
                frame.pack();
                frame.setVisible(true);
            });
            robot.waitForIdle();
            robot.delay(1000);

            Point p = frame.getLocationOnScreen();
            Dimension d = frame.getSize();
            System.out.println("width " + d.getWidth() +
                                " height " + d.getHeight());
            int y = p.y + d.height/2;

            for (int x = p.x; x < p.x + d.width; x++) {
                    System.out.println("color(" + x + "," + y + ")=" +
                                        robot.getPixelColor(x, y));
                    Color color = robot.getPixelColor(x, y);
                    if (checkPixel(color)) {
                        passed = true;
                        break;
                    }
            }
            if (!passed) {
                BufferedImage img =
                    robot.createScreenCapture(new Rectangle(p.x, p.y,
                            d.width,
                            d.height));
                ImageIO.write(img, "png", new File("label.png"));
                throw new RuntimeException("Label.foreground color not honoured");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }
}
