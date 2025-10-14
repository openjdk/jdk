/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4342646
 * @summary Tests that very long menus are properly placed on the screen.
 * @key headful
 * @run main bug4342646
 */

import java.awt.Point;
import java.awt.Robot;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;

public class bug4342646 {

    private static JFrame frame;
    private static JMenu menu;
    private static volatile Point popupLoc;
    private static volatile Point menuLoc;

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        try {
            SwingUtilities.invokeAndWait(() -> {
                JMenuBar mbar = new JMenuBar();
                menu = new JMenu("Menu");
                menu.add(new JMenuItem(
                         "AAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
                         "AAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
                         "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
                         "AAAAAAAAAAAAAAAAAAAAAAA"));
                mbar.add(menu);

                frame = new JFrame("4342646");
                frame.setJMenuBar(mbar);
                frame.setBounds(10, 10, 200, 100);
                frame.setVisible(true);
            });
            robot.waitForIdle();
            robot.delay(1000);
            SwingUtilities.invokeAndWait(() -> {
                menu.doClick();
            });
            robot.waitForIdle();
            robot.delay(200);
            SwingUtilities.invokeAndWait(() -> {
                popupLoc = menu.getPopupMenu().getLocationOnScreen();
                menuLoc = menu.getLocationOnScreen();
            });
            System.out.println(menuLoc);
            System.out.println(popupLoc);
            if (popupLoc.getX() < menuLoc.getX()) {
                throw new RuntimeException("PopupMenu is incorrectly placed at left of menu");
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
