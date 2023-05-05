/*
 * Copyright (c) 2002, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @bug 4484572 4645584
  @summary verifies that showing a modal dialog during the drag operation
           in the same JVM doesn't cause hang
  @key headful
  @run main SameJVMModalDialogDeadlockTest
*/

import java.awt.AWTEvent;
import java.awt.AWTException;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceAdapter;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.event.AWTEventListener;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.InvocationTargetException;


public class SameJVMModalDialogDeadlockTest implements AWTEventListener {

    Frame frame;
    boolean shown = false;
    boolean finished = false;

    final DragSource dragSource = DragSource.getDefaultDragSource();
    final Transferable transferable = new StringSelection("TEXT");
    final DragSourceAdapter dragSourceAdapter = new DragSourceAdapter() {
        public void dragDropEnd(DragSourceDropEvent dsde) {
            finished = true;
        }
        public void dragMouseMoved(DragSourceDragEvent dsde) {
            if (shown) {
                return;
            }

            shown = true;
            final Dialog d = new Dialog(frame, "Dialog");
            d.setModal(true);

            Runnable r1 = () -> d.setVisible(true);
            new Thread(r1).start();
        }
    };
    final DragGestureListener dragGestureListener = dge ->
            dge.startDrag(null, transferable);

    static final Object SYNC_LOCK = new Object();
    static final int FRAME_ACTIVATION_TIMEOUT = 3000;
    static final int DROP_COMPLETION_TIMEOUT = 5000;
    static final int MOUSE_RELEASE_TIMEOUT = 1000;

    Component clickedComponent = null;

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException, AWTException {
        SameJVMModalDialogDeadlockTest sameJVMModalDialogDeadlockTest =
                new SameJVMModalDialogDeadlockTest();
        EventQueue.invokeAndWait(sameJVMModalDialogDeadlockTest::init);
        sameJVMModalDialogDeadlockTest.start();
    }

    public void init() {
        frame = new Frame("SameJVMModalDialogDeadlockTest");
        frame.setTitle("Test frame");
        frame.setBounds(100, 100, 200, 200);
        dragSource.createDefaultDragGestureRecognizer(frame,
                DnDConstants.ACTION_COPY_OR_MOVE, dragGestureListener);

        dragSource.addDragSourceMotionListener(dragSourceAdapter);
        dragSource.addDragSourceListener(dragSourceAdapter);

        frame.getToolkit().addAWTEventListener(this, AWTEvent.MOUSE_EVENT_MASK);
        frame.setVisible(true);
    }

    public void start() throws AWTException, InterruptedException,
            InvocationTargetException {
        try {
            final Robot robot = new Robot();
            robot.waitForIdle();
            robot.delay(FRAME_ACTIVATION_TIMEOUT);

            final Point srcPoint = frame.getLocationOnScreen();
            Dimension d = frame.getSize();
            srcPoint.translate(d.width / 2, d.height / 2);

            if (!pointInComponent(robot, srcPoint, frame)) {
                System.err.println("WARNING: Couldn't locate source frame.");
                return;
            }

            final Point dstPoint = new Point(srcPoint);
            dstPoint.translate(d.width / 4, d.height / 4);

            robot.mouseMove(srcPoint.x, srcPoint.y);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            for (;!srcPoint.equals(dstPoint);
                 srcPoint.translate(sign(dstPoint.x - srcPoint.x),
                         sign(dstPoint.y - srcPoint.y))) {
                robot.mouseMove(srcPoint.x, srcPoint.y);
                robot.delay(50);
            }

            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

            robot.delay(DROP_COMPLETION_TIMEOUT);
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }

        if (!finished) {
            throw new RuntimeException("DnD not completed");
        }
    }

    public static int sign(int n) {
        return n < 0 ? -1 : n == 0 ? 0 : 1;
    }

    public void reset() {
        clickedComponent = null;
    }

    public void eventDispatched(AWTEvent e) {
        if (e.getID() == MouseEvent.MOUSE_RELEASED) {
            clickedComponent = (Component)e.getSource();
            synchronized (SYNC_LOCK) {
                SYNC_LOCK.notifyAll();
            }
        }
    }

    boolean pointInComponent(Robot robot, Point p, Component comp)
      throws InterruptedException {
        robot.waitForIdle();
        reset();
        robot.mouseMove(p.x, p.y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        synchronized (SYNC_LOCK) {
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            SYNC_LOCK.wait(MOUSE_RELEASE_TIMEOUT);
        }

        Component c = clickedComponent;

        while (c != null && c != comp) {
            c = c.getParent();
        }

        return c == comp;
    }
}
