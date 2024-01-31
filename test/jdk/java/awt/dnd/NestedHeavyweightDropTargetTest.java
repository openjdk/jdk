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

import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Toolkit;
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
import java.awt.event.AWTEventListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/*
  @test
  @bug 4389284
  @summary tests that drop targets registered on nested heavyweight
           components work properly
  @key headful
  @run main NestedHeavyweightDropTargetTest
*/

public class NestedHeavyweightDropTargetTest {

    volatile Frame frame;
    volatile DragSourceButton dragSourceButton;
    volatile DropTargetPanel dropTargetPanel;
    volatile InnerDropTargetPanel innerDropTargetPanel;
    volatile Button button;
    volatile Dimension d;
    volatile Point srcPoint;
    volatile Point dstPoint;

    static final int DROP_COMPLETION_TIMEOUT = 1000;

    public static void main(String[] args) throws Exception {
        NestedHeavyweightDropTargetTest test = new NestedHeavyweightDropTargetTest();
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
        frame = new Frame();
        dragSourceButton = new DragSourceButton();
        dropTargetPanel = new DropTargetPanel();
        innerDropTargetPanel = new InnerDropTargetPanel();
        button = new Button("button");
        button.setBackground(Color.red);

        innerDropTargetPanel.setLayout(new GridLayout(3, 1));
        innerDropTargetPanel.add(button);
        innerDropTargetPanel.setBackground(Color.yellow);

        dropTargetPanel.setLayout(new GridLayout(2, 1));
        dropTargetPanel.add(innerDropTargetPanel);
        dropTargetPanel.setBackground(Color.green);

        frame.setTitle("NestedHeavyweightDropTargetTest");
        frame.setLocation(200, 200);
        frame.setLayout(new BorderLayout());
        frame.add(dropTargetPanel, BorderLayout.CENTER);
        frame.add(dragSourceButton, BorderLayout.SOUTH);

        frame.pack();

        innerDropTargetPanel.setDropTarget(new DropTarget(innerDropTargetPanel, innerDropTargetPanel));
        dropTargetPanel.setDropTarget(new DropTarget(dropTargetPanel, dropTargetPanel));

        frame.setVisible(true);
    }

    public void start() throws Exception {
        Robot robot = new Robot();
        Util.waitForInit();

        test1(robot);
        test2(robot);
    }

    public static int sign(int n) {
        return n < 0 ? -1 : n == 0 ? 0 : 1;
    }

    public void test1(Robot robot) throws Exception {
        innerDropTargetPanel.setDragEnterTriggered(false);
        innerDropTargetPanel.setDragOverTriggered(false);
        innerDropTargetPanel.setDragExitTriggered(false);
        innerDropTargetPanel.setDropTriggered(false);

        EventQueue.invokeAndWait(() -> {
            srcPoint = dragSourceButton.getLocationOnScreen();
            d = dragSourceButton.getSize();
        });

        srcPoint.translate(d.width / 2, d.height / 2);

        if (!Util.pointInComponent(robot, srcPoint, dragSourceButton)) {
            System.err.println("WARNING: Couldn't locate " + dragSourceButton +
                               " at point " + srcPoint);
            return;
        }

        EventQueue.invokeAndWait(() -> {
            dstPoint = innerDropTargetPanel.getLocationOnScreen();
            d = innerDropTargetPanel.getSize();
        });

        dstPoint.translate(d.width / 2, d.height / 2);

        if (!Util.pointInComponent(robot, dstPoint, innerDropTargetPanel)) {
            System.err.println("WARNING: Couldn't locate " + innerDropTargetPanel +
                               " at point " + dstPoint);
            return;
        }

        robot.mouseMove(srcPoint.x, srcPoint.y);
        robot.keyPress(KeyEvent.VK_CONTROL);
        robot.mousePress(InputEvent.BUTTON1_MASK);
        for (;!srcPoint.equals(dstPoint);
             srcPoint.translate(sign(dstPoint.x - srcPoint.x),
                                sign(dstPoint.y - srcPoint.y))) {
            robot.mouseMove(srcPoint.x, srcPoint.y);
            Thread.sleep(10);
        }
        robot.mouseRelease(InputEvent.BUTTON1_MASK);
        robot.keyRelease(KeyEvent.VK_CONTROL);

        Thread.sleep(DROP_COMPLETION_TIMEOUT);

        if (!innerDropTargetPanel.isDragEnterTriggered()) {
            throw new RuntimeException("child dragEnter() not triggered");
        }

        if (!innerDropTargetPanel.isDragOverTriggered()) {
            throw new RuntimeException("child dragOver() not triggered");
        }

        if (!innerDropTargetPanel.isDropTriggered()) {
            throw new RuntimeException("child drop() not triggered");
        }
    }

