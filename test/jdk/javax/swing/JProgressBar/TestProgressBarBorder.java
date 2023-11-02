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
 * @bug 8224261
 * @key headful
 * @summary Verifies if JProgressBar border is painted even though border
 *          painting is set to false
 * @run main TestProgressBarBorder
 */

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

public class TestProgressBarBorder {

    private static JFrame frame;
    private static JProgressBar progressBar;
    private static volatile Point pt;
    private static volatile boolean passed;

    public static void main(String[] args) throws Exception {
        for (UIManager.LookAndFeelInfo laf :
                UIManager.getInstalledLookAndFeels()) {
            if (laf.getName().contains("Nimbus") || laf.getName().contains("GTK")) {
                System.out.println("Testing LAF: " + laf.getName());
                SwingUtilities.invokeAndWait(() -> setLookAndFeel(laf));
            } else {
                continue;
            }
            Robot robot = new Robot();
            robot.setAutoDelay(100);
            try {
                SwingUtilities.invokeAndWait(() -> {
                    createAndShowUI();
                });

                robot.waitForIdle();
                robot.delay(1000);

                SwingUtilities.invokeAndWait(() -> {
                    pt = progressBar.getLocationOnScreen();
                });

                BufferedImage borderPaintedImg =
                        robot.createScreenCapture(new Rectangle(pt.x, pt.y,
                                progressBar.getWidth(), progressBar.getHeight()));

                progressBar.setBorderPainted(false);
                robot.waitForIdle();
                robot.delay(500);

                BufferedImage borderNotPaintedImg =
                        robot.createScreenCapture(new Rectangle(pt.x, pt.y,
                                progressBar.getWidth(), progressBar.getHeight()));

                robot.delay(500);

                SwingUtilities.invokeAndWait(() -> {
                    passed = compareImage(borderPaintedImg, borderNotPaintedImg);
                });

                if (!passed) {
                    ImageIO.write(borderPaintedImg, "png", new File("borderPaintedImg.png"));
                    ImageIO.write(borderNotPaintedImg, "png", new File("borderNotPaintedImg.png"));
                    throw new RuntimeException("JProgressBar border is painted although " +
                            "border painting is set to false");
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

    private static void createAndShowUI() {
        frame = new JFrame("Test JProgressBar Border");
        JPanel p = new JPanel(new FlowLayout());
        progressBar  = new JProgressBar();
        // set initial value
        progressBar.setValue(0);
        progressBar.setBorderPainted(true);
        progressBar.setStringPainted(true);
        p.add(progressBar);
        frame.add(p);
        frame.setSize(200, 100);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

    /*
    * Compare JProgressBar border painted and border not painted image and
    * if both images width and height are equal but pixel's RGB values are
    * not equal, method returns true; false otherwise.
    */

    private static boolean compareImage(BufferedImage img1, BufferedImage img2) {
        if (img1.getWidth() == img2.getWidth()
                && img1.getHeight() == img2.getHeight()) {
            for (int x = 0; x < img1.getWidth(); ++x) {
                for (int y = 0; y < img1.getHeight(); ++y) {
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
