/*
 * Copyright (c) 2000, 2011, Oracle and/or its affiliates. All rights reserved.
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

package javax.imageio.metadata;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.DOMException;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.TypeInfo;
import org.w3c.dom.UserDataHandler;


class IIODOMException extends DOMException {

    public IIODOMException(short code, String message) {
        super(code, message);
    }
}

class IIONamedNodeMap implements NamedNodeMap {

    List nodes;

    public IIONamedNodeMap(List nodes) {
        this.nodes = nodes;
    }

    public int getLength() {
        return nodes.size();
    }

    public Node getNamedItem(String name) {
        Iterator iter = nodes.iterator();
        while (iter.hasNext()) {
            Node node = (Node)iter.next();
            if (name.equals(node.getNodeName())) {
                return node;
            }
        }

        return null;
    }

    public Node item(int index) {
        Node node = (Node)nodes.get(index);
        return node;
    }

    public Node removeNamedItem(java.lang.String name) {
        throw new DOMException(DOMException.NO_MODIFICATION_ALLOWED_ERR,
                               "This NamedNodeMap is read-only!");
    }

    public Node setNamedItem(Node arg) {
        throw new DOMException(DOMException.NO_MODIFICATION_ALLOWED_ERR,
                               "This NamedNodeMap is read-only!");
    }

    /**
     * Equivalent to <code>getNamedItem(localName)</code>.
     */
    public Node getNamedItemNS(String namespaceURI, String localName) {
        return getNamedItem(localName);
    }

    /**
     * Equivalent to <code>setNamedItem(arg)</code>.
     */
    public Node setNamedItemNS(Node arg) {
        return setNamedItem(arg);
    }

    /**
     * Equivalent to <code>removeNamedItem(localName)</code>.
     */
    public Node removeNamedItemNS(String namespaceURI, String localName) {
        return removeNamedItem(localName);
    }
}

class IIONodeList implements NodeList {

    List nodes;

    public IIONodeList(List nodes) {
        this.nodes = nodes;
    }

    public int getLength() {
        return nodes.size();
    }

    public Node item(int index) {
        if (index < 0 || index > nodes.size()) {
            return null;
        }
        return (Node)nodes.get(index);
    }
}

class IIOAttr extends IIOMetadataNode implements Attr {

    Element owner;
    String name;
    String value;

    public IIOAttr(Element owner, String name, String value) {
        this.owner = owner;
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public String getNodeName() {
        return name;
    }

    public short getNodeType() {
        return ATTRIBUTE_NODE;
    }

    public boolean getSpecified() {
        return true;
    }

    public String getValue() {
        return value;
    }

    public String getNodeValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public void setNodeValue(String value) {
        this.value = value;
    }

    public Element getOwnerElement() {
        return owner;
    }

    public void setOwnerElement(Element owner) {
        this.owner = owner;
    }

    /** This method is new in the DOM L3 for Attr interface.
     * Could throw DOMException here, but its probably OK
     * to always return false. One reason for this, is we have no good
     * way to document this exception, since this class, IIOAttr,
     * is not a public class. The rest of the methods that throw
     * DOMException are publically documented as such on IIOMetadataNode.
     * @return false
     */
    public boolean isId() {
        return false;
    }


}

/**
 * A class representing a node in a meta-data tree, which implements
 * the <a
 * href="../../../../api/org/w3c/dom/Element.html">
 * <code>org.w3c.dom.Element</code></a> interface and additionally allows
 * for the storage of non-textual objects via the
 * <code>getUserObject</code> and <code>setUserObject</code> methods.
 *
 * <p> This class is not intended to be used for general XML
 * processing. In particular, <code>Element</code> nodes created
 * within the Image I/O API are not compatible with those created by
 * Sun's standard implementation of the <code>org.w3.dom</code> API.
 * In particular, the implementation is tuned for simple uses and may
 * not perform well for intensive processing.
 *
 * <p> Namespaces are ignored in this implementation.  The terms "tag
 * name" and "node name" are always considered to be synonymous.
 *
 * <em>Note:</em>
 * The DOM Level 3 specification added a number of new methods to the
 * {@code Node}, {@code Element} and {@code Attr} interfaces that are not
 * of value to the {@code IIOMetadataNode} implementation or specification.
 *
 * Calling such methods on an {@code IIOMetadataNode}, or an {@code Attr}
 * instance returned from an {@code IIOMetadataNode} will result in a
 * {@code DOMException} being thrown.
 *
 * @see IIOMetadata#getAsTree
 * @see IIOMetadata#setFromTree
 * @see IIOMetadata#mergeTree
 *
 */
public class IIOMetadataNode implements Element, NodeList {

