/*
 * Copyright (c) 2006, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6364741
 * @key headful
 * @requires (os.family == "windows")
 * @summary REG: Using frame's menu actions does not make swing menu disappear on WinXP,
 *          since Mustang-b53
 */

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.Point;
import java.awt.Robot;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

public class SystemMenuTest implements MenuListener {

    static volatile JMenu menu;
    static volatile JMenu sub_menu;
    static volatile JFrame frame;

    static volatile int selectCount = 0;
    static volatile int deselectCount = 0;
    static volatile boolean failed = false;
    static volatile String reason = "none";

    static void createUI() {
        SystemMenuTest smt = new SystemMenuTest();
        sub_menu = new JMenu("SubMenu");
        sub_menu.addMenuListener(smt);
        sub_menu.add(new JMenuItem("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"));
        sub_menu.add(new JMenuItem("BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"));
        menu = new JMenu("Menu");
        menu.addMenuListener(smt);
        menu.add(sub_menu);
        JMenuBar mb = new JMenuBar();
        mb.add(menu);

        frame = new JFrame("JFrame");
        frame.setJMenuBar(mb);
        frame.pack();
        frame.setVisible(true);
    }

    public static void main(String[] args) throws Exception {

        Robot robot = new Robot();

        SwingUtilities.invokeAndWait(SystemMenuTest::createUI);

        try {
            robot.waitForIdle();
            robot.delay(2000);

            Point p = menu.getLocationOnScreen();
            robot.mouseMove(p.x + menu.getWidth() / 2, p.y + menu.getHeight() / 2);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();
            robot.delay(2000);

            p = sub_menu.getLocationOnScreen();
            robot.mouseMove(p.x + sub_menu.getWidth() / 2, p.y + sub_menu.getHeight() /2 );
            robot.mousePress(InputEvent.BUTTON1_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_MASK);
            robot.waitForIdle();
            robot.delay(2000);

            // Alt-Space to invoke System Menu, should close Swing menus.
            robot.keyPress(KeyEvent.VK_ALT);
            robot.keyPress(KeyEvent.VK_SPACE);
            robot.delay(50);
            robot.keyRelease(KeyEvent.VK_SPACE);
            robot.keyRelease(KeyEvent.VK_ALT);
            robot.waitForIdle();
            robot.delay(2000);

            if (selectCount != 2 || deselectCount != 2) {
                throw new RuntimeException("unexpected selection count " + selectCount + ", " + deselectCount);
            }
            if (failed) {
                throw new RuntimeException("Failed because " + reason);
            }
        } finally {
            if (frame != null) {
                SwingUtilities.invokeAndWait(frame::dispose);
            }
        }
    }

    public void menuCanceled(MenuEvent e) {
       System.out.println("cancelled"+e.getSource());
    }

    public void menuDeselected(MenuEvent e) {
       deselectCount++;
       if (selectCount != 2) {
          failed = true;
          reason = "deselect without two selects";
       }
       System.out.println("deselected"+e.getSource());
    }

    public void menuSelected(MenuEvent e) {
       if (deselectCount != 0) {
          failed = true;
          reason = "select without non-zero deselects";
       }
       selectCount++;
       System.out.println("selected"+e.getSource());
    }
}
