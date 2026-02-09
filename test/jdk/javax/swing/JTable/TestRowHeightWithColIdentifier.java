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
 * @bug 6292135
 * @summary Verifies DefaultTableModel.setColumnIdentifiers() doesn't
 *          clear JTable Row Heights
 * @run main TestRowHeightWithColIdentifier
 */

import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

public class TestRowHeightWithColIdentifier{


    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            DefaultTableModel model = new DefaultTableModel(null, new Object[] {"FOO", "BAR"});
            JTable table = new JTable(model);

            model.addRow(new Object[] {"00", "01"});
            model.addRow(new Object[] {"10", "11"});
            model.addRow(new Object[] {"20", "21"});
            for (int row = 0; row < table.getRowCount(); row++) {
                table.setRowHeight(row, 100);
            }
            for (int row = 0; row < table.getRowCount(); row++) {
                System.out.println("Before table rowHeight " + table.getRowHeight(row));
            }
            int oldRowHeight = table.getRowHeight(0);
            model.setColumnIdentifiers(new Object[] {"Check", "it out!"});
            for (int row = 0; row < table.getRowCount(); row++) {
                System.out.println("After table rowHeight " + table.getRowHeight(row));
            }
            int curRowHeight = table.getRowHeight(0);
            if (curRowHeight != oldRowHeight) {
                throw new RuntimeException("DefaultTableModel.setColumnIdentifiers() Clears JTable Row Height");
            }
        });
    }
}
