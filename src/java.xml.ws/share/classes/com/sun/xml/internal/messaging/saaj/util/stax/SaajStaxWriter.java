/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.messaging.saaj.util.stax;

import java.util.Iterator;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedList;

import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.w3c.dom.Comment;
import org.w3c.dom.Node;

/**
 * SaajStaxWriter builds a SAAJ SOAPMessage by using XMLStreamWriter interface.
 *
 * <p>
 * Defers creation of SOAPElement until all the aspects of the name of the element are known.
 * In some cases, the namespace uri is indicated only by the {@link #writeNamespace(String, String)} call.
 * After opening an element ({@code writeStartElement}, {@code writeEmptyElement} methods), all attributes
 * and namespace assignments are retained within {@link DeferredElement} object ({@code deferredElement} field).
 * As soon as any other method than {@code writeAttribute}, {@code writeNamespace}, {@code writeDefaultNamespace}
 * or {@code setNamespace} is called, the contents of {@code deferredElement} is transformed into new SOAPElement
 * (which is appropriately inserted into the SOAPMessage under construction).
 * This mechanism is necessary to fix JDK-8159058 issue.
 * </p>
 *
 * @author shih-chang.chen@oracle.com
 */
public class SaajStaxWriter implements XMLStreamWriter {

    protected SOAPMessage soap;
    protected String envURI;
    protected SOAPElement currentElement;
    protected DeferredElement deferredElement;

    static final protected String Envelope = "Envelope";
    static final protected String Header = "Header";
    static final protected String Body = "Body";
    static final protected String xmlns = "xmlns";

    public SaajStaxWriter(final SOAPMessage msg, String uri) throws SOAPException {
        soap = msg;
        this.envURI = uri;
        this.deferredElement = new DeferredElement();
    }

    public SOAPMessage getSOAPMessage() {
        return soap;
    }

    protected SOAPElement getEnvelope() throws SOAPException {
        return soap.getSOAPPart().getEnvelope();
    }

    @Override
    public void writeStartElement(final String localName) throws XMLStreamException {
        currentElement = deferredElement.flushTo(currentElement);
        deferredElement.setLocalName(localName);
    }

    @Override
    public void writeStartElement(final String ns, final String ln) throws XMLStreamException {
        writeStartElement(null, ln, ns);
    }

    @Override
    public void writeStartElement(final String prefix, final String ln, final String ns) throws XMLStreamException {
        currentElement = deferredElement.flushTo(currentElement);

        if (envURI.equals(ns)) {
            try {
                if (Envelope.equals(ln)) {
                    currentElement = getEnvelope();
                    fixPrefix(prefix);
                    return;
                } else if (Header.equals(ln)) {
                    currentElement = soap.getSOAPHeader();
                    fixPrefix(prefix);
                    return;
                } else if (Body.equals(ln)) {
                    currentElement = soap.getSOAPBody();
                    fixPrefix(prefix);
                    return;
                }
            } catch (SOAPException e) {
                throw new XMLStreamException(e);
            }

        }

        deferredElement.setLocalName(ln);
        deferredElement.setNamespaceUri(ns);
        deferredElement.setPrefix(prefix);

    }

    private void fixPrefix(final String prfx) throws XMLStreamException {
        fixPrefix(prfx, currentElement);
    }

    private void fixPrefix(final String prfx, SOAPElement element) throws XMLStreamException {
        String oldPrfx = element.getPrefix();
        if (prfx != null && !prfx.equals(oldPrfx)) {
            element.setPrefix(prfx);
        }
    }

    @Override
    public void writeEmptyElement(final String uri, final String ln) throws XMLStreamException {
        writeStartElement(null, ln, uri);
    }

    @Override
    public void writeEmptyElement(final String prefix, final String ln, final String uri) throws XMLStreamException {
        writeStartElement(prefix, ln, uri);
    }

    @Override
    public void writeEmptyElement(final String ln) throws XMLStreamException {
        writeStartElement(null, ln, null);
    }

    @Override
    public void writeEndElement() throws XMLStreamException {
        currentElement = deferredElement.flushTo(currentElement);
        if (currentElement != null) currentElement = currentElement.getParentElement();
    }

    @Override
    public void writeEndDocument() throws XMLStreamException {
        currentElement = deferredElement.flushTo(currentElement);
    }

