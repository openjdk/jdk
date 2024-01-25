/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;

/*
 * @test
 * @bug 6429812
 * @summary Test to check if table header is painted without NPE
 * @run main TableHeaderRendererTest
 */

public class TableHeaderRendererTest {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(TableHeaderRendererTest::runTest);
        System.out.println("Test Passed");
    }

    private static void runTest() {
        UIManager.LookAndFeelInfo[] lookAndFeel = UIManager.getInstalledLookAndFeels();
        for (UIManager.LookAndFeelInfo look : lookAndFeel) {
            System.out.println(look.getName() + " LookAndFeel Set");
            setLookAndFeel(look.getClassName());
            // initialize should not throw NullPointerException
            initialize();
        }
    }

    //Initialize the table and paint it to Buffered Image
    static void initialize() {
        JTable table = new JTable(new SampleTableModel());

        final JTableHeader header = table.getTableHeader();
        TableCellRenderer renderer = new DecoratedHeaderRenderer(header.getDefaultRenderer());
        header.setDefaultRenderer(renderer);

        table.updateUI();

        Dimension size = header.getPreferredSize();
        header.setSize(size);
        BufferedImage img = new BufferedImage(size.width, size.height,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        try {
            header.paint(g2d);
        } finally {
            g2d.dispose();
        }
    }

    //Table Model data
    static class SampleTableModel extends AbstractTableModel {
        @Override
        public int getRowCount() {
            return 10;
        }
        @Override
        public int getColumnCount() {
            return 10;
        }
        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            return ""+rowIndex + " X " + columnIndex;
        }
    }

    //Table header renderer
    static class DecoratedHeaderRenderer implements TableCellRenderer {
        public DecoratedHeaderRenderer(TableCellRenderer render) {
            this.render = render;
        }

        private final TableCellRenderer render;

        @Override
        public Component getTableCellRendererComponent(JTable table,
                                                       Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            return render.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);
        }
    }

    private static void setLookAndFeel(String laf) {
        try {
            UIManager.setLookAndFeel(laf);
        } catch (UnsupportedLookAndFeelException ignored) {
            System.out.println("Unsupported L&F: " + laf);
        } catch (ClassNotFoundException | InstantiationException
                | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
