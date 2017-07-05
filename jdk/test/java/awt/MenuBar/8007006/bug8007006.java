/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8007006
 * @summary [macosx] Closing subwindow loses main window menus.
 * @author Leonid Romanov
 * @run main bug8007006
 */

import sun.awt.SunToolkit;
import java.awt.*;
import java.awt.event.*;

public class bug8007006 {
    private static Frame frame1;
    private static Frame frame2;

    public static void main(String[] args) throws Exception {
        if (sun.awt.OSInfo.getOSType() != sun.awt.OSInfo.OSType.MACOSX) {
            System.out.println("This test is for MacOS only. Automatically passed on other platforms.");
            return;
        }

        System.setProperty("apple.laf.useScreenMenuBar", "true");

        createAndShowGUI();
        sleep(1500);

        frame2.dispose();
        sleep(1500);

        SunToolkit tk = (SunToolkit)Toolkit.getDefaultToolkit();

        Robot robot = new Robot();
        robot.setAutoDelay(50);

        // open "Apple" menu (the leftmost one)
        robot.keyPress(KeyEvent.VK_META);
        robot.keyPress(KeyEvent.VK_SHIFT);
        robot.keyPress(KeyEvent.VK_SLASH);
        robot.keyRelease(KeyEvent.VK_SLASH);
        robot.keyRelease(KeyEvent.VK_SHIFT);
        robot.keyRelease(KeyEvent.VK_META);

        // Select our menu
        robot.keyPress(KeyEvent.VK_LEFT);
        robot.keyRelease(KeyEvent.VK_LEFT);

        // Select menu item
        robot.keyPress(KeyEvent.VK_DOWN);
        robot.keyRelease(KeyEvent.VK_DOWN);
        robot.keyPress(KeyEvent.VK_ENTER);
        robot.keyRelease(KeyEvent.VK_ENTER);

        sleep(0);

        MenuBar mbar = frame1.getMenuBar();
        Menu menu = mbar.getMenu(0);
        CheckboxMenuItem item = (CheckboxMenuItem)menu.getItem(0);
        boolean isChecked = item.getState();

        frame1.dispose();

        if (isChecked) {
            throw new Exception("Test failed: menu item remained checked");
        }
    }

    private static void createAndShowGUI() {
        frame1 = new Frame("Frame 1");
        frame1.setMenuBar(createMenuBar());
        frame1.setSize(200, 200);

        frame2 = new Frame("Frame 2");
        frame2.setMenuBar(createMenuBar());
        frame2.setSize(200, 200);

        frame1.setVisible(true);
        frame2.setVisible(true);
    }

    private static MenuBar createMenuBar() {
        MenuBar mbar = new MenuBar();
        Menu menu = new Menu("Menu");
        MenuItem item = new CheckboxMenuItem("Checked", true);

        menu.add(item);
        mbar.add(menu);

        return mbar;
    }

    private static void sleep(int ms) {
        SunToolkit tk = (SunToolkit)Toolkit.getDefaultToolkit();
        tk.realSync();

        try {
            Thread.sleep(ms);
        } catch (Exception ignore) {
        }
    }
}
