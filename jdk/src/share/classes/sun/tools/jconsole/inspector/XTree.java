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

import java.awt.EventQueue;
import java.util.*;
import javax.management.*;
import javax.swing.*;
import javax.swing.tree.*;
import sun.tools.jconsole.JConsole;
import sun.tools.jconsole.MBeansTab;
import sun.tools.jconsole.Resources;
import sun.tools.jconsole.inspector.XNodeInfo;
import sun.tools.jconsole.inspector.XNodeInfo.Type;

@SuppressWarnings("serial")
public class XTree extends JTree {

    private static final List<String> orderedKeyPropertyList =
            new ArrayList<String>();
    static {
        String keyPropertyList =
                System.getProperty("com.sun.tools.jconsole.mbeans.keyPropertyList");
        if (keyPropertyList == null) {
            orderedKeyPropertyList.add("type");
            orderedKeyPropertyList.add("j2eeType");
        } else {
            StringTokenizer st = new StringTokenizer(keyPropertyList, ",");
            while (st.hasMoreTokens()) {
                orderedKeyPropertyList.add(st.nextToken());
            }
        }
    }

    private MBeansTab mbeansTab;

    private Map<String, DefaultMutableTreeNode> nodes =
            new HashMap<String, DefaultMutableTreeNode>();

    public XTree(MBeansTab mbeansTab) {
        this(new DefaultMutableTreeNode("MBeanTreeRootNode"), mbeansTab);
    }

    public XTree(TreeNode root, MBeansTab mbeansTab) {
        super(root);
        this.mbeansTab = mbeansTab;
        setRootVisible(false);
        setShowsRootHandles(true);
        ToolTipManager.sharedInstance().registerComponent(this);
    }

    /**
     * This method removes the node from its parent
     */
    // Call on EDT
    private synchronized void removeChildNode(DefaultMutableTreeNode child) {
        DefaultTreeModel model = (DefaultTreeModel) getModel();
        model.removeNodeFromParent(child);
    }

    /**
     * This method adds the child to the specified parent node
     * at specific index.
     */
    // Call on EDT
    private synchronized void addChildNode(
            DefaultMutableTreeNode parent,
            DefaultMutableTreeNode child,
            int index) {
        // Tree does not show up when there is only the root node
        //
        DefaultTreeModel model = (DefaultTreeModel) getModel();
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
        boolean rootLeaf = root.isLeaf();
        model.insertNodeInto(child, parent, index);
        if (rootLeaf) {
            model.nodeStructureChanged(root);
        }
    }

    /**
     * This method adds the child to the specified parent node.
     * The index where the child is to be added depends on the
     * child node being Comparable or not. If the child node is
     * not Comparable then it is added at the end, i.e. right
     * after the current parent's children.
     */
    // Call on EDT
    private synchronized void addChildNode(
            DefaultMutableTreeNode parent, DefaultMutableTreeNode child) {
        int childCount = parent.getChildCount();
        if (childCount == 0) {
            addChildNode(parent, child, 0);
        } else if (child instanceof ComparableDefaultMutableTreeNode) {
            ComparableDefaultMutableTreeNode comparableChild =
                (ComparableDefaultMutableTreeNode)child;
            int i = 0;
            for (; i < childCount; i++) {
                DefaultMutableTreeNode brother =
                        (DefaultMutableTreeNode) parent.getChildAt(i);
                //child < brother
                if (comparableChild.compareTo(brother) < 0) {
                    addChildNode(parent, child, i);
                    break;
                }
                //child = brother
                else if (comparableChild.compareTo(brother) == 0) {
                    addChildNode(parent, child, i);
                    break;
                }
            }
            //child < all brothers
            if (i == childCount) {
                addChildNode(parent, child, childCount);
            }
        } else {
            //not comparable, add at the end
            addChildNode(parent, child, childCount);
        }
    }

    /**
     * This method removes all the displayed nodes from the tree,
     * but does not affect actual MBeanServer contents.
     */
    // Call on EDT
    public synchronized void removeAll() {
        DefaultTreeModel model = (DefaultTreeModel) getModel();
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
        root.removeAllChildren();
        model.nodeStructureChanged(root);
        nodes.clear();
    }

