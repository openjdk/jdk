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
 * @bug 8024624
 * @key headful
 * @requires (os.family != "mac")
 * @summary Tests some of JTable's key navigation
 * @run main JTableOrientationNavTest
 */

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Rectangle;
import java.awt.Robot;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;

import static java.awt.event.KeyEvent.VK_CONTROL;
import static java.awt.event.KeyEvent.VK_LEFT;
import static java.awt.event.KeyEvent.VK_PAGE_DOWN;
import static java.awt.event.KeyEvent.VK_PAGE_UP;
import static java.awt.event.KeyEvent.VK_RIGHT;
import static java.awt.event.KeyEvent.VK_SHIFT;

public class JTableOrientationNavTest {
    private static JFrame frame;
    private static JTable table;
    private static JScrollPane sp;
    private static Robot robot;
    private static boolean ltr = true;

    public static void main(String[] args) throws InterruptedException, InvocationTargetException {
        try {
            robot = new Robot();
            robot.setAutoDelay(100);
            robot.setAutoWaitForIdle(true);

            SwingUtilities.invokeAndWait(() -> {
                frame = new JFrame();
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.add(createContentPane());
                frame.pack();
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            });

            executeTest();
            setupRTL();
            executeTest();

            System.out.println("Passed");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            SwingUtilities.invokeAndWait(() -> frame.dispose());
        }
    }

    private static TableModel getTableModel1() {
        String[] columnNames = {"Column 0", "Column 1", "Column 2",
                "Column 3", "Column 4"};
        String[][] data = {{"Table 00, 00", "Table 00, 01", "Table 00, 02",
                                    "Table 00, 03", "Table 00, 04"},
                           {"Table 01, 00", "Table 01, 01", "Table 01, 02",
                                   "Table 01, 03", "Table 01, 04"},
                           {"Table 02, 00", "Table 02, 01", "Table 02, 02",
                                   "Table 02, 03", "Table 02, 04"},
                           {"Table 03, 00", "Table 03, 01", "Table 03, 02",
                                   "Table 03, 03", "Table 03, 04"},
                           {"Table 04, 00", "Table 04, 01", "Table 04, 02",
                                   "Table 04, 03", "Table 04, 04"},
                           {"Table 05, 00", "Table 05, 01", "Table 05, 02",
                                   "Table 05, 03", "Table 05, 04"}};

        return new DefaultTableModel(data, columnNames);
    }

    private static TableModel getTableModel2() {
        String[] columnNames = new String[30];
        String[][] data = new String[1][30];

        for (int i = 0; i < columnNames.length; i++) {
            columnNames[i] = "Column " + i;
            data[0][i] = "Data " + 1;
        }

        return new DefaultTableModel(data, columnNames);
    }

    private static Component createContentPane() {
        table = new JTable(getTableModel1());
        table.setCellSelectionEnabled(true);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JPanel panel = new JPanel(new BorderLayout());
        sp = new JScrollPane(table);
        panel.add(sp);
        return panel;
    }

