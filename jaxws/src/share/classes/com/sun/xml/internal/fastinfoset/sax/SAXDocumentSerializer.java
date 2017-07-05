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

import com.sun.xml.internal.fastinfoset.Encoder;
import com.sun.xml.internal.fastinfoset.EncodingConstants;
import com.sun.xml.internal.fastinfoset.QualifiedName;
import com.sun.xml.internal.org.jvnet.fastinfoset.sax.FastInfosetWriter;
import com.sun.xml.internal.fastinfoset.util.LocalNameQualifiedNamesMap;
import java.io.IOException;
import com.sun.xml.internal.org.jvnet.fastinfoset.EncodingAlgorithmIndexes;
import com.sun.xml.internal.org.jvnet.fastinfoset.FastInfosetException;
import com.sun.xml.internal.org.jvnet.fastinfoset.RestrictedAlphabet;
import com.sun.xml.internal.org.jvnet.fastinfoset.sax.EncodingAlgorithmAttributes;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import com.sun.xml.internal.fastinfoset.CommonResourceBundle;

/**
 * The Fast Infoset SAX serializer.
 * <p>
 * Instantiate this serializer to serialize a fast infoset document in accordance
 * with the SAX API.
 * <p>
 * This utilizes the SAX API in a reverse manner to that of parsing. It is the
 * responsibility of the client to call the appropriate event methods on the
 * SAX handlers, and to ensure that such a sequence of methods calls results
 * in the production well-formed fast infoset documents. The
 * SAXDocumentSerializer performs no well-formed checks.
 *
 * <p>
 * More than one fast infoset document may be encoded to the
 * {@link java.io.OutputStream}.
 */
public class SAXDocumentSerializer extends Encoder implements FastInfosetWriter {
    protected boolean _elementHasNamespaces = false;

    protected boolean _charactersAsCDATA = false;

    protected SAXDocumentSerializer(boolean v) {
        super(v);
    }

    public SAXDocumentSerializer() {
    }


    public void reset() {
        super.reset();

        _elementHasNamespaces = false;
        _charactersAsCDATA = false;
    }

    // ContentHandler

    public final void startDocument() throws SAXException {
        try {
            reset();
            encodeHeader(false);
            encodeInitialVocabulary();
        } catch (IOException e) {
            throw new SAXException("startDocument", e);
        }
    }

    public final void endDocument() throws SAXException {
        try {
            encodeDocumentTermination();
        } catch (IOException e) {
            throw new SAXException("endDocument", e);
        }
    }

    public void startPrefixMapping(String prefix, String uri) throws SAXException {
        try {
            if (_elementHasNamespaces == false) {
                encodeTermination();

                // Mark the current buffer position to flag attributes if necessary
                mark();
                _elementHasNamespaces = true;

                // Write out Element byte with namespaces
                write(EncodingConstants.ELEMENT | EncodingConstants.ELEMENT_NAMESPACES_FLAG);
            }

            encodeNamespaceAttribute(prefix, uri);
        } catch (IOException e) {
            throw new SAXException("startElement", e);
        }
    }

    public final void startElement(String namespaceURI, String localName, String qName, Attributes atts) throws SAXException {
        // TODO consider using buffer for encoding of attributes, then pre-counting is not necessary
        final int attributeCount = (atts != null && atts.getLength() > 0)
                ? countAttributes(atts) : 0;
        try {
            if (_elementHasNamespaces) {
                _elementHasNamespaces = false;

                if (attributeCount > 0) {
                    // Flag the marked byte with attributes
                    _octetBuffer[_markIndex] |= EncodingConstants.ELEMENT_ATTRIBUTE_FLAG;
                }
                resetMark();

                write(EncodingConstants.TERMINATOR);

                _b = 0;
            } else {
                encodeTermination();

                _b = EncodingConstants.ELEMENT;
                if (attributeCount > 0) {
                    _b |= EncodingConstants.ELEMENT_ATTRIBUTE_FLAG;
                }
            }

            encodeElement(namespaceURI, qName, localName);

            if (attributeCount > 0) {
                encodeAttributes(atts);
            }
        } catch (IOException e) {
            throw new SAXException("startElement", e);
        } catch (FastInfosetException e) {
            throw new SAXException("startElement", e);
        }
    }

