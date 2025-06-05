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
 * @bug 4169215
 * @summary Accessibility hierarchy JTree node test.
 * @run main bug4169215
 */

import javax.accessibility.AccessibleContext;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;

public class bug4169215 {
    public static void main(String[] args) {
        // create the tree
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("top");
        DefaultMutableTreeNode nodeA = new DefaultMutableTreeNode("A");
        DefaultMutableTreeNode nodeB = new DefaultMutableTreeNode("B");
        root.add(nodeA);
        root.add(nodeB);
        JTree tree = new JTree(root);

        // find the AccessibleContext of the tree
        AccessibleContext actree = tree.getAccessibleContext();

        // find the AccessibleContext of top node of the tree
        AccessibleContext act = actree.getAccessibleChild(0).getAccessibleContext();

        // find the AccessibleContext of the first child of the table ->
        // the AccessibleContext of nodeA
        AccessibleContext accA = act.getAccessibleChild(0).getAccessibleContext();

        // find the AccessibleContext of the next sibling of nodeA, by getting
        // child+1 of the parent (the table)
        AccessibleContext accB = act.getAccessibleChild(
                accA.getAccessibleIndexInParent()+1).getAccessibleContext();

        // look to see who the sibling is.
        if (accB.getAccessibleName().compareTo("B") != 0) {
            throw new RuntimeException("Parent node is a sibling instead!");
        }
    }
}
