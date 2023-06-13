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
  @bug 4215643
  @summary Tests that the drag source receives mouseReleased event
  @key headful
*/

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.EventQueue;
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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;


public class ButtonReleaseTest {

    static volatile ButtonPanelFrame buttonPanelFrame;
    static final int FRAME_ACTIVATION_TIMEOUT = 1000;
    static final int DROP_COMPLETION_TIMEOUT = 4000;

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        robot.setAutoWaitForIdle(true);
        robot.setAutoDelay(20);

        try {
            EventQueue.invokeAndWait(() -> {
                buttonPanelFrame = new ButtonPanelFrame();
                buttonPanelFrame.pack();
                buttonPanelFrame.setVisible(true);
            });

            robot.waitForIdle();
            robot.delay(FRAME_ACTIVATION_TIMEOUT);

            Point p = buttonPanelFrame.getButtonLocation();
            Dimension d = buttonPanelFrame.getButtonSize();
            p.translate(d.width / 2, d.height / 2);
            robot.mouseMove(p.x, p.y);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            for (int i = 0; i < d.width; i++) {
                p.translate(0, 1);
                robot.mouseMove(p.x, p.y);
            }

            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

            try {
                Thread.sleep(DROP_COMPLETION_TIMEOUT);
            } catch (InterruptedException e) {
                throw new RuntimeException("The test failed.");
            }

            if (!buttonPanelFrame.passed()) {
                throw new RuntimeException(
                        "The test failed - mouse release was not received.");
            }

        } finally {
            EventQueue.invokeAndWait(buttonPanelFrame::dispose);
        }
    }
}

class ButtonPanelFrame extends Frame {

    DnDSource dragSource;
    DnDTarget dropTarget;

    ButtonPanelFrame() {
        Panel mainPanel;

        setTitle("ButtonReleaseTest - ButtonPanelFrame");
        setSize(200, 200);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        mainPanel = new Panel();
        mainPanel.setLayout(new BorderLayout());

        mainPanel.setBackground(Color.black);

        dropTarget = new DnDTarget(Color.red, Color.yellow);
        dragSource = new DnDSource("Drag ME!");

        mainPanel.add(dragSource, "North");
        mainPanel.add(dropTarget, "Center");
        add(mainPanel, BorderLayout.CENTER);
    }

    boolean passed() {
        return dragSource.passed();
    }

    Point getButtonLocation() {
        return dragSource.getLocationOnScreen();
    }

    Dimension getButtonSize() {
        return dragSource.getSize();
    }
}

class DnDSource extends Button implements Serializable, Transferable,
                                          DragGestureListener,
                                          DragSourceListener {

    private transient DataFlavor df;
    private transient int dropAction;
    volatile boolean released = false;

    DnDSource(String label) {
        super(label);

        DragSource ds = DragSource.getDefaultDragSource();
        ds.createDefaultDragGestureRecognizer(this, DnDConstants.ACTION_COPY,
                                              this);
        addMouseListener(new MouseAdapter() {
            public void mouseReleased(MouseEvent e) {
                synchronized(this) {
                    released = true;
                    notifyAll();
                }
            }
        });
        setBackground(Color.yellow);
        setForeground(Color.blue);

        df = new DataFlavor(DnDSource.class, "DnDSource");
    }

    public void dragGestureRecognized(DragGestureEvent dge) {
        dge.startDrag(null, this, this);
    }


    public void dragEnter(DragSourceDragEvent dsde) {
        dsde.getDragSourceContext().setCursor(DragSource.DefaultCopyDrop);
    }

    public void dragOver(DragSourceDragEvent dsde) {
    }

    public void dragGestureChanged(DragSourceDragEvent dsde) {
    }

    public void dragExit(DragSourceEvent dsde) {
        dsde.getDragSourceContext().setCursor(null);
    }

    public void dragDropEnd(DragSourceDropEvent dsde) {
    }

    public void dropActionChanged(DragSourceDragEvent dsde) {
    }

    public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[] { df };
    }

    public boolean isDataFlavorSupported(DataFlavor sdf) {
        return df.equals(sdf);
    }

    public Object getTransferData(DataFlavor tdf) throws UnsupportedFlavorException , IOException {

        Object copy = null;

        if (!df.equals(tdf)) {
            throw new UnsupportedFlavorException(tdf);
        }

        Container parent = getParent();
        switch (dropAction) {
            case DnDConstants.ACTION_COPY:
                try {
                    copy = this.clone();
                } catch (CloneNotSupportedException e) {

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ObjectOutputStream    oos  = new ObjectOutputStream(baos);
                    oos.writeObject(this);
                    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
                    ObjectInputStream ois = new ObjectInputStream(bais);

                    try {
                      copy = ois.readObject();
                    } catch (ClassNotFoundException cnfe) {
                      // do nothing
                    }
                }
                return copy;

            case DnDConstants.ACTION_MOVE:
                synchronized(this) {
                    if (parent != null) parent.remove(this);
                }
                return this;

            case DnDConstants.ACTION_LINK:
                return this;

            default:
                return this;
        }
    }

    boolean passed() {
        return !released;
    }
}

class DnDTarget extends Panel implements DropTargetListener {

    Color bgColor;
    Color htColor;

    DnDTarget(Color bgColor, Color htColor) {
        super();
        this.bgColor = bgColor;
        this.htColor = htColor;
        setBackground(bgColor);
        setDropTarget(new DropTarget(this, this));
    }

    public Dimension getPreferredSize() {
        return new Dimension(200, 200);
    }

    public void dragEnter(DropTargetDragEvent e) {
        e.acceptDrag(DnDConstants.ACTION_COPY);
        setBackground(htColor);
        repaint();
    }

    public void dragOver(DropTargetDragEvent e) {
        e.acceptDrag(DnDConstants.ACTION_COPY);
    }

    public void dragExit(DropTargetEvent e) {
        setBackground(bgColor);
        repaint();
    }

    public void drop(DropTargetDropEvent dtde) {
        DropTargetContext dtc = dtde.getDropTargetContext();

        if ((dtde.getSourceActions() & DnDConstants.ACTION_COPY) != 0) {
            dtde.acceptDrop(DnDConstants.ACTION_COPY);
        } else {
            dtde.rejectDrop();
            return;
        }

        DataFlavor[] dfs = dtde.getCurrentDataFlavors();

        if (dfs != null && dfs.length >= 1) {
            Transferable transfer = dtde.getTransferable();
            Object obj = null;

            try {
                obj = transfer.getTransferData(dfs[0]);
            } catch (IOException | UnsupportedFlavorException e) {
                System.err.println(e.getMessage());
                dtc.dropComplete(false);
                return;
            }

            if (obj != null) {
                Button button   = null;

                try {
                    button   = (Button)obj;
                } catch (Exception e) {
                    System.err.println(e.getMessage());
                    dtc.dropComplete(false);
                    return;
                }
                add(button);
                repaint();
            }
        }

        setBackground(bgColor);
        invalidate();
        validate();
        repaint();
        dtc.dropComplete(true);
    }

    public void dragScroll(DropTargetDragEvent e) {
    }

    public void dropActionChanged(DropTargetDragEvent e) {
    }
}
