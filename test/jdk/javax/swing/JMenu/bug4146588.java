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
 * @bug 4146588
 * @summary JMenu.setMenuLocation has no effect
 * @key headful
 * @run main bug4146588
 */

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.SwingUtilities;

public class bug4146588  {

    private static JFrame fr;
    private static JMenu menu;
    private static volatile Point loc;
    private static volatile Point popupLoc;
    private static volatile Point menuLoc;
    private static volatile Rectangle frameBounds;
    private static volatile Rectangle popupBounds;

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        try {
            SwingUtilities.invokeAndWait(() -> {
                fr = new JFrame("bug4146588 frame");

                JMenuBar menubar = new JMenuBar();
                menu = new JMenu("Menu");
                menu.add("Item 1");
                menu.add("Item 2");
                menu.add("Item 3");
                menu.setMenuLocation(150, 150);
                menubar.add(menu);
                fr.setJMenuBar(menubar);

                fr.setSize(400, 400);
                fr.setLocationRelativeTo(null);
                fr.setVisible(true);
            });
            robot.waitForIdle();
            robot.delay(1000);
            SwingUtilities.invokeAndWait(() -> {
                menu.doClick(0);
            });
            robot.waitForIdle();
            robot.delay(200);
            SwingUtilities.invokeAndWait(() -> {
                popupLoc = menu.getPopupMenu().getLocationOnScreen();
                menuLoc = menu.getLocationOnScreen();
                frameBounds = fr.getBounds();
                popupBounds = menu.getPopupMenu().getBounds();
            });
            System.out.println(popupLoc);
            System.out.println(popupBounds);
            System.out.println(frameBounds);
            System.out.println(menuLoc);
            if (!(popupLoc.getX()
                 > ((frameBounds.getX() + frameBounds.getWidth() / 2) - popupBounds.getWidth()))
                && (popupLoc.getY()
                 > ((frameBounds.getY() + frameBounds.getHeight() / 2) - popupBounds.getHeight()))) {
                throw new RuntimeException("popup is not at center of frame");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (fr != null) {
                    fr.dispose();
                }
            });
        }
    }
}
