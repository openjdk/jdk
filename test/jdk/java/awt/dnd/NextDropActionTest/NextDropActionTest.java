/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @bug 4887150
  @summary tests that after performing COPY drop, MOVE drop can be performed too
  @key headful
  @run main NextDropActionTest
*/

import java.awt.AWTException;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Robot;
import java.awt.datatransfer.StringSelection;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceAdapter;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceListener;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.InvocationTargetException;


public class NextDropActionTest {
    private final long WAIT_TIMEOUT = 30000;
    private volatile boolean failed;
    private volatile boolean firstEnd;
    private volatile boolean secondEnd;
    private final Object LOCK = new Object();
    private Frame frame;
    private Panel panel;

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException, AWTException {
        NextDropActionTest nextDropActionTest = new NextDropActionTest();
        nextDropActionTest.start();
    }

    public void start() throws InterruptedException,
            InvocationTargetException, AWTException {

        EventQueue.invokeAndWait(() -> {
            panel = new Panel();
            frame = new Frame("NextDropActionTest");
            frame.add(panel);
            frame.setBounds(300, 300, 300, 300);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            frame.validate();
        });

        try {
            Robot robot = new Robot();
            robot.waitForIdle();
            robot.delay(1000);

            final DragSourceListener dsl = new DragSourceAdapter() {
                boolean firstCall = true;
                public void dragDropEnd(DragSourceDropEvent e) {
                    System.err.println("DragSourseListener.dragDropEnd(): " +
                            " firstCall=" + firstCall +
                            " drop action=" + e.getDropAction());
                    if (firstCall) {
                        firstCall = false;
                        synchronized (LOCK) {
                            firstEnd = true;
                            LOCK.notifyAll();
                        }
                        return;
                    }
                    if (e.getDropAction() != DnDConstants.ACTION_MOVE) {
                        System.err.println("FAILURE: wrong drop action:"
                                + e.getDropAction());
                        failed = true;
                    }
                    synchronized (LOCK) {
                        secondEnd = true;
                        LOCK.notifyAll();
                    }
                }
            };

            DragGestureListener dgl = dge ->
                    dge.startDrag(null, new StringSelection("test"), dsl);

            new DragSource().createDefaultDragGestureRecognizer(panel,
                    DnDConstants.ACTION_COPY_OR_MOVE, dgl);

            DropTargetListener dtl = new DropTargetAdapter() {
                public void drop(DropTargetDropEvent e) {
                    System.err.println("DropTargetListener.drop(): " +
                            "accepting the user drop action=" + e.getDropAction());
                    e.acceptDrop(e.getDropAction());
                    e.dropComplete(true);
                }
            };

            new DropTarget(frame, DnDConstants.ACTION_COPY_OR_MOVE, dtl);

            Point startPoint = new Point(Util.blockTillDisplayed(panel));
            startPoint.translate(50, 50);
            Point endPoint = new Point(startPoint.x
                    + DragSource.getDragThreshold() + 10,
                    startPoint.y + DragSource.getDragThreshold() + 10);

            synchronized (LOCK) {
                robot.keyPress(KeyEvent.VK_CONTROL);
                Util.doDragDrop(robot, startPoint, endPoint);
                robot.keyRelease(KeyEvent.VK_CONTROL);
                LOCK.wait(WAIT_TIMEOUT);
            }
            if (!firstEnd) {
                System.err.println("DragSourseListener.dragDropEnd() " +
                        "was not called, returning");
                return;
            }

            synchronized (LOCK) {
                Util.doDragDrop(robot, startPoint, endPoint);
                LOCK.wait(WAIT_TIMEOUT);
            }
            if (!secondEnd) {
                System.err.println("DragSourseListener.dragDropEnd() " +
                        "was not called, returning");
                return;
            }
        } finally {
            if (frame != null) {
                EventQueue.invokeAndWait(() -> frame.dispose());
            }
        }

        if (failed) {
            throw new RuntimeException("wrong next drop action!");
        }

        System.err.println("test passed!");
     }
}

class Util {
    public static int sign(int n) {
        return n < 0 ? -1 : n == 0 ? 0 : 1;
    }

    public static void doDragDrop(Robot robot, Point startPoint, Point endPoint) {
       robot.mouseMove(startPoint.x, startPoint.y);
       robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
       for (Point p = new Point(startPoint); !p.equals(endPoint);
                p.translate(Util.sign(endPoint.x - p.x),
                            Util.sign(endPoint.y - p.y))) {
           robot.mouseMove(p.x, p.y);
           robot.delay(100);
       }
       robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    public static Point blockTillDisplayed(Component comp) {
        Point p = null;
        while (p == null) {
            try {
                p = comp.getLocationOnScreen();
            } catch (IllegalStateException e) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                }
            }
        }
        return p;
    }
}