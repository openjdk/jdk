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
  @bug 6239938
  @summary Choice drop-down does not disappear when it loses focus, on XToolkit
  @key headful
  @requires (os.family == "linux")
*/

import java.awt.Button;
import java.awt.Choice;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public class ChoiceStaysOpenedOnTAB {

    static volatile Robot robot;
    static volatile Choice choice1;
    static volatile Frame frame;

    public static void main(String[] args) throws Exception {

        if (!System.getProperty("os.name").toLowerCase().startsWith("linux")) {
            System.out.println("This test is for Linux only");
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
        for (int i = 1; i<10; i++) {
            choice1.add("item-0"+i);
        }
        choice1.setForeground(Color.red);
        choice1.setBackground(Color.red);
        Button b1 = new Button("FirstButton");
        Button b2 = new Button("SecondButton");
        frame = new Frame("ChoiceStaysOpenedOnTAB");
        Panel panel = new Panel();
        panel.add(b1);
        panel.add(choice1);
        panel.add(b2);
        frame.add(panel);
        frame.setSize(400,400);
        frame.setLocationRelativeTo(null);
        frame.validate();
        frame.setVisible(true);
    }

    static void runTest() throws Exception {

        /*
         * Choice should not lose focus while it is opened with
         * TAB/Shitf-TAB KeyPress on XAWT.
         * Should only pass on XAWT.
         */
        robot = new Robot();
        robot.setAutoWaitForIdle(true);
        robot.setAutoDelay(50);
        robot.delay(1000);

        testTABKeyPress(InputEvent.BUTTON1_DOWN_MASK, KeyEvent.VK_TAB, false);
        testTABKeyPress(InputEvent.BUTTON1_DOWN_MASK, KeyEvent.VK_TAB, true);
        System.out.println("Passed : Choice should not lose focus on TAB key press while it is opened.");
    }

    static void testTABKeyPress(int openButton, int keyButton, boolean isShiftUsed) {
        Point pt = choice1.getLocationOnScreen();
        robot.mouseMove(pt.x + choice1.getWidth()/2, pt.y + choice1.getHeight()/2);
        robot.delay(100);
        robot.mousePress(openButton);
        robot.mouseRelease(openButton);
        robot.delay(200);

        Color color = robot.getPixelColor(pt.x + choice1.getWidth()/2,
                                          pt.y + 3 * choice1.getHeight());
        if (!color.equals(Color.red)) {
            throw new RuntimeException(
                "Choice wasn't opened with LEFTMOUSE button" + openButton +":"+keyButton+":"+isShiftUsed);
        }
        robot.delay(200);
        if (isShiftUsed) {
            robot.keyPress(KeyEvent.VK_SHIFT);
        }
        robot.keyPress(keyButton);
        robot.keyRelease(keyButton);

        if (isShiftUsed) {
            robot.keyRelease(KeyEvent.VK_SHIFT);
        }

        robot.delay(200);
        if (!choice1.isFocusOwner()) {
            System.out.println("Choice has focus=="+choice1.isFocusOwner());
            throw new RuntimeException(
                "Choice has no focus after pressing TAB/Shitf+TAB" + openButton +":"+keyButton+":"+isShiftUsed);
        }
        int px = pt.x + choice1.getWidth()/2;
        int py = pt.y + 3 * choice1.getHeight();
        color = robot.getPixelColor(px, py);
        //we should take a color on the point on the choice's menu
        System.out.println("color got "+color);
        if (!color.equals(Color.red)) {
            throw new RuntimeException(
                "Choice closed after TAB/Shift+TAB key press" + openButton +":"+keyButton+":"+isShiftUsed);
        }

        //close opened choice
        robot.keyPress(KeyEvent.VK_ESCAPE);
        robot.keyRelease(KeyEvent.VK_ESCAPE);
        robot.delay(200);
    }
}
