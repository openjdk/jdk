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
   @bug 6429812
   @key headful
   @requires (os.family == "windows")
   @library /java/awt/regtesthelpers
   @build PassFailJFrame
   @summary Test to check if table is printed without NPE
   @run main TableRendererTest
*/

import java.awt.Component;
import java.awt.Robot;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.UIManager;
import javax.swing.SwingUtilities;
import javax.swing.JFrame;
import javax.swing.JTable;
import javax.swing.JScrollPane;

import javax.swing.table.AbstractTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;

public class TableRendererTest{
    static JFrame frame = null;
    private static final AtomicReference<Throwable> exception =
            new AtomicReference<>();

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");

        Robot robot = new Robot();
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                Thread.currentThread().setUncaughtExceptionHandler(
                        new ExceptionCheck());
                initialize();
            }
        });

        robot.delay(2000);
        robot.waitForIdle();

        SwingUtilities.invokeAndWait(frame::dispose);

        if (exception.get() != null) {
            throw new RuntimeException("Test Case Failed. NPE raised!",
                    exception.get());
        }
        System.out.println("Test Pass!");
    }

    static void initialize() {
        frame = new JFrame();
        frame.setTitle("JFrame");
        JTable table = new JTable(new MyModel());
        JScrollPane jScrollPane = new JScrollPane();
        jScrollPane.setBounds(new java.awt.Rectangle(0,0,350,618));
        jScrollPane.setViewportView(table);

        JTableHeader header = table.getTableHeader();
        TableCellRenderer render = new DecoratedHeaderRenderer(header.getDefaultRenderer());
        header.setDefaultRenderer(render);

        frame.getContentPane().add(jScrollPane,null);
        frame.setSize(363, 658);
        frame.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        table.updateUI();

        frame.setVisible(true);
    }

    static class ExceptionCheck implements Thread.UncaughtExceptionHandler {
        public void uncaughtException(Thread t, Throwable e)
        {
            exception.set(e);
            System.err.println("Uncaught Exception Handled : " + e);
        }
    }

    static class MyModel extends AbstractTableModel {
        public int getRowCount() {
            return 10;
        }
        public int getColumnCount() {
            return 10;
        }
        public Object getValueAt(int rowIndex, int columnIndex) {
            return ""+rowIndex + " X " + columnIndex;
        }
    }

    static class DecoratedHeaderRenderer implements TableCellRenderer {
        public DecoratedHeaderRenderer(TableCellRenderer render) {
            this.render = render;
        }

        private final TableCellRenderer render;

        public Component getTableCellRendererComponent(JTable table,
                                                       Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            return render.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);
        }
    }
}
