/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Button;
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
import java.awt.event.KeyEvent;
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
  @bug 4338893
  @summary tests that dnd between two different VMs doesn't cause hang
           on the dropping side.
  @key headful
  @run main/timeout=120 DroppingVMHangTest
*/

public class DroppingVMHangTest {

    public static final int CODE_NOT_RETURNED = -1;
    public static final int CODE_OK = 0;
    public static final int CODE_FAILURE = 1;
    public static final int CODE_HANG_FAILURE = 2;
    public static final int CODE_OTHER_FAILURE = 3;
    public static final int CODE_TIMEOUT = 4;

    public static final int FRAME_ACTIVATION_TIMEOUT = 2000;

    private int returnCode = CODE_NOT_RETURNED;

    volatile Frame frame;
    volatile Point p;
    volatile Dimension d;
    Robot robot = null;

    public static void main(String[] args) throws Exception {
        DroppingVMHangTest test = new DroppingVMHangTest();
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
            frame = new Frame();
            frame.setTitle("DroppingVMHangTest DropTarget frame");
            frame.setLocation(300, 400);
            frame.add(new DropTargetPanel());
            frame.pack();
            frame.setVisible(true);

            Thread.sleep(FRAME_ACTIVATION_TIMEOUT);

            if (args.length != 2) {
                throw new RuntimeException("Incorrect number of arguments for child:" +
                                           args.length);
            }

            int x = Integer.parseInt(args[0], 10);
            int y = Integer.parseInt(args[1], 10);

            Point sourcePoint = new Point(x, y);
            Point targetPoint = frame.getLocationOnScreen();
            Dimension d = frame.getSize();
            targetPoint.translate(d.width / 2, d.height / 2);

            robot = new Robot();
            robot.mouseMove(x, y);
            robot.keyPress(KeyEvent.VK_CONTROL);
            robot.mousePress(InputEvent.BUTTON1_MASK);
            while (!sourcePoint.equals(targetPoint)) {
                robot.mouseMove(sourcePoint.x, sourcePoint.y);
                Thread.sleep(10);
                int dx = sign(targetPoint.x - sourcePoint.x);
                int dy = sign(targetPoint.y - sourcePoint.y);
                sourcePoint.translate(dx, dy);
            }
            robot.mouseRelease(InputEvent.BUTTON1_MASK);
            robot.keyRelease(KeyEvent.VK_CONTROL);

            Thread.sleep(5000);
            System.exit(DroppingVMHangTest.CODE_TIMEOUT);
        } catch (Throwable e) {
            e.printStackTrace();
            System.exit(DroppingVMHangTest.CODE_OTHER_FAILURE);
        }
    }

    static int sign(int n) {
        return n > 0 ? 1 : n < 0 ? -1 : 0;
    }

    public void init() {
        frame = new Frame();
        frame.setTitle("DragSource frame");
        frame.setLocation(10, 200);
        frame.add(new DragSourcePanel());

        frame.pack();
        frame.setVisible(true);
    }

    public void start() throws Exception {
        Thread.sleep(FRAME_ACTIVATION_TIMEOUT);

        EventQueue.invokeAndWait(() -> {
            p = frame.getLocationOnScreen();
            d = frame.getSize();
        });

        p.translate(d.width / 2, d.height / 2);

        String javaPath = System.getProperty("java.home", "");
        String command = javaPath + File.separator + "bin" +
        File.separator + "java -cp " + System.getProperty("test.classes", ".") +
        " DroppingVMHangTest" + " " + p.x + " " + p.y;
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
        case CODE_HANG_FAILURE:
            System.err.println("Child VM: hang on drop");
            break;
        case CODE_OTHER_FAILURE:
            System.err.println("Child VM: other failure");
            break;
        case CODE_TIMEOUT:
            System.err.println("Child VM: failed to simulate drag-and-drop operation with Robot");
            break;
        }
        if (returnCode != CODE_OK && returnCode != CODE_TIMEOUT) {
            throw new RuntimeException("The test failed.");
        }
    }
}

class DragSourceButton extends Button implements Serializable,
                                                 Transferable,
                                                 DragGestureListener,
                                                 DragSourceListener {
    private transient final DataFlavor dataflavor =
        new DataFlavor(DragSourceButton.class, "DragSourceButton");

    public DragSourceButton() {
        this("DragSourceButton");
    }

    public DragSourceButton(String str) {
        super(str);

        DragSource ds = DragSource.getDefaultDragSource();
        ds.createDefaultDragGestureRecognizer(this, DnDConstants.ACTION_COPY,
                                              this);
    }

    public void dragGestureRecognized(DragGestureEvent dge) {
        dge.startDrag(null, this, this);
    }

    public void dragEnter(DragSourceDragEvent dsde) {}

    public void dragExit(DragSourceEvent dse) {}

    public void dragOver(DragSourceDragEvent dsde) {}

    public void dragDropEnd(DragSourceDropEvent dsde) {}

    public void dropActionChanged(DragSourceDragEvent dsde) {}

    public Object getTransferData(DataFlavor flavor)
      throws UnsupportedFlavorException, IOException {

        if (!isDataFlavorSupported(flavor)) {
            throw new UnsupportedFlavorException(flavor);
        }

        Object retObj = null;

        ByteArrayOutputStream baoStream = new ByteArrayOutputStream();
        ObjectOutputStream ooStream = new ObjectOutputStream(baoStream);
        ooStream.writeObject(this);

        ByteArrayInputStream baiStream = new ByteArrayInputStream(baoStream.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(baiStream);
        try {
            retObj = ois.readObject();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            throw new RuntimeException(e.toString());
        }

        return retObj;
    }

    public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[] { dataflavor };
    }

    public boolean isDataFlavorSupported(DataFlavor dflavor) {
        return dataflavor.equals(dflavor);
    }
}

class DragSourcePanel extends Panel {

    final Dimension preferredDimension = new Dimension(200, 200);

    public DragSourcePanel() {
        setLayout(new GridLayout(1, 1));
        add(new DragSourceButton());
    }

    public Dimension getPreferredSize() {
        return preferredDimension;
    }
}

class DropTargetPanel extends Panel implements DropTargetListener,
                                               Runnable {

    final Dimension preferredDimension = new Dimension(200, 200);

    public DropTargetPanel() {
        setDropTarget(new DropTarget(this, this));
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
            System.exit(DroppingVMHangTest.CODE_OTHER_FAILURE);
        }

        DataFlavor[] dfs = dtde.getCurrentDataFlavors();
        Component comp = null;

        if (dfs != null && dfs.length >= 1) {
            Transferable transfer = dtde.getTransferable();

            try {
                comp = (Component)transfer.getTransferData(dfs[0]);
                comp.getClass();
            } catch (Throwable e) {
                e.printStackTrace();
                dtc.dropComplete(false);
                System.exit(DroppingVMHangTest.CODE_OTHER_FAILURE);
            }
        }
        dtc.dropComplete(true);

        Thread thread = new Thread(this);
        thread.start();

        add(comp);

        System.exit(DroppingVMHangTest.CODE_OK);
    }

    public void dropActionChanged(DropTargetDragEvent dtde) {}

    public void run() {
        try {
            Thread.sleep(60000);
        } catch (InterruptedException e) {
        }
        Runtime.getRuntime().halt(DroppingVMHangTest.CODE_HANG_FAILURE);
    }
}
