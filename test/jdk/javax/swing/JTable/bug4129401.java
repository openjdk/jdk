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

import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.TableColumn;

/*
 * @test
 * @bug 4129401
 * @summary Tests that keystroking for combobox cell editor in JTable works
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4129401
 */

public class bug4129401 {
    private static final String INSTRUCTIONS = """
            1. Move the mouse cursor to the cell "CELL 2 1",
               which contains JComboBox and click left mouse button
               to drop down combobox list.
            2. Change selected item in the combobox list
               using up and down arrows.
            3. Press Esc. JComboBox drop down list should hide.
            If all was successful then test passes, else test fails.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(50)
                .testUI(bug4129401::createTestUI)
                .build()
                .awaitAndCheck();
    }

    public static JFrame createTestUI() {
        Object data[][] = new Object[4][2];
        JComboBox cb = new JComboBox();
        cb.addItem("Item1");
        cb.addItem("Item2");
        cb.addItem("Item3");
        cb.addItem("Item4");
        data[0][0] = "CELL 0 0";
        data[0][1] = "CELL 0 1";
        data[1][0] = "CELL 1 0";
        data[1][1] = "CELL 1 1";
        data[2][0] = "CELL 2 0";
        data[2][1] = "CELL 2 1";
        data[3][0] = "CELL 3 0";
        data[3][1] = "CELL 3 1";
        String[] str = {"Column 0", "Column 1"};
        JTable tbl = new JTable(data, str);
        JScrollPane sp = new JScrollPane(tbl);

        TableColumn col = tbl.getColumn("Column 1");
        col.setCellEditor(new DefaultCellEditor(cb));

        JFrame f = new JFrame("4129401 test");
        f.getContentPane().add(sp);
        f.setBounds(100, 100, 300, 300);
        return f;
    }
}
