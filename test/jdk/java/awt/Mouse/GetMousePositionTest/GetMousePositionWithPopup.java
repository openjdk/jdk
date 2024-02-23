/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import javax.swing.SwingUtilities;

/*
 * @test
 * @key headful
 * @bug 8012026 8027154
 * @summary Component.getMousePosition() does not work in an applet on MacOS
 *
 * @requires (os.family == "windows")
 * @run main/othervm -Dsun.java2d.uiScale.enabled=true -Dsun.java2d.uiScale=1 GetMousePositionWithPopup
 * @run main/othervm -Dsun.java2d.uiScale.enabled=true -Dsun.java2d.uiScale=1.25 GetMousePositionWithPopup
 * @run main/othervm -Dsun.java2d.uiScale.enabled=true -Dsun.java2d.uiScale=1.5 GetMousePositionWithPopup
 * @run main/othervm -Dsun.java2d.uiScale.enabled=true -Dsun.java2d.uiScale=1.75 GetMousePositionWithPopup
 * @run main/othervm -Dsun.java2d.uiScale.enabled=true -Dsun.java2d.uiScale=2 GetMousePositionWithPopup
 * @run main/othervm -Dsun.java2d.uiScale.enabled=true -Dsun.java2d.uiScale=3 GetMousePositionWithPopup
 */

/*
 * @test
 * @key headful
 * @bug 8012026 8027154
 * @summary Component.getMousePosition() does not work in an applet on MacOS
 *
 * @requires (os.family == "mac" | os.family == "linux")
 * @run main/othervm -Dsun.java2d.uiScale.enabled=true -Dsun.java2d.uiScale=1 GetMousePositionWithPopup
 * @run main/othervm -Dsun.java2d.uiScale.enabled=true -Dsun.java2d.uiScale=2 GetMousePositionWithPopup
 * @run main/othervm -Dsun.java2d.uiScale.enabled=true -Dsun.java2d.uiScale=3 GetMousePositionWithPopup
 */

public class GetMousePositionWithPopup {

    private static Frame frame1;
    private static Frame frame2;
    private static Robot robot;

    private static final int MOUSE_POS1 = 0;
    private static final int MOUSE_POS2 = 149;
    private static final int MOUSE_POS3 = 170;

    private static final int FRAME1_DIM = 100;
    private static final int FRAME2_DIM = 120;

    //expected mouse position w.r.t to Component
    //(2nd Frame in this test) after 3rd mouse move.
    private static final int EXPECTED_MOUSE_POS = MOUSE_POS3 - FRAME2_DIM;

    public static void main(String[] args) throws Exception {
        try {
            robot = new Robot();
            robot.setAutoDelay(200);

            //1st mouse move
            robot.mouseMove(MOUSE_POS1, MOUSE_POS1);
            syncLocationToWindowManager();

            SwingUtilities.invokeAndWait(GetMousePositionWithPopup::createTestUI);
            syncLocationToWindowManager();

            //2nd mouse move
            robot.mouseMove(MOUSE_POS2, MOUSE_POS2);
            syncLocationToWindowManager();

            SwingUtilities.invokeAndWait(GetMousePositionWithPopup::addMouseListenerToFrame2);
            //3rd mouse move
            robot.mouseMove(MOUSE_POS3, MOUSE_POS3);
            syncLocationToWindowManager();
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame1 != null) {
                    frame1.dispose();
                }
                if (frame2 != null) {
                    frame2.dispose();
                }
            });
        }
    }

    private static void createTestUI() {
        frame1 = new Frame();
        frame1.setBounds(FRAME1_DIM, FRAME1_DIM, FRAME1_DIM, FRAME1_DIM);
        frame1.setVisible(true);
        frame1.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                if (frame2 != null) {
                    return;
                }
                frame2 = new Frame();
                frame2.setBounds(FRAME2_DIM, FRAME2_DIM, FRAME2_DIM, FRAME2_DIM);
                frame2.setVisible(true);
            }
        });
    }

    private static void addMouseListenerToFrame2() {
        frame2.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                Point positionInFrame2 = frame2.getMousePosition();
                int deltaX = Math.abs(EXPECTED_MOUSE_POS - positionInFrame2.x);
                int deltaY = Math.abs(EXPECTED_MOUSE_POS - positionInFrame2.y);
                if (deltaX > 2 || deltaY > 2) {
                    throw new RuntimeException("Wrong position reported for Frame 2."
                            + " Should be [50, 50] but was " + "[" + positionInFrame2.x
                            + ", " + positionInFrame2.y + "]");
                }

                Point positionInFrame1 = frame1.getMousePosition();
                if (positionInFrame1 != null) {
                    throw new RuntimeException("Wrong position reported for Frame 1."
                            + " Should be null");
                }
            }
        });
    }

    private static void syncLocationToWindowManager() {
        Toolkit.getDefaultToolkit().sync();
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        robot.waitForIdle();
    }
}
