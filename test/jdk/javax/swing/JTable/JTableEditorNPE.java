/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 6257207
 * @key headful
 * @summary Verifies if JTable.getDefaultEditor throws NullPointerException
 * @run main JTableEditorNPE
 */
import java.awt.Component;
import javax.swing.JFrame;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.SwingUtilities;

public class JTableEditorNPE {
    private static JFrame frame;

    public static void main(String[] args) throws Exception {
        try {
            SwingUtilities.invokeAndWait(() -> {
                frame = new JFrame();
                frame.setSize(100, 100);

                DefaultTableModel myModel = new DefaultTableModel();
                myModel.addColumn("Col1");
                myModel.addColumn("Col2");
                myModel.addColumn("Col3");
                myModel.addRow(new Object[]{"item1.1", "item1.2", "item1.3"});
                myModel.addRow(new Object[]{"item2.1", "item2.2", "item2.3"});
                MyTable tbl = new MyTable(myModel);
                frame.getContentPane().add(tbl);
                frame.validate();
            });
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    static class MyTable extends JTable implements TableModelListener {
        public MyTable(TableModel model) {
            super(model);
        }

        public void tableChanged(TableModelEvent event) {
            super.tableChanged(event);

            int column = event.getColumn();
            if (column == TableModelEvent.ALL_COLUMNS) {
                resizeAll();
            } else {
                resize(column);
            }
        }

        protected void resizeAll() {
            for (int i = 0; i < getModel().getColumnCount(); i++) {
                resize(i);
            }
        }

        protected void resize(int column) {
            TableCellEditor editor = null;
            TableCellRenderer renderer = null;
            Component comp;
            int width=0;
            for (int row = 0; row < getModel().getRowCount(); row++) {
                editor = this.getCellEditor(row, column);
                if (editor != null) {
                    comp = editor.getTableCellEditorComponent(
                            this, // table
                            getModel().getValueAt(row,column), // value
                            false, // isSelected
                            row, // row
                            column // column
                    );
                    if (comp != null) {
                        width = Math.max(width, comp.getPreferredSize().width);
                    }
                }

                renderer = this.getCellRenderer(row, column);
                if (renderer != null) {
                    comp = renderer.getTableCellRendererComponent(
                            this, // table
                            getModel().getValueAt(row, column), // value
                            false, // isSelected
                            false, // hasFocus
                            row, // row
                            column // column
                    );
                    if (comp != null) {
                        width = Math.max(width, comp.getPreferredSize().width);
                    }
                }
            }

            getColumnModel().getColumn(column).setPreferredWidth(width);

            this.revalidate();
            this.repaint();
        }
    }
}

