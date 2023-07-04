/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/*
 * @test
 * @bug 8311031
 * @key headful
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Test to validate JTable header border vertical line is
 * aligned with data grid lines (Metal L&F).
 * @run main/manual/othervm -Dsun.java2d.uiScale=2.25 TableHeaderBorderPositionTest
 */

public class TableHeaderBorderPositionTest {
    static JFrame f;
    static JTable j;
    static PassFailJFrame passFailJFrame;
    public static void showTable() throws Exception {
        final String INSTRUCTIONS = """
                Instructions to Test:
                1. Ensure the test is running 225% scaling.
                2. Check if the Table header border vertical lines are 
                aligned with table data grid lines.
                3. If there is a miss-match between them press FAIL, 
                else press PASS.
                """;
        f = new JFrame();
        f.setTitle("Sample - 225% scaling");

        String[][] data = {
                { "1", "1", "Green"},
                { "2", "2", "Blue"}
        };

        String[] columnNames = { "Number", "Size", "Color"};

        j = new JTable(data, columnNames);
        passFailJFrame = new PassFailJFrame("Test Instructions",
                INSTRUCTIONS, 5L, 8, 30);

        PassFailJFrame.addTestWindow(f);
        PassFailJFrame.positionTestWindow(f, PassFailJFrame.Position.VERTICAL);
        j.setBounds(30, 40, 200, 300);

        JScrollPane sp = new JScrollPane(j);
        f.add(sp);
        f.setSize(500, 200);
        f.setVisible(true);
    }

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
        SwingUtilities.invokeAndWait(() -> {
            try {
                TableHeaderBorderPositionTest.showTable();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        passFailJFrame.awaitAndCheck();
    }
}