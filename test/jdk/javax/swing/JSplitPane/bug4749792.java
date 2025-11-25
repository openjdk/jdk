/*
 * Copyright (c) 2003, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4749792
 * @requires (os.family == "windows")
 * @summary Split pane border is not painted properly
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4749792
 */

import java.awt.BorderLayout;
import java.awt.Dimension;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSplitPane;

public class bug4749792 {
    static final String INSTRUCTIONS = """
        If the right/bottom edges of JSplitPane's border is missing then the
        test fails. If it is visible, then the test passes.
    """;

    static JFrame createUI() {
        JFrame frame = new JFrame("JSplitPane Border Test");
        frame.setSize(450, 220);
        JPanel left = new JPanel();
        JPanel right = new JPanel();
        left.setPreferredSize(new Dimension(200, 200));
        right.setPreferredSize(new Dimension(200, 200));
        JSplitPane sp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        frame.add(sp);

        JPanel south = new JPanel();
        south.setPreferredSize(new Dimension(20, 20));
        frame.add(south, BorderLayout.SOUTH);

        JPanel east = new JPanel();
        east.setPreferredSize(new Dimension(20, 20));
        frame.add(east, BorderLayout.EAST);

        return frame;
    }

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
            .title("bug4749792 Test Instructions")
            .instructions(INSTRUCTIONS)
            .columns(40)
            .testUI(bug4749792::createUI)
            .build()
            .awaitAndCheck();
    }
}
