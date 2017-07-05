/*
 * Copyright (c) 2008, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6694823
 * @summary Checks that popup menu cannot be partially hidden
 * by the task bar in applets.
 * @author Mikhail Lapshin
 * @run main bug6694823
 */

import javax.swing.*;
import java.awt.*;
import sun.awt.SunToolkit;
import java.security.Permission;
import sun.security.util.SecurityConstants;

public class bug6694823 {
    private static JFrame frame;
    private static JPopupMenu popup;
    private static SunToolkit toolkit;
    private static Insets screenInsets;

    public static void main(String[] args) throws Exception {
        toolkit = (SunToolkit) Toolkit.getDefaultToolkit();
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                createGui();
            }
        });

        toolkit.realSync();

        // Get screen insets
        screenInsets = toolkit.getScreenInsets(frame.getGraphicsConfiguration());
        if (screenInsets.bottom == 0) {
            // This test is only for configurations with taskbar on the bottom
            return;
        }

        System.setSecurityManager(new SecurityManager(){

            private String allowsAlwaysOnTopPermission = SecurityConstants.AWT.SET_WINDOW_ALWAYS_ON_TOP_PERMISSION.getName();

            @Override
            public void checkPermission(Permission perm) {
                if (allowsAlwaysOnTopPermission.equals(perm.getName())) {
                    throw new SecurityException();
                }
            }

        });

        // Show popup as if from an applet
        // The popup shouldn't overlap the task bar. It should be shifted up.
        checkPopup();

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
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                // Place frame just above the task bar
                Dimension screenSize = toolkit.getScreenSize();
                frame.setLocation(screenSize.width / 2,
                        screenSize.height - frame.getHeight() - screenInsets.bottom);
                frame.setVisible(true);
            }
        });

        // Ensure frame is visible
        toolkit.realSync();

        final Point point = new Point();
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                // Place popup over the task bar
                point.x = 0;
                point.y = frame.getHeight() - popup.getPreferredSize().height + screenInsets.bottom;
                popup.show(frame, point.x, point.y);
            }
        });

        // Ensure popup is visible
        toolkit.realSync();

        SwingUtilities.invokeAndWait(new Runnable() {

            public void run() {
                Point frameLoc = frame.getLocationOnScreen();
                if (popup.getLocationOnScreen().equals(new Point(frameLoc.x, frameLoc.y + point.y))) {
                    throw new RuntimeException("Popup is not shifted");
                }
                popup.setVisible(false);
                frame.dispose();
            }
        });
    }
}
