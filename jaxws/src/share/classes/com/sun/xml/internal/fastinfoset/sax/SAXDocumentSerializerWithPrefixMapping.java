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

package com.sun.xml.internal.fastinfoset.sax;

import com.sun.xml.internal.fastinfoset.EncodingConstants;
import com.sun.xml.internal.fastinfoset.QualifiedName;
import com.sun.xml.internal.fastinfoset.util.KeyIntMap;
import com.sun.xml.internal.fastinfoset.util.LocalNameQualifiedNamesMap;
import com.sun.xml.internal.fastinfoset.util.StringIntMap;
import java.io.IOException;
import java.util.HashMap;
import org.xml.sax.SAXException;
import java.util.Map;
import com.sun.xml.internal.org.jvnet.fastinfoset.FastInfosetException;
import com.sun.xml.internal.org.jvnet.fastinfoset.RestrictedAlphabet;
import com.sun.xml.internal.org.jvnet.fastinfoset.sax.EncodingAlgorithmAttributes;
import org.xml.sax.Attributes;

/**
 * The Fast Infoset SAX serializer that maps prefixes to user specified prefixes
 * that are specified in a namespace URI to prefix map.
 * <p>
 * This serializer will not preserve the original prefixes and this serializer
 * should not be used when prefixes need to be preserved, such as the case
 * when there are qualified names in content.
 * <p>
 * A namespace URI to prefix map is utilized such that the prefixes
 * in the map are utilized rather than the prefixes specified in
 * the qualified name for elements and attributes.
 * <p>
 * Any namespace declarations with a namespace URI that is not present in
 * the map are added.
 * <p>
 */
public class SAXDocumentSerializerWithPrefixMapping extends SAXDocumentSerializer {
    protected Map _namespaceToPrefixMapping;
    protected Map _prefixToPrefixMapping;
    protected String _lastCheckedNamespace;
    protected String _lastCheckedPrefix;

    protected StringIntMap _declaredNamespaces;

    public SAXDocumentSerializerWithPrefixMapping(Map namespaceToPrefixMapping) {
        // Use the local name to look up elements/attributes
        super(true);
        _namespaceToPrefixMapping = new HashMap(namespaceToPrefixMapping);
        _prefixToPrefixMapping = new HashMap();

        // Empty prefix
        _namespaceToPrefixMapping.put("", "");
        // 'xml' prefix
        _namespaceToPrefixMapping.put(EncodingConstants.XML_NAMESPACE_NAME, EncodingConstants.XML_NAMESPACE_PREFIX);

        _declaredNamespaces = new StringIntMap(4);
    }

    public final void startPrefixMapping(String prefix, String uri) throws SAXException {
        try {
            if (_elementHasNamespaces == false) {
                encodeTermination();

                // Mark the current buffer position to flag attributes if necessary
                mark();
                _elementHasNamespaces = true;

                // Write out Element byte with namespaces
                write(EncodingConstants.ELEMENT | EncodingConstants.ELEMENT_NAMESPACES_FLAG);

                _declaredNamespaces.clear();
                _declaredNamespaces.obtainIndex(uri);
            } else {
                if (_declaredNamespaces.obtainIndex(uri) != KeyIntMap.NOT_PRESENT) {
                    final String p = getPrefix(uri);
                    if (p != null) {
                        _prefixToPrefixMapping.put(prefix, p);
                    }
                    return;
                }
            }

            final String p = getPrefix(uri);
            if (p != null) {
                encodeNamespaceAttribute(p, uri);
                _prefixToPrefixMapping.put(prefix, p);
            } else {
                putPrefix(uri, prefix);
                encodeNamespaceAttribute(prefix, uri);
            }

        } catch (IOException e) {
            throw new SAXException("startElement", e);
        }
    }

    protected final void encodeElement(String namespaceURI, String qName, String localName) throws IOException {
        LocalNameQualifiedNamesMap.Entry entry = _v.elementName.obtainEntry(localName);
        if (entry._valueIndex > 0) {
            if (encodeElementMapEntry(entry, namespaceURI)) return;
            // Check the entry is a member of the read only map
            if (_v.elementName.isQNameFromReadOnlyMap(entry._value[0])) {
                entry = _v.elementName.obtainDynamicEntry(localName);
                if (entry._valueIndex > 0) {
                    if (encodeElementMapEntry(entry, namespaceURI)) return;
                }
            }
        }

        encodeLiteralElementQualifiedNameOnThirdBit(namespaceURI, getPrefix(namespaceURI),
                localName, entry);
    }

    protected boolean encodeElementMapEntry(LocalNameQualifiedNamesMap.Entry entry, String namespaceURI) throws IOException {
        QualifiedName[] names = entry._value;
        for (int i = 0; i < entry._valueIndex; i++) {
            if ((namespaceURI == names[i].namespaceName || namespaceURI.equals(names[i].namespaceName))) {
                encodeNonZeroIntegerOnThirdBit(names[i].index);
                return true;
            }
        }
        return false;
    }


