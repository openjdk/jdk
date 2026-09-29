/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary tests if nested menu is displayed on Wayland
 * @requires (os.family == "linux")
 * @key headful
 * @bug 8342096
 * @library /test/lib
 * @build jtreg.SkippedException
 * @run main NestedFocusablePopupTest
 */

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.IllegalComponentStateException;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jtreg.SkippedException;

public class NestedFocusablePopupTest {

    static volatile JMenu menuWithFocusableItem;
    static volatile JMenu menuWithNonFocusableItem;
    static volatile JPopupMenu popupMenu;
    static volatile JFrame frame;
    static volatile Robot robot;

    public static void main(String[] args) throws Exception {
        if (System.getenv("WAYLAND_DISPLAY") == null) {
            throw new SkippedException("XWayland only test");
        }

        robot = new Robot();
        robot.setAutoDelay(50);

        try {
            SwingUtilities.invokeAndWait(NestedFocusablePopupTest::initAndShowGui);
            test0();
            test1();
        } finally {
            SwingUtilities.invokeAndWait(frame::dispose);
        }
    }

    public static void waitTillShown(final Component component, long msTimeout)
            throws InterruptedException, TimeoutException {
        long startTime = System.currentTimeMillis();

        while (true) {
            try {
                Thread.sleep(50);
                component.getLocationOnScreen();
                break;
            } catch (IllegalComponentStateException e) {
                if (System.currentTimeMillis() - startTime > msTimeout) {
                    throw new TimeoutException("Component not shown within the specified timeout");
                }
            }
        }
    }

    static Rectangle waitAndGetOnScreenBoundsOnEDT(Component component)
            throws InterruptedException, TimeoutException, ExecutionException {
        waitTillShown(component, 500);
        robot.waitForIdle();

        FutureTask<Rectangle> task = new FutureTask<>(()
                -> new Rectangle(component.getLocationOnScreen(), component.getSize()));
        SwingUtilities.invokeLater(task);
        return task.get(500, TimeUnit.MILLISECONDS);
    }

    static void test0() throws Exception {
        Rectangle frameBounds = waitAndGetOnScreenBoundsOnEDT(frame);
        robot.mouseMove(frameBounds.x + frameBounds.width / 2,
                frameBounds.y + frameBounds.height / 2);

        robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);

        Rectangle menuBounds = waitAndGetOnScreenBoundsOnEDT(menuWithFocusableItem);
        robot.mouseMove(menuBounds.x + 5, menuBounds.y + 5);

        // Give popup some time to disappear (in case of failure)
        robot.waitForIdle();
        robot.delay(200);

        try {
            waitTillShown(popupMenu, 500);
        } catch (TimeoutException e) {
            throw new RuntimeException("The popupMenu disappeared when it shouldn't have.");
        }
    }

    static void test1() throws Exception {
        Rectangle frameBounds = waitAndGetOnScreenBoundsOnEDT(frame);
        robot.mouseMove(frameBounds.x + frameBounds.width / 2,
                frameBounds.y + frameBounds.height / 2);

        robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);

        Rectangle menuBounds = waitAndGetOnScreenBoundsOnEDT(menuWithFocusableItem);
        robot.mouseMove(menuBounds.x + 5, menuBounds.y + 5);
        robot.waitForIdle();
        robot.delay(200);

        menuBounds = waitAndGetOnScreenBoundsOnEDT(menuWithNonFocusableItem);
        robot.mouseMove(menuBounds.x + 5, menuBounds.y + 5);

        // Give popup some time to disappear (in case of failure)
        robot.waitForIdle();
        robot.delay(200);

        try {
            waitTillShown(popupMenu, 500);
        } catch (TimeoutException e) {
            throw new RuntimeException("The popupMenu disappeared when it shouldn't have.");
        }
    }

    static JMenu getMenuWithMenuItem(boolean isSubmenuItemFocusable, String text) {
        JMenu menu = new JMenu(text);
        menu.add(isSubmenuItemFocusable
                ? new JButton(text)
                : new JMenuItem(text)
        );
        return menu;
    }

    private static void initAndShowGui() {
        frame = new JFrame("NestedFocusablePopupTest");
        JPanel panel = new JPanel();
        panel.setPreferredSize(new Dimension(200, 180));


        popupMenu = new JPopupMenu();
        menuWithFocusableItem =
                getMenuWithMenuItem(true, "focusable subitem");
        menuWithNonFocusableItem =
                getMenuWithMenuItem(false, "non-focusable subitem");

        popupMenu.add(menuWithFocusableItem);
        popupMenu.add(menuWithNonFocusableItem);

        panel.setComponentPopupMenu(popupMenu);
        frame.add(panel);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
