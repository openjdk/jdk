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
  @bug 6272965
  @summary PIT: Choice triggers MousePressed when pressing mouse outside comp while drop-down is active, XTkt
  @key headful
*/

import java.awt.Choice;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class ChoiceMouseEventOutbounds {

    static final int DELAY = 100;
    static volatile Choice choice1;
    static volatile Frame frame;
    static volatile Robot robot;
    static volatile boolean mousePressed = false;
    static volatile boolean mouseReleased = false;
    static volatile boolean mouseClicked = false;

    public static void main(String[] args) throws Exception {
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
            choice1.add("item "+i);
        }

        choice1.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent me) {
                mousePressed = true;
                System.out.println(me);
            }
            public void mouseReleased(MouseEvent me) {
                mouseReleased = true;
                System.out.println(me);
            }
            public void mouseClicked(MouseEvent me) {
                mouseClicked = true;
                System.out.println(me);
            }
        });

        frame = new Frame("ChoiceMouseEventOutbounds");
        frame.add(choice1);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        frame.validate();
    }

    static void runTest() throws Exception {
        // On Windows, Choice will not close its pop-down menu on a RIGHT
        // MousePress outside of the Choice. So this scenario isn't
        // tested here for that reason.

        robot = new Robot();
        robot.setAutoWaitForIdle(true);
        robot.setAutoDelay(50);
        robot.delay(DELAY*10);
        testMouseClick(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(DELAY);
        testMouseClick(InputEvent.BUTTON2_DOWN_MASK);
        robot.delay(DELAY);
        testMouseClick(InputEvent.BUTTON3_DOWN_MASK);

        System.out.println("Test passed: Choice doesn't generate MOUSEPRESS/CLICK/RELEASE outside Choice.");
    }

    static void testMouseClick(int button) {
        Point pt = choice1.getLocationOnScreen();
        robot.mouseMove(pt.x + choice1.getWidth()/2, pt.y + choice1.getHeight()/2);
        robot.delay(DELAY);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(DELAY);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(DELAY*3);

        //they are true because we just pressed mouse
        mousePressed = false;
        mouseReleased = false;
        mouseClicked = false;

        //move mouse outside Choice
        robot.mouseMove(pt.x + choice1.getWidth()/2, pt.y - choice1.getHeight());
        robot.delay(DELAY*3);
        robot.mousePress(button);
        robot.delay(DELAY);
        robot.mouseRelease(button);

        if (mousePressed || mouseReleased || mouseClicked) {
            System.out.println("ERROR: "+ mousePressed+","+mouseReleased +","+mouseClicked);
            throw new RuntimeException(
               "Test failed. Choice shouldn't generate PRESSED, RELEASED, CLICKED events outside "+ button);
        } else {
            System.out.println(
               "Test passed. Choice didn't generated MouseDragged PRESSED, RELEASED, CLICKED events outside "+ button);
        }
        robot.keyPress(KeyEvent.VK_ESCAPE);
        robot.keyRelease(KeyEvent.VK_ESCAPE);
        robot.delay(DELAY);
        mousePressed = false;
        mouseReleased = false;
        mouseClicked = false;
    }
}
