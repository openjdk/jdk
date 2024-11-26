/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Panel;
import java.awt.Point;
import java.awt.Robot;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

/*
  @test
  @key headful
  @bug 4415175
  @summary tests DragSource.getDragThreshold() and
           that the AWT default drag gesture recognizers
           honor the drag gesture motion threshold
*/

public class DragThresholdTest {
    private static Frame frame;
    private static Panel panel;
    private static MouseEvent lastMouseEvent;
    private static volatile boolean failed;
    private static volatile Point startPoint;
    private static volatile Point endPoint;

    public static void main(String[] args) throws Exception {
        try {
            Robot robot = new Robot();

            EventQueue.invokeAndWait(DragThresholdTest::createAndShowDnD);
            robot.waitForIdle();
            robot.delay(1000);

            EventQueue.invokeAndWait(() -> {
                Point p = panel.getLocationOnScreen();
                p.translate(50, 50);
                startPoint = p;
                endPoint = new Point(p.x + 2 * DragSource.getDragThreshold(),
                                     p.y + 2 * DragSource.getDragThreshold());
            });

            robot.mouseMove(startPoint.x, startPoint.y);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            for (Point p = new Point(startPoint); !p.equals(endPoint);
                 p.translate(sign(endPoint.x - p.x),
                             sign(endPoint.y - p.y))) {
                robot.mouseMove(p.x, p.y);
                robot.delay(100);
            }
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();
            robot.delay(200);

            if (failed) {
                throw new RuntimeException("drag gesture recognized too early");
            }
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static void createAndShowDnD() {
        frame = new Frame("DragThresholdTest");
        panel = new Panel();
        // Mouse motion listener mml is added to the panel first.
        // We rely on it that this listener will be called first.
        panel.addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseDragged(MouseEvent evt) {
                lastMouseEvent = evt;
                System.out.println(evt);
            }
        });
        frame.add(panel);
        frame.setSize(200, 200);
        frame.setLocationRelativeTo(null);

        DragGestureListener dgl = dge -> {
            Point dragOrigin = dge.getDragOrigin();
            int diffx = Math.abs(dragOrigin.x - lastMouseEvent.getX());
            int diffy = Math.abs(dragOrigin.y - lastMouseEvent.getY());
            System.out.println("dragGestureRecognized(): " +
                               " diffx=" + diffx + " diffy=" + diffy +
                               " DragSource.getDragThreshold()="
                               + DragSource.getDragThreshold());
            if (diffx <= DragSource.getDragThreshold() &&
                diffy <= DragSource.getDragThreshold()) {
                failed = true;
                System.out.println("drag gesture recognized too early!");
            }
        };

        // Default drag gesture recognizer is a mouse motion listener.
        // It is added to the panel second.
        new DragSource().createDefaultDragGestureRecognizer(
                panel,
                DnDConstants.ACTION_COPY_OR_MOVE, dgl);
        frame.setVisible(true);
    }

    private static int sign(int n) {
        return Integer.compare(n, 0);
    }
}
