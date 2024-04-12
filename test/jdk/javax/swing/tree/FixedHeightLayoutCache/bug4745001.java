/*
 * Copyright (c) 2002, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4745001
 * @summary JTree with setLargeModel(true) not display correctly
 *          when we expand/collapse nodes
 * @key headful
 * @run main bug4745001
*/

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Robot;

import javax.swing.JFrame;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.SwingUtilities;

public class bug4745001 {

    static JTree tree;
    static JFrame fr;
    boolean stateChanged;

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        robot.setAutoDelay(100);
        bug4745001 test = new bug4745001();
        try {
            SwingUtilities.invokeAndWait(() -> test.init());
            robot.waitForIdle();
            robot.delay(1000);
            test.start();
            robot.delay(1000);
            test.destroy();
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (fr != null) {
                    fr.dispose();
                }
            });
        }
    }

    public void init() {
        fr = new JFrame("Test");
        fr.getContentPane().setLayout(new FlowLayout());

        tree = new JTree();
        tree.setRowHeight(20);
        tree.setLargeModel(true);
        tree.setPreferredSize(new Dimension(100, 400));
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);

        DefaultMutableTreeNode root = new DefaultMutableTreeNode("");
        DefaultMutableTreeNode a = new DefaultMutableTreeNode("a");
        DefaultMutableTreeNode b = new DefaultMutableTreeNode("b");
        DefaultMutableTreeNode c = new DefaultMutableTreeNode("c");
        root.add(a);
        root.add(b);
        root.add(c);
        b.add(new DefaultMutableTreeNode("b1"));
        c.add(new DefaultMutableTreeNode("c2"));
        tree.setModel(new DefaultTreeModel(root));

        fr.getContentPane().add(tree);

        tree.addTreeExpansionListener(new TreeExpansionListener() {
            public void treeExpanded(TreeExpansionEvent e) {
                TreePath path = e.getPath();
                if (path != null) {
                    DefaultMutableTreeNode node =
                        (DefaultMutableTreeNode)path.getLastPathComponent();
                    node.removeAllChildren();
                    String s = (String)node.getUserObject();
                    node.add(new DefaultMutableTreeNode(s + "1"));
                    node.add(new DefaultMutableTreeNode(s + "2"));
                    node.add(new DefaultMutableTreeNode(s + "3"));
                    DefaultTreeModel model = (DefaultTreeModel)tree.getModel();
                    model.nodeStructureChanged(node);
                    synchronized (bug4745001.this) {
                        stateChanged = true;
                        bug4745001.this.notifyAll();
                    }
                }
            }

            public void treeCollapsed(TreeExpansionEvent e) {
                synchronized (bug4745001.this) {
                    stateChanged = true;
                    bug4745001.this.notifyAll();
                }
            }
        });

        fr.pack();
        fr.setVisible(true);
    }

    void changeNodeStateForRow(final int row, final boolean expand) throws Exception {
        try {
            stateChanged = false;
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    try {
                        if (expand) {
                            tree.expandRow(row);
                        } else {
                            tree.collapseRow(row);
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                 }
            });
            synchronized (this) {
                while (!stateChanged) {
                    bug4745001.this.wait();
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public void start() throws Exception {
        // expand node "c"
        changeNodeStateForRow(2, true);
        // expand node "b"
        changeNodeStateForRow(1, true);
        // collapse node "c"
        changeNodeStateForRow(1, false);
    }

    String[] expected = new String[] {"a", "b", "c", "c1", "c2", "c3"};

    public void destroy() {
        for (int i = 0; i < expected.length; i++) {
            Object obj = tree.getPathForRow(i).getLastPathComponent();
            if (!obj.toString().equals(expected[i])) {
                throw new RuntimeException("Unexpected node at row "+i);
            }
        }
    }

}
