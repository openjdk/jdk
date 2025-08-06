/*
 * Copyright (c) 2008, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @key headful
 * @bug 6315717
 * @summary Verifies that the mouse drag events received for every button if the property is set to true
 * @run main ExtraButtonDrag
 */

import java.awt.AWTException;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.InvocationTargetException;

public class ExtraButtonDrag {

    private static Frame frame;
    private static Robot robot;
    private static volatile boolean dragged = false;
    private static volatile boolean moved = false;
    private static volatile Point centerFrame;
    private static volatile Point outboundsFrame;
    private static final String OS_NAME = System.getProperty("os.name");
    private static MouseAdapter mAdapter = new MouseAdapter() {
        @Override
        public void mouseDragged(MouseEvent e) {
            dragged = true;
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            moved = true;
        }
    };

    public static void initializeGUI() {
        frame = new Frame("ExtraButtonDrag");
        frame.addMouseMotionListener(mAdapter);
        frame.addMouseListener(mAdapter);

        frame.setSize(300, 300);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public static void doTest()
        throws InvocationTargetException, InterruptedException {

        int[] buttonMask = new int[MouseInfo.getNumberOfButtons()];

        for (int i = 0; i < MouseInfo.getNumberOfButtons(); i++) {
            buttonMask[i] = InputEvent.getMaskForButton(i + 1);
        }

        EventQueue.invokeAndWait(() -> {
            Point location = frame.getLocationOnScreen();
            Dimension size = frame.getSize();
            centerFrame = new Point(location.x + size.width / 2,
                location.y + size.height / 2);
            outboundsFrame = new Point(location.x + size.width * 3 / 2,
                location.y + size.height / 2);
        });

        System.out.println("areExtraMouseButtonsEnabled() == "
            + Toolkit.getDefaultToolkit().areExtraMouseButtonsEnabled());

        for (int i = 0; i < MouseInfo.getNumberOfButtons(); i++) {
            System.out.println("button to drag = " + (i + 1)
                + " : value passed to robot = " + buttonMask[i]);

            try {
                dragMouse(buttonMask[i], centerFrame.x, centerFrame.y,
                    outboundsFrame.x, outboundsFrame.y);
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Test failed. Exception occured.",
                    e);
            }

            // this is a choice-case for X protocol issue: native events from
            // extra buttons doesn't contain
            // the correct state so it's unable to decide if there is a drag or
            // move. By default we send MOVED event.
            // XToolkit: extra buttons should report MOVED events only
            // WToolkit: extra buttons should report DRAGGED events only
            if (i > 2) { // extra buttons only
                if (OS_NAME.equals("Linux")) {
                    if (!moved || dragged) {
                        throw new RuntimeException("Test failed." + OS_NAME
                            + " Button = " + (i + 1) + " moved = " + moved
                            + " : dragged = " + dragged);
                    }
                } else { // WToolkit
                    if (moved || !dragged) {
                        throw new RuntimeException("Test failed." + OS_NAME
                            + " Button = " + (i + 1) + " moved = " + moved
                            + " : dragged = " + dragged);
                    }
                }
            } else {
                if (moved || !dragged) {
                    throw new RuntimeException(
                        "Test failed. Button = " + (i + 1) + " not dragged.");
                }
            }
        }
    }

    public static void dragMouse(int button, int x0, int y0, int x1, int y1) {
        int curX = x0;
        int curY = y0;
        int dx = x0 < x1 ? 1 : -1;
        int dy = y0 < y1 ? 1 : -1;
        robot.mouseMove(x0, y0);

        robot.delay(200);
        dragged = false;
        moved = false;

        robot.mousePress(button);

        while (curX != x1) {
            curX += dx;
            robot.mouseMove(curX, curY);
            robot.delay(5);
        }
        while (curY != y1) {
            curY += dy;
            robot.mouseMove(curX, curY);
            robot.delay(5);
        }
        robot.mouseRelease(button);
    }

    public static void main(String[] s)
        throws InvocationTargetException, InterruptedException, AWTException {
        try {
            robot = new Robot();
            robot.setAutoDelay(10);
            robot.setAutoWaitForIdle(true);

            EventQueue.invokeAndWait(ExtraButtonDrag::initializeGUI);
            robot.waitForIdle();
            robot.delay(100);

            doTest();

            System.out.println("Test Passed");
        } finally {
            EventQueue.invokeAndWait(ExtraButtonDrag::disposeFrame);
        }
    }

    public static void disposeFrame() {
        if (frame != null) {
            frame.dispose();
            frame = null;
        }
    }

}
