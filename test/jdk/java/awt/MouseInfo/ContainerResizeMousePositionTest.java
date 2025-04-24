/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Button;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/*
 * @test
 * @key headful
 * @bug 4009555
 * @summary Unit test for a new method in Container class: getMousePosition(boolean)
 *          while Container resized.
 */

public class ContainerResizeMousePositionTest {
    private static Frame frame;
    private static Button button;
    private static Robot robot;
    private static volatile Point frameLocation;
    private static volatile Point newLoc;
    private static boolean testSucceeded = false;

    private static final CountDownLatch eventCaught = new CountDownLatch(1);

    public static void main(String[] args) throws Exception {
        try {
            robot = new Robot();
            EventQueue.invokeAndWait(() -> createAndShowUI());
            robot.waitForIdle();
            robot.delay(1000);
            testUI();
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static void createAndShowUI() {
        frame = new Frame("Testing getMousePosition() after resize");
        button = new Button("Button");
        frame.setLayout(new BorderLayout());
        frame.add(button);
        frame.setSize(200, 200);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static void testUI() throws Exception {
        EventQueue.invokeAndWait(() -> {
            frameLocation = frame.getLocationOnScreen();
            newLoc = new Point(frame.getWidth() + 10, frame.getHeight() + 10);
        });

        robot.mouseMove(frameLocation.x + newLoc.x, frameLocation.y + newLoc.y);
        EventQueue.invokeAndWait(() -> {
            button.addComponentListener(new ResizeAdapter());
            frame.setSize(frame.getWidth() * 2, frame.getHeight() * 2);
            frame.validate();
        });
        robot.waitForIdle();
        robot.delay(500);

        if (!eventCaught.await(2, TimeUnit.SECONDS)) {
            throw new RuntimeException("componentResized Event isn't"
                                       + " received within a timeout");
        }

        if (!testSucceeded) {
            throw new RuntimeException("Container.getMousePosition(boolean)"
                                       + " returned incorrect result while Container resized");
        }
    }

    static class ResizeAdapter extends ComponentAdapter {
        int testStageCounter = 0;
        @Override
        public void componentResized(ComponentEvent e) {
            Point pTrue = frame.getMousePosition(true);
            if (frame.getMousePosition(false) == null) {
                testStageCounter++;
                System.out.println("""
                                    TEST STAGE 1 PASSED:
                                    Container.getMousePosition(false)
                                    returned NULL over Child Component
                                    during resize.
                                    """);
            }
            if (pTrue != null) {
                testStageCounter++;
                System.out.println("""
                                    TEST STAGE 2 PASSED:
                                    Container.getMousePosition(true)
                                    returned NON-NULL over Child Component
                                    during resize.
                                    """);
            }
            if (pTrue != null && pTrue.x == newLoc.x && pTrue.y == newLoc.y) {
                testStageCounter++;
                System.out.println("""
                                    TEST STAGE 3 PASSED:
                                    Container.getMousePosition(true)
                                    returned correct result over Child Component
                                    during resize.
                                    """);
            }
            testSucceeded = testStageCounter == 3;
            eventCaught.countDown();
        }
    }
}
