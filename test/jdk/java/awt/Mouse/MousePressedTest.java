/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Container;
import java.awt.GridLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;

/*
 * @test
 * @bug 4268759
 * @summary Tests whether clicking on the edge of a lightweight button
 *          causes sticking behavior
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual MousePressedTest
 */

public class MousePressedTest {

    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                1. Click and hold on the very bottom border (2-pixel-wide border) of the
                   JButton. Then drag the mouse straight down out of the JButton and
                   into the JRadioButton, and release the mouse button
                2. If the component remains highlighted as if the mouse button is still
                   down, the test fails
                """;

        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(initialize())
                .build()
                .awaitAndCheck();
    }

    public static JFrame initialize() {
        JFrame f = new JFrame("JButton Test");
        JPanel p = new JPanel();
        p.setLayout(new GridLayout(2, 2));
        JButton b = new JButton("JButton");
        p.add(b);
        JCheckBox cb = new JCheckBox("JCheckBox");
        p.add(cb);
        JRadioButton rb = new JRadioButton("JRadioButton");
        p.add(rb);
        p.add(new JToggleButton("JToggleButton"));

        JScrollPane j = new JScrollPane(p,
                JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);

        Container c = f.getContentPane();
        c.setLayout(new GridLayout(1, 1));
        c.add(j);
        f.pack();
        return f;
    }
}
