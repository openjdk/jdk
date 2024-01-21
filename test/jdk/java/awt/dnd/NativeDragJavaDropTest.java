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

import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Label;
import java.awt.Point;
import java.awt.Robot;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetContext;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.InputEvent;

/*
  @test
  @bug 4399700
  @summary tests that Motif drag support for label widget doesn't cause
           crash when used for drag and drop from label to Java drop target
  @key headful
  @run main NativeDragJavaDropTest
*/

public class NativeDragJavaDropTest {

    volatile Frame frame;
    volatile DropTargetLabel label;
    volatile Point p;
    volatile Dimension d;
    public static final int FRAME_ACTIVATION_TIMEOUT = 1000;
    public static final int DRAG_START_TIMEOUT = 500;
    public static final int DROP_COMPLETION_TIMEOUT = 2000;

    public static void main(String[] args) throws Exception {
        NativeDragJavaDropTest test = new NativeDragJavaDropTest();
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
        label = new DropTargetLabel();
        frame.setTitle("NativeDragJavaDropTest");
        frame.setLocation(200, 200);
        frame.add(label);

        frame.pack();
        frame.setVisible(true);
    }

    public void start() throws Exception {
        Robot robot = new Robot();
        robot.waitForIdle();
        Thread.sleep(FRAME_ACTIVATION_TIMEOUT);

        EventQueue.invokeAndWait(() -> {
            p = label.getLocationOnScreen();
            d = label.getSize();
        });

        p.translate(d.width / 2, d.height / 2);

        robot.mouseMove(p.x, p.y);
        robot.mousePress(InputEvent.BUTTON2_MASK);

        Thread.sleep(DRAG_START_TIMEOUT);

        robot.mouseRelease(InputEvent.BUTTON2_MASK);

        Thread.sleep(DROP_COMPLETION_TIMEOUT);
    }
}

class DropTargetLabel extends Label implements DropTargetListener {

    final Dimension preferredDimension = new Dimension(200, 100);

    public DropTargetLabel() {
        super("Label");
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

        DataFlavor[] dfs = dtde.getCurrentDataFlavors();

        if (dfs != null && dfs.length >= 1) {
            Transferable transfer = dtde.getTransferable();

            try {
                Object obj = (Object)transfer.getTransferData(dfs[0]);
            } catch (Throwable e) {
                e.printStackTrace();
                dtc.dropComplete(false);
            }
        }
        dtc.dropComplete(true);
    }
}
