/*
 * Copyright (c) 2000, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4319246
 * @summary Tests that MouseReleased, MouseClicked and MouseDragged are triggered on choice
 * @key headful
 * @run main ChoiceMouseEventTest
 */

import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Choice;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.InvocationTargetException;


public class ChoiceMouseEventTest extends Frame {
    static volatile boolean mousePressed = false;
    static volatile boolean mouseReleased = false;
    static volatile boolean mouseClicked = false;
    Choice choice = new Choice();
    static Point location;
    static Dimension size;

    public void setupGUI() {
        setTitle("Choice Mouse Event Test");
        this.setLayout(new BorderLayout());
        choice.add("item-1");
        choice.add("item-2");
        choice.add("item-3");
        choice.add("item-4");
        add("Center", choice);
        choice.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                mouseClicked = true;
            }

            @Override
            public void mousePressed(MouseEvent e) {
                mousePressed = true;
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                mouseReleased = true;
            }
        });
        setLocationRelativeTo(null);
        setSize(400, 200);
        setVisible(true);
    }

    public Point _location() {
        return choice.getLocationOnScreen();
    }

    public Dimension _size() {
        return choice.getSize();
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException, AWTException {
        ChoiceMouseEventTest test = new ChoiceMouseEventTest();
        try {
            EventQueue.invokeAndWait(test::setupGUI);
            Robot robot = new Robot();
            robot.setAutoDelay(50);
            robot.delay(1000);
            robot.waitForIdle();
            EventQueue.invokeAndWait(() -> {
                location = test._location();
                size = test._size();
            });
            robot.waitForIdle();
            robot.mouseMove(location.x + size.width - 10, location.y + (size.height / 2));
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.delay(2000);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.delay(2000);
            robot.waitForIdle();
            if (!mouseClicked || !mousePressed || !mouseReleased) {
                throw new RuntimeException(String.format("One of the events not arrived: " +
                        "mouseClicked = %b, mousePressed = %b, mouseReleased = %b",
                        mouseClicked, mousePressed, mouseReleased));
            }
        } finally {
            if (test != null) {
                EventQueue.invokeAndWait(test::dispose);
            }
        }
    }
}

