/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8192888
 * @key headful
 * @summary Verifies JTable doesn't ignore setPreferredWidth during
 *          initial layout when AUTO_RESIZE_LAST_COLUMN is enabled
 * @run main TestJTableColWidth
 */

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;
import javax.swing.SwingUtilities;

public class TestJTableColWidth {
    static JFrame frame;

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                frame = new JFrame("JTable colwidth");

                String[] cols = {"ID", "Name", "Description", "Status"};
                Object[][] data = {{1, "Mimi", "Testing Java 25 Regression", "Pending"}};

                DefaultTableModel model = new DefaultTableModel(data, cols);
                final JTable tab = new JTable(model);

                tab.getTableHeader().setReorderingAllowed(false);
                tab.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);

                TableColumnModel columnModel = tab.getColumnModel();
                // Defined widths
                int[] widths = {30, 200, 100, 50};

                for (int i = 0; i < widths.length; i++) {
                    columnModel.getColumn(i).setPreferredWidth(widths[i]);
                }

                frame.add(new JScrollPane(tab));
                frame.setSize(600, 200);
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);

                System.out.println("Actual column widths on screen:");
                for (int i = 0; i < columnModel.getColumnCount(); i++) {
                    System.out.println("Column " + i + ": " +
                                        columnModel.getColumn(i).getWidth() + "px");
                }
                if (columnModel.getColumn(0).getWidth()
                    == columnModel.getColumn(1).getWidth()) {
                    throw new RuntimeException("JTable ignores setPreferredWidth during" +
                                  " initial layout when AUTO_RESIZE_LAST_COLUMN is enabled");
                }
            } finally {
                if (frame != null) {
                    frame.dispose();
                }
            }
        });
    }
}
