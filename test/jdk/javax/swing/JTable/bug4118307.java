/*
 * Copyright (c) 1998, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Component;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;

/*
 * @test
 * @bug 4118307
 * @summary Tests that JTable's cell editor for Number and Date work correctly
 * @key headful
 * @run main bug4118307
 */

public class bug4118307 {
    static JFrame frame;
    static MyTable tbl;
    static Point tableLoc;
    static Point p;
    private static volatile boolean flag;
    static final String[] columnNames = {"Integer", "Double"};
    static final Object[][] data = {
            {5, 3.14},
            {10, 2.71},
            {70, 3.14},
            {200, 2.71},
    };

    public static void main(String[] args) throws Exception {
        try {
            Robot robot = new Robot();
            robot.setAutoDelay(250);
            SwingUtilities.invokeAndWait(() -> createTestUI());
            robot.waitForIdle();
            robot.delay(1000);

            SwingUtilities.invokeAndWait(() -> {
                tableLoc = tbl.getLocationOnScreen();
                p = tbl.getCellRect(0, 0, true).getLocation();
            });
            robot.waitForIdle();

            robot.mouseMove(tableLoc.x + p.x + 10, tableLoc.y + p.y + 10);
            robot.waitForIdle();

            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();

            SwingUtilities.invokeAndWait(() ->
                    p = tbl.getCellRect(1, 1, true).getLocation());
            robot.waitForIdle();

            robot.mouseMove(tableLoc.x + p.x + 10, tableLoc.y + p.y + 10);
            robot.waitForIdle();

            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();

            SwingUtilities.invokeAndWait(() ->
                    p = tbl.getCellRect(1, 0, true).getLocation());
            robot.waitForIdle();

            robot.mouseMove(tableLoc.x + p.x + 10, tableLoc.y + p.y + 10);
            robot.waitForIdle();

            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();
            robot.delay(5000);

            if (!flag) {
                throw new RuntimeException("Test Failed.");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    public static void createTestUI() {
        frame = new JFrame("bug4118307");
        MyTableModel myModel = new MyTableModel();
        tbl = new MyTable(myModel);
        JScrollPane sp = new JScrollPane(tbl);
        flag = true;

        frame.add(sp, BorderLayout.CENTER);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    static class MyTable extends JTable {
        public MyTable(TableModel tm) {
            super(tm);
        }

        public Component prepareRenderer(TableCellRenderer rend, int row, int col) {
            try {
                return super.prepareRenderer(rend, row, col);
            } catch (Exception e) {
                e.printStackTrace();
                flag = false;
                return null;
            }
        }
    }

    static class MyTableModel extends AbstractTableModel {
        public int getColumnCount() {
            return columnNames.length;
        }

        public int getRowCount() {
            return data.length;
        }

        @Override
        public String getColumnName(int col) {
            return columnNames[col];
        }

        public Object getValueAt(int row, int col) {
            return data[row][col];
        }

        public Class getColumnClass(int c) {
            return getValueAt(0, c).getClass();
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            return true;
        }

        @Override
        public void setValueAt(Object value, int row, int col) {
            data[row][col] = value;
        }
    }
}
