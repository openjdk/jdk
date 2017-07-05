/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.namespace.QName;
import javax.xml.soap.*;

import org.w3c.dom.*;
import org.w3c.dom.Node;

import com.sun.xml.internal.messaging.saaj.SOAPExceptionImpl;
import com.sun.xml.internal.messaging.saaj.soap.SOAPDocument;
import com.sun.xml.internal.messaging.saaj.soap.SOAPDocumentImpl;
import com.sun.xml.internal.messaging.saaj.soap.name.NameImpl;
import com.sun.xml.internal.messaging.saaj.util.*;

public class ElementImpl
    extends com.sun.org.apache.xerces.internal.dom.ElementNSImpl
    implements SOAPElement, SOAPBodyElement {

    public static final String DSIG_NS = "http://www.w3.org/2000/09/xmldsig#".intern();
    public static final String XENC_NS = "http://www.w3.org/2001/04/xmlenc#".intern();
    public static final String WSU_NS = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd".intern();

    private transient AttributeManager encodingStyleAttribute = new AttributeManager();

    protected QName elementQName;

    protected static final Logger log =
        Logger.getLogger(LogDomainConstants.SOAP_IMPL_DOMAIN,
                         "com.sun.xml.internal.messaging.saaj.soap.impl.LocalStrings");

    /**
     * XML Information Set REC
     * all namespace attributes (including those named xmlns,
     * whose [prefix] property has no value) have a namespace URI of http://www.w3.org/2000/xmlns/
     */
    public final static String XMLNS_URI = "http://www.w3.org/2000/xmlns/".intern();

    /**
     * The XML Namespace ("http://www.w3.org/XML/1998/namespace"). This is
     * the Namespace URI that is automatically mapped to the "xml" prefix.
     */
    public final static String XML_URI = "http://www.w3.org/XML/1998/namespace".intern();

    public ElementImpl(SOAPDocumentImpl ownerDoc, Name name) {
        super(
            ownerDoc,
            name.getURI(),
            name.getQualifiedName(),
            name.getLocalName());
        elementQName = NameImpl.convertToQName(name);
    }

    public ElementImpl(SOAPDocumentImpl ownerDoc, QName name) {
        super(
            ownerDoc,
            name.getNamespaceURI(),
            getQualifiedName(name),
            name.getLocalPart());
        elementQName = name;
    }

    public ElementImpl(
        SOAPDocumentImpl ownerDoc,
        String uri,
        String qualifiedName) {

        super(ownerDoc, uri, qualifiedName);
        elementQName =
            new QName(uri, getLocalPart(qualifiedName), getPrefix(qualifiedName));
    }

    public void ensureNamespaceIsDeclared(String prefix, String uri) {
        String alreadyDeclaredUri = getNamespaceURI(prefix);
        if (alreadyDeclaredUri == null || !alreadyDeclaredUri.equals(uri)) {
            try {
                addNamespaceDeclaration(prefix, uri);
            } catch (SOAPException e) { /*ignore*/
            }
        }
    }

    public Document getOwnerDocument() {
        Document doc = super.getOwnerDocument();
        if (doc instanceof SOAPDocument)
            return ((SOAPDocument) doc).getDocument();
        else
            return doc;
    }

    public SOAPElement addChildElement(Name name) throws SOAPException {
        return  addElement(name);
    }

    public SOAPElement addChildElement(QName qname) throws SOAPException {
        return  addElement(qname);
    }

    public SOAPElement addChildElement(String localName) throws SOAPException {
        return (SOAPElement) addChildElement(
            NameImpl.createFromUnqualifiedName(localName));
    }

    public SOAPElement addChildElement(String localName, String prefix)
        throws SOAPException {
        String uri = getNamespaceURI(prefix);
        if (uri == null) {
            log.log(
                Level.SEVERE,
                "SAAJ0101.impl.parent.of.body.elem.mustbe.body",
                new String[] { prefix });
            throw new SOAPExceptionImpl(
                "Unable to locate namespace for prefix " + prefix);
        }
        return addChildElement(localName, prefix, uri);
    }

    public String getNamespaceURI(String prefix) {

        if ("xmlns".equals(prefix)) {
            return XMLNS_URI;
        }

        if("xml".equals(prefix)) {
            return XML_URI;
        }

        if ("".equals(prefix)) {

            org.w3c.dom.Node currentAncestor = this;
            while (currentAncestor != null &&
                   !(currentAncestor instanceof Document)) {

                if (currentAncestor instanceof ElementImpl) {
                    /*
                    QName name = ((ElementImpl) currentAncestor).getElementQName();
                    if (prefix.equals(name.getPrefix())) {
                        String uri = name.getNamespaceURI();
                        if ("".equals(uri)) {
                            return null;
                        }
                        else {
                            return uri;
                        }
                    }*/
                    if (((Element) currentAncestor).hasAttributeNS(
                            XMLNS_URI, "xmlns")) {

                        String uri =
                            ((Element) currentAncestor).getAttributeNS(
                                XMLNS_URI, "xmlns");
                        if ("".equals(uri))
                            return null;
                        else {
                            return uri;
                        }
                    }
                }
                currentAncestor = currentAncestor.getParentNode();
            }

        } else if (prefix != null) {
            // Find if there's an ancester whose name contains this prefix
            org.w3c.dom.Node currentAncestor = this;

//            String uri = currentAncestor.lookupNamespaceURI(prefix);
//            return uri;
            while (currentAncestor != null &&
                   !(currentAncestor instanceof Document)) {

               /* if (prefix.equals(currentAncestor.getPrefix())) {
                    String uri = currentAncestor.getNamespaceURI();
                    // this is because the javadoc says getNamespaceURI() is not a computed value
                    // and URI for a non-empty prefix cannot be null
                    if (uri != null)
                        return uri;
                }*/
                //String uri = currentAncestor.lookupNamespaceURI(prefix);
                //if (uri != null) {
                //    return uri;
                //}

                if (((Element) currentAncestor).hasAttributeNS(
                        XMLNS_URI, prefix)) {
                    return ((Element) currentAncestor).getAttributeNS(
                               XMLNS_URI, prefix);
                }

                currentAncestor = currentAncestor.getParentNode();
            }
        }

        return null;
    }

    public SOAPElement setElementQName(QName newName) throws SOAPException {
        ElementImpl copy =
            new ElementImpl((SOAPDocumentImpl) getOwnerDocument(), newName);
        return replaceElementWithSOAPElement(this,copy);
    }

    public QName createQName(String localName, String prefix)
        throws SOAPException {
        String uri = getNamespaceURI(prefix);
        if (uri == null) {
            log.log(Level.SEVERE, "SAAJ0102.impl.cannot.locate.ns",
                    new Object[] {prefix});
            throw new SOAPException("Unable to locate namespace for prefix "
                                    + prefix);
        }
        return new QName(uri, localName, prefix);
    }

    public String getNamespacePrefix(String uri) {

        NamespaceContextIterator eachNamespace = getNamespaceContextNodes();
        while (eachNamespace.hasNext()) {
            org.w3c.dom.Attr namespaceDecl = eachNamespace.nextNamespaceAttr();
            if (namespaceDecl.getNodeValue().equals(uri)) {
                String candidatePrefix = namespaceDecl.getLocalName();
                if ("xmlns".equals(candidatePrefix))
                    return "";
                else
                    return candidatePrefix;
            }
        }

        // Find if any of the ancestors' name has this uri
        org.w3c.dom.Node currentAncestor = this;
        while (currentAncestor != null &&
               !(currentAncestor instanceof Document)) {

            if (uri.equals(currentAncestor.getNamespaceURI()))
                return currentAncestor.getPrefix();
            currentAncestor = currentAncestor.getParentNode();
        }

        return null;
    }

    protected org.w3c.dom.Attr getNamespaceAttr(String prefix) {
        NamespaceContextIterator eachNamespace = getNamespaceContextNodes();
        if (!"".equals(prefix))
            prefix = ":"+prefix;
        while (eachNamespace.hasNext()) {
            org.w3c.dom.Attr namespaceDecl = eachNamespace.nextNamespaceAttr();
            if (!"".equals(prefix)) {
                if (namespaceDecl.getNodeName().endsWith(prefix))
                    return namespaceDecl;
            } else {
                if (namespaceDecl.getNodeName().equals("xmlns"))
                    return namespaceDecl;
            }
        }
        return null;
    }

    public NamespaceContextIterator getNamespaceContextNodes() {
        return getNamespaceContextNodes(true);
    }

    public NamespaceContextIterator getNamespaceContextNodes(boolean traverseStack) {
        return new NamespaceContextIterator(this, traverseStack);
    }

    public SOAPElement addChildElement(
        String localName,
        String prefix,
        String uri)
        throws SOAPException {

        SOAPElement newElement = createElement(NameImpl.create(localName, prefix, uri));
        addNode(newElement);
        return convertToSoapElement(newElement);
    }

    public SOAPElement addChildElement(SOAPElement element)
        throws SOAPException {

        // check if Element falls in SOAP 1.1 or 1.2 namespace.
        String elementURI = element.getElementName().getURI();
        String localName = element.getLocalName();

        if ((SOAPConstants.URI_NS_SOAP_ENVELOPE).equals(elementURI)
            || (SOAPConstants.URI_NS_SOAP_1_2_ENVELOPE).equals(elementURI)) {


            if ("Envelope".equalsIgnoreCase(localName) ||
                "Header".equalsIgnoreCase(localName) || "Body".equalsIgnoreCase(localName)) {
                log.severe("SAAJ0103.impl.cannot.add.fragements");
                throw new SOAPExceptionImpl(
                    "Cannot add fragments which contain elements "
                        + "which are in the SOAP namespace");
            }

            if ("Fault".equalsIgnoreCase(localName) && !"Body".equalsIgnoreCase(this.getLocalName())) {
                log.severe("SAAJ0154.impl.adding.fault.to.nonbody");
                throw new SOAPExceptionImpl("Cannot add a SOAPFault as a child of " + this.getLocalName());
            }

            if ("Detail".equalsIgnoreCase(localName) && !"Fault".equalsIgnoreCase(this.getLocalName())) {
                log.severe("SAAJ0155.impl.adding.detail.nonfault");
                throw new SOAPExceptionImpl("Cannot add a Detail as a child of " + this.getLocalName());
            }

            if ("Fault".equalsIgnoreCase(localName)) {
               // if body is not empty throw an exception
               if (!elementURI.equals(this.getElementName().getURI())) {
                   log.severe("SAAJ0158.impl.version.mismatch.fault");
                   throw new SOAPExceptionImpl("SOAP Version mismatch encountered when trying to add SOAPFault to SOAPBody");
               }
               Iterator<Node> it = this.getChildElements();
               if (it.hasNext()) {
                   log.severe("SAAJ0156.impl.adding.fault.error");
                   throw new SOAPExceptionImpl("Cannot add SOAPFault as a child of a non-Empty SOAPBody");
               }
            }
        }

        // preserve the encodingStyle attr as it may get lost in the import
        String encodingStyle = element.getEncodingStyle();

        ElementImpl importedElement = (ElementImpl) importElement(element);
        addNode(importedElement);

        if (encodingStyle != null)
            importedElement.setEncodingStyle(encodingStyle);

        return convertToSoapElement(importedElement);
    }

    protected Element importElement(Element element) {
        Document document = getOwnerDocument();
        Document oldDocument = element.getOwnerDocument();
        if (!oldDocument.equals(document)) {
            return (Element) document.importNode(element, true);
        } else {
            return element;
        }
    }

    protected SOAPElement addElement(Name name) throws SOAPException {
        SOAPElement newElement = createElement(name);
        addNode(newElement);
        return circumventBug5034339(newElement);
    }

    protected SOAPElement addElement(QName name) throws SOAPException {
        SOAPElement newElement = createElement(name);
        addNode(newElement);
        return circumventBug5034339(newElement);
    }

    protected SOAPElement createElement(Name name) {

        if (isNamespaceQualified(name)) {
            return (SOAPElement)
                getOwnerDocument().createElementNS(
                                       name.getURI(),
                                       name.getQualifiedName());
        } else {
            return (SOAPElement)
                getOwnerDocument().createElement(name.getQualifiedName());
        }
    }

    protected SOAPElement createElement(QName name) {

        if (isNamespaceQualified(name)) {
            return (SOAPElement)
                getOwnerDocument().createElementNS(
                                       name.getNamespaceURI(),
                                       getQualifiedName(name));
        } else {
            return (SOAPElement)
                getOwnerDocument().createElement(getQualifiedName(name));
        }
    }

    protected void addNode(org.w3c.dom.Node newElement) throws SOAPException {
        insertBefore(newElement, null);

        if (getOwnerDocument() instanceof DocumentFragment)
            return;

        if (newElement instanceof ElementImpl) {
            ElementImpl element = (ElementImpl) newElement;
            QName elementName = element.getElementQName();
            if (!"".equals(elementName.getNamespaceURI())) {
                element.ensureNamespaceIsDeclared(
                    elementName.getPrefix(), elementName.getNamespaceURI());
            }
        }

    }

    Element getFirstChildElement() {
        Node child = getFirstChild();
        while (child != null) {
            if (child instanceof Element) {
                return ((Element) child);
            }
            child = child.getNextSibling();
        }
        return null;
    }

    protected SOAPElement findChild(NameImpl name) {
        Node eachChild = getFirstChild();
        while (eachChild != null) {
            if (eachChild instanceof SOAPElement) {
                SOAPElement eachChildSoap = (SOAPElement) eachChild;
                if (eachChildSoap.getElementName().equals(name)) {
                    return eachChildSoap;
                }
            }
            eachChild = eachChild.getNextSibling();
        }
        return null;
    }

    protected SOAPElement findAndConvertChildElement(NameImpl name) {
        Iterator<Node> eachChild = getChildElementNodes();
        while (eachChild.hasNext()) {
            SOAPElement child = (SOAPElement) eachChild.next();
            if (child.getElementName().equals(name)) {
                return child;
            }
        }

        return null;
    }

    public SOAPElement addTextNode(String text) throws SOAPException {
        if (text.startsWith(CDATAImpl.cdataUC)
            || text.startsWith(CDATAImpl.cdataLC))
            return addCDATA(
                text.substring(CDATAImpl.cdataUC.length(), text.length() - 3));
        return addText(text);
    }

    protected SOAPElement addCDATA(String text) throws SOAPException {
        org.w3c.dom.Text cdata =
            (org.w3c.dom.Text) getOwnerDocument().createCDATASection(text);
        addNode(cdata);
        return this;
    }

    protected SOAPElement addText(String text) throws SOAPException {
        org.w3c.dom.Text textNode =
            (org.w3c.dom.Text) getOwnerDocument().createTextNode(text);
        addNode(textNode);
        return this;
    }

    public SOAPElement addAttribute(Name name, String value)
        throws SOAPException {
        addAttributeBare(name, value);
        if (!"".equals(name.getURI())) {
            ensureNamespaceIsDeclared(name.getPrefix(), name.getURI());
        }
        return this;
    }

    public SOAPElement addAttribute(QName qname, String value)
        throws SOAPException {
        addAttributeBare(qname, value);
        if (!"".equals(qname.getNamespaceURI())) {
            ensureNamespaceIsDeclared(qname.getPrefix(), qname.getNamespaceURI());
        }
        return this;
    }

    private void addAttributeBare(Name name, String value) {
        addAttributeBare(
            name.getURI(),
            name.getPrefix(),
            name.getQualifiedName(),
            value);
    }
    private void addAttributeBare(QName name, String value) {
        addAttributeBare(
            name.getNamespaceURI(),
            name.getPrefix(),
            getQualifiedName(name),
            value);
    }

    private void addAttributeBare(
        String uri,
        String prefix,
        String qualifiedName,
        String value) {

        uri = uri.length() == 0 ? null : uri;
        if (qualifiedName.equals("xmlns")) {
            uri = XMLNS_URI;
        }

        if (uri == null) {
            setAttribute(qualifiedName, value);
        } else {
            setAttributeNS(uri, qualifiedName, value);
        }
    }

    public SOAPElement addNamespaceDeclaration(String prefix, String uri)
        throws SOAPException {
        if (prefix.length() > 0) {
            setAttributeNS(XMLNS_URI, "xmlns:" + prefix, uri);
        } else {
            setAttributeNS(XMLNS_URI, "xmlns", uri);
        }
        //Fix for CR:6474641
        //tryToFindEncodingStyleAttributeName();
        return this;
    }

    public String getAttributeValue(Name name) {
        return getAttributeValueFrom(this, name);
    }

    public String getAttributeValue(QName qname) {
        return getAttributeValueFrom(
                   this,
                   qname.getNamespaceURI(),
                   qname.getLocalPart(),
                   qname.getPrefix(),
                   getQualifiedName(qname));
    }

    public Iterator getAllAttributes() {
        Iterator<Name> i = getAllAttributesFrom(this);
        ArrayList<Name> list = new ArrayList<Name>();
        while (i.hasNext()) {
            Name name = i.next();
            if (!"xmlns".equalsIgnoreCase(name.getPrefix()))
                list.add(name);
        }
        return list.iterator();
    }

    public Iterator getAllAttributesAsQNames() {
        Iterator<Name> i = getAllAttributesFrom(this);
        ArrayList<QName> list = new ArrayList<QName>();
        while (i.hasNext()) {
            Name name = i.next();
            if (!"xmlns".equalsIgnoreCase(name.getPrefix())) {
                list.add(NameImpl.convertToQName(name));
            }
        }
        return list.iterator();
    }


    public Iterator getNamespacePrefixes() {
        return doGetNamespacePrefixes(false);
    }

    public Iterator getVisibleNamespacePrefixes() {
        return doGetNamespacePrefixes(true);
    }

    protected Iterator<String> doGetNamespacePrefixes(final boolean deep) {
        return new Iterator<String>() {
            String next = null;
            String last = null;
            NamespaceContextIterator eachNamespace =
                getNamespaceContextNodes(deep);

            void findNext() {
                while (next == null && eachNamespace.hasNext()) {
                    String attributeKey =
                        eachNamespace.nextNamespaceAttr().getNodeName();
                    if (attributeKey.startsWith("xmlns:")) {
                        next = attributeKey.substring("xmlns:".length());
                    }
                }
            }

            public boolean hasNext() {
                findNext();
                return next != null;
            }

            public String next() {
                findNext();
                if (next == null) {
                    throw new NoSuchElementException();
                }

                last = next;
                next = null;
                return last;
            }

            public void remove() {
                if (last == null) {
                    throw new IllegalStateException();
                }
                eachNamespace.remove();
                next = null;
                last = null;
            }
        };
    }

    public Name getElementName() {
        return NameImpl.convertToName(elementQName);
    }

    public QName getElementQName() {
        return elementQName;
    }

    public boolean removeAttribute(Name name) {
        return removeAttribute(name.getURI(), name.getLocalName());
    }

    public boolean removeAttribute(QName name) {
        return removeAttribute(name.getNamespaceURI(), name.getLocalPart());
    }

    private boolean removeAttribute(String uri, String localName) {
        String nonzeroLengthUri =
            (uri == null || uri.length() == 0) ? null : uri;
        org.w3c.dom.Attr attribute =
            getAttributeNodeNS(nonzeroLengthUri, localName);
        if (attribute == null) {
            return false;
        }
        removeAttributeNode(attribute);
        return true;
    }

    public boolean removeNamespaceDeclaration(String prefix) {
        org.w3c.dom.Attr declaration = getNamespaceAttr(prefix);
        if (declaration == null) {
            return false;
        }
        try {
            removeAttributeNode(declaration);
        } catch (DOMException de) {
            // ignore
        }
        return true;
    }

    public Iterator<Node> getChildElements() {
        return getChildElementsFrom(this);
    }

    protected SOAPElement convertToSoapElement(Element element) {
        if (element instanceof SOAPElement) {
            return (SOAPElement) element;
        } else {
            return replaceElementWithSOAPElement(
                element,
                (ElementImpl) createElement(NameImpl.copyElementName(element)));
        }
    }

    protected static SOAPElement replaceElementWithSOAPElement(
        Element element,
        ElementImpl copy) {

        Iterator<Name> eachAttribute = getAllAttributesFrom(element);
        while (eachAttribute.hasNext()) {
            Name name = eachAttribute.next();
            copy.addAttributeBare(name, getAttributeValueFrom(element, name));
        }

        Iterator<Node> eachChild = getChildElementsFrom(element);
        while (eachChild.hasNext()) {
            Node nextChild = eachChild.next();
            copy.insertBefore(nextChild, null);
        }

        Node parent = element.getParentNode();
        if (parent != null) {
            parent.replaceChild(copy, element);
        } // XXX else throw an exception?

        return copy;
    }

    protected Iterator<Node> getChildElementNodes() {
        return new Iterator<Node>() {
            Iterator<Node> eachNode = getChildElements();
            Node next = null;
            Node last = null;

            public boolean hasNext() {
                if (next == null) {
                    while (eachNode.hasNext()) {
                        Node node = eachNode.next();
                        if (node instanceof SOAPElement) {
                            next = node;
                            break;
                        }
                    }
                }
                return next != null;
            }

            public Node next() {
                if (hasNext()) {
                    last = next;
                    next = null;
                    return last;
                }
                throw new NoSuchElementException();
            }

            public void remove() {
                if (last == null) {
                    throw new IllegalStateException();
                }
                Node target = last;
                last = null;
                removeChild(target);
            }
        };
    }

    public Iterator getChildElements(final Name name) {
       return getChildElements(name.getURI(), name.getLocalName());
    }

    public Iterator getChildElements(final QName qname) {
        return getChildElements(qname.getNamespaceURI(), qname.getLocalPart());
    }

    private Iterator<Node> getChildElements(final String nameUri, final String nameLocal) {
        return new Iterator<Node>() {
            Iterator<Node> eachElement = getChildElementNodes();
            Node next = null;
            Node last = null;

            public boolean hasNext() {
                if (next == null) {
                    while (eachElement.hasNext()) {
                        Node element = eachElement.next();
                        String elementUri = element.getNamespaceURI();
                        elementUri = elementUri == null ? "" : elementUri;
                        String elementName = element.getLocalName();
                        if (elementUri.equals(nameUri)
                            && elementName.equals(nameLocal)) {
                            next = element;
                            break;
                        }
                    }
                }
                return next != null;
            }

            public Node next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                last = next;
                next = null;
                return last;
            }

            public void remove() {
                if (last == null) {
                    throw new IllegalStateException();
                }
                Node target = last;
                last = null;
                removeChild(target);
            }
        };
    }

    public void removeContents() {
        Node currentChild = getFirstChild();

        while (currentChild != null) {
            Node temp = currentChild.getNextSibling();
            if (currentChild instanceof javax.xml.soap.Node) {
                ((javax.xml.soap.Node) currentChild).detachNode();
            } else {
                Node parent = currentChild.getParentNode();
                if (parent != null) {
                    parent.removeChild(currentChild);
                }

            }
            currentChild = temp;
        }
    }

    public void setEncodingStyle(String encodingStyle) throws SOAPException {
        if (!"".equals(encodingStyle)) {
            try {
                new URI(encodingStyle);
            } catch (URISyntaxException m) {
                log.log(
                    Level.SEVERE,
                    "SAAJ0105.impl.encoding.style.mustbe.valid.URI",
                    new String[] { encodingStyle });
                throw new IllegalArgumentException(
                    "Encoding style (" + encodingStyle + ") should be a valid URI");
            }
        }
        encodingStyleAttribute.setValue(encodingStyle);
        tryToFindEncodingStyleAttributeName();
    }

    public String getEncodingStyle() {
        String encodingStyle = encodingStyleAttribute.getValue();
        if (encodingStyle != null)
            return encodingStyle;
        String soapNamespace = getSOAPNamespace();
        if (soapNamespace != null) {
            Attr attr = getAttributeNodeNS(soapNamespace, "encodingStyle");
            if (attr != null) {
                encodingStyle = attr.getValue();
                try {
                    setEncodingStyle(encodingStyle);
                } catch (SOAPException se) {
                    // has to be ignored
                }
                return encodingStyle;
            }
        }
        return null;
    }

    // Node methods
    public String getValue() {
        javax.xml.soap.Node valueNode = getValueNode();
        return valueNode == null ? null : valueNode.getValue();
    }

    public void setValue(String value) {
        Node valueNode = getValueNodeStrict();
        if (valueNode != null) {
            valueNode.setNodeValue(value);
        } else {
            try {
                addTextNode(value);
            } catch (SOAPException e) {
                throw new RuntimeException(e.getMessage());
            }
        }
    }

    protected Node getValueNodeStrict() {
        Node node = getFirstChild();
        if (node != null) {
            if (node.getNextSibling() == null
                && node.getNodeType() == org.w3c.dom.Node.TEXT_NODE) {
                return node;
            } else {
                log.severe("SAAJ0107.impl.elem.child.not.single.text");
                throw new IllegalStateException();
            }
        }

        return null;
    }

    protected javax.xml.soap.Node getValueNode() {
        Iterator<Node> i = getChildElements();
        while (i.hasNext()) {
            javax.xml.soap.Node n = (javax.xml.soap.Node) i.next();
            if (n.getNodeType() == org.w3c.dom.Node.TEXT_NODE ||
                n.getNodeType() == org.w3c.dom.Node.CDATA_SECTION_NODE) {
                // TODO: Hack to fix text node split into multiple lines.
                normalize();
                // Should remove the normalization step when this gets fixed in
                // DOM/Xerces.
                return (javax.xml.soap.Node) n;
            }
        }
        return null;
    }

    public void setParentElement(SOAPElement element) throws SOAPException {
        if (element == null) {
            log.severe("SAAJ0106.impl.no.null.to.parent.elem");
            throw new SOAPException("Cannot pass NULL to setParentElement");
        }
        element.addChildElement(this);
        findEncodingStyleAttributeName();
    }

    protected void findEncodingStyleAttributeName() throws SOAPException {
        String soapNamespace = getSOAPNamespace();
        if (soapNamespace != null) {
            String soapNamespacePrefix = getNamespacePrefix(soapNamespace);
            if (soapNamespacePrefix != null) {
                setEncodingStyleNamespace(soapNamespace, soapNamespacePrefix);
            }
        }
    }

    protected void setEncodingStyleNamespace(
        String soapNamespace,
        String soapNamespacePrefix)
        throws SOAPException {
        Name encodingStyleAttributeName =
            NameImpl.create(
                "encodingStyle",
                soapNamespacePrefix,
                soapNamespace);
        encodingStyleAttribute.setName(encodingStyleAttributeName);
    }

    public SOAPElement getParentElement() {
        Node parentNode = getParentNode();
        if (parentNode instanceof SOAPDocument) {
            return null;
        }
        return (SOAPElement) parentNode;
    }

    protected String getSOAPNamespace() {
        String soapNamespace = null;

        SOAPElement antecedent = this;
        while (antecedent != null) {
            Name antecedentName = antecedent.getElementName();
            String antecedentNamespace = antecedentName.getURI();

            if (NameImpl.SOAP11_NAMESPACE.equals(antecedentNamespace)
                || NameImpl.SOAP12_NAMESPACE.equals(antecedentNamespace)) {

                soapNamespace = antecedentNamespace;
                break;
            }

            antecedent = antecedent.getParentElement();
        }

        return soapNamespace;
    }

    public void detachNode() {
        Node parent = getParentNode();
        if (parent != null) {
            parent.removeChild(this);
        }
        encodingStyleAttribute.clearNameAndValue();
        // Fix for CR: 6474641
        //tryToFindEncodingStyleAttributeName();
    }

    public void tryToFindEncodingStyleAttributeName() {
        try {
            findEncodingStyleAttributeName();
        } catch (SOAPException e) { /*okay to fail*/
        }
    }

    public void recycleNode() {
        detachNode();
        // TBD
        //  - add this to the factory so subsequent
        //    creations can reuse this object.
    }

    class AttributeManager {
        Name attributeName = null;
        String attributeValue = null;

        public void setName(Name newName) throws SOAPException {
            clearAttribute();
            attributeName = newName;
            reconcileAttribute();
        }
        public void clearName() {
            clearAttribute();
            attributeName = null;
        }
        public void setValue(String value) throws SOAPException {
            attributeValue = value;
            reconcileAttribute();
        }
        public Name getName() {
            return attributeName;
        }
        public String getValue() {
            return attributeValue;
        }

        /** Note: to be used only in detachNode method */
        public void clearNameAndValue() {
            attributeName = null;
            attributeValue = null;
        }

        private void reconcileAttribute() throws SOAPException {
            if (attributeName != null) {
                removeAttribute(attributeName);
                if (attributeValue != null) {
                    addAttribute(attributeName, attributeValue);
                }
            }
        }
        private void clearAttribute() {
            if (attributeName != null) {
                removeAttribute(attributeName);
            }
        }
    }

    protected static org.w3c.dom.Attr getNamespaceAttrFrom(
        Element element,
        String prefix) {
        NamespaceContextIterator eachNamespace =
            new NamespaceContextIterator(element);
        while (eachNamespace.hasNext()) {
            org.w3c.dom.Attr namespaceDecl = eachNamespace.nextNamespaceAttr();
            String declaredPrefix =
                NameImpl.getLocalNameFromTagName(namespaceDecl.getNodeName());
            if (declaredPrefix.equals(prefix)) {
                return namespaceDecl;
            }
        }
        return null;
    }

    protected static Iterator<Name> getAllAttributesFrom(final Element element) {
        final NamedNodeMap attributes = element.getAttributes();

        return new Iterator<Name>() {
            int attributesLength = attributes.getLength();
            int attributeIndex = 0;
            String currentName;

            public boolean hasNext() {
                return attributeIndex < attributesLength;
            }

            public Name next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                Node current = attributes.item(attributeIndex++);
                currentName = current.getNodeName();

                String prefix = NameImpl.getPrefixFromTagName(currentName);
                if (prefix.length() == 0) {
                    return NameImpl.createFromUnqualifiedName(currentName);
                } else {
                    Name attributeName =
                        NameImpl.createFromQualifiedName(
                            currentName,
                            current.getNamespaceURI());
                    return attributeName;
                }
            }

            public void remove() {
                if (currentName == null) {
                    throw new IllegalStateException();
                }
                attributes.removeNamedItem(currentName);
            }
        };
    }

    protected static String getAttributeValueFrom(Element element, Name name) {
      return getAttributeValueFrom(
          element,
          name.getURI(),
          name.getLocalName(),
          name.getPrefix(),
          name.getQualifiedName());
    }

    private static String getAttributeValueFrom(
        Element element,
        String uri,
        String localName,
        String prefix,
        String qualifiedName) {

        String nonzeroLengthUri =
            (uri == null || uri.length() == 0) ? null : uri;

        boolean mustUseGetAttributeNodeNS =  (nonzeroLengthUri != null);

        if (mustUseGetAttributeNodeNS) {

            if (!element.hasAttributeNS(uri, localName)) {
                return null;
            }

            String attrValue =
                element.getAttributeNS(nonzeroLengthUri, localName);

            return attrValue;
        }

        Attr attribute = null;
        attribute = element.getAttributeNode(qualifiedName);

        return attribute == null ? null : attribute.getValue();
    }

    protected static Iterator<Node> getChildElementsFrom(final Element element) {
        return new Iterator<Node>() {
            Node next = element.getFirstChild();
            Node nextNext = null;
            Node last = null;

            public boolean hasNext() {
                if (next != null) {
                    return true;
                }
                if (next == null && nextNext != null) {
                    next = nextNext;
                }

                return next != null;
            }

            public Node next() {
                if (hasNext()) {
                    last = next;
                    next = null;

                    if ((element instanceof ElementImpl)
                        && (last instanceof Element)) {
                        last =
                            ((ElementImpl) element).convertToSoapElement(
                                (Element) last);
                    }

                    nextNext = last.getNextSibling();
                    return last;
                }
                throw new NoSuchElementException();
            }

            public void remove() {
                if (last == null) {
                    throw new IllegalStateException();
                }
                Node target = last;
                last = null;
                element.removeChild(target);
            }
        };
    }

    public static String getQualifiedName(QName name) {
        String prefix = name.getPrefix();
        String localName = name.getLocalPart();
        String qualifiedName = null;

            if (prefix != null && prefix.length() > 0) {
                qualifiedName = prefix + ":" + localName;
            } else {
                qualifiedName = localName;
            }
         return qualifiedName;
    }

    public static String getLocalPart(String qualifiedName) {
        if (qualifiedName == null) {
            // Log
            throw new IllegalArgumentException("Cannot get local name for a \"null\" qualified name");
        }

        int index = qualifiedName.indexOf(':');
        if (index < 0)
            return qualifiedName;
        else
            return qualifiedName.substring(index + 1);
    }

    public static String getPrefix(String qualifiedName) {
        if (qualifiedName == null) {
            // Log
            throw new IllegalArgumentException("Cannot get prefix for a  \"null\" qualified name");
        }

        int index = qualifiedName.indexOf(':');
        if (index < 0)
            return "";
        else
            return qualifiedName.substring(0, index);
    }

    protected boolean isNamespaceQualified(Name name) {
        return !"".equals(name.getURI());
    }

    protected boolean isNamespaceQualified(QName name) {
        return !"".equals(name.getNamespaceURI());
    }

    protected SOAPElement circumventBug5034339(SOAPElement element) {

        Name elementName = element.getElementName();
        if (!isNamespaceQualified(elementName)) {
            String prefix = elementName.getPrefix();
            String defaultNamespace = getNamespaceURI(prefix);
            if (defaultNamespace != null) {
                Name newElementName =
                    NameImpl.create(
                        elementName.getLocalName(),
                        elementName.getPrefix(),
                        defaultNamespace);
                SOAPElement newElement = createElement(newElementName);
                replaceChild(newElement, element);
                return newElement;
            }
        }
        return element;
    }

    //TODO: This is a temporary SAAJ workaround for optimizing XWS
    // should be removed once the corresponding JAXP bug is fixed
    // It appears the bug will be fixed in JAXP 1.4 (not by Appserver 9 timeframe)
    public void setAttributeNS(
        String namespaceURI,String qualifiedName, String value) {
        int index = qualifiedName.indexOf(':');
        String localName;
        if (index < 0)
            localName = qualifiedName;
        else
            localName = qualifiedName.substring(index + 1);

        // Workaround for bug 6467808 - This needs to be fixed in JAXP

        // Rolling back this fix, this is a wrong fix, infact its causing other regressions in JAXWS tck and
        // other tests, because of this change the namespace declarations on soapenv:Fault element are never
        // picked up. The fix for bug 6467808 should be in JAXP.
//        if(elementQName.getLocalPart().equals("Fault") &&
//                (SOAPConstants.URI_NS_SOAP_1_1_ENVELOPE.equals(value) ||
//                SOAPConstants.URI_NS_SOAP_1_2_ENVELOPE.equals(value)))
//            return;

        super.setAttributeNS(namespaceURI,qualifiedName,value);
        //String tmpLocalName = this.getLocalName();
        String tmpURI = this.getNamespaceURI();
        boolean isIDNS = false;
        if( tmpURI != null && (tmpURI.equals(DSIG_NS) || tmpURI.equals(XENC_NS))){
            isIDNS = true;
        }
        //No need to check for Signature/encryption element
        //just check for namespace.
        if(localName.equals("Id")){
            if(namespaceURI == null || namespaceURI.equals("")){
                setIdAttribute(localName,true);
            }else if(isIDNS || WSU_NS.equals(namespaceURI)){
                setIdAttributeNS(namespaceURI,localName,true);
            }
        }

    }

}
