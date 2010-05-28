/*
 * Copyright (c) 1998, 2008, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package com.sun.tools.example.debug.gui;

import java.io.*;
import java.util.*;

import javax.swing.*;
import javax.swing.tree.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;

import com.sun.jdi.*;
import com.sun.tools.example.debug.event.*;
import com.sun.tools.example.debug.bdi.*;

public class ClassTreeTool extends JPanel {

    private Environment env;

    private ExecutionManager runtime;
    private SourceManager sourceManager;
    private ClassManager classManager;

    private JTree tree;
    private DefaultTreeModel treeModel;
    private ClassTreeNode root;
    private SearchPath sourcePath;

    private CommandInterpreter interpreter;

    private static String HEADING = "CLASSES";

    public ClassTreeTool(Environment env) {

        super(new BorderLayout());

        this.env = env;
        this.runtime = env.getExecutionManager();
        this.sourceManager = env.getSourceManager();

        this.interpreter = new CommandInterpreter(env);

        root = createClassTree(HEADING);
        treeModel = new DefaultTreeModel(root);

        // Create a tree that allows one selection at a time.

        tree = new JTree(treeModel);
        tree.setSelectionModel(new SingleLeafTreeSelectionModel());

        /******
        // Listen for when the selection changes.
        tree.addTreeSelectionListener(new TreeSelectionListener() {
            public void valueChanged(TreeSelectionEvent e) {
                ClassTreeNode node = (ClassTreeNode)
                    (e.getPath().getLastPathComponent());
                if (node != null) {
                    interpreter.executeCommand("view " + node.getReferenceTypeName());
                }
            }
        });
        ******/

        MouseListener ml = new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                int selRow = tree.getRowForLocation(e.getX(), e.getY());
                TreePath selPath = tree.getPathForLocation(e.getX(), e.getY());
                if(selRow != -1) {
                    if(e.getClickCount() == 1) {
                        ClassTreeNode node =
                            (ClassTreeNode)selPath.getLastPathComponent();
                        // If user clicks on leaf, select it, and issue 'view' command.
                        if (node.isLeaf()) {
                            tree.setSelectionPath(selPath);
                            interpreter.executeCommand("view " + node.getReferenceTypeName());
                        }
                    }
                }
            }
        };
        tree.addMouseListener(ml);

        JScrollPane treeView = new JScrollPane(tree);
        add(treeView);

        // Create listener.
        ClassTreeToolListener listener = new ClassTreeToolListener();
        runtime.addJDIListener(listener);
        runtime.addSessionListener(listener);

        //### remove listeners on exit!
    }

    private class ClassTreeToolListener extends JDIAdapter
                       implements JDIListener, SessionListener {

        // SessionListener

        public void sessionStart(EventObject e) {
            // Get system classes and any others loaded before attaching.
            try {
                for (ReferenceType type : runtime.allClasses()) {
                    root.addClass(type);
                }
            } catch (VMDisconnectedException ee) {
                // VM terminated unexpectedly.
            } catch (NoSessionException ee) {
                // Ignore.  Should not happen.
            }
        }

        public void sessionInterrupt(EventObject e) {}
        public void sessionContinue(EventObject e) {}

        // JDIListener

        public void classPrepare(ClassPrepareEventSet e) {
            root.addClass(e.getReferenceType());
        }

        public void classUnload(ClassUnloadEventSet e) {
            root.removeClass(e.getClassName());
        }

        public void vmDisconnect(VMDisconnectEventSet e) {
            // Clear contents of this view.
            root = createClassTree(HEADING);
            treeModel = new DefaultTreeModel(root);
            tree.setModel(treeModel);
        }
    }

    ClassTreeNode createClassTree(String label) {
        return new ClassTreeNode(label, null);
    }

    class ClassTreeNode extends DefaultMutableTreeNode {

        private String name;
        private ReferenceType refTy;  // null for package

        ClassTreeNode(String name, ReferenceType refTy) {
            this.name = name;
            this.refTy = refTy;
        }

        public String toString() {
            return name;
        }

        public ReferenceType getReferenceType() {
            return refTy;
        }

        public String getReferenceTypeName() {
            return refTy.name();
        }

        private boolean isPackage() {
            return (refTy == null);
        }

        public boolean isLeaf() {
            return !isPackage();
        }

        public void addClass(ReferenceType refTy) {
            addClass(refTy.name(), refTy);
        }

        private void addClass(String className, ReferenceType refTy) {
            if (className.equals("")) {
                return;
            }
            int pos = className.indexOf('.');
            if (pos < 0) {
                insertNode(className, refTy);
            } else {
                String head = className.substring(0, pos);
                String tail = className.substring(pos + 1);
                ClassTreeNode child = insertNode(head, null);
                child.addClass(tail, refTy);
            }
        }

        private ClassTreeNode insertNode(String name, ReferenceType refTy) {
            for (int i = 0; i < getChildCount(); i++) {
                ClassTreeNode child = (ClassTreeNode)getChildAt(i);
                int cmp = name.compareTo(child.toString());
                if (cmp == 0) {
                    // like-named node already exists
                    return child;
                } else if (cmp < 0) {
                    // insert new node before the child
                    ClassTreeNode newChild = new ClassTreeNode(name, refTy);
                    treeModel.insertNodeInto(newChild, this, i);
                    return newChild;
                }
            }
            // insert new node after last child
            ClassTreeNode newChild = new ClassTreeNode(name, refTy);
            treeModel.insertNodeInto(newChild, this, getChildCount());
            return newChild;
        }

        public void removeClass(String className) {
            if (className.equals("")) {
                return;
            }
            int pos = className.indexOf('.');
            if (pos < 0) {
                ClassTreeNode child = findNode(className);
                if (!isPackage()) {
                    treeModel.removeNodeFromParent(child);
                }
            } else {
                String head = className.substring(0, pos);
                String tail = className.substring(pos + 1);
                ClassTreeNode child = findNode(head);
                child.removeClass(tail);
                if (isPackage() && child.getChildCount() < 1) {
                    // Prune non-leaf nodes with no children.
                    treeModel.removeNodeFromParent(child);
                }
            }
        }

        private ClassTreeNode findNode(String name) {
            for (int i = 0; i < getChildCount(); i++) {
                ClassTreeNode child = (ClassTreeNode)getChildAt(i);
                int cmp = name.compareTo(child.toString());
                if (cmp == 0) {
                    return child;
                } else if (cmp > 0) {
                    // not found, since children are sorted
                    return null;
                }
            }
            return null;
        }

    }

}
