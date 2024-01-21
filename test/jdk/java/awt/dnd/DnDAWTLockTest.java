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
  @bug 4913349
  @summary verifies that AWT_LOCK is properly taken during DnD
  @key headful
  @run main DnDAWTLockTest
*/

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragGestureRecognizer;
import java.awt.dnd.DragSource;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.AWTEventListener;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.InputStream;
import java.util.StringTokenizer;


public class DnDAWTLockTest implements ClipboardOwner {
    public static final int STARTUP_TIMEOUT = 2000;
    volatile Frame frame;

    static final Clipboard clipboard =
            Toolkit.getDefaultToolkit().getSystemClipboard();

    volatile Process process = null;
    volatile Point sourcePoint = null;

    public static void main(String[] args) throws Exception {
        DnDAWTLockTest test = new DnDAWTLockTest();
        EventQueue.invokeAndWait(test::init);
        try {
            test.start();
        } finally {
            EventQueue.invokeAndWait(() -> test.frame.dispose());
        }
    }

    public void init() {
        frame = new Frame("Drop target frame");
        frame.setLocation(200, 200);
        Panel panel = new DragSourcePanel();
        frame.add(panel);
        frame.pack();
        frame.setVisible(true);
    }

    public void start() throws Exception {
        String stderr = null;

        Robot robot = new Robot();
        robot.waitForIdle();
        robot.delay(1000);

        Point p = frame.getLocationOnScreen();
        Dimension d = frame.getSize();

        Point pp = new Point(p);
        pp.translate(d.width / 2, d.height / 2);

        if (!Util.pointInComponent(robot, pp, frame)) {
            System.err.println("WARNING: Couldn't locate " + frame +
                    " at point " + pp);
            return;
        }

        sourcePoint = pp;
        clipboard.setContents(new StringSelection(Util.TRANSFER_DATA),
                this);

        String javaPath = System.getProperty("java.home", "");
        String[] command = {
                javaPath + File.separator + "bin" + File.separator + "java",
                "-cp", System.getProperty("test.classes", "."),
                "Child"
        };

        process = Runtime.getRuntime().exec(command);
        ProcessResults pres = ProcessResults.doWaitFor(process);

        stderr = pres.stderr;

        if (pres.stderr != null && pres.stderr.length() > 0) {
            System.err.println("========= Child VM System.err ========");
            System.err.print(pres.stderr);
            System.err.println("======================================");
        }

        if (pres.stdout != null && pres.stdout.length() > 0) {
            System.err.println("========= Child VM System.out ========");
            System.err.print(pres.stdout);
            System.err.println("======================================");
        }

        System.err.println("Child VM return code: " + pres.exitValue);

        if (stderr != null && stderr.contains("InternalError")) {
            throw new RuntimeException("Test failed");
        }
    }

    public void lostOwnership(Clipboard c, Transferable trans) {
        Runnable r = new Runnable() {
            public void run() {
                try {
                    if (process == null) {
                        throw new RuntimeException("Null process");
                    }

                    if (sourcePoint == null) {
                        throw new RuntimeException("Null point");
                    }

                    Thread.sleep(STARTUP_TIMEOUT);
                    Transferable t = clipboard.getContents(null);

                    String s =
                            (String) t.getTransferData(DataFlavor.stringFlavor);
                    StringTokenizer st = new StringTokenizer(s);

                    int x = Integer.parseInt(st.nextToken());
                    int y = Integer.parseInt(st.nextToken());

                    Point targetPoint = new Point(x, y);

                    Robot robot = new Robot();

                    robot.mouseMove(sourcePoint.x, sourcePoint.y);
                    robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                    for (; !sourcePoint.equals(targetPoint);
                         sourcePoint.translate(
                                 sign(targetPoint.x - sourcePoint.x),
                                 sign(targetPoint.y - sourcePoint.y)
                         )) {
                        robot.mouseMove(sourcePoint.x, sourcePoint.y);
                        robot.delay(25);
                    }
                    robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                } catch (Exception e) {
                    e.printStackTrace();
                    process.destroy();
                }
            }
        };
        new Thread(r).start();
    }

    public static int sign(int n) {
        return Integer.compare(n, 0);
    }
}

class Child {
    public static final int ACTION_TIMEOUT = 30000;

    volatile Frame frame;
    volatile Panel panel;


