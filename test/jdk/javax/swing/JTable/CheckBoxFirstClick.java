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

import java.awt.Color;
import java.awt.Dimension;
import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.border.BevelBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;

/*
 * @test
 * @bug 4115930
 * @summary Verify checkboxes in the table respond to first click.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual CheckBoxFirstClick
 */

public class CheckBoxFirstClick {
    private static final String INSTRUCTIONS = """
            Click over the checkbox in the table. It should change state
            on the first click. If not - press 'fail'.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(50)
                .testUI(CheckBoxFirstClick::createTestUI)
                .build()
                .awaitAndCheck();
    }

    public static JFrame createTestUI() {
        JFrame frame = new JFrame("ListSizeBug");

        // Take the dummy data from SwingSet.
        final String[] names = {"First Name", "Last Name", "Favorite Color",
                "Favorite Number", "Vegetarian"};
        final Object[][] data = {
                {"Mark", "Andrews", "Red", 2, true},
                {"Tom", "Ball", "Blue", 99, false},
                {"Alan", "Chung", "Green", 838, false},
                {"Jeff", "Dinkins", "Turquois", 8, true},
                {"Amy", "Fowler", "Yellow", 3, false},
                {"Brian", "Gerhold", "Green", 0, false},
                {"James", "Gosling", "Pink", 21, false},
                {"David", "Karlton", "Red", 1, false},
                {"Dave", "Kloba", "Yellow", 14, false},
                {"Peter", "Korn", "Purple", 12, false},
                {"Phil", "Milne", "Purple", 3, false},
                {"Dave", "Moore", "Green", 88, false},
                {"Hans", "Muller", "Maroon", 5, false},
                {"Rick", "Levenson", "Blue", 2, false},
                {"Tim", "Prinzing", "Blue", 22, false},
                {"Chester", "Rose", "Black", 0, false},
                {"Ray", "Ryan", "Gray", 77, false},
                {"Georges", "Saab", "Red", 4, false},
                {"Willie", "Walker", "Phthalo Blue", 4, false},
                {"Kathy", "Walrath", "Blue", 8, false},
                {"Arnaud", "Weber", "Green", 44, false}
        };

        // Create a model of the data.
        TableModel dataModel = new AbstractTableModel() {
            // These methods always need to be implemented.
            public int getColumnCount() {
                return names.length;
            }

            public int getRowCount() {
                return data.length;
            }

            public Object getValueAt(int row, int col) {
                return data[row][col];
            }

            // The default implementations of these methods in
            // AbstractTableModel would work, but we can refine them.
            public String getColumnName(int column) {
                return names[column];
            }

            public Class getColumnClass(int c) {
                return getValueAt(0, c).getClass();
            }

            public boolean isCellEditable(int row, int col) {
                return true;
            }

            public void setValueAt(Object aValue, int row, int column) {
                System.out.println("Setting value to: " + aValue);
                data[row][column] = aValue;
            }
        };

        // Create the table
        JTable tableView = new JTable(dataModel);
        // Turn off auto-resizing so that we can set column sizes programmatically.
        // In this mode, all columns will get their preferred widths, as set blow.
        tableView.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        // Create a combo box to show that you can use one in a table.
        JComboBox comboBox = new JComboBox();
        comboBox.addItem("Red");
        comboBox.addItem("Orange");
        comboBox.addItem("Yellow");
        comboBox.addItem("Green");
        comboBox.addItem("Blue");
        comboBox.addItem("Indigo");
        comboBox.addItem("Violet");

        TableColumn colorColumn = tableView.getColumn("Favorite Color");
        // Use the combo box as the editor in the "Favorite Color" column.
        colorColumn.setCellEditor(new DefaultCellEditor(comboBox));

        // Set a pink background and tooltip for the Color column renderer.
        DefaultTableCellRenderer colorColumnRenderer = new DefaultTableCellRenderer();
        colorColumnRenderer.setBackground(Color.pink);
        colorColumnRenderer.setToolTipText("Click for combo box");
        colorColumn.setCellRenderer(colorColumnRenderer);

        // Set a tooltip for the header of the colors column.
        TableCellRenderer headerRenderer = colorColumn.getHeaderRenderer();
        if (headerRenderer instanceof DefaultTableCellRenderer)
            ((DefaultTableCellRenderer) headerRenderer).setToolTipText("Hi Mom!");

        // Set the width of the "Vegetarian" column.
        TableColumn vegetarianColumn = tableView.getColumn("Vegetarian");
        vegetarianColumn.setPreferredWidth(100);

        // Show the values in the "Favorite Number" column in different colors.
        TableColumn numbersColumn = tableView.getColumn("Favorite Number");
        DefaultTableCellRenderer numberColumnRenderer = new DefaultTableCellRenderer() {
            public void setValue(Object value) {
                int cellValue = (value instanceof Number) ? ((Number) value).intValue() : 0;
                setForeground((cellValue > 30) ? Color.black : Color.red);
                setText((value == null) ? "" : value.toString());
            }
        };
        numberColumnRenderer.setHorizontalAlignment(JLabel.RIGHT);
        numbersColumn.setCellRenderer(numberColumnRenderer);
        numbersColumn.setPreferredWidth(110);

        // Finish setting up the table.
        JScrollPane scrollpane = new JScrollPane(tableView);
        scrollpane.setBorder(new BevelBorder(BevelBorder.LOWERED));
        scrollpane.setPreferredSize(new Dimension(430, 200));

        frame.add(scrollpane);
        frame.setSize(500, 200);
        return frame;
    }
}
