/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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
  @bug 4274839 4281703
  @summary tests that List receives mouse enter/exit events properly
  @key headful
  @run main ListEnterExitTest
*/

import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.List;
import java.awt.Point;
import java.awt.Robot;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ListEnterExitTest {
    final List list = new List();
    Frame frame;
    volatile Point p;

    private static final int X_OFFSET = 30;
    private static final int Y_OFFSET = 40;
    private static final int LATCH_TIMEOUT = 3;

    private final CountDownLatch mouseEnterLatch = new CountDownLatch(1);
    private final CountDownLatch mouseExitLatch = new CountDownLatch(1);

    public static void main(String[] args) throws Exception {
        ListEnterExitTest test = new ListEnterExitTest();
        test.start();
    }

    public void start() throws Exception {
        try {
            EventQueue.invokeAndWait(() -> {
                frame = new Frame("ListEnterExitTest");
                list.add("Item 1");
                list.add("Item 2");
                list.add("Item 3");
                list.add("Item 4");
                list.add("Item 5");
                list.add("Item 6");
                list.addMouseListener(new MouseEnterExitListener());
                frame.add(list);
                frame.setLayout(new FlowLayout());
                frame.setSize(300, 200);
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            });

            final Robot robot = new Robot();
            robot.setAutoDelay(100);
            robot.waitForIdle();
            robot.delay(1000);

            EventQueue.invokeAndWait(() -> {
                p = list.getLocationOnScreen();
            });
            robot.mouseMove(p.x + X_OFFSET, p.y + Y_OFFSET);
            robot.waitForIdle();

            robot.mouseMove(p.x - X_OFFSET, p.y + Y_OFFSET);
            robot.waitForIdle();

            robot.mouseMove(p.x + X_OFFSET, p.y + Y_OFFSET);
            robot.waitForIdle();

            if (!mouseEnterLatch.await(LATCH_TIMEOUT, TimeUnit.SECONDS)) {
                throw new RuntimeException("Mouse enter event timeout");
            }

            if (!mouseExitLatch.await(LATCH_TIMEOUT, TimeUnit.SECONDS)) {
                throw new RuntimeException("Mouse exit event timeout");
            }
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private class MouseEnterExitListener extends MouseAdapter {
        @Override
        public void mouseEntered(MouseEvent e) {
            System.out.println("Mouse Entered Event");
            mouseEnterLatch.countDown();
        }

        @Override
        public void mouseExited(MouseEvent e) {
            System.out.println("Mouse Exited Event");
            mouseExitLatch.countDown();
        }
    }
}
