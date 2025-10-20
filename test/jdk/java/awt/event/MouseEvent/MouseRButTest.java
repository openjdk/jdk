/*
 * Copyright (c) 1998, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4037521
 * @summary Mouse Right button does not send mouseClick action
 * @key headful
 * @library /javax/swing/regtesthelpers
 * @build Util
 * @run main MouseRButTest
 */

import java.awt.Button;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MouseRButTest {
    private static Frame frame;
    private static Button button;
    private static final CountDownLatch latch = new CountDownLatch(1);

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        try {
            EventQueue.invokeAndWait(MouseRButTest::createAndShowGUI);

            robot.waitForIdle();
            robot.delay(500);

            Point point = Util.getCenterPoint(button);
            robot.mouseMove(point.x, point.y);
            robot.waitForIdle();
            robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
            robot.delay(50);
            robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);

            if (!latch.await(2, TimeUnit.SECONDS)) {
                throw new RuntimeException("mouse click action was not sent");
            }
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static void createAndShowGUI() {
        button = new Button("Click Me");
        button.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                System.out.println(e);
                if (e.getModifiers() == e.BUTTON3_MASK) {
                    System.out.println("right mouse button clicked");
                    latch.countDown();
                }
            }
        });

        frame = new Frame();
        frame.setLayout(new FlowLayout());
        frame.add(button);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
