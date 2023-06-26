/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @test
  @summary unit test for a new method in Container class: getMousePosition(boolean)
  @bug 4009555
  @key headful
*/

import java.awt.Button;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Robot;

public class ContainerMousePositionTest {
    private Button button;
    private Frame frame;
    private Panel panel;
    private static Dimension BUTTON_DIMENSION = new Dimension(100, 100);
    private static Dimension FRAME_DIMENSION = new Dimension(200, 200);
    private static Point POINT_WITHOUT_COMPONENTS = new Point(10, 10);
    private static Point FIRST_BUTTON_LOCATION = new Point(20, 20);
    private static int DELAY = 1000;
    Robot robot;
    volatile int xPos = 0;
    volatile int yPos = 0;
    Point pMousePosition;

    public static void main(String[] args) throws Exception {
        ContainerMousePositionTest containerObj = new ContainerMousePositionTest();
        containerObj.init();
        containerObj.start();
    }

    public void init() throws Exception {
        robot = new Robot();
        EventQueue.invokeAndWait(() -> {
            button = new Button("Button");
            frame = new Frame("Testing Component.getMousePosition()");
            panel = new Panel();

            button.setSize(BUTTON_DIMENSION);
            button.setLocation(FIRST_BUTTON_LOCATION);

            panel.setLayout(null);

            panel.add(button);
            frame.add(panel);
            frame.setSize(FRAME_DIMENSION);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    public void start() throws Exception {
        try {
            robot.delay(DELAY);
            robot.waitForIdle();

            EventQueue.invokeAndWait(() -> {
                Point p = button.getLocationOnScreen();
                xPos = p.x + button.getWidth() / 2;
                yPos = p.y + button.getHeight() / 2;
            });
            robot.mouseMove(xPos,yPos);
            robot.delay(DELAY);

            EventQueue.invokeAndWait(() -> {
                pMousePosition = frame.getMousePosition(false);
                if (pMousePosition != null) {
                    throw new RuntimeException("Test failed: Container is " +
                            "overlapped by " + " child and it should be taken " +
                            "into account");
                }
                System.out.println("Test stage completed: Container is " +
                        "overlapped by " + " child and it was taken into " +
                        "account");

                pMousePosition = frame.getMousePosition(true);
                if (pMousePosition == null) {
                    throw new RuntimeException("Test failed: Container is " +
                            "overlapped by " + " child and it should not be " +
                            "taken into account");
                }
                System.out.println("Test stage completed: Container is " +
                        "overlapped by " + " child and it should not be " +
                        "taken into account");
                xPos = panel.getLocationOnScreen().x + POINT_WITHOUT_COMPONENTS.x;
                yPos = panel.getLocationOnScreen().y + POINT_WITHOUT_COMPONENTS.y;
            });

            robot.mouseMove(xPos, yPos);

            robot.delay(DELAY);

            EventQueue.invokeAndWait(() -> {
                pMousePosition = panel.getMousePosition(true);
                if (pMousePosition == null) {
                    throw new RuntimeException("Test failed: Pointer was " +
                            "outside of " + "the component so getMousePosition()" +
                            " should not return null");
                }
                System.out.println("Test stage completed: Pointer was outside of " +
                        "the component and getMousePosition() has not returned null");

                pMousePosition = panel.getMousePosition(false);
                if (pMousePosition == null) {
                    throw new RuntimeException("Test failed: Pointer was outside of " +
                            "the component so getMousePosition() should not return null");
                }
                System.out.println("Test stage completed: Pointer was outside of " +
                        "the component and getMousePosition() has not returned null");
                xPos = frame.getLocationOnScreen().x + frame.getWidth() + POINT_WITHOUT_COMPONENTS.x;
                yPos = frame.getLocationOnScreen().y + frame.getHeight() + POINT_WITHOUT_COMPONENTS.y;
            });
            robot.mouseMove(xPos, yPos);

            robot.delay(DELAY);

            EventQueue.invokeAndWait(() -> {
                pMousePosition = frame.getMousePosition(true);
                if (pMousePosition != null) {
                    throw new RuntimeException("Test failed: Pointer was outside of " +
                            "the Frame widow and getMousePosition() should return null");
                }
                System.out.println("Test stage completed: Pointer was outside of " +
                        "the Frame widow and getMousePosition() returned null");

                pMousePosition = frame.getMousePosition(false);
                if (pMousePosition != null) {
                    throw new RuntimeException("Test failed: Pointer was outside of " +
                            "the Frame widow and getMousePosition() should return null");
                }
                System.out.println("Test stage completed: Pointer was outside of " +
                        "the Frame widow and getMousePosition() returned null");
            });
            robot.delay(DELAY);

            System.out.println("ComponentMousePositionTest PASSED.");
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }
}
