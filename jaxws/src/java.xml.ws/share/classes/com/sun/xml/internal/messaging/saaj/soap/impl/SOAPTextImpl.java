/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.messaging.saaj.soap.impl;

import java.util.logging.Logger;

import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPException;

import com.sun.xml.internal.messaging.saaj.soap.SOAPDocumentImpl;
import com.sun.xml.internal.messaging.saaj.util.LogDomainConstants;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.w3c.dom.UserDataHandler;

public class SOAPTextImpl
    implements javax.xml.soap.Text, org.w3c.dom.Text {

    @Override
    public Text splitText(int offset) throws DOMException {
        return textNode.splitText(offset);
    }

    @Override
    public boolean isElementContentWhitespace() {
        return textNode.isElementContentWhitespace();
    }

    @Override
    public String getWholeText() {
        return textNode.getWholeText();
    }

    @Override
    public Text replaceWholeText(String content) throws DOMException {
        return textNode.replaceWholeText(content);
    }

    @Override
    public String getData() throws DOMException {
        return textNode.getData();
    }

    @Override
    public void setData(String data) throws DOMException {
        textNode.setData(data);
    }

    @Override
    public int getLength() {
        return textNode.getLength();
    }

    @Override
    public String substringData(int offset, int count) throws DOMException {
        return textNode.substringData(offset, count);
    }

    @Override
    public void appendData(String arg) throws DOMException {
        textNode.appendData(arg);
    }

    @Override
    public void insertData(int offset, String arg) throws DOMException {
        textNode.insertData(offset, arg);
    }

    @Override
    public void deleteData(int offset, int count) throws DOMException {
        textNode.deleteData(offset, count);
    }

    @Override
    public void replaceData(int offset, int count, String arg) throws DOMException {
        textNode.replaceData(offset, count, arg);
    }

    @Override
    public String getNodeName() {
        return textNode.getNodeName();
    }

    @Override
    public String getNodeValue() throws DOMException {
        return textNode.getNodeValue();
    }

    @Override
    public void setNodeValue(String nodeValue) throws DOMException {
        textNode.setNodeValue(nodeValue);
    }

    @Override
    public short getNodeType() {
        return textNode.getNodeType();
    }

    @Override
    public Node getParentNode() {
        return textNode.getParentNode();
    }

    @Override
    public NodeList getChildNodes() {
        return textNode.getChildNodes();
    }

    @Override
    public Node getFirstChild() {
        return textNode.getFirstChild();
    }

    @Override
    public Node getLastChild() {
        return textNode.getLastChild();
    }

    @Override
    public Node getPreviousSibling() {
        return textNode.getPreviousSibling();
    }

    @Override
    public Node getNextSibling() {
        return textNode.getNextSibling();
    }

    @Override
    public NamedNodeMap getAttributes() {
        return textNode.getAttributes();
    }

    @Override
    public Document getOwnerDocument() {
        return textNode.getOwnerDocument();
    }

    @Override
    public Node insertBefore(Node newChild, Node refChild) throws DOMException {
        return textNode.insertBefore(newChild, refChild);
    }

    @Override
    public Node replaceChild(Node newChild, Node oldChild) throws DOMException {
        return textNode.replaceChild(newChild, oldChild);
    }

    @Override
    public Node removeChild(Node oldChild) throws DOMException {
        return textNode.removeChild(oldChild);
    }

    @Override
    public Node appendChild(Node newChild) throws DOMException {
        return textNode.appendChild(newChild);
    }

    @Override
    public boolean hasChildNodes() {
        return textNode.hasChildNodes();
    }

    @Override
    public Node cloneNode(boolean deep) {
        return textNode.cloneNode(deep);
    }

    @Override
    public void normalize() {
        textNode.normalize();
    }

    @Override
    public boolean isSupported(String feature, String version) {
        return textNode.isSupported(feature, version);
    }

    @Override
    public String getNamespaceURI() {
        return textNode.getNamespaceURI();
    }

    @Override
    public String getPrefix() {
        return textNode.getPrefix();
    }

    @Override
    public void setPrefix(String prefix) throws DOMException {
        textNode.setPrefix(prefix);
    }

    @Override
    public String getLocalName() {
        return textNode.getLocalName();
    }

    @Override
    public boolean hasAttributes() {
        return textNode.hasAttributes();
    }

    @Override
    public String getBaseURI() {
        return textNode.getBaseURI();
    }

    @Override
    public short compareDocumentPosition(Node other) throws DOMException {
        return textNode.compareDocumentPosition(other);
    }

    @Override
    public String getTextContent() throws DOMException {
        return textNode.getTextContent();
    }

    @Override
    public void setTextContent(String textContent) throws DOMException {
        textNode.setTextContent(textContent);
    }

    @Override
    public boolean isSameNode(Node other) {
        return textNode.isSameNode(other);
    }

    @Override
    public String lookupPrefix(String namespaceURI) {
        return textNode.lookupPrefix(namespaceURI);
    }

    @Override
    public boolean isDefaultNamespace(String namespaceURI) {
        return textNode.isDefaultNamespace(namespaceURI);
    }

    @Override
    public String lookupNamespaceURI(String prefix) {
        return textNode.lookupNamespaceURI(prefix);
    }

    @Override
    public boolean isEqualNode(Node arg) {
        return textNode.isEqualNode(arg);
    }

    @Override
    public Object getFeature(String feature, String version) {
        return textNode.getFeature(feature, version);
    }

    @Override
    public Object setUserData(String key, Object data, UserDataHandler handler) {
        return textNode.setUserData(key, data, handler);
    }

    @Override
    public Object getUserData(String key) {
        return textNode.getUserData(key);
    }

    protected static final Logger log =
        Logger.getLogger(LogDomainConstants.SOAP_IMPL_DOMAIN,
                         "com.sun.xml.internal.messaging.saaj.soap.impl.LocalStrings");

    private Text textNode;

    public SOAPTextImpl(SOAPDocumentImpl ownerDoc, String text) {
        textNode = ownerDoc.getDomDocument().createTextNode(text);
        ownerDoc.register(this);
    }

    public String getValue() {
        String nodeValue = getNodeValue();
        return (nodeValue.equals("") ? null : nodeValue);
    }

    public void setValue(String text) {
        setNodeValue(text);
    }

    public void setParentElement(SOAPElement parent) throws SOAPException {
        if (parent == null) {
            log.severe("SAAJ0126.impl.cannot.locate.ns");
            throw new SOAPException("Cannot pass NULL to setParentElement");
        }
        ((ElementImpl) parent).addNode(this);
    }

    public SOAPElement getParentElement() {
        return (SOAPElement) getParentNode();
    }


    public void detachNode() {
        org.w3c.dom.Node parent = getParentNode();
        if (parent != null) {
            parent.removeChild(getDomElement());
        }
    }

    public void recycleNode() {
        detachNode();
        // TBD
        //  - add this to the factory so subsequent
        //    creations can reuse this object.
    }

    public boolean isComment() {
        String txt = getNodeValue();
        if (txt == null) {
            return false;
        }
        return txt.startsWith("<!--") && txt.endsWith("-->");
    }

    public Text getDomElement() {
        return textNode;
    }
}
