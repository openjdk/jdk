/*
 * Copyright (c) 2005, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.stream.buffer.sax;

import com.sun.xml.internal.stream.buffer.AbstractCreator;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import com.sun.xml.internal.stream.buffer.MutableXMLStreamBuffer;
import java.io.IOException;
import java.io.InputStream;
import org.xml.sax.ContentHandler;
import org.xml.sax.DTDHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.LexicalHandler;

/**
 * Writes into {@link MutableXMLStreamBuffer} from SAX.
 *
 * TODO
 * Implement the marking the stream on the element when an ID
 * attribute on the element is defined
 */
public class SAXBufferCreator extends AbstractCreator
        implements EntityResolver, DTDHandler, ContentHandler, ErrorHandler, LexicalHandler {
    protected String[] _namespaceAttributes;

    protected int _namespaceAttributesPtr;

    private int depth = 0;

    public SAXBufferCreator() {
        _namespaceAttributes = new String[16 * 2];
    }

    public SAXBufferCreator(MutableXMLStreamBuffer buffer) {
        this();
        setBuffer(buffer);
    }

    public MutableXMLStreamBuffer create(XMLReader reader, InputStream in) throws IOException, SAXException {
        return create(reader, in, null);
    }

    public MutableXMLStreamBuffer create(XMLReader reader, InputStream in, String systemId) throws IOException, SAXException {
        if (_buffer == null) {
            createBuffer();
        }
        _buffer.setSystemId(systemId);
        reader.setContentHandler(this);
        reader.setProperty(Properties.LEXICAL_HANDLER_PROPERTY, this);

        try {
            setHasInternedStrings(reader.getFeature(Features.STRING_INTERNING_FEATURE));
        } catch (SAXException e) {
        }


        if (systemId != null) {
            InputSource s = new InputSource(systemId);
            s.setByteStream(in);
            reader.parse(s);
        } else {
            reader.parse(new InputSource(in));
        }

        return getXMLStreamBuffer();
    }

    public void reset() {
        _buffer = null;
        _namespaceAttributesPtr = 0;
        depth=0;
    }

    public void startDocument() throws SAXException {
        storeStructure(T_DOCUMENT);
    }

    public void endDocument() throws SAXException {
        storeStructure(T_END);
    }

    public void startPrefixMapping(String prefix, String uri) throws SAXException {
        cacheNamespaceAttribute(prefix, uri);
    }

    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        storeQualifiedName(T_ELEMENT_LN,
                uri, localName, qName);

        // Has namespaces attributes
        if (_namespaceAttributesPtr > 0) {
            storeNamespaceAttributes();
        }

        // Has attributes
        if (attributes.getLength() > 0) {
            storeAttributes(attributes);
        }
        depth++;
    }

    public void endElement(String uri, String localName, String qName) throws SAXException {
        storeStructure(T_END);
        if(--depth==0)
            increaseTreeCount();    // one tree processed
    }

    public void characters(char ch[], int start, int length) throws SAXException {
        storeContentCharacters(T_TEXT_AS_CHAR_ARRAY, ch, start, length);
    }

    public void ignorableWhitespace(char ch[], int start, int length) throws SAXException {
        characters(ch, start, length);
    }

    public void processingInstruction(String target, String data) throws SAXException {
        storeStructure(T_PROCESSING_INSTRUCTION);
        storeStructureString(target);
        storeStructureString(data);
    }

    public void comment(char[] ch, int start, int length) throws SAXException {
        storeContentCharacters(T_COMMENT_AS_CHAR_ARRAY, ch, start, length);
    }

    //

    private void cacheNamespaceAttribute(String prefix, String uri) {
        _namespaceAttributes[_namespaceAttributesPtr++] = prefix;
        _namespaceAttributes[_namespaceAttributesPtr++] = uri;

        if (_namespaceAttributesPtr == _namespaceAttributes.length) {
            final String[] namespaceAttributes = new String[_namespaceAttributesPtr * 2];
            System.arraycopy(_namespaceAttributes, 0, namespaceAttributes, 0, _namespaceAttributesPtr);
            _namespaceAttributes = namespaceAttributes;
        }
    }

    private void storeNamespaceAttributes() {
        for (int i = 0; i < _namespaceAttributesPtr; i += 2) {
            int item = T_NAMESPACE_ATTRIBUTE;
            if (_namespaceAttributes[i].length() > 0) {
                item |= FLAG_PREFIX;
                storeStructureString(_namespaceAttributes[i]);
            }
            if (_namespaceAttributes[i + 1].length() > 0) {
                item |= FLAG_URI;
                storeStructureString(_namespaceAttributes[i + 1]);
            }
            storeStructure(item);
        }
        _namespaceAttributesPtr = 0;
    }

    private void storeAttributes(Attributes attributes) {
        for (int i = 0; i < attributes.getLength(); i++) {
            // Skip NS attributes. Some versions of JDK seem to send wrong local name
            // Also it is not stored correctly by the following.
            if (attributes.getQName(i).startsWith("xmlns"))
                continue;
            storeQualifiedName(T_ATTRIBUTE_LN,
                    attributes.getURI(i),
                    attributes.getLocalName(i),
                    attributes.getQName(i));

            storeStructureString(attributes.getType(i));
            storeContentString(attributes.getValue(i));
        }
    }

    private void storeQualifiedName(int item, String uri, String localName, String qName) {
        if (uri.length() > 0) {
            item |= FLAG_URI;
            storeStructureString(uri);
        }

        storeStructureString(localName);

        if (qName.indexOf(':') >= 0) {
            item |= FLAG_QUALIFIED_NAME;
            storeStructureString(qName);
        }

        storeStructure(item);
    }


    // Empty methods for SAX handlers

    // Entity resolver handler

    public InputSource resolveEntity (String publicId, String systemId)
        throws IOException, SAXException
    {
        return null;
    }

    // DTD handler

    public void notationDecl (String name, String publicId, String systemId)
        throws SAXException
    { }

    public void unparsedEntityDecl (String name, String publicId,
                                    String systemId, String notationName)
        throws SAXException
    { }

    // Content handler

    public void setDocumentLocator (Locator locator) { }

    public void endPrefixMapping (String prefix) throws SAXException { }

    public void skippedEntity (String name) throws SAXException { }

    // Lexical handler

    public void startDTD(String name, String publicId, String systemId) throws SAXException { }

    public void endDTD() throws SAXException { }

    public void startEntity(String name) throws SAXException { }

    public void endEntity(String name) throws SAXException { }

    public void startCDATA() throws SAXException { }

    public void endCDATA() throws SAXException { }

    // Error handler

    public void warning(SAXParseException e) throws SAXException { }

    public void error(SAXParseException e) throws SAXException { }

    public void fatalError(SAXParseException e) throws SAXException
    {
        throw e;
    }
}
