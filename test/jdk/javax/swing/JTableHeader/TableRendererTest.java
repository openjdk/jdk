/*
 * Copyright (c) 2006, 2022, Oracle and/or its affiliates. All rights reserved.
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
   @run main/manual TableRendererTest
*/

import java.awt.Component;
import java.awt.Robot;

import javax.swing.UIManager;
import javax.swing.SwingUtilities;
import javax.swing.JFrame;
import javax.swing.JTable;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;

public class TableRendererTest{
    static TableRendererSample testObj;
    static Boolean PassFailStatus = true;

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
        Robot robot = new Robot();
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    Thread.currentThread().setUncaughtExceptionHandler(new ExceptionCheck());
                    testObj = new TableRendererSample();
                    //Adding the Test Frame to handle dispose
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        robot.delay(2000);
        robot.waitForIdle();

        if (!PassFailStatus) {
            testObj.dispose();
            throw new RuntimeException("Test Case Failed. NPE raised!");
        }
        System.out.println("Test Pass!");
        testObj.dispose();
    }

    static class ExceptionCheck implements Thread.UncaughtExceptionHandler {
        public void uncaughtException(Thread t, Throwable e)
        {
            PassFailStatus = false;
            System.out.println("Uncaught Exception Handled : " + e);
        }
    }
}

class TableRendererSample extends JFrame {
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

    static class MyJTable extends JTable {
        public MyJTable(TableModel model){
            super(model);
        }
    }

    private JPanel jContentPane = null;
    private JScrollPane jScrollPane = null;
    private JTable table = null;

    public TableRendererSample() {
        super();
        initialize();
        table.updateUI();
        this.setVisible(true);
    }

    private void initialize() {
        this.setSize(363, 658);
        this.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        this.setContentPane(getJContentPane());
        this.setTitle("JFrame");
    }

    private JPanel getJContentPane() {
        if (jContentPane == null) {
            jContentPane = new JPanel();
            jContentPane.setLayout(null);
            jContentPane.add(getJScrollPane(), null);
        }
        return jContentPane;
    }

    private JScrollPane getJScrollPane() {
        if (jScrollPane == null) {
            jScrollPane = new JScrollPane();
            jScrollPane.setBounds(new java.awt.Rectangle(0,0,350,618));
            jScrollPane.setViewportView(getTable());
        }
        return jScrollPane;
    }

    private JTable getTable() {
        if (table == null) {
            table = new MyJTable(new MyModel());
            JTableHeader header = table.getTableHeader();
            TableCellRenderer render = new DecoratedHeaderRenderer(header.getDefaultRenderer());
            header.setDefaultRenderer(render);
        }
        return table;
    }

    private static class DecoratedHeaderRenderer implements TableCellRenderer {
        public DecoratedHeaderRenderer(TableCellRenderer render) {
            this.render = render;
        }

        private final TableCellRenderer render;

        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            return render.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        }
    }
}
