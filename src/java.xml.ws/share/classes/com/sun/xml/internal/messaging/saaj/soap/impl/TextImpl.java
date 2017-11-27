/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.xml.internal.messaging.saaj.soap.SOAPDocumentImpl;
import com.sun.xml.internal.messaging.saaj.util.LogDomainConstants;
import java.util.logging.Logger;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPException;
import javax.xml.soap.Text;
import org.w3c.dom.CharacterData;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.UserDataHandler;

/**
 *
 * @author lukas
 * @param <T>
 */
public abstract class TextImpl<T extends CharacterData> implements Text, CharacterData {

    protected static final Logger log
            = Logger.getLogger(LogDomainConstants.SOAP_IMPL_DOMAIN,
                    "com.sun.xml.internal.messaging.saaj.soap.impl.LocalStrings");
    private final T domNode;

    private final SOAPDocumentImpl soapDocument;

    protected TextImpl(SOAPDocumentImpl ownerDoc, String text) {
        this.soapDocument = ownerDoc;
        domNode = createN(ownerDoc, text);
        ownerDoc.register(this);
    }

    protected TextImpl(SOAPDocumentImpl ownerDoc, CharacterData data) {
        this.soapDocument = ownerDoc;
        domNode = createN(ownerDoc, data);
        ownerDoc.register(this);
    }

    protected abstract T createN(SOAPDocumentImpl ownerDoc, CharacterData data);

    protected abstract T createN(SOAPDocumentImpl ownerDoc, String text);

    public T getDomElement() {
        return domNode;
    }

    @Override
    public String getValue() {
        String nodeValue = getNodeValue();
        return (nodeValue.equals("") ? null : nodeValue);
    }

    @Override
    public void setValue(String text) {
        setNodeValue(text);
    }

    @Override
    public void setParentElement(SOAPElement parent) throws SOAPException {
        if (parent == null) {
            log.severe("SAAJ0112.impl.no.null.to.parent.elem");
            throw new SOAPException("Cannot pass NULL to setParentElement");
        }
        ((ElementImpl) parent).addNode(this);
    }

    @Override
    public SOAPElement getParentElement() {
        return (SOAPElement) getParentNode();
    }

    @Override
    public void detachNode() {
        org.w3c.dom.Node parent = getParentNode();
        if (parent != null) {
             parent.removeChild(getDomElement());
        }
    }

    @Override
    public void recycleNode() {
        detachNode();
        // TBD
        //  - add this to the factory so subsequent
        //    creations can reuse this object.
    }

    @Override
    public String getNodeName() {
        return domNode.getNodeName();
    }

    @Override
    public String getNodeValue() throws DOMException {
        return domNode.getNodeValue();
    }

    @Override
    public void setNodeValue(String nodeValue) throws DOMException {
        domNode.setNodeValue(nodeValue);
    }

    @Override
    public short getNodeType() {
        return domNode.getNodeType();
    }

    @Override
    public Node getParentNode() {
        return soapDocument.findIfPresent(domNode.getParentNode());
    }

    @Override
    public NodeList getChildNodes() {
        return new NodeListImpl(soapDocument, domNode.getChildNodes());
    }

    @Override
    public Node getFirstChild() {
        return soapDocument.findIfPresent(domNode.getFirstChild());
    }

    @Override
    public Node getLastChild() {
        return soapDocument.findIfPresent(domNode.getLastChild());
    }

    @Override
    public Node getPreviousSibling() {
        return soapDocument.findIfPresent(domNode.getPreviousSibling());
    }

    @Override
    public Node getNextSibling() {
        return soapDocument.findIfPresent(domNode.getNextSibling());
    }

    @Override
    public NamedNodeMap getAttributes() {
        return new NamedNodeMapImpl(domNode.getAttributes(), soapDocument);
    }

    @Override
    public Document getOwnerDocument() {
        return soapDocument;
    }

    @Override
    public Node insertBefore(Node newChild, Node refChild) throws DOMException {
        return soapDocument.findIfPresent(domNode.insertBefore(newChild, refChild));
    }

    @Override
    public Node replaceChild(Node newChild, Node oldChild) throws DOMException {
        return soapDocument.findIfPresent(domNode.replaceChild(newChild, oldChild));
    }

    @Override
    public Node removeChild(Node oldChild) throws DOMException {
        return soapDocument.findIfPresent(domNode.removeChild(oldChild));
    }

    @Override
    public Node appendChild(Node newChild) throws DOMException {
        return soapDocument.findIfPresent(domNode.appendChild(newChild));
    }

    @Override
    public boolean hasChildNodes() {
        return domNode.hasChildNodes();
    }

    @Override
    public Node cloneNode(boolean deep) {
        return domNode.cloneNode(deep);
    }

    @Override
    public void normalize() {
        domNode.normalize();
    }

    @Override
    public boolean isSupported(String feature, String version) {
        return domNode.isSupported(feature, version);
    }

    @Override
    public String getNamespaceURI() {
        return domNode.getNamespaceURI();
    }

    @Override
    public String getPrefix() {
        return domNode.getPrefix();
    }

    @Override
    public void setPrefix(String prefix) throws DOMException {
        domNode.setPrefix(prefix);
    }

    @Override
    public String getLocalName() {
        return domNode.getLocalName();
    }

    @Override
    public boolean hasAttributes() {
        return domNode.hasAttributes();
    }

    @Override
    public String getBaseURI() {
        return domNode.getBaseURI();
    }

    @Override
    public short compareDocumentPosition(Node other) throws DOMException {
        return domNode.compareDocumentPosition(other);
    }

    @Override
    public String getTextContent() throws DOMException {
        return domNode.getTextContent();
    }

    @Override
    public void setTextContent(String textContent) throws DOMException {
        domNode.setTextContent(textContent);;
    }

    @Override
    public boolean isSameNode(Node other) {
        return domNode.isSameNode(other);
    }

    @Override
    public String lookupPrefix(String namespaceURI) {
        return domNode.lookupPrefix(namespaceURI);
    }

    @Override
    public boolean isDefaultNamespace(String namespaceURI) {
        return domNode.isDefaultNamespace(namespaceURI);
    }

    @Override
    public String lookupNamespaceURI(String prefix) {
        return domNode.lookupNamespaceURI(prefix);
    }

    @Override
    public boolean isEqualNode(Node arg) {
        return domNode.isEqualNode(arg);
    }

    @Override
    public Object getFeature(String feature, String version) {
        return domNode.getFeature(feature, version);
    }

    @Override
    public Object setUserData(String key, Object data, UserDataHandler handler) {
        return domNode.setUserData(key, data, handler);
    }

    @Override
    public Object getUserData(String key) {
        return domNode.getUserData(key);
    }

    @Override
    public String getData() throws DOMException {
        return domNode.getData();
    }

    @Override
    public void setData(String data) throws DOMException {
        domNode.setData(data);
    }

    @Override
    public int getLength() {
        return domNode.getLength();
    }

    @Override
    public String substringData(int offset, int count) throws DOMException {
        return domNode.substringData(offset, count);
    }

    @Override
    public void appendData(String arg) throws DOMException {
        domNode.appendData(arg);
    }

    @Override
    public void insertData(int offset, String arg) throws DOMException {
        domNode.insertData(offset, arg);
    }

    @Override
    public void deleteData(int offset, int count) throws DOMException {
        domNode.deleteData(offset, count);
    }

    @Override
    public void replaceData(int offset, int count, String arg) throws DOMException {
        domNode.replaceData(offset, count, arg);
    }

    public SOAPDocumentImpl getSoapDocument() {
        return soapDocument;
    }
}
