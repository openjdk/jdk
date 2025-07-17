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
   @bug 4170447
   @summary JTable: non-Icon data in Icon column.
   @key headful
*/

import java.io.File;
import java.awt.Component;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;

public class bug4170447 {

    static volatile boolean failed = false;
    static volatile JFrame frame = null;

    public static void main(String args[]) throws Exception {
         SwingUtilities.invokeAndWait(bug4170447::createUI);
         Thread.sleep(5000);
         SwingUtilities.invokeAndWait(() -> {
             if (frame != null) {
                frame.dispose();
             }
         });
        if (failed) {
            throw new RuntimeException("Some exception occurred...");
        }
    }

    static void createUI() {
        String imgDir = System.getProperty("test.src", ".");
        String imgPath = imgDir + File.separator + "swing.small.gif";
        ImageIcon icn = new ImageIcon(imgPath,"test");
        final Object data[][] = {
            {"CELL 0 0", icn},
            {"CELL 1 0", "String"}
        };
        String[] str = {"Column 0", "Column 1"};

        TableModel dataModel = new AbstractTableModel() {
                public int getColumnCount() { return 2; }
                public int getRowCount() { return 2; }
                public Object getValueAt(int row, int col) {return data[row][col];}
                public Class getColumnClass(int c) {return getValueAt(0, c).getClass();}
                public boolean isCellEditable(int row, int col) {return getColumnClass(col) == String.class;}
                public void setValueAt(Object aValue, int row, int column) {data[row][column] = aValue;}
            };

        MyTable tbl = new MyTable(dataModel);
        JScrollPane sp = new JScrollPane(tbl);
        frame = new JFrame("bug4170447");
        frame.getContentPane().add(sp);
        frame.pack();
        frame.setVisible(true);
    }

    static class MyTable extends JTable {
        public MyTable(TableModel tm) {
            super(tm);
        }

        public Component prepareRenderer(TableCellRenderer rend, int row, int col) {
            try {
                return super.prepareRenderer(rend, row, col);
            } catch (Exception e) {
                e.printStackTrace();
                failed = true;
                return null;
            }
        }
    }
}
