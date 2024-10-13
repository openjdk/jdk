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
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.List;
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
import java.io.File;
import java.io.InputStream;
import java.io.Serializable;

/*
  @test
  @bug 4399700
  @summary tests that drop transfer data can be requested in several data flavors.
  @key headful
  @run main MultiDataFlavorDropTest
*/

public class MultiDataFlavorDropTest {

    public static final int CODE_NOT_RETURNED = -1;
    public static final int CODE_OK = 0;
    public static final int CODE_FAILURE = 1;
    public static final int FRAME_ACTIVATION_TIMEOUT = 2000;
    public static final int DROP_TIMEOUT = 10000;
    public static final int DROP_COMPLETION_TIMEOUT = 1000;

    private int returnCode = CODE_NOT_RETURNED;

    volatile Frame frame;
    volatile Robot robot;
    volatile Panel panel;
    volatile Point p;
    volatile Dimension d;

    public static void main(String[] args) throws Exception {
        MultiDataFlavorDropTest test = new MultiDataFlavorDropTest();
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

            Transferable t = new TransferableNumber();
            panel = new DragSourcePanel(t);

            frame = new Frame();
            frame.setTitle("DragSource frame");
            frame.setLocation(300, 200);
            frame.add(panel);
            frame.pack();
            frame.setVisible(true);

            Thread.sleep(FRAME_ACTIVATION_TIMEOUT);

            Point sourcePoint = panel.getLocationOnScreen();
            Dimension d = panel.getSize();
            sourcePoint.translate(d.width / 2, d.height / 2);

            Point targetPoint = new Point(x + w / 2, y + h / 2);

            robot = new Robot();
            robot.mouseMove(sourcePoint.x, sourcePoint.y);
            robot.keyPress(KeyEvent.VK_CONTROL);
            robot.mousePress(InputEvent.BUTTON1_MASK);
            for (; !sourcePoint.equals(targetPoint);
                 sourcePoint.translate(sign(targetPoint.x - sourcePoint.x),
                         sign(targetPoint.y - sourcePoint.y))) {
                robot.mouseMove(sourcePoint.x, sourcePoint.y);
                Thread.sleep(10);
            }
            robot.mouseRelease(InputEvent.BUTTON1_MASK);
            robot.keyRelease(KeyEvent.VK_CONTROL);

            synchronized (t) {
                t.wait(DROP_TIMEOUT);
            }

            Thread.sleep(DROP_COMPLETION_TIMEOUT);

        } catch (Throwable e) {
            e.printStackTrace();
            System.exit(MultiDataFlavorDropTest.CODE_FAILURE);
        }
        System.exit(MultiDataFlavorDropTest.CODE_OK);
    }

    public static int sign(int n) {
        return n < 0 ? -1 : n == 0 ? 0 : 1;
    }

    public void init() {
        frame = new Frame();
        panel = new DropTargetPanel();

        frame.setTitle("MultiDataFlavorDropTest");
        frame.setLocation(10, 200);
        frame.add(panel);

        frame.pack();
        frame.setVisible(true);
    }

    public void start() throws Exception {
        Thread.sleep(FRAME_ACTIVATION_TIMEOUT);

        EventQueue.invokeAndWait(() -> {
            p = panel.getLocationOnScreen();
            d = panel.getSize();
        });

        String javaPath = System.getProperty("java.home", "");
        String command = javaPath + File.separator + "bin" +
                File.separator + "java -cp " + System.getProperty("test.classes", ".") +
                " MultiDataFlavorDropTest " +
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

class DragSourceButton extends Button implements Serializable,
                                                 DragGestureListener,
                                                 DragSourceListener {

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

    public void dragEnter(DragSourceDragEvent dsde) {}

    public void dragExit(DragSourceEvent dse) {}

    public void dragOver(DragSourceDragEvent dsde) {}

    public void dragDropEnd(DragSourceDropEvent dsde) {}

    public void dropActionChanged(DragSourceDragEvent dsde) {}
}

class IntegerDataFlavor extends DataFlavor {

    private final int number;

    public IntegerDataFlavor(int n) throws ClassNotFoundException {
        super("application/integer-" + n +
              "; class=java.lang.Integer");
        this.number = n;
    }

    public int getNumber() {
        return number;
    }
}

class TransferableNumber implements Transferable {

    private int transferDataRequestCount = 0;
    public static final int NUM_DATA_FLAVORS = 5;
    static final DataFlavor[] supportedFlavors =
        new DataFlavor[NUM_DATA_FLAVORS];

    static {
        try {
            for (int i = 0; i < NUM_DATA_FLAVORS; i++) {
                supportedFlavors[i] =
                    new IntegerDataFlavor(i);
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public DataFlavor[] getTransferDataFlavors() {
        return supportedFlavors;
    }

    public boolean isDataFlavorSupported(DataFlavor flavor) {
        if (flavor instanceof IntegerDataFlavor) {
            IntegerDataFlavor integerFlavor = (IntegerDataFlavor)flavor;
            int flavorNumber = integerFlavor.getNumber();
            if (flavorNumber >= 0 && flavorNumber < NUM_DATA_FLAVORS) {
                return true;
            }
        }
        return false;
    }

    public Object getTransferData(DataFlavor flavor)
      throws UnsupportedFlavorException {

        if (!isDataFlavorSupported(flavor)) {
            throw new UnsupportedFlavorException(flavor);
        }

        transferDataRequestCount++;

        if (transferDataRequestCount >= NUM_DATA_FLAVORS) {
            synchronized (this) {
                this.notifyAll();
            }
        }

        IntegerDataFlavor integerFlavor = (IntegerDataFlavor)flavor;
        return new Integer(integerFlavor.getNumber());
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

        removeAll();
        final List list = new List();
        add(list);

        Transferable t = dtde.getTransferable();
        DataFlavor[] dfs = t.getTransferDataFlavors();

        if (dfs.length != TransferableNumber.NUM_DATA_FLAVORS) {
            throw new RuntimeException("FAILED: Incorrect number of data flavors.");
        }

        for (int i = 0; i < dfs.length; i++) {

            DataFlavor flavor = dfs[i];
            Integer transferNumber = null;

            if (flavor.getRepresentationClass().equals(Integer.class)) {
                try {
                    transferNumber = (Integer)t.getTransferData(flavor);
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException("FAILED: Cannot get data: " +
                                               flavor.getMimeType());
                }
            }

            boolean supportedFlavor = false;
            for (int j = 0; j < TransferableNumber.NUM_DATA_FLAVORS; j++) {
                int number = (i + j) % TransferableNumber.NUM_DATA_FLAVORS;
                try {
                    if (flavor.equals(new IntegerDataFlavor(number))) {
                        if (!(new Integer(number).equals(transferNumber))) {
                            throw new RuntimeException("FAILED: Invalid data \n" +
                                                       "\tflavor : " + flavor +
                                                       "\tdata   : " + transferNumber);
                        }
                        supportedFlavor = true;
                        break;
                    }
                } catch (ClassNotFoundException cannotHappen) {
                }
            }
            if (!supportedFlavor) {
                throw new RuntimeException("FAILED: Invalid flavor: " + flavor);
            }

            list.add(transferNumber + ":" + flavor.getMimeType());
        }

        dtc.dropComplete(true);
        validate();
    }

    public void dropActionChanged(DropTargetDragEvent dtde) {}
}
