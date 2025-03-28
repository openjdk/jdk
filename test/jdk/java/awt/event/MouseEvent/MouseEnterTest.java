/*
 * Copyright (c) 2002, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4095172
 * @summary Test for no proper mouse coordinates on MOUSE_ENTER/MOUSE_EXIT events for Win boxes.
 * @key headful
 * @library /lib/client /java/awt/regtesthelpers
 * @build ExtendedRobot Util
 * @run main MouseEnterTest
 */

import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;

import test.java.awt.regtesthelpers.Util;

public class MouseEnterTest {
    private static Frame frame;
    private static final TestMouseAdapter mouseAdapter = new TestMouseAdapter();

    public static void main(String[] args) throws Exception {
        EventQueue.invokeAndWait(MouseEnterTest::initAndShowGUI);
        try {
            test();
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static void initAndShowGUI() {
        frame = new Frame("MouseEnterTest");
        frame.setLayout(null);
        frame.setSize(300, 200);
        frame.setLocationRelativeTo(null);
        frame.addMouseListener(mouseAdapter);
        frame.setVisible(true);
    }

    private static void test() throws Exception {
        ExtendedRobot robot = new ExtendedRobot();
        robot.waitForIdle();
        robot.delay(500);

        Rectangle bounds = Util.invokeOnEDT(frame::getBounds);

        java.util.List<Point> points = getBorderGlidePoints(bounds);
        for (int i = 0; i < points.size(); i += 2) {
            Point p1 = points.get(i);
            Point p2 = points.get(i + 1);

            System.out.println("\n------------------\n");

            System.out.printf("%s > %s > %s\n", p1, p2, p1);
            robot.glide(p1, p2);
            robot.waitForIdle();
            robot.glide(p2, p1);
            robot.waitForIdle();
            robot.delay(200);
            mouseAdapter.testEvents();

            System.out.println("\n------------------\n");

            System.out.printf("%s > %s > %s\n", p2, p1, p2);
            robot.glide(p2, p1);
            robot.waitForIdle();
            robot.glide(p1, p2);
            robot.waitForIdle();
            robot.delay(200);
            mouseAdapter.testEvents();
        }
    }

    private static java.util.List<Point> getBorderGlidePoints(Rectangle bounds) {
        java.util.List<Point> list = new ArrayList<>();

        int d = 10;

        // left
        list.add(new Point(bounds.x - d, bounds.y + bounds.height / 2));
        list.add(new Point(bounds.x + d, bounds.y + bounds.height / 2));

        // right
        list.add(new Point(bounds.x + bounds.width - d, bounds.y + bounds.height / 2));
        list.add(new Point(bounds.x + bounds.width + d, bounds.y + bounds.height / 2));

        // top
        list.add(new Point(bounds.x + bounds.width / 2, bounds.y - d));
        list.add(new Point(bounds.x + bounds.width / 2, bounds.y + d));

        // bottom
        list.add(new Point(bounds.x + bounds.width / 2, bounds.y + bounds.height - d));
        list.add(new Point(bounds.x + bounds.width / 2, bounds.y + bounds.height + d));

        return list;
    }

    private static final class TestMouseAdapter extends MouseAdapter {
        private static final int THRESHOLD = 5;
        private volatile MouseEvent lastEnteredEvent = null;
        private volatile MouseEvent lastExitedEvent = null;

        @Override
        public void mouseEntered(MouseEvent e) {
            System.out.println("MouseEntered " + e);
            lastEnteredEvent = e;
        }

        @Override
        public void mouseExited(MouseEvent e) {
            System.out.println("MouseExited " + e);
            lastExitedEvent = e;
        }

        public void testEvents() {
            if (lastEnteredEvent == null || lastExitedEvent == null) {
                throw new RuntimeException("Missing lastEnteredEvent or lastExitedEvent");
            }

            System.out.println("\nTesting:");
            System.out.println(lastEnteredEvent);
            System.out.println(lastExitedEvent);
            System.out.println();

            int diffX = Math.abs(lastEnteredEvent.getX() - lastExitedEvent.getX());
            int diffScreenX = Math.abs(lastEnteredEvent.getY() - lastExitedEvent.getY());
            int diffY = Math.abs(lastEnteredEvent.getXOnScreen() - lastExitedEvent.getXOnScreen());
            int diffScreenY = Math.abs(lastEnteredEvent.getYOnScreen() - lastExitedEvent.getYOnScreen());

            System.out.printf("THRESHOLD %d, diffX %d diffScreenX %d " +
                            "diffY %d diffScreenY %d\n",
                    THRESHOLD,
                    diffX, diffScreenX,
                    diffY, diffScreenY
            );

            if (diffX > THRESHOLD
                || diffScreenX > THRESHOLD
                || diffY > THRESHOLD
                || diffScreenY > THRESHOLD) {
                throw new RuntimeException("Mouse enter vs exit event is too different");
            }
        }
    }
}
