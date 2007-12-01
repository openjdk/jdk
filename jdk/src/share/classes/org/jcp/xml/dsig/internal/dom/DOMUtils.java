/*
 * Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
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
/*
 * $Id: DOMUtils.java,v 1.18 2005/05/12 19:28:34 mullan Exp $
 */
package org.jcp.xml.dsig.internal.dom;

import java.util.*;
import java.security.spec.AlgorithmParameterSpec;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import javax.xml.crypto.*;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.spec.*;

import com.sun.org.apache.xml.internal.security.utils.IdResolver;

/**
 * Useful static DOM utility methods.
 *
 * @author Sean Mullan
 */
public class DOMUtils {

    // class cannot be instantiated
    private DOMUtils() {}

    /**
     * Returns the owner document of the specified node.
     *
     * @param node the node
     * @return the owner document
     */
    public static Document getOwnerDocument(Node node) {
        if (node.getNodeType() == Node.DOCUMENT_NODE) {
            return (Document) node;
        } else {
            return node.getOwnerDocument();
        }
    }

    /**
     * Creates an element in the specified namespace, with the specified tag
     * and namespace prefix.
     *
     * @param doc the owner document
     * @param tag the tag
     * @param nsURI the namespace URI
     * @param prefix the namespace prefix
     * @return the newly created element
     */
    public static Element createElement(Document doc, String tag, String nsURI,
        String prefix) {
        String qName = prefix == null ? tag : prefix + ":" + tag;
        return doc.createElementNS(nsURI, qName);
    }

    /**
     * Sets an element's attribute (using DOM level 2) with the
     * specified value and namespace prefix.
     *
     * @param elem the element to set the attribute on
     * @param name the name of the attribute
     * @param value the attribute value. If null, no attribute is set.
     */
    public static void setAttribute(Element elem, String name, String value) {
        if (value == null) return;
        elem.setAttributeNS(null, name, value);
    }

    /**
     * Sets an element's attribute (using DOM level 2) with the
     * specified value and namespace prefix AND registers the ID value with
     * the specified element. This is for resolving same-document
     * ID references.
     *
     * @param elem the element to set the attribute on
     * @param name the name of the attribute
     * @param value the attribute value. If null, no attribute is set.
     */
    public static void setAttributeID(Element elem, String name, String value) {
        if (value == null) return;
        elem.setAttributeNS(null, name, value);
        IdResolver.registerElementById(elem, value);
    }

    /**
     * Returns the first child element of the specified node, or null if there
     * is no such element.
     *
     * @param node the node
     * @return the first child element of the specified node, or null if there
     *    is no such element
     * @throws NullPointerException if <code>node == null</code>
     */
    public static Element getFirstChildElement(Node node) {
        Node child = node.getFirstChild();
        while (child != null && child.getNodeType() != Node.ELEMENT_NODE) {
            child = child.getNextSibling();
        }
        return (Element) child;
    }

    /**
     * Returns the last child element of the specified node, or null if there
     * is no such element.
     *
     * @param node the node
     * @return the last child element of the specified node, or null if there
     *    is no such element
     * @throws NullPointerException if <code>node == null</code>
     */
    public static Element getLastChildElement(Node node) {
        Node child = node.getLastChild();
        while (child != null && child.getNodeType() != Node.ELEMENT_NODE) {
            child = child.getPreviousSibling();
        }
        return (Element) child;
    }

    /**
     * Returns the next sibling element of the specified node, or null if there
     * is no such element.
     *
     * @param node the node
     * @return the next sibling element of the specified node, or null if there
     *    is no such element
     * @throws NullPointerException if <code>node == null</code>
     */
    public static Element getNextSiblingElement(Node node) {
        Node sibling = node.getNextSibling();
        while (sibling != null && sibling.getNodeType() != Node.ELEMENT_NODE) {
            sibling = sibling.getNextSibling();
        }
        return (Element) sibling;
    }

    /**
     * Returns the attribute value for the attribute with the specified name.
     * Returns null if there is no such attribute, or
     * the empty string if the attribute value is empty.
     *
     * <p>This works around a limitation of the DOM
     * <code>Element.getAttributeNode</code> method, which does not distinguish
     * between an unspecified attribute and an attribute with a value of
     * "" (it returns "" for both cases).
     *
     * @param elem the element containing the attribute
     * @param name the name of the attribute
     * @return the attribute value (may be null if unspecified)
     */
    public static String getAttributeValue(Element elem, String name) {
        Attr attr = elem.getAttributeNodeNS(null, name);
        return (attr == null) ? null : attr.getValue();
    }

    /**
     * Returns a Set of <code>Node</code>s, backed by the specified
     * <code>NodeList</code>.
     *
     * @param nl the NodeList
     * @return a Set of Nodes
     */
    public static Set nodeSet(NodeList nl) {
        return new NodeSet(nl);
    }

    static class NodeSet extends AbstractSet {
        private NodeList nl;
        public NodeSet(NodeList nl) {
            this.nl = nl;
        }

