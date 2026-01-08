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
 * @bug 4270654
 * @summary Tests that selection change in JTree does not cause unnecessary
            scrolling.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4270654
 */

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;

public class bug4270654 {
    static final String INSTRUCTIONS = """
        Select the "dan" node and scroll to the right a little using the
        scrollbar. Then press down arrow key. If the tree unscrolls back
        to the left, the test FAILS. Otherwise, the test PASSES.
    """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("bug4270654 Test Instructions")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(bug4270654::createUI)
                .build()
                .awaitAndCheck();
    }

    static JFrame createUI() {
        JFrame f = new JFrame("JTree Scroll Back Test");
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
        String[] lev1 = {"foo", "bar", "dan"};
        String[][] lev2 = {
                {},
                {"small", "a nice big node for testing"},
                {"xyz", "pqd", "a really really big node for testing"}};
        for (int i = 0; i < lev1.length; i++) {
            DefaultMutableTreeNode child = new DefaultMutableTreeNode(lev1[i]);
            root.add(child);
            for (int j = 0; j < lev2[i].length; j++)
                child.add(new DefaultMutableTreeNode(lev2[i][j]));
        }
        final JTree tree = new JTree(root);
        tree.expandRow(3);
        f.add(new JScrollPane(tree));
        f.setSize(200, 200);
        return f;
    }
}
