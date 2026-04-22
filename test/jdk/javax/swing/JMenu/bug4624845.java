/*
 * Copyright (c) 2002, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4624845
 * @requires (os.family == "windows")
 * @summary Tests how submenus in WinLAF are painted
 * @key headful
 * @run main bug4624845
 */

import java.awt.Color;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class bug4624845 {
    private static JFrame f;
    private static JMenu menu, subMenu;
    private static JMenuItem menuItem;
    private static volatile Point menuLocation;
    private static volatile Point subMenuLocation;
    private static volatile Point menuItemLocation;
    private static volatile int menuWidth;
    private static volatile int menuHeight;
    private static volatile int subMenuWidth;
    private static volatile int subMenuHeight;
    private static volatile int menuItemWidth;
    private static volatile int menuItemHeight;
    private static Color menuItemColor;
    private static Color subMenuColor;
    private static boolean passed;
    private final static int OFFSET = 2;
    private static final int COLOR_TOLERANCE = 10;

    public static void main(String[] args) throws Exception {
        try {
            UIManager.setLookAndFeel
                ("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
        } catch (Exception e) {
            throw new RuntimeException("Failed to set Windows LAF");
        }
        try {
            bug4624845 test = new bug4624845();
            SwingUtilities.invokeAndWait(() -> test.createUI());
            runTest();
            verifyColor();
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (f != null) {
                    f.dispose();
                }
            });
        }
        if (!passed) {
            throw new RuntimeException("Nested MenuItem color : " +
                menuItemColor + " is not similar to sub Menu color : "
                + subMenuColor);
        }
    }
    private void createUI() {
        f = new JFrame("bug4624845");
        menu = new JMenu("Menu");
        menu.add(new JMenuItem("Item 1"));

        subMenu = new JMenu("Submenu");
        menuItem = new JMenuItem("This");
        subMenu.add(menuItem);
        subMenu.add(new JMenuItem("That"));
        menu.add(subMenu);

        JMenuBar mBar = new JMenuBar();
        mBar.add(menu);
        f.add(mBar);
        f.pack();
        f.setLocationRelativeTo(null);
        f.setVisible(true);
    }

    private static void runTest() throws Exception {
        Robot robot = new Robot();
        robot.setAutoDelay(200);
        robot.waitForIdle();
        SwingUtilities.invokeAndWait(() -> {
            menuLocation = menu.getLocationOnScreen();
            menuWidth = menu.getWidth();
            menuHeight = menu.getHeight();
        });
        robot.mouseMove(menuLocation.x + menuWidth / 2,
            menuLocation.y + menuHeight / 2 );
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
        SwingUtilities.invokeAndWait(() -> {
            subMenuLocation = subMenu.getLocationOnScreen();
            subMenuWidth = subMenu.getWidth();
            subMenuHeight = subMenu.getHeight();
        });
        robot.mouseMove(subMenuLocation.x + subMenuWidth / 2,
            subMenuLocation.y + subMenuHeight / 2 );
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
        subMenuColor = robot.
            getPixelColor(subMenuLocation.x + OFFSET,
                subMenuLocation.y + OFFSET);
        SwingUtilities.invokeAndWait(() -> {
            menuItemLocation = menuItem.getLocationOnScreen();
            menuItemWidth = subMenu.getWidth();
            menuItemHeight = subMenu.getHeight();
        });
        robot.mouseMove(menuItemLocation.x + menuItemWidth / 2,
            menuItemLocation.y + menuItemHeight / 2 );
        robot.waitForIdle();
        menuItemColor = robot.
            getPixelColor(menuItemLocation.x + OFFSET,
                menuItemLocation.y + OFFSET);
    }

    private static void verifyColor() {

        int red1 = subMenuColor.getRed();
        int blue1 = subMenuColor.getBlue();
        int green1 = subMenuColor.getGreen();

        int red2 = menuItemColor.getRed();
        int blue2 = menuItemColor.getBlue();
        int green2 = menuItemColor.getGreen();

        passed = true;
        if ((Math.abs(red1 - red2) > COLOR_TOLERANCE)
            || (Math.abs(green1 - green2) > COLOR_TOLERANCE)
            || (Math.abs(blue1 - blue2) > COLOR_TOLERANCE)) {
            passed = false;
        }
    }
}
