/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4119993
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Check that mouse button 3 is reserved for popup invocation not selection.
 * @run main/manual bug4119993
 */

import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.border.BevelBorder;

/*
 * This is a sort of negative test. Mouse Button 3 is not supposed to cause selections.
 * If it did, then it would not be useable to invoke popup menus.
 * So this popup menu test .. does not popup menus.
 */

public class bug4119993 {

    static final String INSTRUCTIONS = """
<html>
        The test window contains a text area, a table, and a list.
        <p>
        For each component, try to select text/cells/rows/items as appropriate
        using the <font size=+2 color=red>RIGHT</font> mouse button (Mouse Button 3).
        <p>
        If the selection changes, then press <em><b>FAIL</b></em>.
        <p>
        If the selection does not change, press <em><b>PASS</b></em></html
 </html>
    """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
            .instructions(INSTRUCTIONS)
            .columns(60)
            .testUI(bug4119993::createUI)
            .build()
            .awaitAndCheck();
    }

    static JFrame createUI() {
        JFrame frame = new JFrame("bug4119993");
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        frame.add(p);

        String text = "This is some text that you should try to select using the right mouse button";
        JTextArea area = new JTextArea(text, 5, 40);
        JScrollPane scrollpane0 = new JScrollPane(area);
        scrollpane0.setBorder(new BevelBorder(BevelBorder.LOWERED));
        scrollpane0.setPreferredSize(new Dimension(430, 200));
        p.add(scrollpane0);

        String[][] data = new String[5][5];
        String[] cols = new String[5];
        for (int r = 0; r < 5; r ++) {
            cols[r] = "col " + r;
            for (int c = 0; c < 5; c ++) {
               data[r][c] = "(" + r + "," + c + ")";
            }
        }

        JTable tableView = new JTable(data, cols);
        JScrollPane scrollpane = new JScrollPane(tableView);
        scrollpane.setBorder(new BevelBorder(BevelBorder.LOWERED));
        scrollpane.setPreferredSize(new Dimension(430, 200));
        p.add(scrollpane);

        String[] s = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "10"};
        JList listView = new JList(s);
        JScrollPane scrollpane2 = new JScrollPane(listView);
        scrollpane2.setBorder(new BevelBorder(BevelBorder.LOWERED));
        scrollpane2.setPreferredSize(new Dimension(430, 200));
        p.add(scrollpane2);

       frame.pack();
       return frame;
    }
}
