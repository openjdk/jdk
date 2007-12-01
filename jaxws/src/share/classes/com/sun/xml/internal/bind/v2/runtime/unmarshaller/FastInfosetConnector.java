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
 */

package com.sun.xml.internal.bind.v2.runtime.unmarshaller;

import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import com.sun.xml.internal.bind.WhiteSpaceProcessor;
import com.sun.xml.internal.fastinfoset.stax.StAXDocumentParser;

import com.sun.xml.internal.org.jvnet.fastinfoset.EncodingAlgorithmIndexes;
import org.xml.sax.SAXException;

/**
 * Reads from FastInfoset StAX parser and feeds into JAXB Unmarshaller.
 *
 * @author Paul Sandoz.
 */
final class FastInfosetConnector extends StAXConnector {

    // event source
    private final StAXDocumentParser fastInfosetStreamReader;

    // Flag set to true if there is octets instead of characters
    boolean hasBase64Data = false;
    // Flag set to true if the first chunk of CIIs
    boolean firstCIIChunk = true;

    // Buffer for octets
    private Base64Data base64Data = new Base64Data();

    // Buffer for characters
    private StringBuilder buffer = new StringBuilder();

    public FastInfosetConnector(StAXDocumentParser fastInfosetStreamReader,
            XmlVisitor visitor) {
        super(visitor);
        fastInfosetStreamReader.setStringInterning(true);
        this.fastInfosetStreamReader = fastInfosetStreamReader;
    }

    public void bridge() throws XMLStreamException {
        try {
            // remembers the nest level of elements to know when we are done.
            int depth=0;

            // if the parser is at the start tag, proceed to the first element
            int event = fastInfosetStreamReader.getEventType();
            if(event == XMLStreamConstants.START_DOCUMENT) {
                // nextTag doesn't correctly handle DTDs
                while( !fastInfosetStreamReader.isStartElement() )
                    event = fastInfosetStreamReader.next();
            }


            if( event!=XMLStreamConstants.START_ELEMENT)
                throw new IllegalStateException("The current event is not START_ELEMENT\n but " + event);

            // TODO: we don't have to rely on this hack --- we can just emulate
            // start/end prefix mappings. But for now, I'll rely on this hack.
            handleStartDocument(fastInfosetStreamReader.getNamespaceContext());

            OUTER:
            while(true) {
                // These are all of the events listed in the javadoc for
                // XMLEvent.
                // The spec only really describes 11 of them.
                switch (event) {
                    case XMLStreamConstants.START_ELEMENT :
                        handleStartElement();
                        depth++;
                        break;
                    case XMLStreamConstants.END_ELEMENT :
                        depth--;
                        handleEndElement();
                        if(depth==0)    break OUTER;
                        break;
                    case XMLStreamConstants.CHARACTERS :
                    case XMLStreamConstants.CDATA :
                    case XMLStreamConstants.SPACE :
                        handleCharacters();
                        break;
                    // otherwise simply ignore
                }

                event=fastInfosetStreamReader.next();
            }

            fastInfosetStreamReader.next();    // move beyond the end tag.

            handleEndDocument();
        } catch (SAXException e) {
            throw new XMLStreamException(e);
        }
    }

    protected Location getCurrentLocation() {
        return fastInfosetStreamReader.getLocation();
    }

    protected String getCurrentQName() {
        return fastInfosetStreamReader.getNameString();
    }

    private void handleStartElement() throws SAXException {
        processText(true);

        for (int i = 0; i < fastInfosetStreamReader.getNamespaceCount(); i++) {
            visitor.startPrefixMapping(fastInfosetStreamReader.getNamespacePrefix(i),
                    fastInfosetStreamReader.getNamespaceURI(i));
        }

        tagName.uri = fastInfosetStreamReader.getNamespaceURI();
        tagName.local = fastInfosetStreamReader.getLocalName();
        tagName.atts = fastInfosetStreamReader.getAttributesHolder();

        visitor.startElement(tagName);
    }

    private void handleCharacters() {
        if (predictor.expectText()) {
            // If the first chunk of CIIs and character data is present
            if (firstCIIChunk &&
                    fastInfosetStreamReader.getTextAlgorithmBytes() == null) {
                buffer.append(fastInfosetStreamReader.getTextCharacters(),
                        fastInfosetStreamReader.getTextStart(),
                        fastInfosetStreamReader.getTextLength());
                firstCIIChunk = false;
            // If the first chunk of CIIs and octet data is present
            } else if (firstCIIChunk &&
                    fastInfosetStreamReader.getTextAlgorithmIndex() == EncodingAlgorithmIndexes.BASE64) {
                firstCIIChunk = false;
                hasBase64Data = true;
                // Clone the octets
                base64Data.set(fastInfosetStreamReader.getTextAlgorithmBytesClone(),null);
                return;
            // If a subsequent sequential chunk of CIIs
            } else {
                // If the first chunk is octet data
                if (hasBase64Data) {
                    // Append base64 encoded octets to the character buffer
                    buffer.append(base64Data);
                    hasBase64Data = false;
                }

                // Append the second or subsequence chunk of CIIs to the buffer
                buffer.append(fastInfosetStreamReader.getTextCharacters(),
                        fastInfosetStreamReader.getTextStart(),
                        fastInfosetStreamReader.getTextLength());
            }

        }
    }

    private void handleEndElement() throws SAXException {
        processText(false);

        tagName.uri = fastInfosetStreamReader.getNamespaceURI();
        tagName.local = fastInfosetStreamReader.getLocalName();

        visitor.endElement(tagName);

        for (int i = fastInfosetStreamReader.getNamespaceCount() - 1; i >= 0; i--) {
            visitor.endPrefixMapping(fastInfosetStreamReader.getNamespacePrefix(i));
        }
    }

    private void processText(boolean ignorable) throws SAXException {
        firstCIIChunk = true;
        if(predictor.expectText() && (!ignorable || !WhiteSpaceProcessor.isWhiteSpace(buffer))) {
            if (!hasBase64Data) {
                visitor.text(buffer);
            } else {
                visitor.text(base64Data);
                hasBase64Data = false;
            }
        }
        buffer.setLength(0);
    }
}
