/*
 * Copyright (c) 1999, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4213868
 * @summary Tests if AccessibleJTreeNode.getAccessibleIndexInParent() returns
 * correct value
 * @run main bug4213868
 */

import javax.accessibility.AccessibleContext;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;

public class bug4213868 {
    public static JTree createTree() {
        DefaultMutableTreeNode root =
                new DefaultMutableTreeNode(0, true);
        JTree tree = new JTree(root);
        for (int i = 1; i < 10; i++) {
            root.add(new DefaultMutableTreeNode(i));
        }
        return tree;
    }

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTree parent = createTree();
            AccessibleContext c = parent.getAccessibleContext()
                                        .getAccessibleChild(0)
                                        .getAccessibleContext();
            if (c.getAccessibleChild(1)
                 .getAccessibleContext()
                 .getAccessibleIndexInParent() != 1) {
                throw new RuntimeException("Test failed: " +
                        "AccessibleJTreeNode.getAccessibleIndexInParent() " +
                        "returns incorrect value");
            }
        });
    }
}
