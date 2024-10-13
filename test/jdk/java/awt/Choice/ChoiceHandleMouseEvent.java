/*
 * Copyright (c) 2005, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @bug 5003166
  @summary REG:Mouse button not validated before bringing up the drop-down menu for choice
  @key headful
  @requires (os.family == "linux" | os.family == "windows")
*/

import java.awt.Choice;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public class ChoiceHandleMouseEvent {
    static Robot robot;
    static volatile Choice choice1;
    static volatile Frame frame;

    public static void main(String[] args) throws Exception {
        String os = System.getProperty("os.name").toLowerCase();
        if (!os.startsWith("windows") && !os.startsWith("linux")) {
            System.out.println("This test is only for Windows and Linux");
            return;
        }
        try {
            EventQueue.invokeAndWait(() -> createUI());
            runTest();
        } finally {
            if (frame != null) {
                EventQueue.invokeAndWait(() -> frame.dispose());
            }
       }
    }

    static void createUI() {
        choice1 = new Choice();
        choice1.add("item-01");
        choice1.add("item-02");
        choice1.add("item-03");
        choice1.add("item-04");
        choice1.setForeground(Color.red);
        choice1.setBackground(Color.red);
        frame = new Frame("ChoiceHandleMouseEvent");
        frame.add(choice1);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.validate();
        frame.setVisible(true);
    }

    static void runTest() throws Exception {
        robot = new Robot();
        robot.setAutoWaitForIdle(true);
        robot.setAutoDelay(50);

        /*
         * Stage 1: Choice should only opens with LEFTMOUSE click.
         * Should only pass on Windows or XAWT.
         * Choice on motif might be opened only by click on small box
         * in the right side.
         */
        testPressMouseButton(InputEvent.BUTTON2_DOWN_MASK);
        testPressMouseButton(InputEvent.BUTTON3_DOWN_MASK);
        System.out.println("Passed Stage 1: Choice should only opens with LEFT BUTTON.");

        /*
         * Stage 2: Choice should only change its value if pressed
         * mouse button is LEFTMOUSE.
         */
        // first parameter is for opening choice. The second is for
        // selecting item inside the menu
        testPressMouseButton_2(InputEvent.BUTTON1_DOWN_MASK, InputEvent.BUTTON2_DOWN_MASK);
        testPressMouseButton_2(InputEvent.BUTTON1_DOWN_MASK, InputEvent.BUTTON3_DOWN_MASK);
        System.out.println("Passed Stage 2: Choice should not change its value if pressed mouse buttonis  not left.");

        /*
         * Stage 3: Choice should only react on drags with LEFTMOUSE button.
         */
        // first parameter is for opening choice. The second is for
        // selecting item inside the menu
        testDragMouseButton(InputEvent.BUTTON1_DOWN_MASK, InputEvent.BUTTON2_DOWN_MASK);
        testDragMouseButton(InputEvent.BUTTON1_DOWN_MASK, InputEvent.BUTTON3_DOWN_MASK);
        System.out.println("Passed Stage 3: Choice should only react on drags with LEFTMOUSE button.");
    }

    static void testPressMouseButton(int button) {
        Point pt = choice1.getLocationOnScreen();
        robot.mouseMove(pt.x + choice1.getWidth()/2, pt.y + choice1.getHeight()/2);
        robot.delay(100);
        robot.mousePress(button);
        robot.mouseRelease(button);
        robot.delay(200);

        int px = pt.x + choice1.getWidth()/2;
        int py = pt.y + 3 * choice1.getHeight();
        Color color = robot.getPixelColor(px, py);
        //we should take a color on the point on the choice's menu
        System.out.println("Got color " + color + " at (" + px + "," + py + ")");
        System.out.println("RED="+Color.red);
        if (color.equals(Color.red)) {
            throw new RuntimeException("Test failed. Choice opens with "+button);
        } else {
            System.out.println("Stage 1 passed."+ button);
        }

        //close opened choice
        robot.keyPress(KeyEvent.VK_ESCAPE);
        robot.keyRelease(KeyEvent.VK_ESCAPE);
    }

    static void testPressMouseButton_2(int openButton, int button) {
        Point pt = choice1.getLocationOnScreen();
        robot.mouseMove(pt.x + choice1.getWidth()/2,
                        pt.y + choice1.getHeight()/2);
        robot.delay(100);
        robot.mousePress(openButton);
        robot.mouseRelease(openButton);
        robot.delay(200);
        robot.mouseMove(pt.x + choice1.getWidth()/2,
                        pt.y + 2 * choice1.getHeight());
        robot.mousePress(button);
        robot.mouseRelease(button);

        System.out.println();

        if (choice1.getSelectedIndex() == 0) {
            System.out.println("Stage 2 passed." + openButton +":"+button);
        } else {
            throw new RuntimeException("Stage 2 failed." + openButton +":"+button);
        }

        //close opened choice
        robot.keyPress(KeyEvent.VK_ESCAPE);
        robot.keyRelease(KeyEvent.VK_ESCAPE);
    }

    static void testDragMouseButton(int openButton, int button) {
        Point pt = choice1.getLocationOnScreen();
        robot.mouseMove(pt.x + choice1.getWidth()/2, pt.y + choice1.getHeight()/2);
        robot.delay(100);
        robot.mousePress(openButton);
        robot.mouseRelease(openButton);
        robot.delay(200);

        robot.mousePress(button);
        dragMouse(pt.x + choice1.getWidth()/2, pt.y +
                  choice1.getHeight()/2,
                  pt.x + choice1.getWidth()/2,
                  pt.y + 2 * choice1.getHeight());
        robot.mouseRelease(button);

        if (choice1.getSelectedIndex() == 0 ){
            System.out.println("Stage 3 passed." + openButton +":"+button);
            //            System.out.println("choice1.getSelectedIndex()" + choice1.getSelectedIndex());
        } else {
            throw new RuntimeException("Stage 3 failed." + openButton +":"+button);
        }

        //close opened choice
        robot.keyPress(KeyEvent.VK_ESCAPE);
        robot.keyRelease(KeyEvent.VK_ESCAPE);
    }

    static void dragMouse(int x0, int y0, int x1, int y1) {
        int curX = x0;
        int curY = y0;
        int dx = x0 < x1 ? 1 : -1;
        int dy = y0 < y1 ? 1 : -1;

        while (curX != x1){
            curX += dx;
            robot.mouseMove(curX, curY);
        }
        while (curY != y1 ){
            curY += dy;
            robot.mouseMove(curX, curY);
        }
    }
}
