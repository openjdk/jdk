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
import com.sun.xml.internal.messaging.saaj.util.SAAJUtil;
import org.w3c.dom.CDATASection;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.w3c.dom.UserDataHandler;

public class CDATAImpl implements CDATASection, javax.xml.soap.Text {

    protected static final Logger log =
        Logger.getLogger(LogDomainConstants.SOAP_IMPL_DOMAIN,
                         "com.sun.xml.internal.messaging.saaj.soap.impl.LocalStrings");

    static final String cdataUC = "<![CDATA[";
    static final String cdataLC = "<![cdata[";

    @Override
    public Text splitText(int offset) throws DOMException {
        return cdataSection.splitText(offset);
    }

    @Override
    public boolean isElementContentWhitespace() {
        return cdataSection.isElementContentWhitespace();
    }

    @Override
    public String getWholeText() {
        return cdataSection.getWholeText();
    }

    @Override
    public Text replaceWholeText(String content) throws DOMException {
        return cdataSection.replaceWholeText(content);
    }

    @Override
    public String getData() throws DOMException {
        return cdataSection.getData();
    }

    @Override
    public void setData(String data) throws DOMException {
        cdataSection.setData(data);
    }

    @Override
    public int getLength() {
        return cdataSection.getLength();
    }

    @Override
    public String substringData(int offset, int count) throws DOMException {
        return cdataSection.substringData(offset, count);
    }

    @Override
    public void appendData(String arg) throws DOMException {
        cdataSection.appendData(arg);
    }

    @Override
    public void insertData(int offset, String arg) throws DOMException {
        cdataSection.insertData(offset, arg);
    }

    @Override
    public void deleteData(int offset, int count) throws DOMException {
        cdataSection.deleteData(offset, count);
    }

    @Override
    public void replaceData(int offset, int count, String arg) throws DOMException {
        cdataSection.replaceData(offset, count, arg);
    }

    @Override
    public String getNodeName() {
        return cdataSection.getNodeName();
    }

    @Override
    public String getNodeValue() throws DOMException {
        return cdataSection.getNodeValue();
    }

    @Override
    public void setNodeValue(String nodeValue) throws DOMException {
        cdataSection.setNodeValue(nodeValue);
    }

    @Override
    public short getNodeType() {
        return cdataSection.getNodeType();
    }

    @Override
    public Node getParentNode() {
        return cdataSection.getParentNode();
    }

    @Override
    public NodeList getChildNodes() {
        return cdataSection.getChildNodes();
    }

    @Override
    public Node getFirstChild() {
        return cdataSection.getFirstChild();
    }

    @Override
    public Node getLastChild() {
        return cdataSection.getLastChild();
    }

    @Override
    public Node getPreviousSibling() {
        return cdataSection.getPreviousSibling();
    }

    @Override
    public Node getNextSibling() {
        return cdataSection.getNextSibling();
    }

    @Override
    public NamedNodeMap getAttributes() {
        return cdataSection.getAttributes();
    }

    @Override
    public Document getOwnerDocument() {
        return cdataSection.getOwnerDocument();
    }

    @Override
    public Node insertBefore(Node newChild, Node refChild) throws DOMException {
        return cdataSection.insertBefore(newChild, refChild);
    }

    @Override
    public Node replaceChild(Node newChild, Node oldChild) throws DOMException {
        return cdataSection.replaceChild(newChild, oldChild);
    }

    @Override
    public Node removeChild(Node oldChild) throws DOMException {
        return cdataSection.removeChild(oldChild);
    }

    @Override
    public Node appendChild(Node newChild) throws DOMException {
        return cdataSection.appendChild(newChild);
    }

    @Override
    public boolean hasChildNodes() {
        return cdataSection.hasChildNodes();
    }

    @Override
    public Node cloneNode(boolean deep) {
        return cdataSection.cloneNode(deep);
    }

    @Override
    public void normalize() {
        cdataSection.normalize();
    }

    @Override
    public boolean isSupported(String feature, String version) {
        return cdataSection.isSupported(feature, version);
    }

    @Override
    public String getNamespaceURI() {
        return cdataSection.getNamespaceURI();
    }

    @Override
    public String getPrefix() {
        return cdataSection.getPrefix();
    }

    @Override
    public void setPrefix(String prefix) throws DOMException {
        cdataSection.setPrefix(prefix);
    }

    @Override
    public String getLocalName() {
        return cdataSection.getLocalName();
    }

    @Override
    public boolean hasAttributes() {
        return cdataSection.hasAttributes();
    }

    @Override
    public String getBaseURI() {
        return cdataSection.getBaseURI();
    }

    @Override
    public short compareDocumentPosition(Node other) throws DOMException {
        return cdataSection.compareDocumentPosition(other);
    }

    @Override
    public String getTextContent() throws DOMException {
        return cdataSection.getTextContent();
    }

    @Override
    public void setTextContent(String textContent) throws DOMException {
        cdataSection.setTextContent(textContent);
    }

    @Override
    public boolean isSameNode(Node other) {
        return cdataSection.isSameNode(other);
    }

    @Override
    public String lookupPrefix(String namespaceURI) {
        return cdataSection.lookupPrefix(namespaceURI);
    }

    @Override
    public boolean isDefaultNamespace(String namespaceURI) {
        return cdataSection.isDefaultNamespace(namespaceURI);
    }

    @Override
    public String lookupNamespaceURI(String prefix) {
        return cdataSection.lookupNamespaceURI(prefix);
    }

    @Override
    public boolean isEqualNode(Node arg) {
        return cdataSection.isEqualNode(arg);
    }

    @Override
    public Object getFeature(String feature, String version) {
        return cdataSection.getFeature(feature, version);
    }

    @Override
    public Object setUserData(String key, Object data, UserDataHandler handler) {
        return cdataSection.setUserData(key, data, handler);
    }

    @Override
    public Object getUserData(String key) {
        return cdataSection.getUserData(key);
    }

    private CDATASection cdataSection;

    public CDATAImpl(SOAPDocumentImpl ownerDoc, String text) {
        cdataSection = ownerDoc.getDomDocument().createCDATASection(text);
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
            log.severe("SAAJ0145.impl.no.null.to.parent.elem");
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
            parent.removeChild(this);
        }
    }

    public void recycleNode() {
        detachNode();
        // TBD
        //  - add this to the factory so subsequent
        //    creations can reuse this object.
    }

    public boolean isComment() {
        return false;
    }

    public CDATASection getDomElement() {
        return cdataSection;
    }
}
