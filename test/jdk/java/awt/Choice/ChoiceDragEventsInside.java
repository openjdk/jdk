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
 * @bug 6251983 6722236
 * @summary MouseDragged events not triggered for Choice when dragging it with left mouse button
 * @key headful
 * @run main ChoiceDragEventsInside
 */

import java.awt.Choice;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.lang.reflect.InvocationTargetException;

public class ChoiceDragEventsInside extends Frame {
    Robot robot;
    Choice choice1;
    Point pt;
    Dimension size;
    volatile boolean mouseDragged = false;
    volatile boolean mouseDraggedOutside = false;

    public void setupUI() {
        setTitle("Choce Drag Events Inside");
        choice1 = new Choice();
        for (int i = 1; i < 50; i++) {
            choice1.add("item-0" + i);
        }
        choice1.setForeground(Color.red);
        choice1.setBackground(Color.red);
        choice1.addMouseMotionListener(new MouseMotionAdapter() {
                public void mouseMoved(MouseEvent me) {
                    System.out.println(me);
                }

                public void mouseDragged(MouseEvent me) {
                    System.out.println(me);
                    mouseDragged = true;
                    if (me.getY() < 0) {
                        mouseDraggedOutside = true;
                    }
                }
            }
        );
        add(choice1);
        setLayout(new FlowLayout());
        setSize(200, 200);
        setLocationRelativeTo(null);
        setVisible(true);
        validate();
    }

    public void start() {
        try {
            robot = new Robot();
            robot.setAutoWaitForIdle(true);
            robot.setAutoDelay(50);
            robot.delay(100);
            EventQueue.invokeAndWait(() -> {
                pt = choice1.getLocationOnScreen();
                size = choice1.getSize();
            });
            testDragInsideChoice(InputEvent.BUTTON1_MASK);
            testDragInsideChoiceList(InputEvent.BUTTON1_MASK);
            testDragOutsideChoice(InputEvent.BUTTON1_MASK);
        } catch (Throwable e) {
            throw new RuntimeException("Test failed. Exception thrown: " + e);
        }
    }

    public void testDragInsideChoice(int button) {
        robot.mouseMove(pt.x + size.width / 2, pt.y + size.height / 2);
        robot.delay(100);
        robot.mousePress(button);
        robot.mouseRelease(button);
        robot.delay(200);
        robot.mousePress(button);
        robot.mouseRelease(button);
        robot.delay(200);

        //close opened choice
        robot.keyPress(KeyEvent.VK_ESCAPE);
        robot.keyRelease(KeyEvent.VK_ESCAPE);
        robot.delay(200);

        robot.mouseMove(pt.x + size.width / 4, pt.y + size.height / 2);
        robot.mousePress(button);

        dragMouse(pt.x + size.width / 4, pt.y + size.height / 2,
                pt.x + size.width * 3 / 4, pt.y + size.height / 2);
        robot.mouseRelease(button);
        robot.delay(200);
        if (!mouseDragged) {
            throw new RuntimeException("Test failed. Choice should generate MouseDragged events inside Choice itself");
        } else {
            System.out.println("Stage 1 passed. Choice generates MouseDragged events inside Choice itself");
        }
        mouseDragged = false;
        //close opened choice
        robot.keyPress(KeyEvent.VK_ESCAPE);
        robot.keyRelease(KeyEvent.VK_ESCAPE);
        robot.delay(200);
    }

    public void testDragInsideChoiceList(int button) {
        robot.mouseMove(pt.x + size.width / 2, pt.y + size.height / 2);
        robot.delay(100);
        robot.mousePress(button);
        robot.mouseRelease(button);
        robot.delay(200);

        robot.mouseMove(pt.x + size.width / 2, pt.y + 5 * size.height);
        robot.delay(200);
        robot.mousePress(button);

        dragMouse(pt.x + size.width / 2, pt.y + 5 * size.height,
                pt.x + size.width / 2, pt.y + 8 * size.height);
        robot.mouseRelease(button);
        robot.delay(200);
        if (mouseDragged) {
            throw new RuntimeException("Test failed. Choice shouldn't generate MouseDragged events inside Choice's list");
        } else {
            System.out.println("Stage 2 passed. Choice doesn't generate MouseDragged events inside Choice's list");
        }
        robot.keyPress(KeyEvent.VK_ESCAPE);
        robot.keyRelease(KeyEvent.VK_ESCAPE);
        robot.delay(200);
        mouseDragged = false;
    }

    public void testDragOutsideChoice(int button) {
        pt = choice1.getLocationOnScreen();
        robot.mouseMove(pt.x + size.width / 2, pt.y + size.height / 2);
        robot.delay(100);

        robot.mousePress(button);
        //drag mouse outside of Choice
        dragMouse(pt.x + size.width / 2, pt.y + size.height / 2,
                pt.x + size.width / 2, pt.y - 3 * size.height);
        robot.mouseRelease(button);
        robot.delay(200);
        if (!mouseDragged || !mouseDraggedOutside) {
            throw new RuntimeException("Test failed. Choice should generate MouseDragged events outside Choice");
        } else {
            System.out.println("Stage 3 passed. Choice generates MouseDragged events outside Choice");
        }
        robot.keyPress(KeyEvent.VK_ESCAPE);
        robot.keyRelease(KeyEvent.VK_ESCAPE);
        robot.delay(200);
        mouseDragged = false;
    }

    public void dragMouse(int x0, int y0, int x1, int y1) {
        int curX = x0;
        int curY = y0;
        int dx = x0 < x1 ? 1 : -1;
        int dy = y0 < y1 ? 1 : -1;

        while (curX != x1) {
            curX += dx;
            robot.mouseMove(curX, curY);
        }
        while (curY != y1) {
            curY += dy;
            robot.mouseMove(curX, curY);
        }
    }

    public static void main(final String[] args) throws InterruptedException,
            InvocationTargetException {
        ChoiceDragEventsInside app = new ChoiceDragEventsInside();
        try {
            EventQueue.invokeAndWait(app::setupUI);
            app.start();
        } finally {
            EventQueue.invokeAndWait(app::dispose);
        }
    }
}