    /**
     * The name of the node as a <code>String</code>.
     */
    private String nodeName = null;

    /**
     * The value of the node as a <code>String</code>.  The Image I/O
     * API typically does not make use of the node value.
     */
    private String nodeValue = null;

    /**
     * The <code>Object</code> value associated with this node.
     */
    private Object userObject = null;

    /**
     * The parent node of this node, or <code>null</code> if this node
     * forms the root of its own tree.
     */
    private IIOMetadataNode parent = null;

    /**
     * The number of child nodes.
     */
    private int numChildren = 0;

    /**
     * The first (leftmost) child node of this node, or
     * <code>null</code> if this node is a leaf node.
     */
    private IIOMetadataNode firstChild = null;

    /**
     * The last (rightmost) child node of this node, or
     * <code>null</code> if this node is a leaf node.
     */
    private IIOMetadataNode lastChild = null;

    /**
     * The next (right) sibling node of this node, or
     * <code>null</code> if this node is its parent's last child node.
     */
    private IIOMetadataNode nextSibling = null;

    /**
     * The previous (left) sibling node of this node, or
     * <code>null</code> if this node is its parent's first child node.
     */
    private IIOMetadataNode previousSibling = null;

    /**
     * A <code>List</code> of <code>IIOAttr</code> nodes representing
     * attributes.
     */
    private List attributes = new ArrayList();

    /**
     * Constructs an empty <code>IIOMetadataNode</code>.
     */
    public IIOMetadataNode() {}

    /**
     * Constructs an <code>IIOMetadataNode</code> with a given node
     * name.
     *
     * @param nodeName the name of the node, as a <code>String</code>.
     */
    public IIOMetadataNode(String nodeName) {
        this.nodeName = nodeName;
    }

    /**
     * Check that the node is either <code>null</code> or an
     * <code>IIOMetadataNode</code>.
     */
    private void checkNode(Node node) throws DOMException {
        if (node == null) {
            return;
        }
        if (!(node instanceof IIOMetadataNode)) {
            throw new IIODOMException(DOMException.WRONG_DOCUMENT_ERR,
                                      "Node not an IIOMetadataNode!");
        }
    }

    // Methods from Node

    /**
     * Returns the node name associated with this node.
     *
     * @return the node name, as a <code>String</code>.
     */
    public String getNodeName() {
        return nodeName;
    }

    /**
     * Returns the value associated with this node.
     *
     * @return the node value, as a <code>String</code>.
     */
    public String getNodeValue(){
        return nodeValue;
    }

    /**
     * Sets the <code>String</code> value associated with this node.
     */
    public void setNodeValue(String nodeValue) {
        this.nodeValue = nodeValue;
    }

    /**
     * Returns the node type, which is always
     * <code>ELEMENT_NODE</code>.
     *
     * @return the <code>short</code> value <code>ELEMENT_NODE</code>.
     */
    public short getNodeType() {
        return ELEMENT_NODE;
    }

