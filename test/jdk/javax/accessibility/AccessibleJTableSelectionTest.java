/*
 * Copyright (c) 2010, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @key headful
 * @bug 4495286
 * @summary Verify that AccessibleJTable.setAccessibleSelction
 * selects rows/cols if getCellSelectionEnabled() is false
 * @run main AccessibleJTableSelectionTest
 */

import java.awt.BorderLayout;
import java.awt.Robot;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

public final class AccessibleJTableSelectionTest {

    private static JTable jTable;
    private static JFrame jFrame;

    private static Robot robot;

    private static void createGUI() {

        Object[][] rowData = { { "RowData1", Integer.valueOf(1) },
            { "RowData2", Integer.valueOf(2) },
            { "RowData3", Integer.valueOf(3) } };
        Object[] columnData = { "Column One", "Column Two" };

        jTable = new JTable(rowData, columnData);
        jTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        jTable.setRowSelectionAllowed(false);
        jTable.setColumnSelectionAllowed(false);
        jTable.setCellSelectionEnabled(true);

        jFrame = new JFrame();
        jFrame.add(new JScrollPane(jTable), BorderLayout.CENTER);
        jFrame.setSize(200, 200);
        jFrame.setLocationRelativeTo(null);
        jFrame.setVisible(true);
    }

    private static void doTest() throws Exception {
        SwingUtilities.invokeAndWait(() -> createGUI());

        robot = new Robot();
        robot.waitForIdle();
        SwingUtilities.invokeAndWait(() -> {
            jTable.requestFocus();
            jTable.getAccessibleContext().getAccessibleSelection()
            .addAccessibleSelection(1);
        });

        robot.waitForIdle();
        SwingUtilities.invokeAndWait(() -> {
            if (!jTable.isRowSelected(0) || !jTable.isColumnSelected(1)) {
                throw new RuntimeException(
                    "Unexpected selection state of "
                    + "Table Row & Column");
            }
        });

        robot.waitForIdle();
        SwingUtilities.invokeAndWait(() -> {
            jTable.setRowSelectionAllowed(true);
            jTable.setColumnSelectionAllowed(false);
            jTable.setCellSelectionEnabled(false);
            jTable.requestFocus();
            jTable.getAccessibleContext().getAccessibleSelection()
            .addAccessibleSelection(3);
        });

        robot.waitForIdle();
        SwingUtilities.invokeAndWait(() -> {
            if (!jTable.isRowSelected(1)) {
                throw new RuntimeException(
                    "Unexpected selection state of "
                    + "Table Row & Column");
            }
        });

        robot.waitForIdle();
        SwingUtilities.invokeAndWait(() -> {
            jTable.setRowSelectionAllowed(false);
            jTable.setColumnSelectionAllowed(true);
            jTable.setCellSelectionEnabled(false);
            jTable.requestFocus();
            jTable.getAccessibleContext().getAccessibleSelection()
            .addAccessibleSelection(4);
        });

        robot.waitForIdle();
        SwingUtilities.invokeAndWait(() -> {
            if (!jTable.isColumnSelected(0)) {
                throw new RuntimeException(
                    "Unexpected selection state of "
                    + "Table Row & Column");
            }
        });

        robot.waitForIdle();
        SwingUtilities.invokeAndWait(() -> {
            jTable.setRowSelectionAllowed(true);
            jTable.setColumnSelectionAllowed(true);
            jTable.setCellSelectionEnabled(false);
            jTable.requestFocus();
            jTable.getAccessibleContext().getAccessibleSelection()
            .addAccessibleSelection(5);
        });

        robot.waitForIdle();
        SwingUtilities.invokeAndWait(() -> {
            if (!(jTable.isRowSelected(2) && jTable.isColumnSelected(1))) {
                throw new RuntimeException(
                    "Unexpected selection state of "
                    + "Table Row & Column");
            }
        });

        robot.waitForIdle();
        SwingUtilities.invokeAndWait(() -> {
            jTable.setCellSelectionEnabled(true);
            jTable.setColumnSelectionAllowed(true);
            jTable.setRowSelectionAllowed(true);
            jTable.requestFocus();
            jTable.getAccessibleContext().getAccessibleSelection()
            .addAccessibleSelection(4);
        });

        robot.waitForIdle();
        SwingUtilities.invokeAndWait(() -> {
            if (!(jTable.isRowSelected(2) && jTable.isColumnSelected(0)
                && jTable.isCellSelected(2, 0))) {
                throw new RuntimeException(
                    "Unexpected selection state of "
                    + "Table Row & Column");
            }
        });
    }

    public static void main(final String[] argv) throws Exception {
        doTest();
        SwingUtilities.invokeAndWait(() -> jFrame.dispose());
        System.out.println("Test Passed.");
    }
}

