/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4466930
 * @summary Verifies selectAll selects all available rows and columns
 *          irrespective of presence of columns and rows respectively
 * @run main TestTableSelectAll
 */

import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

public class TestTableSelectAll {

    public static void main(String[] args) throws Exception {

        testColumnSelect();
        testRowSelect();
    }

    private static void testColumnSelect() {
        boolean colSelNoRow;
        boolean colSelWithRow;

        // TableModel with no rows, but 10 columns
        DefaultTableModel data = new DefaultTableModel(0, 10);

        JTable table = new JTable(data);

        // columns can be selected
        table.setColumnSelectionAllowed(true);
        table.setRowSelectionAllowed(false);

        table.selectAll();

        colSelNoRow = table.isColumnSelected(0);

        // After selectAll(), I would expect all columns to be selected, no matter
        // whether there are rows or not.
        System.out.println("Column 0 is selected: " + colSelNoRow);

        data.addRow(new Object[0]);

        table.selectAll();

        colSelWithRow = table.isColumnSelected(0);
        System.out.println("Column 0 is selected: " + colSelWithRow);

        if (!(colSelNoRow && colSelWithRow)) {
            throw new RuntimeException("selectAll did not select column");
        }
    }

    private static void testRowSelect() {
        boolean rowSelNoColumn;
        boolean rowSelWithColumn;

        // TableModel with 10 rows, but no columns
        DefaultTableModel data = new DefaultTableModel(10, 0);

        JTable table = new JTable(data);

        // rows can be selected
        table.setRowSelectionAllowed(true);
        table.setColumnSelectionAllowed(false);

        table.selectAll();

        rowSelNoColumn = table.isRowSelected(0);

        // After selectAll(), I would expect all rows to be selected, no matter
        // whether there are columns or not.
        System.out.println("Row 0 is selected: " + rowSelNoColumn);

        data.addColumn(new Object[0]);
        table.selectAll();

        rowSelWithColumn = table.isRowSelected(0);
        System.out.println("Row 0 is selected: " + rowSelWithColumn);

        if (!(rowSelNoColumn && rowSelWithColumn)) {
            throw new RuntimeException("selectAll did not select row");
        }
    }
}
