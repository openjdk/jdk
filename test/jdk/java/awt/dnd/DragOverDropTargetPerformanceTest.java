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

/*
  @test
  @bug 4445747
  @summary tests that drag over drop target is not very slow on Win9X/WinME
  @key headful
*/

import java.awt.Button;
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
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.dnd.InvalidDnDOperationException;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.Serializable;

public class DragOverDropTargetPerformanceTest {

    Frame frame;
    volatile DragSourceButton dragSourceButton;
    volatile DropTargetPanel dropTargetPanel;

    static final int FRAME_ACTIVATION_TIMEOUT = 1000;
    static final int DROP_COMPLETION_TIMEOUT = 1000;

    public static void main(String[] args) throws Exception {
        DragOverDropTargetPerformanceTest test =
                new DragOverDropTargetPerformanceTest();

        EventQueue.invokeAndWait(test::init);
        try {
            test.start();
        } finally {
            EventQueue.invokeAndWait(()-> test.frame.dispose());
        }
    }

    public void init() {
        dragSourceButton = new DragSourceButton();
        dropTargetPanel = new DropTargetPanel();

        frame  = new Frame();
        frame.setTitle("DragOverDropTargetPerformanceTest frame");
        frame.setLocation(200, 200);
        frame.setLayout(new GridLayout(2, 1));
        frame.add(dragSourceButton);
        frame.add(dropTargetPanel);

        frame.pack();
        frame.setVisible(true);
    }

    public static int sign(int n) {
        return Integer.compare(n, 0);
    }

    public void start() throws Exception {
        Robot robot = new Robot();
        robot.setAutoDelay(10);
        robot.waitForIdle();
        robot.delay(FRAME_ACTIVATION_TIMEOUT);

        Point srcPoint = dragSourceButton.getLocationOnScreen();
        Dimension d = dragSourceButton.getSize();
        srcPoint.translate(d.width / 2, d.height / 2);

        Point dstPoint = dropTargetPanel.getLocationOnScreen();
        d = dropTargetPanel.getSize();
        dstPoint.translate(d.width / 2, d.height / 2);

        robot.mouseMove(srcPoint.x, srcPoint.y);
        robot.keyPress(KeyEvent.VK_CONTROL);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);

        for (;!srcPoint.equals(dstPoint);
             srcPoint.translate(sign(dstPoint.x - srcPoint.x),
                                sign(dstPoint.y - srcPoint.y))) {
            robot.mouseMove(srcPoint.x, srcPoint.y);
            robot.delay(10);
        }
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.keyRelease(KeyEvent.VK_CONTROL);

        robot.delay(DROP_COMPLETION_TIMEOUT);

        long dstime = dragSourceButton.getDragSourceTime();
        long dttime = dragSourceButton.getDropTargetTime();
        if (dstime == 0 || dttime == 0) {
            System.err.println(
                    "WARNING: couldn't emulate DnD to measure performance.");
        } else if (dttime > dstime * 4) {
            throw new RuntimeException("The test failed." +
                                       "Over drag source: " + dstime + "." +
                                       "Over drop target: " + dttime);
        }
    }
}

class DragSourceButton extends Button implements Serializable,
                                                 Transferable,
                                                 DragGestureListener,
                                                 DragSourceListener {
    private final DataFlavor dataflavor =
        new DataFlavor(Button.class, "DragSourceButton");
    private volatile long dsTime = 0;
    private volatile long dtTime = 0;

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
        try {
            dge.startDrag(null, this, this);
            dsTime = System.currentTimeMillis();
        } catch (InvalidDnDOperationException e) {
            e.printStackTrace();
        }
    }

    public void dragEnter(DragSourceDragEvent dsde) {
        long currentTime = System.currentTimeMillis();
        dsTime = currentTime - dsTime;
        dtTime = currentTime;
    }

    public void dragExit(DragSourceEvent dse) {}

    public void dragOver(DragSourceDragEvent dsde) {}

    public void dragDropEnd(DragSourceDropEvent dsde) {
        long currentTime = System.currentTimeMillis();
        dtTime = currentTime - dtTime;
    }

    public void dropActionChanged(DragSourceDragEvent dsde) {}

    public Object getTransferData(DataFlavor flavor)
      throws UnsupportedFlavorException, IOException {

        if (!isDataFlavorSupported(flavor)) {
            throw new UnsupportedFlavorException(flavor);
        }

        return this;
    }

    public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[] { dataflavor };
    }

    public boolean isDataFlavorSupported(DataFlavor dflavor) {
        return dataflavor.equals(dflavor);
    }

    public long getDragSourceTime() {
        return dsTime;
    }

    public long getDropTargetTime() {
        return dtTime;
    }
}

class DropTargetPanel extends Panel {

    final Dimension preferredDimension = new Dimension(200, 200);
    final DropTargetListener dtl = new DropTargetAdapter() {
            public void drop(DropTargetDropEvent dtde) {
                dtde.rejectDrop();
            }
        };

    public DropTargetPanel() {
        setDropTarget(new DropTarget(this, dtl));
    }

    public Dimension getPreferredSize() {
        return preferredDimension;
    }

}
