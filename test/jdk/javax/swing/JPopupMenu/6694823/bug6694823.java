/*
 * Copyright (c) 2008, 2024, Oracle and/or its affiliates. All rights reserved.
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


import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Toolkit;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

/*
 * @test
 * @key headful
 * @bug 6694823
 * @summary Checks that popup menu cannot be partially hidden
 *          by the task bar.
 * @run main bug6694823
 */

public class bug6694823 {
    private static JFrame frame;
    private static JPopupMenu popup;
    private static Toolkit toolkit;
    private static Insets screenInsets;
    private static Robot robot;

    public static void main(String[] args) throws Exception {
        try {
            robot = new Robot();
            toolkit = Toolkit.getDefaultToolkit();
            SwingUtilities.invokeAndWait(() -> createGui());
            robot.waitForIdle();
            robot.delay(1000);

            // Get screen insets
            screenInsets = toolkit.getScreenInsets(frame.getGraphicsConfiguration());
            if (screenInsets.bottom == 0) {
                // This test is only for configurations with taskbar on the bottom
                return;
            }
            // The popup shouldn't overlap the task bar. It should be shifted up.
            checkPopup();
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (popup != null) {
                    popup.setVisible(false);
                }
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static void createGui() {
        frame = new JFrame();
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setUndecorated(true);

        popup = new JPopupMenu("Menu");
        for (int i = 0; i < 7; i++) {
            popup.add(new JMenuItem("MenuItem"));
        }
        JPanel panel = new JPanel();
        panel.setComponentPopupMenu(popup);
        frame.add(panel);

        frame.setSize(200, 200);
    }

    private static void checkPopup() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            // Place frame just above the task bar
            Dimension screenSize = toolkit.getScreenSize();
            frame.setLocation(screenSize.width / 2,
                    screenSize.height - frame.getHeight() - screenInsets.bottom);
            frame.setVisible(true);
        });
        // Ensure frame is visible
        robot.waitForIdle();
        robot.delay(300);

        final Point point = new Point();
        SwingUtilities.invokeAndWait(() -> {
            // Place popup over the task bar
            point.x = 0;
            point.y = frame.getHeight() - popup.getPreferredSize().height + screenInsets.bottom;
            popup.show(frame, point.x, point.y);
        });
        // Ensure popup is visible
        robot.waitForIdle();
        robot.delay(300);

        SwingUtilities.invokeAndWait(() -> {
            Point frameLoc = frame.getLocationOnScreen();
            if (popup.getLocationOnScreen().equals(new Point(frameLoc.x, frameLoc.y + point.y))) {
                throw new RuntimeException("Popup is not shifted");
            }
        });
    }
}
