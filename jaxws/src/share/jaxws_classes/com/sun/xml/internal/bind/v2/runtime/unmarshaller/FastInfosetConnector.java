/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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
 * <p>
 * This class will peek at future events to ascertain if characters need to be
 * buffered or not.
 *
 * @author Paul Sandoz.
 */
final class FastInfosetConnector extends StAXConnector {

    // event source
    private final StAXDocumentParser fastInfosetStreamReader;

    // Flag set to true if text has been reported
    private boolean textReported;

    // Buffer for octets
    private final Base64Data base64Data = new Base64Data();

    // Buffer for characters
    private final StringBuilder buffer = new StringBuilder();

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
                        if (predictor.expectText()) {
                            // Peek at the next event to see if there are
                            // fragmented characters
                            event = fastInfosetStreamReader.peekNext();
                            if (event == XMLStreamConstants.END_ELEMENT)
                                processNonIgnorableText();
                            else if (event == XMLStreamConstants.START_ELEMENT)
                                processIgnorableText();
                            else
                                handleFragmentedCharacters();
                        }
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
        processUnreportedText();

        for (int i = 0; i < fastInfosetStreamReader.accessNamespaceCount(); i++) {
            visitor.startPrefixMapping(fastInfosetStreamReader.getNamespacePrefix(i),
                    fastInfosetStreamReader.getNamespaceURI(i));
        }

        tagName.uri = fastInfosetStreamReader.accessNamespaceURI();
        tagName.local = fastInfosetStreamReader.accessLocalName();
        tagName.atts = fastInfosetStreamReader.getAttributesHolder();

        visitor.startElement(tagName);
    }

    private void handleFragmentedCharacters() throws XMLStreamException, SAXException {
        buffer.setLength(0);

        // Append characters of first character event
        buffer.append(fastInfosetStreamReader.getTextCharacters(),
                fastInfosetStreamReader.getTextStart(),
                fastInfosetStreamReader.getTextLength());

        // Consume all character
        while(true) {
            switch(fastInfosetStreamReader.peekNext()) {
                case XMLStreamConstants.START_ELEMENT :
                    processBufferedText(true);
                    return;
                case XMLStreamConstants.END_ELEMENT :
                    processBufferedText(false);
                    return;
                case XMLStreamConstants.CHARACTERS :
                case XMLStreamConstants.CDATA :
                case XMLStreamConstants.SPACE :
                    // Append characters of second and subsequent character events
                    fastInfosetStreamReader.next();
                    buffer.append(fastInfosetStreamReader.getTextCharacters(),
                            fastInfosetStreamReader.getTextStart(),
                            fastInfosetStreamReader.getTextLength());
                    break;
                default:
                    fastInfosetStreamReader.next();
            }
        }
    }

    private void handleEndElement() throws SAXException {
        processUnreportedText();

        tagName.uri = fastInfosetStreamReader.accessNamespaceURI();
        tagName.local = fastInfosetStreamReader.accessLocalName();

        visitor.endElement(tagName);

        for (int i = fastInfosetStreamReader.accessNamespaceCount() - 1; i >= 0; i--) {
            visitor.endPrefixMapping(fastInfosetStreamReader.getNamespacePrefix(i));
        }
    }

    final private class CharSequenceImpl implements CharSequence {
        char[] ch;
        int start;
        int length;

        CharSequenceImpl() {
        }

        CharSequenceImpl(final char[] ch, final int start, final int length) {
            this.ch = ch;
            this.start = start;
            this.length = length;
        }

        public void set() {
            ch = fastInfosetStreamReader.getTextCharacters();
            start = fastInfosetStreamReader.getTextStart();
            length = fastInfosetStreamReader.getTextLength();
        }

        // CharSequence interface

        public final int length() {
            return length;
        }

        public final char charAt(final int index) {
            return ch[start + index];
        }

        public final CharSequence subSequence(final int start, final int end) {
            return new CharSequenceImpl(ch, this.start + start, end - start);
        }

        public String toString() {
            return new String(ch, start, length);
        }
    }

    final private CharSequenceImpl charArray = new CharSequenceImpl();

    private void processNonIgnorableText() throws SAXException {
        textReported = true;
        boolean isTextAlgorithmAplied =
                (fastInfosetStreamReader.getTextAlgorithmBytes() != null);

        if (isTextAlgorithmAplied &&
                fastInfosetStreamReader.getTextAlgorithmIndex() == EncodingAlgorithmIndexes.BASE64) {
            base64Data.set(fastInfosetStreamReader.getTextAlgorithmBytesClone(),null);
            visitor.text(base64Data);
        } else {
            if (isTextAlgorithmAplied) {
                fastInfosetStreamReader.getText();
            }

            charArray.set();
            visitor.text(charArray);
        }
    }

    private void processIgnorableText() throws SAXException {
        boolean isTextAlgorithmAplied =
                (fastInfosetStreamReader.getTextAlgorithmBytes() != null);

        if (isTextAlgorithmAplied &&
                fastInfosetStreamReader.getTextAlgorithmIndex() == EncodingAlgorithmIndexes.BASE64) {
            base64Data.set(fastInfosetStreamReader.getTextAlgorithmBytesClone(),null);
            visitor.text(base64Data);
            textReported = true;
        } else {
            if (isTextAlgorithmAplied) {
                fastInfosetStreamReader.getText();
            }

            charArray.set();
            if (!WhiteSpaceProcessor.isWhiteSpace(charArray)) {
                visitor.text(charArray);
                textReported = true;
            }
        }
    }

    private void processBufferedText(boolean ignorable) throws SAXException {
        if (!ignorable || !WhiteSpaceProcessor.isWhiteSpace(buffer)) {
            visitor.text(buffer);
            textReported = true;
        }
    }

    private void processUnreportedText() throws SAXException {
        if(!textReported && predictor.expectText()) {
            visitor.text("");
        }
        textReported = false;
    }
}
