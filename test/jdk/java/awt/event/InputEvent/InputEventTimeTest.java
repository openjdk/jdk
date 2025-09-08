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
 * @bug 4176525
 * @summary InputEvent.getWhen() returns the wrong event time.
 * @key headful
 * @run main InputEventTimeTest
 */

import java.awt.AWTEvent;
import java.awt.AWTException;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.Date;

public class InputEventTimeTest extends Frame {
    public void initUI() {
        setTitle("Input Event Time Test");
        enableEvents(AWTEvent.MOUSE_EVENT_MASK);
        enableEvents(AWTEvent.KEY_EVENT_MASK);
        setSize(200, 200);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    public void center(Point point) {
        Point loc = getLocationOnScreen();
        Dimension size = getSize();
        point.setLocation(loc.x + (size.width / 2), loc.y + (size.height / 2));
    }

    public void processEvent(AWTEvent e) {
        long currentTime;
        long eventTime;
        long difference;

        if (!(e instanceof InputEvent)) {
            return;
        }

        currentTime = (new Date()).getTime();
        eventTime = ((InputEvent) e).getWhen();
        difference = currentTime - eventTime;

        if ((difference > 5000) || (difference < -5000)) {
            throw new RuntimeException("The difference between current time" +
                    " and event creation time is " + difference + "ms");
        }
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException, AWTException {
        InputEventTimeTest test = new InputEventTimeTest();
        try {
            EventQueue.invokeAndWait(test::initUI);
            Robot robot = new Robot();
            robot.setAutoDelay(50);
            robot.waitForIdle();
            robot.delay(1000);
            Point center = new Point();
            EventQueue.invokeAndWait(() -> test.center(center));
            robot.mouseMove(center.x, center.y);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();
            robot.mousePress(InputEvent.BUTTON2_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON2_DOWN_MASK);
            robot.waitForIdle();
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();
            for (int i = 0; i < 6; i++) {
                robot.keyPress(KeyEvent.VK_A + i);
                robot.keyRelease(KeyEvent.VK_A + i);
                robot.waitForIdle();
            }
            for (int i = 0; i < 150; i += 5) {
                robot.mouseMove(center.x - i, center.y - i);
            }
            for (int i = 150; i > 0; i -= 5) {
                robot.mouseMove(center.x - i, center.y - i);
            }
        } finally {
            EventQueue.invokeAndWait(test::dispose);
        }
    }
}
