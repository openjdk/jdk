/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import java.util.Enumeration;
import java.util.Vector;
import java.awt.BorderLayout;
import java.awt.Graphics;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.DefaultTreeSelectionModel;
import javax.swing.tree.TreeSelectionModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import javax.swing.SwingUtilities;

/**
  * This test is mean to isolate the speed of the JTree.
  * It creates a JTree and performs the following scenarios :
  * 1) Recursively adding node to the tree
  * 2) Recursively expand all nodes
  * 3) Selection Test
  *    -SINGLE_TREE_SELECTION mode
  *    -CONTIGUOUS_TREE_SELECTION mode
  *    -DISCONTIGUOUS_TREE_SELECTION mode
  * 4) Collapse all nodes
  * 5) Recursively Remove all the nodes
  */

public class TreeTest extends AbstractSwingTest {
   JTree tree;
   int totalChildCount = 0;
   int targetChildCount = 200;
   int interval = 5;
   boolean useLargeModel = false;
   boolean debug = false;

   public JComponent getTestComponent() {
      JPanel panel = new JPanel();
      panel.setLayout(new BorderLayout());

      DefaultMutableTreeNode top =
      new DefaultMutableTreeNode(Integer.valueOf(0));
      totalChildCount ++;

      DefaultTreeModel model = new DefaultTreeModel(top);

      tree = new CountTree(model);
      tree.setLargeModel(useLargeModel);
      if (useLargeModel) {
         tree.setRowHeight(18);
      }
      JScrollPane scroller = new JScrollPane(tree);

      if (SwingMark.useBlitScrolling) {
         scroller.getViewport().putClientProperty("EnableWindowBlit", Boolean.TRUE);
      }

      panel.add(scroller, BorderLayout.CENTER);

      return panel;
   }

   public String getTestName() {
      return "Tree";
   }

   public void runTest() {
      testTree();
   }

   public void testTree() {
      // Recursively add Nodes to the tree
      if (debug) {
         System.out.println("(1)Adding nodes...");
      }

      TreeNodeAdder adder =
      new TreeNodeAdder( (DefaultTreeModel)tree.getModel(), true );
      Vector nodeList = new Vector();
      nodeList.addElement(tree.getModel().getRoot());

      addChild(nodeList, adder);

      // Recursively Expend all nodes
      if (debug) {
         System.out.println("(2)Recursively Expending all nodes...");
      }

      TreeExpender expender = new TreeExpender(tree, true);
      TreePath path = tree.getPathForRow(0);
      DefaultMutableTreeNode root =
      (DefaultMutableTreeNode)path.getLastPathComponent();
      expandNodes(root, expender);

      // Selection Test

      // 1) SINGLE_TREE_SELECTION
      if (debug) {
         System.out.println("(3)Selection Test .....");
         System.out.println("   -SINGLE_TREE_SELECTION .....");

      }
      TreeSelector selector =
      new TreeSelector(tree,TreeSelectionModel.SINGLE_TREE_SELECTION);
      int [] rows = new int[1];

      for (int i=0; i < tree.getRowCount() ; i ++ ) {
         rows[0] = i;
         selector.addSelectionRows(rows);
         try {
            SwingUtilities.invokeLater(selector);
            rest();
         } catch (Exception e) {System.out.println(e);}
      }


      // 2) CONTIGUOUS_TREE_SELECTION
      if (debug) {
         System.out.println("   -CONTIGUOUS_TREE_SELECTION .....");
      }

      selector =
      new TreeSelector(tree,TreeSelectionModel.CONTIGUOUS_TREE_SELECTION);
      rows = new int[3];
      int count = tree.getRowCount()/4 ;

      for (int i=0; i < count ;i ++) {
         rows[0]=i*4; rows[1] = rows[0]+1; rows[2] = rows[0]+2;
         selector.addSelectionRows(rows);
         try {
            SwingUtilities.invokeAndWait(selector);
         } catch (Exception e) {System.out.println(e);}
      }

      // 3) CONTIGUOUS_TREE_SELECTION
      if (debug) {
         System.out.println("   -DISCONTIGUOUS_TREE_SELECTION .....");
      }
      new TreeSelector(tree,TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
      count = tree.getRowCount()/5 ;

      for (int i=0; i < count ; i ++) {
         rows[0]=i*5; rows[1] = rows[0]+1; rows[2] = rows[0]+2;
         selector.addSelectionRows(rows);
         try {
            SwingUtilities.invokeAndWait(selector);
         } catch (Exception e) {System.out.println(e);}
      }

      //
      // Collapse all the nodes
      //
      if (debug) {
         System.out.println("(4)Collapsing all nodes .....");

      }
      TreeExpender collapser = new TreeExpender(tree, false);
      TreePath[] paths = new TreePath[ tree.getRowCount()];

      for ( int rowcount = 0 ; rowcount < tree.getRowCount() ; rowcount ++ ) {
         paths[rowcount] = tree.getPathForRow(rowcount );
      }

      for ( int i = paths.length - 1 ; i >=0 ; i-- ) {
         try {
            collapser.setPath(paths[i]);
            SwingUtilities.invokeAndWait(collapser);
         } catch (Exception e) {System.out.println(e);}
      }

      // Recursively remove Nodes from the tree
      //
      if (debug) {
         System.out.println("(5)Removing nodes...");
      }

      TreeNodeAdder remover =
      new TreeNodeAdder( (DefaultTreeModel)tree.getModel(), false);

      removeNodes((DefaultMutableTreeNode)tree.getModel().getRoot(),remover);
      tree.repaint();
   }


   public void addChild(Vector nodeList, TreeNodeAdder adder){
      DefaultMutableTreeNode node;
      Vector newVec = new Vector();

      for ( int i=0; i < nodeList.size() ; i++ ) {

         node = (DefaultMutableTreeNode)nodeList.elementAt(i);

         while ( node.getChildCount() < interval ) {
            if ( totalChildCount >= targetChildCount ) return;

            adder.setNode(node, totalChildCount);
            try {
               SwingUtilities.invokeAndWait(adder);
               totalChildCount ++;
            } catch (Exception e) {System.out.println(e);}

            newVec.addElement(node.getChildAt(node.getChildCount()-1));
         }
      }
      addChild(newVec, adder);
   }

   public void expandNodes(DefaultMutableTreeNode node, TreeExpender expender){
      try {
         expender.setPath(new TreePath ( node.getPath()));
         SwingUtilities.invokeAndWait(expender);
      } catch (Exception e) {System.out.println(e);}

      for (Enumeration e = node.children() ; e.hasMoreElements() ;) {
         DefaultMutableTreeNode childNode =
         (DefaultMutableTreeNode)e.nextElement();
         expandNodes(childNode, expender);
      }
   }

   public void removeNodes(DefaultMutableTreeNode node, TreeNodeAdder remover){
      Vector nodeList = new Vector();
      for (Enumeration e = node.depthFirstEnumeration() ; e.hasMoreElements() ;) {
         nodeList.addElement(e.nextElement());
      }

      for ( int i=0; i < nodeList.size(); i ++ ) {
         DefaultMutableTreeNode nodeToRemove =
         (DefaultMutableTreeNode)nodeList.elementAt(i);

         try {
            remover.setNode(nodeToRemove, -1);
            SwingUtilities.invokeAndWait(remover);
         } catch (Exception exp) {System.out.println(exp);
         }
      }
   }

   public static void main(String[] args) {
      TreeTest test = new TreeTest();
      test.debug = true;
      if (args.length > 0) {
         test.targetChildCount = Integer.parseInt(args[0]);
         System.out.println("Setting nodes to: " + test.targetChildCount);
      }
      if (args.length > 1) {
         if (args[1].equals("-l")) {
            System.out.println("Large Model On");
            test.useLargeModel = true;
         }
      }
      runStandAloneTest(test);
   }

   class CountTree extends JTree {
      public CountTree(TreeModel tm) {
         super(tm);
      }
      public void paint(Graphics g) {
         super.paint(g);
         paintCount++;
      }
   }
}

class TreeNodeAdder implements Runnable {
   DefaultTreeModel treeModel;
   DefaultMutableTreeNode currentNode;
   int totalChildCount = 0;
   boolean add = true;