    /**
     * Returns the parent of this node.  A <code>null</code> value
     * indicates that the node is the root of its own tree.  To add a
     * node to an existing tree, use one of the
     * <code>insertBefore</code>, <code>replaceChild</code>, or
     * <code>appendChild</code> methods.
     *
     * @return the parent, as a <code>Node</code>.
     *
     * @see #insertBefore
     * @see #replaceChild
     * @see #appendChild
     */
    public Node getParentNode() {
        return parent;
    }

    /**
     * Returns a <code>NodeList</code> that contains all children of this node.
     * If there are no children, this is a <code>NodeList</code> containing
     * no nodes.
     *
     * @return the children as a <code>NodeList</code>
     */
    public NodeList getChildNodes() {
        return this;
    }

    /**
     * Returns the first child of this node, or <code>null</code> if
     * the node has no children.
     *
     * @return the first child, as a <code>Node</code>, or
     * <code>null</code>
     */
    public Node getFirstChild() {
        return firstChild;
    }

    /**
     * Returns the last child of this node, or <code>null</code> if
     * the node has no children.
     *
     * @return the last child, as a <code>Node</code>, or
     * <code>null</code>.
     */
    public Node getLastChild() {
        return lastChild;
    }

    /**
     * Returns the previous sibling of this node, or <code>null</code>
     * if this node has no previous sibling.
     *
     * @return the previous sibling, as a <code>Node</code>, or
     * <code>null</code>.
     */
    public Node getPreviousSibling() {
        return previousSibling;
    }

    /**
     * Returns the next sibling of this node, or <code>null</code> if
     * the node has no next sibling.
     *
     * @return the next sibling, as a <code>Node</code>, or
     * <code>null</code>.
     */
    public Node getNextSibling() {
        return nextSibling;
    }

    /**
     * Returns a <code>NamedNodeMap</code> containing the attributes of
     * this node.
     *
     * @return a <code>NamedNodeMap</code> containing the attributes of
     * this node.
     */
    public NamedNodeMap getAttributes() {
        return new IIONamedNodeMap(attributes);
    }

    /**
     * Returns <code>null</code>, since <code>IIOMetadataNode</code>s
     * do not belong to any <code>Document</code>.
     *
     * @return <code>null</code>.
     */
    public Document getOwnerDocument() {
        return null;
    }

    /**
     * Inserts the node <code>newChild</code> before the existing
     * child node <code>refChild</code>. If <code>refChild</code> is
     * <code>null</code>, insert <code>newChild</code> at the end of
     * the list of children.
     *
     * @param newChild the <code>Node</code> to insert.
     * @param refChild the reference <code>Node</code>.
     *
     * @return the node being inserted.
     *
     * @exception IllegalArgumentException if <code>newChild</code> is
     * <code>null</code>.
     */
    public Node insertBefore(Node newChild,
                             Node refChild) {
        if (newChild == null) {
            throw new IllegalArgumentException("newChild == null!");
        }

        checkNode(newChild);
        checkNode(refChild);

        IIOMetadataNode newChildNode = (IIOMetadataNode)newChild;
        IIOMetadataNode refChildNode = (IIOMetadataNode)refChild;

        // Siblings, can be null.
        IIOMetadataNode previous = null;
        IIOMetadataNode next = null;

        if (refChild == null) {
            previous = this.lastChild;
            next = null;
            this.lastChild = newChildNode;
        } else {
            previous = refChildNode.previousSibling;
            next = refChildNode;
        }

        if (previous != null) {
            previous.nextSibling = newChildNode;
        }
        if (next != null) {
            next.previousSibling = newChildNode;
        }

        newChildNode.parent = this;
        newChildNode.previousSibling = previous;
        newChildNode.nextSibling = next;

        // N.B.: O.K. if refChild == null
        if (this.firstChild == refChildNode) {
            this.firstChild = newChildNode;
        }

        ++numChildren;
        return newChildNode;
    }

