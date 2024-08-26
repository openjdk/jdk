/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceAdapter;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceListener;
import java.awt.event.AWTEventListener;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseMotionListener;
import javax.swing.SwingUtilities;

/*
 * @test
 * @bug 4613903
 * @summary verifies that mouse events are not dispatched during drag
 * @key headful
 * @run main MouseEventAfterStartDragTest
 */

public final class MouseEventAfterStartDragTest implements AWTEventListener {
    final Frame frame = new Frame();
    volatile Point srcPoint;
    volatile Dimension d;
    volatile MouseEvent lastMouseEvent = null;
    volatile boolean passed = true;
    final DragSource dragSource = DragSource.getDefaultDragSource();
    final Transferable transferable = new StringSelection("TEXT");

    final MouseMotionListener mouseMotionListener = new MouseMotionAdapter() {
        public void mouseDragged(MouseEvent e) {
            System.out.println("mouseDragged: " + e
                    + ", hash:" + e.hashCode());
            if (lastMouseEvent != null && !e.equals(lastMouseEvent)) {
                System.out.println("Unexpected: " + e
                        + ", hash:" + e.hashCode());
                passed = false;
            }
        }
    };

    final DragSourceListener dragSourceListener = new DragSourceAdapter() {
        public void dragDropEnd(DragSourceDragEvent dsde) {
            System.out.println("dragDropEnd: " + dsde);
            lastMouseEvent = null;
        }
    };

    final DragGestureListener dragGestureListener = new DragGestureListener() {
        public void dragGestureRecognized(DragGestureEvent dge) {
            System.out.println("dragGestureRecognized: " + dge);
            Object[] events = dge.toArray();
            Object lastEvent = events[events.length - 1];
            if (lastEvent instanceof MouseEvent) {
                lastMouseEvent = (MouseEvent) lastEvent;
            }
            System.out.println("The last mouse event: " + lastMouseEvent
                    + ", hash:" + lastMouseEvent.hashCode());
            dge.startDrag(null, transferable, dragSourceListener);
        }
    };

    static final Object SYNC_LOCK = new Object();
    static final int MOUSE_RELEASE_TIMEOUT = 1000;
    volatile Component clickedComponent = null;

    public static void main(String[] args) throws Exception {
        System.setProperty("awt.dnd.drag.threshold", "0");
        MouseEventAfterStartDragTest app = new MouseEventAfterStartDragTest();
        try {
            app.createAndShowGUI();
            app.test();
        } finally {
            app.dispose();
        }
    }

    public void createAndShowGUI() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            frame.setTitle("Test frame");
            frame.setBounds(100, 100, 200, 200);
            frame.setLocationRelativeTo(null);
            frame.addMouseMotionListener(mouseMotionListener);
            dragSource.createDefaultDragGestureRecognizer(frame, DnDConstants.ACTION_COPY_OR_MOVE,
                    dragGestureListener);

            frame.getToolkit().addAWTEventListener(this, AWTEvent.MOUSE_EVENT_MASK);
            frame.setVisible(true);
        });
    }

    public static int sign(int n) {
        return n < 0 ? -1 : n == 0 ? 0 : 1;
    }

    public void test() throws Exception {
        final Robot robot = new Robot();
        robot.setAutoDelay(45);
        robot.waitForIdle();

        SwingUtilities.invokeAndWait(() -> {
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

        if (!pointInComponent(robot, dstPoint, frame)) {
            System.err.println("WARNING: Couldn't locate target frame.");
            return;
        }

        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseMove(srcPoint.x, srcPoint.y);
        robot.delay(250);
        System.out.println("srcPoint = " + srcPoint);
        for (; !srcPoint.equals(dstPoint);
                srcPoint.translate(sign(dstPoint.x - srcPoint.x),
                sign(dstPoint.y - srcPoint.y))) {
            robot.mouseMove(srcPoint.x, srcPoint.y);
            System.out.println("srcPoint = " + srcPoint);
        }

        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        System.out.println("done");
        robot.waitForIdle();
        robot.delay(MOUSE_RELEASE_TIMEOUT);

        if (!passed) {
            throw new RuntimeException("Test failed");
        }
    }

    public void dispose() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            if (frame != null) {
                frame.dispose();
            }
        });
    }

    public void reset() {
        clickedComponent = null;
    }

    public void eventDispatched(AWTEvent e) {
        if (e.getID() == MouseEvent.MOUSE_RELEASED) {
            clickedComponent = (Component) e.getSource();
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
