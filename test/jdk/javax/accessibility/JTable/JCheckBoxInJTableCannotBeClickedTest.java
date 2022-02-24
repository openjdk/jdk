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

/* @test
   @bug 8277922
   @key headful
   @summary Execution of AccessibleAction of AccessibleContext of JTable cell
            with JCheckBox does not lead to change of the cell value and view.
 */

import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Robot;
import java.lang.reflect.InvocationTargetException;
import javax.accessibility.Accessible;
import javax.accessibility.AccessibleAction;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleTable;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

public class JCheckBoxInJTableCannotBeClickedTest {
    private volatile JFrame frame;
    private volatile JTable table;

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException, AWTException {
        final JCheckBoxInJTableCannotBeClickedTest test =
            new JCheckBoxInJTableCannotBeClickedTest();

        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    test.createGUI();
                }
            });
            Robot robot = new Robot();
            robot.waitForIdle();

            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    test.runTest();
                }
            });
        } finally {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    test.dispose();
                }
            });
        }
    }

    private void createGUI() {
        frame = new JFrame("JCheckBoxInJTableCannotBeClickedTest");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        Container content = frame.getContentPane();
        content.setLayout(new BorderLayout());

        String[] tblColNames = {"Column 1", "Column 2", "Column 3"};
        Object[][] tblData = {
            {Boolean.TRUE, "Text 1", Boolean.FALSE},
            {Boolean.FALSE, "Text 2", Boolean.TRUE},
            {Boolean.TRUE, "Text 3", Boolean.FALSE}
        };
        final DefaultTableModel tblModel = new DefaultTableModel(
                tblData, tblColNames) {
            @Override
            public Class<?> getColumnClass(int column) {
                return getValueAt(0, column).getClass();
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                if (column == 0) {
                    return false;
                }
                return true;
            }
        };
        table = new JTable(tblModel);
        table.setPreferredScrollableViewportSize(new Dimension(400, 100));

        JScrollPane tblScroller = new JScrollPane(table);
        tblScroller.setHorizontalScrollBarPolicy(
            JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        tblScroller.setVerticalScrollBarPolicy(
            JScrollPane.VERTICAL_SCROLLBAR_ALWAYS
        );
        content.add(tblScroller, BorderLayout.CENTER);

        frame.pack();
        frame.setVisible(true);
    }

    private void dispose() {
        if (frame != null) {
            frame.dispose();
            frame = null;
        }
    }

    private void runTest() {
        if (table == null) {
            throw new RuntimeException("'table' should not be null");
        }

        testDoAccessibleActionInCell(0, 2, 0, true);
        testDoAccessibleActionInCell(0, 2, 0, true);
        testDoAccessibleActionInCell(1, 2, 0, true);
        testDoAccessibleActionInCell(1, 2, 0, true);
        testDoAccessibleActionInCell(0, 0, 0, false);
        testDoAccessibleActionInCell(1, 0, 0, false);
        testDoAccessibleActionInCell(2, 0, 0, false);

        System.out.println("Disabling the table...");
        table.setEnabled(false);
        testDoAccessibleActionInCell(0, 2, 0, false);
        testDoAccessibleActionInCell(1, 2, 0, false);
        testDoAccessibleActionInCell(2, 2, 0, false);

        System.out.println("Enabling the table...");
        table.setEnabled(true);
        testDoAccessibleActionInCell(0, 2, 0, true);
        testDoAccessibleActionInCell(1, 2, 0, true);
        testDoAccessibleActionInCell(2, 2, 0, true);

        System.out.println("Test passed.");
    }

    private void testDoAccessibleActionInCell(int row, int column,
            int actionIndex, boolean expectCellValChange) {
        System.out.println(String.format("testDoAccessibleActionInCell():" +
                    " row='%d', column='%d', actionIndex='%d'" +
                    ", expectCellValChange='%b'",
                row, column, actionIndex, expectCellValChange));

        if (table == null) {
            throw new RuntimeException("'table' should not be null");
        }

        AccessibleContext tblAc = table.getAccessibleContext();
        AccessibleTable accessibleTbl = tblAc.getAccessibleTable();
        if (accessibleTbl == null) {
            throw new RuntimeException("'accessibleTbl' should not be null");
        }

        Accessible cellAccessible = accessibleTbl.getAccessibleAt(row, column);
        AccessibleContext cellAc = cellAccessible.getAccessibleContext();
        if (cellAc == null) {
            throw new RuntimeException("'cellAc' should not be null");
        }

        AccessibleAction cellAa = cellAc.getAccessibleAction();
        if (cellAa == null) {
            throw new RuntimeException("'cellAa' should not be null");
        }
        if (cellAa.getAccessibleActionCount() <= actionIndex) {
            throw new RuntimeException(
                "cellAa.getAccessibleActionCount() <= actionIndex");
        }

        Object oldCellVal = table.getValueAt(row, column);
        cellAa.doAccessibleAction(actionIndex);
        Object newCellVal = table.getValueAt(row, column);

        boolean cellValChanged = oldCellVal != newCellVal;
        if ((expectCellValChange && !cellValChanged) ||
            (!expectCellValChange && cellValChanged)) {
            throw new RuntimeException(String.format(
                    "Test failed. cellValChanged='%b'", cellValChanged));
        }
    }
}