    /**
     * Replaces the child node <code>oldChild</code> with
     * <code>newChild</code> in the list of children, and returns the
     * <code>oldChild</code> node.
     *
     * @param newChild the <code>Node</code> to insert.
     * @param oldChild the <code>Node</code> to be replaced.
     *
     * @return the node replaced.
     *
     * @exception IllegalArgumentException if <code>newChild</code> is
     * <code>null</code>.
     */
    public Node replaceChild(Node newChild,
                             Node oldChild) {
        if (newChild == null) {
            throw new IllegalArgumentException("newChild == null!");
        }

        checkNode(newChild);
        checkNode(oldChild);

        IIOMetadataNode newChildNode = (IIOMetadataNode)newChild;
        IIOMetadataNode oldChildNode = (IIOMetadataNode)oldChild;

        IIOMetadataNode previous = oldChildNode.previousSibling;
        IIOMetadataNode next = oldChildNode.nextSibling;

        if (previous != null) {
            previous.nextSibling = newChildNode;
        }
        if (next != null) {
            next.previousSibling = newChildNode;
        }

        newChildNode.parent = this;
        newChildNode.previousSibling = previous;
        newChildNode.nextSibling = next;

        if (firstChild == oldChildNode) {
            firstChild = newChildNode;
        }
        if (lastChild == oldChildNode) {
            lastChild = newChildNode;
        }

        oldChildNode.parent = null;
        oldChildNode.previousSibling = null;
        oldChildNode.nextSibling = null;

        return oldChildNode;
    }

    /**
     * Removes the child node indicated by <code>oldChild</code> from
     * the list of children, and returns it.
     *
     * @param oldChild the <code>Node</code> to be removed.
     *
     * @return the node removed.
     *
     * @exception IllegalArgumentException if <code>oldChild</code> is
     * <code>null</code>.
     */
    public Node removeChild(Node oldChild) {
        if (oldChild == null) {
            throw new IllegalArgumentException("oldChild == null!");
        }
        checkNode(oldChild);

        IIOMetadataNode oldChildNode = (IIOMetadataNode)oldChild;

        IIOMetadataNode previous = oldChildNode.previousSibling;
        IIOMetadataNode next = oldChildNode.nextSibling;

        if (previous != null) {
            previous.nextSibling = next;
        }
        if (next != null) {
            next.previousSibling = previous;
        }

        if (this.firstChild == oldChildNode) {
            this.firstChild = next;
        }
        if (this.lastChild == oldChildNode) {
            this.lastChild = previous;
        }

        oldChildNode.parent = null;
        oldChildNode.previousSibling = null;
        oldChildNode.nextSibling = null;

        --numChildren;
        return oldChildNode;
    }

    /**
     * Adds the node <code>newChild</code> to the end of the list of
     * children of this node.
     *
     * @param newChild the <code>Node</code> to insert.
     *
     * @return the node added.
     *
     * @exception IllegalArgumentException if <code>newChild</code> is
     * <code>null</code>.
     */
    public Node appendChild(Node newChild) {
        if (newChild == null) {
            throw new IllegalArgumentException("newChild == null!");
        }
        checkNode(newChild);

        // insertBefore will increment numChildren
        return insertBefore(newChild, null);
    }

    /**
     * Returns <code>true</code> if this node has child nodes.
     *
     * @return <code>true</code> if this node has children.
     */
    public boolean hasChildNodes() {
        return numChildren > 0;
    }

    /**
     * Returns a duplicate of this node.  The duplicate node has no
     * parent (<code>getParentNode</code> returns <code>null</code>).
     * If a shallow clone is being performed (<code>deep</code> is
     * <code>false</code>), the new node will not have any children or
     * siblings.  If a deep clone is being performed, the new node
     * will form the root of a complete cloned subtree.
     *
     * @param deep if <code>true</code>, recursively clone the subtree
     * under the specified node; if <code>false</code>, clone only the
     * node itself.
     *
     * @return the duplicate node.
     */
    public Node cloneNode(boolean deep) {
        IIOMetadataNode newNode = new IIOMetadataNode(this.nodeName);
        newNode.setUserObject(getUserObject());
        // Attributes

        if (deep) {
            for (IIOMetadataNode child = firstChild;
                 child != null;
                 child = child.nextSibling) {
                newNode.appendChild(child.cloneNode(true));
            }
        }

        return newNode;
    }

