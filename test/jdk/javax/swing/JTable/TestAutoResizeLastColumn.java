/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8234071 8387267
 * @summary AUTO_RESIZE_LAST_COLUMN should resize only the last column during table layout
 * @run main TestAutoResizeLastColumn
 */

import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.TableColumnModel;

public class TestAutoResizeLastColumn {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(TestAutoResizeLastColumn::testLastColumnOnly);
    }

    private static void testLastColumnOnly() {
        JTable table = new JTable(3, 3);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);

        if (table.getTableHeader().getResizingColumn() != null) {
            throw new RuntimeException(
                    "AUTO_RESIZE_LAST_COLUMN must not set resizingColumn");
        }

        TableColumnModel cm = table.getColumnModel();
        for (int i = 0; i < cm.getColumnCount(); i++) {
            cm.getColumn(i).setMinWidth(10);
            cm.getColumn(i).setPreferredWidth(100);
            cm.getColumn(i).setWidth(100);
        }

        table.setSize(300, 100);
        table.doLayout();

        assertWidth(cm, 0, 100);
        assertWidth(cm, 1, 100);
        assertWidth(cm, 2, 100);

        table.setSize(360, 100);
        table.doLayout();

        /*
         * AUTO_RESIZE_LAST_COLUMN means the +60 delta is absorbed by
         * the last column only.
         * Without fix all columns width will change to 120.
         */
        assertWidth(cm, 0, 100);
        assertWidth(cm, 1, 100);
        assertWidth(cm, 2, 160);

        table.setSize(330, 100);
        table.doLayout();

        assertWidth(cm, 0, 100);
        assertWidth(cm, 1, 100);
        assertWidth(cm, 2, 130);
    }

    private static void assertWidth(TableColumnModel cm, int column, int expected) {
        int actual = cm.getColumn(column).getWidth();
        if (actual != expected) {
            throw new RuntimeException(
                    "Unexpected width for column " + column
                            + ": expected " + expected
                            + ", actual " + actual);
        }
    }
}
