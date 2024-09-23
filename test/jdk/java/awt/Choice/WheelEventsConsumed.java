/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Choice;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

/*
 * @test
 * @bug 6253211
 * @summary PIT: MouseWheel events not triggered for Choice drop down in XAWT
 * @requires (os.family == "linux")
 * @key headful
 * @run main WheelEventsConsumed
 */

public class WheelEventsConsumed extends Frame implements MouseWheelListener
{
    Robot robot;
    Choice choice1 = new Choice();
    Point pt;
    final static int delay = 100;
    boolean mouseWheeled = false;
    final static int OUTSIDE_CHOICE = 1;
    final static int INSIDE_LIST_OF_CHOICE = 2;
    final static int INSIDE_CHOICE_COMPONENT = 3;
    static String toolkit;

    private static volatile WheelEventsConsumed frame = null;

    public static void main(String[] args) throws Exception {
        toolkit = Toolkit.getDefaultToolkit().getClass().getName();
        try {
            EventQueue.invokeAndWait(() -> {
                frame = new WheelEventsConsumed();
                frame.initAndShow();
            });
            frame.test();
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    public void mouseWheelMoved(MouseWheelEvent me) {
        mouseWheeled = true;
        System.out.println(me);
    }

    public void initAndShow() {
        setTitle("WheelEventsConsumed test");
        for (int i = 1; i < 10; i++) {
            choice1.add("item-0" + i);
        }

        choice1.addMouseWheelListener(this);
        add(choice1);
        setLayout(new FlowLayout());
        setSize(200, 200);
        setLocationRelativeTo(null);
        setVisible(true);
        validate();
    }

    public void test() {
        try {
            robot = new Robot();
            robot.setAutoWaitForIdle(true);
            robot.setAutoDelay(50);
            robot.waitForIdle();
            robot.delay(delay * 5);
            testMouseWheel(1, OUTSIDE_CHOICE);
            robot.delay(delay);
            testMouseWheel(-1, INSIDE_LIST_OF_CHOICE);
            robot.delay(delay);
            testMouseWheel(1, INSIDE_CHOICE_COMPONENT);
            robot.delay(delay);
        } catch (Throwable e) {
            throw new RuntimeException("Test failed. Exception thrown: " + e);
        }
    }

    public void testMouseWheel(int amt, int mousePosition) {
        pt = choice1.getLocationOnScreen();
        robot.mouseMove(pt.x + choice1.getWidth() / 2, pt.y + choice1.getHeight() / 2);

        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(50);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(50);

        switch (mousePosition) {
            case OUTSIDE_CHOICE:
                robot.mouseMove(pt.x + choice1.getWidth() * 3 / 2, pt.y + choice1.getHeight() / 2);
                break;
            case INSIDE_LIST_OF_CHOICE:
                robot.mouseMove(pt.x + choice1.getWidth() / 2, pt.y + choice1.getHeight() * 4);
                break;
            case INSIDE_CHOICE_COMPONENT:
                robot.mouseMove(pt.x + choice1.getWidth() / 2, pt.y + choice1.getHeight() / 2);
                break;
        }

        robot.delay(delay);
        for (int i = 0; i < 10; i++) {
            robot.mouseWheel(amt);
            robot.delay(delay);
        }

        if (!mouseWheeled) {
            if (toolkit.equals("sun.awt.windows.WToolkit") && mousePosition == OUTSIDE_CHOICE) {
                System.out.println("Passed. Separate case on Win32. Choice generated MouseWheel events" + mousePosition);
            } else {
                throw new RuntimeException("Test failed. Choice should generate MOUSE_WHEEL events." + mousePosition);
            }
        } else {
            System.out.println("Passed. Choice generated MouseWheel events" + mousePosition);
        }
        robot.keyPress(KeyEvent.VK_ESCAPE);
        robot.delay(10);
        robot.keyRelease(KeyEvent.VK_ESCAPE);
        robot.delay(200);
        mouseWheeled = false;
    }
}
