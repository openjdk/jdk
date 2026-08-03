/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/*
 * @test
 * @key headful
 * @bug 8007006
 * @requires (os.family == "mac")
 * @summary [macosx] Closing subwindow loses main window menus.
 */

public class bug8007006 {
    private static Frame mainFrame;
    private static Frame subFrame;
    private static final CountDownLatch isActionPerformed = new CountDownLatch(1);

    public static void main(String[] args) throws Exception {
        try {
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            Robot robot = new Robot();

            EventQueue.invokeAndWait(bug8007006::createAndShowGUI);
            robot.waitForIdle();
            robot.delay(1000);

            EventQueue.invokeAndWait(() -> subFrame.dispose());
            robot.waitForIdle();
            robot.delay(300);

            performMenuItemTest(robot);

            if (!isActionPerformed.await(1, TimeUnit.SECONDS)) {
                throw new Exception("Test failed: menu item action was not performed");
            }
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (mainFrame != null) {
                    mainFrame.dispose();
                }
            });
        }
    }

    private static void createAndShowGUI() {
        mainFrame = new Frame("Frame 1");
        mainFrame.setMenuBar(createMenuBar());
        mainFrame.setSize(200, 200);
        mainFrame.setBackground(Color.GREEN);
        mainFrame.setLocationRelativeTo(null);

        subFrame = new Frame("Frame 2");
        subFrame.setMenuBar(createMenuBar());
        subFrame.setSize(200, 200);
        subFrame.setBackground(Color.RED);
        subFrame.setLocationRelativeTo(null);

        mainFrame.setVisible(true);
        subFrame.setVisible(true);
    }

    private static MenuBar createMenuBar() {
        // A very long name makes it more likely
        // that the robot will hit the menu
        Menu menu = new Menu("TestTestTestTestTestTestTestTestTestTest");
        MenuItem item = new MenuItem("TestTestTestTestTestTestTestTestTestTest");
        menu.add(item);
        item.addActionListener(ev -> isActionPerformed.countDown());

        MenuBar mb = new MenuBar();
        mb.add(menu);
        return mb;
    }

    private static void performMenuItemTest(Robot robot) {
        // Find the menu on the screen menu bar
        // The location depends upon the application name which is the name
        // of the first menu.
        // Unfortunately, the application name can vary based on how the
        // application is run.
        // The workaround is to make the menu and the menu item names very
        // long.
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        GraphicsConfiguration gc =
                GraphicsEnvironment.getLocalGraphicsEnvironment()
                                   .getDefaultScreenDevice()
                                   .getDefaultConfiguration();

        Insets screenInsets = toolkit.getScreenInsets(gc);
        System.out.println("Screen insets: " + screenInsets);

        int menuBarX = 250;
        int menuBarY = screenInsets.top / 2;
        int menuItemX = menuBarX;
        int menuItemY = screenInsets.top + 10;
        robot.mouseMove(menuBarX, menuBarY);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();

        robot.mouseMove(menuItemX, menuItemY);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
    }
}
