/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6507038
 * @key headful
 * @summary Verifies memory leak in BasicTreeUI TreeCellRenderer
 * @run main TreeCellRendererLeakTest
 */

import java.awt.BorderLayout;
import java.awt.Component;
import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;

public final class TreeCellRendererLeakTest {

    private static JFrame frame;
    private JPanel jPanel1;
    private JPanel jPanel2;
    private JScrollPane jScrollPane1;
    private JTabbedPane jTabbedPane1;
    private JTree jTree1;
    private DefaultMutableTreeNode defTreeNode;
    private DefaultTreeModel model;

    private static final CountDownLatch testDone = new CountDownLatch(1);

    // Access to referenceList and referenceQueue is guarded by referenceList
    private static final List<Reference<JLabel>> referenceList = new ArrayList<>(50);
    private static final ReferenceQueue<JLabel> referenceQueue = new ReferenceQueue<>();


    // Custom TreeCellRenderer
    public static final class TreeCellRenderer extends DefaultTreeCellRenderer {

        public TreeCellRenderer() {}

        // Create a new JLabel every time
        @Override
        public Component getTreeCellRendererComponent(
                JTree tree,
                Object value,
                boolean sel,
                boolean expanded,
                boolean leaf,
                int row,
                boolean hasFocus) {
            JLabel label = new JLabel();
            label.setText("TreeNode: " + value.toString());
            if (sel) {
                label.setBackground(getBackgroundSelectionColor());
            } else {
                label.setBackground(getBackgroundNonSelectionColor());
            }

            synchronized (referenceList) {
                referenceList.add(new PhantomReference<>(label, referenceQueue));
            }
            return label;
        }
    }

    public TreeCellRendererLeakTest() {
        initComponents();
        jTree1.setCellRenderer(new TreeCellRenderer());
        Thread updateThread = new Thread(this::runChanges);
        updateThread.setDaemon(true);
        updateThread.start();
        Thread infoThread = new Thread(this::runInfo);
        infoThread.setDaemon(true);
        infoThread.start();
    }

    private void initComponents() {
        jTabbedPane1 = new JTabbedPane();
        jPanel1 = new JPanel();
        jScrollPane1 = new JScrollPane();
        jTree1 = new JTree();
        jPanel2 = new JPanel();

        jPanel1.setLayout(new BorderLayout());

        jScrollPane1.setViewportView(jTree1);

        jPanel1.add(jScrollPane1, BorderLayout.CENTER);

        jTabbedPane1.addTab("tab1", jPanel1);

        jPanel2.setLayout(new BorderLayout());

        jTabbedPane1.addTab("tab2", jPanel2);

        jTabbedPane1.setSelectedIndex(1);

        model = (DefaultTreeModel) jTree1.getModel();
        TreeNode root = (TreeNode) model.getRoot();
        defTreeNode = (DefaultMutableTreeNode) model.getChild(root, 0);

        frame = new JFrame();
        frame.getContentPane().add(jTabbedPane1, java.awt.BorderLayout.CENTER);

        frame.setSize(200, 200);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }// </editor-fold>

    public static void main(String[] args) throws Exception {
        try {
            SwingUtilities.invokeAndWait(() -> {
                new TreeCellRendererLeakTest();
            });
            testDone.await();
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    // Periodically cause a nodeChanged() for one of the nodes
    public void runChanges() {
        long count = 0;
        long time = System.currentTimeMillis();
        long tm = System.currentTimeMillis();
        while ((tm - time) < (15 * 1000)) {
            final long currentCount = count;
            try {
                SwingUtilities.invokeAndWait(() -> {
                    defTreeNode.setUserObject("runcount " + currentCount);
                    model.nodeChanged(defTreeNode);
                });
                count++;
                Thread.sleep(1000);
                tm = System.currentTimeMillis();
                System.out.println("time elapsed " + (tm - time)/1000 + " s");
            } catch (InterruptedException ex) {
                break;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        testDone.countDown();
    }

    // Print number of uncollected JLabels
    public void runInfo() {
        final long time = System.currentTimeMillis();
        long removedLabels = 0;
        while ((System.currentTimeMillis() - time) < (15 * 1000)) {
            System.gc();

            int start;
            int removed = 0;
            int left;
            // Remove dead references
            synchronized (referenceList) {
                start = referenceList.size();
                Reference<?> ref;
                while ((ref = referenceQueue.poll()) != null) {
                    referenceList.remove(ref);
                    removed++;
                }
                left = referenceList.size();
            }
            removedLabels += removed;
            System.out.println("Live JLabels: " + start + " - " + removed + " = " + left);
            System.out.println("All time removed: " + removedLabels);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
                break;
            }
        }

        System.out.println("\nCleaned up labels: " + removedLabels);
        if (removedLabels == 0) {
            throw new RuntimeException("TreeCellRenderer component leaked");
        }
    }
}
