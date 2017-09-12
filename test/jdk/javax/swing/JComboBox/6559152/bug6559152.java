/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6559152
 * @summary Checks that you can select an item in JComboBox with keyboard
 *          when it is a JTable cell editor.
 * @author Mikhail Lapshin
 * @library ../../../../lib/testlibrary
 * @build ExtendedRobot
 * @run main bug6559152
 */

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.KeyEvent;

public class bug6559152 {
    private JFrame frame;
    private JComboBox cb;
    private ExtendedRobot robot;

    public static void main(String[] args) throws Exception {
        final bug6559152 test = new bug6559152();
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    test.setupUI();
                }
            });
            test.test();
        } finally {
            if (test.frame != null) {
                test.frame.dispose();
            }
        }
    }

    private void setupUI() {
        frame = new JFrame();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        DefaultTableModel model = new DefaultTableModel(1, 1);
        JTable table = new JTable(model);

        cb = new JComboBox(new String[]{"one", "two", "three"});
        cb.setEditable(true);
        table.getColumnModel().getColumn(0).setCellEditor(new DefaultCellEditor(cb));
        frame.add(cb);

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void test() throws Exception {
        robot = new ExtendedRobot();
        robot.waitForIdle();
        testImpl();
        robot.waitForIdle();
        checkResult();
    }

    private void testImpl() throws Exception {
        robot.type(KeyEvent.VK_DOWN);
        robot.waitForIdle();
        robot.type(KeyEvent.VK_DOWN);
        robot.waitForIdle();
        robot.type(KeyEvent.VK_ENTER);
    }

    private void checkResult() {
        if (cb.getSelectedItem().equals("two")) {
            System.out.println("Test passed");
        } else {
            System.out.println("Test failed");
            throw new RuntimeException("Cannot select an item " +
                    "from popup with the ENTER key.");
        }
    }
}
