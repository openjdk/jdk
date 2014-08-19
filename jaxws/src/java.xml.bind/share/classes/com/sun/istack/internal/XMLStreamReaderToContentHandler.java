/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.istack.internal;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.Locator;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.AttributesImpl;

import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.namespace.QName;

/**
 * This is a simple utility class that adapts StAX events from an
 * {@link XMLStreamReader} to SAX events on a
 * {@link ContentHandler}, bridging between the two
 * parser technologies.
 *
 * @author Ryan.Shoemaker@Sun.COM
 * @version 1.0
 */
public class XMLStreamReaderToContentHandler {

    // StAX event source
    private final XMLStreamReader staxStreamReader;

    // SAX event sink
    private final ContentHandler saxHandler;

    // if true, when the conversion is completed, leave the cursor to the last
    // event that was fired (such as end element)
    private final boolean eagerQuit;

    /**
     * If true, not start/endDocument event.
     */
    private final boolean fragment;

    // array of the even length of the form { prefix0, uri0, prefix1, uri1, ... }
    private final String[] inscopeNamespaces;

    /**
     * @see #XMLStreamReaderToContentHandler(XMLStreamReader, ContentHandler, boolean, boolean, String[])
     */
    public XMLStreamReaderToContentHandler(XMLStreamReader staxCore, ContentHandler saxCore, boolean eagerQuit, boolean fragment) {
        this(staxCore, saxCore, eagerQuit, fragment, new String[0]);
    }

    /**
     * Construct a new StAX to SAX adapter that will convert a StAX event
     * stream into a SAX event stream.
     *
     * @param staxCore
     *                StAX event source
     * @param saxCore
     *                SAXevent sink
     * @param eagerQuit
     * @param fragment
     * @param inscopeNamespaces
     *                array of the even length of the form { prefix0, uri0, prefix1, uri1, ... }
     */
    public XMLStreamReaderToContentHandler(XMLStreamReader staxCore, ContentHandler saxCore,
            boolean eagerQuit, boolean fragment, String[] inscopeNamespaces) {
        this.staxStreamReader = staxCore;
        this.saxHandler = saxCore;
        this.eagerQuit = eagerQuit;
        this.fragment = fragment;
        this.inscopeNamespaces = inscopeNamespaces.clone();
        assert inscopeNamespaces.length%2 == 0;
    }


    /*
     * @see StAXReaderToContentHandler#bridge()
     */
    public void bridge() throws XMLStreamException {

        try {
            // remembers the nest level of elements to know when we are done.
            int depth=0;

            // if the parser is at the start tag, proceed to the first element
            int event = staxStreamReader.getEventType();
            if(event == XMLStreamConstants.START_DOCUMENT) {
                // nextTag doesn't correctly handle DTDs
                while( !staxStreamReader.isStartElement() )
                    event = staxStreamReader.next();
            }


            if( event!=XMLStreamConstants.START_ELEMENT)
                throw new IllegalStateException("The current event is not START_ELEMENT\n but " + event);

            handleStartDocument();

            for(int i=0; i < inscopeNamespaces.length; i+=2) {
                saxHandler.startPrefixMapping(inscopeNamespaces[i], inscopeNamespaces[i+1]);
            }

            OUTER:
            do {
                // These are all of the events listed in the javadoc for
                // XMLEvent.
                // The spec only really describes 11 of them.
                switch (event) {
                    case XMLStreamConstants.START_ELEMENT :
                        depth++;
                        handleStartElement();
                        break;
                    case XMLStreamConstants.END_ELEMENT :
                        handleEndElement();
                        depth--;
                        if(depth==0 && eagerQuit)
                            break OUTER;
                        break;
                    case XMLStreamConstants.CHARACTERS :
                        handleCharacters();
                        break;
                    case XMLStreamConstants.ENTITY_REFERENCE :
                        handleEntityReference();
                        break;
                    case XMLStreamConstants.PROCESSING_INSTRUCTION :
                        handlePI();
                        break;
                    case XMLStreamConstants.COMMENT :
                        handleComment();
                        break;
                    case XMLStreamConstants.DTD :
                        handleDTD();
                        break;
                    case XMLStreamConstants.ATTRIBUTE :
                        handleAttribute();
                        break;
                    case XMLStreamConstants.NAMESPACE :
                        handleNamespace();
                        break;
                    case XMLStreamConstants.CDATA :
                        handleCDATA();
                        break;
                    case XMLStreamConstants.ENTITY_DECLARATION :
                        handleEntityDecl();
                        break;
                    case XMLStreamConstants.NOTATION_DECLARATION :
                        handleNotationDecl();
                        break;
                    case XMLStreamConstants.SPACE :
                        handleSpace();
                        break;
                    default :
                        throw new InternalError("processing event: " + event);
                }

                event=staxStreamReader.next();
            } while (depth!=0);

            for(int i=0; i < inscopeNamespaces.length; i+=2) {
                saxHandler.endPrefixMapping(inscopeNamespaces[i]);
            }

            handleEndDocument();
        } catch (SAXException e) {
            throw new XMLStreamException2(e);
        }
    }

    private void handleEndDocument() throws SAXException {
        if(fragment)
            return;

        saxHandler.endDocument();
    }

