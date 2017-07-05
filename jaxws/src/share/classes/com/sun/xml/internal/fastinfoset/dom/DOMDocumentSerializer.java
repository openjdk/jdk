/*
 * Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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

/*
 * Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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
 *
 */

package com.sun.xml.internal.fastinfoset.dom;

import com.sun.xml.internal.fastinfoset.Encoder;
import com.sun.xml.internal.fastinfoset.EncodingConstants;
import com.sun.xml.internal.fastinfoset.QualifiedName;
import com.sun.xml.internal.fastinfoset.util.LocalNameQualifiedNamesMap;
import java.io.IOException;
import com.sun.xml.internal.org.jvnet.fastinfoset.FastInfosetException;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * The Fast Infoset DOM serializer.
 * <p>
 * Instantiate this serializer to serialize a fast infoset document in accordance
 * with the DOM API.
 *
 */
public class DOMDocumentSerializer extends Encoder {

    /**
     * Serialize a {@link Node}.
     *
     * @param n the node to serialize.
     */
    public final void serialize(Node n) throws IOException {
        switch (n.getNodeType()) {
            case Node.DOCUMENT_NODE:
                serialize((Document)n);
            case Node.ELEMENT_NODE:
                serializeElementAsDocument(n);
                break;
            case Node.COMMENT_NODE:
                serializeComment(n);
                break;
            case Node.PROCESSING_INSTRUCTION_NODE:
                serializeProcessingInstruction(n);
                break;
        }
    }

