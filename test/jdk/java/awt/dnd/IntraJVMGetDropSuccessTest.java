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
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.GridLayout;
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
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.AWTEventListener;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

/*
  @test
  @bug 4658741
  @summary verifies that getDropSuccess() returns correct value for intra-JVM DnD
  @key headful
  @run main IntraJVMGetDropSuccessTest
*/

public class IntraJVMGetDropSuccessTest implements AWTEventListener {

    static final Object SYNC_LOCK = new Object();
    static final int FRAME_ACTIVATION_TIMEOUT = 3000;
    static final int MOUSE_RELEASE_TIMEOUT = 1000;

    static class DragSourceDropListener extends DragSourceAdapter {
        private boolean finished = false;
        private boolean dropSuccess = false;

        public void reset() {
            finished = false;
            dropSuccess = false;
        }

        public boolean isDropFinished() {
            return finished;
        }

        public boolean getDropSuccess() {
            return dropSuccess;
        }

        public void dragDropEnd(DragSourceDropEvent dsde) {
            finished = true;
            dropSuccess = dsde.getDropSuccess();
            synchronized (SYNC_LOCK) {
                SYNC_LOCK.notifyAll();
            }
        }
    }

    static class ChildCanvas extends Canvas {
        private final Dimension preferredDimension = new Dimension(100, 200);

        public Dimension getPreferredSize() {
            return preferredDimension;
        }
    }

    volatile Frame frame;
    volatile Canvas canvas1;
    volatile Canvas canvas2;
    volatile Canvas canvas3;
    volatile Point p;
    volatile Dimension d;
    volatile Component c;

    volatile DragSourceDropListener dragSourceListener;
    volatile DragSource dragSource;
    volatile Transferable transferable;
    volatile DragGestureListener dragGestureListener;
    volatile DragGestureRecognizer dragGestureRecognizer;
    volatile DropTargetListener dropTargetListener;
    volatile DropTarget dropTarget;

    Component clickedComponent = null;

    public static void main(String[] args) throws Exception {
        IntraJVMGetDropSuccessTest test = new IntraJVMGetDropSuccessTest();
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
        frame = new Frame();
        canvas1 = new ChildCanvas();
        canvas2 = new ChildCanvas();
        canvas3 = new ChildCanvas();

        dragSourceListener = new DragSourceDropListener();
        dragSource = DragSource.getDefaultDragSource();
        transferable = new StringSelection("TEXT");
        dragGestureListener = new DragGestureListener() {
            public void dragGestureRecognized(DragGestureEvent dge) {
                dge.startDrag(null, transferable, dragSourceListener);
            }
        };
        dragGestureRecognizer =
                dragSource.createDefaultDragGestureRecognizer(canvas2, DnDConstants.ACTION_COPY,
                        dragGestureListener);
        dropTargetListener = new DropTargetAdapter() {
            public void drop(DropTargetDropEvent dtde) {
                dtde.acceptDrop(DnDConstants.ACTION_COPY);
                dtde.dropComplete(true);
            }
        };
        dropTarget = new DropTarget(canvas3, dropTargetListener);


        canvas1.setBackground(Color.red);
        canvas2.setBackground(Color.yellow);
        canvas3.setBackground(Color.green);

        frame.setTitle("IntraJVMGetDropSuccessTest");
        frame.setLocation(100, 100);
        frame.setLayout(new GridLayout(1, 3));
        frame.getToolkit().addAWTEventListener(this, AWTEvent.MOUSE_EVENT_MASK);
        frame.add(canvas1);
        frame.add(canvas2);
        frame.add(canvas3);
        frame.pack();

        frame.setVisible(true);
    }

    public static int sign(int n) {
        return n < 0 ? -1 : n == 0 ? 0 : 1;
    }

    public void start() throws Exception {
        Robot robot = new Robot();

        robot.delay(FRAME_ACTIVATION_TIMEOUT);

        final Point srcPoint = getCenterLocationOnScreen(canvas2);

        if (!pointInComponent(robot, srcPoint, canvas2)) {
            System.err.println("WARNING: Couldn't locate " + canvas2);
            return;
        }

        final Point dstPoint1 = getCenterLocationOnScreen(canvas1);

        if (!pointInComponent(robot, dstPoint1, canvas1)) {
            System.err.println("WARNING: Couldn't locate " + canvas1);
            return;
        }

        final Point dstPoint2 = getCenterLocationOnScreen(canvas3);
        if (!pointInComponent(robot, dstPoint2, canvas3)) {
            System.err.println("WARNING: Couldn't locate " + canvas3);
            return;
        }

        robot.waitForIdle();
        test(robot, srcPoint, dstPoint1, false);
        test(robot, srcPoint, dstPoint2, true);
        test(robot, srcPoint, dstPoint1, false);
    }

    public Point getCenterLocationOnScreen(Component c) throws Exception {
        EventQueue.invokeAndWait(() -> {
            p = c.getLocationOnScreen();
            d = c.getSize();
        });
        p.translate(d.width / 2, d.height / 2);
        return p;
    }

    public void test(Robot robot, Point src, Point dst, boolean success)
      throws InterruptedException {

        dragSourceListener.reset();
        robot.mouseMove(src.x, src.y);
        robot.mousePress(InputEvent.BUTTON1_MASK);

        for (Point p = new Point(src); !p.equals(dst);
             p.translate(sign(dst.x - p.x),
                         sign(dst.y - p.y))) {
            robot.mouseMove(p.x, p.y);
            robot.delay(50);
        }

        synchronized (SYNC_LOCK) {
            robot.mouseRelease(InputEvent.BUTTON1_MASK);
            SYNC_LOCK.wait();
        }

        if (!dragSourceListener.isDropFinished()) {
            throw new RuntimeException("Drop not finished");
        }

        if (dragSourceListener.getDropSuccess() != success) {
            throw new RuntimeException("getDropSuccess() returned wrong value:"
                                       + dragSourceListener.getDropSuccess());
        }
    }

    public void reset() throws Exception {
        EventQueue.invokeAndWait(() -> {
            clickedComponent = null;
        });

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
      throws Exception {
        robot.waitForIdle();
        reset();
        robot.mouseMove(p.x, p.y);
        robot.mousePress(InputEvent.BUTTON1_MASK);
        synchronized (SYNC_LOCK) {
            robot.mouseRelease(InputEvent.BUTTON1_MASK);
            SYNC_LOCK.wait(MOUSE_RELEASE_TIMEOUT);
        }

        EventQueue.invokeAndWait(() -> {
            c = clickedComponent;

            while (c != null && c != comp) {
                c = c.getParent();
            }
        });

        return c == comp;
    }
}
