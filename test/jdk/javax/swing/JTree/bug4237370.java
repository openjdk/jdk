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
 * @bug 4237370
 * @summary Tests that JTree calls TreeExpansionListener methods
 *          after it has been updated due to expanded/collapsed event
 * @run main bug4237370
 */

import java.lang.reflect.InvocationTargetException;

import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;

public class bug4237370 {
    static class TestTree extends JTree implements TreeExpansionListener {
        int[] testMap = {1, 2};
        int testIndex = 0;

        private void testRowCount() {
            int rows = getRowCount();
            if (rows != testMap[testIndex]) {
                throw new RuntimeException("Bad row count: reported " + rows +
                                " instead of " + testMap[testIndex]);
            } else {
                testIndex++;
            }
        }

        public void treeExpanded(TreeExpansionEvent e) {
            testRowCount();
        }

        public void treeCollapsed(TreeExpansionEvent e) {
            testRowCount();
        }

        public TestTree() {
            super((TreeModel)null);
            DefaultMutableTreeNode top = new DefaultMutableTreeNode("Root");
            top.add(new DefaultMutableTreeNode("Sub 1"));
            setModel(new DefaultTreeModel(top));
            addTreeExpansionListener(this);
        }
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        SwingUtilities.invokeAndWait(() -> {
            TestTree tree = new TestTree();
            tree.collapseRow(0);
            tree.expandRow(0);
        });
    }
}
