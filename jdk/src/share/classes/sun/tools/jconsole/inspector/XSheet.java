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

package sun.tools.jconsole.inspector;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.Enumeration;
import javax.management.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.tree.*;
import sun.tools.jconsole.*;
import sun.tools.jconsole.inspector.XNodeInfo.Type;

import static sun.tools.jconsole.Resources.*;
import static sun.tools.jconsole.Utilities.*;

@SuppressWarnings("serial")
public class XSheet extends JPanel
        implements ActionListener, NotificationListener {

    private JPanel mainPanel;
    private JPanel southPanel;

    // Node being currently displayed
    private DefaultMutableTreeNode node;

    // MBean being currently displayed
    private XMBean mbean;

    // XMBeanAttributes container
    private XMBeanAttributes mbeanAttributes;

    // XMBeanOperations container
    private XMBeanOperations mbeanOperations;

    // XMBeanNotifications container
    private XMBeanNotifications mbeanNotifications;

    // XMBeanInfo container
    private XMBeanInfo mbeanInfo;

    // Refresh JButton (mbean attributes case)
    private JButton refreshButton;

    // Subscribe/Unsubscribe/Clear JButton (mbean notifications case)
    private JButton clearButton, subscribeButton, unsubscribeButton;

    // Reference to MBeans tab
    private MBeansTab mbeansTab;

    public XSheet(MBeansTab mbeansTab) {
        this.mbeansTab = mbeansTab;
        setupScreen();
    }

    public void dispose() {
        clear();
        XDataViewer.dispose(mbeansTab);
        mbeanNotifications.dispose();
    }

    private void setupScreen() {
        setLayout(new BorderLayout());
        // add main panel to XSheet
        mainPanel = new JPanel();
        mainPanel.setLayout(new BorderLayout());
        add(mainPanel, BorderLayout.CENTER);
        // add south panel to XSheet
        southPanel = new JPanel();
        add(southPanel, BorderLayout.SOUTH);
        // create the refresh button
        String refreshButtonKey = "MBeansTab.refreshAttributesButton";
        refreshButton = new JButton(getText(refreshButtonKey));
        refreshButton.setMnemonic(getMnemonicInt(refreshButtonKey));
        refreshButton.setToolTipText(getText(refreshButtonKey + ".toolTip"));
        refreshButton.addActionListener(this);
        // create the clear button
        String clearButtonKey = "MBeansTab.clearNotificationsButton";
        clearButton = new JButton(getText(clearButtonKey));
        clearButton.setMnemonic(getMnemonicInt(clearButtonKey));
        clearButton.setToolTipText(getText(clearButtonKey + ".toolTip"));
        clearButton.addActionListener(this);
        // create the subscribe button
        String subscribeButtonKey = "MBeansTab.subscribeNotificationsButton";
        subscribeButton = new JButton(getText(subscribeButtonKey));
        subscribeButton.setMnemonic(getMnemonicInt(subscribeButtonKey));
        subscribeButton.setToolTipText(getText(subscribeButtonKey + ".toolTip"));
        subscribeButton.addActionListener(this);
        // create the unsubscribe button
        String unsubscribeButtonKey = "MBeansTab.unsubscribeNotificationsButton";
        unsubscribeButton = new JButton(getText(unsubscribeButtonKey));
        unsubscribeButton.setMnemonic(getMnemonicInt(unsubscribeButtonKey));
        unsubscribeButton.setToolTipText(getText(unsubscribeButtonKey + ".toolTip"));
        unsubscribeButton.addActionListener(this);
        // create XMBeanAttributes container
        mbeanAttributes = new XMBeanAttributes(mbeansTab);
        // create XMBeanOperations container
        mbeanOperations = new XMBeanOperations(mbeansTab);
        mbeanOperations.addOperationsListener(this);
        // create XMBeanNotifications container
        mbeanNotifications = new XMBeanNotifications();
        mbeanNotifications.addNotificationsListener(this);
        // create XMBeanInfo container
        mbeanInfo = new XMBeanInfo();
    }

    public boolean isMBeanNode(DefaultMutableTreeNode node) {
        XNodeInfo uo = (XNodeInfo) node.getUserObject();
        return uo.getType().equals(Type.MBEAN);
    }

    public void displayNode(DefaultMutableTreeNode node) {
        clear();
        if (node == null) {
            displayEmptyNode();
            return;
        }
        Object userObject = node.getUserObject();
        if (userObject instanceof XNodeInfo) {
            XNodeInfo uo = (XNodeInfo) userObject;
            switch (uo.getType()) {
                case MBEAN:
                    displayMBeanNode(node);
                    break;
                case NONMBEAN:
                    displayEmptyNode();
                    break;
                case ATTRIBUTES:
                    displayMBeanAttributesNode(node);
                    break;
                case OPERATIONS:
                    displayMBeanOperationsNode(node);
                    break;
                case NOTIFICATIONS:
                    displayMBeanNotificationsNode(node);
                    break;
                case ATTRIBUTE:
                case OPERATION:
                case NOTIFICATION:
                    displayMetadataNode(node);
                    break;
                default:
                    displayEmptyNode();
                    break;
            }
        } else {
            displayEmptyNode();
        }
    }

    private void displayMBeanNode(final DefaultMutableTreeNode node) {
        final XNodeInfo uo = (XNodeInfo) node.getUserObject();
        if (!uo.getType().equals(Type.MBEAN)) {
            return;
        }
        mbeansTab.workerAdd(new Runnable() {
            public void run() {
                try {
                    XSheet.this.node = node;
                    XSheet.this.mbean = (XMBean) uo.getData();
                    mbeanInfo.addMBeanInfo(mbean, mbean.getMBeanInfo());
                } catch (Throwable ex) {
                    EventQueue.invokeLater(new ThreadDialog(
                            XSheet.this,
                            ex.getMessage(),
                            Resources.getText("Problem displaying MBean"),
                            JOptionPane.ERROR_MESSAGE));
                    return;
                }
                EventQueue.invokeLater(new Runnable() {
                    public void run() {
                        invalidate();
                        mainPanel.removeAll();
                        mainPanel.add(mbeanInfo, BorderLayout.CENTER);
                        southPanel.setVisible(false);
                        southPanel.removeAll();
                        validate();
                        repaint();
                    }
                });
            }
        });
    }

    // Call on EDT
    private void displayMetadataNode(final DefaultMutableTreeNode node) {
        final XNodeInfo uo = (XNodeInfo) node.getUserObject();
        final XMBeanInfo mbi = mbeanInfo;
        switch (uo.getType()) {
            case ATTRIBUTE:
                mbeansTab.workerAdd(new Runnable() {
                    public void run() {
                        Object attrData = uo.getData();
                        XSheet.this.mbean = (XMBean) ((Object[]) attrData)[0];
                        final MBeanAttributeInfo mbai =
                                (MBeanAttributeInfo) ((Object[]) attrData)[1];
                        final XMBeanAttributes mba = mbeanAttributes;
                        try {
                            mba.loadAttributes(mbean, new MBeanInfo(
                                    null, null, new MBeanAttributeInfo[] {mbai},
                                    null, null, null));
                        } catch (Exception e) {
                            EventQueue.invokeLater(new ThreadDialog(
                                    XSheet.this,
                                    e.getMessage(),
                                    Resources.getText("Problem displaying MBean"),
                                    JOptionPane.ERROR_MESSAGE));
                            return;
                        }
                        EventQueue.invokeLater(new Runnable() {
                            public void run() {
                                invalidate();
                                mainPanel.removeAll();
                                JPanel attributePanel =
                                        new JPanel(new BorderLayout());
                                JPanel attributeBorderPanel =
                                        new JPanel(new BorderLayout());
                                attributeBorderPanel.setBorder(
                                        BorderFactory.createTitledBorder(
                                        Resources.getText("Attribute value")));
                                JPanel attributeValuePanel =
                                        new JPanel(new BorderLayout());
                                attributeValuePanel.setBorder(
                                        LineBorder.createGrayLineBorder());
                                attributeValuePanel.add(mba.getTableHeader(),
                                        BorderLayout.PAGE_START);
                                attributeValuePanel.add(mba,
                                        BorderLayout.CENTER);
                                attributeBorderPanel.add(attributeValuePanel,
                                        BorderLayout.CENTER);
                                JPanel refreshButtonPanel = new JPanel();
                                refreshButtonPanel.add(refreshButton);
                                attributeBorderPanel.add(refreshButtonPanel,
                                        BorderLayout.SOUTH);
                                refreshButton.setEnabled(true);
                                attributePanel.add(attributeBorderPanel,
                                        BorderLayout.NORTH);
                                mbi.addMBeanAttributeInfo(mbai);
                                attributePanel.add(mbi, BorderLayout.CENTER);
                                mainPanel.add(attributePanel,
                                        BorderLayout.CENTER);
                                southPanel.setVisible(false);
                                southPanel.removeAll();
                                validate();
                                repaint();
                            }
                        });
                    }
                });
                break;
            case OPERATION:
                Object operData = uo.getData();
                XSheet.this.mbean = (XMBean) ((Object[]) operData)[0];
                MBeanOperationInfo mboi =
                        (MBeanOperationInfo) ((Object[]) operData)[1];
                XMBeanOperations mbo = mbeanOperations;
                try {
                    mbo.loadOperations(mbean, new MBeanInfo(null, null, null,
                            null, new MBeanOperationInfo[] {mboi}, null));
                } catch (Exception e) {
                    EventQueue.invokeLater(new ThreadDialog(
                            XSheet.this,
                            e.getMessage(),
                            Resources.getText("Problem displaying MBean"),
                            JOptionPane.ERROR_MESSAGE));
                    return;
                }
                invalidate();
                mainPanel.removeAll();
                JPanel operationPanel = new JPanel(new BorderLayout());
                JPanel operationBorderPanel = new JPanel(new BorderLayout());
                operationBorderPanel.setBorder(BorderFactory.createTitledBorder(
                        Resources.getText("Operation invocation")));
                operationBorderPanel.add(new JScrollPane(mbo));
                operationPanel.add(operationBorderPanel, BorderLayout.NORTH);
                mbi.addMBeanOperationInfo(mboi);
                operationPanel.add(mbi, BorderLayout.CENTER);
                mainPanel.add(operationPanel, BorderLayout.CENTER);
                southPanel.setVisible(false);
                southPanel.removeAll();
                validate();
                repaint();
                break;
            case NOTIFICATION:
                Object notifData = uo.getData();
                invalidate();
                mainPanel.removeAll();
                mbi.addMBeanNotificationInfo((MBeanNotificationInfo) notifData);
                mainPanel.add(mbi, BorderLayout.CENTER);
                southPanel.setVisible(false);
                southPanel.removeAll();
                validate();
                repaint();
                break;
        }
    }

    private void displayMBeanAttributesNode(final DefaultMutableTreeNode node) {
        final XNodeInfo uo = (XNodeInfo) node.getUserObject();
        if (!uo.getType().equals(Type.ATTRIBUTES)) {
            return;
        }
        final XMBeanAttributes mba = mbeanAttributes;
        mbeansTab.workerAdd(new Runnable() {
            public void run() {
                try {
                    XSheet.this.node = node;
                    XSheet.this.mbean = (XMBean) uo.getData();
                    mba.loadAttributes(mbean, mbean.getMBeanInfo());
                } catch (Throwable ex) {
                    EventQueue.invokeLater(new ThreadDialog(
                            XSheet.this,
                            ex.getMessage(),
                            Resources.getText("Problem displaying MBean"),
                            JOptionPane.ERROR_MESSAGE));
                    return;
                }
                EventQueue.invokeLater(new Runnable() {
                    public void run() {
                        invalidate();
                        mainPanel.removeAll();
                        JPanel borderPanel = new JPanel(new BorderLayout());
                        borderPanel.setBorder(BorderFactory.createTitledBorder(
                                Resources.getText("Attribute values")));
                        borderPanel.add(new JScrollPane(mba));
                        mainPanel.add(borderPanel, BorderLayout.CENTER);
                        // add the refresh button to the south panel
                        southPanel.removeAll();
                        southPanel.add(refreshButton, BorderLayout.SOUTH);
                        southPanel.setVisible(true);
                        refreshButton.setEnabled(true);
                        validate();
                        repaint();
                    }
                });
            }
        });
    }

    private void displayMBeanOperationsNode(final DefaultMutableTreeNode node) {
        final XNodeInfo uo = (XNodeInfo) node.getUserObject();
        if (!uo.getType().equals(Type.OPERATIONS)) {
            return;
        }
        final XMBeanOperations mbo = mbeanOperations;
        mbeansTab.workerAdd(new Runnable() {
            public void run() {
                try {
                    XSheet.this.node = node;
                    XSheet.this.mbean = (XMBean) uo.getData();
                    mbo.loadOperations(mbean, mbean.getMBeanInfo());
                } catch (Throwable ex) {
                    EventQueue.invokeLater(new ThreadDialog(
                            XSheet.this,
                            ex.getMessage(),
                            Resources.getText("Problem displaying MBean"),
                            JOptionPane.ERROR_MESSAGE));
                    return;
                }
                EventQueue.invokeLater(new Runnable() {
                    public void run() {
                        invalidate();
                        mainPanel.removeAll();
                        JPanel borderPanel = new JPanel(new BorderLayout());
                        borderPanel.setBorder(BorderFactory.createTitledBorder(
                                Resources.getText("Operation invocation")));
                        borderPanel.add(new JScrollPane(mbo));
                        mainPanel.add(borderPanel, BorderLayout.CENTER);
                        southPanel.setVisible(false);
                        southPanel.removeAll();
                        validate();
                        repaint();
                    }
                });
            }
        });
    }

    private void displayMBeanNotificationsNode(
            final DefaultMutableTreeNode node) {
        final XNodeInfo uo = (XNodeInfo) node.getUserObject();
        if (!uo.getType().equals(Type.NOTIFICATIONS)) {
            return;
        }
        final XMBeanNotifications mbn = mbeanNotifications;
        mbeansTab.workerAdd(new Runnable() {
            public void run() {
                try {
                    XSheet.this.node = node;
                    XSheet.this.mbean = (XMBean) uo.getData();
                    mbn.loadNotifications(mbean);
                    updateNotifications();
                } catch (Throwable ex) {
                    EventQueue.invokeLater(new ThreadDialog(
                            XSheet.this,
                            ex.getMessage(),
                            Resources.getText("Problem displaying MBean"),
                            JOptionPane.ERROR_MESSAGE));
                    return;
                }
                EventQueue.invokeLater(new Runnable() {
                    public void run() {
                        invalidate();
                        mainPanel.removeAll();
                        JPanel borderPanel = new JPanel(new BorderLayout());
                        borderPanel.setBorder(BorderFactory.createTitledBorder(
                                Resources.getText("Notification buffer")));
                        borderPanel.add(new JScrollPane(mbn));
                        mainPanel.add(borderPanel, BorderLayout.CENTER);
                        // add the subscribe/unsubscribe/clear buttons to
                        // the south panel
                        southPanel.removeAll();
                        southPanel.add(subscribeButton, BorderLayout.WEST);
                        southPanel.add(unsubscribeButton, BorderLayout.CENTER);
                        southPanel.add(clearButton, BorderLayout.EAST);
                        southPanel.setVisible(true);
                        subscribeButton.setEnabled(true);
                        unsubscribeButton.setEnabled(true);
                        clearButton.setEnabled(true);
                        validate();
                        repaint();
                    }
                });
            }
        });
    }

    // Call on EDT
    private void displayEmptyNode() {
        invalidate();
        mainPanel.removeAll();
        southPanel.removeAll();
        validate();
        repaint();
    }

    /**
     * Subscribe button action.
     */
    private void registerListener() throws InstanceNotFoundException,
            IOException {
        mbeanNotifications.registerListener(node);
        updateNotifications();
        validate();
    }

    /**
     * Unsubscribe button action.
     */
    private void unregisterListener() {
        if (mbeanNotifications.unregisterListener(node)) {
            clearNotifications();
            validate();
        }
    }

    /**
     * Refresh button action.
     */
    private void refreshAttributes() {
        mbeanAttributes.refreshAttributes();
    }

    private void updateNotifications() {
        if (mbean.isBroadcaster()) {
            if (mbeanNotifications.isListenerRegistered(mbean)) {
                long received =
                        mbeanNotifications.getReceivedNotifications(mbean);
                updateReceivedNotifications(node, received, false);
            } else {
                clearNotifications();
            }
        } else {
            clearNotifications();
        }
    }

    /**
     * Update notification node label in MBean tree: "Notifications[received]".
     */
    private void updateReceivedNotifications(
            DefaultMutableTreeNode emitter, long received, boolean bold) {
        String text = Resources.getText("Notifications") + "[" + received + "]";
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode)
        mbeansTab.getTree().getLastSelectedPathComponent();
        if (bold && emitter != selectedNode) {
            text = "<html><b>" + text + "</b></html>";
        }
        updateNotificationsNodeLabel(emitter, text);
    }

    /**
     * Update notification node label in MBean tree: "Notifications".
     */
    private void clearNotifications() {
        updateNotificationsNodeLabel(node,
                Resources.getText("Notifications"));
    }

    /**
     * Update notification node label in MBean tree: "Notifications[0]".
     */
    private void clearNotifications0() {
        updateNotificationsNodeLabel(node,
                Resources.getText("Notifications") + "[0]");
    }

    /**
     * Update the label of the supplied MBean tree node.
     */
    private void updateNotificationsNodeLabel(
            final DefaultMutableTreeNode node, final String label) {
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                synchronized (mbeansTab.getTree()) {
                    invalidate();
                    XNodeInfo oldUserObject = (XNodeInfo) node.getUserObject();
                    XNodeInfo newUserObject = new XNodeInfo(
                            oldUserObject.getType(), oldUserObject.getData(),
                            label, oldUserObject.getToolTipText());
                    node.setUserObject(newUserObject);
                    DefaultTreeModel model =
                            (DefaultTreeModel) mbeansTab.getTree().getModel();
                    model.nodeChanged(node);
                    validate();
                    repaint();
                }
            }
        });
    }

    /**
     * Clear button action.
     */
    // Call on EDT
    private void clearCurrentNotifications() {
        mbeanNotifications.clearCurrentNotifications();
        if (mbeanNotifications.isListenerRegistered(mbean)) {
            // Update notifs in MBean tree "Notifications[0]".
            //
            // Notification buffer has been cleared with a listener been
            // registered so add "[0]" at the end of the node label.
            //
            clearNotifications0();
        } else {
            // Update notifs in MBean tree "Notifications".
            //
            // Notification buffer has been cleared without a listener been
            // registered so don't add "[0]" at the end of the node label.
            //
            clearNotifications();
        }
    }

    private void clear() {
        mbeanAttributes.stopCellEditing();
        mbeanAttributes.emptyTable();
        mbeanAttributes.removeAttributes();
        mbeanOperations.removeOperations();
        mbeanNotifications.stopCellEditing();
        mbeanNotifications.emptyTable();
        mbeanNotifications.disableNotifications();
        mbean = null;
        node = null;
    }

    /**
     * Notification listener: handles asynchronous reception
     * of MBean operation results and MBean notifications.
     */
    public void handleNotification(Notification e, Object handback) {
        // Operation result
        if (e.getType().equals(XOperations.OPERATION_INVOCATION_EVENT)) {
            final Object message;
            if (handback == null) {
                JTextArea textArea = new JTextArea("null");
                textArea.setEditable(false);
                textArea.setEnabled(true);
                textArea.setRows(textArea.getLineCount());
                message = textArea;
            } else {
                Component comp = mbeansTab.getDataViewer().
                        createOperationViewer(handback, mbean);
                if (comp == null) {
                    JTextArea textArea = new JTextArea(handback.toString());
                    textArea.setEditable(false);
                    textArea.setEnabled(true);
                    textArea.setRows(textArea.getLineCount());
                    JScrollPane scrollPane = new JScrollPane(textArea);
                    Dimension d = scrollPane.getPreferredSize();
                    if (d.getWidth() > 400 || d.getHeight() > 250) {
                        scrollPane.setPreferredSize(new Dimension(400, 250));
                    }
                    message = scrollPane;
                } else {
                    if (!(comp instanceof JScrollPane)) {
                        comp = new JScrollPane(comp);
                    }
                    Dimension d = comp.getPreferredSize();
                    if (d.getWidth() > 400 || d.getHeight() > 250) {
                        comp.setPreferredSize(new Dimension(400, 250));
                    }
                    message = comp;
                }
            }
            EventQueue.invokeLater(new ThreadDialog(
                    (Component) e.getSource(),
                    message,
                    Resources.getText("Operation return value"),
                    JOptionPane.INFORMATION_MESSAGE));
        }
        // Got notification
        else if (e.getType().equals(
                XMBeanNotifications.NOTIFICATION_RECEIVED_EVENT)) {
            DefaultMutableTreeNode emitter = (DefaultMutableTreeNode) handback;
            Long received = (Long) e.getUserData();
            updateReceivedNotifications(emitter, received.longValue(), true);
        }
    }

    /**
     * Action listener: handles actions in panel buttons
     */
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() instanceof JButton) {
            JButton button = (JButton) e.getSource();
            // Refresh button
            if (button == refreshButton) {
                mbeansTab.workerAdd(new Runnable() {
                    public void run() {
                        refreshAttributes();
                    }
                });
                return;
            }
            // Clear button
            if (button == clearButton) {
                clearCurrentNotifications();
                return;
            }
            // Subscribe button
            if (button == subscribeButton) {
                mbeansTab.workerAdd(new Runnable() {
                    public void run() {
                        try {
                            registerListener();
                        } catch (Throwable ex) {
                            ex = Utils.getActualException(ex);
                            EventQueue.invokeLater(new ThreadDialog(
                                    XSheet.this,
                                    ex.getMessage(),
                                    Resources.getText("Problem adding listener"),
                                    JOptionPane.ERROR_MESSAGE));
                        }
                    }
                });
                return;
            }
            // Unsubscribe button
            if (button == unsubscribeButton) {
                mbeansTab.workerAdd(new Runnable() {
                    public void run() {
                        try {
                            unregisterListener();
                        } catch (Throwable ex) {
                            ex = Utils.getActualException(ex);
                            EventQueue.invokeLater(new ThreadDialog(
                                    XSheet.this,
                                    ex.getMessage(),
                                    Resources.getText("Problem removing listener"),
                                    JOptionPane.ERROR_MESSAGE));
                        }
                    }
                });
                return;
            }
        }
    }
}
