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

/* @test
   @bug 8303904
   @requires (os.family == "mac")
   @summary when "swing.volatileImageBufferEnabled" is "false" translucent windows repaint as opaque
   @run main TransparentWindowTest
*/

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class TransparentWindowTest extends JWindow {

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        robot.setAutoDelay(100);

        SwingUtilities.invokeLater(() -> {
            TransparentWindowTest t = new TransparentWindowTest();
            t.setVisible(true);
        });

        robot.waitForIdle();
        robot.delay(1000);
    }

    public TransparentWindowTest() {
        JPanel p = new JPanel() {
            @Override
            public void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(220, 180, 0, 200));
                g2.fillRect(5, 5, getWidth(), getHeight());
            }
        };
        p.setOpaque(false);
        p.setLayout(new BorderLayout());
        getContentPane().add(p);
        setBackground(new Color(0, 0, 0, 0));
        setSize(400, 400);
        setLocationRelativeTo(null);

        // Check transparency and border color
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                testWindowProperties();
            }
        });
    }

    private void testWindowProperties() {
        BufferedImage image = (BufferedImage) createImage(getWidth(), getHeight());

        if (image == null) {
            throw new RuntimeException("Test FAILED! Unable to capture the window.");
        }

        testTransparency(image);
        testBlackBorder(image);
        System.out.println("Test PASSED!");
    }

    private void testTransparency(BufferedImage img) {
        if (img.getTransparency() != Transparency.TRANSLUCENT) {
            throw new RuntimeException("Test FAILED! The windows should be translucent.");
        }
    }

    private void testBlackBorder(BufferedImage img) {
        final int blackRGB = Color.BLACK.getRGB();

        if (img.getRGB(0, 0) == blackRGB ||
                img.getRGB(img.getWidth() - 1, 0) == blackRGB ||
                img.getRGB(0, img.getHeight() - 1) == blackRGB ||
                img.getRGB(img.getWidth() - 1, img.getHeight() - 1) == blackRGB) {
            throw new RuntimeException("Test FAILED! The window should not have a black border.");
        }
    }
}