    @Override
    public void close() throws XMLStreamException {
    }

    @Override
    public void flush() throws XMLStreamException {
    }

    @Override
    public void writeAttribute(final String ln, final String val) throws XMLStreamException {
        writeAttribute(null, null, ln, val);
    }

    @Override
    public void writeAttribute(final String prefix, final String ns, final String ln, final String value) throws XMLStreamException {
        if (ns == null && prefix == null && xmlns.equals(ln)) {
            writeNamespace("", value);
        } else {
            if (deferredElement.isInitialized()) {
                deferredElement.addAttribute(prefix, ns, ln, value);
            } else {
                addAttibuteToElement(currentElement, prefix, ns, ln, value);
            }
        }
    }

    @Override
    public void writeAttribute(final String ns, final String ln, final String val) throws XMLStreamException {
        writeAttribute(null, ns, ln, val);
    }

    @Override
    public void writeNamespace(String prefix, final String uri) throws XMLStreamException {
        // make prefix default if null or "xmlns" (according to javadoc)
        String thePrefix = prefix == null || "xmlns".equals(prefix) ? "" : prefix;
        if (deferredElement.isInitialized()) {
            deferredElement.addNamespaceDeclaration(thePrefix, uri);
        } else {
            try {
                currentElement.addNamespaceDeclaration(thePrefix, uri);
            } catch (SOAPException e) {
                throw new XMLStreamException(e);
            }
        }
    }

    @Override
    public void writeDefaultNamespace(final String uri) throws XMLStreamException {
        writeNamespace("", uri);
    }

    @Override
    public void writeComment(final String data) throws XMLStreamException {
        currentElement = deferredElement.flushTo(currentElement);
        Comment c = soap.getSOAPPart().createComment(data);
        currentElement.appendChild(c);
    }

    @Override
    public void writeProcessingInstruction(final String target) throws XMLStreamException {
        currentElement = deferredElement.flushTo(currentElement);
        Node n = soap.getSOAPPart().createProcessingInstruction(target, "");
        currentElement.appendChild(n);
    }

    @Override
    public void writeProcessingInstruction(final String target, final String data) throws XMLStreamException {
        currentElement = deferredElement.flushTo(currentElement);
        Node n = soap.getSOAPPart().createProcessingInstruction(target, data);
        currentElement.appendChild(n);
    }

    @Override
    public void writeCData(final String data) throws XMLStreamException {
        currentElement = deferredElement.flushTo(currentElement);
        Node n = soap.getSOAPPart().createCDATASection(data);
        currentElement.appendChild(n);
    }

    @Override
    public void writeDTD(final String dtd) throws XMLStreamException {
        currentElement = deferredElement.flushTo(currentElement);
    }

    @Override
    public void writeEntityRef(final String name) throws XMLStreamException {
        currentElement = deferredElement.flushTo(currentElement);
        Node n = soap.getSOAPPart().createEntityReference(name);
        currentElement.appendChild(n);
    }

    @Override
    public void writeStartDocument() throws XMLStreamException {
    }

    @Override
    public void writeStartDocument(final String version) throws XMLStreamException {
        if (version != null) soap.getSOAPPart().setXmlVersion(version);
    }

    @Override
    public void writeStartDocument(final String encoding, final String version) throws XMLStreamException {
        if (version != null) soap.getSOAPPart().setXmlVersion(version);
        if (encoding != null) {
            try {
                soap.setProperty(SOAPMessage.CHARACTER_SET_ENCODING, encoding);
            } catch (SOAPException e) {
                throw new XMLStreamException(e);
            }
        }
    }

    @Override
    public void writeCharacters(final String text) throws XMLStreamException {
        currentElement = deferredElement.flushTo(currentElement);
        try {
            currentElement.addTextNode(text);
        } catch (SOAPException e) {
            throw new XMLStreamException(e);
        }
    }

    @Override
    public void writeCharacters(final char[] text, final int start, final int len) throws XMLStreamException {
        currentElement = deferredElement.flushTo(currentElement);
        char[] chr = (start == 0 && len == text.length) ? text : Arrays.copyOfRange(text, start, start + len);
        try {
            currentElement.addTextNode(new String(chr));
        } catch (SOAPException e) {
            throw new XMLStreamException(e);
        }
    }