    public void test2(Robot robot) throws Exception {
        innerDropTargetPanel.setDragEnterTriggered(false);
        innerDropTargetPanel.setDragOverTriggered(false);
        innerDropTargetPanel.setDragExitTriggered(false);
        innerDropTargetPanel.setDropTriggered(false);

        EventQueue.invokeAndWait(() -> {
            srcPoint = dragSourceButton.getLocationOnScreen();
            d = dragSourceButton.getSize();
        });
        srcPoint.translate(d.width / 2, d.height / 2);

        if (!Util.pointInComponent(robot, srcPoint, dragSourceButton)) {
            System.err.println("WARNING: Couldn't locate " + dragSourceButton +
                               " at point " + srcPoint);
            return;
        }

        EventQueue.invokeAndWait(() -> {
            dstPoint = button.getLocationOnScreen();
            d = button.getSize();
        });

        dstPoint.translate(d.width / 2, d.height / 2);

        if (!Util.pointInComponent(robot, dstPoint, button)) {
            System.err.println("WARNING: Couldn't locate " + button +
                               " at point " + dstPoint);
            return;
        }

        robot.mouseMove(srcPoint.x, srcPoint.y);
        robot.keyPress(KeyEvent.VK_CONTROL);
        robot.mousePress(InputEvent.BUTTON1_MASK);
        for (;!srcPoint.equals(dstPoint);
             srcPoint.translate(sign(dstPoint.x - srcPoint.x),
                                sign(dstPoint.y - srcPoint.y))) {
            robot.mouseMove(srcPoint.x, srcPoint.y);
            Thread.sleep(10);
        }
        robot.mouseRelease(InputEvent.BUTTON1_MASK);
        robot.keyRelease(KeyEvent.VK_CONTROL);

        Thread.sleep(DROP_COMPLETION_TIMEOUT);

        if (!innerDropTargetPanel.isDragEnterTriggered()) {
            throw new RuntimeException("child dragEnter() not triggered");
        }

        if (!innerDropTargetPanel.isDragOverTriggered()) {
            throw new RuntimeException("child dragOver() not triggered");
        }

        if (!innerDropTargetPanel.isDropTriggered()) {
            throw new RuntimeException("child drop() not triggered");
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

class InnerDropTargetPanel extends DropTargetPanel {
    private boolean dragEnterTriggered = false;
    private boolean dragOverTriggered = false;
    private boolean dragExitTriggered = false;
    private boolean dropTriggered = false;

    public void dragEnter(DropTargetDragEvent dtde) {
        setDragEnterTriggered(true);
    }

    public void dragExit(DropTargetEvent dte) {
        setDragExitTriggered(true);
    }

    public void dragOver(DropTargetDragEvent dtde) {
        setDragOverTriggered(true);
    }

    public void dropActionChanged(DropTargetDragEvent dtde) {}

    public void drop(DropTargetDropEvent dtde) {
        setDropTriggered(true);
        dtde.rejectDrop();
    }

    public boolean isDragEnterTriggered() {
        return dragEnterTriggered;
    }

    public boolean isDragOverTriggered() {
        return dragOverTriggered;
    }

    public boolean isDragExitTriggered() {
        return dragExitTriggered;
    }

    public boolean isDropTriggered() {
        return dropTriggered;
    }

    public void setDragEnterTriggered(boolean b) {
        dragEnterTriggered = b;
    }

    public void setDragOverTriggered(boolean b) {
        dragOverTriggered = b;
    }

    public void setDragExitTriggered(boolean b) {
        dragExitTriggered = b;
    }

    public void setDropTriggered(boolean b) {
        dropTriggered = b;
    }
}

class DropTargetPanel extends Panel implements DropTargetListener {

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
}
