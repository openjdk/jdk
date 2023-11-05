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
  @bug 4888520
  @summary tests that drag source application invoked via debug java does not
           crash on exit after drop on other Java drop target application
  @key headful
*/

import java.awt.EventQueue;
import java.awt.Frame;
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
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceListener;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.InputEvent;
import java.io.File;
import java.io.InputStream;
import java.io.Reader;


public class DragSourceGCrashTest {

    volatile Frame frame;
    volatile Panel panel;

    public static void main(String[] args) throws Exception {
        DragSourceGCrashTest test = new DragSourceGCrashTest();
        EventQueue.invokeAndWait(test::init);
        try {
            test.start();
        } finally {
            EventQueue.invokeAndWait(()-> test.frame.dispose());
        }
    }

    public void init() {
        frame = new Frame("target - DragSourceGCrashTest");
        panel = new Panel();
        frame.add(panel);
        frame.setBounds(100, 100, 100, 100);

        DropTargetListener dtl = new DropTargetAdapter() {
            public void drop(DropTargetDropEvent dtde) {
                dtde.acceptDrop(DnDConstants.ACTION_MOVE);
                Transferable t = dtde.getTransferable();
                try {
                    DataFlavor df = new DataFlavor(
                            "text/plain;class=java.io.Reader");
                    Reader r = df.getReaderForText(t);
                    // To verify the bug do not close the reader!
                    dtde.dropComplete(true);
                } catch (Exception e) {
                    e.printStackTrace();
                    dtde.dropComplete(false);
                }
            }
        };

        new DropTarget(frame, dtl);

        frame.setVisible(true);
    }

    public void start() throws Exception {
        Robot robot = new Robot();
        robot.waitForIdle();
        robot.delay(1000);

        ProcessResults pres = null;

        Point endPoint = panel.getLocationOnScreen();

        endPoint.translate(panel.getWidth() / 2, panel.getHeight() / 2);

        String jdkPath = System.getProperty("java.home");
        String javaPath = jdkPath + File.separator + "bin" +
                File.separator + "java";

        String[] cmd = {
                javaPath, "-cp",
                System.getProperty("test.classes", "."),
                "Child",
                String.valueOf(endPoint.x),
                String.valueOf(endPoint.y)
        };
        Process process = Runtime.getRuntime().exec(cmd);
        pres = ProcessResults.doWaitFor(process);

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

        if (pres.exitValue != 0) {
            throw new RuntimeException("FAILURE: child java exited " +
                                       "with code " + pres.exitValue);
        }
    }
}

class Child {
    volatile Frame frame;
    volatile Panel panel;

    public static void main(String[] args) throws Exception {
        int endX = Integer.parseInt(args[0]);
        int endY = Integer.parseInt(args[1]);
        Point endPoint = new Point(endX, endY);

        Child child = new Child();
        EventQueue.invokeAndWait(child::init);
        try {
            child.start(endPoint);
        } finally {
            EventQueue.invokeAndWait(() -> child.frame.dispose());
        }
    }

    public void init() {
        frame = new Frame("source - DragSourceGCrashTest");
        panel = new Panel();
        frame.add(panel);
        frame.setBounds(200, 100, 100, 100);

        final DragSourceListener dsl = new DragSourceAdapter() {
            public void dragDropEnd(DragSourceDropEvent dsde) {
                System.err.println("DragSourceListener.dragDropEnd(): " +
                        "exiting application");
                System.exit(0);
            }
        };
        DragGestureListener dgl = new DragGestureListener() {
            public void dragGestureRecognized(DragGestureEvent dge) {
                dge.startDrag(null,
                        new StringSelection("test"), dsl);
            }
        };

        new DragSource().createDefaultDragGestureRecognizer(panel,
                DnDConstants.ACTION_MOVE, dgl);

        frame.setVisible(true);
    }

    public void start(Point endPoint) throws Exception {
        Robot robot = new Robot();

        robot.waitForIdle();
        robot.delay(1000);

        Point startPoint = panel.getLocationOnScreen();

        startPoint.translate(
                panel.getWidth() / 2,
                panel.getHeight() / 2
        );

        robot.mouseMove(startPoint.x, startPoint.y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        for (Point p = new Point(startPoint); !p.equals(endPoint);
             p.translate(
                     Integer.compare(endPoint.x - p.x, 0),
                     Integer.compare(endPoint.y - p.y, 0)
             )) {
            robot.mouseMove(p.x, p.y);
            robot.delay(50);
        }
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

        long timeout = 30000;
        Object LOCK = new Object();
        synchronized (LOCK) {
            LOCK.wait(timeout);
            System.err.println(System.currentTimeMillis() + " end");
        }
        System.err.println("WARNING: drop has not ended within " + timeout +
                " ms, exiting application!");
        System.exit(0);
    }
}

class ProcessResults {
    final static long TIMEOUT = 60000;

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

        long startTime = System.currentTimeMillis();

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
                    if (System.currentTimeMillis() > startTime + TIMEOUT) {
                         System.err.println("WARNING: child process has not " +
                                 "exited within " + TIMEOUT + " ms, returning" +
                                 " from ProcessResults.doWaitFor()");
                         pres.exitValue = 0;
                         return pres;
                    }
                    // Process is not finished yet;
                    // Sleep a little to save on CPU cycles
                    Thread.currentThread().sleep(500);
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
