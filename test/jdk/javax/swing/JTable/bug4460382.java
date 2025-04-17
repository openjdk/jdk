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

import java.awt.Point;
import java.awt.Robot;
import java.awt.event.MouseEvent;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.MenuKeyEvent;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableModel;

/*
 * @test
 * @bug 4460382
 * @summary Tests table editors' work
 * @key headful
 * @run main bug4460382
 */

public class bug4460382 {
    static JFrame frame;
    static Table table;
    static Point tableLoc;
    static Point p;
    static int row1 = -1;
    static int row2 = -1;

    public static void main(String[] args) throws Exception {
        try {
            Robot robot = new Robot();
            robot.setAutoDelay(250);
            SwingUtilities.invokeAndWait(() -> createTestUI());
            robot.waitForIdle();

            SwingUtilities.invokeAndWait(() -> {
                tableLoc = table.getLocationOnScreen();
                p = table.getCellRect(0, 0, true).getLocation();
            });

            robot.mouseMove(tableLoc.x + p.x + 10, tableLoc.y + p.y + 10);
            robot.waitForIdle();

            robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();

            robot.keyPress(MenuKeyEvent.VK_A);
            robot.keyRelease(MenuKeyEvent.VK_A);
            robot.keyPress(MenuKeyEvent.VK_ENTER);
            robot.keyRelease(MenuKeyEvent.VK_ENTER);
            robot.waitForIdle();
        } finally {
            if (frame != null) {
                SwingUtilities.invokeAndWait(() -> frame.dispose());
            }
            if (row1 != row2) {
                throw new RuntimeException("Failed 4460382: editingRow is " + row2);
            }
        }
    }

    public static void createTestUI() {
        frame = new JFrame("bug4460382");
        table = new Table();
        frame.add(new JScrollPane(table));
        frame.setLocationRelativeTo(null);
        frame.pack();
        frame.setVisible(true);
    }

    static class Table extends JTable {
        public Table() {
            String[][] data = {{"Click and Edit here"},
                    {"Click and Edit here"},
                    {"Click and Edit here"}};
            String[] columnNames = {"Editable Column"};

            TableModel model = new DefaultTableModel(data, columnNames) {
                public boolean isCellEditable(int rowIndex, int columnIndex) {
                    return true;
                }
            };

            setModel(model);
        }

        public void editingStopped(ChangeEvent e) {
            TableCellEditor editor = getCellEditor();
            Object value = editor.getCellEditorValue();
            row1 = editingRow;
            setValueAt(value, editingRow, editingColumn);
            row2 = editingRow;
            super.editingStopped(e);
        }
    }
}
