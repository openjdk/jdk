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

import com.sun.xml.internal.ws.api.streaming.XMLStreamReaderFactory;

import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * {@link XMLStreamReader} that delegates to another {@link XMLStreamReader}.
 *
 * <p>
 * This class isn't very useful by itself, but works as a base class
 * for {@link XMLStreamReader} filtering.
 *
 * @author Kohsuke Kawaguchi
 */
public class XMLStreamReaderFilter implements XMLStreamReaderFactory.RecycleAware, XMLStreamReader {
    /**
     * The underlying {@link XMLStreamReader} that does the parsing of the root part.
     */
    protected XMLStreamReader reader;

    public XMLStreamReaderFilter(XMLStreamReader core) {
        this.reader = core;
    }

    public void onRecycled() {
        XMLStreamReaderFactory.recycle(reader);
        reader = null;
    }

    public int getAttributeCount() {
        return reader.getAttributeCount();
    }

    public int getEventType() {
        return reader.getEventType();
    }

    public int getNamespaceCount() {
        return reader.getNamespaceCount();
    }

    public int getTextLength() {
        return reader.getTextLength();
    }

    public int getTextStart() {
        return reader.getTextStart();
    }

    public int next() throws XMLStreamException {
        return reader.next();
    }

    public int nextTag() throws XMLStreamException {
        return reader.nextTag();
    }

    public void close() throws XMLStreamException {
        reader.close();
    }

    public boolean hasName() {
        return reader.hasName();
    }

    public boolean hasNext() throws XMLStreamException {
        return reader.hasNext();
    }

    public boolean hasText() {
        return reader.hasText();
    }

    public boolean isCharacters() {
        return reader.isCharacters();
    }

    public boolean isEndElement() {
        return reader.isEndElement();
    }

    public boolean isStandalone() {
        return reader.isStandalone();
    }

    public boolean isStartElement() {
        return reader.isStartElement();
    }

    public boolean isWhiteSpace() {
        return reader.isWhiteSpace();
    }

    public boolean standaloneSet() {
        return reader.standaloneSet();
    }

    public char[] getTextCharacters() {
        return reader.getTextCharacters();
    }

    public boolean isAttributeSpecified(int index) {
        return reader.isAttributeSpecified(index);
    }

    public int getTextCharacters(int sourceStart, char[] target, int targetStart, int length) throws XMLStreamException {
        return reader.getTextCharacters(sourceStart, target, targetStart, length);
    }

    public String getCharacterEncodingScheme() {
        return reader.getCharacterEncodingScheme();
    }

    public String getElementText() throws XMLStreamException {
        return reader.getElementText();
    }

    public String getEncoding() {
        return reader.getEncoding();
    }

    public String getLocalName() {
        return reader.getLocalName();
    }

    public String getNamespaceURI() {
        return reader.getNamespaceURI();
    }

    public String getPIData() {
        return reader.getPIData();
    }

    public String getPITarget() {
        return reader.getPITarget();
    }

    public String getPrefix() {
        return reader.getPrefix();
    }

    public String getText() {
        return reader.getText();
    }

    public String getVersion() {
        return reader.getVersion();
    }

    public String getAttributeLocalName(int index) {
        return reader.getAttributeLocalName(index);
    }

    public String getAttributeNamespace(int index) {
        return reader.getAttributeNamespace(index);
    }

    public String getAttributePrefix(int index) {
        return reader.getAttributePrefix(index);
    }

    public String getAttributeType(int index) {
        return reader.getAttributeType(index);
    }

    public String getAttributeValue(int index) {
        return reader.getAttributeValue(index);
    }

    public String getNamespacePrefix(int index) {
        return reader.getNamespacePrefix(index);
    }

    public String getNamespaceURI(int index) {
        return reader.getNamespaceURI(index);
    }

    public NamespaceContext getNamespaceContext() {
        return reader.getNamespaceContext();
    }

    public QName getName() {
        return reader.getName();
    }

    public QName getAttributeName(int index) {
        return reader.getAttributeName(index);
    }

    public Location getLocation() {
        return reader.getLocation();
    }

    public Object getProperty(String name) throws IllegalArgumentException {
        return reader.getProperty(name);
    }

    public void require(int type, String namespaceURI, String localName) throws XMLStreamException {
        reader.require(type, namespaceURI, localName);
    }

    public String getNamespaceURI(String prefix) {
        return reader.getNamespaceURI(prefix);
    }

    public String getAttributeValue(String namespaceURI, String localName) {
        return reader.getAttributeValue(namespaceURI, localName);
    }
}
