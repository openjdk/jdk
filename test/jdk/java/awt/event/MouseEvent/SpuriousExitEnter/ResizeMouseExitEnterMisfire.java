/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.InputEvent;
import javax.swing.JFrame;
import javax.swing.JLabel;

/**
 * @test
 * @bug 8254841
 * @summary Tests spurious firing of mouseEntered and mouseExit on resize
 * @author Alisen Chung: area=MouseEvent
 * @library /test/lib
 * @run main ResizeMouseExitEnterMisfire
 */
public class ResizeMouseExitEnterMisfire {

    // Keep track of number of mouseEntered and mouseExited events
    private static volatile int mouseEntered = 0;
    private static volatile int mouseExited = 0;

    public static void main (String[] args) throws Exception {

        JFrame frame = new JFrame();
        frame.setSize(200, 200);
        JLabel label = new JLabel();
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered (MouseEvent ev) {
                System.err.println("mouseEntered");
                mouseEntered++;
            }
            @Override
            public void mouseExited (MouseEvent ev) {
                System.err.println("mouseExited");
                mouseExited++;
            }
        });
        frame.add(label);
        frame.setVisible(true);

        Robot robot = new Robot();
        robot.setAutoDelay(100);
        robot.waitForIdle();

        Rectangle rect = frame.getBounds();
        int xLocInnerCorner = rect.x + rect.width - 5;
        int yLocInnerCorner = rect.y + rect.height - 5;
        int xLocOuterCorner = rect.x + rect.width + 3;
        int yLocOuterCorner = rect.y + rect.height + 3;
        int xLocNewEdge = rect.x + rect.width + 500;
        int yLocNewEdge = rect.y + rect.height + 500;

        // Testing for resizing while cursor starts from inside the window
        robot.mouseMove(xLocInnerCorner, yLocInnerCorner);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseMove(xLocNewEdge, yLocNewEdge);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(100);

        robot.mouseMove(xLocNewEdge, yLocNewEdge);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseMove(xLocInnerCorner, yLocInnerCorner);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(100);

        /* Expected behavior should be a mouseEntered event when the mouse initially
         * enters the window, then no mouse events when the window size is changed.
         */
        if(mouseEntered != 1) {
            throw new RuntimeException("mouseEntered = " + mouseEntered
                    + ", expected value =  " + 1);
        }
        if(mouseExited != 0) {
            throw new RuntimeException("mouseExited = " + mouseExited
                    + ", expected value =  " + 0);
        }
        mouseEntered = 0;
        mouseExited = 0;

        robot.delay(500);

        // Testing for resizing while cursor starts outside the window
        robot.mouseMove(xLocOuterCorner, yLocOuterCorner);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseMove(xLocNewEdge, yLocNewEdge);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(100);

        robot.mouseMove(xLocNewEdge, yLocNewEdge);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseMove(xLocOuterCorner, yLocOuterCorner);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(100);
        robot.waitForIdle();

        /* Expected behavior should be a mouseExit event when the mouse initially
         * exits the window, then no mouse events when the window size is changed.
         */
        if(mouseEntered != 0) {
            throw new RuntimeException("mouseEntered = " + mouseEntered
                    + ", expected value =  " + 0);
        }
        if(mouseExited != 1) {
            throw new RuntimeException("mouseExited = " + mouseExited
                    + ", expected value =  " + 1);
        }
    }
}