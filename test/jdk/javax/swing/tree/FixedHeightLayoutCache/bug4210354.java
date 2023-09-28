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

/*
 * @test
 * @bug 4210354
 * @summary Tests whether method FixedHeightLayoutCache.getBounds returns bad Rectangle
 * @run main bug4210354
 */

import java.awt.Rectangle;

import javax.swing.tree.AbstractLayoutCache;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.FixedHeightLayoutCache;
import javax.swing.tree.TreePath;

public class bug4210354 {
    static class DummyNodeDimensions extends AbstractLayoutCache.NodeDimensions {
        private final Rectangle rectangle;

        public DummyNodeDimensions(Rectangle r) {
            rectangle = r;
        }
        public Rectangle getNodeDimensions(Object value, int row, int depth,
                                           boolean expanded, Rectangle bounds) {
            return rectangle;
        }

        /* create the TreeModel of depth 1 with specified num of children */
        public DefaultTreeModel getTreeModelILike(int childrenCount) {
            DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
            for (int i = 0; i < childrenCount; i++) {
                DefaultMutableTreeNode child =
                        new DefaultMutableTreeNode("root.child" + i);
                root.insert(child, i);
            }
            return new DefaultTreeModel(root);
        }
    }

    public void init() {
        int x = 1, y = 2, dx = 3, dy = 4, h = 3;
        DummyNodeDimensions dim = new DummyNodeDimensions(new Rectangle(x, y, dx, dy));
        FixedHeightLayoutCache fhlc = new FixedHeightLayoutCache();
        fhlc.setModel(dim.getTreeModelILike(3));
        fhlc.setRootVisible(true);
        fhlc.setNodeDimensions(dim);
        fhlc.setRowHeight(h);
        int row = 0;
        TreePath path = fhlc.getPathForRow(row);
        Rectangle r = fhlc.getBounds(path, new Rectangle());
        Rectangle r2 = new Rectangle(x, row * h, dx, h);
        if (r.width != r2.width) {
            throw new RuntimeException("FixedHeightLayoutCache.getBounds returns bad Rectangle");
        }
    }

    public static void main(String[] args) throws Exception {
        bug4210354 b = new bug4210354();
        b.init();
    }
}
