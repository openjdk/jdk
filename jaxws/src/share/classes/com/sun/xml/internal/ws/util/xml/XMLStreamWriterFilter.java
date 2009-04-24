/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.xml.internal.ws.util.xml;

import com.sun.xml.internal.ws.api.streaming.XMLStreamWriterFactory;
import com.sun.xml.internal.ws.api.streaming.XMLStreamWriterFactory.RecycleAware;

import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 * {@link XMLStreamWriter} that delegates to another {@link XMLStreamWriter}.
 *
 * <p>
 * This class isn't very useful by itself, but works as a base class
 * for {@link XMLStreamWriter} filtering.
 *
 * @author Kohsuke Kawaguchi
 */
public class XMLStreamWriterFilter implements XMLStreamWriter, RecycleAware {
    protected XMLStreamWriter writer;

    public XMLStreamWriterFilter(XMLStreamWriter writer) {
        this.writer = writer;
    }

    public void close() throws XMLStreamException {
        writer.close();
    }

    public void flush() throws XMLStreamException {
        writer.flush();
    }

    public void writeEndDocument() throws XMLStreamException {
        writer.writeEndDocument();
    }

    public void writeEndElement() throws XMLStreamException {
        writer.writeEndElement();
    }

    public void writeStartDocument() throws XMLStreamException {
        writer.writeStartDocument();
    }

    public void writeCharacters(char[] text, int start, int len) throws XMLStreamException {
        writer.writeCharacters(text, start, len);
    }

    public void setDefaultNamespace(String uri) throws XMLStreamException {
        writer.setDefaultNamespace(uri);
    }

    public void writeCData(String data) throws XMLStreamException {
        writer.writeCData(data);
    }

    public void writeCharacters(String text) throws XMLStreamException {
        writer.writeCharacters(text);
    }

    public void writeComment(String data) throws XMLStreamException {
        writer.writeComment(data);
    }

    public void writeDTD(String dtd) throws XMLStreamException {
        writer.writeDTD(dtd);
    }

    public void writeDefaultNamespace(String namespaceURI) throws XMLStreamException {
        writer.writeDefaultNamespace(namespaceURI);
    }

    public void writeEmptyElement(String localName) throws XMLStreamException {
        writer.writeEmptyElement(localName);
    }

    public void writeEntityRef(String name) throws XMLStreamException {
        writer.writeEntityRef(name);
    }

    public void writeProcessingInstruction(String target) throws XMLStreamException {
        writer.writeProcessingInstruction(target);
    }

    public void writeStartDocument(String version) throws XMLStreamException {
        writer.writeStartDocument(version);
    }

    public void writeStartElement(String localName) throws XMLStreamException {
        writer.writeStartElement(localName);
    }

    public NamespaceContext getNamespaceContext() {
        return writer.getNamespaceContext();
    }

    public void setNamespaceContext(NamespaceContext context) throws XMLStreamException {
        writer.setNamespaceContext(context);
    }

    public Object getProperty(String name) throws IllegalArgumentException {
        return writer.getProperty(name);
    }

    public String getPrefix(String uri) throws XMLStreamException {
        return writer.getPrefix(uri);
    }

    public void setPrefix(String prefix, String uri) throws XMLStreamException {
        writer.setPrefix(prefix, uri);
    }

    public void writeAttribute(String localName, String value) throws XMLStreamException {
        writer.writeAttribute(localName, value);
    }

    public void writeEmptyElement(String namespaceURI, String localName) throws XMLStreamException {
        writer.writeEmptyElement(namespaceURI, localName);
    }

    public void writeNamespace(String prefix, String namespaceURI) throws XMLStreamException {
        writer.writeNamespace(prefix, namespaceURI);
    }

    public void writeProcessingInstruction(String target, String data) throws XMLStreamException {
        writer.writeProcessingInstruction(target, data);
    }

    public void writeStartDocument(String encoding, String version) throws XMLStreamException {
        writer.writeStartDocument(encoding, version);
    }

    public void writeStartElement(String namespaceURI, String localName) throws XMLStreamException {
        writer.writeStartElement(namespaceURI, localName);
    }

    public void writeAttribute(String namespaceURI, String localName, String value) throws XMLStreamException {
        writer.writeAttribute(namespaceURI, localName, value);
    }

    public void writeEmptyElement(String prefix, String localName, String namespaceURI) throws XMLStreamException {
        writer.writeEmptyElement(prefix, localName, namespaceURI);
    }

    public void writeStartElement(String prefix, String localName, String namespaceURI) throws XMLStreamException {
        writer.writeStartElement(prefix, localName, namespaceURI);
    }

    public void writeAttribute(String prefix, String namespaceURI, String localName, String value) throws XMLStreamException {
        writer.writeAttribute(prefix, namespaceURI, localName, value);
    }

    public void onRecycled() {
        XMLStreamWriterFactory.recycle(writer);
        writer = null;
    }
}
