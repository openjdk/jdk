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

/*
  @test
  @bug 4774532
  @summary tests that DropTargetDragEvent.getDropAction() returns correct value
           after DropTargetDragEvent.rejectDrag()
  @key headful
  @run main/timeout=300 RejectDragDropActionTest
*/

import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;
import java.awt.datatransfer.StringSelection;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragGestureRecognizer;
import java.awt.dnd.DragSource;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.InputEvent;


public class RejectDragDropActionTest {

    private static volatile boolean incorrectActionDetected = false;

    private static final int DELAY_TIME = 500;

    private static Frame frame;
    private static DragSource ds;
    private static DragGestureListener dgl;
    private static DragGestureRecognizer dgr;
    private final DropTargetListener dtl = new DropTargetAdapter() {
            public void dragEnter(DropTargetDragEvent dtde) {
                dtde.rejectDrag();
            }
            public void dragOver(DropTargetDragEvent dtde) {
                if (dtde.getDropAction() == DnDConstants.ACTION_NONE) {
                    incorrectActionDetected = true;
                }
            }
            public void drop(DropTargetDropEvent dtde) {
                dtde.rejectDrop();
            }
        };
    private final DropTarget dt = new DropTarget(frame, dtl);

    public static void main(String[] args) throws Exception {
        EventQueue.invokeAndWait(() -> {
            frame = new Frame("RejectDragDropActionTest");
            ds = DragSource.getDefaultDragSource();
            dgl = dge -> dge.startDrag(null, new StringSelection("OOKK"));
            dgr = ds.createDefaultDragGestureRecognizer(frame,
                    DnDConstants.ACTION_COPY, dgl);
            frame.setSize(200, 200);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });

        try {
            Robot robot = new Robot();
            robot.setAutoWaitForIdle(true);
            robot.waitForIdle();
            robot.delay(DELAY_TIME);

            Point startPoint = frame.getLocationOnScreen();
            Point endPoint = new Point(startPoint);
            startPoint.translate(50, 50);
            endPoint.translate(150, 150);

            robot.mouseMove(startPoint.x, startPoint.y);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            for (Point p = new Point(startPoint);
                 !p.equals(endPoint) && !incorrectActionDetected;
                 p.translate(sign(endPoint.x - p.x),
                             sign(endPoint.y - p.y))) {
                robot.mouseMove(p.x, p.y);
            }

            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }

        if (incorrectActionDetected) {
            throw new RuntimeException("User action reported incorrectly.");
        }
    }

    public static int sign(int n) {
        return n < 0 ? -1 : n == 0 ? 0 : 1;
    }
}
