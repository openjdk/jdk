/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @key headful
 * @bug 4217252
 * @summary Verify that scrolling beyond the visible region and scrolling
 *          a component smaller than the viewport is not allowed.
 * @library /javax/swing/regtesthelpers
 * @build Util
 * @run main/othervm -Dsun.java2d.uiScale=1 ScrollRectToVisibleTest3
 */

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;

public class ScrollRectToVisibleTest3 {
    private static JFrame frame;
    private static JTable table;
    private static JButton scrollButton;
    private static volatile int clickCount = 0;
    private static final String[] EXPECTED_TEXT = {"99 x 0", "98 x 0",
                                                   "97 x 0", "96 x 0"};
    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        robot.setAutoWaitForIdle(true);

        SwingUtilities.invokeAndWait(ScrollRectToVisibleTest3::createTestUI);
        robot.waitForIdle();
        robot.delay(1000);

        Rectangle frameBounds = Util.invokeOnEDT(() -> getComponentBounds(frame));
        robot.delay(100);
        Point scrollBtnLoc = Util.getCenterPoint(scrollButton);

        robot.mouseMove(scrollBtnLoc.x, scrollBtnLoc.y);
        robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);
        robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);
        robot.delay(50);

        int rowHeight = Util.invokeOnEDT(() -> table.getRowHeight());
        for (int i = 1; i <= 4; i++) {
            robot.mouseMove(frameBounds.x + 50,
                            frameBounds.y + frameBounds.height - (rowHeight * i + 2));
            robot.delay(300);
            robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);
            // 500 ms delay added so that current mouseClicked event
            // is processed successfully before proceeding to the next
            robot.delay(500);
        }
        if (clickCount != 4) {
            throw new RuntimeException("Test Failed! Expected 4 mouse clicks"
                                       + " but got " + clickCount);
        }
    }

    private static void createTestUI() {
        frame = new JFrame("ScrollRectToVisibleTest3");
        table = new JTable(new TestModel());
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                JTable testTable = (JTable) e.getComponent();
                int row = testTable.getSelectedRow();
                int column = testTable.getSelectedColumn();
                String cellContent = testTable.getValueAt(row, column).toString();
                if (!EXPECTED_TEXT[clickCount].equals(cellContent)) {
                    throw new RuntimeException(("Test failed! Table Cell Content"
                                + " at (row %d , col %d)\n Expected: %s vs Actual: %s")
                                    .formatted(row, column,
                                            EXPECTED_TEXT[clickCount], cellContent));
                }
                clickCount++;
            }
        });

        scrollButton = new JButton("Scroll");
        scrollButton.addActionListener(ae -> {
            Rectangle bounds = table.getBounds();
            bounds.y = bounds.height + table.getRowHeight();
            bounds.height = table.getRowHeight();
            System.out.println("scrolling: " + bounds);
            table.scrollRectToVisible(bounds);
            System.out.println("bounds: " + table.getVisibleRect());
        });

        frame.add(scrollButton, BorderLayout.NORTH);
        frame.add(new JScrollPane(table), BorderLayout.CENTER);
        frame.setSize(400, 300);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }


    private static class TestModel extends AbstractTableModel {
        @Override
        public String getColumnName(int column) {
            return Integer.toString(column);
        }

        @Override
        public int getRowCount() {
            return 100;
        }

        @Override
        public int getColumnCount() {
            return 5;
        }

        @Override
        public Object getValueAt(int row, int col) {
            return row + " x " + col;
        }

        @Override
        public boolean isCellEditable(int row, int column) { return false; }

        @Override
        public void setValueAt(Object value, int row, int col) {
        }
    }

    private static Rectangle getComponentBounds(Component c) {
        Point locationOnScreen = c.getLocationOnScreen();
        Dimension size = c.getSize();
        return new Rectangle(locationOnScreen, size);
    }
}
