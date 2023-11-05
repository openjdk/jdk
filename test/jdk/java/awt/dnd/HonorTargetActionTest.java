/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;
import java.awt.datatransfer.StringSelection;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceAdapter;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceListener;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.InputEvent;

/*
  @test
  @bug 4869264
  @summary tests that DragSourceDragEvent.getDropAction() accords to its new spec
           (does not depend on the user drop action)
  @key headful
  @run main/othervm HonorTargetActionTest
*/

public class HonorTargetActionTest extends Frame {

    private static final int FRAME_ACTIVATION_TIMEOUT = 3000;

    private boolean dragOverCalled;
    private int dropAction;

    volatile Frame frame;
    volatile Point startPoint;
    volatile Point endPoint;

    public static void main(String[] args) throws Exception {
        HonorTargetActionTest test = new HonorTargetActionTest();
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
        DragSourceListener dragSourceListener = new DragSourceAdapter() {
            public void dragOver(DragSourceDragEvent dsde) {
                dragOverCalled = true;
                dropAction = dsde.getDropAction();
            }
        };

        DragGestureListener dragGestureListener = new DragGestureListener() {
            public void dragGestureRecognized(DragGestureEvent dge) {
                dge.startDrag(null, new StringSelection("OOKK"), dragSourceListener);
            }
        };

        new DragSource().createDefaultDragGestureRecognizer(frame,
                DnDConstants.ACTION_COPY_OR_MOVE, dragGestureListener);


        DropTargetAdapter dropTargetListener = new DropTargetAdapter() {
            public void dragEnter(DropTargetDragEvent dtde) {
                dtde.acceptDrag(DnDConstants.ACTION_COPY);
            }

            public void dragOver(DropTargetDragEvent dtde) {
                dtde.acceptDrag(DnDConstants.ACTION_COPY);
            }

            public void drop(DropTargetDropEvent dtde) {
                dtde.acceptDrop(DnDConstants.ACTION_COPY);
            }
        };

        new DropTarget(frame, dropTargetListener);

        dragOverCalled = false;
        dropAction = 0;
        frame = new Frame("Drag Test Frame");

        setTitle("HonorTargetActionTest");
        setSize (200,200);
        setLayout (new BorderLayout());
        setVisible(true);
        validate();

        frame.setBounds(100, 100, 200, 200);
        frame.setVisible(true);
    }


    public void start() throws Exception {
        Robot robot = new Robot();
        robot.waitForIdle();

        Thread.sleep(FRAME_ACTIVATION_TIMEOUT);

        EventQueue.invokeAndWait(() -> {
            startPoint = frame.getLocationOnScreen();
        });
        endPoint = new Point(startPoint);
        robot.waitForIdle();

        startPoint.translate(50, 50);
        endPoint.translate(150, 150);

        robot.mouseMove(startPoint.x, startPoint.y);
        robot.mousePress(InputEvent.BUTTON1_MASK);
        for (Point p = new Point(startPoint); !p.equals(endPoint);
             p.translate(sign(endPoint.x - p.x),
                         sign(endPoint.y - p.y))) {
            robot.mouseMove(p.x, p.y);
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
              e.printStackTrace();
            }
        }

        robot.mouseRelease(InputEvent.BUTTON1_MASK);

        boolean failed = dragOverCalled && dropAction != DnDConstants.ACTION_COPY;

        if (failed) {
            throw new RuntimeException("test failed: dropAction=" + dropAction);
        } else {
            System.err.println("test passed");
        }

    }


    public static int sign(int n) {
        return n < 0 ? -1 : n == 0 ? 0 : 1;
    }

}
