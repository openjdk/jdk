/*
 * Copyright (c) 1998, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.BorderLayout;
import java.awt.Point;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JPanel;

/*
 * @test
 * @bug 4185024
 * @summary Tests that Heavyweight combo boxes on JDesktop work correctly
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4185024
 */

public class bug4185024 {
    private static final String INSTRUCTIONS = """
            Click on the JComboBox button inside the JInternalFrame to bring up the menu.
            Select one of the menu items and verify that the choice is highlighted correctly.
            Click and drag the menu's scroll bar down and verify that it causes the menu to scroll down.

            Inside JInternalFrame:
            This test is for the JComboBox in the JInternalFrame.
            To test, please click on the combobox and check the following:
            Does the selection in the popup list follow the mouse?
            Does the popup list respond to clicking and dragging the scroll bar?
            If so, press PASS, otherwise, press FAIL.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(50)
                .testUI(bug4185024::createTestUI)
                .build()
                .awaitAndCheck();
    }

    public static JFrame createTestUI() {
        JFrame frame = new JFrame("bug4185024");
        JPanel p = new JPanel();
        p.setLayout(new BorderLayout());
        JDesktopPane desktop = new JDesktopPane();
        p.add(desktop);
        frame.add(p);

        JComboBox months = new JComboBox();
        months.addItem("January");
        months.addItem("February");
        months.addItem("March");
        months.addItem("April");
        months.addItem("May");
        months.addItem("June");
        months.addItem("July");
        months.addItem("August");
        months.addItem("September");
        months.addItem("October");
        months.addItem("November");
        months.addItem("December");
        months.getAccessibleContext().setAccessibleName("Months");
        months.getAccessibleContext().setAccessibleDescription("Choose a month of the year");

        // Set this to true and the popup works correctly...
        months.setLightWeightPopupEnabled(false);

        addFrame("Months", desktop, months);

        frame.setSize(300, 300);
        return frame;
    }

    private static void addFrame(String title, JDesktopPane desktop, JComponent component) {
        JInternalFrame jf = new JInternalFrame(title);
        Point newPos = new Point(20, 20);
        jf.setResizable(true);
        jf.add(component);
        jf.setLocation(newPos);
        desktop.add(jf);

        jf.pack();
        jf.show();
    }
}
