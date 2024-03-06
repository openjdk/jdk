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

import java.awt.Color;
import java.awt.Dimension;

import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.border.BevelBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;

/*
 * @test
 * @key headful
 * @bug 4112270 8264102
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Test Keyboard Navigation in JTable.
 * @run main/manual KeyBoardNavigation
 */

public class KeyBoardNavigation {
    static JFrame frame;
    public static PassFailJFrame passFailJFrame;

    static void initTest() throws Exception {
        final String INSTRUCTIONS = """
                Instructions to Test:
                1. Refer the below keyboard navigation specs
                 (referenced from bug report 4112270).
                2. Check all combinations of navigational keys in all four modes
                  shift and control verifying each change to the selection against
                  the spec. If it does, press "pass", otherwise press "fail".

                  Navigate In - Tab, shift-tab, control-tab, shift-control-tab
                  Return/shift-return - move focus one cell down/up.
                  Tab/shift-tab - move focus one cell right/left.
                  Up/down arrow - deselect current selection; move focus one cell up/down
                  Left/right arrow - deselect current selection; move focus one cell
                                             left/right
                  PageUp/PageDown - deselect current selection; scroll up/down one
                                    JViewport view; first visible cell in current
                                    column gets focus
                  Control-PageUp/PageDown - deselect current selection; scroll
                                            left/right one JViewport view; first
                                            visible cell in current row gets
                                            focus
                  Home/end - deselect current selection; move focus and view to
                                     first/last cell in current row
                  Control-home/end - deselect current selection; move focus and view to
                                             upper-left/lower-right cell in table
                  F2 - Allows editing in a cell containing information without
                               overwriting the information
                  Esc - Resets the cell content back to the state it was in before
                                editing started
                  Ctrl+A, Ctrl+/ = Select all
                  Ctrl+\\ = De-select all
                  Shift-up/down arrow - extend selection up/down one row
                  Shift-left/right arrow - extend selection left/right one column
                  Control-shift up/down arrow - extend selection to top/bottom of column
                  Shift-home/end - extend selection to left/right end of row
                  Control-shift-home/end - extend selection to beginning/end of data
                  Shift-PageUp/PageDown - extend selection up/down one view and scroll
                                          table
                  Control-shift-PageUp/PageDown - extend selection left/right one view
                                                          and scroll table
                """;

        final String[] names = {"First Name", "Last Name", "Favorite Color",
                "Favorite Number", "Vegetarian"};
        final Object[][] data = {
                {"Mark", "Andrews", "Red", 2, Boolean.TRUE},
                {"Tom", "Ball", "Blue", 99, Boolean.FALSE},
                {"Alan", "Chung", "Green", 838, Boolean.FALSE},
                {"Jeff", "Dinkins", "Turquois", 8, Boolean.TRUE},
                {"Amy", "Fowler", "Yellow", 3, Boolean.FALSE},
                {"Brian", "Gerhold", "Green", 0, Boolean.FALSE},
                {"James", "Gosling", "Pink", 21, Boolean.FALSE},
                {"David", "Karlton", "Red", 1, Boolean.FALSE},
                {"Dave", "Kloba", "Yellow", 14, Boolean.FALSE},
                {"Peter", "Korn", "Purple", 12, Boolean.FALSE},
                {"Phil", "Milne", "Purple", 3, Boolean.FALSE},
                {"Dave", "Moore", "Green", 88, Boolean.FALSE},
                {"Hans", "Muller", "Maroon", 5, Boolean.FALSE},
                {"Rick", "Levenson", "Blue", 2, Boolean.FALSE},
                {"Tim", "Prinzing", "Blue", 22, Boolean.FALSE},
                {"Chester", "Rose", "Black", 0, Boolean.FALSE},
                {"Ray", "Ryan", "Gray", 77, Boolean.FALSE},
                {"Georges", "Saab", "Red", 4, Boolean.FALSE},
                {"Willie", "Walker", "Phthalo Blue", 4, Boolean.FALSE},
                {"Kathy", "Walrath", "Blue", 8, Boolean.FALSE},
                {"Arnaud", "Weber", "Green", 44, Boolean.FALSE}
        };

        frame = new JFrame("JTable Keyboard Navigation Test");
        passFailJFrame = new PassFailJFrame("Test Instructions",
                INSTRUCTIONS, 5L, 15, 50);

        PassFailJFrame.addTestWindow(frame);
        PassFailJFrame.positionTestWindow(frame, PassFailJFrame.Position.VERTICAL);

        JTable tableView = getTableDetails(names, data);

        // Create a combo box to show that you can use one in a table.
        JComboBox<String> comboBox = new JComboBox<>();
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

        JScrollPane scrollPane = new JScrollPane(tableView);
        scrollPane.setBorder(new BevelBorder(BevelBorder.LOWERED));
        scrollPane.setPreferredSize(new Dimension(430, 200));

        frame.add(scrollPane);
        frame.pack();
        frame.setVisible(true);
    }

    private static JTable getTableDetails(String[] names, Object[][] data) {
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

            public Class<?> getColumnClass(int c) {
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

        JTable tableView = new JTable(dataModel);
        // Turn off auto-resizing so that we can set column sizes programmatically.
        // In this mode, all columns will get their preferred widths, as set below.
        tableView.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        return tableView;
    }

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                initTest();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        passFailJFrame.awaitAndCheck();
    }
}
