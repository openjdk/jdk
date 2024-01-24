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
 * @summary Verifies memory leak in BasicTreeUI TreeCellRenderer 
 * @run main TreeCellRendererLeakTest
 */

import java.awt.Component;
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

public class TreeCellRendererLeakTest {
    
    static long smCount = 0;
    static boolean done;    

    private static JFrame frame;
    private JPanel jPanel1;
    private JPanel jPanel2;
    private JScrollPane jScrollPane1;
    private JTabbedPane jTabbedPane1;
    private JTree jTree1;
    // End of variables declaration
    
    // JLabel with an instance counter
    public class TestLabel extends JLabel {
        public TestLabel() {
            smCount++;
        }
        
        public void finalize( ) {
            smCount--;
        }
    }
    
    // Custom TreeCellRenderer
    public class TreeCellRenderer extends DefaultTreeCellRenderer {
        
        public TreeCellRenderer( ) {
        }
        
        // Create a new JLabel every time
        public Component getTreeCellRendererComponent(
                JTree tree,
                Object value,
                boolean sel,
                boolean expanded,
                boolean leaf,
                int row,
                boolean hasFocus) {
            JLabel label = new TestLabel( );
            label.setText("TreeNode: " + value.toString());
            if (sel) {
                label.setBackground(getBackgroundSelectionColor());
            } else {
                label.setBackground(getBackgroundNonSelectionColor());
            }
            
            return label;
        }
    }
    
    public TreeCellRendererLeakTest() {
        initComponents();
        jTree1.setCellRenderer(new TreeCellRenderer());
        Thread updateThread = new Thread(new Runnable( ) {
            public void run( ) {
                runChanges( );
            }
        });
        updateThread.setDaemon(true);
        updateThread.start();
        Thread infoThread = new Thread(new Runnable( ) {
            public void run( ) {
                runInfo( );
            }
        });
        infoThread.setDaemon(true);
        infoThread.start();
    }
    
    // <editor-fold defaultstate="collapsed" desc=" Generated Code ">
    private void initComponents() {
        jTabbedPane1 = new javax.swing.JTabbedPane();
        jPanel1 = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        jTree1 = new javax.swing.JTree();
        jPanel2 = new javax.swing.JPanel();

        jPanel1.setLayout(new java.awt.BorderLayout());

        jScrollPane1.setViewportView(jTree1);

        jPanel1.add(jScrollPane1, java.awt.BorderLayout.CENTER);

        jTabbedPane1.addTab("tab1", jPanel1);

        jPanel2.setLayout(new java.awt.BorderLayout());

        jTabbedPane1.addTab("tab2", jPanel2);

        jTabbedPane1.setSelectedIndex(1);

        frame = new JFrame();
        frame.getContentPane().add(jTabbedPane1, java.awt.BorderLayout.CENTER);

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }// </editor-fold>
    
    public static void main(String args[]) throws Exception {
        try {
            SwingUtilities.invokeAndWait(() -> {
                TreeCellRendererLeakTest tf = new TreeCellRendererLeakTest();
            });
            while (!done) {
                Thread.sleep(100);
            }
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
        while ((tm - time) < (30 * 1000)) {
            final long currentCount = count;
            try {
                SwingUtilities.invokeAndWait(new Runnable( ) {
                    public void run( ) {
                        DefaultTreeModel model = (DefaultTreeModel) jTree1.getModel();
                        TreeNode root = (TreeNode) model.getRoot();
                        DefaultMutableTreeNode n = (DefaultMutableTreeNode) model.getChild(root, 0);
                        n.setUserObject("runcount " + currentCount);
                        model.nodeChanged(n);
                    }
                });
                count++;
                Thread.sleep(1000);
                tm = System.currentTimeMillis();
                System.out.println("time elapsed " + (tm - time));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        done = true;
    }
    
    // Print number of uncollected TestLabels
    public void runInfo( ) {
        long time = System.currentTimeMillis();
        long initialCnt = smCount;
        while ((System.currentTimeMillis() - time) < (30 * 1000)) {
            System.gc();
            System.err.println("Live TestLabels:" + smCount);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
        if (smCount - initialCnt > 50) {
            throw new RuntimeException("TreeCellRenderer component leaked");
        }
    }
    
}
