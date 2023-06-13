/*
 * Copyright (c) 1999, 2023, Oracle and/or its affiliates. All rights reserved.
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
   @test
   @bug 4159300
   @summary Tests that JTable processes tableChanged events quickly
   @key headful
*/

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Rectangle;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class bug4159300 {

    static volatile JFrame frame = null;
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(bug4159300::createUI);
        Thread.sleep(3000);
        SwingUtilities.invokeAndWait(() -> {
            if (frame != null) {
                frame.dispose();
            }
        });
    }

    static void createUI() {
        frame = new JFrame("bug4159300");
        Container c = frame.getContentPane();
        c.setLayout(new BorderLayout());
        // create table
        Object[] columnNames = {"only column"};
        DefaultTableModel model = new DefaultTableModel(columnNames, 0);
        Object[] row = makeRow(model.getRowCount());
        model.addRow(row);

        JTable table = new JTable(model);
        c.add(new JScrollPane(table), BorderLayout.CENTER);

        // create button
        JButton immediateButton = new JButton("Add row");
        immediateButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int rowCount = model.getRowCount();
                Object[] row = makeRow(rowCount);
                model.addRow(row);
                int rows = model.getRowCount();
                int lastRow = rows - 1;
                table.setRowSelectionInterval(lastRow, lastRow);
                Rectangle r = table.getCellRect(lastRow, 0, false);
                table.scrollRectToVisible(r);
            }
        });
        c.add(immediateButton, BorderLayout.SOUTH);
        frame.pack();
        frame.setVisible(true);
    }

    static Object[] makeRow(int rowNumber) {
        Object[] row = { ""+rowNumber };
        return row;
    }
}
