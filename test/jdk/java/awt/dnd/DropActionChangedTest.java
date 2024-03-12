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
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/*
  @test
  @bug 4357930
  @summary tests that dropActionChanged() is not invoked if the drop gesture
           is not modified.
  @key headful
  @run main DropActionChangedTest
*/

public class DropActionChangedTest {

    volatile Frame frame;
    volatile DragSourcePanel dragSourcePanel;
    volatile DropTargetPanel dropTargetPanel;

    public static void main(String[] args) throws Exception {
        DropActionChangedTest test = new DropActionChangedTest();
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
        dragSourcePanel = new DragSourcePanel();
        dropTargetPanel = new DropTargetPanel();

        frame = new Frame();
        frame.setTitle("DropTargetAddNotifyNPETest");
        frame.setLocation(200, 200);
        frame.setLayout(new GridLayout(2, 1));
        frame.add(dragSourcePanel);
        frame.add(dropTargetPanel);

        frame.pack();
        frame.setVisible(true);
    }

    public void start() throws Exception {
        Robot robot = new Robot();
        robot.delay(2000);
        robot.mouseMove(250, 250);
        robot.mousePress(InputEvent.BUTTON1_MASK);
        robot.delay(1000);
        for (int y = 250; y < 350; y+=5) {
                robot.mouseMove(250, y);
                robot.delay(100);
            }
            robot.mouseRelease(InputEvent.BUTTON1_MASK);
        if (dropTargetPanel.isDropActionChangedTriggered()) {
            throw new RuntimeException("The test failed.");
        }
    }
}

class DragSourceButton extends Button implements Serializable,
                                                 Transferable,
                                                 DragGestureListener,
                                                 DragSourceListener {
    private final DataFlavor dataflavor =
        new DataFlavor(Button.class, "DragSourceButton");

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

    final Dimension preferredDimension = new Dimension(200, 100);

    public DragSourcePanel() {
        setLayout(new GridLayout(1, 1));
        add(new DragSourceButton());
    }

    public Dimension getPreferredSize() {
        return preferredDimension;
    }
}

class DropTargetPanel extends Panel implements DropTargetListener {

    final Dimension preferredDimension = new Dimension(200, 100);
    private boolean dropActionChangedTriggered = false;

    public DropTargetPanel() {
        setDropTarget(new DropTarget(this, this));
    }

    public Dimension getPreferredSize() {
        return preferredDimension;
    }

    public void dragEnter(DropTargetDragEvent dtde) {}

    public void dragExit(DropTargetEvent dte) {}

    public void dragOver(DropTargetDragEvent dtde) {}

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

    public void dropActionChanged(DropTargetDragEvent dtde) {
        dropActionChangedTriggered = true;
        throw new RuntimeException("dropActionChanged triggered");
    }

    public boolean isDropActionChangedTriggered() {
        return dropActionChangedTriggered;
    }
}
