/*
 * Copyright (c) 2002, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4490179
 * @summary Tests that JButton only responds to left mouse clicks.
 * @key headful
 * @run main bug4490179
 */

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public class bug4490179 {
    static JFrame frame;
    static JButton button;
    static volatile Point pt;
    private static final CountDownLatch anyButton = new CountDownLatch(1);
    private static final CountDownLatch mouseButton1 = new CountDownLatch(1);

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        robot.setAutoDelay(100);
        robot.setAutoWaitForIdle(true);
        try {
            SwingUtilities.invokeAndWait(() -> {
                frame = new JFrame("bug4490179");
                button = new JButton("Button");
                frame.getContentPane().add(button);
                button.addActionListener(e -> {
                    System.err.println("ActionEvent: " + e);
                    if ((e.getModifiers() & InputEvent.BUTTON1_MASK) != 0) {
                        mouseButton1.countDown();
                    }
                    anyButton.countDown();
                });
                frame.pack();
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            });
            robot.waitForIdle();
            robot.delay(1000);
            SwingUtilities.invokeAndWait(() -> {
                Point loc = button.getLocationOnScreen();
                Dimension size = button.getSize();
                pt = new Point(loc.x + size.width / 2, loc.y + size.height / 2);
            });

            robot.mouseMove(pt.x, pt.y);
            robot.waitForIdle();
            robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);

            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
            if (anyButton.await(3, TimeUnit.SECONDS)) {
                // Restore robot state
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                throw new RuntimeException("Unexpected ActionEvent");
            }

            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            if (!mouseButton1.await(2, TimeUnit.SECONDS)) {
                throw new RuntimeException("Missing ActionEvent for BUTTON1");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }
}