    public void init() {
        panel = new DropTargetPanel();

        frame = new Frame("Drag source frame");
        frame.setLocation(500, 200);
        frame.add(panel);
        frame.pack();
        frame.setVisible(true);
    }

    public void run() {
        try {
            Robot robot = new Robot();
            robot.waitForIdle();
            robot.delay(1000);

            Point targetPoint = panel.getLocationOnScreen();
            Dimension d = panel.getSize();
            targetPoint.translate(d.width / 2, d.height / 2);

            if (!Util.pointInComponent(robot, targetPoint, panel)) {
                System.err.println("WARNING: Couldn't locate " + panel +
                        " at point " + targetPoint);
                System.exit(0);
            }

            String positionData = "" + targetPoint.x + " " + targetPoint.y;
            DnDAWTLockTest.clipboard.setContents(
                    new StringSelection(positionData), null);

            Thread.sleep(ACTION_TIMEOUT);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        System.exit(0);
    }

    public static void main(String[] args) throws Exception {
        Child child = new Child();
        EventQueue.invokeAndWait(child::init);
        try {
            child.run();
        } finally {
            EventQueue.invokeAndWait(() -> child.frame.dispose());
        }
    }
}

class Util implements AWTEventListener {
    private static final Toolkit tk = Toolkit.getDefaultToolkit();
    public static final Object SYNC_LOCK = new Object();
    private Component clickedComponent = null;
    private static final int PAINT_TIMEOUT = 10000;
    private static final int MOUSE_RELEASE_TIMEOUT = 10000;
    private static final Util util = new Util();
    public static final String TRANSFER_DATA = "TRANSFER_DATA";

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
}

class DragSourcePanel extends Panel {
    public DragSourcePanel() {
        final Transferable t = new StringSelection(Util.TRANSFER_DATA);
        final DragGestureListener dgl = new DragGestureListener() {
            public void dragGestureRecognized(DragGestureEvent dge) {
                dge.startDrag(null, t);
            }
        };
        final DragSource ds = DragSource.getDefaultDragSource();
        final DragGestureRecognizer dgr =
                ds.createDefaultDragGestureRecognizer(this, DnDConstants.ACTION_COPY,
                        dgl);
    }

    public Dimension getPreferredSize() {
        return new Dimension(100, 100);
    }
}

class DropTargetPanel extends Panel {
    public DropTargetPanel() {
        final DropTargetListener dtl = new DropTargetAdapter() {
            public void drop(DropTargetDropEvent dtde) {
                Transferable t = dtde.getTransferable();
                dtde.acceptDrop(dtde.getDropAction());
                try {
                    t.getTransferData(DataFlavor.stringFlavor);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                dtde.dropComplete(true);
                EventQueue.invokeLater(new Runnable() {
                    public void run() {
                        System.exit(0);
                    }
                });
            }
        };
        final DropTarget dt = new DropTarget(this, dtl);
    }

    public Dimension getPreferredSize() {
        return new Dimension(100, 100);
    }
}

class ProcessResults {
    public int exitValue;
    public String stdout;
    public String stderr;

    public ProcessResults() {
        exitValue = -1;
        stdout = "";
        stderr = "";
    }

    /**
     * Method to perform a "wait" for a process and return its exit value.
     * This is a workaround for <code>Process.waitFor()</code> never returning.
     */
    public static ProcessResults doWaitFor(Process p) {
        ProcessResults pres = new ProcessResults();

        InputStream in = null;
        InputStream err = null;

        try {
            in = p.getInputStream();
            err = p.getErrorStream();

            boolean finished = false;

            while (!finished) {
                try {
                    while (in.available() > 0) {
                        pres.stdout += (char)in.read();
                    }
                    while (err.available() > 0) {
                        pres.stderr += (char)err.read();
                    }
                    // Ask the process for its exitValue. If the process
                    // is not finished, an IllegalThreadStateException
                    // is thrown. If it is finished, we fall through and
                    // the variable finished is set to true.
                    pres.exitValue = p.exitValue();
                    finished  = true;
                }
                catch (IllegalThreadStateException e) {
                    // Process is not finished yet;
                    // Sleep a little to save on CPU cycles
                    Thread.sleep(500);
                }
            }
            if (in != null) in.close();
            if (err != null) err.close();
        }
        catch (Throwable e) {
            System.err.println("doWaitFor(): unexpected exception");
            e.printStackTrace();
        }
        return pres;
    }
}
