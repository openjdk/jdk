/*
 * Copyright (c) 2007, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6519005
 * @summary regression: Selection the item on the choice don't work properly in vista ultimate.
 * @key headful
 * @run main NonFocusablePopupMenuTest
 */

import java.awt.AWTException;
import java.awt.Choice;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.lang.reflect.InvocationTargetException;

public class NonFocusablePopupMenuTest extends Frame {
    Choice choice;
    volatile Point pos;
    volatile Dimension size;
    volatile int selection1, selection2;

    public void performTest() throws AWTException,
            InterruptedException, InvocationTargetException {
        Robot robot = new Robot();
        robot.setAutoDelay(100);
        EventQueue.invokeAndWait(() -> {
            choice = new Choice();
            choice.add("111");
            choice.add("222");
            choice.add("333");
            choice.add("444");
            choice.setFocusable(false);
            this.add(choice);
            this.setLayout(new FlowLayout());
            setSize (200, 200);
            setLocationRelativeTo(null);
            setVisible(true);
        });
        robot.waitForIdle();
        EventQueue.invokeAndWait(() -> {
            pos = choice.getLocationOnScreen();
            size = choice.getSize();
            selection1 = choice.getSelectedIndex();
        });
        robot.mouseMove(pos.x + size.width / 2, pos.y + size.height / 2);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
        robot.delay(500);
        robot.mouseMove(pos.x + size.width / 2, pos.y + size.height * 2);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
        EventQueue.invokeAndWait(() -> {
            selection2 = choice.getSelectedIndex();
            setVisible(false);
            dispose();
        });
        if (selection1 == selection2) {
            throw new RuntimeException("Can not change choice selection with the mouse click");
        }
    }

    public static void main(String[] args) throws AWTException,
            InterruptedException, InvocationTargetException {
        NonFocusablePopupMenuTest me = new NonFocusablePopupMenuTest();
        me.performTest();
    }
}