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

/**
*
* @author SAAJ RI Development Team
*/
package com.sun.xml.internal.messaging.saaj.soap;

import com.sun.xml.internal.messaging.saaj.soap.impl.CDATAImpl;
import com.sun.xml.internal.messaging.saaj.soap.impl.ElementFactory;
import com.sun.xml.internal.messaging.saaj.soap.impl.ElementImpl;
import com.sun.xml.internal.messaging.saaj.soap.impl.SOAPCommentImpl;
import com.sun.xml.internal.messaging.saaj.soap.impl.SOAPTextImpl;
import com.sun.xml.internal.messaging.saaj.soap.name.NameImpl;
import com.sun.xml.internal.messaging.saaj.util.LogDomainConstants;
import com.sun.xml.internal.messaging.saaj.util.SAAJUtil;
import org.w3c.dom.Attr;
import org.w3c.dom.CDATASection;
import org.w3c.dom.Comment;
import org.w3c.dom.DOMConfiguration;
import org.w3c.dom.DOMException;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;
import org.w3c.dom.EntityReference;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.UserDataHandler;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPException;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class SOAPDocumentImpl implements SOAPDocument, javax.xml.soap.Node, Document {

    private static final String XMLNS = "xmlns".intern();
    protected static final Logger log =
        Logger.getLogger(LogDomainConstants.SOAP_DOMAIN,
                         "com.sun.xml.internal.messaging.saaj.soap.LocalStrings");

    SOAPPartImpl enclosingSOAPPart;

    private Document document;

    private Map<Node, javax.xml.soap.Node> domToSoap = new HashMap<>();

    public SOAPDocumentImpl(SOAPPartImpl enclosingDocument) {
        document = createDocument();
        this.enclosingSOAPPart = enclosingDocument;
        register(this);
    }

    private Document createDocument() {
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance("com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl", SAAJUtil.getSystemClassLoader());
        try {
            final DocumentBuilder documentBuilder = docFactory.newDocumentBuilder();
            return documentBuilder.newDocument();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException("Error creating xml document", e);
        }
    }

    //    public SOAPDocumentImpl(boolean grammarAccess) {
    //        super(grammarAccess);
    //    }
    //
    //    public SOAPDocumentImpl(DocumentType doctype) {
    //        super(doctype);
    //    }
    //
    //    public SOAPDocumentImpl(DocumentType doctype, boolean grammarAccess) {
    //        super(doctype, grammarAccess);
    //    }

    public SOAPPartImpl getSOAPPart() {
        if (enclosingSOAPPart == null) {
            log.severe("SAAJ0541.soap.fragment.not.bound.to.part");
            throw new RuntimeException("Could not complete operation. Fragment not bound to SOAP part.");
        }
        return enclosingSOAPPart;
    }

    public SOAPDocumentImpl getDocument() {
        return this;
    }

    public DocumentType getDoctype() {
        // SOAP means no DTD, No DTD means no doctype (SOAP 1.2 only?)
        return null;
    }

    public DOMImplementation getImplementation() {
        return document.getImplementation();
    }

    public Element getDocumentElement() {
        // This had better be an Envelope!
        getSOAPPart().doGetDocumentElement();
        return doGetDocumentElement();
    }

    protected Element doGetDocumentElement() {
        return document.getDocumentElement();
    }

    public Element createElement(String tagName) throws DOMException {
        return ElementFactory.createElement(
            this,
            NameImpl.getLocalNameFromTagName(tagName),
            NameImpl.getPrefixFromTagName(tagName),
            null);
    }

    public DocumentFragment createDocumentFragment() {
        return document.createDocumentFragment();
    }

    public org.w3c.dom.Text createTextNode(String data) {
        return new SOAPTextImpl(this, data);
    }

    public Comment createComment(String data) {
        return new SOAPCommentImpl(this, data);
    }

    public CDATASection createCDATASection(String data) throws DOMException {
        return new CDATAImpl(this, data);
    }

    public ProcessingInstruction createProcessingInstruction(
        String target,
        String data)
        throws DOMException {
        log.severe("SAAJ0542.soap.proc.instructions.not.allowed.in.docs");
        throw new UnsupportedOperationException("Processing Instructions are not allowed in SOAP documents");
    }

    public Attr createAttribute(String name) throws DOMException {
        boolean isQualifiedName = (name.indexOf(":") > 0);
        if (isQualifiedName) {
            String nsUri = null;
            String prefix = name.substring(0, name.indexOf(":"));
            //cannot do anything to resolve the URI if prefix is not
            //XMLNS.
            if (XMLNS.equals(prefix)) {
                nsUri = ElementImpl.XMLNS_URI;
                return createAttributeNS(nsUri, name);
            }
        }

        return document.createAttribute(name);
    }

    public EntityReference createEntityReference(String name)
        throws DOMException {
            log.severe("SAAJ0543.soap.entity.refs.not.allowed.in.docs");
            throw new UnsupportedOperationException("Entity References are not allowed in SOAP documents");
    }

    public NodeList getElementsByTagName(String tagname) {
        return document.getElementsByTagName(tagname);
    }

    public org.w3c.dom.Node importNode(Node importedNode, boolean deep)
        throws DOMException {
        final Node node = document.importNode(getDomNode(importedNode), deep);
        return node instanceof Element ?
            ElementFactory.createElement(this, (Element) node)
                : node;
    }

    public Element createElementNS(String namespaceURI, String qualifiedName)
        throws DOMException {
        return ElementFactory.createElement(
            this,
            NameImpl.getLocalNameFromTagName(qualifiedName),
            NameImpl.getPrefixFromTagName(qualifiedName),
            namespaceURI);
    }

    public Attr createAttributeNS(String namespaceURI, String qualifiedName)
        throws DOMException {
        return document.createAttributeNS(namespaceURI, qualifiedName);
    }

    public NodeList getElementsByTagNameNS(
        String namespaceURI,
        String localName) {
        return document.getElementsByTagNameNS(namespaceURI, localName);
    }

    public Element getElementById(String elementId) {
        return document.getElementById(elementId);
    }

    @Override
    public String getInputEncoding() {
        return document.getInputEncoding();
    }

    @Override
    public String getXmlEncoding() {
        return document.getXmlEncoding();
    }

    @Override
    public boolean getXmlStandalone() {
        return document.getXmlStandalone();
    }

    @Override
    public void setXmlStandalone(boolean xmlStandalone) throws DOMException {
        document.setXmlStandalone(xmlStandalone);
    }

    @Override
    public String getXmlVersion() {
        return document.getXmlVersion();
    }

    @Override
    public void setXmlVersion(String xmlVersion) throws DOMException {
        document.setXmlVersion(xmlVersion);
    }

    @Override
    public boolean getStrictErrorChecking() {
        return document.getStrictErrorChecking();
    }

    @Override
    public void setStrictErrorChecking(boolean strictErrorChecking) {
        document.setStrictErrorChecking(strictErrorChecking);
    }

    @Override
    public String getDocumentURI() {
        return document.getDocumentURI();
    }

    @Override
    public void setDocumentURI(String documentURI) {
        document.setDocumentURI(documentURI);
    }

    @Override
    public Node adoptNode(Node source) throws DOMException {
        return document.adoptNode(source);
    }

    @Override
    public DOMConfiguration getDomConfig() {
        return document.getDomConfig();
    }

    @Override
    public void normalizeDocument() {
        document.normalizeDocument();
    }

    @Override
    public Node renameNode(Node n, String namespaceURI, String qualifiedName) throws DOMException {
        return document.renameNode(n, namespaceURI, qualifiedName);
    }

    @Override
    public String getNodeName() {
        return document.getNodeName();
    }

    @Override
    public String getNodeValue() throws DOMException {
        return document.getNodeValue();
    }

    @Override
    public void setNodeValue(String nodeValue) throws DOMException {
        document.setNodeValue(nodeValue);
    }

    @Override
    public short getNodeType() {
        return document.getNodeType();
    }

    @Override
    public Node getParentNode() {
        return document.getParentNode();
    }

    @Override
    public NodeList getChildNodes() {
        return document.getChildNodes();
    }

    @Override
    public Node getFirstChild() {
        return document.getFirstChild();
    }

    @Override
    public Node getLastChild() {
        return document.getLastChild();
    }

    @Override
    public Node getPreviousSibling() {
        return document.getPreviousSibling();
    }

    @Override
    public Node getNextSibling() {
        return document.getNextSibling();
    }

    @Override
    public NamedNodeMap getAttributes() {
        return document.getAttributes();
    }

    @Override
    public Document getOwnerDocument() {
        return document.getOwnerDocument();
    }

    @Override
    public Node insertBefore(Node newChild, Node refChild) throws DOMException {
        return document.insertBefore(getDomNode(newChild), getDomNode(refChild));
    }

    @Override
    public Node replaceChild(Node newChild, Node oldChild) throws DOMException {
        return document.replaceChild(getDomNode(newChild), getDomNode(oldChild));
    }

    @Override
    public Node removeChild(Node oldChild) throws DOMException {
        return document.removeChild(getDomNode(oldChild));
    }

    @Override
    public Node appendChild(Node newChild) throws DOMException {
        return document.appendChild(getDomNode(newChild));
    }

    @Override
    public boolean hasChildNodes() {
        return document.hasChildNodes();
    }

    @Override
    public Node cloneNode(boolean deep) {
        return document.cloneNode(deep);
    }

    @Override
    public void normalize() {
        document.normalize();
    }

    @Override
    public boolean isSupported(String feature, String version) {
        return document.isSupported(feature, version);
    }

    @Override
    public String getNamespaceURI() {
        return document.getNamespaceURI();
    }

    @Override
    public String getPrefix() {
        return document.getPrefix();
    }

    @Override
    public void setPrefix(String prefix) throws DOMException {
        document.setPrefix(prefix);
    }

    @Override
    public String getLocalName() {
        return document.getLocalName();
    }

    @Override
    public boolean hasAttributes() {
        return document.hasAttributes();
    }

    @Override
    public String getBaseURI() {
        return document.getBaseURI();
    }

    @Override
    public short compareDocumentPosition(Node other) throws DOMException {
        return document.compareDocumentPosition(other);
    }

    @Override
    public String getTextContent() throws DOMException {
        return document.getTextContent();
    }

    @Override
    public void setTextContent(String textContent) throws DOMException {
        document.setTextContent(textContent);
    }

    @Override
    public boolean isSameNode(Node other) {
        return document.isSameNode(other);
    }

    @Override
    public String lookupPrefix(String namespaceURI) {
        return document.lookupPrefix(namespaceURI);
    }

    @Override
    public boolean isDefaultNamespace(String namespaceURI) {
        return document.isDefaultNamespace(namespaceURI);
    }

    @Override
    public String lookupNamespaceURI(String prefix) {
        return document.lookupNamespaceURI(prefix);
    }

    @Override
    public boolean isEqualNode(Node arg) {
        return document.isEqualNode(arg);
    }

    @Override
    public Object getFeature(String feature, String version) {
        return document.getFeature(feature, version);
    }

    @Override
    public Object setUserData(String key, Object data, UserDataHandler handler) {
        return document.setUserData(key, data, handler);
    }

    @Override
    public Object getUserData(String key) {
        return document.getUserData(key);
    }

    public Document getDomDocument() {
        return document;
    }

    /**
     * Insert a mapping information for {@link org.w3c.dom.Node} - {@link javax.xml.soap.Node}.
     *
     * In SAAJ, elements in DOM are expected to be interfaces of SAAJ, on the other hand in JDKs Xerces,
     * they are casted to internal impl classes. After removal of SAAJ dependency
     * to JDKs internal classes elements in DOM can never be both of them.
     *
     * @param node SAAJ wrapper node for w3c DOM node
     */
    public void register(javax.xml.soap.Node node) {
        final Node domElement = getDomNode(node);
        if (domToSoap.containsKey(domElement)) {
            throw new IllegalStateException("Element " + domElement.getNodeName()
                    + " is already registered");
        }
        domToSoap.put(domElement, node);
    }

    /**
     * Find a soap wrapper for w3c dom node.
     *
     * @param node w3c dom node nullable
     * @return soap wrapper for w3c dom node
     *
     * @throws
     */
    public javax.xml.soap.Node find(Node node) {
        return find(node, true);
    }

    private javax.xml.soap.Node find(Node node, boolean required) {
        if (node == null) {
            return null;
        }
        if (node instanceof javax.xml.soap.Node) {
            return (javax.xml.soap.Node) node;
        }
        final javax.xml.soap.Node found = domToSoap.get(node);
        if (found == null && required) {
            throw new IllegalArgumentException(MessageFormat.format("Cannot find SOAP wrapper for element {0}", node));
        }
        return found;
    }

    /**
     * If corresponding soap wrapper exists for w3c dom node it is returned,
     * if not passed dom element is returned.
     *
     * @param node w3c dom node
     * @return soap wrapper or passed w3c dom node if not found
     */
    public Node findIfPresent(Node node) {
        final javax.xml.soap.Node found = find(node, false);
        return found != null ? found : node;
    }

    /**
     * Extracts w3c dom node from corresponding soap wrapper.
     *
     * @param node soap or dom nullable
     * @return dom node
     */
    public Node getDomNode(Node node) {
        if (node instanceof SOAPDocumentImpl) {
            return ((SOAPDocumentImpl)node).getDomElement();
        } else if (node instanceof ElementImpl) {
            return ((ElementImpl) node).getDomElement();
        } else if (node instanceof SOAPTextImpl) {
            return ((SOAPTextImpl)node).getDomElement();
        } else if (node instanceof SOAPCommentImpl) {
            return ((SOAPCommentImpl)node).getDomElement();
        } else if (node instanceof CDATAImpl) {
            return ((CDATAImpl) node).getDomElement();
        }
        return node;
    }

    public Document getDomElement() {
        return document;
    }

    @Override
    public String getValue() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setValue(String value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setParentElement(SOAPElement parent) throws SOAPException {
        throw new UnsupportedOperationException();
    }

    @Override
    public SOAPElement getParentElement() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void detachNode() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void recycleNode() {
        throw new UnsupportedOperationException();
    }
}
