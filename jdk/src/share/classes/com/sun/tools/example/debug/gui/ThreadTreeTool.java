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
import java.util.List;  // Must import explicitly due to conflict with javax.awt.List

import javax.swing.*;
import javax.swing.tree.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;

import com.sun.jdi.*;
import com.sun.tools.example.debug.event.*;
import com.sun.tools.example.debug.bdi.*;

//### Bug: If the name of a thread is changed via Thread.setName(), the
//### thread tree view does not reflect this.  The name of the thread at
//### the time it is created is used throughout its lifetime.

public class ThreadTreeTool extends JPanel {

    private Environment env;

    private ExecutionManager runtime;
    private SourceManager sourceManager;
    private ClassManager classManager;

    private JTree tree;
    private DefaultTreeModel treeModel;
    private ThreadTreeNode root;
    private SearchPath sourcePath;

    private CommandInterpreter interpreter;

    private static String HEADING = "THREADS";

    public ThreadTreeTool(Environment env) {

        super(new BorderLayout());

        this.env = env;
        this.runtime = env.getExecutionManager();
        this.sourceManager = env.getSourceManager();

        this.interpreter = new CommandInterpreter(env);

        root = createThreadTree(HEADING);
        treeModel = new DefaultTreeModel(root);

        // Create a tree that allows one selection at a time.

        tree = new JTree(treeModel);
        tree.setSelectionModel(new SingleLeafTreeSelectionModel());

        MouseListener ml = new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                int selRow = tree.getRowForLocation(e.getX(), e.getY());
                TreePath selPath = tree.getPathForLocation(e.getX(), e.getY());
                if(selRow != -1) {
                    if(e.getClickCount() == 1) {
                        ThreadTreeNode node =
                            (ThreadTreeNode)selPath.getLastPathComponent();
                        // If user clicks on leaf, select it, and issue 'thread' command.
                        if (node.isLeaf()) {
                            tree.setSelectionPath(selPath);
                            interpreter.executeCommand("thread " +
                                                       node.getThreadId() +
                                                       "  (\"" +
                                                       node.getName() + "\")");
                        }
                    }
                }
            }
        };

        tree.addMouseListener(ml);

        JScrollPane treeView = new JScrollPane(tree);
        add(treeView);

        // Create listener.
        ThreadTreeToolListener listener = new ThreadTreeToolListener();
        runtime.addJDIListener(listener);
        runtime.addSessionListener(listener);

        //### remove listeners on exit!
    }

    HashMap<ThreadReference, List<String>> threadTable = new HashMap<ThreadReference, List<String>>();

    private List<String> threadPath(ThreadReference thread) {
        // May exit abnormally if VM disconnects.
        List<String> l = new ArrayList<String>();
        l.add(0, thread.name());
        ThreadGroupReference group = thread.threadGroup();
        while (group != null) {
            l.add(0, group.name());
            group = group.parent();
        }
        return l;
    }

    private class ThreadTreeToolListener extends JDIAdapter
                              implements JDIListener, SessionListener {

        // SessionListener

        public void sessionStart(EventObject e) {
            try {
                for (ThreadReference thread : runtime.allThreads()) {
                    root.addThread(thread);
                }
            } catch (VMDisconnectedException ee) {
                // VM went away unexpectedly.
            } catch (NoSessionException ee) {
                // Ignore.  Should not happen.
            }
        }

        public void sessionInterrupt(EventObject e) {}
        public void sessionContinue(EventObject e) {}


        // JDIListener

        public void threadStart(ThreadStartEventSet e) {
            root.addThread(e.getThread());
        }

        public void threadDeath(ThreadDeathEventSet e) {
            root.removeThread(e.getThread());
        }

        public void vmDisconnect(VMDisconnectEventSet e) {
            // Clear the contents of this view.
            root = createThreadTree(HEADING);
            treeModel = new DefaultTreeModel(root);
            tree.setModel(treeModel);
            threadTable = new HashMap<ThreadReference, List<String>>();
        }

    }

    ThreadTreeNode createThreadTree(String label) {
        return new ThreadTreeNode(label, null);
    }

    class ThreadTreeNode extends DefaultMutableTreeNode {

        String name;
        ThreadReference thread; // null if thread group
        long uid;
        String description;

        ThreadTreeNode(String name, ThreadReference thread) {
            if (name == null) {
                name = "<unnamed>";
            }
            this.name = name;
            this.thread = thread;
            if (thread == null) {
                this.uid = -1;
                this.description = name;
            } else {
                this.uid = thread.uniqueID();
                this.description = name + " (t@" + Long.toHexString(uid) + ")";
            }
        }

        public String toString() {
            return description;
        }

        public String getName() {
            return name;
        }

        public ThreadReference getThread() {
            return thread;
        }

        public String getThreadId() {
            return "t@" + Long.toHexString(uid);
        }

        private boolean isThreadGroup() {
            return (thread == null);
        }

        public boolean isLeaf() {
            return !isThreadGroup();
        }

        public void addThread(ThreadReference thread) {
            // This can fail if the VM disconnects.
            // It is important to do all necessary JDI calls
            // before modifying the tree, so we don't abort
            // midway through!
            if (threadTable.get(thread) == null) {
                // Add thread only if not already present.
                try {
                    List<String> path = threadPath(thread);
                    // May not get here due to exception.
                    // If we get here, we are committed.
                    // We must not leave the tree partially updated.
                    try {
                        threadTable.put(thread, path);
                        addThread(path, thread);
                    } catch (Throwable tt) {
                        //### Assertion failure.
                        throw new RuntimeException("ThreadTree corrupted");
                    }
                } catch (VMDisconnectedException ee) {
                    // Ignore.  Thread will not be added.
                }
            }
        }

        private void addThread(List<String> threadPath, ThreadReference thread) {
            int size = threadPath.size();
            if (size == 0) {
                return;
            } else if (size == 1) {
                String name = threadPath.get(0);
                insertNode(name, thread);
            } else {
                String head = threadPath.get(0);
                List<String> tail = threadPath.subList(1, size);
                ThreadTreeNode child = insertNode(head, null);
                child.addThread(tail, thread);
            }
        }

        private ThreadTreeNode insertNode(String name, ThreadReference thread) {
            for (int i = 0; i < getChildCount(); i++) {
                ThreadTreeNode child = (ThreadTreeNode)getChildAt(i);
                int cmp = name.compareTo(child.getName());
                if (cmp == 0 && thread == null) {
                    // A like-named interior node already exists.
                    return child;
                } else if (cmp < 0) {
                    // Insert new node before the child.
                    ThreadTreeNode newChild = new ThreadTreeNode(name, thread);
                    treeModel.insertNodeInto(newChild, this, i);
                    return newChild;
                }
            }
            // Insert new node after last child.
            ThreadTreeNode newChild = new ThreadTreeNode(name, thread);
            treeModel.insertNodeInto(newChild, this, getChildCount());
            return newChild;
        }

        public void removeThread(ThreadReference thread) {
            List<String> threadPath = threadTable.get(thread);
            // Only remove thread if we recorded it in table.
            // Original add may have failed due to VM disconnect.
            if (threadPath != null) {
                removeThread(threadPath, thread);
            }
        }

        private void removeThread(List<String> threadPath, ThreadReference thread) {
            int size = threadPath.size();
            if (size == 0) {
                return;
            } else if (size == 1) {
                String name = threadPath.get(0);
                ThreadTreeNode child = findLeafNode(thread, name);
                treeModel.removeNodeFromParent(child);
            } else {
                String head = threadPath.get(0);
                List<String> tail = threadPath.subList(1, size);
                ThreadTreeNode child = findInternalNode(head);
                child.removeThread(tail, thread);
                if (child.isThreadGroup() && child.getChildCount() < 1) {
                    // Prune non-leaf nodes with no children.
                    treeModel.removeNodeFromParent(child);
                }
            }
        }

        private ThreadTreeNode findLeafNode(ThreadReference thread, String name) {
            for (int i = 0; i < getChildCount(); i++) {
                ThreadTreeNode child = (ThreadTreeNode)getChildAt(i);
                if (child.getThread() == thread) {
                    if (!name.equals(child.getName())) {
                        //### Assertion failure.
                        throw new RuntimeException("name mismatch");
                    }
                    return child;
                }
            }
            //### Assertion failure.
            throw new RuntimeException("not found");
        }

        private ThreadTreeNode findInternalNode(String name) {
            for (int i = 0; i < getChildCount(); i++) {
                ThreadTreeNode child = (ThreadTreeNode)getChildAt(i);
                if (name.equals(child.getName())) {
                    return child;
                }
            }
            //### Assertion failure.
            throw new RuntimeException("not found");
        }

    }

}
