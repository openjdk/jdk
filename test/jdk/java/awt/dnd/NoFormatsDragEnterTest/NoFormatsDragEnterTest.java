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

/*
  @test
  @bug 4702735
  @summary tests that a dragEnter is called even if the source doesn't
           export data in native formats.
  @key headful
  @run main NoFormatsDragEnterTest
*/

import java.awt.AWTException;
import java.awt.Canvas;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Robot;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceAdapter;
import java.awt.dnd.DragSourceListener;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;


public class NoFormatsDragEnterTest {

    Frame frame;
    DragSourcePanel dragSourcePanel;
    DropTargetPanel dropTargetPanel;

    static final int FRAME_ACTIVATION_TIMEOUT = 1000;
    static final int DROP_COMPLETION_TIMEOUT = 1000;

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException, AWTException {
        NoFormatsDragEnterTest noFormatsDragEnterTest = new NoFormatsDragEnterTest();
        EventQueue.invokeAndWait(noFormatsDragEnterTest::init);
        noFormatsDragEnterTest.start();
    }

    public void init() {
        frame = new Frame();
        dragSourcePanel = new DragSourcePanel();
        dropTargetPanel = new DropTargetPanel();

        frame.setTitle("NoFormatsDragEnterTest");
        frame.setLayout(new GridLayout(2, 1));
        frame.add(dragSourcePanel);
        frame.add(dropTargetPanel);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        frame.validate();
    }

    public void start() throws AWTException, InterruptedException,
            InvocationTargetException {
        try {
            Robot robot = new Robot();
            robot.waitForIdle();
            robot.delay(FRAME_ACTIVATION_TIMEOUT);

            final Point srcPoint = dragSourcePanel.getLocationOnScreen();
            Dimension d = dragSourcePanel.getSize();
            srcPoint.translate(d.width / 2, d.height / 2);

            final Point dstPoint = dropTargetPanel.getLocationOnScreen();
            d = dropTargetPanel.getSize();
            dstPoint.translate(d.width / 2, d.height / 2);

            robot.mouseMove(srcPoint.x, srcPoint.y);
            robot.keyPress(KeyEvent.VK_CONTROL);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            for (Point curPoint = new Point(srcPoint);
                 !curPoint.equals(dstPoint);
                 curPoint.translate(sign(dstPoint.x - curPoint.x),
                                    sign(dstPoint.y - curPoint.y))) {
                robot.mouseMove(curPoint.x, curPoint.y);
                robot.delay(100);
            }
            robot.keyRelease(KeyEvent.VK_CONTROL);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

            robot.delay(DROP_COMPLETION_TIMEOUT);
        } finally {
            if (frame != null) {
                EventQueue.invokeAndWait(() -> frame.dispose());
            }
        }

        if (!dropTargetPanel.passed()) {
            throw new RuntimeException("Drop doesn't happen.");
        }
    }

    public static int sign(int n) {
        return n < 0 ? -1 : n > 0 ? 1 : 0;
    }
}

class DragSourcePanel extends Canvas implements DragGestureListener {

    private final Dimension preferredDimension = new Dimension(200, 100);
    private final DragSourceListener listener = new DragSourceAdapter() {};

    public DragSourcePanel() {
        DragSource ds = DragSource.getDefaultDragSource();
        ds.createDefaultDragGestureRecognizer(this,
                                              DnDConstants.ACTION_COPY_OR_MOVE,
                                              this);
    }

    public Dimension getPreferredSize() {
        return preferredDimension;
    }

    public void dragGestureRecognized(DragGestureEvent dge) {
        dge.startDrag(null, new TestTransferable(), listener);
    }
}

class TestTransferable implements Transferable {

    public static DataFlavor dataFlavor = null;
    static final Object data = new Object();

    static {
        DataFlavor df = null;
        try {
            df = new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType +
                                "; class=java.lang.Object");
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError(e);
        }
        dataFlavor = df;
    }

    public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[] { dataFlavor };
    }

    public boolean isDataFlavorSupported(DataFlavor df) {
        return dataFlavor.equals(df);
    }

    public Object getTransferData(DataFlavor df)
      throws UnsupportedFlavorException, IOException {
        if (!isDataFlavorSupported(df)) {
            throw new UnsupportedFlavorException(df);
        }
        return data;
    }
}

class DropTargetPanel extends Canvas implements DropTargetListener {

    private final Dimension preferredDimension = new Dimension(200, 100);
    private boolean dragEnterTriggered = false;
    private boolean dragOverTriggered = false;

    public DropTargetPanel() {
        setDropTarget(new DropTarget(this, this));
    }

    public Dimension getPreferredSize() {
        return preferredDimension;
    }

    public void dragEnter(DropTargetDragEvent dtde) {
        dragEnterTriggered = true;
    }

    public void dragExit(DropTargetEvent dte) {}

    public void dragOver(DropTargetDragEvent dtde) {
        dragOverTriggered = true;
    }

    public void dropActionChanged(DropTargetDragEvent dtde) {}

    public void drop(DropTargetDropEvent dtde) {
        dtde.rejectDrop();
    }

    public boolean passed() {
        // asserts that dragEnter has been called if dragOver has been called.
        return !dragOverTriggered || dragEnterTriggered;
    }
}