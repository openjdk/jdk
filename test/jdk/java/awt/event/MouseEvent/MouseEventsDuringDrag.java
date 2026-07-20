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
 * @bug 4017222
 * @summary Checks whether mouse events are reported correctly during drag.
 * @author Stuart Lawrence, Brent Christian: area=event
 * @key headful
 * @library /lib/client /java/awt/regtesthelpers
 * @build ExtendedRobot Util
 * @run main MouseEventsDuringDrag
 */


/*
 * MouseEventsDuringDrag.java
 *
 * summary:
 * On Solaris drag enter/exit events are only reported for the
 * component where drag started, they're not reported on other
 * components. On Win32 enter/exit events are reported correctly.
 */

import test.java.awt.regtesthelpers.Util;

import java.awt.Canvas;
import java.awt.Choice;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Label;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class MouseEventsDuringDrag {

    private static ExtendedRobot robot;
    private static Frame frame;

    private static final MouseHandler mouseHandler = new MouseHandler();

    static Label lab;
    static Canvas c1, c2;
    static Choice choice;


    public static void main(String[] args) throws Exception {
        try {
            EventQueue.invokeAndWait(MouseEventsDuringDrag::createAndShowGUI);
            test();
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static void test() throws Exception {
        robot = new ExtendedRobot();
        robot.waitForIdle();
        robot.delay(500);

        // Part 1: Press and hold down the mouse button inside the red box.
        // Drag the mouse over to the blue box (whilst still holding down
        // the mouse button).
        // Whilst dragging the mouse from the red box, enter and exit
        // events should be reported for the blue box.
        testcase(c2, "c1 to c2");

        // Part 2: Again, press and hold down the mouse button inside the red box.
        // This time drag the mouse over to the Choice menu.
        // Enter and exit events should be reported for the Choice menu.
        testcase(choice, "c1 to choice");
    }

    private static void testcase(Component moveTo, String message) throws Exception {
        System.out.println("\ntestcase: " + message);
        Rectangle c1bounds = getBounds(c1);
        Rectangle moveToBound = getBounds(moveTo);

        Point startDragLocation =
                new Point(c1bounds.x + c1bounds.width - 10,
                        c1bounds.y + c1bounds.height / 2);

        Point endDragLocation =
                new Point(moveToBound.x + 10, moveToBound.y + moveToBound.height / 2);

        robot.mouseMove(startDragLocation);
        robot.waitForIdle();
        robot.delay(200);
        mouseHandler.reset();

        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();

        robot.glide(startDragLocation, endDragLocation);
        robot.waitForIdle();

        robot.glide(endDragLocation.x, endDragLocation.y, endDragLocation.x - 20, endDragLocation.y);

        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

        robot.waitForIdle();
        robot.delay(200);

        List<EventRecord> actual = mouseHandler.getRecordedEvents();

        List<EventRecord> expected = List.of(
                new EventRecord(MouseEvent.MOUSE_PRESSED, c1),
                new EventRecord(MouseEvent.MOUSE_EXITED, c1),
                new EventRecord(MouseEvent.MOUSE_ENTERED, moveTo),
                new EventRecord(MouseEvent.MOUSE_EXITED, moveTo),
                new EventRecord(MouseEvent.MOUSE_RELEASED, c1)
        );

        System.out.println("Expected:\n" + expected);
        System.out.println("Actual:\n" + actual);
        if (!actual.equals(expected)) {
            throw new RuntimeException("Mismatch between expected and actual events\n%s\n%s"
                    .formatted(expected, actual));
        }
    }

    private static Rectangle getBounds(Component c) throws Exception {
        return Util.invokeOnEDT(() -> {
            Point locationOnScreen = c.getLocationOnScreen();
            Dimension size = c.getSize();
            return new Rectangle(locationOnScreen.x, locationOnScreen.y, size.width, size.height);
        });
    }

    private static void createAndShowGUI() {
        frame = new Frame();
        MouseHandler mouseHandler = new MouseHandler();
        frame.setLayout(new GridBagLayout());

        int canvasSize = 100;
        c1 = new Canvas();
        c1.setPreferredSize(new Dimension(canvasSize, canvasSize));
        c1.setBackground(Color.red);
        c1.addMouseListener(mouseHandler);

        c2 = new Canvas();
        c2.setPreferredSize(new Dimension(canvasSize, canvasSize));
        c2.setBackground(Color.blue);
        c2.addMouseListener(mouseHandler);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);

        gbc.gridx = 0;
        gbc.gridy = 0;
        frame.add(c1, gbc);

        gbc.gridx = 2;
        frame.add(c2, gbc);

        Panel p1 = new Panel();
        p1.setLayout(new FlowLayout());
        choice = new Choice();
        choice.addItem("Choice");
        choice.addItem("One");
        choice.addItem("Two");
        choice.addMouseListener(mouseHandler);
        p1.add(choice);

        gbc.gridx = 1;
        gbc.gridy = 1;
        frame.add(p1, gbc);

        lab = new Label();

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 3;
        frame.add(lab, gbc);

        frame.pack();
        frame.setLocationRelativeTo(null);

        frame.setVisible(true);
    }

    record EventRecord(int eventId, Component component) {

        @Override
        public String toString() {
            StringBuilder str = new StringBuilder(80);
            switch(eventId) {
                case MouseEvent.MOUSE_PRESSED:
                    str.append("MOUSE_PRESSED");
                    break;
                case MouseEvent.MOUSE_RELEASED:
                    str.append("MOUSE_RELEASED");
                    break;
                case MouseEvent.MOUSE_CLICKED:
                    str.append("MOUSE_CLICKED");
                    break;
                case MouseEvent.MOUSE_ENTERED:
                    str.append("MOUSE_ENTERED");
                    break;
                case MouseEvent.MOUSE_EXITED:
                    str.append("MOUSE_EXITED");
                    break;
                case MouseEvent.MOUSE_MOVED:
                    str.append("MOUSE_MOVED");
                    break;
                case MouseEvent.MOUSE_DRAGGED:
                    str.append("MOUSE_DRAGGED");
                    break;
                case MouseEvent.MOUSE_WHEEL:
                    str.append("MOUSE_WHEEL");
                    break;
                default:
                    str.append("unknown type");
            }
            return str.append(" ").append(component).toString();
        }
    }

    static class MouseHandler extends MouseAdapter {
        static final List<EventRecord> list = new CopyOnWriteArrayList<>();

        public void mousePressed(MouseEvent e) {
            list.add(new EventRecord(e.getID(), e.getComponent()));
            if (e.getSource() == c1) {
                lab.setText("Mouse pressed in red box");
            } else if (e.getSource() == c2) {
                lab.setText("Mouse pressed in blue box");
            } else if (e.getSource() == choice) {
                lab.setText("Mouse pressed in choice");
            }
        }

        public void mouseReleased(MouseEvent e) {
            list.add(new EventRecord(e.getID(), e.getComponent()));
            lab.setText("Mouse released");
        }

        public void mouseEntered(MouseEvent e) {
            list.add(new EventRecord(e.getID(), e.getComponent()));
            if (e.getSource() == c1) {
                lab.setText("Mouse entered red box");
            } else if (e.getSource() == c2) {
                lab.setText("Mouse entered blue box");
            } else if (e.getSource() == choice) {
                lab.setText("Mouse entered choice");
            }
        }

        public void mouseExited(MouseEvent e) {
            list.add(new EventRecord(e.getID(), e.getComponent()));
            if (e.getSource() == c1) {
                lab.setText("Mouse exited red box");
            } else if (e.getSource() == c2) {
                lab.setText("Mouse exited blue box");
            } else if (e.getSource() == choice) {
                lab.setText("Mouse exited choice");
            }
        }

        public void reset() {
            list.clear();
        }

        public List<EventRecord> getRecordedEvents() {
            return new ArrayList<>(list);
        }
    }
}
