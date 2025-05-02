/*
 * Copyright (c) 2002, 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.SystemFlavorMap;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragGestureRecognizer;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceAdapter;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceListener;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetContext;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.AWTEventListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

/*
  @test
  @bug 4746177
  @summary tests that data types exported by Netscape 6.2 are supported
  @requires (os.family != "windows")
  @key headful
  @run main MozillaDnDTest
*/

public class MozillaDnDTest {

    public static final int CODE_NOT_RETURNED = -1;
    public static final int CODE_OK = 0;
    public static final int CODE_FAILURE = 1;
    public static final String DATA = "www.sun.com";

    private int returnCode = CODE_NOT_RETURNED;

    volatile Frame frame;
    volatile Robot robot;
    volatile Panel panel;
    volatile Point p;
    volatile Dimension d;

    public static void main(String[] args) throws Exception {
        MozillaDnDTest test = new MozillaDnDTest();
        if (args.length > 0) {
            test.run(args);
        } else {
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

            panel = new DragSourcePanel();
            frame = new Frame();

            frame.setTitle("DragSource frame");
            frame.setLocation(300, 200);
            frame.add(panel);
            frame.pack();
            frame.setVisible(true);

            Util.waitForInit();

            Point sourcePoint = panel.getLocationOnScreen();
            Dimension d = panel.getSize();
            sourcePoint.translate(d.width / 2, d.height / 2);

            Point targetPoint = new Point(x + w / 2, y + h / 2);

            robot = new Robot();

            if (!Util.pointInComponent(robot, sourcePoint, panel)) {
                System.err.println("WARNING: Couldn't locate " + panel +
                                   " at point " + sourcePoint);
                System.exit(MozillaDnDTest.CODE_OK);
            }

            robot.mouseMove(sourcePoint.x, sourcePoint.y);
            robot.keyPress(KeyEvent.VK_CONTROL);
            robot.mousePress(InputEvent.BUTTON1_MASK);
            for (; !sourcePoint.equals(targetPoint);
                 sourcePoint.translate(sign(targetPoint.x - sourcePoint.x),
                                       sign(targetPoint.y - sourcePoint.y))) {
                robot.mouseMove(sourcePoint.x, sourcePoint.y);
                Thread.sleep(50);
            }
            robot.mouseRelease(InputEvent.BUTTON1_MASK);
            robot.keyRelease(KeyEvent.VK_CONTROL);

        } catch (Throwable e) {
            e.printStackTrace();
            System.exit(MozillaDnDTest.CODE_FAILURE);
        }
    }

    public static int sign(int n) {
        return n < 0 ? -1 : n == 0 ? 0 : 1;
    }

    public void init() {
        frame = new Frame();
        panel = new DropTargetPanel();

        frame.setTitle("DropTarget frame");
        frame.setLocation(10, 200);
        frame.add(panel);

        frame.pack();
        frame.setVisible(true);
    }

