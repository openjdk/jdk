/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Container;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceAdapter;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.AWTEventListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/*
 * @test
 * @key headful
 * @bug 4422345
 * @summary tests that DragSourceMotionListeners work correctly and
           DragSourceEvents position is correct
 */

public class DragSourceMotionListenerTest implements AWTEventListener {
    static class TestPanel extends Panel {
        final Dimension preferredDimension = new Dimension(200, 200);
        public Dimension getPreferredSize() {
            return preferredDimension;
        }
    }

    private static Frame frame;
    private static final Panel source = new TestPanel();
    private static final Panel target = new TestPanel();
    private static final DragSource ds = DragSource.getDefaultDragSource();
    private static volatile CountDownLatch mouseReleaseEvent;

    static volatile boolean passedTest1 = false;
    static volatile boolean passedTest2 = false;

    private static final Point testPoint1 = new Point();
    private static final Point testPoint2 = new Point();
    private static volatile Point srcPoint;
    private static volatile Point dstOutsidePoint;
    private static volatile Point dstInsidePoint;

    private static final Transferable t = new StringSelection("TEXT");
    private static final DragGestureListener gestureListener = e -> e.startDrag(null, t);

    private static final DragSourceAdapter sourceAdapter = new DragSourceAdapter() {
        public void dragMouseMoved(DragSourceDragEvent dsde) {
            if (Math.abs(dsde.getX() - testPoint1.getX()) < 5) {
                passedTest1 = true;
            }
        }

        public void dragDropEnd(DragSourceDropEvent dsde) {
            if (Math.abs(dsde.getX() - testPoint2.getX()) < 5) {
                passedTest2 = true;
            }
        }
    };

    private static final DropTargetListener targetAdapter = new DropTargetAdapter() {
        public void drop(DropTargetDropEvent e) {
            e.acceptDrop(DnDConstants.ACTION_COPY);
            try {
                final Transferable t = e.getTransferable();
                final String str =
                        (String) t.getTransferData(DataFlavor.stringFlavor);
                e.dropComplete(true);
            } catch (Exception ex) {
                ex.printStackTrace();
                e.dropComplete(false);
            }
        }
    };

    private static final DropTarget dropTarget = new DropTarget(target, targetAdapter);
    Component clickedComponent = null;

    private void createAndShowUI() {
        frame = new Frame("DragSourceMotionListenerTest");
        ds.addDragSourceListener(sourceAdapter);
        ds.addDragSourceMotionListener(sourceAdapter);
        ds.createDefaultDragGestureRecognizer(source, DnDConstants.ACTION_COPY, gestureListener);
        target.setDropTarget(dropTarget);

        frame.setLayout(new GridLayout(1, 2));

        frame.add(source);
        frame.add(target);

        Toolkit.getDefaultToolkit()
               .addAWTEventListener(this, AWTEvent.MOUSE_EVENT_MASK);
        frame.pack();
        frame.setVisible(true);
    }

    public static void main(String[] args) throws Exception {
        try {
            Robot robot = new Robot();
            robot.setAutoDelay(10);

            DragSourceMotionListenerTest dsmObj = new DragSourceMotionListenerTest();
            EventQueue.invokeAndWait(dsmObj::createAndShowUI);
            robot.waitForIdle();
            robot.delay(1000);

            EventQueue.invokeAndWait(() -> {
                srcPoint = getPoint(source, 1);

                dstOutsidePoint = getPoint(frame, 3);
                testPoint1.setLocation(dstOutsidePoint);

                dstInsidePoint = getPoint(target, 1);
                testPoint2.setLocation(dstInsidePoint);
            });
            robot.waitForIdle();

            if (!dsmObj.pointInComponent(robot, srcPoint, source)) {
                throw new RuntimeException("WARNING: Couldn't locate source panel.");
            }

            if (!dsmObj.pointInComponent(robot, dstInsidePoint, target)) {
                throw new RuntimeException("WARNING: Couldn't locate target panel.");
            }

            robot.mouseMove(srcPoint.x, srcPoint.y);
            robot.keyPress(KeyEvent.VK_CONTROL);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            for (; !srcPoint.equals(dstOutsidePoint);
                 srcPoint.translate(sign(dstOutsidePoint.x - srcPoint.x),
                                    sign(dstOutsidePoint.y - srcPoint.y))) {
                robot.mouseMove(srcPoint.x, srcPoint.y);
            }

            for (int i = 0; i < 10; i++) {
                robot.mouseMove(srcPoint.x, srcPoint.y++);
            }

            for (;!srcPoint.equals(dstInsidePoint);
                 srcPoint.translate(sign(dstInsidePoint.x - srcPoint.x),
                                    sign(dstInsidePoint.y - srcPoint.y))) {
                robot.mouseMove(srcPoint.x, srcPoint.y);
            }
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.keyRelease(KeyEvent.VK_CONTROL);
            robot.waitForIdle();
            robot.delay(1000);

            if (!passedTest1) {
                throw new RuntimeException("Failed first test.");
            }

            if (!passedTest2) {
                throw new RuntimeException("Failed second test.");
            }
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static Point getPoint(Container container, int multiple) {
        Point p = container.getLocationOnScreen();
        Dimension d = container.getSize();
        p.translate(multiple * d.width / 2, d.height / 2);
        return p;
    }

    public static int sign(int n) {
        return Integer.compare(n, 0);
    }

    public void eventDispatched(AWTEvent e) {
        if (e.getID() == MouseEvent.MOUSE_RELEASED) {
            clickedComponent = (Component)e.getSource();
            mouseReleaseEvent.countDown();
        }
    }

    boolean pointInComponent(Robot robot, Point p, Component comp) throws Exception {
        robot.waitForIdle();
        clickedComponent = null;
        mouseReleaseEvent = new CountDownLatch(1);
        robot.mouseMove(p.x, p.y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        if (!mouseReleaseEvent.await(2, TimeUnit.SECONDS)) {
            throw new RuntimeException("Mouse Release Event not received");
        }

        Component c = clickedComponent;
        while (c != null && c != comp) {
            c = c.getParent();
        }
        return c == comp;
    }
}
