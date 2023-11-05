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
  @bug 4874092
  @summary tests that DragSourceListener.dragExit() is not called if the mouse
           is not dragged over any drop site
  @key headful
  @run main NoTargetNoDragExitTest
*/

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceAdapter;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceEvent;
import java.awt.dnd.DragSourceListener;
import java.awt.event.AWTEventListener;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;


public class NoTargetNoDragExitTest {
    private volatile boolean failed;
    private volatile boolean end;
    private final Object LOCK = new Object();
    private Frame frame;
    private Panel panel;

    public static void main(String[] args) throws Exception {
        NoTargetNoDragExitTest noTargetNoDragExitTest = new NoTargetNoDragExitTest();
        EventQueue.invokeAndWait(noTargetNoDragExitTest::init);
        noTargetNoDragExitTest.start();
    }

    public void init() {
        frame = new Frame("NoTargetNoDragExitTest");
        panel = new Panel();
        frame.add(panel);
        frame.setSize(300, 300);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        frame.validate();
    }

    public void start() throws Exception {
        try {
            Robot robot = new Robot();
            robot.waitForIdle();
            robot.delay(1000);

            final DragSourceListener dsl = new DragSourceAdapter() {
                public void dragExit(DragSourceEvent e) {
                    failed = true;
                    System.err.println("FAILURE: DragSourceListener.dragExit() called!");
                }
                public void dragDropEnd(DragSourceDropEvent e) {
                    System.err.println("DragSourceListener.dragDropEnd()");
                    synchronized (LOCK) {
                        end = true;
                        LOCK.notifyAll();
                    }
                }
            };

            DragGestureListener dgl = dge ->
                    dge.startDrag(null, new StringSelection("NoTargetNoDragExitTest"), dsl);

            new DragSource().createDefaultDragGestureRecognizer(panel,
                    DnDConstants.ACTION_COPY_OR_MOVE, dgl);

            Point startPoint = frame.getLocationOnScreen();
            startPoint.translate(50, 50);
            Point endPoint = new Point(startPoint.x + 100, startPoint.y + 100);

            Util.waitForInit();

            if (!Util.pointInComponent(robot, startPoint, frame)) {
                System.err.println("WARNING: Could not locate " + frame +
                        " at point " + startPoint);
                return;
            }

            robot.mouseMove(startPoint.x, startPoint.y);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            for (Point p = new Point(startPoint); !p.equals(endPoint);
                 p.translate(Util.sign(endPoint.x - p.x),
                         Util.sign(endPoint.y - p.y))) {
                robot.mouseMove(p.x, p.y);
                robot.delay(100);
            }
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

            synchronized (LOCK) {
                while (!end) {
                    LOCK.wait();
                }
            }
        } finally {
            if (frame != null) {
                EventQueue.invokeAndWait(() -> frame.dispose());
            }
        }

        if (failed) {
            throw new RuntimeException("DragSourceListener.dragExit() called!");
        }

        System.err.println("test passed!");
     }
}


class Util implements AWTEventListener {
    private static final Toolkit tk = Toolkit.getDefaultToolkit();
    private static final Object SYNC_LOCK = new Object();
    private Component clickedComponent = null;
    private static final int PAINT_TIMEOUT = 10000;
    private static final int MOUSE_RELEASE_TIMEOUT = 10000;
    private static final Util util = new Util();

    static {
        tk.addAWTEventListener(util, 0xFFFFFFFF);
    }

    private void reset() {
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

    public static boolean pointInComponent(Robot robot, Point p, Component comp)
      throws InterruptedException {
        return util.isPointInComponent(robot, p, comp);
    }

    private boolean isPointInComponent(Robot robot, Point p, Component comp)
      throws InterruptedException {
        tk.sync();
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

    public static void waitForInit() throws InterruptedException {
        final Frame f = new Frame() {
                public void paint(Graphics g) {
                    dispose();
                    synchronized (SYNC_LOCK) {
                        SYNC_LOCK.notifyAll();
                    }
                }
            };
        f.setBounds(600, 400, 200, 200);
        synchronized (SYNC_LOCK) {
            f.setVisible(true);
            SYNC_LOCK.wait(PAINT_TIMEOUT);
        }
        tk.sync();
    }

    public static int sign(int n) {
        return n < 0 ? -1 : n == 0 ? 0 : 1;
    }
}