    public void delMBeanFromView(final ObjectName mbean) {
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                // We assume here that MBeans are removed one by one (on MBean
                // unregistered notification). Deletes the tree node associated
                // with the given MBean and recursively all the node parents
                // which are leaves and non XMBean.
                //
                synchronized (XTree.this) {
                    DefaultMutableTreeNode node = null;
                    Dn dn = buildDn(mbean);
                    if (dn.size() > 0) {
                        DefaultTreeModel model = (DefaultTreeModel) getModel();
                        Token token = dn.getToken(0);
                        String hashKey = dn.getHashKey(token);
                        node = nodes.get(hashKey);
                        if ((node != null) && (!node.isRoot())) {
                            if (hasMBeanChildren(node)) {
                                removeNonMBeanChildren(node);
                                String label = token.getValue().toString();
                                XNodeInfo userObject = new XNodeInfo(
                                        Type.NONMBEAN, label,
                                        label, token.toString());
                                changeNodeValue(node, userObject);
                            } else {
                                DefaultMutableTreeNode parent =
                                        (DefaultMutableTreeNode) node.getParent();
                                model.removeNodeFromParent(node);
                                nodes.remove(hashKey);
                                delParentFromView(dn, 1, parent);
                            }
                        }
                    }
                }
            }
        });
    }

    /**
     * Returns true if any of the children nodes is an MBean.
     */
    private boolean hasMBeanChildren(DefaultMutableTreeNode node) {
        for (Enumeration e = node.children(); e.hasMoreElements(); ) {
            DefaultMutableTreeNode n = (DefaultMutableTreeNode) e.nextElement();
            if (((XNodeInfo) n.getUserObject()).getType().equals(Type.MBEAN)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Remove all the children nodes which are not MBean.
     */
    private void removeNonMBeanChildren(DefaultMutableTreeNode node) {
        Set<DefaultMutableTreeNode> metadataNodes =
                new HashSet<DefaultMutableTreeNode>();
        DefaultTreeModel model = (DefaultTreeModel) getModel();
        for (Enumeration e = node.children(); e.hasMoreElements(); ) {
            DefaultMutableTreeNode n = (DefaultMutableTreeNode) e.nextElement();
            if (!((XNodeInfo) n.getUserObject()).getType().equals(Type.MBEAN)) {
                metadataNodes.add(n);
            }
        }
        for (DefaultMutableTreeNode n : metadataNodes) {
            model.removeNodeFromParent(n);
        }
    }

    /**
     * Removes only the parent nodes which are non MBean and leaf.
     * This method assumes the child nodes have been removed before.
     */
    private DefaultMutableTreeNode delParentFromView(
            Dn dn, int index, DefaultMutableTreeNode node) {
        if ((!node.isRoot()) && node.isLeaf() &&
                (!(((XNodeInfo) node.getUserObject()).getType().equals(Type.MBEAN)))) {
            DefaultMutableTreeNode parent =
                    (DefaultMutableTreeNode) node.getParent();
            removeChildNode(node);
            String hashKey = dn.getHashKey(dn.getToken(index));
            nodes.remove(hashKey);
            delParentFromView(dn, index + 1, parent);
        }
        return node;
    }

    public synchronized void addMBeanToView(final ObjectName mbean) {
        final XMBean xmbean;
        try {
            xmbean = new XMBean(mbean, mbeansTab);
            if (xmbean == null) {
                return;
            }
        } catch (Exception e) {
            // Got exception while trying to retrieve the
            // given MBean from the underlying MBeanServer
            //
            if (JConsole.isDebug()) {
                e.printStackTrace();
            }
            return;
        }
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                synchronized (XTree.this) {
                    // Add the new nodes to the MBean tree from leaf to root

                    Dn dn = buildDn(mbean);
                    if (dn.size() == 0) return;
                    Token token = dn.getToken(0);
                    DefaultMutableTreeNode node = null;
                    boolean nodeCreated = true;

                    //
                    // Add the node or replace its user object if already added
                    //

                    String hashKey = dn.getHashKey(token);
                    if (nodes.containsKey(hashKey)) {
                        //already in the tree, means it has been created previously
                        //when adding another node
                        node = nodes.get(hashKey);
                        //sets the user object
                        final Object data = createNodeValue(xmbean, token);
                        final String label = data.toString();
                        final XNodeInfo userObject =
                                new XNodeInfo(Type.MBEAN, data, label, mbean.toString());
                        changeNodeValue(node, userObject);
                        nodeCreated = false;
                    } else {
                        //create a new node
                        node = createDnNode(dn, token, xmbean);
                        if (node != null) {
                            nodes.put(hashKey, node);
                            nodeCreated = true;
                        } else {
                            return;
                        }
                    }

                    //
                    // Add (virtual) nodes without user object if necessary
                    //

                    for (int i = 1; i < dn.size(); i++) {
                        DefaultMutableTreeNode currentNode = null;
                        token = dn.getToken(i);
                        hashKey = dn.getHashKey(token);
                        if (nodes.containsKey(hashKey)) {
                            //node already present
                            if (nodeCreated) {
                                //previous node created, link to do
                                currentNode = nodes.get(hashKey);
                                addChildNode(currentNode, node);
                                return;
                            } else {
                                //both nodes already present
                                return;
                            }
                        } else {
                            //creates the node that can be a virtual one
                            if (token.getKeyDn().equals("domain")) {
                                //better match on keyDn that on Dn
                                currentNode = createDomainNode(dn, token);
                                if (currentNode != null) {
                                    final DefaultMutableTreeNode root =
                                            (DefaultMutableTreeNode) getModel().getRoot();
                                    addChildNode(root, currentNode);
                                }
                            } else {
                                currentNode = createSubDnNode(dn, token);
                                if (currentNode == null) {
                                    //skip
                                    continue;
                                }
                            }
                            nodes.put(hashKey, currentNode);
                            addChildNode(currentNode, node);
                            nodeCreated = true;
                        }
                        node = currentNode;
                    }
                }
            }
        });
    }

    // Call on EDT
    private synchronized void changeNodeValue(
            final DefaultMutableTreeNode node, XNodeInfo nodeValue) {
        if (node instanceof ComparableDefaultMutableTreeNode) {
            // should it stay at the same place?
            DefaultMutableTreeNode clone =
                    (DefaultMutableTreeNode) node.clone();
            clone.setUserObject(nodeValue);
            if (((ComparableDefaultMutableTreeNode) node).compareTo(clone) == 0) {
                // the order in the tree didn't change
                node.setUserObject(nodeValue);
                DefaultTreeModel model = (DefaultTreeModel) getModel();
                model.nodeChanged(node);
            } else {
                // delete the node and re-order it in case the
                // node value modifies the order in the tree
                DefaultMutableTreeNode parent =
                        (DefaultMutableTreeNode) node.getParent();
                removeChildNode(node);
                node.setUserObject(nodeValue);
                addChildNode(parent, node);
            }
        } else {
            // not comparable stays at the same place
            node.setUserObject(nodeValue);
            DefaultTreeModel model = (DefaultTreeModel) getModel();
            model.nodeChanged(node);
        }
        // Load the MBean metadata if type is MBEAN
        if (nodeValue.getType().equals(Type.MBEAN)) {
            XMBeanInfo.loadInfo(node);
            DefaultTreeModel model = (DefaultTreeModel) getModel();
            model.nodeStructureChanged(node);
        }
        // Clear the current selection and set it
        // again so valueChanged() gets called
        if (node == getLastSelectedPathComponent()) {
            TreePath selectionPath = getSelectionPath();
            clearSelection();
            setSelectionPath(selectionPath);
        }
    }

    //creates the domain node, called on a domain token
    private DefaultMutableTreeNode createDomainNode(Dn dn, Token token) {
        DefaultMutableTreeNode node = new ComparableDefaultMutableTreeNode();
        String label = dn.getDomain();
        XNodeInfo userObject =
                new XNodeInfo(Type.NONMBEAN, label, label, label);
        node.setUserObject(userObject);
        return node;
    }

    //creates the node corresponding to the whole Dn
    private DefaultMutableTreeNode createDnNode(
            Dn dn, Token token, XMBean xmbean) {
        DefaultMutableTreeNode node = new ComparableDefaultMutableTreeNode();
        Object data = createNodeValue(xmbean, token);
        String label = data.toString();
        XNodeInfo userObject = new XNodeInfo(Type.MBEAN, data, label,
                xmbean.getObjectName().toString());
        node.setUserObject(userObject);
        XMBeanInfo.loadInfo(node);
        return node;
    }

    //creates a node with the token value, call for each non domain sub
    //dn token
    private DefaultMutableTreeNode createSubDnNode(Dn dn, Token token) {
        DefaultMutableTreeNode node = new ComparableDefaultMutableTreeNode();
        String label = isKeyValueView() ? token.toString() :
            token.getValue().toString();
        XNodeInfo userObject =
                new XNodeInfo(Type.NONMBEAN, label, label, token.toString());
        node.setUserObject(userObject);
        return node;
    }

    private Object createNodeValue(XMBean xmbean, Token token) {
        String label = isKeyValueView() ? token.toString() :
            token.getValue().toString();
        xmbean.setText(label);
        return xmbean;
    }

    /**
     * Parses MBean ObjectName comma-separated properties string and put the
     * individual key/value pairs into the map. Key order in the properties
     * string is preserved by the map.
     */
    private Map<String,String> extractKeyValuePairs(
            String properties, ObjectName mbean) {
        String props = properties;
        Map<String,String> map = new LinkedHashMap<String,String>();
        int eq = props.indexOf("=");
        while (eq != -1) {
            String key = props.substring(0, eq);
            String value = mbean.getKeyProperty(key);
            map.put(key, value);
            props = props.substring(key.length() + 1 + value.length());
            if (props.startsWith(",")) {
                props = props.substring(1);
            }
            eq = props.indexOf("=");
        }
        return map;
    }

    /**
     * Returns the ordered key property list that will be used to build the
     * MBean tree. If the "com.sun.tools.jconsole.mbeans.keyPropertyList" system
     * property is not specified, then the ordered key property list used
     * to build the MBean tree will be the one returned by the method
     * ObjectName.getKeyPropertyListString() with "type" as first key,
     * and "j2eeType" as second key, if present. If any of the keys specified
     * in the comma-separated key property list does not apply to the given
     * MBean then it will be discarded.
     */
    private String getKeyPropertyListString(ObjectName mbean) {
        String props = mbean.getKeyPropertyListString();
        Map<String,String> map = extractKeyValuePairs(props, mbean);
        StringBuilder sb = new StringBuilder();
        // Add the key/value pairs to the buffer following the
        // key order defined by the "orderedKeyPropertyList"
        for (String key : orderedKeyPropertyList) {
            if (map.containsKey(key)) {
                sb.append(key + "=" + map.get(key) + ",");
                map.remove(key);
            }
        }
        // Add the remaining key/value pairs to the buffer
        for (Map.Entry<String,String> entry : map.entrySet()) {
            sb.append(entry.getKey() + "=" + entry.getValue() + ",");
        }
        String orderedKeyPropertyListString = sb.toString();
        orderedKeyPropertyListString = orderedKeyPropertyListString.substring(
                0, orderedKeyPropertyListString.length() - 1);
        return orderedKeyPropertyListString;
    }

    /**
     * Builds the Dn for the given MBean.
     */
    private Dn buildDn(ObjectName mbean) {

        String domain = mbean.getDomain();
        String globalDn = getKeyPropertyListString(mbean);

        Dn dn = buildDn(domain, globalDn, mbean);

        //update the Dn tokens to add the domain
        dn.updateDn();

        //reverse the Dn (from leaf to root)
        dn.reverseOrder();

        //compute the hashDn
        dn.computeHashDn();

        return dn;
    }

    /**
     * Builds the Dn for the given MBean.
     */
    private Dn buildDn(String domain, String globalDn, ObjectName mbean) {
        Dn dn = new Dn(domain, globalDn);
        String keyDn = "no_key";
        if (isTreeView()) {
            String props = globalDn;
            Map<String,String> map = extractKeyValuePairs(props, mbean);
            for (Map.Entry<String,String> entry : map.entrySet()) {
                dn.addToken(new Token(keyDn,
                        entry.getKey() + "=" + entry.getValue()));
            }
        } else {
            //flat view
            dn.addToken(new Token(keyDn, "properties=" + globalDn));
        }
        return dn;
    }

    //
    //utility objects
    //

    public static class ComparableDefaultMutableTreeNode
            extends DefaultMutableTreeNode
            implements Comparable<DefaultMutableTreeNode> {
        public int compareTo(DefaultMutableTreeNode node) {
            return (this.toString().compareTo(node.toString()));
        }
    }

    //
    //tree preferences
    //

    private boolean treeView;
    private boolean treeViewInit = false;
    public boolean isTreeView() {
        if (!treeViewInit) {
            treeView = getTreeViewValue();
            treeViewInit = true;
        }
        return treeView;
    }

    private boolean getTreeViewValue() {
        String treeView = System.getProperty("treeView");
        return ((treeView == null) ? true : !(treeView.equals("false")));
    }

    //
    //MBean key-value preferences
    //

    private boolean keyValueView = Boolean.getBoolean("keyValueView");
    public boolean isKeyValueView() {
        return keyValueView;
    }

    //
    //utility classes
    //

    public static class Dn {

        private String domain;
        private String dn;
        private String hashDn;
        private ArrayList<Token> tokens = new ArrayList<Token>();

        public Dn(String domain, String dn) {
            this.domain = domain;
            this.dn = dn;
        }

        public void clearTokens() {
            tokens.clear();
        }

        public void addToken(Token token) {
            tokens.add(token);
        }

        public void addToken(int index, Token token) {
            tokens.add(index, token);
        }

        public void setToken(int index, Token token) {
            tokens.set(index, token);
        }

        public void removeToken(int index) {
            tokens.remove(index);
        }

        public Token getToken(int index) {
            return tokens.get(index);
        }

        public void reverseOrder() {
            ArrayList<Token> newOrder = new ArrayList<Token>(tokens.size());
            for (int i = tokens.size() - 1; i >= 0; i--) {
                newOrder.add(tokens.get(i));
            }
            tokens = newOrder;
        }

        public int size() {
            return tokens.size();
        }

        public String getDomain() {
            return domain;
        }

        public String getDn() {
            return dn;
        }

        public String getHashDn() {
            return hashDn;
        }

        public String getHashKey(Token token) {
            final int begin = getHashDn().indexOf(token.getHashToken());
            return  getHashDn().substring(begin, getHashDn().length());
        }

        public void computeHashDn() {
            final StringBuilder hashDn = new StringBuilder();
            final int tokensSize = tokens.size();
            for (int i = 0; i < tokensSize; i++) {
                Token token = tokens.get(i);
                String hashToken = token.getHashToken();
                if (hashToken == null) {
                    hashToken = token.getToken() + (tokensSize - i);
                    token.setHashToken(hashToken);
                }
                hashDn.append(hashToken);
                hashDn.append(",");
            }
            if (tokensSize > 0) {
                this.hashDn = hashDn.substring(0, hashDn.length() - 1);
            } else {
                this.hashDn = "";
            }
        }

        /**
         * Adds the domain as the first token in the Dn.
         */
        public void updateDn() {
            addToken(0, new Token("domain", "domain=" + getDomain()));
        }

        public String toString() {
            return tokens.toString();
        }
    }

    public static class Token {

        private String keyDn;
        private String token;
        private String hashToken;
        private String key;
        private String value;

        public Token(String keyDn, String token) {
            this.keyDn = keyDn;
            this.token = token;
            buildKeyValue();
        }

        public Token(String keyDn, String token, String hashToken) {
            this.keyDn = keyDn;
            this.token = token;
            this.hashToken = hashToken;
            buildKeyValue();
        }

        public String getKeyDn() {
            return keyDn;
        }

        public String getToken() {
            return token;
        }

        public void setValue(String value) {
            this.value = value;
            this.token = key + "=" + value;
        }

        public void setKey(String key) {
            this.key = key;
            this.token = key + "=" + value;
        }

        public void setKeyDn(String keyDn) {
            this.keyDn = keyDn;
        }

        public  void setHashToken(String hashToken) {
            this.hashToken = hashToken;
        }

        public String getHashToken() {
            return hashToken;
        }

        public String getKey() {
            return key;
        }

        public String getValue() {
            return value;
        }

        public String toString(){
            return getToken();
        }

        public boolean equals(Object object) {
            if (object instanceof Token) {
                return token.equals(((Token) object));
            } else {
                return false;
            }
        }

        private void buildKeyValue() {
            int index = token.indexOf("=");
            if (index < 0) {
                key = token;
                value = token;
            } else {
                key = token.substring(0, index);
                value = token.substring(index + 1, token.length());
            }
        }
    }
}
