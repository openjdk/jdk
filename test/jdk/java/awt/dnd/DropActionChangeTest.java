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

import javax.swing.JFrame;
import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Robot;
import java.awt.datatransfer.StringSelection;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceAdapter;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceListener;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/*
  @test
  @bug 4896462
  @summary tests that drop action is computed correctly
  @key headful
  @run main DropActionChangeTest
*/

public class DropActionChangeTest extends JFrame implements AWTEventListener {
    Robot robot;
    Frame frame;
    Panel panel;
    private volatile boolean failed;
    private volatile boolean dropEnd;
    private volatile Component clickedComponent;
    private final Object LOCK = new Object();
    static final int FRAME_ACTIVATION_TIMEOUT = 3000;
    static final int DROP_COMPLETION_TIMEOUT = 5000;
    static final int MOUSE_RELEASE_TIMEOUT = 2000;

    public static void main(String[] args) throws Exception {
        DropActionChangeTest test = new DropActionChangeTest();
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
        getToolkit().addAWTEventListener(this, AWTEvent.MOUSE_EVENT_MASK);

        setSize (200,200);
        setTitle("DropActionChangeTest");
        setVisible(true);
        validate();

        frame = new Frame("Empty Frame with Panel");
        panel = new Panel();
        frame.add(panel);
        frame.setBounds(300, 300, 300, 300);
        failed = false;

        final DragSourceListener dsl = new DragSourceAdapter() {
            public void dragDropEnd(DragSourceDropEvent e) {
                System.err.println("DragSourseListener.dragDropEnd(): " +
                        "drop action=" + e.getDropAction());
                if (e.getDropAction() != DnDConstants.ACTION_MOVE) {
                    System.err.println("FAILURE: wrong drop action:" + e.getDropAction());
                    failed = true;
                }
                synchronized (LOCK) {
                    dropEnd = true;
                    LOCK.notifyAll();
                }
            }
        };

        DragGestureListener dgl = new DragGestureListener() {
            public void dragGestureRecognized(DragGestureEvent dge) {
                dge.startDrag(null, new StringSelection("test"), dsl);
            }
        };

        new DragSource().createDefaultDragGestureRecognizer(panel,
                DnDConstants.ACTION_COPY_OR_MOVE, dgl);

        DropTargetListener dtl = new DropTargetAdapter() {
            public void dragEnter(DropTargetDragEvent e) {
                System.err.println("DropTargetListener.dragEnter(): " +
                        "user drop action=" + e.getDropAction());
                e.acceptDrag(e.getDropAction());
            }

            public void dragOver(DropTargetDragEvent e) {
                e.acceptDrag(e.getDropAction());
            }
            public void drop(DropTargetDropEvent e) {
                System.err.println("DropTargetListener.drop(): " +
                        "user drop action=" + e.getDropAction());
                e.acceptDrop(e.getDropAction());
                e.dropComplete(true);
            }
        };

        new DropTarget(panel, dtl);

        frame.setVisible(true);
    }

    public void start() {
        try {
            robot = new Robot();

            Point startPoint = panel.getLocationOnScreen();
            startPoint.translate(50, 50);

            if (!pointInComponent(robot, startPoint, panel)) {
                System.err.println("WARNING: Couldn't locate source panel");
                return;
            }


            Point medPoint = new Point(startPoint.x + (DragSource.getDragThreshold()+10)*2,
                                       startPoint.y);
            Point endPoint = new Point(startPoint.x + (DragSource.getDragThreshold()+10)*4,
                                       startPoint.y);

            synchronized (LOCK) {
                robot.keyPress(KeyEvent.VK_CONTROL);
                robot.mouseMove(startPoint.x, startPoint.y);
                robot.mousePress(InputEvent.BUTTON1_MASK);
                Util.doDrag(robot, startPoint, medPoint);
                robot.keyRelease(KeyEvent.VK_CONTROL);
                Util.doDrag(robot, medPoint, endPoint);
                robot.mouseRelease(InputEvent.BUTTON1_MASK);
                LOCK.wait(DROP_COMPLETION_TIMEOUT);
            }
            if (!dropEnd) {
                System.err.println("DragSourseListener.dragDropEnd() was not called, returning");
                return;
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }

        if (failed) {
            throw new RuntimeException("wrong drop action!");
        }

        System.err.println("test passed!");
    }

    public void reset() {
        clickedComponent = null;
    }

    public void eventDispatched(AWTEvent e) {
        if (e.getID() == MouseEvent.MOUSE_RELEASED) {
            clickedComponent = (Component)e.getSource();
            synchronized (LOCK) {
                LOCK.notifyAll();
            }
        }
    }

    boolean pointInComponent(Robot robot, Point p, Component comp)
      throws InterruptedException {
        robot.waitForIdle();
        reset();
        robot.mouseMove(p.x, p.y);
        robot.mousePress(InputEvent.BUTTON1_MASK);
        synchronized (LOCK) {
            robot.mouseRelease(InputEvent.BUTTON1_MASK);
            LOCK.wait(MOUSE_RELEASE_TIMEOUT);
        }

        Component c = clickedComponent;

        while (c != null && c != comp) {
            c = c.getParent();
        }

        return c == comp;
    }
}


class Util {

    public static int sign(int n) {
        return n < 0 ? -1 : n == 0 ? 0 : 1;
    }

    public static void doDrag(Robot robot, Point startPoint, Point endPoint) {
       for (Point p = new Point(startPoint); !p.equals(endPoint);
                p.translate(Util.sign(endPoint.x - p.x),
                            Util.sign(endPoint.y - p.y))) {
           robot.mouseMove(p.x, p.y);
           try {
               Thread.sleep(100);
           } catch (InterruptedException e) {
             e.printStackTrace();
           }
       }
    }
}
