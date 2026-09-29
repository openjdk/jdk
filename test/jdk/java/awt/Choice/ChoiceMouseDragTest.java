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


import java.awt.BorderLayout;
import java.awt.Choice;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/*
 * @test
 * @bug 4328557
 * @summary Tests that MouseDragged and MouseReleased are triggered on choice
 * @library /lib/client
 * @build ExtendedRobot
 * @key headful
 * @run main ChoiceMouseDragTest
 */


public class ChoiceMouseDragTest extends Frame {
    private static final Choice choice = new Choice();

    private static ExtendedRobot robot;
    private volatile boolean isDragged;
    private volatile boolean isReleased;

    private static volatile ChoiceMouseDragTest choiceMouseDragTest;

    public ChoiceMouseDragTest() {
        super("ChoiceMouseDragTest");
        this.setLayout(new BorderLayout());
        choice.add("item-1");
        choice.add("item-2");
        choice.add("item-3");
        choice.add("item-4");
        add("Center", choice);
        choice.addMouseListener(new MouseEventHandler());
        choice.addMouseMotionListener(new MouseMotionEventHandler());
        setSize(400, 200);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    public static void main(String[] args) throws Exception {
        try {
            EventQueue.invokeAndWait(() ->
                    choiceMouseDragTest = new ChoiceMouseDragTest());

            robot = new ExtendedRobot();
            robot.waitForIdle();
            robot.delay(500);

            Point pointToDrag = choice.getLocationOnScreen();
            pointToDrag.x += choice.getWidth() - 10;
            pointToDrag.y += choice.getHeight() / 2 ;

            choiceMouseDragTest.test(InputEvent.BUTTON3_DOWN_MASK, pointToDrag);
            choiceMouseDragTest.test(InputEvent.BUTTON1_DOWN_MASK, pointToDrag);
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (choiceMouseDragTest != null) {
                    choiceMouseDragTest.dispose();
                }
            });
        }
    }

    void test(int buttonToTest, Point pointToDrag) {
        isDragged = false;
        isReleased = false;

        robot.mouseMove(pointToDrag.x, pointToDrag.y);
        robot.waitForIdle();

        robot.mousePress(buttonToTest);

        robot.glide(pointToDrag.x + 100, pointToDrag.y);
        robot.waitForIdle();

        robot.mouseRelease(buttonToTest);
        robot.waitForIdle();

        if (!isReleased || !isDragged) {
            throw new RuntimeException(("Test failed: button %d dragged(received %b) or " +
                    "released(received %b)")
                    .formatted(buttonToTest, isDragged, isReleased));
        }

        robot.delay(500);
    }

    class MouseEventHandler extends MouseAdapter {
        public void mousePressed(MouseEvent me) {
            System.out.println(me.paramString());
        }

        public void mouseReleased(MouseEvent me) {
            System.out.println(me.paramString());
            isReleased = true;
        }

        public void mouseClicked(MouseEvent me) {
            System.out.println(me.paramString());
        }
    }

    class MouseMotionEventHandler extends MouseAdapter {
        public void mouseDragged(MouseEvent me) {
            System.out.println(me.paramString());
            isDragged = true;
        }
    }
}
