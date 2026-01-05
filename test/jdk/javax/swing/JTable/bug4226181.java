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

import java.awt.BorderLayout;
import java.awt.GridLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

/*
 * @test
 * @bug 4226181
 * @summary Tests that JTable setModel() correctly re-sizes and counts columns
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4226181
 */

public class bug4226181 {
    private static final String INSTRUCTIONS = """
            Take a look at the table and remember the number of columns you see.
            Now press the "setModel" button. If the number of columns has changed,
            then test fails, otherwise it passes.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(50)
                .testUI(bug4226181::createTestUI)
                .build()
                .awaitAndCheck();
    }

    static class TestModel extends AbstractTableModel {
        public int getRowCount() {
            return 5;
        }

        public int getColumnCount() {
            return 7;
        }

        public Object getValueAt(int row, int column) {
            return row + ":" + column;
        }
    }

    public static JFrame createTestUI() {
        JFrame frame = new JFrame("bug4226181");
        TestModel testModel = new TestModel();
        final JTable t = new JTable(testModel);
        JButton b = new JButton("setModel");
        b.addActionListener(ae -> t.setModel(new TestModel()));
        t.setCellSelectionEnabled(true);
        JPanel p1 = new JPanel(new GridLayout(1, 2));
        p1.add(new JLabel("dummy"));
        p1.add(t);
        frame.add(p1);
        frame.add(b, BorderLayout.SOUTH);
        frame.pack();
        return frame;
    }
}
