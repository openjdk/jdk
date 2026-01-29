/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4117523
 * @summary Solaris: MousePressed event has modifier=0 when left button is pressed
 * @key headful
 * @library /javax/swing/regtesthelpers /test/lib
 * @build Util jdk.test.lib.Platform
 * @run main MouseModifierTest
*/


import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import jdk.test.lib.Platform;

public class MouseModifierTest {
    private static Frame frame;
    private static volatile MouseEvent lastMousePressedEvent = null;

    public static void main(String[] args) throws Exception {
        try {
            EventQueue.invokeAndWait(MouseModifierTest::createAndShowGUI);
            test();
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static void test() throws Exception {
        Robot robot = new Robot();
        robot.waitForIdle();
        robot.delay(500);

        Point centerPoint = Util.getCenterPoint(frame);

        System.out.println("MOUSE1 press case");

        robot.mouseMove(centerPoint.x, centerPoint.y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(25);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
        robot.delay(300);

        if (lastMousePressedEvent == null
                || lastMousePressedEvent.getModifiers() != InputEvent.BUTTON1_MASK) {
            throw new RuntimeException("Test failed");
        }

        if (Platform.isWindows()) {
            System.out.println("Windows: Testing ALT + MOUSE1 press case");
            lastMousePressedEvent = null;
            robot.waitForIdle();
            robot.delay(300);

            robot.keyPress(KeyEvent.VK_ALT);
            robot.delay(25);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.delay(25);
            robot.keyRelease(KeyEvent.VK_ALT);
            robot.delay(25);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();
            robot.delay(300);

            int expectedModifiers  = InputEvent.BUTTON1_MASK
                    | InputEvent.BUTTON2_MASK
                    | InputEvent.ALT_MASK;
            if (lastMousePressedEvent == null
                    || lastMousePressedEvent.getModifiers() != expectedModifiers) {
                throw new RuntimeException("Test failed");
            }
        }
    }

    private static void createAndShowGUI() {
        frame = new Frame("MouseModifierTest");
        frame.setSize(300, 300);
        frame.setLocationRelativeTo(null);
        frame.addMouseListener(new MouseHandler());
        frame.setVisible(true);
    }

    private static class MouseHandler extends MouseAdapter {
        public void mouseClicked(MouseEvent e) {
            System.out.println("\nmouseClicked:");
            printMouseEventDetail(e);
        }

        public void mousePressed(MouseEvent e) {
            lastMousePressedEvent = e;
            System.out.println("\nmousePressed:");
            printMouseEventDetail(e);
        }

        public void mouseReleased(MouseEvent e) {
            System.out.println("\nmouseReleased:");
            printMouseEventDetail(e);
        }

        public void mouseEntered(MouseEvent e) {
            System.out.println("\nmouseEntered:");
            printMouseEventDetail(e);
        }

        public void mouseExited(MouseEvent e) {
            System.out.println("\nmouseExited:");
            printMouseEventDetail(e);
        }

        private void printMouseEventDetail(MouseEvent e) {
            System.out.println(e.toString());
            System.out.println("Modifiers: ");
            printModifiers(e);
        }

        private void printModifiers(MouseEvent e) {
            if (e == null) {
                return;
            }

            int mod = e.getModifiers();

            if ((mod & InputEvent.ALT_MASK) != 0) {
                System.out.println("\tALT_MASK");
            }
            if ((mod & InputEvent.BUTTON1_MASK) != 0) {
                System.out.println("\tBUTTON1_MASK");
            }
            if ((mod & InputEvent.BUTTON2_MASK) != 0) {
                System.out.println("\tBUTTON2_MASK");
            }
            if ((mod & InputEvent.BUTTON3_MASK) != 0) {
                System.out.println("\tBUTTON3_MASK");
            }
            if ((mod & InputEvent.CTRL_MASK) != 0) {
                System.out.println("\tCTRL_MASK");
            }
            if ((mod & InputEvent.META_MASK) != 0) {
                System.out.println("\tMETA_MASK");
            }
            if ((mod & InputEvent.SHIFT_MASK) != 0) {
                System.out.println("\tSHIFT_MASK");
            }
        }
    }
}
