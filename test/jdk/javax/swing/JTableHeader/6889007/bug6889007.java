/*
 * Copyright (c) 2010, 2026, Oracle and/or its affiliates. All rights reserved.
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
   @key headful
   @bug 6889007
   @summary No resize cursor during hovering mouse over JTable
*/

import java.awt.Cursor;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.MouseEvent;
import javax.swing.JFrame;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.event.MouseInputListener;
import javax.swing.plaf.basic.BasicTableHeaderUI;
import javax.swing.table.JTableHeader;

public class bug6889007 {
    static JFrame frame;
    static Robot robot;
    static JTableHeader header;
    static MyTableHeaderUI ui;
    static Point screenPoint;
    static Point previousPoint;

    public static void main(String[] args) throws Exception {
        try {
            robot = new Robot();
            robot.mouseMove(100, 100);
            SwingUtilities.invokeAndWait(() -> {
                frame = new JFrame();
                frame.setUndecorated(true);
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                header = new JTableHeader();
                header.setColumnModel(new JTable(20, 5).getColumnModel());
                ui = new MyTableHeaderUI();
                header.setUI(ui);
                frame.add(header);
                frame.pack();
                frame.setLocationRelativeTo(null);
                frame.setAlwaysOnTop(true);
                frame.setVisible(true);
            });
            robot.waitForIdle();
            robot.delay(1000);

            // Start just outside the header, at the height used for the sweep.
            SwingUtilities.invokeAndWait(() -> {
                previousPoint = header.getLocationOnScreen();
                previousPoint.translate(-10, header.getHeight() / 2);
            });
            robot.mouseMove(previousPoint.x, previousPoint.y);
            robot.delay(100);
            robot.waitForIdle();

            // Move to middle of column first, then move to the
            // next column boundary
            for (int column = 1; column < 5; column++) {
                final int nextColumn = column;
                SwingUtilities.invokeAndWait(() -> {
                    Rectangle r = header.getHeaderRect(nextColumn - 1);
                    ui.prepare(new Point(r.x + r.width / 2,
                                     header.getHeight() / 2), false);
                    screenPoint = header.getLocationOnScreen();
                    screenPoint.translate(ui.target.x, ui.target.y);
                });
                moveAndCheck();
                SwingUtilities.invokeAndWait(() -> {
                    Rectangle r = header.getHeaderRect(nextColumn);
                    ui.prepare(new Point(r.x, header.getHeight() / 2), true);
                    screenPoint = header.getLocationOnScreen();
                    screenPoint.translate(ui.target.x, ui.target.y);
                });
                moveAndCheck();
            }
            // Also verify that the cursor is restored in the final column.
            SwingUtilities.invokeAndWait(() -> {
                Rectangle r = header.getHeaderRect(4);
                ui.prepare(new Point(r.x + r.width / 2,
                                 header.getHeight() / 2), false);
                screenPoint = header.getLocationOnScreen();
                screenPoint.translate(ui.target.x, ui.target.y);
            });
            moveAndCheck();
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
        System.out.println("ok");
    }

    static void moveAndCheck() throws Exception {
        while (previousPoint.x != screenPoint.x) {
            previousPoint.x += Integer.compare(screenPoint.x, previousPoint.x);
            robot.mouseMove(previousPoint.x, screenPoint.y);
            robot.delay(100);
        }
        robot.waitForIdle();
        SwingUtilities.invokeAndWait(ui::check);
    }

    static class MyTableHeaderUI extends BasicTableHeaderUI {
        private Point target;
        private Point eventPoint;
        private boolean resize;
        private boolean targetSeen;
        private boolean rolloverSeen;
        private RuntimeException failure;

        // prepare the test for next mouse movement without moving the mouse
        void prepare(Point point, boolean expectResize) {
            target = point;
            resize = expectResize;
            targetSeen = false;
            rolloverSeen = false;
            failure = null;
        }

        @Override
        protected MouseInputListener createMouseInputListener() {
            return new MouseInputHandler() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    eventPoint = e.getPoint();
                    System.out.println("mouseMoved e.getPoint()=" + eventPoint
                            + " column=" + header.columnAtPoint(eventPoint)
                            + " cursorBefore=" + header.getCursor());
                    try {
                        super.mouseMoved(e);
                        if (eventPoint.equals(target)) {
                            targetSeen = true;
                        }
                        System.out.println("mouseMoved e.getPoint()=" + eventPoint
                                + " cursorAfter=" + header.getCursor());
                    } finally {
                        eventPoint = null;
                    }
                }
            };
        }

        @Override
        protected void rolloverColumnUpdated(int oldColumn, int newColumn) {
            System.out.println("oldColumn=" + oldColumn + " newColumn=" + newColumn
                    + " e.getPoint()=" + eventPoint
                    + " header.getCursor()=" + header.getCursor());
            // Intermediate native mouse positions need not be resize positions.
            // Check ordering only for the exact boundary requested by the test.
            if (resize && target != null && target.equals(eventPoint)
                    && oldColumn >= 0 && newColumn >= 0) {
                rolloverSeen = true;
                if (header.getCursor().getType() != Cursor.E_RESIZE_CURSOR) {
                    failure = new RuntimeException(
                            "Cursor not updated before rollover at " + eventPoint
                            + ": " + oldColumn + " -> " + newColumn
                            + ", cursor=" + header.getCursor());
                }
            }
        }

        void check() {
            if (failure != null) {
                throw failure;
            }
            if (!targetSeen) {
                throw new RuntimeException("No mouseMoved event at " + target);
            }
            if (resize && !rolloverSeen) {
                throw new RuntimeException("No rollover callback at " + target);
            }
            int expected = resize ? Cursor.E_RESIZE_CURSOR : Cursor.DEFAULT_CURSOR;
            if (header.getCursor().getType() != expected) {
                throw new RuntimeException("Wrong cursor at " + target
                        + ": " + header.getCursor() + ", expected type=" + expected);
            }
        }
    }
}
