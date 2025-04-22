/*
 * Copyright (c) 2003, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4008152
 * @summary ScrollPane position does not return correct values
 * @key headful
 * @run main ScrollPositionTest
 */

import java.awt.Adjustable;
import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;
import java.awt.ScrollPane;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;

public class ScrollPositionTest {
    static Frame frame;
    static int i = 0;
    static Point p;
    static ScrollPane sp;

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        try {
            EventQueue.invokeAndWait(() -> {
                frame = new Frame("Scroll Position Test");
                frame.setLayout(new BorderLayout());
                frame.setSize(200, 200);
                sp = new ScrollPane(ScrollPane.SCROLLBARS_AS_NEEDED);
                Canvas canvas = new Canvas();
                canvas.setSize(300, 300);
                sp.add(canvas);
                frame.add("Center", sp);
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            });
            robot.waitForIdle();
            robot.delay(1000);
            EventQueue.invokeAndWait(() -> {
                Adjustable saH = sp.getHAdjustable();
                saH.addAdjustmentListener(new TestAdjustmentListener());
            });
            for (i = 0; i < 1000; i++) {
                EventQueue.invokeAndWait(() -> {
                    p = new Point(i % 100, i % 100);
                    sp.setScrollPosition(p);
                });

                robot.waitForIdle();
                robot.delay(10);
                EventQueue.invokeAndWait(() -> {
                    if (!sp.getScrollPosition().equals(p)) {
                        throw new RuntimeException("Test failed. " + i + " : " +
                                "Expected " + p + ", but Returned: " + sp.getScrollPosition());
                    }
                });
            }
            System.out.println("Test Passed.");
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static class TestAdjustmentListener implements AdjustmentListener {
        public void adjustmentValueChanged(AdjustmentEvent e) {
            System.out.println("AdjEvent caught:" + e);
        }
    }
}
