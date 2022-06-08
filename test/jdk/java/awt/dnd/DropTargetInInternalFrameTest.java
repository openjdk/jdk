/*
 * Copyright (c) 2000, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
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
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/*
  @test
  @bug 4395279
  @summary Tests that a drop target in InternalFrame functions properly
  @key headful
  @run main DropTargetInInternalFrameTest
*/
public class DropTargetInInternalFrameTest implements Serializable {
    private static final CountDownLatch dropLatch = new CountDownLatch(1);
    private static final CountDownLatch focusLatch = new CountDownLatch(1);
    private static JFrame frame;
    private static JInternalFrame sourceFrame;
    private static JInternalFrame targetFrame;
    private static DragSourcePanel dragSourcePanel;
    private static DropTargetPanel dropTargetPanel;
    private static Robot robot;

    private static void createUI() {
        frame = new JFrame("Test frame");
        sourceFrame = new JInternalFrame("Source");
        targetFrame = new JInternalFrame("Destination");
        dragSourcePanel = new DragSourcePanel();
        dropTargetPanel = new DropTargetPanel(dropLatch);
        JDesktopPane desktopPane = new JDesktopPane();

        sourceFrame.getContentPane().setLayout(new GridLayout(3, 1));

        // add panels to content panes
        sourceFrame.getContentPane().add(dragSourcePanel);
        targetFrame.getContentPane().add(dropTargetPanel);

        sourceFrame.setSize(200, 200);
        targetFrame.setSize(200, 200);
        targetFrame
                .setLocation(sourceFrame.getX() + sourceFrame.getWidth() + 10,
                             sourceFrame.getY());

        desktopPane.add(sourceFrame);
        desktopPane.add(targetFrame);

        frame.setTitle("Test frame");
        frame.setBounds(200, 200, 450, 250);
        frame.getContentPane().add(desktopPane);
        frame.setAlwaysOnTop(true);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        sourceFrame.setVisible(true);
        targetFrame.setVisible(true);
        frame.setVisible(true);
        dragSourcePanel.dragSourceButton.requestFocusInWindow();
    }

