/*
 * Copyright 2004-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.tools.jconsole;

import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.beans.*;
import java.io.*;
import java.util.Set;
import javax.management.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.tree.*;
import sun.tools.jconsole.inspector.*;

import com.sun.tools.jconsole.JConsoleContext;

@SuppressWarnings("serial")
public class MBeansTab extends Tab implements
        NotificationListener, PropertyChangeListener, TreeSelectionListener {

    private XTree tree;
    private XSheet sheet;
    private XDataViewer viewer;

    public static String getTabName() {
        return Resources.getText("MBeans");
    }

    public MBeansTab(final VMPanel vmPanel) {
        super(vmPanel, getTabName());
        addPropertyChangeListener(this);
        setupTab();
    }

    public XDataViewer getDataViewer() {
        return viewer;
    }

    public XTree getTree() {
        return tree;
    }

    public XSheet getSheet() {
        return sheet;
    }

    public void dispose() {
        super.dispose();
        sheet.dispose();
    }

    public int getUpdateInterval() {
        return vmPanel.getUpdateInterval();
    }

    void synchroniseMBeanServerView() {
        // Register listener for MBean registration/unregistration
        //
        try {
            getMBeanServerConnection().addNotificationListener(
                    MBeanServerDelegate.DELEGATE_NAME,
                    this,
                    null,
                    null);
        } catch (InstanceNotFoundException e) {
            // Should never happen because the MBeanServerDelegate
            // is always present in any standard MBeanServer
            //
            if (JConsole.isDebug()) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            if (JConsole.isDebug()) {
                e.printStackTrace();
            }
            vmPanel.getProxyClient().markAsDead();
            return;
        }
        // Retrieve MBeans from MBeanServer
        //
        Set<ObjectName> newSet = null;
        try {
            newSet = getMBeanServerConnection().queryNames(null,null);
        } catch (IOException e) {
            if (JConsole.isDebug()) {
                e.printStackTrace();
            }
            vmPanel.getProxyClient().markAsDead();
            return;
        }
        // Cleanup current tree
        //
        tree.removeAll();
        // Do not display anything until the new tree has been built
        //
        tree.setVisible(false);
        // Add MBeans to tree
        //
        for (ObjectName mbean : newSet) {
            tree.addMBeanToView(mbean);
        }
        // Display the new tree
        //
        tree.setVisible(true);
    }

    public MBeanServerConnection getMBeanServerConnection() {
        return vmPanel.getProxyClient().getMBeanServerConnection();
    }

    public void update() {
        // Ping the connection to see if it is still alive. At
        // some point the ProxyClient class should centralize
        // the connection aliveness monitoring and no longer
        // rely on the custom tabs to ping the connections.
        //
        try {
            getMBeanServerConnection().getDefaultDomain();
        } catch (IOException ex) {
            vmPanel.getProxyClient().markAsDead();
        }
    }

    private void setupTab() {
        // set up the split pane with the MBean tree and MBean sheet panels
        setLayout(new BorderLayout());
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplit.setDividerLocation(160);
        mainSplit.setBorder(BorderFactory.createEmptyBorder());

        // set up the MBean tree panel (left pane)
        tree = new XTree(this);
        tree.setCellRenderer(new XTreeRenderer());
        tree.getSelectionModel().setSelectionMode(
                TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.addTreeSelectionListener(this);
        tree.addMouseListener(ml);
        JScrollPane theScrollPane = new JScrollPane(
                tree,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        JPanel treePanel = new JPanel(new BorderLayout());
        treePanel.add(theScrollPane, BorderLayout.CENTER);
        mainSplit.add(treePanel, JSplitPane.LEFT, 0);

        // set up the MBean sheet panel (right pane)
        viewer = new XDataViewer(this);
        sheet = new XSheet(this);
        mainSplit.add(sheet, JSplitPane.RIGHT, 0);

        add(mainSplit);
    }

    /* notification listener */
    public void handleNotification(Notification notification, Object handback) {
        if (notification instanceof MBeanServerNotification) {
            ObjectName mbean =
                    ((MBeanServerNotification) notification).getMBeanName();
            if (notification.getType().equals(
                    MBeanServerNotification.REGISTRATION_NOTIFICATION)) {
                tree.addMBeanToView(mbean);
            } else if (notification.getType().equals(
                    MBeanServerNotification.UNREGISTRATION_NOTIFICATION)) {
                tree.delMBeanFromView(mbean);
            }
        }
    }

    /* property change listener */
    public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getPropertyName() == JConsoleContext.CONNECTION_STATE_PROPERTY) {
            boolean connected = (Boolean) evt.getNewValue();
            if (connected) {
                workerAdd(new Runnable() {
                    public void run() {
                        synchroniseMBeanServerView();
                    }
                });
            } else {
                sheet.dispose();
            }
        }

    }

    /* tree selection listener */
    public void valueChanged(TreeSelectionEvent e) {
        DefaultMutableTreeNode node =
                (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        sheet.displayNode(node);
    }

    /* tree mouse listener */
    private MouseListener ml = new MouseAdapter() {
        public void mousePressed(MouseEvent e) {
            if (e.getClickCount() == 1) {
                int selRow = tree.getRowForLocation(e.getX(), e.getY());
                if (selRow != -1) {
                    TreePath selPath =
                            tree.getPathForLocation(e.getX(), e.getY());
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode)
                            selPath.getLastPathComponent();
                    if (sheet.isMBeanNode(node)) {
                        tree.expandPath(selPath);
                    }
                }
            }
        }
    };
}
