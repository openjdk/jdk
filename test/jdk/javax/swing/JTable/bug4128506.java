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

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;

/*
 * @test
 * @bug 4128506
 * @summary Tests that JTable with AUTO_RESIZE_ALL_COLUMNS correctly compute width of columns
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4128506
 */

public class bug4128506 {
    private static final String INSTRUCTIONS = """
            If the columns of JTable have the same width the test passes, else test fails.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(50)
                .testUI(bug4128506::createTestUI)
                .build()
                .awaitAndCheck();
    }

    public static JFrame createTestUI() {
        final Object[][] data = {
                {"cell_1_1", "cell_1_2", "cell_1_3"},
                {"cell_2_1", "cell_2_2", "cell_2_3"},
                {"cell_3_1", "cell_3_2", "cell_3_3"},
                {"cell_4_1", "cell_4_2", "cell_4_3"},
        };

        TableModel dataModel = new AbstractTableModel() {
            public int getColumnCount() {
                return 3;
            }

            public int getRowCount() {
                return data.length;
            }

            public Object getValueAt(int row, int col) {
                return data[row][col];
            }
        };

        JFrame frame = new JFrame("bug4128506");
        JTable tableView = new JTable(dataModel);
        tableView.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        tableView.getColumnModel().getColumn(1).setMinWidth(5);
        JScrollPane scrollpane = new JScrollPane(tableView);
        frame.add(scrollpane);
        frame.pack();
        return frame;
    }
}
