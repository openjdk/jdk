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
  @bug 6251988
  @summary PIT: Choice consumes MouseReleased, MouseClicked events when clicking it with left button,
  @key headful
*/

import java.awt.Choice;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public class ChoiceConsumeMouseEvents {

    static volatile Frame frame;
    static volatile Robot robot;
    static volatile Choice choice1 = new Choice();
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
        for (int i = 1; i<10; i++){
            choice1.add("item-0"+i);
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

        frame = new Frame("ChoiceConsumeMouseEvents");
        frame.add(choice1);
        frame.setSize(400, 400);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        frame.validate();
    }

    static void runTest() {
        try {
            robot = new Robot();
            robot.setAutoWaitForIdle(true);
            robot.setAutoDelay(50);
            robot.delay(100);
            testMouseClick(InputEvent.BUTTON1_DOWN_MASK, 0);
            robot.delay(100);
            testMouseClick(InputEvent.BUTTON1_DOWN_MASK, 100);
        } catch (Throwable e) {
            throw new RuntimeException("Test failed. Exception thrown: "+e);
        }
    }

    static void testMouseClick(int button, int delay) {
        Point pt = choice1.getLocationOnScreen();
        robot.mouseMove(pt.x + choice1.getWidth()/2, pt.y + choice1.getHeight()/2);
        robot.delay(100);
        robot.mousePress(button);
        robot.delay(delay);
        robot.mouseRelease(button);
        robot.delay(200);
        if (!(mousePressed &&
              mouseReleased &&
              mouseClicked))
        {
            throw new RuntimeException("Test failed. Choice should generate PRESSED, RELEASED, CLICKED events");
        } else {
            System.out.println("Test passed. Choice generated MouseDragged PRESSED, RELEASED, CLICKED events");
        }
        robot.keyPress(KeyEvent.VK_ESCAPE);
        robot.keyRelease(KeyEvent.VK_ESCAPE);
        robot.delay(200);
        mousePressed = false;
        mouseReleased = false;
        mouseClicked = false;
    }
}
