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
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Robot;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
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
import java.awt.event.InputEvent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/*
  @test
  @bug 4484996
  @summary Tests that drop doesn't take too much time on Win 95/98.
  @key headful
  @run main DropPerformanceTest
*/

public class DropPerformanceTest {

    public static final int CODE_NOT_RETURNED = -1;
    public static final int CODE_OK = 0;
    public static final int CODE_FAILURE = 1;
    public static final int FRAME_ACTIVATION_TIMEOUT = 2000;
    public static final int DROP_COMPLETION_TIMEOUT = 4000;
    public static final int TIME_THRESHOLD = 40000;

    private int returnCode = CODE_NOT_RETURNED;

    final Frame frame = new Frame();
    Robot robot = null;
    DropTargetPanel dtpanel = null;
    DragSourcePanel dspanel = null;

    public static void main(String[] args) throws Exception {
        DropPerformanceTest test = new DropPerformanceTest();
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

            dspanel = new DragSourcePanel();

            frame.setTitle("DropPerformanceTest Drop Source Frame");
            frame.setLocation(100, 200);
            frame.add(dspanel);
            frame.pack();
            frame.setVisible(true);

            Thread.sleep(FRAME_ACTIVATION_TIMEOUT);

            Point sourcePoint = dspanel.getLocationOnScreen();
            Dimension d = dspanel.getSize();
            sourcePoint.translate(d.width / 2, d.height / 2);

            Point targetPoint = new Point(x + w / 2, y + h / 2);

            robot = new Robot();
            robot.mouseMove(sourcePoint.x, sourcePoint.y);
            robot.mousePress(InputEvent.BUTTON1_MASK);
            for (; !sourcePoint.equals(targetPoint);
                 sourcePoint.translate(sign(targetPoint.x - sourcePoint.x),
                                       sign(targetPoint.y - sourcePoint.y))) {
                robot.mouseMove(sourcePoint.x, sourcePoint.y);
                Thread.sleep(10);
            }
            robot.mouseRelease(InputEvent.BUTTON1_MASK);

            Thread.sleep(DROP_COMPLETION_TIMEOUT);

        } catch (Throwable e) {
            e.printStackTrace();
            System.exit(DropPerformanceTest.CODE_FAILURE);
        }

        System.exit(DropPerformanceTest.CODE_OK);
    } // run()

    public static int sign(int n) {
        return n < 0 ? -1 : n == 0 ? 0 : 1;
    }

    public void init() {
        dtpanel = new DropTargetPanel();

        frame.setTitle("Drop Target Frame");
        frame.setLocation(250, 200);
        frame.add(dtpanel);

        frame.pack();
        frame.setVisible(true);
    }

    private void launchChildVM() {
        try {
            Thread.sleep(FRAME_ACTIVATION_TIMEOUT);

            Point p = dtpanel.getLocationOnScreen();
            Dimension d = dtpanel.getSize();

            String javaPath = System.getProperty("java.home", "");
            String command = javaPath + File.separator + "bin" +
                File.separator + "java -cp " + System.getProperty("test.classes", ".") +
                " DropPerformanceTest " +
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
    }

    public void start() {
        launchChildVM();
        System.err.println("Drop consumed " + dtpanel.getDropTime() + " milliseconds");
        if (dtpanel.getDropTime() > TIME_THRESHOLD) {
            throw new RuntimeException("The test failed: drop took too much time");
        }
    }
}

class DragSourceButton extends JButton
                              implements Transferable, Serializable,
                                         DragGestureListener, DragSourceListener {

    public DataFlavor dataflavor = new DataFlavor(DragSourceButton.class, "Source");

    DragSourceButton(String str) {
        super(str);
        DragSource ds = DragSource.getDefaultDragSource();
        ds.createDefaultDragGestureRecognizer(this, DnDConstants.ACTION_COPY, this);
    }

    public void dragGestureRecognized(DragGestureEvent dge) {
        dge.startDrag(null, this, this);
    }

    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
        Object ret = null;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(this);
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bais);
        try {
            ret = (DragSourceButton)ois.readObject();
        } catch (ClassNotFoundException cannotHappen) {
            return null;
        }
        return ret;
    }

    public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[] { dataflavor };
    }

    public boolean isDataFlavorSupported(DataFlavor dflavor) {
        return dataflavor.equals(dflavor);
    }

    public void dragEnter(DragSourceDragEvent dsde) {}

    public void dragExit(DragSourceEvent dse) {}

    public void dragOver(DragSourceDragEvent dsde) {}

    public void dragDropEnd(DragSourceDropEvent dsde) {}

    public void dropActionChanged(DragSourceDragEvent dsde) {}
}

class DragSourcePanel extends Panel {

    final Dimension preferredDimension = new Dimension(100, 50);

    public DragSourcePanel() {
        setLayout(new GridLayout(1, 1));
        add(new DragSourceButton("Drag me"));
    }

    public Dimension getPreferredSize() {
        return preferredDimension;
    }
}

class DropTargetPanel extends Panel implements DropTargetListener {

    final Dimension preferredDimension = new Dimension(100, 50);
    private long dropTime = 0;

    public DropTargetPanel() {
        setBackground(Color.green);
        setDropTarget(new DropTarget(this, this));
    }

    public long getDropTime() {
        return dropTime;
    }

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

    public void drop(DropTargetDropEvent dtde) {
        DropTargetContext dtc = dtde.getDropTargetContext();

        if ((dtde.getSourceActions() & DnDConstants.ACTION_COPY) != 0) {
            dtde.acceptDrop(DnDConstants.ACTION_COPY);
        } else {
            dtde.rejectDrop();
            return;
        }

        Transferable t = dtde.getTransferable();
        DataFlavor[] dfs = t.getTransferDataFlavors();

        long before = System.currentTimeMillis();

        if (dfs != null && dfs.length >= 1) {
            Object obj = null;
            try {
                obj = t.getTransferData(dfs[0]);
            } catch (IOException ioe) {
                dtc.dropComplete(false);
                return;
            } catch (UnsupportedFlavorException ufe) {
                dtc.dropComplete(false);
                return;
            }

            if (obj != null) {
                Component comp = (Component)obj;
                add(comp);
            }
        }

        long after = System.currentTimeMillis();
        dropTime = after - before;

        synchronized (this) {
            notifyAll();
        }

        dtc.dropComplete(true);
        validate();
    }

    public void dropActionChanged(DropTargetDragEvent dtde) {}

}