    /**
     * Does nothing, since <code>IIOMetadataNode</code>s do not
     * contain <code>Text</code> children.
     */
    public void normalize() {
    }

    /**
     * Returns <code>false</code> since DOM features are not
     * supported.
     *
     * @return <code>false</code>.
     *
     * @param feature a <code>String</code>, which is ignored.
     * @param version a <code>String</code>, which is ignored.
     */
    public boolean isSupported(String feature, String version) {
        return false;
    }

    /**
     * Returns <code>null</code>, since namespaces are not supported.
     */
    public String getNamespaceURI() throws DOMException {
        return null;
    }

    /**
     * Returns <code>null</code>, since namespaces are not supported.
     *
     * @return <code>null</code>.
     *
     * @see #setPrefix
     */
    public String getPrefix() {
        return null;
    }

    /**
     * Does nothing, since namespaces are not supported.
     *
     * @param prefix a <code>String</code>, which is ignored.
     *
     * @see #getPrefix
     */
    public void setPrefix(String prefix) {
    }

    /**
     * Equivalent to <code>getNodeName</code>.
     *
     * @return the node name, as a <code>String</code>.
     */
    public String getLocalName() {
        return nodeName;
    }

    // Methods from Element


    /**
     * Equivalent to <code>getNodeName</code>.
     *
     * @return the node name, as a <code>String</code>
     */
    public String getTagName() {
        return nodeName;
    }

    /**
     * Retrieves an attribute value by name.
     * @param name The name of the attribute to retrieve.
     * @return The <code>Attr</code> value as a string, or the empty string
     * if that attribute does not have a specified or default value.
     */
    public String getAttribute(String name) {
        Attr attr = getAttributeNode(name);
        if (attr == null) {
            return "";
        }
        return attr.getValue();
    }

    /**
     * Equivalent to <code>getAttribute(localName)</code>.
     *
     * @see #setAttributeNS
     */
    public String getAttributeNS(String namespaceURI, String localName) {
        return getAttribute(localName);
    }

    public void setAttribute(String name, String value) {
        // Name must be valid unicode chars
        boolean valid = true;
        char[] chs = name.toCharArray();
        for (int i=0;i<chs.length;i++) {
            if (chs[i] >= 0xfffe) {
                valid = false;
                break;
            }
        }
        if (!valid) {
            throw new IIODOMException(DOMException.INVALID_CHARACTER_ERR,
                                      "Attribute name is illegal!");
        }
        removeAttribute(name, false);
        attributes.add(new IIOAttr(this, name, value));
    }

    /**
     * Equivalent to <code>setAttribute(qualifiedName, value)</code>.
     *
     * @see #getAttributeNS
     */
    public void setAttributeNS(String namespaceURI,
                               String qualifiedName, String value) {
        setAttribute(qualifiedName, value);
    }

    public void removeAttribute(String name) {
        removeAttribute(name, true);
    }

    private void removeAttribute(String name, boolean checkPresent) {
        int numAttributes = attributes.size();
        for (int i = 0; i < numAttributes; i++) {
            IIOAttr attr = (IIOAttr)attributes.get(i);
            if (name.equals(attr.getName())) {
                attr.setOwnerElement(null);
                attributes.remove(i);
                return;
            }
        }

        // If we get here, the attribute doesn't exist
        if (checkPresent) {
            throw new IIODOMException(DOMException.NOT_FOUND_ERR,
                                      "No such attribute!");
        }
    }

    /**
     * Equivalent to <code>removeAttribute(localName)</code>.
     */
    public void removeAttributeNS(String namespaceURI,
                                  String localName) {
        removeAttribute(localName);
    }