    /**
     * Serialize a {@link Document}.
     *
     * @param d the document to serialize.
     */
    public final void serialize(Document d) throws IOException {
        reset();
        encodeHeader(false);
        encodeInitialVocabulary();

        final NodeList nl = d.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            final Node n = nl.item(i);
            switch (n.getNodeType()) {
                case Node.ELEMENT_NODE:
                    serializeElement(n);
                    break;
                case Node.COMMENT_NODE:
                    serializeComment(n);
                    break;
                case Node.PROCESSING_INSTRUCTION_NODE:
                    serializeProcessingInstruction(n);
                    break;
            }
        }
        encodeDocumentTermination();
    }

    protected final void serializeElementAsDocument(Node e) throws IOException {
        reset();
        encodeHeader(false);
        encodeInitialVocabulary();

        serializeElement(e);

        encodeDocumentTermination();
    }


    protected Node[] _namespaceAttributes = new Node[4];
    protected Node[] _attributes = new Node[32];

    protected final void serializeElement(Node e) throws IOException {
        encodeTermination();

        int namespaceAttributesSize = 0;
        int attributesSize = 0;
        if (e.hasAttributes()) {
            /*
             * Split the attribute nodes into namespace attributes
             * or normal attributes.
             */
            final NamedNodeMap nnm = e.getAttributes();
            for (int i = 0; i < nnm.getLength(); i++) {
                final Node a = nnm.item(i);
                final String namespaceURI = a.getNamespaceURI();
                if (namespaceURI != null && namespaceURI.equals("http://www.w3.org/2000/xmlns/")) {
                    if (namespaceAttributesSize == _namespaceAttributes.length) {
                        final Node[] attributes = new Node[namespaceAttributesSize * 3 / 2 + 1];
                        System.arraycopy(_namespaceAttributes, 0, attributes, 0, namespaceAttributesSize);
                        _namespaceAttributes = attributes;
                    }
                    _namespaceAttributes[namespaceAttributesSize++] = a;
                } else {
                    if (attributesSize == _attributes.length) {
                        final Node[] attributes = new Node[attributesSize * 3 / 2 + 1];
                        System.arraycopy(_attributes, 0, attributes, 0, attributesSize);
                        _attributes = attributes;
                    }
                    _attributes[attributesSize++] = a;
                }
            }
        }

        if (namespaceAttributesSize > 0) {
            if (attributesSize > 0) {
                write(EncodingConstants.ELEMENT | EncodingConstants.ELEMENT_NAMESPACES_FLAG |
                        EncodingConstants.ELEMENT_ATTRIBUTE_FLAG);
            } else {
                write(EncodingConstants.ELEMENT | EncodingConstants.ELEMENT_NAMESPACES_FLAG);
            }

            // Serialize the namespace attributes
            for (int i = 0; i < namespaceAttributesSize; i++) {
                final Node a = _namespaceAttributes[i];
                _namespaceAttributes[i] = null;
                String prefix = a.getLocalName();
                if (prefix == "xmlns" || prefix.equals("xmlns")) {
                    prefix = "";
                }
                final String uri = a.getNodeValue();
                encodeNamespaceAttribute(prefix, uri);
            }

            write(EncodingConstants.TERMINATOR);
            _b = 0;
        } else {
            _b = (attributesSize > 0) ? EncodingConstants.ELEMENT | EncodingConstants.ELEMENT_ATTRIBUTE_FLAG :
                EncodingConstants.ELEMENT;
        }

        String namespaceURI = e.getNamespaceURI();
        namespaceURI = (namespaceURI == null) ? "" : namespaceURI;
        encodeElement(namespaceURI, e.getNodeName(), e.getLocalName());

        if (attributesSize > 0) {
            // Serialize the attributes
            for (int i = 0; i < attributesSize; i++) {
                final Node a = _attributes[i];
                _attributes[i] = null;
                namespaceURI = a.getNamespaceURI();
                namespaceURI = (namespaceURI == null) ? "" : namespaceURI;
                encodeAttribute(namespaceURI, a.getNodeName(), a.getLocalName());

                final String value = a.getNodeValue();
                final boolean addToTable = (value.length() < attributeValueSizeConstraint) ? true : false;
                encodeNonIdentifyingStringOnFirstBit(value, _v.attributeValue, addToTable);
            }

            _b = EncodingConstants.TERMINATOR;
            _terminate = true;
        }

        if (e.hasChildNodes()) {
            // Serialize the children
            final NodeList nl = e.getChildNodes();
            for (int i = 0; i < nl.getLength(); i++) {
                final Node n = nl.item(i);
                switch (n.getNodeType()) {
                    case Node.ELEMENT_NODE:
                        serializeElement(n);
                        break;
                    case Node.TEXT_NODE:
                        serializeText(n);
                        break;
                    case Node.CDATA_SECTION_NODE:
                        serializeCDATA(n);
                        break;
                    case Node.COMMENT_NODE:
                        serializeComment(n);
                        break;
                    case Node.PROCESSING_INSTRUCTION_NODE:
                        serializeProcessingInstruction(n);
                        break;
                }
            }
        }
        encodeElementTermination();
    }

    protected final void serializeText(Node t) throws IOException {
        final String text = t.getNodeValue();

        final int length = (text != null) ? text.length() : 0;
        if (length == 0) {
            return;
        } else if (length < _charBuffer.length) {
            text.getChars(0, length, _charBuffer, 0);
            if (getIgnoreWhiteSpaceTextContent() &&
                    isWhiteSpace(_charBuffer, 0, length)) return;

            encodeTermination();
            encodeCharacters(_charBuffer, 0, length);
        } else {
            final char ch[] = text.toCharArray();
            if (getIgnoreWhiteSpaceTextContent() &&
                    isWhiteSpace(ch, 0, length)) return;

            encodeTermination();
            encodeCharactersNoClone(ch, 0, length);
        }
    }

    protected final void serializeCDATA(Node t) throws IOException {
        final String text = t.getNodeValue();

        final int length = (text != null) ? text.length() : 0;
        if (length == 0) {
            return;
        } else {
            final char ch[] = text.toCharArray();
            if (getIgnoreWhiteSpaceTextContent() &&
                    isWhiteSpace(ch, 0, length)) return;

            encodeTermination();
            try {
                encodeCIIBuiltInAlgorithmDataAsCDATA(ch, 0, length);
            } catch (FastInfosetException e) {
                throw new IOException("");
            }
        }
    }

    protected final void serializeComment(Node c) throws IOException {
        if (getIgnoreComments()) return;

        encodeTermination();

        final String comment = c.getNodeValue();

        final int length = (comment != null) ? comment.length() : 0;
        if (length == 0) {
            encodeComment(_charBuffer, 0, 0);
        } else if (length < _charBuffer.length) {
            comment.getChars(0, length, _charBuffer, 0);
            encodeComment(_charBuffer, 0, length);
        } else {
            final char ch[] = comment.toCharArray();
            encodeCommentNoClone(ch, 0, length);
        }
    }

    protected final void serializeProcessingInstruction(Node pi) throws IOException {
        if (getIgnoreProcesingInstructions()) return;

        encodeTermination();

        final String target = pi.getNodeName();
        final String data = pi.getNodeValue();
        encodeProcessingInstruction(target, data);
    }

    protected final void encodeElement(String namespaceURI, String qName, String localName) throws IOException {
        LocalNameQualifiedNamesMap.Entry entry = _v.elementName.obtainEntry(qName);
        if (entry._valueIndex > 0) {
            final QualifiedName[] names = entry._value;
            for (int i = 0; i < entry._valueIndex; i++) {
                if ((namespaceURI == names[i].namespaceName || namespaceURI.equals(names[i].namespaceName))) {
                    encodeNonZeroIntegerOnThirdBit(names[i].index);
                    return;
                }
            }
        }

        // Was DOM node created using an NS-aware call?
        if (localName != null) {
            encodeLiteralElementQualifiedNameOnThirdBit(namespaceURI, getPrefixFromQualifiedName(qName),
                    localName, entry);
        }
        else {
            encodeLiteralElementQualifiedNameOnThirdBit(namespaceURI, "", qName, entry);
        }
    }

    protected final void encodeAttribute(String namespaceURI, String qName, String localName) throws IOException {
        LocalNameQualifiedNamesMap.Entry entry = _v.attributeName.obtainEntry(qName);
        if (entry._valueIndex > 0) {
            final QualifiedName[] names = entry._value;
            for (int i = 0; i < entry._valueIndex; i++) {
                if ((namespaceURI == names[i].namespaceName || namespaceURI.equals(names[i].namespaceName))) {
                    encodeNonZeroIntegerOnSecondBitFirstBitZero(names[i].index);
                    return;
                }
            }
        }

        // Was DOM node created using an NS-aware call?
        if (localName != null) {
            encodeLiteralAttributeQualifiedNameOnSecondBit(namespaceURI, getPrefixFromQualifiedName(qName),
                    localName, entry);
        }
        else {
            encodeLiteralAttributeQualifiedNameOnSecondBit(namespaceURI, "", qName, entry);
        }
    }
}
