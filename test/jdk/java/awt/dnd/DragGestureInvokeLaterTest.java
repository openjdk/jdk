/*
 * Copyright (c) 2001, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @bug 4354044
  @summary tests that a drag can be initiated with MOUSE_MOVED event
  @key headful
*/

import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Robot;
import java.awt.datatransfer.StringSelection;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragGestureRecognizer;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceEvent;
import java.awt.dnd.DragSourceListener;
import java.awt.dnd.InvalidDnDOperationException;
import java.awt.event.InputEvent;

public class DragGestureInvokeLaterTest {

    volatile Frame frame;
    volatile DragSourcePanel panel;

    public static void main(String[] args) throws Exception {
        DragGestureInvokeLaterTest test =
                new DragGestureInvokeLaterTest();
        EventQueue.invokeAndWait(test::init);
        try {
            test.start();
        } finally {
            EventQueue.invokeAndWait(() -> test.frame.dispose());
        }
    }

    public void init() {
        panel = new DragSourcePanel();
        frame = new Frame("DragGestureInvokeLaterTest frame");
        frame.setSize(200, 200);
        frame.setLocation(200, 200);
        frame.add(panel);
        frame.setVisible(true);
    }

    public void start() throws Exception {
        Robot robot = new Robot();

        robot.waitForIdle();
        robot.delay(1000);

        Point loc = panel.getLocationOnScreen();

        robot.mouseMove(loc.x + 2, loc.y + 2);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);

        for (int i = 0; i < 10; i++) {
            robot.delay(100);
            robot.mouseMove(loc.x + 2 + i, loc.y + 2 + i);
        }

        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(1000);
    }
}

class DragSourcePanel extends Panel
        implements DragSourceListener, DragGestureListener {

    DragSource ds;
    DragGestureRecognizer dgr;

    public DragSourcePanel() {
        ds = new DragSource();
        dgr = ds.createDefaultDragGestureRecognizer(this,
            DnDConstants.ACTION_COPY_OR_MOVE, this);
    }

    public void dragGestureRecognized(DragGestureEvent e) {
        Runnable dragThread = new DragThread(e);
        EventQueue.invokeLater(dragThread);
    }

    class DragThread implements Runnable {

        DragGestureEvent event;

        public DragThread(DragGestureEvent e) {
            event = e;
        }

        public void run() {
            try {
                event.startDrag(DragSource.DefaultCopyNoDrop,
                    new StringSelection("Test"), DragSourcePanel.this);
            } catch (InvalidDnDOperationException e) {
                System.out.println("The test PASSED");
                return;
            }
            throw new RuntimeException(
                    "Test failed, InvalidDnDOperationException is not thrown");
        }
    }

    public void dragEnter(DragSourceDragEvent e) {}

    public void dragOver(DragSourceDragEvent e) {}

    public void dropActionChanged(DragSourceDragEvent e) {}

    public void dragExit(DragSourceEvent e) {}

    public void dragDropEnd(DragSourceDropEvent e) {}
}
