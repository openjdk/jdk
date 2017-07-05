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
 *
 * THIS FILE WAS MODIFIED BY SUN MICROSYSTEMS, INC.
 */



package com.sun.xml.internal.fastinfoset.tools;

import com.sun.xml.internal.fastinfoset.QualifiedName;
import com.sun.xml.internal.fastinfoset.util.CharArray;
import com.sun.xml.internal.fastinfoset.util.DuplicateAttributeVerifier;
import com.sun.xml.internal.fastinfoset.util.KeyIntMap;
import com.sun.xml.internal.fastinfoset.util.LocalNameQualifiedNamesMap;
import com.sun.xml.internal.fastinfoset.util.PrefixArray;
import com.sun.xml.internal.fastinfoset.util.QualifiedNameArray;
import com.sun.xml.internal.fastinfoset.util.StringArray;
import com.sun.xml.internal.fastinfoset.util.StringIntMap;
import com.sun.xml.internal.fastinfoset.vocab.ParserVocabulary;
import com.sun.xml.internal.fastinfoset.vocab.SerializerVocabulary;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.helpers.DefaultHandler;
import com.sun.xml.internal.fastinfoset.CommonResourceBundle;
import java.util.Set;
import com.sun.xml.internal.org.jvnet.fastinfoset.FastInfosetSerializer;

public class VocabularyGenerator extends DefaultHandler implements LexicalHandler {

    protected SerializerVocabulary _serializerVocabulary;
    protected ParserVocabulary _parserVocabulary;
    protected com.sun.xml.internal.org.jvnet.fastinfoset.Vocabulary _v;

    protected int attributeValueSizeConstraint = FastInfosetSerializer.MAX_ATTRIBUTE_VALUE_SIZE;

    protected int characterContentChunkSizeContraint = FastInfosetSerializer.MAX_CHARACTER_CONTENT_CHUNK_SIZE;

    /** Creates a new instance of VocabularyGenerator */
    public VocabularyGenerator() {
        _serializerVocabulary = new SerializerVocabulary();
        _parserVocabulary = new ParserVocabulary();

        _v = new com.sun.xml.internal.org.jvnet.fastinfoset.Vocabulary();
    }

    public VocabularyGenerator(SerializerVocabulary serializerVocabulary) {
        _serializerVocabulary = serializerVocabulary;
        _parserVocabulary = new ParserVocabulary();

        _v = new com.sun.xml.internal.org.jvnet.fastinfoset.Vocabulary();
    }

    public VocabularyGenerator(ParserVocabulary parserVocabulary) {
        _serializerVocabulary = new SerializerVocabulary();
        _parserVocabulary = parserVocabulary;

        _v = new com.sun.xml.internal.org.jvnet.fastinfoset.Vocabulary();
    }

    /** Creates a new instance of VocabularyGenerator */
    public VocabularyGenerator(SerializerVocabulary serializerVocabulary, ParserVocabulary parserVocabulary) {
        _serializerVocabulary = serializerVocabulary;
        _parserVocabulary = parserVocabulary;

        _v = new com.sun.xml.internal.org.jvnet.fastinfoset.Vocabulary();
    }

    public com.sun.xml.internal.org.jvnet.fastinfoset.Vocabulary getVocabulary() {
        return _v;
    }

    public void setCharacterContentChunkSizeLimit(int size) {
        if (size < 0 ) {
            size = 0;
        }

        characterContentChunkSizeContraint = size;
    }

    public int getCharacterContentChunkSizeLimit() {
        return characterContentChunkSizeContraint;
    }

    public void setAttributeValueSizeLimit(int size) {
        if (size < 0 ) {
            size = 0;
        }

        attributeValueSizeConstraint = size;
    }

    public int getAttributeValueSizeLimit() {
        return attributeValueSizeConstraint;
    }

    // ContentHandler

    public void startDocument() throws SAXException {
    }

    public void endDocument() throws SAXException {
    }

    public void startPrefixMapping(String prefix, String uri) throws SAXException {
        addToTable(prefix, _v.prefixes, _serializerVocabulary.prefix, _parserVocabulary.prefix);
        addToTable(uri, _v.namespaceNames, _serializerVocabulary.namespaceName, _parserVocabulary.namespaceName);
    }

    public void endPrefixMapping(String prefix) throws SAXException {
    }

