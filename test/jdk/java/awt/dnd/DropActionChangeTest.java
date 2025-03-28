/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.dnd.DnDConstants;
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
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.JFrame;

/*
  @test
  @bug 4896462
  @summary tests that drop action is computed correctly
  @key headful
  @run main DropActionChangeTest
*/

public class DropActionChangeTest extends JFrame implements AWTEventListener {
    private static Robot robot;
    private static Frame frame;
    private static volatile DropActionChangeTest test;
    Panel panel;
    private volatile boolean failed;
    private volatile boolean dropEnd;
    private volatile Component clickedComponent;
    private final Object LOCK = new Object();
    static final int DROP_COMPLETION_TIMEOUT = 8000;
    static final int MOUSE_RELEASE_TIMEOUT = 2000;

    public static void main(String[] args) throws Exception {
        EventQueue.invokeAndWait(() -> test = new DropActionChangeTest());
        EventQueue.invokeAndWait(test::init);
        try {
            test.start();
        } finally {
            EventQueue.invokeAndWait(DropActionChangeTest::disposeFrame);
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
                System.err.println("DragSourceListener.dragDropEnd(): " +
                        "drop action=" + e.getDropAction());
                if (e.getDropAction() != DnDConstants.ACTION_MOVE) {
                    System.err.println("FAILURE: wrong drop action:"
                                       + e.getDropAction() + ", It should be "
                                       + DnDConstants.ACTION_MOVE);
                    failed = true;
                }
                synchronized (LOCK) {
                    dropEnd = true;
                    LOCK.notifyAll();
                }
            }
        };

        DragGestureListener dgl = dge -> dge.startDrag(null, new StringSelection("test"), dsl);

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

    private static void disposeFrame() {
        if (frame != null) {
            frame.dispose();
        }
        if (test != null) {
            test.dispose();
        }
    }

    public void start() {
        try {
            robot = new Robot();
            robot.setAutoDelay(100);
            robot.waitForIdle();
            robot.delay(500);

            AtomicReference<Point> startPointRef = new AtomicReference<>();
            EventQueue.invokeAndWait(()-> startPointRef.set(panel.getLocationOnScreen()));
            Point startPoint = startPointRef.get();
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
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                Util.doDrag(robot, startPoint, medPoint);
                robot.delay(500);
                robot.keyRelease(KeyEvent.VK_CONTROL);
                Util.doDrag(robot, medPoint, endPoint);
                robot.delay(500);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                LOCK.wait(DROP_COMPLETION_TIMEOUT);
            }
            if (!dropEnd) {
                captureScreen("No_Drop_End_");
                System.err.println("DragSourceListener.dragDropEnd() was not called, returning");
                return;
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }

        if (failed) {
            captureScreen("Wrong_Drop_Action_");
            throw new RuntimeException("Wrong drop action!");
        }

        System.err.println("Test passed!");
    }

    private static void captureScreen(String str) {
        try {
            final Rectangle screenBounds = new Rectangle(
                    Toolkit.getDefaultToolkit().getScreenSize());
            ImageIO.write(robot.createScreenCapture(screenBounds), "png",
                          new File(str + "Failure_Screen.png"));
        } catch (Exception e) {
            e.printStackTrace();
        }
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
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        synchronized (LOCK) {
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
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
       robot.waitForIdle();
       for (Point p = new Point(startPoint); !p.equals(endPoint);
                p.translate(Util.sign(endPoint.x - p.x),
                            Util.sign(endPoint.y - p.y))) {
           robot.mouseMove(p.x, p.y);
       }
    }
}