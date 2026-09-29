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

import java.awt.Dimension;
import java.awt.Robot;
import java.util.Vector;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

/*
 * @test
 * @bug 4190222
 * @summary Setting data vector on the model correctly repaint table
 * @key headful
 * @run main bug4190222
 */

public class bug4190222 {
    static JFrame frame;
    static DefaultTableModel dtm;
    static JTable tbl;

    static Vector data;
    static Vector colNames;

    public static void main(String[] args) throws Exception {
        try {
            Robot robot = new Robot();
            robot.setAutoDelay(250);

            SwingUtilities.invokeAndWait(() -> createTestUI());
            robot.waitForIdle();
            robot.delay(1000);

            SwingUtilities.invokeAndWait(() -> {
                Dimension preResize = tbl.getSize();
                dtm.setDataVector(data, colNames);

                if (!preResize.equals(tbl.getSize())) {
                    throw new RuntimeException("Size of table changed after resizing.");
                }
            });
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    public static void createTestUI() {
        frame = new JFrame("bug4190222");

        data = new Vector(1, 1);
        colNames = new Vector(3);
        for (int i = 1; i < 4; i++) {
            Vector row = new Vector(1, 1);
            row.addElement("Row " + i + ", Col 1");
            row.addElement("Row " + i + ", Col 2");
            row.addElement("Row " + i + ", Col 3");
            data.addElement(row);
        }
        colNames.addElement("Col 1");
        colNames.addElement("Col 2");
        colNames.addElement("Col 3");

        dtm = new DefaultTableModel(data, colNames);
        tbl = new JTable(dtm);
        JScrollPane scrollPane = new JScrollPane(tbl);
        frame.add("Center", scrollPane);
        JPanel panel = new JPanel();
        frame.add("South", panel);

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
