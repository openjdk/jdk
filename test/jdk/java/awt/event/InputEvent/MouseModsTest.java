/*
 * Copyright (c) 2001, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @test
  @bug 4353201
  @summary Wrong modifiers on InputEvent
  @key headful
  @run main MouseModsTest
*/

import java.awt.AWTEvent;
import java.awt.AWTException;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.MouseInfo;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.InvocationTargetException;

public class MouseModsTest {
    static volatile int testCtrl = 0;
    static volatile int testBtn = 0;
    static volatile boolean passed = true;

    static final int BUTTONS = Math.min(3, MouseInfo.getNumberOfButtons());
    static final int KEYS = 2;

    static Frame frame;
    static Panel panel;
    static Canvas button1;
    static Canvas button2;

    static volatile Point pt1;
    static volatile Point pt2;

    public static void main(String args[]) throws AWTException,
            InterruptedException, InvocationTargetException {

        EventQueue.invokeAndWait(() -> {
            frame = new Frame("MouseModsTest");
            panel = new Panel();
            button1 = new TestCanvas();
            button2 = new TestCanvas();
            frame.setSize(300, 200);
            panel.add(button1);
            panel.add(button2);
            frame.add(panel);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });

        try {
            Robot robot;
            robot = new Robot();
            robot.setAutoWaitForIdle(true);
            robot.setAutoDelay(50);
            robot.delay(1000);

            EventQueue.invokeAndWait(() -> {
                pt1 = button1.getLocationOnScreen();
                pt2 = button2.getLocationOnScreen();

                pt1.x += button1.getSize().width / 2;
                pt1.y += button1.getSize().height / 2;

                pt2.x += button2.getSize().width / 2;
                pt2.y += button2.getSize().height / 2;
            });

            robot.mouseMove(pt2.x, pt2.y);

            //Keyboard to Mouse Test
            for (int ctrl = 1; ctrl <= KEYS; ++ctrl) {
                testCtrl = ctrl;
                robot.keyPress(getKeycode(ctrl));
                robot.keyPress(KeyEvent.VK_A);
                robot.keyRelease(KeyEvent.VK_A);
                for (int btn = 2; btn <= BUTTONS; ++btn) {
                    testBtn = btn;
                    robot.mousePress(getMouseModifier(btn));
                    robot.mouseMove(pt1.x, pt1.y);
                    robot.mouseMove(pt2.x, pt2.y);
                    robot.mouseRelease(getMouseModifier(btn));
                }
                robot.keyRelease(getKeycode(ctrl));
            }

            //Mouse to Mouse Test
            for (int btn1 = 1; btn1 <= BUTTONS; ++btn1) {
                testBtn = btn1;
                robot.mousePress(getMouseModifier(btn1));
                for (int btn = 1; btn <= BUTTONS; ++btn) {
                    if (btn == btn1) continue;
                    testBtn = btn;
                    robot.mousePress(getMouseModifier(btn));
                    robot.mouseMove(pt1.x, pt1.y);
                    robot.mouseMove(pt2.x, pt2.y);
                    robot.mouseRelease(getMouseModifier(btn));
                }
                testBtn = btn1;
                robot.mouseRelease(getMouseModifier(btn1));
            }
            testBtn = 0;
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }

        if (!passed) {
            throw new RuntimeException("Test Failed");
        }
    }

    static int getKeycode(int ctrl) {
        switch (ctrl) {
        case 1: return KeyEvent.VK_SHIFT;
        case 2: return KeyEvent.VK_CONTROL;
        case 3: return KeyEvent.VK_ALT;
        default: return 0;
        }
    }

    static int getKeyModifier(int ctrl) {
        switch (ctrl) {
        case 1: return InputEvent.SHIFT_MASK;
        case 2: return InputEvent.CTRL_MASK;
        case 3: return InputEvent.ALT_MASK;
        default: return 0;
        }
    }

    static int getMouseModifier(int btn) {
        switch (btn) {
        case 1: return InputEvent.BUTTON1_MASK;
        case 2: return InputEvent.BUTTON2_MASK;
        case 3: return InputEvent.BUTTON3_MASK;
        default: return 0;
        }
    }

