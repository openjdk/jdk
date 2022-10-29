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
 * @bug 7189422
 * @key headful
 * @requires (os.family == "mac")
 * @summary Verifies arrow position in submenu with empty title
 * @run main TestSubMenuArrowPosition
 */

import java.io.File;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.SwingUtilities;
import javax.imageio.ImageIO;

public class TestSubMenuArrowPosition {

    private static JFrame frame;
    private static JMenu menu;
    private static JMenu subMenu;

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        robot.setAutoDelay(100);
        try {
            SwingUtilities.invokeAndWait(() -> {
                frame = new JFrame();
                JMenuBar menuBar = new JMenuBar();
                menu = new JMenu("Test menu");
                subMenu = new JMenu("");

                menu.add(subMenu);
                menuBar.add(menu);

                frame.setJMenuBar(menuBar);
                frame.setSize(300, 300);
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            });

            robot.waitForIdle();
            robot.delay(1000);

            Point p = menu.getLocationOnScreen();
            robot.mouseMove(p.x+5, p.y+5);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();
            robot.delay(1000);

            p = subMenu.getLocationOnScreen();
            BufferedImage img =
                    robot.createScreenCapture(new Rectangle(p.x, p.y,
                                              subMenu.getWidth(),
                                              subMenu.getHeight()));

            System.out.println("width " + img.getWidth() +
                               " height " + img.getHeight());
            Color prevColor = new Color(img.getRGB(img.getWidth() / 2,
                                                   img.getHeight() / 2));
            boolean passed = false;
            for (int x = img.getWidth() / 2; x < img.getWidth() - 1; ++x) {
                System.out.println("x " + x + " rgb = " +
                                     Integer.toHexString(
                                      img.getRGB(x, img.getHeight() / 2)));
                Color c = new Color(img.getRGB(x, img.getHeight() / 2));
                if (!c.equals(prevColor)) {
                    passed = true;
                }
                prevColor = c;
            }
            if (!passed) {
                ImageIO.write(img, "png", new File("SimpleTest.png"));
                throw new RuntimeException("Submenu's arrow have wrong position");
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
