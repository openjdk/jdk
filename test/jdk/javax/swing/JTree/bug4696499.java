/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4696499
 * @summary new tree model asked about nodes of previous tree model
 * @run main bug4696499
 */

import java.util.ArrayList;

import javax.swing.JTree;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

public class bug4696499 {
    public static void main(String[] args) throws Exception {
        JTree tree = new JTree();
        TreeModel model = new MyModel();
        tree.setModel(model);

        tree.setSelectionRow(1);
        model = new MyModel();
        tree.setModel(model);
    }
}

class MyModel implements TreeModel {
    private Object root = "Root";
    private ArrayList listeners = new ArrayList();
    private TreeNode ONE;
    static int next = 1;

    MyModel() {
        ONE = new DefaultMutableTreeNode(next);
        next *= 2;
    }

    public void addTreeModelListener(TreeModelListener l) {
        listeners.add(l);
    }

    public void removeTreeModelListener(TreeModelListener l) {
        listeners.remove(l);
    }

    public void valueForPathChanged(TreePath tp, Object newValue) {
    }

    public Object getRoot() {
        return root;
    }

    public boolean isLeaf(Object o) {
        return o == ONE;
    }

    public int getIndexOfChild(Object parent, Object child) {
        if (parent != root || child != ONE) {
            throw new RuntimeException("This method is called with the child " +
                    "of the previous tree model");
        }
        return 0;
    }

    public int getChildCount(Object o) {
        if (o == root) {
            return 1;
        }
        if (o == ONE) {
            return 0;
        }
        throw new IllegalArgumentException(o.toString());
    }

    public Object getChild(Object o, int index) {
        if (o != root || index != 0) {
            throw new IllegalArgumentException(o + ", " + index);
        }
        return ONE;
    }
}
