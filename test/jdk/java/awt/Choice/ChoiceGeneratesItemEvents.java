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
  @bug 6239941
  @summary Choice triggers ItemEvent when selecting an item with right mouse button, Xtoolkit
  @key headful
  @requires (os.family == "linux")
*/

import java.awt.Choice;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;

public class ChoiceGeneratesItemEvents implements ItemListener {

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

    static volatile Frame frame;
    static volatile Robot robot;
    static volatile Choice choice1;
    static volatile boolean passed = true;

    static void createUI() {
        choice1 = new Choice();
        for (int i = 1; i<10; i++){
            choice1.add("item-0"+i);
        }
        choice1.setForeground(Color.red);
        choice1.setBackground(Color.red);
        choice1.addItemListener(new ChoiceGeneratesItemEvents());
        frame = new Frame("ChoiceGeneratesItemEvents");
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
        robot.delay(100);
        testMousePressOnChoice(InputEvent.BUTTON2_DOWN_MASK);
        testMousePressOnChoice(InputEvent.BUTTON3_DOWN_MASK);
        if (!passed) {
            throw new RuntimeException("Test failed.");
        } else {
            System.out.println("Test passed. ");
        }
    }

    static void testMousePressOnChoice(int button) {
        Point pt = choice1.getLocationOnScreen();
        robot.mouseMove(pt.x + choice1.getWidth()/2, pt.y + choice1.getHeight()/2);
        robot.delay(100);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(2000);

        int px = pt.x + choice1.getWidth()/2;
        int py = pt.y + 2 * choice1.getHeight();
        Color color = robot.getPixelColor(px, py);
        //we should take a color on the point on the choice's menu
        System.out.println("Got color " + color + " at (" + px + "," + py + ")");
        if (!color.equals(Color.red)) {
            throw new RuntimeException("Test failed. Choice wasn't open with LEFTMOUSE button." +button);
        }
        robot.mouseMove(pt.x + choice1.getWidth()/2,
                        pt.y + 5*choice1.getHeight());
        robot.delay(200);
        robot.mousePress(button);
        robot.mouseRelease(button);
        robot.delay(200);

        //close opened choice
        robot.keyPress(KeyEvent.VK_ESCAPE);
        robot.keyRelease(KeyEvent.VK_ESCAPE);
        robot.delay(200);
    }

    public void itemStateChanged(ItemEvent ie) {
        System.err.println("Opened Choice generated ItemEvent on RIGHT/MIDDLE mouse press." +ie);
        passed = false;
    }
}