    @Override
    public String getPrefix(final String uri) throws XMLStreamException {
        return currentElement.lookupPrefix(uri);
    }

    @Override
    public void setPrefix(final String prefix, final String uri) throws XMLStreamException {
        // TODO: this in fact is not what would be expected from XMLStreamWriter
        //       (e.g. XMLStreamWriter for writing to output stream does not write anything as result of
        //        this method, it just rememebers that given prefix is associated with the given uri
        //        for the scope; to actually declare the prefix assignment in the resulting XML, one
        //        needs to call writeNamespace(...) method
        // Kept for backwards compatibility reasons - this might be worth of further investigation.
        if (deferredElement.isInitialized()) {
            deferredElement.addNamespaceDeclaration(prefix, uri);
        } else {
            throw new XMLStreamException("Namespace not associated with any element");
        }
    }

    @Override
    public void setDefaultNamespace(final String uri) throws XMLStreamException {
        setPrefix("", uri);
    }

    @Override
    public void setNamespaceContext(final NamespaceContext context)throws XMLStreamException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object getProperty(final String name) throws IllegalArgumentException {
        //TODO the following line is to make eclipselink happy ... they are aware of this problem -
        if (javax.xml.stream.XMLOutputFactory.IS_REPAIRING_NAMESPACES.equals(name)) return Boolean.FALSE;
        return null;
    }

    @Override
    public NamespaceContext getNamespaceContext() {
        return new NamespaceContext() {
            @Override
            public String getNamespaceURI(final String prefix) {
                return currentElement.getNamespaceURI(prefix);
            }
            @Override
            public String getPrefix(final String namespaceURI) {
                return currentElement.lookupPrefix(namespaceURI);
            }
            @Override
            public Iterator getPrefixes(final String namespaceURI) {
                return new Iterator<String>() {
                    String prefix = getPrefix(namespaceURI);
                    @Override
                    public boolean hasNext() {
                        return (prefix != null);
                    }
                    @Override
                    public String next() {
                        if (!hasNext()) throw new java.util.NoSuchElementException();
                        String next = prefix;
                        prefix = null;
                        return next;
                    }
                    @Override
                    public void remove() {}
                };
            }
        };
    }

    static void addAttibuteToElement(SOAPElement element, String prefix, String ns, String ln, String value)
            throws XMLStreamException {
        try {
            if (ns == null) {
                element.setAttributeNS("", ln, value);
            } else {
                QName name = prefix == null ? new QName(ns, ln) : new QName(ns, ln, prefix);
                element.addAttribute(name, value);
            }
        } catch (SOAPException e) {
            throw new XMLStreamException(e);
        }
    }

    /**
     * Holds details of element that needs to be deferred in order to manage namespace assignments correctly.
     *
     * <p>
     * An instance of can be set with all the aspects of the element name (local name, prefix, namespace uri).
     * Attributes and namespace declarations (special case of attribute) can be added.
     * Namespace declarations are handled so that the element namespace is updated if it is implied by the namespace
     * declaration and the namespace was not set to non-{@code null} value previously.
     * </p>
     *
     * <p>
     * The state of this object can be {@link #flushTo(SOAPElement) flushed} to SOAPElement - new SOAPElement will
     * be added a child element; the new element will have exactly the shape as represented by the state of this
     * object. Note that the {@link #flushTo(SOAPElement)} method does nothing
     * (and returns the argument immediately) if the state of this object is not initialized
     * (i.e. local name is null).
     * </p>
     *
     * @author ondrej.cerny@oracle.com
     */
    static class DeferredElement {
        private String prefix;
        private String localName;
        private String namespaceUri;
        private final List<NamespaceDeclaration> namespaceDeclarations;
        private final List<AttributeDeclaration> attributeDeclarations;

        DeferredElement() {
            this.namespaceDeclarations = new LinkedList<NamespaceDeclaration>();
            this.attributeDeclarations = new LinkedList<AttributeDeclaration>();
            reset();
        }


        /**
         * Set prefix of the element.
         * @param prefix namespace prefix
         */
        public void setPrefix(final String prefix) {
            this.prefix = prefix;
        }

