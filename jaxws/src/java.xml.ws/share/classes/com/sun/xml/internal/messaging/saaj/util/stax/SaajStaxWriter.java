/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.util.Iterator;

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
 * @author shih-chang.chen@oracle.com
 */
public class SaajStaxWriter implements XMLStreamWriter {

    protected SOAPMessage soap;
    protected String envURI;
    protected SOAPElement currentElement;

    static final protected String Envelope = "Envelope";
    static final protected String Header = "Header";
    static final protected String Body = "Body";
    static final protected String xmlns = "xmlns";

    public SaajStaxWriter(final SOAPMessage msg, String uri) throws SOAPException {
        soap = msg;
        this.envURI = uri;
    }

    public SOAPMessage getSOAPMessage() {
        return soap;
    }

    protected SOAPElement getEnvelope() throws SOAPException {
        return soap.getSOAPPart().getEnvelope();
    }

    @Override
    public void writeStartElement(final String localName) throws XMLStreamException {
        try {
            currentElement = currentElement.addChildElement(localName);
        } catch (SOAPException e) {
            throw new XMLStreamException(e);
        }
    }

    @Override
    public void writeStartElement(final String ns, final String ln) throws XMLStreamException {
        writeStartElement(null, ln, ns);
    }

    @Override
    public void writeStartElement(final String prefix, final String ln, final String ns) throws XMLStreamException {
        try {
            if (envURI.equals(ns)) {
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
            }
            currentElement = (prefix == null) ?
                    currentElement.addChildElement(new QName(ns, ln)) :
                    currentElement.addChildElement(ln, prefix, ns);
        } catch (SOAPException e) {
            throw new XMLStreamException(e);
        }
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
        if (currentElement != null) currentElement = currentElement.getParentElement();
    }

    @Override
    public void writeEndDocument() throws XMLStreamException {
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
        try {
            if (ns == null) {
                if (prefix == null && xmlns.equals(ln)) {
                    currentElement.addNamespaceDeclaration("", value);
                } else {
                    currentElement.setAttributeNS("", ln, value);
                }
            } else {
                QName name = (prefix == null) ? new QName(ns, ln) : new QName(ns, ln, prefix);
                currentElement.addAttribute(name, value);
            }
        } catch (SOAPException e) {
            throw new XMLStreamException(e);
        }
    }

    @Override
    public void writeAttribute(final String ns, final String ln, final String val) throws XMLStreamException {
        writeAttribute(null, ns, ln, val);
    }

    @Override
    public void writeNamespace(String prefix, final String uri) throws XMLStreamException {

        // make prefix default if null or "xmlns" (according to javadoc)
        if (prefix == null || "xmlns".equals(prefix)) {
            prefix = "";
        }

        try {
            currentElement.addNamespaceDeclaration(prefix, uri);
        } catch (SOAPException e) {
            throw new XMLStreamException(e);
        }
    }

    @Override
    public void writeDefaultNamespace(final String uri) throws XMLStreamException {
        writeNamespace("", uri);
    }

    @Override
    public void writeComment(final String data) throws XMLStreamException {
        Comment c = soap.getSOAPPart().createComment(data);
        currentElement.appendChild(c);
    }

    @Override
    public void writeProcessingInstruction(final String target) throws XMLStreamException {
        Node n = soap.getSOAPPart().createProcessingInstruction(target, "");
        currentElement.appendChild(n);
    }

    @Override
    public void writeProcessingInstruction(final String target, final String data) throws XMLStreamException {
        Node n = soap.getSOAPPart().createProcessingInstruction(target, data);
        currentElement.appendChild(n);
    }

    @Override
    public void writeCData(final String data) throws XMLStreamException {
        Node n = soap.getSOAPPart().createCDATASection(data);
        currentElement.appendChild(n);
    }

    @Override
    public void writeDTD(final String dtd) throws XMLStreamException {
        //TODO ... Don't do anything here
    }

    @Override
    public void writeEntityRef(final String name) throws XMLStreamException {
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
        try {
            currentElement.addTextNode(text);
        } catch (SOAPException e) {
            throw new XMLStreamException(e);
        }
    }

    @Override
    public void writeCharacters(final char[] text, final int start, final int len) throws XMLStreamException {
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
        try {
            this.currentElement.addNamespaceDeclaration(prefix, uri);
        } catch (SOAPException e) {
            throw new XMLStreamException(e);
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
            public String getNamespaceURI(final String prefix) {
                return currentElement.getNamespaceURI(prefix);
            }
            public String getPrefix(final String namespaceURI) {
                return currentElement.lookupPrefix(namespaceURI);
            }
            public Iterator getPrefixes(final String namespaceURI) {
                return new Iterator<String>() {
                    String prefix = getPrefix(namespaceURI);
                    public boolean hasNext() {
                        return (prefix != null);
                    }
                    public String next() {
                        if (!hasNext()) throw new java.util.NoSuchElementException();
                        String next = prefix;
                        prefix = null;
                        return next;
                    }
                    public void remove() {}
                };
            }
        };
    }
}