    public void startElement(String namespaceURI, String localName, String qName, Attributes atts) throws SAXException {
        addToNameTable(namespaceURI, qName, localName,
                _v.elements, _serializerVocabulary.elementName, _parserVocabulary.elementName, false);

        for (int a = 0; a < atts.getLength(); a++) {
            addToNameTable(atts.getURI(a), atts.getQName(a), atts.getLocalName(a),
                    _v.attributes, _serializerVocabulary.attributeName, _parserVocabulary.attributeName, true);

            String value = atts.getValue(a);
            if (value.length() < attributeValueSizeConstraint) {
                addToTable(value, _v.attributeValues, _serializerVocabulary.attributeValue, _parserVocabulary.attributeValue);
            }
        }
    }

    public void endElement(String namespaceURI, String localName, String qName) throws SAXException {
    }

    public void characters(char[] ch, int start, int length) throws SAXException {
        if (length < characterContentChunkSizeContraint) {
            addToCharArrayTable(new CharArray(ch, start, length, true));
        }
    }

    public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
    }

    public void processingInstruction(String target, String data) throws SAXException {
    }

    public void setDocumentLocator(org.xml.sax.Locator locator) {
    }

    public void skippedEntity(String name) throws SAXException {
    }



    // LexicalHandler

    public void comment(char[] ch, int start, int length) throws SAXException {
    }

    public void startCDATA() throws SAXException {
    }

    public void endCDATA() throws SAXException {
    }

    public void startDTD(String name, String publicId, String systemId) throws SAXException {
    }

    public void endDTD() throws SAXException {
    }

    public void startEntity(String name) throws SAXException {
    }

    public void endEntity(String name) throws SAXException {
    }


    public void addToTable(String s, Set v, StringIntMap m, StringArray a) {
        if (s.length() == 0) {
            return;
        }

        if (m.obtainIndex(s) == KeyIntMap.NOT_PRESENT) {
            a.add(s);
        }

        v.add(s);
    }

    public void addToTable(String s, Set v, StringIntMap m, PrefixArray a) {
        if (s.length() == 0) {
            return;
        }

        if (m.obtainIndex(s) == KeyIntMap.NOT_PRESENT) {
            a.add(s);
        }

        v.add(s);
    }

    public void addToCharArrayTable(CharArray c) {
        if (_serializerVocabulary.characterContentChunk.obtainIndex(c.ch, c.start, c.length, false) == KeyIntMap.NOT_PRESENT) {
            _parserVocabulary.characterContentChunk.add(c.ch, c.length);
        }

        _v.characterContentChunks.add(c.toString());
    }

    public void addToNameTable(String namespaceURI, String qName, String localName,
            Set v, LocalNameQualifiedNamesMap m, QualifiedNameArray a,
            boolean isAttribute) throws SAXException {
        LocalNameQualifiedNamesMap.Entry entry = m.obtainEntry(qName);
        if (entry._valueIndex > 0) {
            QualifiedName[] names = entry._value;
            for (int i = 0; i < entry._valueIndex; i++) {
                if ((namespaceURI == names[i].namespaceName || namespaceURI.equals(names[i].namespaceName))) {
                    return;
                }
            }
        }

        String prefix = getPrefixFromQualifiedName(qName);

        int namespaceURIIndex = -1;
        int prefixIndex = -1;
        int localNameIndex = -1;
        if (namespaceURI.length() > 0) {
            namespaceURIIndex = _serializerVocabulary.namespaceName.get(namespaceURI);
            if (namespaceURIIndex == KeyIntMap.NOT_PRESENT) {
                throw new SAXException(CommonResourceBundle.getInstance().
                        getString("message.namespaceURINotIndexed", new Object[]{Integer.valueOf(namespaceURIIndex)}));
            }

            if (prefix.length() > 0) {
                prefixIndex = _serializerVocabulary.prefix.get(prefix);
                if (prefixIndex == KeyIntMap.NOT_PRESENT) {
                    throw new SAXException(CommonResourceBundle.getInstance().
                            getString("message.prefixNotIndexed", new Object[]{Integer.valueOf(prefixIndex)}));
                }
            }
        }

        localNameIndex = _serializerVocabulary.localName.obtainIndex(localName);
        if (localNameIndex == KeyIntMap.NOT_PRESENT) {
            _parserVocabulary.localName.add(localName);
            localNameIndex = _parserVocabulary.localName.getSize() - 1;
        }
        QualifiedName name = new QualifiedName(prefix, namespaceURI, localName, m.getNextIndex(),
                prefixIndex, namespaceURIIndex, localNameIndex);
        if (isAttribute) {
            name.createAttributeValues(DuplicateAttributeVerifier.MAP_SIZE);
        }
        entry.addQualifiedName(name);
        a.add(name);

        v.add(name.getQName());
    }

    public static String getPrefixFromQualifiedName(String qName) {
        int i = qName.indexOf(':');
        String prefix = "";
        if (i != -1) {
            prefix = qName.substring(0, i);
        }
        return prefix;
    }

}
