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
  @bug 4516490
  @summary verifies that autoscroll is stopped when the drop happens
  @key headful
  @run main AutoscrollStopTest
*/


import java.awt.AWTException;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Robot;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.Autoscroll;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragGestureRecognizer;
import java.awt.dnd.DragSource;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.InputEvent;


public class AutoscrollStopTest extends Frame implements Autoscroll {
    static volatile AutoscrollStopTest test = null;

    volatile boolean dropHappened = false;

    final DragSource dragSource = DragSource.getDefaultDragSource();
    final Transferable transferable = new StringSelection("TEXT");
    final DragGestureListener dragGestureListener = new DragGestureListener() {
            public void dragGestureRecognized(DragGestureEvent dge) {
                dge.startDrag(null, transferable);
            }
        };
    final DragGestureRecognizer dragGestureRecognizer =
            dragSource.createDefaultDragGestureRecognizer(
                    this, DnDConstants.ACTION_COPY_OR_MOVE,
                    dragGestureListener);

    final DropTargetListener dropTargetListener = new DropTargetAdapter() {
        public void drop(DropTargetDropEvent e) {
            e.rejectDrop();
            dropHappened = true;
        }
    };

    final DropTarget dropTarget = new DropTarget(this, dropTargetListener);

    public static void main(String[] args) throws Exception {
        EventQueue.invokeAndWait(() -> {
            test = new AutoscrollStopTest();
            test.createUI();
        });
        try {
            test.start();
        } finally {
            EventQueue.invokeAndWait(test::dispose);
        }
    }

    public static int sign(int n) {
        return Integer.compare(n, 0);
    }

    public void createUI() {
        setTitle("AutoscrollStopTest");
        setMinimumSize(new Dimension(200, 200));
        setLocationRelativeTo(null);
        setVisible(true);
    }

    public void start() throws AWTException {
        final Robot robot = new Robot();
        try {
            robot.setAutoWaitForIdle(true);
            robot.setAutoDelay(50);
            robot.waitForIdle();
            robot.delay(1000);

            final Point srcPoint = this.getLocationOnScreen();
            final Dimension d = this.getSize();
            srcPoint.translate(d.width / 2, d.height / 2);

            final Point dstPoint = new Point(srcPoint);
            dstPoint.translate(d.width / 4, d.height / 4);

            robot.mouseMove(srcPoint.x, srcPoint.y);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);

            for (;!srcPoint.equals(dstPoint);
                 srcPoint.translate(sign(dstPoint.x - srcPoint.x),
                                    sign(dstPoint.y - srcPoint.y))) {
                robot.mouseMove(srcPoint.x, srcPoint.y);
            }
        } finally {
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        }
    }

    public Insets getAutoscrollInsets() {
        final Dimension d = this.getSize();
        return new Insets(d.height / 2, d.width / 2,
                d.height / 2, d.width / 2);
    }

    public void autoscroll(Point cursorLocation) {
        if (dropHappened) {
            throw new RuntimeException("Test failed");
        }
    }
}