    private static void executeTest() throws InterruptedException, InvocationTargetException {
        SwingUtilities.invokeAndWait(() -> {
            table.setRowSelectionInterval(0, 0);
            table.setColumnSelectionInterval(0, 0);
            checkSelection(0, 0);
        });

        robot.keyPress(ltr ? VK_RIGHT : VK_LEFT);
        robot.keyRelease(ltr ? VK_RIGHT : VK_LEFT);

        SwingUtilities.invokeAndWait(() -> checkSelection(1, 1));

        robot.keyPress(VK_CONTROL);
        robot.keyPress(ltr ? VK_RIGHT : VK_LEFT);
        robot.keyRelease(ltr ? VK_RIGHT : VK_LEFT);
        robot.keyRelease(VK_CONTROL);

        SwingUtilities.invokeAndWait(() -> {
            checkSelection(2, 1);
            table.setRowSelectionInterval(0, 0);
            table.setColumnSelectionInterval(4, 4);
            checkSelection(4, 4);
        });

        robot.keyPress(ltr ? VK_LEFT : VK_RIGHT);
        robot.keyRelease(ltr ? VK_LEFT : VK_RIGHT);

        SwingUtilities.invokeAndWait(() -> checkSelection(3, 3));

        robot.keyPress(VK_CONTROL);
        robot.keyPress(ltr ? VK_LEFT : VK_RIGHT);
        robot.keyRelease(ltr ? VK_LEFT : VK_RIGHT);
        robot.keyRelease(VK_CONTROL);

        SwingUtilities.invokeAndWait(() -> {
            checkSelection(2, 3);
            table.setColumnSelectionInterval(2, 2);
            checkSelection(2, 2);
        });

        robot.keyPress(VK_CONTROL);
        robot.keyPress(VK_PAGE_UP);
        robot.keyRelease(VK_PAGE_UP);
        robot.keyRelease(VK_CONTROL);

        SwingUtilities.invokeAndWait(() -> {
            checkSelection(0, 0);
            table.setColumnSelectionInterval(2, 2);
            checkSelection(2, 2);
        });

        robot.keyPress(VK_CONTROL);
        robot.keyPress(VK_PAGE_DOWN);
        robot.keyRelease(VK_PAGE_DOWN);
        robot.keyRelease(VK_CONTROL);

        SwingUtilities.invokeAndWait(() -> {
            checkSelection(4, 4);
            table.setColumnSelectionInterval(2, 2);
            checkSelection(2, 2);
        });

        robot.keyPress(VK_CONTROL);
        robot.keyPress(VK_SHIFT);
        robot.keyPress(VK_PAGE_UP);
        robot.keyRelease(VK_PAGE_UP);
        robot.keyRelease(VK_SHIFT);
        robot.keyRelease(VK_CONTROL);

        SwingUtilities.invokeAndWait(() -> {
            checkSelection(0, 0, 1, 2);
            table.setColumnSelectionInterval(2, 2);
            checkSelection(2, 2);
        });

        robot.keyPress(VK_CONTROL);
        robot.keyPress(VK_SHIFT);
        robot.keyPress(VK_PAGE_DOWN);
        robot.keyRelease(VK_PAGE_DOWN);
        robot.keyRelease(VK_SHIFT);
        robot.keyRelease(VK_CONTROL);

        SwingUtilities.invokeAndWait(() -> {
            checkSelection(4, 2, 3, 4);
            table.setModel(getTableModel2());
            table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            table.setRowSelectionInterval(0, 0);
            table.setColumnSelectionInterval(0, 0);
            table.scrollRectToVisible(new Rectangle(ltr ? 0 : table.getWidth(), 0, 1, 1));
            checkSelection(0, 0);
        });

        robot.keyPress(VK_CONTROL);
        robot.keyPress(VK_PAGE_DOWN);
        robot.keyRelease(VK_PAGE_DOWN);
        robot.keyRelease(VK_CONTROL);

        SwingUtilities.invokeAndWait(() -> {
            checkSelection(6, 6);
            table.setColumnSelectionInterval(29, 29);
            table.scrollRectToVisible(new Rectangle(ltr ? table.getWidth() : 0, 0, 1, 1));
            checkSelection(29, 29);
        });

        robot.keyPress(VK_CONTROL);
        robot.keyPress(VK_PAGE_UP);
        robot.keyRelease(VK_PAGE_UP);
        robot.keyRelease(VK_CONTROL);

        SwingUtilities.invokeAndWait(() -> checkSelection(23, 23));

        System.out.println("Done with ltr: " + ltr);
    }

    private static void setupRTL() throws InterruptedException, InvocationTargetException {
        ltr = false;
        SwingUtilities.invokeAndWait(() -> {
            table.setModel(getTableModel1());
            table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
            sp.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        });
    }

    private static void checkSelection(int col, int... allCols) {
        int trow = table.getSelectionModel().getLeadSelectionIndex();
        int[] trows = table.getSelectedRows();
        int tcol = table.getColumnModel().getSelectionModel().getLeadSelectionIndex();
        int[] tcols = table.getSelectedColumns();

        if (trow != 0) {
            throw new RuntimeException("Wrong lead row");
        }

        if (trows.length != 1 || trows[0] != 0) {
            throw new RuntimeException("Bad row selection");
        }

        if (col != tcol) {
            throw new RuntimeException("Wrong lead col");
        }

        if (allCols == null || allCols.length == 0) {
            if (tcols.length != 0) {
                throw new RuntimeException("Should be no cols selected");
            }
        } else {
            Arrays.sort(tcols);
            Arrays.sort(allCols);

            for (int c : allCols) {
                if (Arrays.binarySearch(tcols, c) < 0) {
                    throw new RuntimeException("Wrong column selection");
                }
            }

            for (int c : tcols) {
                if (Arrays.binarySearch(allCols, c) < 0) {
                    throw new RuntimeException("Wrong column selection");
                }
            }
        }
    }

}
