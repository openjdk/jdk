/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 4388802
 * @summary tests that clipboard operations during drag-and-drop don't deadlock
 * @key headful
 * @run main DnDClipboardDeadlockTest
 */

import java.awt.AWTEvent;
import java.awt.AWTException;
import java.awt.Button;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.List;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceEvent;
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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;

public class DnDClipboardDeadlockTest {

    public static final int CODE_NOT_RETURNED = -1;
    public static final int CODE_OK = 0;
    public static final int CODE_FAILURE = 1;

    private int returnCode = CODE_NOT_RETURNED;

    final Frame frame = new Frame();
    Robot robot = null;
    Panel panel = null;

    public static void main(String[] args) throws Exception {
        DnDClipboardDeadlockTest test = new DnDClipboardDeadlockTest();
        if (args.length == 4) {
            test.run(args);
        } else {
            test.start();
        }
    }

    public void run(String[] args) throws InterruptedException, AWTException {
        try {
            if (args.length != 4) {
                throw new RuntimeException("Incorrect command line arguments.");
            }

            int x = Integer.parseInt(args[0]);
            int y = Integer.parseInt(args[1]);
            int w = Integer.parseInt(args[2]);
            int h = Integer.parseInt(args[3]);

            Transferable t = new StringSelection("TEXT");
            panel = new DragSourcePanel(t);

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
                throw new RuntimeException("WARNING: Cannot locate source panel");
            }

            robot.mouseMove(sourcePoint.x, sourcePoint.y);
            robot.keyPress(KeyEvent.VK_CONTROL);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            for (; !sourcePoint.equals(targetPoint);
                 sourcePoint.translate(sign(targetPoint.x - sourcePoint.x),
                         sign(targetPoint.y - sourcePoint.y))) {
                robot.mouseMove(sourcePoint.x, sourcePoint.y);
                Thread.sleep(10);
            }
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.keyRelease(KeyEvent.VK_CONTROL);
            if (frame != null) {
                frame.dispose();
            }

        } catch (Throwable e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    } // run()

    public static int sign(int n) {
        return n < 0 ? -1 : n == 0 ? 0 : 1;
    }

    public void start() {
        panel = new DropTargetPanel();

        frame.setTitle("DropTarget frame");
        frame.setLocation(10, 200);
        frame.add(panel);

        frame.pack();
        frame.setVisible(true);

        try {
            Util.waitForInit();

            Point p = panel.getLocationOnScreen();
            Dimension d = panel.getSize();

            try {
                Robot robot = new Robot();
                Point center = new Point(p);
                center.translate(d.width / 2, d.height / 2);
                if (!Util.pointInComponent(robot, center, panel)) {
                    System.err.println("WARNING: Cannot locate target panel");
                    return;
                }
            } catch (AWTException awte) {
                awte.printStackTrace();
                return;
            }

            String javaPath = System.getProperty("java.home", "");
            String command = javaPath + File.separator + "bin" +
                    File.separator + "java -cp "
                    + System.getProperty("java.class.path", ".")
                    + " DnDClipboardDeadlockTest " +
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
        if (frame != null) {
            frame.dispose();
        }
    } // start()
} // class DnDClipboardDeadlockTest

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
            clickedComponent = (Component) e.getSource();
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
}

class DragSourceButton extends Button implements Serializable,
        DragGestureListener,
        DragSourceListener {
    static final Clipboard systemClipboard =
            Toolkit.getDefaultToolkit().getSystemClipboard();
    final Transferable transferable;

    public DragSourceButton(Transferable t) {
        super("DragSourceButton");

        this.transferable = t;
        DragSource ds = DragSource.getDefaultDragSource();
        ds.createDefaultDragGestureRecognizer(this, DnDConstants.ACTION_COPY,
                this);
    }

    public void dragGestureRecognized(DragGestureEvent dge) {
        dge.startDrag(null, transferable, this);
    }

    public void dragEnter(DragSourceDragEvent dsde) {
    }

    public void dragExit(DragSourceEvent dse) {
    }

    public void dragOver(DragSourceDragEvent dsde) {
        try {
            Transferable t = systemClipboard.getContents(null);
            if (t.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                String str = (String) t.getTransferData(DataFlavor.stringFlavor);
            }
            systemClipboard.setContents(new StringSelection("SOURCE"), null);
        } catch (IOException ioe) {
            ioe.printStackTrace();
            if (!ioe.getMessage().equals("Owner failed to convert data")) {
                throw new RuntimeException("Owner failed to convert data");
            }
        } catch (IllegalStateException e) {
            // IllegalStateExceptions do not indicate a bug in this case.
            // They result from concurrent modification of system clipboard
            // contents by the parent and child processes.
            // These exceptions are numerous, so we avoid dumping their
            // backtraces to prevent blocking child process io, which
            // causes test failure on timeout.
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void dragDropEnd(DragSourceDropEvent dsde) {
        System.exit(DnDClipboardDeadlockTest.CODE_OK);
    }

    public void dropActionChanged(DragSourceDragEvent dsde) {
    }
}

class DragSourcePanel extends Panel {

    final Dimension preferredDimension = new Dimension(200, 200);

    public DragSourcePanel(Transferable t) {
        setLayout(new GridLayout(1, 1));
        add(new DragSourceButton(t));
    }

    public Dimension getPreferredSize() {
        return preferredDimension;
    }
}

class DropTargetPanel extends Panel implements DropTargetListener {

    static final Clipboard systemClipboard =
            Toolkit.getDefaultToolkit().getSystemClipboard();
    final Dimension preferredDimension = new Dimension(200, 200);

    public DropTargetPanel() {
        setBackground(Color.green);
        setDropTarget(new DropTarget(this, this));
        setLayout(new GridLayout(1, 1));
    }

    public Dimension getPreferredSize() {
        return preferredDimension;
    }

    public void dragEnter(DropTargetDragEvent dtde) {
        dtde.acceptDrag(DnDConstants.ACTION_COPY);
    }

    public void dragExit(DropTargetEvent dte) {
    }

    public void dragOver(DropTargetDragEvent dtde) {
        dtde.acceptDrag(DnDConstants.ACTION_COPY);
        try {
            Transferable t = systemClipboard.getContents(null);
            if (t.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                String str = (String) t.getTransferData(DataFlavor.stringFlavor);
            }
            systemClipboard.setContents(new StringSelection("TARGET"), null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void drop(DropTargetDropEvent dtde) {
        DropTargetContext dtc = dtde.getDropTargetContext();

        if ((dtde.getSourceActions() & DnDConstants.ACTION_COPY) != 0) {
            dtde.acceptDrop(DnDConstants.ACTION_COPY);
        } else {
            dtde.rejectDrop();
            return;
        }

        removeAll();
        final List list = new List();
        add(list);

        Transferable t = dtde.getTransferable();
        DataFlavor[] dfs = t.getTransferDataFlavors();

        for (int i = 0; i < dfs.length; i++) {

            DataFlavor flavor = dfs[i];
            String str = null;

            if (DataFlavor.stringFlavor.equals(flavor)) {
                try {
                    str = (String) t.getTransferData(flavor);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            list.add(str + ":" + flavor.getMimeType());
        }

        dtc.dropComplete(true);
        validate();
    }

    public void dropActionChanged(DropTargetDragEvent dtde) {
    }
}
