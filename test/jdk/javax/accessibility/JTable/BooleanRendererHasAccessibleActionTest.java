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
   @summary TableCellRenderer of JTable cell with Boolean data should not
            support any AccessibleAction.
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
import javax.swing.table.TableCellRenderer;

public class BooleanRendererHasAccessibleActionTest {
    private volatile JFrame frame;
    private volatile JTable table;

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException, AWTException {
        final BooleanRendererHasAccessibleActionTest test =
            new BooleanRendererHasAccessibleActionTest();

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
        frame = new JFrame("BooleanRendererHasAccessibleActionTest");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        Container content = frame.getContentPane();
        content.setLayout(new BorderLayout());

        String[] tblColNames = {"Column 1", "Column 2", "Column 3"};
        Object[][] tblData = {
            {Boolean.TRUE, "Text 1", Boolean.FALSE},
            {Boolean.FALSE, "Text 2", Boolean.TRUE}
        };
        final DefaultTableModel tblModel = new DefaultTableModel(
                tblData, tblColNames) {
            @Override
            public Class<?> getColumnClass(int column) {
                return getValueAt(0, column).getClass();
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

        testAccessibleActionInCellRenderer(0, 0, true);
        testAccessibleActionInCellRenderer(1, 0, true);
        testAccessibleActionInCellRenderer(0, 2, true);
        testAccessibleActionInCellRenderer(1, 2, true);

        testAccessibleActionInCell(0, 0, true);
        testAccessibleActionInCell(1, 0, true);
        testAccessibleActionInCell(0, 2, true);
        testAccessibleActionInCell(1, 2, true);

        System.out.println("Test passed.");
    }

    private void testAccessibleActionInCellRenderer(int row, int column,
            boolean shouldBeNull) {
        System.out.println(String.format(
                "testAccessibleActionInCellRenderer():" +
                    " row='%d', column='%d', shouldBeNull='%b'",
                row, column, shouldBeNull));

        TableCellRenderer cellRenderer = table.getCellRenderer(row, column);
        if (!(cellRenderer instanceof Accessible)) {
            throw new RuntimeException("'cellRenderer' is not Accessible");
        }

        AccessibleContext cellRendererAc =
            ((Accessible) cellRenderer).getAccessibleContext();
        if (cellRendererAc == null) {
            throw new RuntimeException("'cellRendererAc' should not be null");
        }

        AccessibleAction cellRendererAa = cellRendererAc.getAccessibleAction();
        if ((shouldBeNull && (cellRendererAa != null)) ||
            (!shouldBeNull && (cellRendererAa == null))) {
            throw new RuntimeException(
                "Test failed. 'cellRendererAa' is not as should be");
        }
    }

    private void testAccessibleActionInCell(int row, int column,
            boolean shouldBeNull) {
        System.out.println(String.format("testAccessibleActionInCell():" +
                    " row='%d', column='%d', shouldBeNull='%b'",
                row, column, shouldBeNull));

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
        if ((shouldBeNull && (cellAa != null)) ||
            (!shouldBeNull && (cellAa == null))) {
            throw new RuntimeException(
                "Test failed. 'cellAa' is not as should be");
        }
    }
}
