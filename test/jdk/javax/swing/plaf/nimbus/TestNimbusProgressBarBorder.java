/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8192888
 * @key headful
 * @summary Verifies ProgressBar in Synth L&F renders background
 *          when border is not painted
 * @run main TestNimbusProgressBarBorder
 */

import java.io.File;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.image.BufferedImage;
import java.awt.Rectangle;
import java.awt.Robot;

import javax.imageio.ImageIO;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.UIManager;
import javax.swing.SwingUtilities;

public class TestNimbusProgressBarBorder {

    private static JFrame frame;
    private static JProgressBar progressBar;
    private static boolean failure = true;
    private static volatile Rectangle rect;

    public static void main(String[] args) throws Exception {
        int width = 200;
        int height = 100;
        try {
            Robot robot = new Robot();
            SwingUtilities.invokeAndWait(() -> {
                try {
                    // Set Nimbus L&F
                    UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                frame = new JFrame("Nimbus JProgressBar Test");
                frame.setSize(width, height);

                // ProgressBar setup
                progressBar = new JProgressBar(0, 100);
                progressBar.setValue(0);
                progressBar.setBorderPainted(false);

                JPanel center = new JPanel(new GridBagLayout());
                center.setBackground(Color.WHITE);
                center.add(progressBar);

                frame.add(center, BorderLayout.CENTER);
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            });
            robot.waitForIdle();
            robot.delay(1000);
            SwingUtilities.invokeAndWait(() -> {
                rect = progressBar.getBounds();
            });

            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = (Graphics2D) img.getGraphics();
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, rect.width, rect.height);
            progressBar.paint(g2d);
            g2d.dispose();

            robot.waitForIdle();
            robot.delay(100);

            for (int x = 10; x < (10 + rect.width / 2); x++) {
                for (int y = 10; y < (10 + rect.height / 2); y++) {
                    Color col = new Color(img.getRGB(x, y));
                    if (!col.equals(Color.WHITE)) {
                        failure = false;
                        break;
                    }
                }
            }
            if (failure) {
                ImageIO.write(img, "png", new File("ProgressBarTest.png"));
                throw new RuntimeException("ProgressBar background not drawn");
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
