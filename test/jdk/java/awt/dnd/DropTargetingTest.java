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

import javax.swing.JButton;
import javax.swing.JFrame;
import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Robot;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceAdapter;
import java.awt.dnd.DragSourceListener;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetContext;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.AWTEventListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

/*
  @test
  @bug 4426794 4435403
  @summary tests that drag notifications are targeted to the drop target
           whose operable part is currently intersected by cursor's hotspot
  @key headful
  @run main DropTargetingTest
*/

public class DropTargetingTest implements AWTEventListener {

    volatile JFrame sourceFrame;
    volatile JFrame targetFrame1;
    volatile JFrame targetFrame2;
    volatile JButton obscurer;

    volatile DragSource dragSource;
    volatile Transferable transferable;
    volatile DragSourceListener dragSourceListener;
    volatile DragGestureListener dragGestureListener;
    volatile Point srcPoint;
    volatile Point dstPoint;
    volatile Dimension d;

    static class TestDropTargetListener extends DropTargetAdapter {
        private boolean dropRecognized = false;
        public void drop(DropTargetDropEvent dtde) {
            dropRecognized = true;
            dtde.rejectDrop();
            synchronized (SYNC_LOCK) {
                SYNC_LOCK.notifyAll();
            }
        }
        public void reset() {
            dropRecognized = false;
        }
        public boolean dropRecognized() {
            return dropRecognized;
        }
    }
    volatile TestDropTargetListener dropTargetListener;

    static final Object SYNC_LOCK = new Object();
    static final int FRAME_ACTIVATION_TIMEOUT = 2000;
    static final int DROP_COMPLETION_TIMEOUT = 5000;
    static final int MOUSE_RELEASE_TIMEOUT = 1000;

    Component clickedComponent = null;

    public static void main(String[] args) throws Exception {
        DropTargetingTest test = new DropTargetingTest();
        EventQueue.invokeAndWait(test::init);
        try {
            test.start();
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (test.sourceFrame != null) {
                    test.sourceFrame.dispose();
                }
                if (test.targetFrame1 != null) {
                    test.targetFrame1.dispose();
                }
                if (test.targetFrame2 != null) {
                    test.targetFrame2.dispose();
                }
            });
        }
    }

    public void init() {
        sourceFrame = new JFrame();
        targetFrame1 = new JFrame();
        targetFrame2 = new JFrame();
        obscurer = new JButton("Obscurer");

        dragSource = DragSource.getDefaultDragSource();
        transferable = new StringSelection("TEXT");
        dragSourceListener = new DragSourceAdapter() {};
        dragGestureListener = new DragGestureListener() {
            public void dragGestureRecognized(DragGestureEvent dge) {
                dge.startDrag(null, transferable, dragSourceListener);
            }
        };
        dropTargetListener = new TestDropTargetListener();

        sourceFrame.setTitle("DropTargetingTest Source frame");
        sourceFrame.setBounds(100, 100, 100, 100);
        sourceFrame.getToolkit().addAWTEventListener(this, AWTEvent.MOUSE_EVENT_MASK);
        dragSource.createDefaultDragGestureRecognizer(sourceFrame, DnDConstants.ACTION_COPY,
                                                      dragGestureListener);
        targetFrame1.setTitle("Target frame 1");
        targetFrame1.setBounds(200, 100, 100, 100);
        targetFrame1.getGlassPane().setVisible(true);
        targetFrame1.getGlassPane().setDropTarget(
            new DropTarget(targetFrame1.getGlassPane(), dropTargetListener));
        targetFrame2.setTitle("Target frame 2");
        targetFrame2.setBounds(300, 100, 100, 100);
        targetFrame2.setDropTarget(new DropTarget(targetFrame1, dropTargetListener));
        targetFrame2.getContentPane().add(obscurer);

        sourceFrame.setVisible(true);
        targetFrame1.setVisible(true);
        targetFrame2.setVisible(true);
    }

    public static int sign(int n) {
        return n < 0 ? -1 : n == 0 ? 0 : 1;
    }

    public void start() throws Exception {
        Robot robot = new Robot();
        robot.delay(FRAME_ACTIVATION_TIMEOUT);

        if (!test(robot, targetFrame1)) {
            throw new RuntimeException("Failed to recognize drop on a glass pane");
        }

        if (!test(robot, targetFrame2)) {
            throw new RuntimeException("Failed to recognize drop on a composite component");
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

    boolean test(Robot robot, JFrame targetFrame) throws Exception {
        EventQueue.invokeAndWait(() -> {
            srcPoint = sourceFrame.getLocationOnScreen();
            d = sourceFrame.getSize();
        });
        srcPoint.translate(d.width / 2, d.height / 2);

        if (!pointInComponent(robot, srcPoint, sourceFrame)) {
            System.err.println("WARNING: Couldn't locate source frame.");
            return true;
        }
        EventQueue.invokeAndWait(() -> {
            dstPoint = targetFrame.getLocationOnScreen();
            d = targetFrame.getSize();
        });
        dstPoint.translate(d.width / 2, d.height / 2);

        if (!pointInComponent(robot, dstPoint, targetFrame)) {
            System.err.println("WARNING: Couldn't locate target frame: " + targetFrame);
            return true;
        }

        dropTargetListener.reset();
        robot.mouseMove(srcPoint.x, srcPoint.y);
        robot.keyPress(KeyEvent.VK_CONTROL);
        robot.mousePress(InputEvent.BUTTON1_MASK);
        for (;!srcPoint.equals(dstPoint);
             srcPoint.translate(sign(dstPoint.x - srcPoint.x),
                                sign(dstPoint.y - srcPoint.y))) {
            robot.mouseMove(srcPoint.x, srcPoint.y);
            robot.delay(10);
        }
        synchronized (SYNC_LOCK) {
            robot.mouseRelease(InputEvent.BUTTON1_MASK);
            robot.keyRelease(KeyEvent.VK_CONTROL);
            SYNC_LOCK.wait(DROP_COMPLETION_TIMEOUT);
        }

        return dropTargetListener.dropRecognized();
    }
}

class DropTargetPanel extends Panel implements DropTargetListener {

    final Dimension preferredDimension = new Dimension(200, 100);
    boolean testPassed = true;

    public DropTargetPanel() {
        setDropTarget(new DropTarget(this, this));
    }

    public boolean getStatus() {
        return testPassed;
    }

    public Dimension getPreferredSize() {
        return preferredDimension;
    }

    public void dragEnter(DropTargetDragEvent dtde) {}

    public void dragExit(DropTargetEvent dte) {
        testPassed = false;
    }

    public void dragOver(DropTargetDragEvent dtde) {}

    public void dropActionChanged(DropTargetDragEvent dtde) {}

    public void drop(DropTargetDropEvent dtde) {
        DropTargetContext dtc = dtde.getDropTargetContext();

        if ((dtde.getSourceActions() & DnDConstants.ACTION_COPY) != 0) {
            dtde.acceptDrop(DnDConstants.ACTION_COPY);
        } else {
            dtde.rejectDrop();
        }

        DataFlavor[] dfs = dtde.getCurrentDataFlavors();
        Component comp = null;

        if (dfs != null && dfs.length >= 1) {
            Transferable transfer = dtde.getTransferable();

            try {
                comp = (Component)transfer.getTransferData(dfs[0]);
            } catch (Throwable e) {
                e.printStackTrace();
                dtc.dropComplete(false);
            }
        }
        dtc.dropComplete(true);

        add(comp);
    }
}
