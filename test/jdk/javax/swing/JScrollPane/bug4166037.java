/*
 * Copyright (c) 2000, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4166037
 * @summary Tests if changes to JScrollPane propagate to ScrollPaneLayout
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4166037
 */

import java.awt.Color;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneLayout;

public class bug4166037 {
    static final String INSTRUCTIONS = """
        Press button "Never". Scroll bars should disappear.
        Press button "Always". Scroll bars should appear.
        Press button "Colhead". Label ColumnHeader should
        get replaced with yellow rectangles.
        Press button "Corner". You should see 4 green
        rectangles in the corners of the scroll pane.
        Press button "Rowhead". Label RowHeader should get
        replaced with yellow rectangles.
        Press button "Viewport". Viewport (the rest of the
        area except scrollbars) should get replaced with yellow
        rectangles.
        If the behavior is as described, the test PASSES.
        Otherwise, this test FAILS.
    """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("bug4166037 Test Instructions")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(bug4166037::createUI)
                .logArea()
                .build()
                .awaitAndCheck();
    }

    static JFrame createUI() {
        JFrame f = new JFrame("JScrollPane in JScrollLayout Test");
        JScrollPane scroll = new JScrollPane(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        JPanel p = new JPanel();
        scroll.setSize(200, 200);
        f.add(scroll);
        f.setLayout(new BoxLayout(f.getContentPane(), BoxLayout.Y_AXIS));
        JButton bn = new JButton("Never");
        bn.addActionListener(e -> {
            PassFailJFrame.log("pane before: "
                    + scroll.getVerticalScrollBarPolicy()
                    + scroll.getHorizontalScrollBarPolicy());
            PassFailJFrame.log("layout before: "
                    + ((ScrollPaneLayout) scroll.getLayout()).getVerticalScrollBarPolicy()
                    + ((ScrollPaneLayout) scroll.getLayout()).getHorizontalScrollBarPolicy());
            scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
            scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            PassFailJFrame.log("pane after: "
                    + scroll.getVerticalScrollBarPolicy()
                    + scroll.getHorizontalScrollBarPolicy());
            PassFailJFrame.log("layout after: "
                    + ((ScrollPaneLayout) scroll.getLayout()).getVerticalScrollBarPolicy()
                    + ((ScrollPaneLayout) scroll.getLayout()).getHorizontalScrollBarPolicy());
        });
        JButton ba = new JButton("Always");
        ba.addActionListener(e -> {
            PassFailJFrame.log("pane before: "
                    + scroll.getVerticalScrollBarPolicy()
                    + scroll.getHorizontalScrollBarPolicy());
            PassFailJFrame.log("layout before: "
                    + ((ScrollPaneLayout) scroll.getLayout()).getVerticalScrollBarPolicy()
                    + ((ScrollPaneLayout) scroll.getLayout()).getHorizontalScrollBarPolicy());
            scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
            scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
            PassFailJFrame.log("pane after: "
                    + scroll.getVerticalScrollBarPolicy()
                    + scroll.getHorizontalScrollBarPolicy());
            PassFailJFrame.log("layout after: "
                    + ((ScrollPaneLayout) scroll.getLayout()).getVerticalScrollBarPolicy()
                    + ((ScrollPaneLayout) scroll.getLayout()).getHorizontalScrollBarPolicy());
        });
        JLabel ch = new JLabel("ColumnHeader");
        scroll.setColumnHeaderView(ch);
        JButton b1 = new JButton("Colhead");
        b1.addActionListener(e -> {
            JPanel filler = new JPanel();
            filler.setSize(150, 150);
            filler.setBackground(Color.yellow);
            scroll.getColumnHeader().add(filler);
        });
        JButton b2 = new JButton("Corners");
        b2.addActionListener(e -> {
            JPanel filler1 = new JPanel();
            filler1.setSize(150, 150);
            filler1.setBackground(Color.green);
            scroll.setCorner(JScrollPane.LOWER_RIGHT_CORNER, filler1);
            JPanel filler2 = new JPanel();
            filler2.setSize(150, 150);
            filler2.setBackground(Color.green);
            scroll.setCorner(JScrollPane.LOWER_LEFT_CORNER, filler2);
            JPanel filler3 = new JPanel();
            filler3.setSize(150, 150);
            filler3.setBackground(Color.green);
            scroll.setCorner(JScrollPane.UPPER_RIGHT_CORNER, filler3);
            JPanel filler4 = new JPanel();
            filler4.setSize(150, 150);
            filler4.setBackground(Color.green);
            scroll.setCorner(JScrollPane.UPPER_LEFT_CORNER, filler4);
        });
        JLabel rh = new JLabel("RowHeader");
        scroll.setRowHeaderView(rh);
        JButton b3 = new JButton("Rowhead");
        b3.addActionListener(e -> {
            JPanel filler = new JPanel();
            filler.setSize(150, 150);
            filler.setBackground(Color.yellow);
            scroll.getRowHeader().add(filler);
        });
        JButton b4 = new JButton("Viewport");
        b4.addActionListener(e -> {
            JPanel filler = new JPanel();
            filler.setSize(150, 150);
            filler.setBackground(Color.yellow);
            scroll.getViewport().add(filler);
        });

        p.add(bn);
        p.add(ba);
        p.add(b1);
        p.add(b2);
        p.add(b3);
        p.add(b4);
        f.add(p);
        f.setSize(300, 300);
        return f;
    }
}
