/*
 * Copyright (c) 2002, 2023, Oracle and/or its affiliates. All rights reserved.
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

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;

/*
 * @test
 * @bug 4749659
 * @summary Tests that popup menu doesn't steal focus from top-level
 * @key headful
 */

public class TestWindowsLFFocus {
    static volatile boolean actionFired;

    static JFrame frame;
    static JMenuBar bar;
    static JMenuItem item;
    static volatile Point frameLoc;

    public static void main(String[] args) throws Exception {
        for (UIManager.LookAndFeelInfo lookAndFeel : UIManager.getInstalledLookAndFeels()) {
            UIManager.setLookAndFeel(lookAndFeel.getClassName());
            test();
        }

        System.err.println("PASSED");
    }

    private static void test() throws Exception {
        try {
            SwingUtilities.invokeAndWait(() -> {
                actionFired = false;
                frame = new JFrame();
                bar = new JMenuBar();
                frame.setJMenuBar(bar);
                JMenu menu = new JMenu("menu");
                bar.add(menu);
                item = new JMenuItem("item");
                menu.add(item);
                item.addActionListener(e -> actionFired = true);

                frame.getContentPane().add(new JButton("none"));
                frame.setBounds(100, 100, 100, 100);
                frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                frame.setVisible(true);
            });

            Robot robot = new Robot();
            robot.setAutoWaitForIdle(true);
            robot.setAutoDelay(50);

            robot.waitForIdle();
            robot.delay(1000);

            SwingUtilities.invokeAndWait(() -> {
                Point location = frame.getLocationOnScreen();
                Insets insets = frame.getInsets();

                location.translate(insets.left + 15, insets.top + bar.getHeight() / 2);

                frameLoc = location;
            });

            robot.mouseMove(frameLoc.x, frameLoc.y);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

            robot.delay(1000);

            SwingUtilities.invokeAndWait(() -> {
                Point location = new Point(frameLoc);
                location.y += bar.getHeight() / 2 + item.getHeight() / 2;

                frameLoc = location;
            });

            robot.mouseMove(frameLoc.x, frameLoc.y);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

            robot.waitForIdle();
            robot.delay(500);

            if (!actionFired) {
                throw new RuntimeException("Menu closed without action");
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
