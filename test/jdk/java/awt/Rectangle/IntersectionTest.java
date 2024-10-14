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

/*
 * @test
 * @bug 4147957
 * @key headful
 * @summary Test to verify setClip with invalid rect changes rect to valid
 * @run main IntersectionTest
 */

import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Panel;
import java.awt.Rectangle;
import java.awt.Robot;

public class IntersectionTest {
    public static Frame frame;

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        try {
            robot.setAutoDelay(100);
            EventQueue.invokeAndWait(() -> {
                TestFrame panel = new TestFrame();
                frame = new Frame("Rectangle Intersection Test");
                frame.add(panel);

                frame.pack();
                frame.setVisible(true);
            });
            robot.waitForIdle();
            robot.delay(200);
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }
}

class TestFrame extends Panel {
    @Override
    public void paint(Graphics g) {
        Rectangle r1 = new Rectangle(0, 0, 100, 100);
        Rectangle r2 = new Rectangle(200, 200, 20, 20);
        Rectangle r3 = r1.intersection(r2);
        System.out.println("intersect:(" + (int) r3.getX() + "," +
                (int) r3.getY() + "," + (int) r3.getWidth() + "," +
                (int) r3.getHeight() + ")");
        g.setClip(r3);
        Rectangle r4 = g.getClipBounds();
        System.out.println("getClipBounds:(" + (int) r4.getX() + "," +
                (int) r4.getY() + "," + (int) r4.getWidth() + "," +
                (int) r4.getHeight() + ")");

        if ((r4.getWidth() <= 0) || (r4.getHeight() <= 0)) {
            System.out.println("Test Passed");
        } else {
            throw new RuntimeException("IntersectionTest failed. " +
                    "Non-empty clip bounds.");
        }
    }
}
