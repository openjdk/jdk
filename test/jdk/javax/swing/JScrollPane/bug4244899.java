/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4244899
 * @summary Tests whether scrolling with blit has artifacts
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4244899
 */

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

public class bug4244899 {
    static final String INSTRUCTIONS = """
        Widen the first column of the table, so that
        you get a horizontal scrollbar. Click in the
        scrollbar (not on the thumb, but in the track).
        If you notice some artifacts/flashing at
        the bottom of the frame, the test FAILS.
        Otherwise, the test PASSES.
    """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("bug4244899 Test Instructions")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(bug4244899::createUI)
                .build()
                .awaitAndCheck();
    }

    static class TestModel extends AbstractTableModel {
        private final int rows = 20;
        private final int cols = 5;

        private Integer[][] data;

        public TestModel() {
            data = new Integer[rows][];
            int realCount = 0;
            for (int counter = 0; counter < rows; counter++) {
                data[counter] = new Integer[cols];
                for (int y = 0; y < cols; y++) {
                    data[counter][y] = Integer.valueOf(realCount);
                    realCount = (realCount + 1) % 23;
                }
            }
        }

        public int getRowCount() {
            return data.length;
        }

        public int getColumnCount() {
            return data[0].length;
        }

        public Object getValueAt(int row, int column) {
            return data[row][column];
        }
    }

    static JFrame createUI() {
        JFrame f = new JFrame("Scrolling Blit Artifact Test");
        JTable table = new JTable(new TestModel());
        JScrollPane sp = new JScrollPane(table);
        sp.getViewport().putClientProperty("EnableWindowBlit", Boolean.TRUE);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        f.add(sp);
        f.setSize(400, 400);
        return f;
    }
}
