/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.AWTEvent;
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
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragGestureRecognizer;
import java.awt.dnd.DragSource;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.AWTEventListener;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

/*
  @test
  @bug 4352221
  @summary verifies that showing a modal dialog on drag doesn't hang
  @key headful
  @run main ModalDialogOnDragDeadlockTest
*/

public class ModalDialogOnDragDeadlockTest implements AWTEventListener {

    volatile Frame frame;
    volatile Dialog dialog;
    volatile Point srcPoint;
    volatile Dimension d;
    volatile boolean finished;

    static final Object SYNC_LOCK = new Object();
    static final int FRAME_ACTIVATION_TIMEOUT = 3000;
    static final int DROP_COMPLETION_TIMEOUT = 5000;
    static final int MOUSE_RELEASE_TIMEOUT = 1000;

    volatile DragSource dragSource;
    volatile Transferable transferable;
    volatile DragGestureListener dragGestureListener;
    volatile DragGestureRecognizer dragGestureRecognizer;
    volatile DropTargetListener dropTargetListener;
    volatile DropTarget dropTarget;

    Component clickedComponent = null;

    public static void main(String[] args) throws Exception {
        ModalDialogOnDragDeadlockTest test = new ModalDialogOnDragDeadlockTest();
        EventQueue.invokeAndWait(test::init);
        try {
            test.start();
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (test.frame != null) {
                    test.frame.dispose();
                }
            });
        }
    }

    public void init() {
        frame = new Frame("ModalDialogOnDragDeadlockTest");
        dialog = new Dialog(frame, "Modal dialog", true);
        frame.setTitle("Test frame");
        frame.setBounds(100, 100, 200, 200);

        dragSource = DragSource.getDefaultDragSource();
        transferable = new StringSelection("TEXT");
        dragGestureListener = new DragGestureListener() {
            public void dragGestureRecognized(DragGestureEvent dge) {
                dge.startDrag(null, transferable);
            }
        };
        dragGestureRecognizer =
                dragSource.createDefaultDragGestureRecognizer(frame, DnDConstants.ACTION_COPY,
                        dragGestureListener);
        dropTargetListener = new DropTargetAdapter() {
            public void dragOver(DropTargetDragEvent dtde) {
                dialog.setBounds(200, 200, 200, 200);
                dialog.setVisible(true);
            }
            public void drop(DropTargetDropEvent dtde) {
                dtde.acceptDrop(DnDConstants.ACTION_COPY);
                dtde.dropComplete(true);
            }
        };
        dropTarget = new DropTarget(frame, dropTargetListener);

        frame.getToolkit().addAWTEventListener(this, AWTEvent.MOUSE_EVENT_MASK);
        frame.setVisible(true);
    }

    public static int sign(int n) {
        return n < 0 ? -1 : n == 0 ? 0 : 1;
    }

    public void start() throws Exception {
        finished = false;
        Robot robot = new Robot();
        robot.waitForIdle();

        Thread.sleep(FRAME_ACTIVATION_TIMEOUT);
        EventQueue.invokeAndWait(() -> {
            srcPoint = frame.getLocationOnScreen();
            d = frame.getSize();
        });
        srcPoint.translate(d.width / 2, d.height / 2);

        if (!pointInComponent(robot, srcPoint, frame)) {
            System.err.println("WARNING: Couldn't locate source frame.");
            return;
        }

        final Point dstPoint = new Point(srcPoint);
        dstPoint.translate(d.width / 4, d.height / 4);

        robot.mouseMove(srcPoint.x, srcPoint.y);
        robot.mousePress(InputEvent.BUTTON1_MASK);
        for (;!srcPoint.equals(dstPoint);
             srcPoint.translate(sign(dstPoint.x - srcPoint.x),
                                sign(dstPoint.y - srcPoint.y))) {
            robot.mouseMove(srcPoint.x, srcPoint.y);
            Thread.sleep(50);
        }

        robot.mouseRelease(InputEvent.BUTTON1_MASK);

        Thread.sleep(DROP_COMPLETION_TIMEOUT);
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
        robot.mousePress(InputEvent.BUTTON1_MASK);
        synchronized (SYNC_LOCK) {
            robot.mouseRelease(InputEvent.BUTTON1_MASK);
            SYNC_LOCK.wait(MOUSE_RELEASE_TIMEOUT);
        }

        Component c = clickedComponent;

        while (c != null && c != comp) {
            c = c.getParent();
        }

        return c == comp;
    }
}