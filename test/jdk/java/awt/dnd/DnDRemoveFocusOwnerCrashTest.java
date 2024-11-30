/*
 * Copyright (c) 2000, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 4357905
 * @summary Tests that removal of the focus owner component during
 *          drop processing doesn't cause crash
 * @key headful
 * @run main DnDRemoveFocusOwnerCrashTest
 */

import java.awt.Button;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
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
import java.io.Serializable;

public class DnDRemoveFocusOwnerCrashTest {
    public static final int FRAME_ACTIVATION_TIMEOUT = 1000;
    public static Frame frame;
    public static Robot robot;
    public static DragSourceButton dragSourceButton;
    static volatile Point p;

    public static void main(String[] args) throws Exception {
        try {
            robot = new Robot();
            robot.setAutoWaitForIdle(true);
            robot.delay(FRAME_ACTIVATION_TIMEOUT);
            EventQueue.invokeAndWait(() -> {
                frame = new Frame();
                dragSourceButton = new DragSourceButton();
                DropTargetPanel dropTargetPanel =
                        new DropTargetPanel(dragSourceButton);
                frame.add(new Button("Test"));
                frame.setTitle("Remove Focus Owner Test Frame");
                frame.setLocation(200, 200);
                frame.add(dropTargetPanel);
                frame.pack();
                frame.setVisible(true);
            });

            robot.waitForIdle();
            robot.delay(FRAME_ACTIVATION_TIMEOUT);

            EventQueue.invokeAndWait(() -> {
                p = dragSourceButton.getLocationOnScreen();
                p.translate(10, 10);
            });

            robot.delay(FRAME_ACTIVATION_TIMEOUT);
            robot.mouseMove(p.x, p.y);
            robot.delay(FRAME_ACTIVATION_TIMEOUT);
            robot.keyPress(KeyEvent.VK_CONTROL);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            for (int dy = 0; dy < 50; dy++) {
                robot.mouseMove(p.x, p.y + dy);
                robot.delay(10);
            }
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.keyRelease(KeyEvent.VK_CONTROL);
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    static class DragSourceButton extends Button implements Serializable,
            Transferable,
            DragGestureListener,
            DragSourceListener {

        private static DataFlavor dataflavor;

        static {
            try {
                dataflavor =
                        new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType);
                dataflavor.setHumanPresentableName("Local Object Flavor");
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
                throw new ExceptionInInitializerError();
            }
        }

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

        public void dragEnter(DragSourceDragEvent dsde) {
        }

        public void dragExit(DragSourceEvent dse) {
        }

        public void dragOver(DragSourceDragEvent dsde) {
        }

        public void dragDropEnd(DragSourceDropEvent dsde) {
        }

        public void dropActionChanged(DragSourceDragEvent dsde) {
        }

        public Object getTransferData(DataFlavor flavor)
                throws UnsupportedFlavorException {

            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }

            return this;
        }

        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{dataflavor};
        }

        public boolean isDataFlavorSupported(DataFlavor dflavor) {
            return dataflavor.equals(dflavor);
        }
    }

    static class DropTargetPanel extends Panel implements DropTargetListener {

        public DropTargetPanel(DragSourceButton button) {
            setLayout(new FlowLayout(FlowLayout.CENTER, 50, 50));
            add(button);
            setDropTarget(new DropTarget(this, this));
        }

        public void dragEnter(DropTargetDragEvent dtde) {
        }

        public void dragExit(DropTargetEvent dte) {
        }

        public void dragOver(DropTargetDragEvent dtde) {
        }

        public void dropActionChanged(DropTargetDragEvent dtde) {
        }

        public void drop(DropTargetDropEvent dtde) {
            removeAll();

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
            validate();
        }
    }
}
