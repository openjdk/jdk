/*
 * Copyright (c) 2000, 2025, Oracle and/or its affiliates. All rights reserved.
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
import javax.swing.table.AbstractTableModel;

/*
 * @test
 * @bug 4368848
 * @summary Tests that mouse wheel events cancel popups
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4368848
 */

public class bug4368848 {
    static final String[] names = {"First Name", "Last Name", "Veggy"};
    static Object[][] data = {
            {"Mark", "Andrews", false},
            {"Tom", "Ball", false},
            {"Alan", "Chung", false},
            {"Jeff", "Dinkins", false},
            {"Amy", "Fowler", false},
            {"Brian", "Gerhold", false},
            {"James", "Gosling", false},
            {"David", "Karlton", false},
            {"Dave", "Kloba", false},
            {"Peter", "Korn", false},
            {"Phil", "Milne", false},
            {"Dave", "Moore", false},
            {"Hans", "Muller", false},
            {"Rick", "Levenson", false},
            {"Tim", "Prinzing", false},
            {"Chester", "Rose", false},
            {"Ray", "Ryan", false},
            {"Georges", "Saab", false},
            {"Willie", "Walker", false},
            {"Kathy", "Walrath", false},
            {"Arnaud", "Weber", false}
    };

    private static final String INSTRUCTIONS = """
            Click any cell in the 'Veggy' column, so that combo box appears.
            Make sure mouse pointer is over the table, but _not_ over the combo
            box. Try scrolling the table using the mouse wheel. The combo popup
            should disappear. If it stays visible, test fails.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(50)
                .testUI(bug4368848::createTestUI)
                .build()
                .awaitAndCheck();
    }

    public static JFrame createTestUI() {
        JFrame frame = new JFrame("bug4368848");
        ExampleTableModel dataModel = new ExampleTableModel();

        JComboBox _editor = new JComboBox();
        _editor.addItem(false);
        _editor.addItem(true);

        JTable tableView = new JTable(dataModel);
        tableView.setDefaultEditor(Boolean.class, new DefaultCellEditor(_editor));

        frame.add(new JScrollPane(tableView));
        frame.setSize(200, 200);
        return frame;
    }

    static class ExampleTableModel extends AbstractTableModel {
        // These methods always need to be implemented.
        @Override
        public int getColumnCount() {
            return names.length;
        }

        @Override
        public int getRowCount() {
            return data.length;
        }

        public Object getValueAt(int row, int col) {
            return data[row][col];
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            return true;
        }

        @Override
        public String getColumnName(int column) {
            return names[column];
        }

        @Override
        public Class getColumnClass(int col) {
            return getValueAt(0, col).getClass();
        }
    }
}
