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

import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class ListEnterExitTest {
    final List list = new List();
    final MouseEnterExitListener mouseEnterExitListener = new MouseEnterExitListener();
    Frame frame;
    volatile Point p;

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
                list.addMouseListener(mouseEnterExitListener);
                frame.add(list);
                frame.setLayout(new FlowLayout());
                frame.setSize(300, 200);
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            });

            final Robot robot = new Robot();
            robot.delay(1000);
            robot.waitForIdle();

            EventQueue.invokeAndWait(() -> {
                p = list.getLocationOnScreen();
            });
            robot.mouseMove(p.x + 10, p.y + 10);
            robot.delay(100);
            robot.waitForIdle();
            robot.mouseMove(p.x - 10, p.y - 10);
            robot.delay(100);
            robot.waitForIdle();
            robot.mouseMove(p.x + 10, p.y + 10);

            robot.mousePress(InputEvent.BUTTON1_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_MASK);

            synchronized (mouseEnterExitListener) {
                mouseEnterExitListener.wait(2000);
            }
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
        if (!mouseEnterExitListener.isPassed()) {
            throw new RuntimeException("Haven't receive mouse enter/exit events");
        }

    }

}

class MouseEnterExitListener extends MouseAdapter {

    volatile boolean passed_1 = false;
    volatile boolean passed_2 = false;

    public void mouseEntered(MouseEvent e) {
        passed_1 = true;
    }

    public void mouseExited(MouseEvent e) {
        passed_2 = true;
    }

    public void mousePressed(MouseEvent e) {
        synchronized (this) {
            System.out.println("mouse pressed");
            this.notifyAll();
        }
    }

    public boolean isPassed() {
        return passed_1 & passed_2;
    }
}
