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

import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
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
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/*
  @test
  @bug 4388802
  @summary tests that dragEnter() is called on a DropTargetListener if its drop
           target is associated with a component which initiated the drag
  @key headful
  @run main MissedDragEnterTest
*/

public class MissedDragEnterTest {

    static final int FRAME_ACTIVATION_TIMEOUT = 1000;
    volatile JFrame frame;
    volatile DragSourceDropTargetPanel panel;
    volatile Point p;
    volatile Dimension d;

    public static void main(String[] args) throws Exception {
        MissedDragEnterTest test = new MissedDragEnterTest();
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

    public void init() {
        panel = new DragSourceDropTargetPanel();
        frame = new JFrame();
        frame.setTitle("MissedDragEnterTest");
        frame.setLocation(200, 200);
        frame.getContentPane().add(panel);

        frame.pack();
        frame.setVisible(true);
    }

    public void start() throws Exception {
        Robot robot = new Robot();

        robot.delay(FRAME_ACTIVATION_TIMEOUT);
        EventQueue.invokeAndWait(() -> {
            p = panel.getLocationOnScreen();
            d = panel.getSize();
        });

        p.translate(d.width / 2, d.height / 2);
        robot.mouseMove(p.x, p.y);
        robot.keyPress(KeyEvent.VK_CONTROL);
        robot.mousePress(InputEvent.BUTTON1_MASK);
        for (int i = 0; i < d.width; i++) {
            p.translate(1, 1);
            robot.mouseMove(p.x, p.y);
            robot.delay(10);
        }
        robot.mouseRelease(InputEvent.BUTTON1_MASK);
        robot.keyRelease(KeyEvent.VK_CONTROL);

        EventQueue.invokeAndWait(() -> {
            if (!panel.getResult()) {
                throw new RuntimeException("The test failed.");
            }
        });
    }
}

class DragSourceDropTargetPanel extends JPanel implements DropTargetListener,
                                                          Serializable,
                                                          Transferable,
                                                          DragGestureListener,
                                                          DragSourceListener {
    private final DataFlavor dataflavor =
        new DataFlavor(JPanel.class, "panel");
    private final Dimension preferredDimension = new Dimension(200, 100);
    private boolean inside = false;
    private boolean passed = true;

    public DragSourceDropTargetPanel() {
        setLayout(new FlowLayout());
        DragSource ds = DragSource.getDefaultDragSource();
        ds.createDefaultDragGestureRecognizer(this, DnDConstants.ACTION_COPY,
                                              this);
        setDropTarget(new DropTarget(this, this));
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

    public Dimension getPreferredSize() {
        return preferredDimension;
    }

    public void dragEnter(DropTargetDragEvent dtde) {
        inside = true;
    }

    public void dragExit(DropTargetEvent dte) {
        if (!inside) {
            passed = false;
            inside = false;
            throw new RuntimeException("dragEnter() is not called before dragExit()");

        }
        inside = false;
    }

    public void dragOver(DropTargetDragEvent dtde) {
        if (!inside) {
            passed = false;
            throw new RuntimeException("dragEnter() is not called before dragOver()");
        }
    }

    public void dropActionChanged(DropTargetDragEvent dtde) {
    }

    public void drop(DropTargetDropEvent dtde) {
        DropTargetContext dtc = dtde.getDropTargetContext();

        if ((dtde.getSourceActions() & DnDConstants.ACTION_COPY) != 0) {
            dtde.acceptDrop(DnDConstants.ACTION_COPY);
        } else {
            dtde.rejectDrop();
        }

        DataFlavor[] dfs = dtde.getCurrentDataFlavors();
        Component comp = null;

        if (dfs != null && dfs.length >= 1) {
            Transferable transfer = dtde.getTransferable();

            try {
                comp = (Component)transfer.getTransferData(dfs[0]);
            } catch (Throwable e) {
                e.printStackTrace();
                dtc.dropComplete(false);
            }
        }
        dtc.dropComplete(true);

        add(comp);
    }

    public boolean getResult() {
        return passed;
    }
}