    protected final void encodeAttributes(Attributes atts) throws IOException, FastInfosetException {
        boolean addToTable;
        String value;
        if (atts instanceof EncodingAlgorithmAttributes) {
            final EncodingAlgorithmAttributes eAtts = (EncodingAlgorithmAttributes)atts;
            Object data;
            String alphabet;
            for (int i = 0; i < eAtts.getLength(); i++) {
                final String uri = atts.getURI(i);
                if (encodeAttribute(uri, atts.getQName(i), atts.getLocalName(i))) {
                    data = eAtts.getAlgorithmData(i);
                    // If data is null then there is no algorithm data
                    if (data == null) {
                        value = eAtts.getValue(i);
                        addToTable = eAtts.getToIndex(i) || isAttributeValueLengthMatchesLimit(value.length());

                        alphabet = eAtts.getAlpababet(i);
                        if (alphabet == null) {
                            if (uri == "http://www.w3.org/2001/XMLSchema-instance" ||
                                    uri.equals("http://www.w3.org/2001/XMLSchema-instance")) {
                                value = convertQName(value);
                            }
                            encodeNonIdentifyingStringOnFirstBit(value, _v.attributeValue, addToTable);
                        } else if (alphabet == RestrictedAlphabet.DATE_TIME_CHARACTERS)
                            encodeNonIdentifyingStringOnFirstBit(
                                    RestrictedAlphabet.DATE_TIME_CHARACTERS_INDEX,
                                    DATE_TIME_CHARACTERS_TABLE,
                                    value, addToTable);
                        else if (alphabet == RestrictedAlphabet.DATE_TIME_CHARACTERS)
                            encodeNonIdentifyingStringOnFirstBit(
                                    RestrictedAlphabet.NUMERIC_CHARACTERS_INDEX,
                                    NUMERIC_CHARACTERS_TABLE,
                                    value, addToTable);
                        else
                            encodeNonIdentifyingStringOnFirstBit(value, _v.attributeValue, addToTable);

                    } else {
                        encodeNonIdentifyingStringOnFirstBit(eAtts.getAlgorithmURI(i),
                                eAtts.getAlgorithmIndex(i), data);
                    }
                }
            }
        } else {
            for (int i = 0; i < atts.getLength(); i++) {
                final String uri = atts.getURI(i);
                if (encodeAttribute(atts.getURI(i), atts.getQName(i), atts.getLocalName(i))) {
                    value = atts.getValue(i);
                    addToTable = isAttributeValueLengthMatchesLimit(value.length());

                    if (uri == "http://www.w3.org/2001/XMLSchema-instance" ||
                            uri.equals("http://www.w3.org/2001/XMLSchema-instance")) {
                        value = convertQName(value);
                    }
                    encodeNonIdentifyingStringOnFirstBit(value, _v.attributeValue, addToTable);
                }
            }
        }
        _b = EncodingConstants.TERMINATOR;
        _terminate = true;
    }

    private String convertQName(String qName) {
        int i = qName.indexOf(':');
        String prefix = "";
        String localName = qName;
        if (i != -1) {
            prefix = qName.substring(0, i);
            localName = qName.substring(i + 1);
        }

        String p = (String)_prefixToPrefixMapping.get(prefix);
        if (p != null) {
            if (p.length() == 0)
                return localName;
            else
                return p + ":" + localName;
        } else {
            return qName;
        }
    }

    protected final boolean encodeAttribute(String namespaceURI, String qName, String localName) throws IOException {
        LocalNameQualifiedNamesMap.Entry entry = _v.attributeName.obtainEntry(localName);
        if (entry._valueIndex > 0) {
            if (encodeAttributeMapEntry(entry, namespaceURI)) return true;
            // Check the entry is a member of the read only map
            if (_v.attributeName.isQNameFromReadOnlyMap(entry._value[0])) {
                entry = _v.attributeName.obtainDynamicEntry(localName);
                if (entry._valueIndex > 0) {
                    if (encodeAttributeMapEntry(entry, namespaceURI)) return true;
                }
            }
        }

        return encodeLiteralAttributeQualifiedNameOnSecondBit(namespaceURI, getPrefix(namespaceURI),
                localName, entry);
    }

    protected boolean encodeAttributeMapEntry(LocalNameQualifiedNamesMap.Entry entry, String namespaceURI) throws IOException {
        QualifiedName[] names = entry._value;
        for (int i = 0; i < entry._valueIndex; i++) {
            if ((namespaceURI == names[i].namespaceName || namespaceURI.equals(names[i].namespaceName))) {
                encodeNonZeroIntegerOnSecondBitFirstBitZero(names[i].index);
                return true;
            }
        }
        return false;
    }

    protected final String getPrefix(String namespaceURI) {
        if (_lastCheckedNamespace == namespaceURI) return _lastCheckedPrefix;

        _lastCheckedNamespace = namespaceURI;
        return _lastCheckedPrefix = (String)_namespaceToPrefixMapping.get(namespaceURI);
    }

    protected final void putPrefix(String namespaceURI, String prefix) {
        _namespaceToPrefixMapping.put(namespaceURI, prefix);

        _lastCheckedNamespace = namespaceURI;
        _lastCheckedPrefix = prefix;
    }
}
