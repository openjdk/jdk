/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.List;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

/*
 * @test
 * @key headful
 * @bug 8295774
 * @summary Verify that List Item selection via mouse/keys generates ItemEvent/ActionEvent appropriately.
 * @run main ListItemEventsTest
 */
public class ListItemEventsTest {

    private static final int MOUSE_DELAY = 100;
    private static final int KEYBOARD_DELAY = 1000;

    private static Frame frame;
    private volatile static List list;
    private volatile static boolean actionPerformed = false;
    private volatile static boolean itemStateChanged = false;
    private static Robot robot;

    public static void initializeGUI() {
        frame = new Frame("Test Frame");
        frame.setLayout(new FlowLayout());
        list = new List();
        list.add("One");
        list.add("Two");
        list.add("Three");
        list.add("Four");
        list.add("Five");
        list.addItemListener((event) -> {
            System.out.println("Got an ItemEvent: " + event);
            itemStateChanged = true;
        });
        list.addActionListener((event) -> {
            System.out.println("Got an ActionEvent: " + event);
            actionPerformed = true;
        });

        frame.add(list);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public static void main(String[] s) throws Exception {
        robot = new Robot();
        try {
            robot.setAutoDelay(MOUSE_DELAY);
            robot.setAutoWaitForIdle(true);

            EventQueue.invokeLater(ListItemEventsTest::initializeGUI);
            robot.waitForIdle();

            Point listAt = list.getLocationOnScreen();
            Dimension listSize = list.getSize();
            robot.mouseMove(listAt.x + listSize.width / 2,
                listAt.y + listSize.height / 2);

            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

            if (!itemStateChanged) {
                throw new RuntimeException(
                    "FAIL: List did not trigger ItemEvent when item selected!");
            }

            robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);
            robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);

            if (!actionPerformed) {
                throw new RuntimeException(
                    "FAIL: List did not trigger ActionEvent when double"
                        + " clicked!");
            }

            itemStateChanged = false;
            actionPerformed = false;

            EventQueue.invokeAndWait(() -> list.select(0));
            robot.waitForIdle();

            if (itemStateChanged) {
                throw new RuntimeException(
                    "FAIL: List triggered ItemEvent when item selected by "
                        + "calling the API!");
            }

            robot.setAutoDelay(KEYBOARD_DELAY);

            itemStateChanged = false;
            typeKey(KeyEvent.VK_DOWN);

            if (!itemStateChanged) {
                throw new RuntimeException(
                    "FAIL: List did not trigger ItemEvent when item selected by"
                        + " down arrow key!");
            }

            itemStateChanged = false;
            typeKey(KeyEvent.VK_UP);

            if (!itemStateChanged) {
                throw new RuntimeException(
                    "FAIL: List did not trigger ItemEvent when item selected by"
                        + " up arrow key!");
            }

            if (actionPerformed) {
                throw new RuntimeException(
                    "FAIL: List triggerd ActionEvent unnecessarily. Action generated"
                        + " when item selected using API or UP/DOWN keys!");
            }

            actionPerformed = false;
            typeKey(KeyEvent.VK_ENTER);

            if (!actionPerformed) {
                throw new RuntimeException(
                    "FAIL: List did not trigger ActionEvent when enter"
                        + " key typed!");
            }

            System.out.println("Test passed!");

        } finally {
            EventQueue.invokeAndWait(ListItemEventsTest::disposeFrame);
        }
    }

    public static void disposeFrame() {
        if (frame != null) {
            frame.dispose();
            frame = null;
        }
    }

    private static void typeKey(int key) throws Exception {
        robot.keyPress(key);
        robot.keyRelease(key);
    }
}