    public Attr getAttributeNode(String name) {
        Node node = getAttributes().getNamedItem(name);
        return (Attr)node;
    }

    /**
     * Equivalent to <code>getAttributeNode(localName)</code>.
     *
     * @see #setAttributeNodeNS
     */
   public Attr getAttributeNodeNS(String namespaceURI,
                                   String localName) {
        return getAttributeNode(localName);
    }

    public Attr setAttributeNode(Attr newAttr) throws DOMException {
        Element owner = newAttr.getOwnerElement();
        if (owner != null) {
            if (owner == this) {
                return null;
            } else {
                throw new DOMException(DOMException.INUSE_ATTRIBUTE_ERR,
                                       "Attribute is already in use");
            }
        }

        IIOAttr attr;
        if (newAttr instanceof IIOAttr) {
            attr = (IIOAttr)newAttr;
            attr.setOwnerElement(this);
        } else {
            attr = new IIOAttr(this,
                               newAttr.getName(),
                               newAttr.getValue());
        }

        Attr oldAttr = getAttributeNode(attr.getName());
        if (oldAttr != null) {
            removeAttributeNode(oldAttr);
        }

        attributes.add(attr);

        return oldAttr;
    }

    /**
     * Equivalent to <code>setAttributeNode(newAttr)</code>.
     *
     * @see #getAttributeNodeNS
     */
    public Attr setAttributeNodeNS(Attr newAttr) {
        return setAttributeNode(newAttr);
    }

    public Attr removeAttributeNode(Attr oldAttr) {
        removeAttribute(oldAttr.getName());
        return oldAttr;
    }

    public NodeList getElementsByTagName(String name) {
        List l = new ArrayList();
        getElementsByTagName(name, l);
        return new IIONodeList(l);
    }

    private void getElementsByTagName(String name, List l) {
        if (nodeName.equals(name)) {
            l.add(this);
        }

        Node child = getFirstChild();
        while (child != null) {
            ((IIOMetadataNode)child).getElementsByTagName(name, l);
            child = child.getNextSibling();
        }
    }

    /**
     * Equivalent to <code>getElementsByTagName(localName)</code>.
     */
    public NodeList getElementsByTagNameNS(String namespaceURI,
                                           String localName) {
        return getElementsByTagName(localName);
    }

    public boolean hasAttributes() {
        return attributes.size() > 0;
    }

    public boolean hasAttribute(String name) {
        return getAttributeNode(name) != null;
    }

    /**
     * Equivalent to <code>hasAttribute(localName)</code>.
     */
    public boolean hasAttributeNS(String namespaceURI,
                                  String localName) {
        return hasAttribute(localName);
    }

    // Methods from NodeList

    public int getLength() {
        return numChildren;
    }

    public Node item(int index) {
        if (index < 0) {
            return null;
        }

        Node child = getFirstChild();
        while (child != null && index-- > 0) {
            child = child.getNextSibling();
        }
        return child;
    }

    /**
     * Returns the <code>Object</code> value associated with this node.
     *
     * @return the user <code>Object</code>.
     *
     * @see #setUserObject
     */
    public Object getUserObject() {
        return userObject;
    }

    /**
     * Sets the value associated with this node.
     *
     * @param userObject the user <code>Object</code>.
     *
     * @see #getUserObject
     */
    public void setUserObject(Object userObject) {
        this.userObject = userObject;
    }

    // Start of dummy methods for DOM L3.

