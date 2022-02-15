/*
 * Copyright (c) 2006, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6394566
 * @key headful
 * @summary Tests that ESC moves the focus from the header to the table
 * @library ../../../../regtesthelpers
 * @build SwingTestHelper JRobot
 * @run main bug6394566
 */

import java.awt.Component;
import java.awt.event.KeyEvent;
import java.awt.event.FocusEvent;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.JTableHeader;
import javax.swing.table.DefaultTableModel;

public class bug6394566 extends SwingTestHelper {
    private JTable table;
    private JTableHeader header;

    public static void main(String[] args) throws Throwable {
        new bug6394566().run(args);
    }

    protected Component createContentPane() {
        table = new JTable(new DefaultTableModel(2, 2));
        header = table.getTableHeader();
        return new JScrollPane(table);
    }

    public void onEDT10() {
        // Give the table the focus.
        requestAndWaitForFocus(table);
    }

    //First, give the header focus using F8 on the JTable.
    //This will fail prior to mustang b72.
    public void onEDT20() {
        waitForEvent(header, FocusEvent.FOCUS_GAINED); //set up focus listener
        robot.hitKey(KeyEvent.VK_F8);
    }

    //Next, give the table back the focus using ESC.
    //This will fail prior to the build with this bugfix.
    public void onEDT30() {
        waitForEvent(table, FocusEvent.FOCUS_GAINED); //set up focus listener
        robot.hitKey(KeyEvent.VK_ESCAPE);
    }
}