    static final int allKeyMods =
    InputEvent.SHIFT_MASK
    | InputEvent.CTRL_MASK
    | InputEvent.ALT_MASK;

    static final int allMouseMods =
    InputEvent.BUTTON1_MASK
    | InputEvent.BUTTON2_MASK
    | InputEvent.BUTTON3_MASK;

    static void printInputEvent(InputEvent e) {
        System.out.println(e);
        if (e.isAltDown()) {
            System.out.println("Alt is Down");
        }
        if (e.isControlDown()) {
            System.out.println("Ctrl is Down");
        }
        if (e.isShiftDown()) {
            System.out.println("Shift is Down");
        }
        if (e.isMetaDown()) {
            System.out.println("Meta is Down");
        }
    }

    static class TestCanvas extends Canvas {
        public TestCanvas() {
            setSize(100, 100);
            setBackground(Color.blue);
            enableEvents(AWTEvent.MOUSE_EVENT_MASK
                         | AWTEvent.MOUSE_MOTION_EVENT_MASK
                         | AWTEvent.KEY_EVENT_MASK);
        }

        protected void processMouseEvent(MouseEvent e) {
            try {
                if (testBtn == 0) {
                    return;
                }
                if (e.getID() == MouseEvent.MOUSE_ENTERED
                    || e.getID() == MouseEvent.MOUSE_EXITED)
                {
                    if ((e.getModifiers() & getMouseModifier(testBtn)) != 0) {
                        System.out.println("Mouse modifiers on MOUSE_ENTERED, MOUSE_EXITED are set");
                    } else {
                        printInputEvent(e);
                        System.out.println("Cur mods = " + (e.getModifiers() & allMouseMods) + " Wanted = " +
                                          getMouseModifier(testBtn));
                        passed = false;
                        throw new RuntimeException("Mouse modifiers on MOUSE_ENTERED, MOUSE_EXITED aren't set");
                    }
                }
                if (e.getID() == MouseEvent.MOUSE_PRESSED
                    || e.getID() == MouseEvent.MOUSE_RELEASED)
                {
                    if ((e.getModifiers() & getMouseModifier(testBtn)) != 0) {
                        System.out.println("Right Mouse modifiers on MOUSE_PRESSED, MOUSE_RELEASED");
                    } else {
                        printInputEvent(e);
                        System.out.println("Cur mods = " + (e.getModifiers() & allMouseMods) + " Wanted = " +
                                          getMouseModifier(testBtn));
                        passed = false;
                        throw new RuntimeException("Wrong Mouse modifiers on MOUSE_PRESSED, MOUSE_RELEASED");
                    }
                }
            } finally {
                synchronized (frame) {
                    frame.notify();
                }
            }
        }

        protected void processMouseMotionEvent(MouseEvent e) {
            try {
                if (testBtn == 0) {
                    return;
                }
                if (e.getID() == MouseEvent.MOUSE_DRAGGED) {
                    if ((e.getModifiers() & getMouseModifier(testBtn)) != 0) {
                        System.out.println("Mouse modifiers on MOUSE_DRAGGED are set");
                    } else {
                        printInputEvent(e);
                        System.out.println("Cur mods = " + (e.getModifiers() & allMouseMods) + " Wanted = " +
                                          getMouseModifier(testBtn));
                        passed = false;
                        throw new RuntimeException("Mouse modifiers on MOUSE_DRAGGED aren't set");
                    }
                }
            } finally {
                synchronized (frame) {
                    frame.notify();
                }
            }
        }

        protected void processKeyEvent(KeyEvent e) {
            try {
                if (e.getKeyCode() != KeyEvent.VK_A) {
                    return;
                }
                if ((e.getModifiers() & getKeyModifier(testCtrl)) != 0) {
                    System.out.println("Right Key modifiers on KeyEvent");
                } else {
                    printInputEvent(e);
                    passed = false;
                    throw new RuntimeException("Wrong Key modifiers on KeyEvent");
                }
            } finally {
                synchronized (frame) {
                    frame.notify();
                }
            }
        }
    }
}