    private void handleStartDocument() throws SAXException {
        if(fragment)
            return;

        saxHandler.setDocumentLocator(new Locator() {
            public int getColumnNumber() {
                return staxStreamReader.getLocation().getColumnNumber();
            }
            public int getLineNumber() {
                return staxStreamReader.getLocation().getLineNumber();
            }
            public String getPublicId() {
                return staxStreamReader.getLocation().getPublicId();
            }
            public String getSystemId() {
                return staxStreamReader.getLocation().getSystemId();
            }
        });
        saxHandler.startDocument();
    }

    private void handlePI() throws XMLStreamException {
        try {
            saxHandler.processingInstruction(
                staxStreamReader.getPITarget(),
                staxStreamReader.getPIData());
        } catch (SAXException e) {
            throw new XMLStreamException2(e);
        }
    }

    private void handleCharacters() throws XMLStreamException {
        try {
            saxHandler.characters(
                staxStreamReader.getTextCharacters(),
                staxStreamReader.getTextStart(),
                staxStreamReader.getTextLength() );
        } catch (SAXException e) {
            throw new XMLStreamException2(e);
        }
    }

    private void handleEndElement() throws XMLStreamException {
        QName qName = staxStreamReader.getName();

        try {
            String pfix = qName.getPrefix();
            String rawname = (pfix == null || pfix.length() == 0)
                    ? qName.getLocalPart()
                    : pfix + ':' + qName.getLocalPart();
            // fire endElement
            saxHandler.endElement(
                qName.getNamespaceURI(),
                qName.getLocalPart(),
                rawname);

            // end namespace bindings
            int nsCount = staxStreamReader.getNamespaceCount();
            for (int i = nsCount - 1; i >= 0; i--) {
                String prefix = staxStreamReader.getNamespacePrefix(i);
                if (prefix == null) { // true for default namespace
                    prefix = "";
                }
                saxHandler.endPrefixMapping(prefix);
            }
        } catch (SAXException e) {
            throw new XMLStreamException2(e);
        }
    }

    private void handleStartElement() throws XMLStreamException {

        try {
            // start namespace bindings
            int nsCount = staxStreamReader.getNamespaceCount();
            for (int i = 0; i < nsCount; i++) {
                saxHandler.startPrefixMapping(
                    fixNull(staxStreamReader.getNamespacePrefix(i)),
                    fixNull(staxStreamReader.getNamespaceURI(i)));
            }

            // fire startElement
            QName qName = staxStreamReader.getName();
            String prefix = qName.getPrefix();
            String rawname;
            if(prefix==null || prefix.length()==0)
                rawname = qName.getLocalPart();
            else
                rawname = prefix + ':' + qName.getLocalPart();
            Attributes attrs = getAttributes();
            saxHandler.startElement(
                qName.getNamespaceURI(),
                qName.getLocalPart(),
                rawname,
                attrs);
        } catch (SAXException e) {
            throw new XMLStreamException2(e);
        }
    }

    private static String fixNull(String s) {
        if(s==null)     return "";
        else            return s;
    }

    /**
     * Get the attributes associated with the given START_ELEMENT or ATTRIBUTE
     * StAXevent.
     *
     * @return the StAX attributes converted to an org.xml.sax.Attributes
     */
    private Attributes getAttributes() {
        AttributesImpl attrs = new AttributesImpl();

        int eventType = staxStreamReader.getEventType();
        if (eventType != XMLStreamConstants.ATTRIBUTE
            && eventType != XMLStreamConstants.START_ELEMENT) {
            throw new InternalError(
                "getAttributes() attempting to process: " + eventType);
        }

        // in SAX, namespace declarations are not part of attributes by default.
        // (there's a property to control that, but as far as we are concerned
        // we don't use it.) So don't add xmlns:* to attributes.

        // gather non-namespace attrs
        for (int i = 0; i < staxStreamReader.getAttributeCount(); i++) {
            String uri = staxStreamReader.getAttributeNamespace(i);
            if(uri==null)   uri="";
            String localName = staxStreamReader.getAttributeLocalName(i);
            String prefix = staxStreamReader.getAttributePrefix(i);
            String qName;
            if(prefix==null || prefix.length()==0)
                qName = localName;
            else
                qName = prefix + ':' + localName;
            String type = staxStreamReader.getAttributeType(i);
            String value = staxStreamReader.getAttributeValue(i);

            attrs.addAttribute(uri, localName, qName, type, value);
        }

        return attrs;
    }

    private void handleNamespace() {
        // no-op ???
        // namespace events don't normally occur outside of a startElement
        // or endElement
    }

    private void handleAttribute() {
        // no-op ???
        // attribute events don't normally occur outside of a startElement
        // or endElement
    }

    private void handleDTD() {
        // no-op ???
        // it seems like we need to pass this info along, but how?
    }

    private void handleComment() {
        // no-op ???
    }

    private void handleEntityReference() {
        // no-op ???
    }

    private void handleSpace() {
        // no-op ???
        // this event is listed in the javadoc, but not in the spec.
    }

    private void handleNotationDecl() {
        // no-op ???
        // this event is listed in the javadoc, but not in the spec.
    }

    private void handleEntityDecl() {
        // no-op ???
        // this event is listed in the javadoc, but not in the spec.
    }

    private void handleCDATA() {
        // no-op ???
        // this event is listed in the javadoc, but not in the spec.
    }
}
