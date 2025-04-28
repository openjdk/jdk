/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4193267
 * @summary Tests that JList first and last visible indices are
 * updated properly
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4193267
 */

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class bug4193267 {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
            Resize the frame "JList" with a different ways and scroll the list
            (if it possible). The indices of first and last visible elements
            should be indicated in the corresponding fields in "Index" frame.
            If the indicated indices is not right then test fails.

            Note:
              - the first and last visible indices should be -1 if nothing
              is visible;
              - the first or last visible cells may only be partially visible.
            """;
        PassFailJFrame.builder()
            .title("bug4193267 Instructions")
            .instructions(INSTRUCTIONS)
            .positionTestUI(WindowLayouts::rightOneRow)
            .columns(35)
            .testUI(bug4193267::initialize)
            .build()
            .awaitAndCheck();
    }

    private static List initialize() {
        String[] data = {"000000000000000", "111111111111111",
            "222222222222222", "333333333333333",
            "444444444444444", "555555555555555",
            "666666666666666", "777777777777777",
            "888888888888888", "999999999999999"};

        JFrame[] fr = new JFrame[2];
        fr[0] = new JFrame("JList");
        JList lst = new JList(data);
        lst.setLayoutOrientation(JList.VERTICAL_WRAP);
        lst.setVisibleRowCount(4);
        JScrollPane jsp = new JScrollPane(lst);
        fr[0].add(jsp);
        fr[0].setSize(400, 200);

        JPanel pL = new JPanel();
        pL.setLayout(new GridLayout(2, 1));
        pL.add(new JLabel("First Visible Index"));
        pL.add(new JLabel("Last Visible Index"));

        JPanel p = new JPanel();
        p.setLayout(new GridLayout(2, 1));
        JTextField first = new JTextField("0", 2);
        first.setEditable(false);
        first.setBackground(Color.white);
        p.add(first);
        JTextField last = new JTextField("9", 2);
        last.setEditable(false);
        last.setBackground(Color.white);
        p.add(last);

        fr[1] = new JFrame("Index");
        fr[1].setSize(200, 200);
        fr[1].setLayout(new FlowLayout());
        fr[1].add(pL);
        fr[1].add(p);

        jsp.getViewport().addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                first.setText(String.valueOf(lst.getFirstVisibleIndex()));
                last.setText(String.valueOf(lst.getLastVisibleIndex()));
            }
        });
        List frameList = List.of(fr[0], fr[1]);
        return frameList;
    }
}
