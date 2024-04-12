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
 * @bug 6875229
 * @key headful
 * @summary  Verifies icon is drawn after text in NimbusL&F
 * @run main TestNimbusTabbedPaneIconPosition
 */

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class TestNimbusTabbedPaneIconPosition {
    static JTabbedPane tabbedPane;
    static JFrame frame;
    static volatile Point pt;
    static volatile Rectangle bounds;
    private static void addTab(JTabbedPane aTabbedPane, int anIndex) {
        aTabbedPane.addTab("\u2588", new Icon() {
            @Override
            public void paintIcon(Component aComponent, Graphics aGraphics, int anX, int aY) {
                aGraphics.setColor(Color.RED);
                aGraphics.fillRect(anX, aY, 16, 16);
            }

            @Override
            public int getIconWidth() {
                return 16;
            }

            @Override
            public int getIconHeight() {
                return 16;
            }
        }, null);
    }

    public static void main(String[] args) throws Exception {
        try {
            Robot robot = new Robot();
            robot.setAutoDelay(100);
            UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
            SwingUtilities.invokeAndWait(() -> {
                tabbedPane = new JTabbedPane();
                addTab(tabbedPane, 0);
                frame = new JFrame();
                frame.add(tabbedPane);
                frame.pack();
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            });
            robot.waitForIdle();
            robot.delay(1000);
            SwingUtilities.invokeAndWait(() -> {
                pt = tabbedPane.getLocationOnScreen();
                bounds = tabbedPane.getBoundsAt(0);
            });
            BufferedImage img = robot.createScreenCapture(
                    new Rectangle(pt.x + bounds.x,
                            pt.y + bounds.y,
                            bounds.width,
                            bounds.height));

            robot.delay(500);
            int y = pt.y + bounds.height / 2;
            boolean expected = false;
            for (int x = pt.x + bounds.x; x <= pt.x + bounds.width; x++) {
                Color col = robot.getPixelColor(x, y);
                if (col.equals(Color.RED)) {
                    expected = true;
                    break;
                }
                if (col.equals(Color.BLACK)) {
                    expected = false;
                    break;
                }
            }
            if (!expected) {
                ImageIO.write(img, "png", new File("tab.png"));
                throw new RuntimeException("icon drawn after text");
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