        public int size() { return nl.getLength(); }
        public Iterator iterator() {
            return new Iterator() {
                int index = 0;

                public void remove() {
                    throw new UnsupportedOperationException();
                }
                public Object next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                    return nl.item(index++);
                }
                public boolean hasNext() {
                    return index < nl.getLength() ? true : false;
                }
            };
        }
    }

    /**
     * Returns the prefix associated with the specified namespace URI
     *
     * @param context contains the namespace map
     * @param nsURI the namespace URI
     * @return the prefix associated with the specified namespace URI, or
     *    null if not set
     */
    public static String getNSPrefix(XMLCryptoContext context, String nsURI) {
        if (context != null) {
            return context.getNamespacePrefix
                (nsURI, context.getDefaultNamespacePrefix());
        } else {
            return null;
        }
    }

    /**
     * Returns the prefix associated with the XML Signature namespace URI
     *
     * @param context contains the namespace map
     * @return the prefix associated with the specified namespace URI, or
     *    null if not set
     */
    public static String getSignaturePrefix(XMLCryptoContext context) {
        return getNSPrefix(context, XMLSignature.XMLNS);
    }

    /**
     * Removes all children nodes from the specified node.
     *
     * @param node the parent node whose children are to be removed
     */
    public static void removeAllChildren(Node node) {
        NodeList children = node.getChildNodes();
        for (int i = 0, length = children.getLength(); i < length; i++) {
            node.removeChild(children.item(i));
        }
    }

    /**
     * Compares 2 nodes for equality. Implementation is not complete.
     */
    public static boolean nodesEqual(Node thisNode, Node otherNode) {
        if (thisNode == otherNode) {
            return true;
        }
        if (thisNode.getNodeType() != otherNode.getNodeType()) {
            return false;
        }
        // FIXME - test content, etc
        return true;
    }

    /**
     * Checks if child element has same owner document before
     * appending to the parent, and imports it to the parent's document
     * if necessary.
     */
    public static void appendChild(Node parent, Node child) {
        Document ownerDoc = getOwnerDocument(parent);
        if (child.getOwnerDocument() != ownerDoc) {
            parent.appendChild(ownerDoc.importNode(child, true));
        } else {
            parent.appendChild(child);
        }
    }

    public static boolean paramsEqual(AlgorithmParameterSpec spec1,
        AlgorithmParameterSpec spec2) {
        if (spec1 == spec2) {
            return true;
        }
        if (spec1 instanceof XPathFilter2ParameterSpec &&
            spec2 instanceof XPathFilter2ParameterSpec) {
            return paramsEqual((XPathFilter2ParameterSpec) spec1,
                (XPathFilter2ParameterSpec) spec2);
        }
        if (spec1 instanceof ExcC14NParameterSpec &&
            spec2 instanceof ExcC14NParameterSpec) {
            return paramsEqual((ExcC14NParameterSpec) spec1,
                (ExcC14NParameterSpec) spec2);
        }
        if (spec1 instanceof XPathFilterParameterSpec &&
            spec2 instanceof XPathFilterParameterSpec) {
            return paramsEqual((XPathFilterParameterSpec) spec1,
                (XPathFilterParameterSpec) spec2);
        }
        if (spec1 instanceof XSLTTransformParameterSpec &&
            spec2 instanceof XSLTTransformParameterSpec) {
            return paramsEqual((XSLTTransformParameterSpec) spec1,
                (XSLTTransformParameterSpec) spec2);
        }
        return false;
    }

    private static boolean paramsEqual(XPathFilter2ParameterSpec spec1,
        XPathFilter2ParameterSpec spec2) {

        List types = spec1.getXPathList();
        List otypes = spec2.getXPathList();
        int size = types.size();
        if (size != otypes.size()) {
            return false;
        }
        for (int i = 0; i < size; i++) {
            XPathType type = (XPathType) types.get(i);
            XPathType otype = (XPathType) otypes.get(i);
            if (!type.getExpression().equals(otype.getExpression()) ||
                type.getFilter() != otype.getFilter()) {
                return false;
            }
        }
        return true;
    }

    private static boolean paramsEqual(ExcC14NParameterSpec spec1,
        ExcC14NParameterSpec spec2) {
        return spec1.getPrefixList().equals(spec2.getPrefixList());
    }

    private static boolean paramsEqual(XPathFilterParameterSpec spec1,
        XPathFilterParameterSpec spec2) {

        return spec1.getXPath().equals(spec2.getXPath());
    }

    private static boolean paramsEqual(XSLTTransformParameterSpec spec1,
        XSLTTransformParameterSpec spec2) {

        XMLStructure ostylesheet = spec2.getStylesheet();
        if (!(ostylesheet instanceof javax.xml.crypto.dom.DOMStructure)) {
            return false;
        }
        Node ostylesheetElem =
            ((javax.xml.crypto.dom.DOMStructure) ostylesheet).getNode();
        XMLStructure stylesheet = spec1.getStylesheet();
        Node stylesheetElem =
            ((javax.xml.crypto.dom.DOMStructure) stylesheet).getNode();
        return nodesEqual(stylesheetElem, ostylesheetElem);
    }
}
