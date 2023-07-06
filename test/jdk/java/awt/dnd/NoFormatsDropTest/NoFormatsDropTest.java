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
  @bug 4473062
  @summary tests that a drop happens even if the source doesn't export
           data in native formats.
  @key headful
  @run main NoFormatsDropTest
*/

import java.awt.AWTEvent;
import java.awt.AWTException;
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
import java.awt.dnd.DragSourceAdapter;
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
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

public class NoFormatsDropTest implements AWTEventListener {

    Frame frame;
    volatile DragSourcePanel dragSourcePanel;
    volatile DropTargetPanel dropTargetPanel;

    static final int FRAME_ACTIVATION_TIMEOUT = 1000;
    static final int DROP_COMPLETION_TIMEOUT = 1000;
    static final int MOUSE_RELEASE_TIMEOUT = 1000;
    static final Object SYNC_LOCK = new Object();

    Component clickedComponent = null;

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException, AWTException {
        NoFormatsDropTest noFormatsDropTest = new NoFormatsDropTest();
        EventQueue.invokeAndWait(noFormatsDropTest::init);
        noFormatsDropTest.start();
    }

    public void init() {
        frame = new Frame();
        dragSourcePanel = new DragSourcePanel();
        dropTargetPanel = new DropTargetPanel();

        frame.setTitle("NoFormatsDropTest");
        frame.setLayout(new GridLayout(2, 1));
        frame.add(dragSourcePanel);
        frame.add(dropTargetPanel);

        frame.getToolkit().addAWTEventListener(this, AWTEvent.MOUSE_EVENT_MASK);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        frame.validate();
    }

    public void start() throws InterruptedException, AWTException,
            InvocationTargetException {
        try {
            Robot robot = new Robot();
            robot.delay(FRAME_ACTIVATION_TIMEOUT);

            final Point srcPoint = dragSourcePanel.getLocationOnScreen();
            Dimension d = dragSourcePanel.getSize();
            srcPoint.translate(d.width / 2, d.height / 2);

            if (!pointInComponent(robot, srcPoint, dragSourcePanel)) {
                System.err.println("WARNING: Couldn't locate source panel.");
                return;
            }

            final Point dstPoint = dropTargetPanel.getLocationOnScreen();
            d = dropTargetPanel.getSize();
            dstPoint.translate(d.width / 2, d.height / 2);

            if (!pointInComponent(robot, dstPoint, dropTargetPanel)) {
                System.err.println("WARNING: Couldn't locate target panel.");
                return;
            }

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
            robot.waitForIdle();
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

    public void reset() {
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

    boolean pointInComponent(Robot robot, Point p, Component comp)
      throws InterruptedException {
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
}

class DragSourcePanel extends Panel implements DragGestureListener {

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

class DropTargetPanel extends Panel implements DropTargetListener {

    final Dimension preferredDimension = new Dimension(200, 100);
    volatile boolean passed = false;

    public DropTargetPanel() {
        setDropTarget(new DropTarget(this, this));
    }

    public Dimension getPreferredSize() {
        return preferredDimension;
    }

    public void dragEnter(DropTargetDragEvent dtde) {}

    public void dragExit(DropTargetEvent dte) {}

    public void dragOver(DropTargetDragEvent dtde) {}

    public void dropActionChanged(DropTargetDragEvent dtde) {}

    public void drop(DropTargetDropEvent dtde) {
        DropTargetContext dtc = dtde.getDropTargetContext();

        if ((dtde.getSourceActions() & DnDConstants.ACTION_COPY) != 0) {
            dtde.acceptDrop(DnDConstants.ACTION_COPY);
        } else {
            dtde.rejectDrop();
        }

        Transferable transfer = dtde.getTransferable();

        if (transfer.isDataFlavorSupported(TestTransferable.dataFlavor)) {
            try {
                Object data =
                    transfer.getTransferData(TestTransferable.dataFlavor);
                passed = true;
                dtc.dropComplete(true);
            } catch (IOException e) {
                e.printStackTrace();
                dtc.dropComplete(false);
            } catch (UnsupportedFlavorException e) {
                e.printStackTrace();
                dtc.dropComplete(false);
            }
        } else {
            dtc.dropComplete(false);
        }
    }

    boolean passed() {
        return passed;
    }
}