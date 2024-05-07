/*
 * Copyright (c) 2004, 2023, Oracle and/or its affiliates. All rights reserved.
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

import javax.swing.JLabel;
import javax.swing.JFrame;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/*
 * @test
 * @bug 4966109
 * @summary Popup is not populated by mouse actions on lightweight components without mouse
 * @key headful
 * @run main bug4966109
 */

public class bug4966109 {
    public static JFrame mainFrame;
    public static JPopupMenu popup;
    public static JLabel label1;
    public static JLabel label2;
    public static Robot robot;
    public static volatile Point loc;
    public static volatile Boolean passed = true;
    public static int popupTrigger;

    public static void main(String[] args) throws Exception {
        try {
            robot = new Robot();
            SwingUtilities.invokeAndWait(() -> {
                mainFrame = new JFrame("Bug4966109");
                popup = new JPopupMenu();
                label1 = new JLabel("Label with the listener");
                label2 = new JLabel("Label w/o listener");
                mainFrame.setLayout(new BorderLayout());
                mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                mainFrame.add(label1, BorderLayout.NORTH);
                mainFrame.add(label2, BorderLayout.SOUTH);
                mainFrame.pack();
                mainFrame.setVisible(true);
                popup.add("One");
                popup.add("Two");
                popup.add("Three");
                label1.setComponentPopupMenu(popup);
                label1.addMouseListener(new MouseAdapter() {
                });
                label2.setComponentPopupMenu(popup);
            });
            robot.waitForIdle();
            robot.delay(1000);

            SwingUtilities.invokeAndWait(() -> {
                loc = label1.getLocationOnScreen();
                loc.x = loc.x + (int) (label1.getWidth() / 2);
                loc.y = loc.y + (int) (label1.getHeight() / 2);
            });
            popupTrigger = MouseEvent.BUTTON2_DOWN_MASK;
            robot.mouseMove(loc.x, loc.y);
            robot.mousePress(popupTrigger);
            robot.mouseRelease(popupTrigger);
            robot.waitForIdle();
            robot.delay(100);

            SwingUtilities.invokeAndWait(() -> {
                if (popup.isVisible()) {
                    System.out.println("ZAV: Good!!! BUTTON2 is the way to go.");
                } else {
                    System.out.println("ZAV: Bad :( Let's try BUTTON3");
                    popupTrigger = MouseEvent.BUTTON3_DOWN_MASK;
                }
            });

            robot.mousePress(popupTrigger);
            robot.mouseRelease(popupTrigger);
            robot.waitForIdle();
            robot.delay(100);

            SwingUtilities.invokeAndWait(() -> {
                if (popup.isVisible()) {
                    System.out.println("ZAV: Good!!! BUTTON3 is working. At last :)");
                    popup.setVisible(false);
                } else {
                    System.out.println("ZAV: Bad :( Very bad. Nothing is working...");
                    passed = false;
                }
            });
            if (!passed) {
                throw new RuntimeException("No popup trigger mouse events found");
            }
            robot.waitForIdle();
            robot.delay(100);

            SwingUtilities.invokeAndWait(() -> {
                loc = label2.getLocationOnScreen();
                loc.x = loc.x + (int) (label2.getWidth() / 2);
                loc.y = loc.y + (int) (label2.getHeight() / 2);
            });
            robot.mouseMove(loc.x, loc.y);
            robot.mousePress(popupTrigger);
            robot.mouseRelease(popupTrigger);
            robot.waitForIdle();
            robot.delay(100);

            SwingUtilities.invokeAndWait(() -> {
                if (!popup.isVisible()) {
                    passed = false;
                }
            });
            if (!passed) {
                throw new RuntimeException("Regression: bug 4966109, popup is not visible");
            }
        } finally {
            if (mainFrame != null) {
                mainFrame.dispose();
            }
        }
        System.out.println("test Passed!");
    }
}