        /**
         * Set local name of the element.
         *
         * <p>
         *     This method initializes the element.
         * </p>
         *
         * @param localName local name {@code not null}
         */
        public void setLocalName(final String localName) {
            if (localName == null) {
                throw new IllegalArgumentException("localName can not be null");
            }
            this.localName = localName;
        }

        /**
         * Set namespace uri.
         *
         * @param namespaceUri namespace uri
         */
        public void setNamespaceUri(final String namespaceUri) {
            this.namespaceUri = namespaceUri;
        }

        /**
         * Adds namespace prefix assignment to the element.
         *
         * @param prefix prefix (not {@code null})
         * @param namespaceUri namespace uri
         */
        public void addNamespaceDeclaration(final String prefix, final String namespaceUri) {
            if (null == this.namespaceUri && null != namespaceUri && prefix.equals(emptyIfNull(this.prefix))) {
                this.namespaceUri = namespaceUri;
            }
            this.namespaceDeclarations.add(new NamespaceDeclaration(prefix, namespaceUri));
        }

        /**
         * Adds attribute to the element.
         * @param prefix prefix
         * @param ns namespace
         * @param ln local name
         * @param value value
         */
        public void addAttribute(final String prefix, final String ns, final String ln, final String value) {
            if (ns == null && prefix == null && xmlns.equals(ln)) {
                this.addNamespaceDeclaration(prefix, value);
            } else {
                this.attributeDeclarations.add(new AttributeDeclaration(prefix, ns, ln, value));
            }
        }

        /**
         * Flushes state of this element to the {@code target} element.
         *
         * <p>
         * If this element is initialized then it is added with all the namespace declarations and attributes
         * to the {@code target} element as a child. The state of this element is reset to uninitialized.
         * The newly added element object is returned.
         * </p>
         * <p>
         * If this element is not initialized then the {@code target} is returned immediately, nothing else is done.
         * </p>
         *
         * @param target target element
         * @return {@code target} or new element
         * @throws XMLStreamException on error
         */
        public SOAPElement flushTo(final SOAPElement target) throws XMLStreamException {
            try {
                if (this.localName != null) {
                    // add the element appropriately (based on namespace declaration)
                    final SOAPElement newElement;
                    if (this.namespaceUri == null) {
                        // add element with inherited scope
                        newElement = target.addChildElement(this.localName);
                    } else if (prefix == null) {
                        newElement = target.addChildElement(new QName(this.namespaceUri, this.localName));
                    } else {
                        newElement = target.addChildElement(this.localName, this.prefix, this.namespaceUri);
                    }
                    // add namespace declarations
                    for (NamespaceDeclaration namespace : this.namespaceDeclarations) {
                        newElement.addNamespaceDeclaration(namespace.prefix, namespace.namespaceUri);
                    }
                    // add attribute declarations
                    for (AttributeDeclaration attribute : this.attributeDeclarations) {
                        addAttibuteToElement(newElement,
                                attribute.prefix, attribute.namespaceUri, attribute.localName, attribute.value);
                    }
                    // reset state
                    this.reset();

                    return newElement;
                } else {
                    return target;
                }
                // else after reset state -> not initialized
            } catch (SOAPException e) {
                throw new XMLStreamException(e);
            }
        }

        /**
         * Is the element initialized?
         * @return boolean indicating whether it was initialized after last flush
         */
        public boolean isInitialized() {
            return this.localName != null;
        }

        private void reset() {
            this.localName = null;
            this.prefix = null;
            this.namespaceUri = null;
            this.namespaceDeclarations.clear();
            this.attributeDeclarations.clear();
        }

        private static String emptyIfNull(String s) {
            return s == null ? "" : s;
        }
    }

    static class NamespaceDeclaration {
        final String prefix;
        final String namespaceUri;

        NamespaceDeclaration(String prefix, String namespaceUri) {
            this.prefix = prefix;
            this.namespaceUri = namespaceUri;
        }
    }

    static class AttributeDeclaration {
        final String prefix;
        final String namespaceUri;
        final String localName;
        final String value;

        AttributeDeclaration(String prefix, String namespaceUri, String localName, String value) {
            this.prefix = prefix;
            this.namespaceUri = namespaceUri;
            this.localName = localName;
            this.value = value;
        }
    }
}
