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

import java.util.ResourceBundle;
import java.util.logging.Logger;

import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPException;

import com.sun.xml.internal.messaging.saaj.util.SAAJUtil;
import org.w3c.dom.Comment;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import com.sun.xml.internal.messaging.saaj.soap.SOAPDocumentImpl;
import com.sun.xml.internal.messaging.saaj.util.LogDomainConstants;
import org.w3c.dom.UserDataHandler;

public class SOAPCommentImpl
    implements javax.xml.soap.Text, org.w3c.dom.Comment {

    protected static final Logger log =
        Logger.getLogger(LogDomainConstants.SOAP_IMPL_DOMAIN,
                         "com.sun.xml.internal.messaging.saaj.soap.impl.LocalStrings");
    protected static ResourceBundle rb =
        log.getResourceBundle();

    @Override
    public String getData() throws DOMException {
        return comment.getData();
    }

    @Override
    public void setData(String data) throws DOMException {
        comment.setData(data);
    }

    @Override
    public int getLength() {
        return comment.getLength();
    }

    @Override
    public String substringData(int offset, int count) throws DOMException {
        return comment.substringData(offset, count);
    }

    @Override
    public void appendData(String arg) throws DOMException {
        comment.appendData(arg);
    }

    @Override
    public void insertData(int offset, String arg) throws DOMException {
        comment.insertData(offset, arg);
    }

    @Override
    public void deleteData(int offset, int count) throws DOMException {
        comment.deleteData(offset, count);
    }

    @Override
    public void replaceData(int offset, int count, String arg) throws DOMException {
        comment.replaceData(offset, count, arg);
    }

    @Override
    public String getNodeName() {
        return comment.getNodeName();
    }

    @Override
    public String getNodeValue() throws DOMException {
        return comment.getNodeValue();
    }

    @Override
    public void setNodeValue(String nodeValue) throws DOMException {
        comment.setNodeValue(nodeValue);
    }

    @Override
    public short getNodeType() {
        return comment.getNodeType();
    }

    @Override
    public Node getParentNode() {
        return comment.getParentNode();
    }

    @Override
    public NodeList getChildNodes() {
        return comment.getChildNodes();
    }

    @Override
    public Node getFirstChild() {
        return comment.getFirstChild();
    }

    @Override
    public Node getLastChild() {
        return comment.getLastChild();
    }

    @Override
    public Node getPreviousSibling() {
        return comment.getPreviousSibling();
    }

    @Override
    public Node getNextSibling() {
        return comment.getNextSibling();
    }

    @Override
    public NamedNodeMap getAttributes() {
        return comment.getAttributes();
    }

    @Override
    public Document getOwnerDocument() {
        return comment.getOwnerDocument();
    }

    @Override
    public Node insertBefore(Node newChild, Node refChild) throws DOMException {
        return comment.insertBefore(newChild, refChild);
    }

    @Override
    public Node replaceChild(Node newChild, Node oldChild) throws DOMException {
        return comment.replaceChild(newChild, oldChild);
    }

    @Override
    public Node removeChild(Node oldChild) throws DOMException {
        return comment.removeChild(oldChild);
    }

    @Override
    public Node appendChild(Node newChild) throws DOMException {
        return comment.appendChild(newChild);
    }

    @Override
    public boolean hasChildNodes() {
        return comment.hasChildNodes();
    }

    @Override
    public Node cloneNode(boolean deep) {
        return comment.cloneNode(deep);
    }

    @Override
    public void normalize() {
        comment.normalize();
    }

    @Override
    public boolean isSupported(String feature, String version) {
        return comment.isSupported(feature, version);
    }

    @Override
    public String getNamespaceURI() {
        return comment.getNamespaceURI();
    }

    @Override
    public String getPrefix() {
        return comment.getPrefix();
    }

    @Override
    public void setPrefix(String prefix) throws DOMException {
        comment.setPrefix(prefix);
    }

    @Override
    public String getLocalName() {
        return comment.getLocalName();
    }

    @Override
    public boolean hasAttributes() {
        return comment.hasAttributes();
    }

    @Override
    public String getBaseURI() {
        return comment.getBaseURI();
    }

    @Override
    public short compareDocumentPosition(Node other) throws DOMException {
        return comment.compareDocumentPosition(other);
    }

    @Override
    public String getTextContent() throws DOMException {
        return comment.getTextContent();
    }

    @Override
    public void setTextContent(String textContent) throws DOMException {
        comment.setTextContent(textContent);
    }

    @Override
    public boolean isSameNode(Node other) {
        return comment.isSameNode(other);
    }

    @Override
    public String lookupPrefix(String namespaceURI) {
        return comment.lookupPrefix(namespaceURI);
    }

    @Override
    public boolean isDefaultNamespace(String namespaceURI) {
        return comment.isDefaultNamespace(namespaceURI);
    }

    @Override
    public String lookupNamespaceURI(String prefix) {
        return comment.lookupNamespaceURI(prefix);
    }

    @Override
    public boolean isEqualNode(Node arg) {
        return comment.isEqualNode(arg);
    }

    @Override
    public Object getFeature(String feature, String version) {
        return comment.getFeature(feature, version);
    }

    @Override
    public Object setUserData(String key, Object data, UserDataHandler handler) {
        return comment.setUserData(key, data, handler);
    }

    @Override
    public Object getUserData(String key) {
        return comment.getUserData(key);
    }

    private Comment comment;

    public SOAPCommentImpl(SOAPDocumentImpl ownerDoc, String text) {
        comment = ownerDoc.getDomDocument().createComment(text);
        ownerDoc.register(this);
    }

    public String getValue() {
        String nodeValue = getNodeValue();
        return (nodeValue.equals("") ? null : nodeValue);
    }

    public void setValue(String text) {
        setNodeValue(text);
    }


    public void setParentElement(SOAPElement element) throws SOAPException {
        if (element == null) {
            log.severe("SAAJ0112.impl.no.null.to.parent.elem");
            throw new SOAPException("Cannot pass NULL to setParentElement");
        }
        ((ElementImpl) element).addNode(this);
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
        return true;
    }

    public Text splitText(int offset) throws DOMException {
        log.severe("SAAJ0113.impl.cannot.split.text.from.comment");
        throw new UnsupportedOperationException("Cannot split text from a Comment Node.");
    }

    public Text replaceWholeText(String content) throws DOMException {
        log.severe("SAAJ0114.impl.cannot.replace.wholetext.from.comment");
        throw new UnsupportedOperationException("Cannot replace Whole Text from a Comment Node.");
    }

    public String getWholeText() {
        //TODO: maybe we have to implement this in future.
        throw new UnsupportedOperationException("Not Supported");
    }

    public boolean isElementContentWhitespace() {
        //TODO: maybe we have to implement this in future.
        throw new UnsupportedOperationException("Not Supported");
    }

    public Comment getDomElement() {
        return comment;
    }
}