    /**
     * This DOM Level 3 method is not supported for {@code IIOMetadataNode}
     * and will throw a {@code DOMException}.
     * @throws DOMException - always.
     */
    public void setIdAttribute(String name,
                               boolean isId)
                               throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
                               "Method not supported");
    }

    /**
     * This DOM Level 3 method is not supported for {@code IIOMetadataNode}
     * and will throw a {@code DOMException}.
     * @throws DOMException - always.
     */
    public void setIdAttributeNS(String namespaceURI,
                                 String localName,
                                 boolean isId)
                                 throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
                               "Method not supported");
    }

    /**
     * This DOM Level 3 method is not supported for {@code IIOMetadataNode}
     * and will throw a {@code DOMException}.
     * @throws DOMException - always.
     */
    public void setIdAttributeNode(Attr idAttr,
                                   boolean isId)
                                   throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
                               "Method not supported");
    }

    /**
     * This DOM Level 3 method is not supported for {@code IIOMetadataNode}
     * and will throw a {@code DOMException}.
     * @throws DOMException - always.
     */
    public TypeInfo getSchemaTypeInfo() throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
                               "Method not supported");
    }

    /**
     * This DOM Level 3 method is not supported for {@code IIOMetadataNode}
     * and will throw a {@code DOMException}.
     * @throws DOMException - always.
     */
    public Object setUserData(String key,
                              Object data,
                              UserDataHandler handler) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
                               "Method not supported");
    }

    /**
     * This DOM Level 3 method is not supported for {@code IIOMetadataNode}
     * and will throw a {@code DOMException}.
     * @throws DOMException - always.
     */
    public Object getUserData(String key) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
                               "Method not supported");
    }

    /**
     * This DOM Level 3 method is not supported for {@code IIOMetadataNode}
     * and will throw a {@code DOMException}.
     * @throws DOMException - always.
     */
    public Object getFeature(String feature, String version)
                              throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
                               "Method not supported");
    }

    /**
     * This DOM Level 3 method is not supported for {@code IIOMetadataNode}
     * and will throw a {@code DOMException}.
     * @throws DOMException - always.
     */
    public boolean isSameNode(Node node) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
                               "Method not supported");
    }

    /**
     * This DOM Level 3 method is not supported for {@code IIOMetadataNode}
     * and will throw a {@code DOMException}.
     * @throws DOMException - always.
     */
    public boolean isEqualNode(Node node) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
                               "Method not supported");
    }

    /**
     * This DOM Level 3 method is not supported for {@code IIOMetadataNode}
     * and will throw a {@code DOMException}.
     * @throws DOMException - always.
     */
    public String lookupNamespaceURI(String prefix) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
                               "Method not supported");
    }

    /**
     * This DOM Level 3 method is not supported for {@code IIOMetadataNode}
     * and will throw a {@code DOMException}.
     * @throws DOMException - always.
     */
    public boolean isDefaultNamespace(String namespaceURI)
                                               throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
                               "Method not supported");
    }

    /**
     * This DOM Level 3 method is not supported for {@code IIOMetadataNode}
     * and will throw a {@code DOMException}.
     * @throws DOMException - always.
     */
    public String lookupPrefix(String namespaceURI) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
                               "Method not supported");
    }

    /**
     * This DOM Level 3 method is not supported for {@code IIOMetadataNode}
     * and will throw a {@code DOMException}.
     * @throws DOMException - always.
     */
    public String getTextContent() throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
                               "Method not supported");
    }

    /**
     * This DOM Level 3 method is not supported for {@code IIOMetadataNode}
     * and will throw a {@code DOMException}.
     * @throws DOMException - always.
     */
    public void setTextContent(String textContent) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
                               "Method not supported");
    }

    /**
     * This DOM Level 3 method is not supported for {@code IIOMetadataNode}
     * and will throw a {@code DOMException}.
     * @throws DOMException - always.
     */
    public short compareDocumentPosition(Node other)
                                         throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
                               "Method not supported");
    }

    /**
     * This DOM Level 3 method is not supported for {@code IIOMetadataNode}
     * and will throw a {@code DOMException}.
     * @throws DOMException - always.
     */
    public String getBaseURI() throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
                               "Method not supported");
    }
    //End of dummy methods for DOM L3.


}
