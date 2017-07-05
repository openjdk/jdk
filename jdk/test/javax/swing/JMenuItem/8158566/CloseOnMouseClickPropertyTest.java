/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/*
 * @test
 * @bug 8158566 8160879 8160977
 * @summary Provide a Swing property which modifies MenuItemUI behaviour
 */
public class CloseOnMouseClickPropertyTest {

    private static JFrame frame;
    private static JMenu menu;

    public static void main(String[] args) throws Exception {

        for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
            UIManager.setLookAndFeel(info.getClassName());
            test(true);

            setProperty(false);
            test(false);

            setProperty(true);
            test(true);
        }
    }

    private static void setProperty(boolean closeOnMouseClick) {
        UIManager.put("CheckBoxMenuItem.closeOnMouseClick", closeOnMouseClick);
        UIManager.put("RadioButtonMenuItem.closeOnMouseClick", closeOnMouseClick);
    }

    private static void test(boolean closeOnMouseClick) throws Exception {
        for (TestType testType : TestType.values()) {
            test(testType, closeOnMouseClick);
        }
    }

    private static void test(TestType testType, boolean closeOnMouseClick)
            throws Exception {

        Robot robot = new Robot();
        robot.setAutoDelay(50);
        SwingUtilities.invokeAndWait(() -> createAndShowGUI(testType));
        robot.waitForIdle();

        Point point = getClickPoint(true);
        robot.mouseMove(point.x, point.y);
        robot.mousePress(InputEvent.BUTTON1_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_MASK);
        robot.waitForIdle();

        point = getClickPoint(false);
        robot.mouseMove(point.x, point.y);
        robot.mousePress(InputEvent.BUTTON1_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_MASK);
        robot.waitForIdle();

        SwingUtilities.invokeAndWait(() -> {
            JMenuItem menuItem = menu.getItem(0);
            boolean isShowing = menuItem.isShowing();
            frame.dispose();

            if (TestType.MENU_ITEM.equals(testType)) {
                if (isShowing) {
                    throw new RuntimeException("Menu Item is not closed!");
                }
            } else {
                if (isShowing ^ !closeOnMouseClick) {
                    throw new RuntimeException("Property is not taken into account:"
                            + " closeOnMouseClick");
                }
            }
        });
    }

    private static void createAndShowGUI(TestType testType) {

        frame = new JFrame();
        frame.setSize(300, 300);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JMenuBar menuBar = new JMenuBar();
        menu = new JMenu("Menu");
        menu.add(getMenuItem(testType));
        menuBar.add(menu);
        frame.setJMenuBar(menuBar);
        frame.setVisible(true);
    }

    private static JMenuItem getMenuItem(TestType testType) {
        switch (testType) {
            case CHECK_BOX_MENU_ITEM:
                return new JCheckBoxMenuItem("Check Box");
            case RADIO_BUTTON_MENU_ITEM:
                return new JRadioButtonMenuItem("Radio Button");
            default:
                return new JMenuItem("Menu Item");
        }
    }

    private static Point getClickPoint(boolean parent) throws Exception {
        Point points[] = new Point[1];

        SwingUtilities.invokeAndWait(() -> {

            JComponent comp = parent ? menu : menu.getItem(0);

            Point point = comp.getLocationOnScreen();
            Rectangle bounds = comp.getBounds();
            point.x += bounds.getWidth() / 2;
            point.y += bounds.getHeight() / 2;

            points[0] = point;
        });

        return points[0];
    }

    enum TestType {

        MENU_ITEM,
        CHECK_BOX_MENU_ITEM,
        RADIO_BUTTON_MENU_ITEM
    }
}
