/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @key headful
 * @bug 8024163
 * @summary Checks that dragExit is generated when the new DropTarget is created under the drag
 * @library ../../regtesthelpers
 * @build Util
 * @compile MissedDragExitTest.java
 * @run main/othervm MissedDragExitTest
 * @author Petr Pchelko
 */

import test.java.awt.regtesthelpers.Util;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Frame;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Robot;
import java.awt.datatransfer.StringSelection;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.event.InputEvent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;

public class MissedDragExitTest {

    private static final int FRAME_SIZE = 100;
    private static final int FRAME_LOCATION = 100;

    private static volatile Frame f;
    private static CountDownLatch dragLatch = new CountDownLatch(2);

    private static void initAndShowUI() {
        f = new Frame("Test frame");
        f.setUndecorated(true);
        f.setBounds(FRAME_LOCATION,FRAME_LOCATION,FRAME_SIZE,FRAME_SIZE);

        final DraggablePanel dragSource = new DraggablePanel();
        dragSource.setBackground(Color.yellow);
        DropTarget dt = new DropTarget(dragSource, new DropTargetAdapter() {
            @Override public void drop(DropTargetDropEvent dtde) { }

            @Override
            public void dragExit(DropTargetEvent dte) {
                System.out.println("Drag Exit");
                dragLatch.countDown();
            }

            @Override
            public void dragOver(DropTargetDragEvent dtde) {
                Panel newDropTargetPanel = new Panel();
                final DropTarget dropTarget = new DropTarget(null,new DropTargetAdapter() {
                    @Override
                    public void drop(DropTargetDropEvent dtde) {
                        System.out.println("Drop complete");
                        dragLatch.countDown();
                    }
                });
                newDropTargetPanel.setDropTarget(dropTarget);
                newDropTargetPanel.setBackground(Color.red);
                newDropTargetPanel.setSize(FRAME_SIZE, FRAME_SIZE);
                dragSource.add(newDropTargetPanel);
            }
        });
        dragSource.setDropTarget(dt);
        f.add(dragSource);

        f.setAlwaysOnTop(true);
        f.setVisible(true);
    }

    public static void main(String[] args) throws Throwable {
        try {

            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    initAndShowUI();
                }
            });

            Robot r = new Robot();
            Util.waitForIdle(r);
            Util.drag(r,
                    new Point(FRAME_LOCATION + FRAME_SIZE / 3, FRAME_LOCATION + FRAME_SIZE / 3),
                    new Point(FRAME_LOCATION + FRAME_SIZE / 3 * 2, FRAME_LOCATION + FRAME_SIZE / 3 * 2),
                    InputEvent.BUTTON1_DOWN_MASK);
            Util.waitForIdle(r);
            if(!dragLatch.await(10, TimeUnit.SECONDS)) {
                throw new RuntimeException("Failed. Drag exit was not called" );
            }
        } finally {
            if (f != null) {
                f.dispose();
            }
        }
    }

    private static class DraggablePanel extends Panel implements DragGestureListener {

        public DraggablePanel() {
            (new DragSource()).createDefaultDragGestureRecognizer(this, DnDConstants.ACTION_COPY, this);
        }

        @Override
        public void dragGestureRecognized(DragGestureEvent dge) {
            dge.startDrag(Cursor.getDefaultCursor(), new StringSelection("test"));
        }
    }
}
