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
 * @bug 4199472
 * @summary Tests that node changed for the root of the tree update the
 *          structure.
 * @run main NodeChangedTest
 */

import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

public class NodeChangedTest {
    public static void main(String[] args) {
        // Create 3 nodes
        final DefaultMutableTreeNode root = new DefaultMutableTreeNode("root",
                true);
        final DefaultMutableTreeNode child = new DefaultMutableTreeNode("child",
                true);
        final DefaultMutableTreeNode leaf = new DefaultMutableTreeNode("leaf",
                false);
        root.add(child);
        child.add(leaf);

        final JTree tree = new JTree(root);

        // Change the root node
        root.setUserObject("New root");
        ((DefaultTreeModel) tree.getModel()).nodeChanged(root);

        // Check
        if (!root.getUserObject().toString().equals("New root")) {
            throw new RuntimeException("Failed changing root node for default model.");
        }

        // Change to large model
        tree.setLargeModel(true);
        tree.setRowHeight(20);
        root.setUserObject("root");
        tree.setModel(new DefaultTreeModel(root));
        root.setUserObject("New root");
        ((DefaultTreeModel) tree.getModel()).nodeChanged(root);

        // Check again
        if (!root.getUserObject().toString().equals("New root")) {
            throw new RuntimeException("Failed changing root node for large model.");
        }
    }
}
