/*
 * Copyright (c) 2002, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;
import java.awt.datatransfer.StringSelection;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceAdapter;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceEvent;
import java.awt.dnd.DragSourceListener;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.InputEvent;

/*
 * @test
 * @key headful
 * @bug 4407521
 * @summary Tests that DragSourceListener.dragEnter() and
            DragSourceListener.dragOver() are not called after
            drag rejecting, but DragSourceListener.dragExit() is.
 */

public class RejectDragTest {
    private static Frame frame;
    private static Robot robot;
    private static volatile boolean dragEnterCalled;
    private static volatile boolean dragOverCalled;
    private static volatile boolean dragExitCalledAtFirst;
    private static volatile Point startPoint;
    private static volatile Point endPoint;

    public static void main(String[] args) throws Exception {
        try {
            robot = new Robot();

            EventQueue.invokeAndWait(RejectDragTest::createAndShowUI);
            robot.waitForIdle();
            robot.delay(1000);

            EventQueue.invokeAndWait(RejectDragTest::addDnDListeners);
            robot.waitForIdle();

            testDnD();
            robot.waitForIdle();
            robot.delay(200);
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static void addDnDListeners() {
        final DragSourceListener dragSourceListener = new DragSourceAdapter() {
            private boolean first = true;

            public void dragEnter(DragSourceDragEvent dsde) {
                first = false;
                dragEnterCalled = true;
            }

            public void dragExit(DragSourceEvent dse) {
                if (first) {
                    dragExitCalledAtFirst = true;
                    first = false;
                }
            }

            public void dragDropEnd(DragSourceDropEvent dsde) {
                first = false;
            }

            public void dragOver(DragSourceDragEvent dsde) {
                first = false;
                dragOverCalled = true;
            }

            public void dropActionChanged(DragSourceDragEvent dsde) {
                first = false;
            }
        };

        DragGestureListener dragGestureListener =
                dge -> dge.startDrag(null, new StringSelection("OKAY"),
                                     dragSourceListener);
        new DragSource().createDefaultDragGestureRecognizer(frame,
                                                            DnDConstants.ACTION_COPY,
                                                            dragGestureListener);

        DropTargetAdapter dropTargetListener = new DropTargetAdapter() {
            public void dragEnter(DropTargetDragEvent dtde) {
               dtde.rejectDrag();
            }

            public void drop(DropTargetDropEvent dtde) {
               dtde.rejectDrop();
            }
        };

        new DropTarget(frame, dropTargetListener);
    }

    private static void createAndShowUI() {
        frame = new Frame("RejectDragTest");
        frame.setSize(200, 200);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static void testDnD() throws Exception {
        EventQueue.invokeAndWait(() -> {
            Point start = frame.getLocationOnScreen();
            start.translate(50, 50);
            startPoint = start;

            Point end = new Point(start);
            end.translate(150, 150);
            endPoint = end;
        });

        robot.mouseMove(startPoint.x, startPoint.y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        for (Point p = new Point(startPoint); !p.equals(endPoint);
             p.translate(sign(endPoint.x - p.x),
                         sign(endPoint.y - p.y))) {
            robot.mouseMove(p.x, p.y);
            robot.delay(30);
        }
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

        if (dragEnterCalled || dragOverCalled) {
            throw new RuntimeException("Test failed: " +
                                       (dragEnterCalled ? "DragSourceListener.dragEnter() was called; " : "") +
                                       (dragOverCalled ? "DragSourceListener.dragOver() was called; " : "") +
                                       (!dragExitCalledAtFirst ? "DragSourceListener.dragExit() was not " +
                                                                 "called immediately after rejectDrag() " : ""));
        }
    }

    public static int sign(int n) {
        return Integer.compare(n, 0);
    }
}
