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

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

/*
 * @test
 * @bug 4242631
 * @summary Tests that JTable repaints itself correctly after a record
 *          has been removed and added to the table model.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4242631
 */

public class bug4242631 {
    private static JButton addButton;
    private static JButton removeButton;
    private static JButton bothButton;
    private static SimpleTableModel tableModel;

    private static final String INSTRUCTIONS = """
            Press Add button to add a record to the table. The record added should
            have number 0. Then press Remove/Add button some times. The record number
            should increase as you press. If it does not, test fails.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(50)
                .testUI(bug4242631::createTestUI)
                .build()
                .awaitAndCheck();
    }

    public static JFrame createTestUI() {
        JFrame frame = new JFrame("bug4242631");
        GridBagLayout grid = new GridBagLayout();

        frame.setLayout(grid);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 2, 2, 2);

        // Add button.
        c.gridx = 0;
        c.gridy = 0;
        grid.setConstraints(addButton = new JButton("Add"), c);
        frame.add(addButton);

        // Edit button.
        c.gridx = 1;
        c.gridy = 0;
        grid.setConstraints(removeButton = new JButton("Remove"), c);
        frame.add(removeButton);

        // Remove button.
        c.gridx = 2;
        c.gridy = 0;
        grid.setConstraints(bothButton = new JButton("Remove/Add"), c);
        frame.add(bothButton);

        // Table.
        c.gridx = 0;
        c.gridy = 1;
        c.gridwidth = 6;
        c.gridheight = 0;
        c.anchor = GridBagConstraints.CENTER;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1.0;
        c.weighty = 1.0;
        JScrollPane scroll = null;
        tableModel = new SimpleTableModel();
        grid.setConstraints(scroll = new JScrollPane(new JTable(tableModel)), c);
        frame.add(scroll);

        // Create some action listeners.
        addButton.addActionListener(event -> tableModel.addRow());
        removeButton.addActionListener(event -> tableModel.removeRow());
        bothButton.addActionListener(event -> tableModel.removeThenAddRow());

        frame.pack();
        return frame;
    }

    static class SimpleTableModel extends AbstractTableModel {
        int counter = 0;
        ArrayList list = new ArrayList();

        public SimpleTableModel() {}
        public int getColumnCount() { return 1; }
        public int getRowCount() { return list.size(); }

        public Object getValueAt(int row, int col) {
            String str = (String) list.get(row);
            return str;// + "." + col;
        }

        public void addRow() {
            list.add("" + counter++);
            fireTableRowsInserted(list.size() - 1, list.size() - 1);
        }

        public void removeRow() {
            if (list.size() == 0) return;
            list.remove(list.size() - 1);
            fireTableRowsDeleted(list.size(), list.size());
        }

        public void removeThenAddRow() {
            if (list.size() == 0) return;
            removeRow();
            addRow();
        }
    }
}
