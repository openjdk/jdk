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

/*
  @test
  @key headful
  @bug 4658741
  @summary verifies that getDropSuccess() returns correct value for inter-JVM DnD
  @run main InterJVMGetDropSuccessTest
*/

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Toolkit;
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
import java.io.File;
import java.io.InputStream;

public class InterJVMGetDropSuccessTest {

    private int returnCode = Util.CODE_NOT_RETURNED;
    private final boolean[] successCodes = { true, false };
    private int dropCount = 0;

    final Frame frame = new Frame("Target Frame");

    final DropTargetListener dropTargetListener = new DropTargetAdapter() {
            public void drop(DropTargetDropEvent dtde) {
                dtde.acceptDrop(DnDConstants.ACTION_COPY);
                dtde.dropComplete(successCodes[dropCount]);
                dropCount++;
            }
        };
    final DropTarget dropTarget = new DropTarget(frame, dropTargetListener);

    public static void main(final String[] args) {
        InterJVMGetDropSuccessTest app = new InterJVMGetDropSuccessTest();
        app.init();
        app.start();
    }

    public void init() {
        frame.setTitle("Test frame");
        frame.setBounds(100, 100, 150, 150);
    } // init()

    public void start() {

        frame.setVisible(true);

        try {
            Robot robot = new Robot();
            robot.waitForIdle();
            robot.delay(Util.FRAME_ACTIVATION_TIMEOUT);

            Point p = frame.getLocationOnScreen();
            Dimension d = frame.getSize();

            String javaPath = System.getProperty("java.home", "");
            String command = javaPath + File.separator + "bin" +
                File.separator + "java -cp " + System.getProperty("test.classes", ".") +
                " Child " +
                p.x + " " + p.y + " " + d.width + " " + d.height;

            Process process = Runtime.getRuntime().exec(command);
            returnCode = process.waitFor();

            InputStream errorStream = process.getErrorStream();
            int count = errorStream.available();
            if (count > 0) {
                byte[] b = new byte[count];
                errorStream.read(b);
                System.err.println("========= Child VM System.err ========");
                System.err.print(new String(b));
                System.err.println("======================================");
            }

            InputStream outputStream = process.getInputStream();
            count = outputStream.available();
            if (count > 0) {
                byte[] b = new byte[count];
                outputStream.read(b);
                System.err.println("========= Child VM System.out ========");
                System.err.print(new String(b));
                System.err.println("======================================");
            }
        } catch (Throwable e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        switch (returnCode) {
        case Util.CODE_NOT_RETURNED:
            throw new RuntimeException("Child VM: failed to start");
        case Util.CODE_FAILURE:
            throw new RuntimeException("Child VM: abnormal termination");
        default:
            if (dropCount == 2) {
                int expectedRetCode = 0;
                if (successCodes[0]) {
                    expectedRetCode |= Util.CODE_FIRST_SUCCESS;
                }
                if (successCodes[1]) {
                    expectedRetCode |= Util.CODE_SECOND_SUCCESS;
                }
                if (expectedRetCode != returnCode) {
                    throw new RuntimeException("The test failed. Expected:" +
                                               expectedRetCode + ". Returned:" +
                                               returnCode);
                }
            }
            break;
        }
    } // start()
} // class InterJVMGetDropSuccessTest

final class Util implements AWTEventListener {
    public static final int CODE_NOT_RETURNED = -1;
    public static final int CODE_FIRST_SUCCESS = 0x2;
    public static final int CODE_SECOND_SUCCESS = 0x2;
    public static final int CODE_FAILURE = 0x1;

    public static final int FRAME_ACTIVATION_TIMEOUT = 1000;

    static final Object SYNC_LOCK = new Object();

    static final Util theInstance = new Util();

    static {
        Toolkit.getDefaultToolkit().addAWTEventListener(theInstance, AWTEvent.MOUSE_EVENT_MASK);
    }

    public static Point getCenterLocationOnScreen(Component c) {
        Point p = c.getLocationOnScreen();
        Dimension d = c.getSize();
        p.translate(d.width / 2, d.height / 2);
        return p;
    }

