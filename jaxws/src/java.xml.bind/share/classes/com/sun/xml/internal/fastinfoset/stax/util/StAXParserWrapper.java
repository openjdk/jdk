/*
 * Copyright (c) 2004, 2012, Oracle and/or its affiliates. All rights reserved.
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
 *
 * THIS FILE WAS MODIFIED BY SUN MICROSYSTEMS, INC.
 */

package com.sun.xml.internal.fastinfoset.stax.util;

import javax.xml.namespace.QName;
import javax.xml.namespace.NamespaceContext;

import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;


public class StAXParserWrapper implements XMLStreamReader{
    private XMLStreamReader _reader;

    /** Creates a new instance of StAXParserWrapper */
    public StAXParserWrapper() {
    }

    public StAXParserWrapper(XMLStreamReader reader) {
        _reader = reader;
    }
    public void setReader(XMLStreamReader reader) {
        _reader = reader;
    }
    public XMLStreamReader getReader() {
        return _reader;
    }

    public int next() throws XMLStreamException
    {
        return _reader.next();
    }

    public int nextTag() throws XMLStreamException
    {
        return _reader.nextTag();
    }

    public String getElementText() throws XMLStreamException
    {
        return _reader.getElementText();
    }

    public void require(int type, String namespaceURI, String localName) throws XMLStreamException
    {
        _reader.require(type,namespaceURI,localName);
    }

    public boolean hasNext() throws XMLStreamException
    {
        return _reader.hasNext();
    }

    public void close() throws XMLStreamException
    {
        _reader.close();
    }

    public String getNamespaceURI(String prefix)
    {
        return _reader.getNamespaceURI(prefix);
    }

    public NamespaceContext getNamespaceContext() {
        return _reader.getNamespaceContext();
    }

    public boolean isStartElement() {
        return _reader.isStartElement();
    }

    public boolean isEndElement() {
        return _reader.isEndElement();
    }

    public boolean isCharacters() {
    return _reader.isCharacters();
    }

    public boolean isWhiteSpace() {
        return _reader.isWhiteSpace();
    }

    public QName getAttributeName(int index) {
        return _reader.getAttributeName(index);
    }

    public int getTextCharacters(int sourceStart, char[] target, int targetStart,
                               int length) throws XMLStreamException
    {
        return _reader.getTextCharacters(sourceStart, target, targetStart, length);
    }

    public String getAttributeValue(String namespaceUri,
                                  String localName)
    {
        return _reader.getAttributeValue(namespaceUri,localName);
    }
    public int getAttributeCount() {
        return _reader.getAttributeCount();
    }
    public String getAttributePrefix(int index) {
        return _reader.getAttributePrefix(index);
    }
    public String getAttributeNamespace(int index) {
        return _reader.getAttributeNamespace(index);
    }
    public String getAttributeLocalName(int index) {
        return _reader.getAttributeLocalName(index);
    }
    public String getAttributeType(int index) {
        return _reader.getAttributeType(index);
    }
    public String getAttributeValue(int index) {
        return _reader.getAttributeValue(index);
    }
    public boolean isAttributeSpecified(int index) {
        return _reader.isAttributeSpecified(index);
    }

    public int getNamespaceCount() {
        return _reader.getNamespaceCount();
    }
    public String getNamespacePrefix(int index) {
        return _reader.getNamespacePrefix(index);
    }
    public String getNamespaceURI(int index) {
        return _reader.getNamespaceURI(index);
    }

    public int getEventType() {
        return _reader.getEventType();
    }

    public String getText() {
        return _reader.getText();
    }

    public char[] getTextCharacters() {
        return _reader.getTextCharacters();
    }

    public int getTextStart() {
        return _reader.getTextStart();
    }

    public int getTextLength() {
        return _reader.getTextLength();
    }

    public String getEncoding() {
        return _reader.getEncoding();
    }

    public boolean hasText() {
        return _reader.hasText();
    }

    public Location getLocation() {
        return _reader.getLocation();
    }

    public QName getName() {
        return _reader.getName();
    }

    public String getLocalName() {
        return _reader.getLocalName();
    }

    public boolean hasName() {
        return _reader.hasName();
    }

    public String getNamespaceURI() {
        return _reader.getNamespaceURI();
    }

    public String getPrefix() {
        return _reader.getPrefix();
    }

    public String getVersion() {
        return _reader.getVersion();
    }

    public boolean isStandalone() {
        return _reader.isStandalone();
    }

    public boolean standaloneSet() {
        return _reader.standaloneSet();
    }

    public String getCharacterEncodingScheme() {
        return _reader.getCharacterEncodingScheme();
    }

    public String getPITarget() {
        return _reader.getPITarget();
    }

    public String getPIData() {
        return _reader.getPIData();
    }

    public Object getProperty(String name) {
        return _reader.getProperty(name);
    }
}
