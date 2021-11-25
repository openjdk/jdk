/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, JetBrains s.r.o.. All rights reserved.
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
 * @bug 8267388
 * @summary Test implementation of NSAccessibilityTable protocol peer
 * @author Artem.Semenov@jetbrains.com
 * @run main/manual AccessibleJTableTest
 * @requires (os.family == "windows" | os.family == "mac")
 */

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class AccessibleJTableTest extends AccessibleComponentTest {
    private static final String[] columnNames = {"One", "Two", "Three"};
    private static final String[][] data = {
            {"One1", "Two1", "Three1"},
            {"One2", "Two2", "Three2"},
            {"One3", "Two3", "Three3"},
            {"One4", "Two4", "Three4"},
            {"One5", "Two5", "Three5"}
    };

    @Override
    public CountDownLatch createCountDownLatch() {
        return new CountDownLatch(1);
    }

    public void  createUI() {
        INSTRUCTIONS = "INSTRUCTIONS:\n"
                + "Check a11y of JTable.\n\n"
                + "Turn screen reader on, and Tab to the table.\n"
                + "On Windows press the arrow buttons to move through the table.\n\n"
                + "On MacOS, use the up and down arrow buttons to move through rows, and VoiceOver fast navigation to move through columns.\n\n"
                + "If you can hear table cells ctrl+tab further and press PASS, otherwise press FAIL.\n";

        JTable table = new JTable(data, columnNames);
        JPanel panel = new JPanel();
        panel.setLayout(new FlowLayout());
        JScrollPane scrollPane = new JScrollPane(table);
        panel.add(scrollPane);
        panel.setFocusable(false);
        exceptionString = "AccessibleJTable test failed!";
        super.createUI(panel, "AccessibleJTableTest");
    }

    public void  createUINamed() {
        INSTRUCTIONS = "INSTRUCTIONS:\n"
                + "Check a11y of named JTable.\n\n"
                + "Turn screen reader on, and Tab to the table.\n"
                + "Press the ctrl+tab button to move to second table.\n\n"
                + "If you can hear second table name: \"second table\" - ctrl+tab further and press PASS, otherwise press FAIL.\n";

        JTable table = new JTable(data, columnNames);
        JTable secondTable = new JTable(data, columnNames);
        secondTable.getAccessibleContext().setAccessibleName("Second table");
        JPanel panel = new JPanel();
        panel.setLayout(new FlowLayout());
        JScrollPane scrollPane = new JScrollPane(table);
        JScrollPane secondScrollPane = new JScrollPane(secondTable);
        panel.add(scrollPane);
        panel.add(secondScrollPane);
        panel.setFocusable(false);
        exceptionString = "AccessibleJTable test failed!";
        super.createUI(panel, "AccessibleJTableTest");
    }

    public void  createUIWithChangingContent() {
        INSTRUCTIONS = "INSTRUCTIONS:\n"
                + "Check a11y of dynamic JTable.\n\n"
                + "Turn screen reader on, and Tab to the table.\n"
                + "Add and remove rows and columns using the appropriate buttons and try to move around the table\n\n"
                + "If you hear changes in the table - ctrl+tab further and press PASS, otherwise press FAIL.\n";

        JTable table = new JTable(new TestTableModel(3, 3));

                JPanel panel = new JPanel();
        panel.setLayout(new FlowLayout());
        JScrollPane scrollPane = new JScrollPane(table);
        panel.add(scrollPane);

        JPanel buttonPanel = new JPanel(new GridLayout());
        JButton addRow = new JButton("Add row");
        addRow.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                table.setModel(new TestTableModel(table.getModel().getRowCount() + 1, table.getModel().getColumnCount()));
            }
        });

        JButton addColumn = new JButton("Add column");
        addColumn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                table.setModel(new TestTableModel(table.getModel().getRowCount(), table.getModel().getColumnCount() + 1));
            }
        });

        JButton removeRow = new JButton("Remove row");
        removeRow.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                table.setModel(new TestTableModel(table.getModel().getRowCount() - 1, table.getModel().getColumnCount()));
            }
        });

        JButton removeColumn = new JButton("Remove column");
        removeColumn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                table.setModel(new TestTableModel(table.getModel().getRowCount(), table.getModel().getColumnCount() - 1));
            }
        });

        buttonPanel.add(addRow);
        buttonPanel.add(addColumn);
        buttonPanel.add(removeRow);
        buttonPanel.add(removeColumn);

        panel.add(buttonPanel);

        panel.setFocusable(false);
        exceptionString = "AccessibleJTable test failed!";
        super.createUI(panel, "AccessibleJTableTest");
    }

    public static void main(String[] args) throws Exception {
        AccessibleJTableTest test = new AccessibleJTableTest();

        countDownLatch = test.createCountDownLatch();
        SwingUtilities.invokeAndWait(test::createUI);
        countDownLatch.await(15, TimeUnit.MINUTES);
        if (!testResult) {
            throw new RuntimeException(exceptionString);
        }

        countDownLatch = test.createCountDownLatch();
        SwingUtilities.invokeAndWait(test::createUINamed);
        countDownLatch.await(15, TimeUnit.MINUTES);
        if (!testResult) {
            throw new RuntimeException(exceptionString);
        }

        countDownLatch = test.createCountDownLatch();
        SwingUtilities.invokeAndWait(test::createUIWithChangingContent);
        countDownLatch.await(15, TimeUnit.MINUTES);
        if (!testResult) {
            throw new RuntimeException(exceptionString);
        }

    }

    private static class TestTableModel extends AbstractTableModel {
        private final int rows;
        private final int cols;

        TestTableModel(final int r, final int c) {
            super();
            rows = r;
            cols = c;
        }

        @Override
        public int getRowCount() {
            return rows >= 0 ? rows : 0;
        }

        @Override
        public int getColumnCount() {
            return cols >= 0 ? cols : 0;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            return String.valueOf((rowIndex + 1) * (columnIndex + 1));
        }
    }
}
