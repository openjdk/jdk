/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4217252
 * @summary Tests
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4217252
 */

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.Rectangle;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

public class bug4217252 {

    private static final String INSTRUCTIONS = """
        Click on the 'Scroll' button and then click it again.
        If you see row 98 and 99 twice, then test failed, otherwise it passed.""";

    static class TestModel extends AbstractTableModel {

        public String getColumnName(int column) {
            return Integer.toString(column);
        }

        public int getRowCount() {
                return 100;
        }

        public int getColumnCount() {
                return 5;
        }

        public Object getValueAt(int row, int col) {
                return row + " x " + col;
        }

        public boolean isCellEditable(int row, int column) { return false; }

        public void setValueAt(Object value, int row, int col) { }
    }

    public static void main(String[] args) throws Exception {

         PassFailJFrame.builder()
                .title("JViewport Instructions")
                .instructions(INSTRUCTIONS)
                .rows(5)
                .columns(30)
                .testUI(bug4217252::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createTestUI(){
        JFrame frame = new JFrame("bug4217252");
        final JTable table = new JTable(new TestModel());
        JButton scrollButton = new JButton("Scroll");
        scrollButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                Rectangle bounds = table.getBounds();
                bounds.y = bounds.height + table.getRowHeight();
                bounds.height = table.getRowHeight();
                table.scrollRectToVisible(bounds);
            }
        });
        frame.getContentPane().add(new JScrollPane(table),
                                   BorderLayout.CENTER);
        frame.getContentPane().add(scrollButton, BorderLayout.SOUTH);
        frame.pack();
        return frame;
    }

}