    public static void main(String[] argv) throws Exception {
        SwingUtilities.invokeAndWait(DropTargetInInternalFrameTest::createUI);

        robot = new Robot();
        robot.setAutoDelay(50);
        robot.setAutoWaitForIdle(true);
        robot.waitForIdle();
        if (!focusLatch.await(5, TimeUnit.SECONDS)) {
            captureScreen();
            SwingUtilities
                    .invokeAndWait(DropTargetInInternalFrameTest::disposeFrame);
            System.out.println(
                    "Test failed, waited too long for the drag button to gain focus");
        }
        final AtomicReference<Point> p1Ref = new AtomicReference<>();
        final AtomicReference<Point> p2Ref = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            final Point dragLocation =
                    dragSourcePanel.dragSourceButton.getLocationOnScreen();
            Dimension d1 = dragSourcePanel.dragSourceButton.getSize();
            dragLocation.translate(d1.width / 2, d1.height / 2);
            p1Ref.set(dragLocation);
            final Point dropLocation = dropTargetPanel.getLocationOnScreen();
            dropLocation.translate(d1.width / 2, d1.height / 2);
            p2Ref.set(dropLocation);
        });
        Point p1 = p1Ref.get();
        Point p2 = p2Ref.get();

        dragAndDrop(p1, p2);

        if (!dropLatch.await(5, TimeUnit.SECONDS)) {
            captureScreen();
            System.out.println("Test Failed, Waited too long for the Drop to complete");
        }
        int calledMethods = dropTargetPanel.getCalledMethods();
        SwingUtilities
                .invokeAndWait(DropTargetInInternalFrameTest::disposeFrame);
        System.out.println("CalledMethods = " + calledMethods);
        if ((calledMethods & DropTargetPanel.ENTER_CALLED) == 0) {
            throw new RuntimeException(
                    "Test Failed, DropTargetListener.dragEnter() not called");
        }
        if ((calledMethods & DropTargetPanel.OVER_CALLED) == 0) {
            throw new RuntimeException(
                    "Test Failed, DropTargetListener.dragOver() not called");
        }
        if ((calledMethods & DropTargetPanel.DROP_CALLED) == 0) {
            throw new RuntimeException(
                    "Test Failed, DropTargetListener.drop() not called.");
        }

        System.out.println("Test Passed");
    }

    private static void dragAndDrop(final Point p1, final Point p2) {
        robot.mouseMove(p1.x, p1.y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        int dx = 1;
        while (p1.x < p2.x) {
            p1.translate(dx, 0);
            robot.mouseMove(p1.x, p1.y);
            dx++;
        }
        robot.mouseMove(p2.x, p2.y);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    private static void captureScreen() {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        try {
            ImageIO.write(robot.createScreenCapture(
                    new Rectangle(0, 0, screenSize.width, screenSize.height)),
                          "png", new File("screenImage.png"));
        } catch (IOException ignore) {
        }
    }

    private static void disposeFrame() {
        sourceFrame.dispose();
        targetFrame.dispose();
        frame.dispose();
    }

    private static class DragSourcePanel extends JPanel {

        final Dimension preferredDimension = new Dimension(200, 100);
        final DragSourceButton dragSourceButton = new DragSourceButton();

        public DragSourcePanel() {
            setLayout(new GridLayout(1, 1));
            dragSourceButton.addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(final FocusEvent e) {
                    super.focusGained(e);
                    focusLatch.countDown();
                }
            });
            add(dragSourceButton);
        }

        public Dimension getPreferredSize() {
            return preferredDimension;
        }

    }

    private static class DropTargetPanel extends JPanel
            implements DropTargetListener {

        public static final int ENTER_CALLED = 0x1;
        public static final int OVER_CALLED = 0x2;
        public static final int DROP_CALLED = 0x4;
        private final Dimension preferredDimension = new Dimension(200, 100);
        private final CountDownLatch dropLatch;
        private volatile int calledMethods = 0;

        public DropTargetPanel(final CountDownLatch dropLatch) {
            this.dropLatch = dropLatch;
            setDropTarget(new DropTarget(this, this));
        }

        public Dimension getPreferredSize() {
            return preferredDimension;
        }

        public void dragEnter(DropTargetDragEvent dtde) {
            calledMethods |= ENTER_CALLED;
        }

        public void dragOver(DropTargetDragEvent dtde) {
            calledMethods |= OVER_CALLED;
        }

        public void dropActionChanged(DropTargetDragEvent dtde) {
        }

        public void dragExit(DropTargetEvent dte) {
        }

        public void drop(DropTargetDropEvent dtde) {
            System.out.println("Drop!!!!!!!!!!!! ");
            calledMethods |= DROP_CALLED;
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
                    comp = (Component) transfer.getTransferData(dfs[0]);
                } catch (Throwable e) {
                    e.printStackTrace();
                    dtc.dropComplete(false);
                }
            }
            dtc.dropComplete(true);
            add(comp);
            dropLatch.countDown();
        }

        public int getCalledMethods() {
            return calledMethods;
        }

    }

    private static class DragSourceButton extends JButton
            implements Serializable, Transferable, DragGestureListener,
                       DragSourceListener {
        private final DataFlavor dataflavor =
                new DataFlavor(Button.class, "DragSourceButton");

        public DragSourceButton() {
            this("DragSourceButton");
        }

        public DragSourceButton(String str) {
            super(str);
            DragSource ds = DragSource.getDefaultDragSource();
            ds.createDefaultDragGestureRecognizer(this,
                                                  DnDConstants.ACTION_COPY,
                                                  this);
        }

        public void dragGestureRecognized(DragGestureEvent dge) {
            dge.startDrag(new Cursor(Cursor.HAND_CURSOR), this, this);
        }

        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{dataflavor};
        }

        public boolean isDataFlavorSupported(DataFlavor dflavor) {
            return dataflavor.equals(dflavor);
        }

        public Object getTransferData(DataFlavor flavor)
                throws UnsupportedFlavorException, IOException {

            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            Object retObj;
            ByteArrayOutputStream baoStream = new ByteArrayOutputStream();
            ObjectOutputStream ooStream = new ObjectOutputStream(baoStream);
            ooStream.writeObject(this);

            ByteArrayInputStream baiStream =
                    new ByteArrayInputStream(baoStream.toByteArray());
            ObjectInputStream ois = new ObjectInputStream(baiStream);
            try {
                retObj = ois.readObject();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
                throw new RuntimeException(e.toString());
            }
            return retObj;
        }

        @Override
        public void dragEnter(final DragSourceDragEvent dsde) {

        }

        @Override
        public void dragOver(final DragSourceDragEvent dsde) {

        }

        @Override
        public void dropActionChanged(final DragSourceDragEvent dsde) {

        }

        @Override
        public void dragExit(final DragSourceEvent dse) {

        }

        @Override
        public void dragDropEnd(final DragSourceDropEvent dsde) {

        }

    }

}
