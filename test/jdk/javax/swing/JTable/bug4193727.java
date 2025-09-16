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
import java.awt.Component;
import java.awt.FontMetrics;
import java.util.Vector;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

/*
 * @test
 * @bug 4193727
 * @summary Tests that resizing JTable via TableColumn's
 *          setWidth(int) repaints correctly
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4193727
 */

public class bug4193727 {
    static EnhancedJTable tblResults;
    static JButton bTest = new JButton("Resize");

    private static final String INSTRUCTIONS = """
            Push button "Resize".
            If either of the following happen, test fails:
            1) The size of the columns change
            2) The JTable is not repainted correctly

            Otherwise test passes.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(50)
                .testUI(bug4193727::createTestUI)
                .build()
                .awaitAndCheck();
    }

    public static JFrame createTestUI() {
        JFrame frame = new JFrame("bug4193727");
        Vector v = new Vector();
        Vector data = new Vector();
        Vector cols = new Vector();

        cols.add("Name");
        cols.add("Address");
        data.add("Steve");
        data.add("100 East Main Street");
        v.add(data);

        data.add("Richard");
        data.add("99 Main Road");
        v.add(data);

        frame.setLayout(new BorderLayout());
        tblResults = new EnhancedJTable(v, cols);
        MyTableHeader mth = new MyTableHeader();
        for (int i = 0; i < tblResults.getColumnCount(); i++)
            tblResults.getColumnModel().getColumn(i).setHeaderRenderer(mth.getTHR());
        tblResults.setAutoResizeMode(EnhancedJTable.AUTO_RESIZE_OFF);

        JScrollPane pane = new JScrollPane(tblResults);
        frame.add(pane, BorderLayout.CENTER);
        JPanel panel = new JPanel();
        panel.add(bTest);
        frame.add(panel, BorderLayout.EAST);
        bTest.addActionListener(e -> tblResults.autoSizeColumns());
        frame.setSize(300, 200);
        return frame;
    }
}

class MyTableHeader extends TableColumn {
    public TableCellRenderer getTHR() {
        return createDefaultHeaderRenderer();
    }
}

class EnhancedJTable extends JTable {
    public EnhancedJTable(Vector data, Vector colNames) {
        super(data, colNames);
    }

    public synchronized void autoSizeColumns() {
        setAutoResizeMode(AUTO_RESIZE_OFF);
        int colcnt = getColumnCount();
        int rowcnt = getRowCount();

        for (int i = 0; i < colcnt; i++) {
            // get the max column width needed
            Component cell = getColumnModel().getColumn(i).getHeaderRenderer()
                    .getTableCellRendererComponent(this, null, false, false, -1, i);
            FontMetrics fm = cell.getFontMetrics(cell.getFont());
            int max = SwingUtilities.computeStringWidth(fm, getColumnModel().getColumn(i).getHeaderValue()
                    .toString() + "  ");
            for (int j = 0; j < rowcnt; j++) {
                // add 2 spaces to account for gutter
                int width = SwingUtilities.computeStringWidth(fm, getValueAt(j, i).toString() + "  ");
                if (max < width) max = width;
            }
            // set the new column width
            getColumnModel().getColumn(i).setWidth(max);
        }
    }
}
