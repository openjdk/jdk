/*
 * Copyright (c) 1999, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Label;
import java.awt.Point;
import java.awt.Robot;

/*
 * @test
 * @bug 4189198
 * @key headful
 * @summary updateClient should post a PaintEvent
 */

public class RepaintTest {
    private static volatile Frame frame;
    private static volatile Label label;
    private static volatile Point frameLoc;

    private static final int FRAME_DIM = 100;

    public static void main(String[] args) throws Exception {
        try {
            Robot robot = new Robot();
            EventQueue.invokeAndWait(() -> {
                frame = new Frame("Repaint Tester");
                frame.setSize(FRAME_DIM, FRAME_DIM);
                label = new Label("Hi");
                frame.add(label);
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            });
            robot.waitForIdle();
            robot.delay(1000);

            EventQueue.invokeAndWait(() -> {
                label.setBackground(Color.GREEN);
                label.repaint();
                frameLoc = frame.getLocationOnScreen();
            });
            robot.waitForIdle();
            robot.delay(500);

            Color expectedColor = robot.getPixelColor(frameLoc.x + FRAME_DIM / 2,
                                                      frameLoc.y + FRAME_DIM / 2);
            if (!Color.GREEN.equals(expectedColor)) {
                throw new RuntimeException("Test Failed! \n" +
                        "PaintEvent was not triggered: ");
            }
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                   frame.dispose();
                }
            });
        }
    }
}
