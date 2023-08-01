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
import java.awt.dnd.DragSourceAdapter;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceListener;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.AWTEventListener;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

/*
  @test
  @bug 4633417
  @summary tests that drag operation doesn't cause hang when a modal dialog is
           shown
  @key headful
  @run main ModalDialogDeadlockTest
*/

public class ModalDialogDeadlockTest implements AWTEventListener {

    volatile Frame frame;
    volatile Dialog dialog;
    volatile Point dstPoint;
    volatile Point srcPoint;
    volatile Dimension d;

    volatile DragSource dragSource;
    volatile Transferable transferable;
    volatile DragSourceListener dsl;
    volatile DragGestureListener dgl;
    volatile DragGestureRecognizer dgr;
    volatile DropTarget dt;

    static final Object SYNC_LOCK = new Object();
    static final int FRAME_ACTIVATION_TIMEOUT = 2000;
    static final int MOUSE_RELEASE_TIMEOUT = 1000;

    Component clickedComponent = null;

    public static void main(String[] args) throws Exception {
        ModalDialogDeadlockTest test = new ModalDialogDeadlockTest();
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
        frame = new Frame("ModalDialogDeadlockTest");
        frame.setBounds(100, 100, 200, 200);
        dialog = new Dialog(frame, "Dialog", true);
        dialog.setBounds(350, 100, 200, 200);

        dragSource = DragSource.getDefaultDragSource();
        transferable = new StringSelection("TEXT");
        dsl = new DragSourceAdapter() {
            public void dragDropEnd(DragSourceDropEvent dsde) {
                synchronized (SYNC_LOCK) {
                    SYNC_LOCK.notifyAll();
                }
            }
        };
        dgl = new DragGestureListener() {
            public void dragGestureRecognized(DragGestureEvent dge) {
                dge.startDrag(null, transferable, dsl);
            }
        };
        dgr = dragSource.createDefaultDragGestureRecognizer(dialog,
                        DnDConstants.ACTION_COPY,
                        dgl);
        final DropTargetListener dtl = new DropTargetAdapter() {
            public void drop(DropTargetDropEvent dtde) {
                dtde.rejectDrop();
                dialog.dispose();
            }
        };
        dt = new DropTarget(frame, dtl);

        frame.getToolkit().addAWTEventListener(this, AWTEvent.MOUSE_EVENT_MASK);
        frame.setVisible(true);
    }

    public static int sign(int n) {
        return n < 0 ? -1 : n == 0 ? 0 : 1;
    }

    public void start() throws Exception {
        final Robot robot = new Robot();

        robot.delay(FRAME_ACTIVATION_TIMEOUT);

        EventQueue.invokeAndWait(() -> {
            dstPoint = frame.getLocationOnScreen();
            d = frame.getSize();
        });
        dstPoint.translate(d.width / 2, d.height / 2);

        if (!pointInComponent(robot, dstPoint, frame)) {
            System.err.println("WARNING: Couldn't locate frame.");
            return;
        }

        EventQueue.invokeLater(() -> {
            dialog.setVisible(true);
        });

        robot.delay(FRAME_ACTIVATION_TIMEOUT);

        EventQueue.invokeAndWait(() -> {
            srcPoint = dialog.getLocationOnScreen();
            d = dialog.getSize();
        });
        srcPoint.translate(d.width / 2, d.height / 2);

        if (!pointInComponent(robot, srcPoint, dialog)) {
            System.err.println("WARNING: Couldn't locate dialog.");
            return;
        }

        robot.mouseMove(srcPoint.x, srcPoint.y);
        robot.mousePress(InputEvent.BUTTON1_MASK);
        for (;!srcPoint.equals(dstPoint);
             srcPoint.translate(sign(dstPoint.x - srcPoint.x),
                                sign(dstPoint.y - srcPoint.y))) {
            robot.mouseMove(srcPoint.x, srcPoint.y);
            robot.delay(50);
        }
        synchronized (SYNC_LOCK) {
            robot.mouseRelease(InputEvent.BUTTON1_MASK);
            SYNC_LOCK.wait();
        }
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
