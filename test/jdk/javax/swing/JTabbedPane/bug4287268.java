/*
 * Copyright (c) 2000, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4287268
 * @summary Tests if setIconAt(index,Icon) does not set Tab's disabled icon
 * @key headful
*/

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

public class bug4287268 {

    static JFrame frame;
    static volatile JTabbedPane jtp;

    public static void main(String[] args) throws Exception {
        try {
            SwingUtilities.invokeAndWait(bug4287268::createUI);

            Robot robot = new Robot();
            robot.waitForIdle();
            robot.delay(1000);

            Point point = jtp.getLocationOnScreen();
            int width = jtp.getWidth();
            int height = jtp.getHeight();
            Rectangle r = new Rectangle(point.x, point.y, width, height);
            BufferedImage cap = robot.createScreenCapture(r);

            int red = Color.red.getRGB();
            for (int x = 0; x < cap.getWidth(); x++) {
                for (int y = 0; y < cap.getHeight(); y++) {
                    int rgb = cap.getRGB(x, y);
                    if (rgb == red) {
                        try {
                             javax.imageio.ImageIO.write(cap, "png", new java.io.File("cap.png"));
                        } catch (Exception ee) {
                        }
                        throw new RuntimeException("Test failed : found red");
                    }
                }
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    static void createUI() {
        frame = new JFrame("bug4287268");
        jtp = new JTabbedPane();
        JPanel panel = new JPanel();
        jtp.add("Panel One", panel);
        int size = 64;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics g = img.createGraphics();
        g.setColor(Color.red);
        g.fillRect(0, 0, size, size);
        ImageIcon ii = new ImageIcon(img);
        jtp.setIconAt(0, ii);
        jtp.setEnabledAt(0, false);
        frame.getContentPane().add(jtp, BorderLayout.CENTER);
        frame.pack();
        frame.setVisible(true);
    }
}
