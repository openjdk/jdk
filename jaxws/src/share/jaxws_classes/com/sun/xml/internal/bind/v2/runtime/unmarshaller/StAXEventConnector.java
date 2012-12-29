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

import java.util.Iterator;

import javax.xml.namespace.QName;
import javax.xml.stream.Location;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.Namespace;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * This is a simple utility class that adapts StAX events from an
 * {@link XMLEventReader} to unmarshaller events on a
 * {@link XmlVisitor}, bridging between the two
 * parser technologies.
 *
 * @author Ryan.Shoemaker@Sun.COM
 * @version 1.0
 */
final class StAXEventConnector extends StAXConnector {

    // StAX event source
    private final XMLEventReader staxEventReader;

    /** Current event. */
    private XMLEvent event;

    /**
     * Shared and reused {@link Attributes}.
     */
    private final AttributesImpl attrs = new AttributesImpl();

    /**
     * SAX may fire consective characters event, but we don't allow it.
     * so use this buffer to perform buffering.
     */
    private final StringBuilder buffer = new StringBuilder();

    private boolean seenText;

    /**
     * Construct a new StAX to SAX adapter that will convert a StAX event
     * stream into a SAX event stream.
     *
     * @param staxCore
     *                StAX event source
     * @param visitor
     *                sink
     */
    public StAXEventConnector(XMLEventReader staxCore, XmlVisitor visitor) {
        super(visitor);
        staxEventReader = staxCore;
    }

    public void bridge() throws XMLStreamException {

        try {
            // remembers the nest level of elements to know when we are done.
            int depth=0;

            event = staxEventReader.peek();

            if( !event.isStartDocument() && !event.isStartElement() )
                throw new IllegalStateException();

            // if the parser is on START_DOCUMENT, skip ahead to the first element
            do {
                event = staxEventReader.nextEvent();
            } while( !event.isStartElement() );

            handleStartDocument(event.asStartElement().getNamespaceContext());

            OUTER:
            while(true) {
                // These are all of the events listed in the javadoc for
                // XMLEvent.
                // The spec only really describes 11 of them.
                switch (event.getEventType()) {
                    case XMLStreamConstants.START_ELEMENT :
                        handleStartElement(event.asStartElement());
                        depth++;
                        break;
                    case XMLStreamConstants.END_ELEMENT :
                        depth--;
                        handleEndElement(event.asEndElement());
                        if(depth==0)    break OUTER;
                        break;
                    case XMLStreamConstants.CHARACTERS :
                    case XMLStreamConstants.CDATA :
                    case XMLStreamConstants.SPACE :
                        handleCharacters(event.asCharacters());
                        break;
                }


                event=staxEventReader.nextEvent();
            }

            handleEndDocument();
            event = null; // avoid keeping a stale reference
        } catch (SAXException e) {
            throw new XMLStreamException(e);
        }
    }

    protected Location getCurrentLocation() {
        return event.getLocation();
    }

    protected String getCurrentQName() {
        QName qName;
        if(event.isEndElement())
            qName = event.asEndElement().getName();
        else
            qName = event.asStartElement().getName();
        return getQName(qName.getPrefix(), qName.getLocalPart());
    }


    private void handleCharacters(Characters event) throws SAXException, XMLStreamException {
        if(!predictor.expectText())
            return;     // text isn't expected. simply skip

        seenText = true;

        // check the next event
        XMLEvent next;
        while(true) {
            next = staxEventReader.peek();
            if(!isIgnorable(next))
                break;
            staxEventReader.nextEvent();
        }

        if(isTag(next)) {
            // this is by far the common case --- you have <foo>abc</foo> or <foo>abc<bar/>...</foo>
            visitor.text(event.getData());
            return;
        }

        // otherwise we have things like "abc<!-- test -->def".
        // concatenate all text
        buffer.append(event.getData());

        while(true) {
            while(true) {
                next = staxEventReader.peek();
                if(!isIgnorable(next))
                    break;
                staxEventReader.nextEvent();
            }

            if(isTag(next)) {
                // found all adjacent text
                visitor.text(buffer);
                buffer.setLength(0);
                return;
            }

            buffer.append(next.asCharacters().getData());
            staxEventReader.nextEvent();    // consume
        }
    }

    private boolean isTag(XMLEvent event) {
        int eventType = event.getEventType();
        return eventType==XMLEvent.START_ELEMENT || eventType==XMLEvent.END_ELEMENT;
    }

    private boolean isIgnorable(XMLEvent event) {
        int eventType = event.getEventType();
        return eventType==XMLEvent.COMMENT || eventType==XMLEvent.PROCESSING_INSTRUCTION;
    }

    private void handleEndElement(EndElement event) throws SAXException {
        if(!seenText && predictor.expectText()) {
            visitor.text("");
        }

        // fire endElement
        QName qName = event.getName();
        tagName.uri = fixNull(qName.getNamespaceURI());
        tagName.local = qName.getLocalPart();
        visitor.endElement(tagName);

        // end namespace bindings
        for( Iterator<Namespace> i = event.getNamespaces(); i.hasNext();) {
            String prefix = fixNull(i.next().getPrefix());  // be defensive
            visitor.endPrefixMapping(prefix);
        }

        seenText = false;
    }

    private void handleStartElement(StartElement event) throws SAXException {
        // start namespace bindings
        for (Iterator i = event.getNamespaces(); i.hasNext();) {
            Namespace ns = (Namespace)i.next();
            visitor.startPrefixMapping(
                fixNull(ns.getPrefix()),
                fixNull(ns.getNamespaceURI()));
        }

        // fire startElement
        QName qName = event.getName();
        tagName.uri = fixNull(qName.getNamespaceURI());
        String localName = qName.getLocalPart();
        tagName.uri = fixNull(qName.getNamespaceURI());
        tagName.local = localName;
        tagName.atts = getAttributes(event);
        visitor.startElement(tagName);

        seenText = false;
    }



    /**
     * Get the attributes associated with the given START_ELEMENT StAXevent.
     *
     * @return the StAX attributes converted to an org.xml.sax.Attributes
     */
    private Attributes getAttributes(StartElement event) {
        attrs.clear();

        // in SAX, namespace declarations are not part of attributes by default.
        // (there's a property to control that, but as far as we are concerned
        // we don't use it.) So don't add xmlns:* to attributes.

        // gather non-namespace attrs
        for (Iterator i = event.getAttributes(); i.hasNext();) {
            Attribute staxAttr = (Attribute)i.next();

            QName name = staxAttr.getName();
            String uri = fixNull(name.getNamespaceURI());
            String localName = name.getLocalPart();
            String prefix = name.getPrefix();
            String qName;
            if (prefix == null || prefix.length() == 0)
                qName = localName;
            else
                qName = prefix + ':' + localName;
            String type = staxAttr.getDTDType();
            String value = staxAttr.getValue();

            attrs.addAttribute(uri, localName, qName, type, value);
        }

        return attrs;
    }
}
