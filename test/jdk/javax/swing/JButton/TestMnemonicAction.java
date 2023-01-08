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

/* @test
 * @key headful
 * @bug 4850101
 * @summary Verifies if Setting mnemonic to VK_F4 underlines the letter S.
 * @run main TestMnemonicAction
 */

import java.io.File;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public class TestMnemonicAction {
    private static JFrame f;
    private static JButton b;

    public static boolean compareBufferedImages(BufferedImage bufferedImage0,
                                                BufferedImage bufferedImage1) {
        int width = bufferedImage0.getWidth();
        int height = bufferedImage0.getHeight();

        if (width != bufferedImage1.getWidth() || height != bufferedImage1.getHeight()) {
            return false;
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (bufferedImage0.getRGB(x, y) != bufferedImage1.getRGB(x, y)) {
                    return false;
                }
            }
        }

        return true;
    }

    public static void main(String[] args) throws Exception {

        SwingUtilities.invokeAndWait(() -> {
            f = new JFrame();
            b = new JButton("Shutdown");
            b.setMnemonic(KeyEvent.VK_F4);
            b.setPreferredSize(new Dimension(100, 25));
            b.setToolTipText("Shutdown");
            f.getContentPane().add(b);
            f.setDefaultCloseOperation(f.EXIT_ON_CLOSE);
            f.setUndecorated(true);
            f.setLocationRelativeTo(null);
            f.pack();
            f.setVisible(true);
        });
        Robot robot = new Robot();
        robot.waitForIdle();
        robot.delay(1000);
        Point p = b.getLocationOnScreen();
        BufferedImage imgmnemonic = robot.createScreenCapture(
                        new Rectangle(p.x, p.y, b.getWidth(), b.getHeight()));
        robot.delay(1000);
        SwingUtilities.invokeAndWait(() -> {
            if (f != null) {
                f.dispose();
            }
        });
        SwingUtilities.invokeAndWait(() -> {
            f = new JFrame();
            b = new JButton("Shutdown");
            b.setPreferredSize(new Dimension(100, 25));
            f.getContentPane().add(b);
            f.setDefaultCloseOperation(f.EXIT_ON_CLOSE);
            f.setUndecorated(true);
            f.setLocationRelativeTo(null);
            f.pack();
            f.setVisible(true);
        });
        robot.waitForIdle();
        robot.delay(1000);
        p = b.getLocationOnScreen();
        BufferedImage buttonimage = robot.createScreenCapture(
                        new Rectangle(p.x, p.y, b.getWidth(), b.getHeight()));
        robot.delay(1000);
        SwingUtilities.invokeAndWait(() -> {
            if (f != null) {
                f.dispose();
            }
        });
        if (!compareBufferedImages(imgmnemonic, buttonimage)) {
            ImageIO.write(imgmnemonic, "png", new File("imgmnemonic.png"));
            ImageIO.write(buttonimage, "png", new File("buttonimage.png"));
            throw new RuntimeException("F4 mnemonic underlines S");
        }
    }
}
