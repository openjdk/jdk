/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/*
 * @test
 * @key headful
 * @bug 4065534
 * @summary Frame.setSize() doesn't change size if window is in an iconified state
 * @run main SizeMinimizedTest
 */

public class SizeMinimizedTest {
    private static Frame frame;
    private static final int INITIAL_SIZE = 100;
    private static final int INITIAL_X = 150;
    private static final int INITIAL_Y = 50;
    private static final int RESET_SIZE = 200;
    private static final int OFFSET = 10;
    private static int iterationCnt = 0;
    private static Dimension expectedSize;
    private static Dimension frameSize;
    private static Point expectedLoc;
    private static Point frameLoc;

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        try {
            EventQueue.invokeAndWait(() -> {
                createUI();
            });
            robot.waitForIdle();
            robot.delay(1000);

            EventQueue.invokeAndWait(() -> {
                frame.setState(Frame.ICONIFIED);
            });
            robot.waitForIdle();
            robot.delay(100);

            EventQueue.invokeAndWait(() -> {
                frame.setSize(RESET_SIZE, RESET_SIZE);
            });
            robot.waitForIdle();
            robot.delay(100);

            for (int i = 0; i < 5; i++) {
                EventQueue.invokeAndWait(() -> {
                    Point pt = frame.getLocation();
                    frame.setLocation(pt.x + OFFSET, pt.y);
                });
                iterationCnt++;
                robot.waitForIdle();
                robot.delay(100);
            }
            EventQueue.invokeAndWait(() -> {
                frame.setState(Frame.NORMAL);
            });
            robot.waitForIdle();
            robot.delay(100);

            System.out.println("Test Passed!");
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    public static void createUI() {
        frame = new Frame("Frame size test");
        frame.setSize(INITIAL_SIZE, INITIAL_SIZE);
        frame.setLocation(INITIAL_X, INITIAL_Y);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                System.out.println("Initial Frame Size: " + frame.getSize());
                System.out.println("Initial Frame Location: " +
                        frame.getLocationOnScreen());
            }
        });

        frame.addWindowStateListener(new WindowAdapter() {
            @Override
            public void windowStateChanged(WindowEvent e) {
                if (e.getNewState() == Frame.NORMAL) {
                    System.out.println("Frame Size: " + frame.getSize());
                    System.out.println("Frame Location: " +
                            frame.getLocationOnScreen());
                    expectedSize = new Dimension(RESET_SIZE, RESET_SIZE);
                    frameSize = frame.getSize();

                    if (!expectedSize.equals(frameSize)) {
                        throw new RuntimeException("Test Failed due to size mismatch.");
                    }

                    expectedLoc = new Point(INITIAL_X + OFFSET * iterationCnt,
                            INITIAL_Y);
                    frameLoc = frame.getLocationOnScreen();

                    if (!expectedLoc.equals(frameLoc)) {
                        throw new RuntimeException("Test Failed due to " +
                                "location mismatch.");
                    }
                }
            }
        });
        frame.setVisible(true);
    }
}