    public void start() {
        // Solaris/Linux-only test
        if (System.getProperty("os.name").startsWith("Windows")) {
            return;
        }
        try {
            Util.waitForInit();
            EventQueue.invokeAndWait(() -> {
                p = panel.getLocationOnScreen();
                d = panel.getSize();
            });

            Robot robot = new Robot();
            Point pp = new Point(p);
            pp.translate(d.width / 2, d.height / 2);
            if (!Util.pointInComponent(robot, pp, panel)) {
                System.err.println("WARNING: Couldn't locate " + panel +
                                   " at point " + pp);
                return;
            }

            String javaPath = System.getProperty("java.home", "");
            String command = javaPath + File.separator + "bin" +
                File.separator + "java -cp " + System.getProperty("test.classes", ".") +
                " MozillaDnDTest " +
                p.x + " " + p.y + " " + d.width + " " + d.height;

            Process process = Runtime.getRuntime().exec(command);
            ProcessResults pres = ProcessResults.doWaitFor(process);
            returnCode = pres.exitValue;

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

        } catch (Throwable e) {
            e.printStackTrace();
        }
        switch (returnCode) {
        case CODE_NOT_RETURNED:
            System.err.println("Child VM: failed to start");
            break;
        case CODE_OK:
            System.err.println("Child VM: normal termination");
            break;
        case CODE_FAILURE:
            System.err.println("Child VM: abnormal termination");
            break;
        }
        if (returnCode != CODE_OK) {
            throw new RuntimeException("The test failed.");
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
                    finished = true;
                }
                catch (IllegalThreadStateException e) {
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

class DragSourcePanel extends Panel {
    static final Dimension preferredDimension = new Dimension(200, 200);
    static final DataFlavor df = new DataFlavor("application/mozilla-test-flavor",
                                                null);
    final DragSource ds = DragSource.getDefaultDragSource();
    final Transferable t = new Transferable() {
            final DataFlavor[] flavors = new DataFlavor[] { df };
            public DataFlavor[] getTransferDataFlavors() {
                return flavors;
            }
            public boolean isDataFlavorSupported(DataFlavor flav) {
                return df.equals(flav);
            }
            public Object getTransferData(DataFlavor flav)
              throws IOException, UnsupportedFlavorException {
                if (!isDataFlavorSupported(flav)) {
                    throw new UnsupportedFlavorException(flav);
                }
                byte[] bytes = MozillaDnDTest.DATA.getBytes("ASCII");
                return new ByteArrayInputStream(bytes);
            }
        };
    final DragSourceListener dsl = new DragSourceAdapter() {
            public void dragDropEnd(DragSourceDropEvent dsde) {
                System.exit(MozillaDnDTest.CODE_OK);
            }
        };
    final DragGestureListener dgl = new DragGestureListener() {
            public void dragGestureRecognized(DragGestureEvent dge) {
                dge.startDrag(null, t, dsl);
            }
        };
    final DragGestureRecognizer dgr =
        ds.createDefaultDragGestureRecognizer(this, DnDConstants.ACTION_COPY,
                                              dgl);
    static {
        SystemFlavorMap sfm =
            (SystemFlavorMap)SystemFlavorMap.getDefaultFlavorMap();
        String[] natives = new String[] {
            "_NETSCAPE_URL",
            "text/plain",
            "text/unicode",
            "text/x-moz-url",
            "text/html"
        };
        sfm.setNativesForFlavor(df, natives);
    }

    public Dimension getPreferredSize() {
        return preferredDimension;
    }
}

class DropTargetPanel extends Panel implements DropTargetListener {

    final Dimension preferredDimension = new Dimension(200, 200);
    final DropTarget dt = new DropTarget(this, this);

    public Dimension getPreferredSize() {
        return preferredDimension;
    }

    public void dragEnter(DropTargetDragEvent dtde) {
        dtde.acceptDrag(DnDConstants.ACTION_COPY);
    }

    public void dragExit(DropTargetEvent dte) {}

    public void dragOver(DropTargetDragEvent dtde) {
        dtde.acceptDrag(DnDConstants.ACTION_COPY);
    }

    public String getTransferString(Transferable t) {
        String string = null;
        DataFlavor[] dfs = t.getTransferDataFlavors();
        for (int i = 0; i < dfs.length; i++) {
            if ("text".equals(dfs[i].getPrimaryType()) ||
                DataFlavor.stringFlavor.equals(dfs[i])) {
                try {
                    Object o = t.getTransferData(dfs[i]);
                    if (o instanceof InputStream ||
                        o instanceof Reader) {
                        Reader reader = null;
                        if (o instanceof InputStream) {
                            InputStream is = (InputStream)o;
                            reader = new InputStreamReader(is);
                        } else {
                            reader = (Reader)o;
                        }
                        StringBuffer buf = new StringBuffer();
                        for (int c = reader.read(); c != -1; c = reader.read()) {
                            buf.append((char)c);
                        }
                        reader.close();
                        string = buf.toString();
                        break;
                    } else if (o instanceof String) {
                        string = (String)o;
                        break;
                    }
                } catch (Exception e) {
                    // ignore.
                }
            }
        }
        return string;
     }

    public void drop(DropTargetDropEvent dtde) {
        DropTargetContext dtc = dtde.getDropTargetContext();

        if ((dtde.getSourceActions() & DnDConstants.ACTION_COPY) != 0) {
            dtde.acceptDrop(DnDConstants.ACTION_COPY);
        } else {
            dtde.rejectDrop();
            return;
        }

        Transferable t = dtde.getTransferable();
        String str = getTransferString(t);
        dtde.dropComplete(true);

        if (!MozillaDnDTest.DATA.equals(str)) {
            throw new RuntimeException("Drop data:" + str);
        }
    }

    public void dropActionChanged(DropTargetDragEvent dtde) {}
}