    public final void endElement(String namespaceURI, String localName, String qName) throws SAXException {
        try {
            encodeElementTermination();
        } catch (IOException e) {
            throw new SAXException("endElement", e);
        }
    }

    public final void characters(char[] ch, int start, int length) throws SAXException {
        if (length <= 0) {
            return;
        }

        if (getIgnoreWhiteSpaceTextContent() &&
                isWhiteSpace(ch, start, length)) return;

        try {
            encodeTermination();

            if (!_charactersAsCDATA) {
                encodeCharacters(ch, start, length);
            } else {
                encodeCIIBuiltInAlgorithmDataAsCDATA(ch, start, length);
            }
        } catch (IOException e) {
            throw new SAXException(e);
        } catch (FastInfosetException e) {
            throw new SAXException(e);
        }
    }

    public final void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
        if (getIgnoreWhiteSpaceTextContent()) return;

        characters(ch, start, length);
    }

    public final void processingInstruction(String target, String data) throws SAXException {
        try {
            if (getIgnoreProcesingInstructions()) return;

            if (target.length() == 0) {
                throw new SAXException(CommonResourceBundle.getInstance().
                        getString("message.processingInstructionTargetIsEmpty"));
            }
            encodeTermination();

            encodeProcessingInstruction(target, data);
        } catch (IOException e) {
            throw new SAXException("processingInstruction", e);
        }
    }

    public final void setDocumentLocator(org.xml.sax.Locator locator) {
    }

    public final void skippedEntity(String name) throws SAXException {
    }



    // LexicalHandler

    public final void comment(char[] ch, int start, int length) throws SAXException {
        try {
            if (getIgnoreComments()) return;

            encodeTermination();

            encodeComment(ch, start, length);
        } catch (IOException e) {
            throw new SAXException("startElement", e);
        }
    }

    public final void startCDATA() throws SAXException {
        _charactersAsCDATA = true;
    }

    public final void endCDATA() throws SAXException {
        _charactersAsCDATA = false;
    }

    public final void startDTD(String name, String publicId, String systemId) throws SAXException {
        if (getIgnoreDTD()) return;

        try {
            encodeTermination();

            encodeDocumentTypeDeclaration(publicId, systemId);
            encodeElementTermination();
        } catch (IOException e) {
            throw new SAXException("startDTD", e);
        }
    }

    public final void endDTD() throws SAXException {
    }

    public final void startEntity(String name) throws SAXException {
    }

    public final void endEntity(String name) throws SAXException {
    }


    // EncodingAlgorithmContentHandler

    public final void octets(String URI, int id, byte[] b, int start, int length)  throws SAXException {
        if (length <= 0) {
            return;
        }

        try {
            encodeTermination();

            encodeNonIdentifyingStringOnThirdBit(URI, id, b, start, length);
        } catch (IOException e) {
            throw new SAXException(e);
        } catch (FastInfosetException e) {
            throw new SAXException(e);
        }
    }

    public final void object(String URI, int id, Object data)  throws SAXException {
        try {
            encodeTermination();

            encodeNonIdentifyingStringOnThirdBit(URI, id, data);
        } catch (IOException e) {
            throw new SAXException(e);
        } catch (FastInfosetException e) {
            throw new SAXException(e);
        }
    }


    // PrimitiveTypeContentHandler

    public final void bytes(byte[] b, int start, int length) throws SAXException {
        if (length <= 0) {
            return;
        }

        try {
            encodeTermination();

            encodeCIIOctetAlgorithmData(EncodingAlgorithmIndexes.BASE64, b, start, length);
        } catch (IOException e) {
            throw new SAXException(e);
        }
    }

    public final void shorts(short[] s, int start, int length) throws SAXException {
        if (length <= 0) {
            return;
        }

        try {
            encodeTermination();

            encodeCIIBuiltInAlgorithmData(EncodingAlgorithmIndexes.SHORT, s, start, length);
        } catch (IOException e) {
            throw new SAXException(e);
        } catch (FastInfosetException e) {
            throw new SAXException(e);
        }
    }

    public final void ints(int[] i, int start, int length) throws SAXException {
        if (length <= 0) {
            return;
        }

        try {
            encodeTermination();

            encodeCIIBuiltInAlgorithmData(EncodingAlgorithmIndexes.INT, i, start, length);
        } catch (IOException e) {
            throw new SAXException(e);
        } catch (FastInfosetException e) {
            throw new SAXException(e);
        }
    }

    public final void longs(long[] l, int start, int length) throws SAXException {
        if (length <= 0) {
            return;
        }

        try {
            encodeTermination();

            encodeCIIBuiltInAlgorithmData(EncodingAlgorithmIndexes.LONG, l, start, length);
        } catch (IOException e) {
            throw new SAXException(e);
        } catch (FastInfosetException e) {
            throw new SAXException(e);
        }
    }

    public final void booleans(boolean[] b, int start, int length) throws SAXException {
        if (length <= 0) {
            return;
        }

        try {
            encodeTermination();

            encodeCIIBuiltInAlgorithmData(EncodingAlgorithmIndexes.BOOLEAN, b, start, length);
        } catch (IOException e) {
            throw new SAXException(e);
        } catch (FastInfosetException e) {
            throw new SAXException(e);
        }
    }

    public final void floats(float[] f, int start, int length) throws SAXException {
        if (length <= 0) {
            return;
        }

        try {
            encodeTermination();

            encodeCIIBuiltInAlgorithmData(EncodingAlgorithmIndexes.FLOAT, f, start, length);
        } catch (IOException e) {
            throw new SAXException(e);
        } catch (FastInfosetException e) {
            throw new SAXException(e);
        }
    }

    public final void doubles(double[] d, int start, int length) throws SAXException {
        if (length <= 0) {
            return;
        }

        try {
            encodeTermination();

            encodeCIIBuiltInAlgorithmData(EncodingAlgorithmIndexes.DOUBLE, d, start, length);
        } catch (IOException e) {
            throw new SAXException(e);
        } catch (FastInfosetException e) {
            throw new SAXException(e);
        }
    }

    public void uuids(long[] msblsb, int start, int length) throws SAXException {
        if (length <= 0) {
            return;
        }

        try {
            encodeTermination();

            encodeCIIBuiltInAlgorithmData(EncodingAlgorithmIndexes.UUID, msblsb, start, length);
        } catch (IOException e) {
            throw new SAXException(e);
        } catch (FastInfosetException e) {
            throw new SAXException(e);
        }
    }


    // RestrictedAlphabetContentHandler

    public void numericCharacters(char ch[], int start, int length) throws SAXException {
        if (length <= 0) {
            return;
        }

        try {
            encodeTermination();

            final boolean addToTable = isCharacterContentChunkLengthMatchesLimit(length, _v.characterContentChunk);
            encodeFourBitCharacters(RestrictedAlphabet.NUMERIC_CHARACTERS_INDEX, NUMERIC_CHARACTERS_TABLE,
                    ch, start, length, addToTable);
        } catch (IOException e) {
            throw new SAXException(e);
        } catch (FastInfosetException e) {
            throw new SAXException(e);
        }
    }

    public void dateTimeCharacters(char ch[], int start, int length) throws SAXException {
        if (length <= 0) {
            return;
        }

        try {
            encodeTermination();

            final boolean addToTable = isCharacterContentChunkLengthMatchesLimit(length, _v.characterContentChunk);
            encodeFourBitCharacters(RestrictedAlphabet.DATE_TIME_CHARACTERS_INDEX, DATE_TIME_CHARACTERS_TABLE,
                    ch, start, length, addToTable);
        } catch (IOException e) {
            throw new SAXException(e);
        } catch (FastInfosetException e) {
            throw new SAXException(e);
        }
    }

    public void alphabetCharacters(String alphabet, char ch[], int start, int length) throws SAXException {
        if (length <= 0) {
            return;
        }

        try {
            encodeTermination();

            final boolean addToTable = isCharacterContentChunkLengthMatchesLimit(length, _v.characterContentChunk);
            encodeAlphabetCharacters(alphabet, ch, start, length, addToTable);
        } catch (IOException e) {
            throw new SAXException(e);
        } catch (FastInfosetException e) {
            throw new SAXException(e);
        }
    }

    // ExtendedContentHandler

    public void characters(char[] ch, int start, int length, boolean index) throws SAXException {
        if (length <= 0) {
            return;
        }

        if (getIgnoreWhiteSpaceTextContent() &&
                isWhiteSpace(ch, start, length)) return;

        try {
            encodeTermination();

            if (!_charactersAsCDATA) {
                encodeNonIdentifyingStringOnThirdBit(ch, start, length, _v.characterContentChunk, index, true);
            } else {
                encodeCIIBuiltInAlgorithmDataAsCDATA(ch, start, length);
            }
        } catch (IOException e) {
            throw new SAXException(e);
        } catch (FastInfosetException e) {
            throw new SAXException(e);
        }
    }



    protected final int countAttributes(Attributes atts) {
        // Count attributes ignoring any in the XMLNS namespace
        // Note, such attributes may be produced when transforming from a DOM node
        int count = 0;
        for (int i = 0; i < atts.getLength(); i++) {
            final String uri = atts.getURI(i);
            if (uri == "http://www.w3.org/2000/xmlns/" || uri.equals("http://www.w3.org/2000/xmlns/")) {
                continue;
            }
            count++;
        }
        return count;
    }

    protected void encodeAttributes(Attributes atts) throws IOException, FastInfosetException {
        boolean addToTable;
        String value;
        if (atts instanceof EncodingAlgorithmAttributes) {
            final EncodingAlgorithmAttributes eAtts = (EncodingAlgorithmAttributes)atts;
            Object data;
            String alphabet;
            for (int i = 0; i < eAtts.getLength(); i++) {
                if (encodeAttribute(atts.getURI(i), atts.getQName(i), atts.getLocalName(i))) {
                    data = eAtts.getAlgorithmData(i);
                    // If data is null then there is no algorithm data
                    if (data == null) {
                        value = eAtts.getValue(i);
                        addToTable = eAtts.getToIndex(i) || isAttributeValueLengthMatchesLimit(value.length());

                        alphabet = eAtts.getAlpababet(i);
                        if (alphabet == null)
                            encodeNonIdentifyingStringOnFirstBit(value, _v.attributeValue, addToTable);
                        else if (alphabet == RestrictedAlphabet.DATE_TIME_CHARACTERS)
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
                if (encodeAttribute(atts.getURI(i), atts.getQName(i), atts.getLocalName(i))) {
                    value = atts.getValue(i);
                    addToTable = isAttributeValueLengthMatchesLimit(value.length());
                    encodeNonIdentifyingStringOnFirstBit(value, _v.attributeValue, addToTable);
                }
            }
        }
        _b = EncodingConstants.TERMINATOR;
        _terminate = true;
    }

    protected void encodeElement(String namespaceURI, String qName, String localName) throws IOException {
        LocalNameQualifiedNamesMap.Entry entry = _v.elementName.obtainEntry(qName);
        if (entry._valueIndex > 0) {
            QualifiedName[] names = entry._value;
            for (int i = 0; i < entry._valueIndex; i++) {
                final QualifiedName n = names[i];
                if ((namespaceURI == n.namespaceName || namespaceURI.equals(n.namespaceName))) {
                    encodeNonZeroIntegerOnThirdBit(names[i].index);
                    return;
                }
            }
        }

        encodeLiteralElementQualifiedNameOnThirdBit(namespaceURI, getPrefixFromQualifiedName(qName),
                localName, entry);
    }

    protected boolean encodeAttribute(String namespaceURI, String qName, String localName) throws IOException {
        LocalNameQualifiedNamesMap.Entry entry = _v.attributeName.obtainEntry(qName);
        if (entry._valueIndex > 0) {
            QualifiedName[] names = entry._value;
            for (int i = 0; i < entry._valueIndex; i++) {
                if ((namespaceURI == names[i].namespaceName || namespaceURI.equals(names[i].namespaceName))) {
                    encodeNonZeroIntegerOnSecondBitFirstBitZero(names[i].index);
                    return true;
                }
            }
        }

        return encodeLiteralAttributeQualifiedNameOnSecondBit(namespaceURI, getPrefixFromQualifiedName(qName),
                localName, entry);
    }
}
