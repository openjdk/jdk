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

/*
 * @test
 * @bug 4118860
 * @summary setToggleClickCount/getToggleClickCount have been added.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4118860
 */

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTree;

public class bug4118860 {
    static final String INSTRUCTIONS = """
        Push the "Single Click" button and try expanding/contracting
        branch nodes of the tree with one left mouse button click
        on the label part of the node (not the icon or handles).

        Then push the "Double Click" button and try doing the same using
        left mouse button double click. Single click shouldn't cause
        expanding/contracting. A double click should now be required
        to expand/contract nodes.

        If it works then the test PASSES, else the test FAILS.
    """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("bug4118860 Test Instructions")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(bug4118860::createUI)
                .build()
                .awaitAndCheck();
    }

    static JFrame createUI() {
        JFrame f = new JFrame("ToggleClickCount Test");
        JTree tr = new JTree();
        JPanel p = new JPanel();
        p.setBackground(Color.red);
        p.setLayout(new GridLayout(1, 1));
        tr.setOpaque(false);
        p.add(tr);
        f.add(p, BorderLayout.CENTER);
        JPanel bp = new JPanel();
        JButton bt1 = new JButton("Single Click");
        bt1.addActionListener(e -> {
            tr.setToggleClickCount(1);
            if (tr.getToggleClickCount() != 1) {
                throw new RuntimeException("ToggleClickCount doesn't set...");
            }
        });
        JButton bt2 = new JButton("Double Click");
        bt2.addActionListener(e -> {
            tr.setToggleClickCount(2);
            if (tr.getToggleClickCount() != 2) {
                throw new RuntimeException("ToggleClickCount doesn't set...");
            }
        });
        bp.setLayout(new GridLayout(1, 2));
        bp.add(bt1);
        bp.add(bt2);
        f.add(bp, BorderLayout.SOUTH);
        f.setSize(300, 200);
        return f;
    }
}
