/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8068824
 * @key headful
 * @summary JTable header rendering problem (after setting preferred size)
 * @run main JTableHeaderExceptionTest
*/
import java.awt.BorderLayout;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import javax.swing.AbstractAction;
import javax.swing.JFrame;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;

public class JTableHeaderExceptionTest {
    static JFrame frame;
    static JTable table;
    static Point tableLoc;

    public static void main(String args[]) throws Exception {
        try {
            SwingUtilities.invokeAndWait(() -> {
                frame = new JFrame();
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

                Object rowData[][] =
                        {{"Row1-Column1", "Row1-Column2", "Row1-Column3"},
                        {"Row2-Column1", "Row2-Column2", "Row2-Column3"}};
                Object columnNames[] =
                    {"Test", "Click me with right mouse click!", "Test"};

                DragTestTable.DragTestTableModel model =
                    new DragTestTable.DragTestTableModel(rowData, columnNames);

                DragTestTable dragTable = new DragTestTable(model);
                table = dragTable;

                JScrollPane scrollPane = new JScrollPane(table);
                frame.add(scrollPane, BorderLayout.CENTER);
                frame.setSize(600, 150);
                frame.setVisible(true);
            });
            Robot robot = new Robot();
            robot.delay(1000);
            SwingUtilities.invokeAndWait(() -> {
                tableLoc = table.getTableHeader().getLocationOnScreen();
            });
            robot.mouseMove(tableLoc.x + 5, tableLoc.y + 5);
            robot.waitForIdle();
            robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
            robot.waitForIdle();
            robot.delay(1000);
            Point p = MouseInfo.getPointerInfo().getLocation();
            robot.mouseMove(p.x + 5, p.y + 5);
            robot.delay(1000);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.delay(1000);
        } finally {
            SwingUtilities.invokeAndWait(frame::dispose);
        }
    }
}

class DragTestTable extends JTable {

    Point loc;
    public DragTestTable(DragTestTableModel model) {

        super(model);

        JPopupMenu menu = new JPopupMenu();
        menu.add(new AbstractAction("Bug Test") {
            @Override
            public void actionPerformed(ActionEvent e) {
                ((DragTestTableModel)getModel()).fireTableStructureChanged();
            }
        });



        getTableHeader().setComponentPopupMenu(menu);
    }

    public static class DragTestTableModel extends AbstractTableModel {
        private Object rowData[][];
        private Object columnNames[];

        public DragTestTableModel(Object rowData[][], Object columnNames[]) {
            super();
            this.rowData = rowData;
            this.columnNames = columnNames;
        }

        public String getColumnName(int column) {
            return columnNames[column].toString();
        }
        public int getRowCount() {
            return rowData.length;
        }
        public int getColumnCount() {
            return columnNames.length;
        }
        public Object getValueAt(int row, int col) {
            return rowData[row][col];
        }
        public boolean isCellEditable(int row, int column) {
            return true;
        }
        public void setValueAt(Object value, int row, int col) {
            rowData[row][col] = value;
            fireTableCellUpdated(row, col);
        }
        public void fireTableStructureChanged(){
            super.fireTableStructureChanged();
        }

    }
}


