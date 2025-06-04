/*
 * Copyright (c) 2004, 2023, Oracle and/or its affiliates. All rights reserved.
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
   @test
   @bug 4890345
   @requires (os.family == "windows")
   @summary 1.4.2 REGRESSION: JComboBox has problem in JTable in Windows L&F
   @key headful
*/

import java.util.Vector;
import java.awt.BorderLayout;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

public class bug4890345 {

    volatile boolean passed = false;
    volatile boolean isLafOk = true;

    volatile JFrame mainFrame;
    volatile JTable tbl;

    public static void main(String[] args) throws Exception {
        bug4890345 test = new bug4890345();
        try {
            SwingUtilities.invokeAndWait(test::createUI);
            if (!test.isLafOk) {
                throw new RuntimeException("Could not create Win L&F");
            }
            test.test();
        } finally {
            JFrame f = test.mainFrame;
            if (f != null) {
                SwingUtilities.invokeAndWait(() -> f.dispose());
            }
        }
    }

    void createUI() {
        try {
            UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
        } catch (Exception ex) {
            System.err.println("Can not initialize Windows L&F. Testing skipped.");
            isLafOk = false;
        }

        if (isLafOk) {
            mainFrame = new JFrame("Bug4890345");
            String[] items = {"tt", "aa", "gg", "zz", "dd", "ll" };
            JComboBox<String> comboBox = new JComboBox<String>(items);

            tbl = new JTable();
            JScrollPane panel = new JScrollPane(tbl);
            TableModel tm = createTableModel();
            tbl.setModel(tm);
            tbl.setRowHeight(20);
            tbl.getColumnModel().getColumn(1).setCellEditor(
                new DefaultCellEditor(comboBox));

            comboBox.addPopupMenuListener(new PopupMenuListener() {
                public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                    passed = true;
                }

                public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {}
                public void popupMenuCanceled(PopupMenuEvent e) {}
            });

            mainFrame.setLayout(new BorderLayout());
            mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            mainFrame.add(panel, BorderLayout.CENTER);
            mainFrame.pack();
            mainFrame.setLocationRelativeTo(null);
            mainFrame.setVisible(true);
        }
    }

    public void test() throws Exception {
        Robot robo = new Robot();
        robo.setAutoDelay(50);
        robo.delay(1000);
        tbl.editCellAt(0,0);

        robo.keyPress(KeyEvent.VK_TAB);
        robo.keyRelease(KeyEvent.VK_TAB);

        robo.keyPress(KeyEvent.VK_TAB);
        robo.keyRelease(KeyEvent.VK_TAB);

        robo.keyPress(KeyEvent.VK_F2);
        robo.keyRelease(KeyEvent.VK_F2);

        robo.keyPress(KeyEvent.VK_DOWN);
        robo.keyRelease(KeyEvent.VK_DOWN);

        robo.keyPress(KeyEvent.VK_ENTER);
        robo.keyRelease(KeyEvent.VK_ENTER);

        robo.delay(1000);

        if (!passed) {
            throw new RuntimeException("Popup was not shown after VK_DOWN press. Test failed.");
        }
    }

    private TableModel createTableModel() {
        Vector<String> hdr = new Vector<String>();
        hdr.add("One");
        hdr.add("Two");
        Vector<Vector> data = new Vector<Vector>();
        Vector<String> row = new Vector<String>();
        row.add("tt");
        row.add("dd");
        data.add(row);
        row = new Vector<String>();
        row.add("ll");
        row.add("jj");
        data.add(row);
        return new DefaultTableModel(data, hdr);
    }
}
