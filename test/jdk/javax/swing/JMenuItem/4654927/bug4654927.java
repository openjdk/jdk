/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @key headful
 * @bug 4654927
 * @summary Clicking on Greyed Menuitems closes the Menubar Dropdown
 * @library ../../regtesthelpers
 * @build Util
 * @run main bug4654927
 */

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.util.concurrent.Callable;

public class bug4654927 {

    private static volatile JMenu menu;
    private static volatile JMenuItem menuItem;
    private static JFrame frame;

    public static void main(String[] args) throws Exception {
        try {
            String systemLAF = UIManager.getSystemLookAndFeelClassName();
            // the test is not applicable to Motif L&F
            if (systemLAF.endsWith("MotifLookAndFeel")) {
                return;
            }

            UIManager.setLookAndFeel(systemLAF);
            Robot robot = new Robot();
            robot.setAutoWaitForIdle(true);
            robot.setAutoDelay(100);

            SwingUtilities.invokeAndWait(() -> createAndShowUI());

            robot.waitForIdle();
            robot.delay(1000);

            // test mouse press
            Point menuLocation = Util.getCenterPoint(menu);
            System.out.println("Menu Location " + menuLocation);
            robot.mouseMove(menuLocation.x, menuLocation.y);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();
            robot.delay(250);

            Point itemLocation = Util.getCenterPoint(menuItem);
            System.out.println("MenuItem Location " + itemLocation);
            robot.mouseMove(itemLocation.x, itemLocation.y);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();
            robot.delay(250);

            if (!isMenuItemShowing()) {
                throw new RuntimeException("Popup is unexpectedly closed");
            }

            // close menu
            robot.mouseMove(menuLocation.x, menuLocation.y);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();
            robot.delay(250);

            // test mouse drag
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.delay(250);
            Util.glide(robot, menuLocation.x, menuLocation.y,
                              itemLocation.x, itemLocation.y);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();

            if (!isMenuItemShowing()) {
                throw new RuntimeException("Popup is unexpectedly closed");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static boolean isMenuItemShowing() throws Exception {
        return Util.invokeOnEDT(new Callable<Boolean>() {

            @Override
            public Boolean call() throws Exception {
                return menuItem.isShowing();
            }
        });
    }

    private static void createAndShowUI() {
        frame = new JFrame();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        menu = new JMenu("Menu");
        menu.add(new JMenuItem("menuItem"));
        menuItem = new JMenuItem("menuItem");
        menuItem.setEnabled(false);
        menu.add(menuItem);
        menu.add(new JMenuItem("menuItem"));

        JMenuBar bar = new JMenuBar();
        bar.add(menu);
        frame.setJMenuBar(bar);

        frame.setSize(200, 200);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}