    public static int sign(int n) {
        return n < 0 ? -1 : n == 0 ? 0 : 1;
    }

    public void eventDispatched(AWTEvent e) {
        if (e.getID() == MouseEvent.MOUSE_RELEASED) {
            synchronized (SYNC_LOCK) {
                SYNC_LOCK.notifyAll();
            }
        }
    }
}

class Child {
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
            synchronized (Util.SYNC_LOCK) {
                Util.SYNC_LOCK.notifyAll();
            }
        }
    }

    private volatile boolean success1 = false;
    private volatile boolean success2 = false;

    final Frame frame = new Frame("Source Frame");
    final DragSource dragSource = DragSource.getDefaultDragSource();
    final DragSourceDropListener dragSourceListener = new DragSourceDropListener();
    final Transferable transferable = new StringSelection("TEXT");
    final DragGestureListener dragGestureListener = new DragGestureListener() {
            public void dragGestureRecognized(DragGestureEvent dge) {
                dge.startDrag(null, transferable, dragSourceListener);
            }
        };
    final DragGestureRecognizer dragGestureRecognizer =
        dragSource.createDefaultDragGestureRecognizer(frame, DnDConstants.ACTION_COPY,
                                                      dragGestureListener);

    public static void main(String[] args) {
        Child child = new Child();
        child.run(args);
    }

    public void run(String[] args) {
        try {
            if (args.length != 4) {
                throw new RuntimeException("Incorrect command line arguments.");
            }

            int x = Integer.parseInt(args[0]);
            int y = Integer.parseInt(args[1]);
            int w = Integer.parseInt(args[2]);
            int h = Integer.parseInt(args[3]);

            frame.setBounds(300, 200, 150, 150);
            frame.setVisible(true);

            Robot robot = new Robot();
            robot.waitForIdle();
            robot.delay(Util.FRAME_ACTIVATION_TIMEOUT);

            Point sourcePoint = Util.getCenterLocationOnScreen(frame);
            Point targetPoint = new Point(x + w / 2, y + h / 2);

            robot.mouseMove(sourcePoint.x, sourcePoint.y);
            robot.waitForIdle();
            robot.delay(50);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            for (Point p = new Point(sourcePoint); !p.equals(targetPoint);
                p.translate(Util.sign(targetPoint.x - p.x),
                            Util.sign(targetPoint.y - p.y))) {
                robot.mouseMove(p.x, p.y);
                robot.delay(5);
            }

            synchronized (Util.SYNC_LOCK) {
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                Util.SYNC_LOCK.wait(Util.FRAME_ACTIVATION_TIMEOUT);
            }

            EventQueue.invokeAndWait(() -> {
                if (!dragSourceListener.isDropFinished()) {
                    throw new RuntimeException("Drop not finished");
                }
                success1 = dragSourceListener.getDropSuccess();
                dragSourceListener.reset();
            });


            robot.mouseMove(sourcePoint.x, sourcePoint.y);
            robot.waitForIdle();
            robot.delay(50);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            for (Point p = new Point(sourcePoint); !p.equals(targetPoint);
                p.translate(Util.sign(targetPoint.x - p.x),
                            Util.sign(targetPoint.y - p.y))) {
                robot.mouseMove(p.x, p.y);
                robot.delay(5);
            }

            synchronized (Util.SYNC_LOCK) {
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                Util.SYNC_LOCK.wait(Util.FRAME_ACTIVATION_TIMEOUT);
            }

            EventQueue.invokeAndWait(() -> {
                if (!dragSourceListener.isDropFinished()) {
                    throw new RuntimeException("Drop not finished");
                }
                success2 = dragSourceListener.getDropSuccess();
                dragSourceListener.reset();
            });

            int retCode = 0;

            if (success1) {
                retCode |= Util.CODE_FIRST_SUCCESS;
            }
            if (success2) {
                retCode |= Util.CODE_SECOND_SUCCESS;
            }
            // This returns the diagnostic code from the child VM
            System.exit(retCode);
        } catch (Throwable e) {
            e.printStackTrace();
            // This returns the diagnostic code from the child VM
            System.exit(Util.CODE_FAILURE);
        }
    } // run()
} // class child