   public TreeNodeAdder(DefaultTreeModel treeModel, boolean add ) {
      this.treeModel = treeModel;
      this.add = add;
   }

   public void setNode(DefaultMutableTreeNode node, int totalCount){
      currentNode = node;
      totalChildCount = totalCount;
   }

   public void run() {
      if ( add ) {
         // add a new node to the currentNode's child list
         DefaultMutableTreeNode newNode =
         new DefaultMutableTreeNode(Integer.valueOf(totalChildCount));
         treeModel.insertNodeInto(newNode, currentNode, currentNode.getChildCount());
      } else {
         // remove the current Node from its parent
         if ( currentNode.getParent() != null ) {
            treeModel.removeNodeFromParent(currentNode);
         }
      }
   }
}

class TreeExpender implements Runnable {
   JTree tree;
   boolean expand = true;
   TreePath currentPath;

   public TreeExpender(JTree tree, boolean expand ) {
      this.tree = tree;
      this.expand = expand;
   }

   public void setPath(TreePath path){
      currentPath = path;
   }

   public void run() {
      if ( expand ) {
         // Expand the current Path
         if ( tree.isExpanded(currentPath)) tree.expandPath(currentPath);
         tree.scrollPathToVisible(currentPath);
      } else {
         // Collapse the node
         if ( !tree.isCollapsed(currentPath) ) {
            tree.scrollPathToVisible(currentPath);
            tree.collapsePath(currentPath);
         }
      }
   }
}

class TreeSelector implements Runnable {
   JTree tree;
   DefaultMutableTreeNode currentNode ;
   TreePath currentPath;
   int selectionMode = 0;
   int [] rows;

   public TreeSelector(JTree tree, int mode ) {
      this.tree = tree;

      selectionMode = mode;
      DefaultTreeSelectionModel selectionModel = new DefaultTreeSelectionModel();
      selectionModel.setSelectionMode(mode);
      tree.setSelectionModel( selectionModel );
      int [] rows;
   }

   public void addSelectionRows( int[] rows ){
      this.rows = rows;
   }

   public void setNode(TreePath path ) {
      currentPath = path;
      currentNode = (DefaultMutableTreeNode)path.getLastPathComponent();
   }

   public void run() {
      tree.addSelectionRows(rows);
      tree.scrollRowToVisible( rows[ rows.length -1 ]);
   }
}
