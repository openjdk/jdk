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


package com.sun.xml.internal.fastinfoset.stax.events;

import com.sun.xml.internal.fastinfoset.stax.*;
import java.util.NoSuchElementException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.XMLEvent;
import javax.xml.stream.util.XMLEventAllocator;
import com.sun.xml.internal.fastinfoset.CommonResourceBundle;



public class StAXEventReader implements javax.xml.stream.XMLEventReader{
    protected XMLStreamReader _streamReader ;
    protected XMLEventAllocator _eventAllocator;
    private XMLEvent _currentEvent;     //the current event
    private XMLEvent[] events = new XMLEvent[3];
    private int size = 3;
    private int currentIndex = 0;
    private boolean hasEvent = false;   //true when current event exists, false initially & at end

    //only constructor will do because we delegate everything to underlying XMLStreamReader
    public StAXEventReader(XMLStreamReader reader) throws  XMLStreamException {
        _streamReader = reader ;
        _eventAllocator = (XMLEventAllocator)reader.getProperty(XMLInputFactory.ALLOCATOR);
        if(_eventAllocator == null){
            _eventAllocator = new StAXEventAllocatorBase();
        }
        //initialize
        if (_streamReader.hasNext())
        {
            _streamReader.next();
            _currentEvent =_eventAllocator.allocate(_streamReader);
            events[0] = _currentEvent;
            hasEvent = true;
        } else {
            throw new XMLStreamException(CommonResourceBundle.getInstance().getString("message.noElement"));
        }
    }

    public boolean hasNext() {
        return hasEvent;
    }

    public XMLEvent nextEvent() throws XMLStreamException {
        XMLEvent event = null;
        XMLEvent nextEvent = null;
        if (hasEvent)
        {
            event = events[currentIndex];
            events[currentIndex] = null;
            if (_streamReader.hasNext())
            {
                //advance and read the next
                _streamReader.next();
                nextEvent = _eventAllocator.allocate(_streamReader);
                if (++currentIndex==size)
                    currentIndex = 0;
                events[currentIndex] = nextEvent;
                hasEvent = true;
            } else {
                _currentEvent = null;
                hasEvent = false;
            }
            return event;
        }
        else{
            throw new NoSuchElementException();
        }
    }

    public void remove(){
        //stream reader is read-only.
        throw new java.lang.UnsupportedOperationException();
    }


    public void close() throws XMLStreamException {
        _streamReader.close();
    }

    /** Reads the content of a text-only element. Precondition:
     * the current event is START_ELEMENT. Postcondition:
     * The current event is the corresponding END_ELEMENT.
     * @throws XMLStreamException if the current event is not a START_ELEMENT
     * or if a non text element is encountered
     */
    public String getElementText() throws XMLStreamException {
        if(!hasEvent) {
            throw new NoSuchElementException();
        }

        if(!_currentEvent.isStartElement()) {
            StAXDocumentParser parser = (StAXDocumentParser)_streamReader;
            return parser.getElementText(true);
        } else {
            return _streamReader.getElementText();
        }
    }

    /** Get the value of a feature/property from the underlying implementation
     * @param name The name of the property
     * @return The value of the property
     * @throws IllegalArgumentException if the property is not supported
     */
    public Object getProperty(java.lang.String name) throws java.lang.IllegalArgumentException {
        return _streamReader.getProperty(name) ;
    }

    /** Skips any insignificant space events until a START_ELEMENT or
     * END_ELEMENT is reached. If anything other than space characters are
     * encountered, an exception is thrown. This method should
     * be used when processing element-only content because
     * the parser is not able to recognize ignorable whitespace if
     * the DTD is missing or not interpreted.
     * @throws XMLStreamException if anything other than space characters are encountered
     */
    public XMLEvent nextTag() throws XMLStreamException {
        if(!hasEvent) {
            throw new NoSuchElementException();
        }
        StAXDocumentParser parser = (StAXDocumentParser)_streamReader;
        parser.nextTag(true);
        return _eventAllocator.allocate(_streamReader);
    }

    //XMLEventReader extends Iterator;
    public Object next() {
        try{
            return nextEvent();
        }catch(XMLStreamException streamException){
            return null;
        }
    }

    public XMLEvent peek() throws XMLStreamException{
        if (!hasEvent)
             throw new XMLStreamException(CommonResourceBundle.getInstance().getString("message.noElement"));
        _currentEvent = events[currentIndex];
        return _currentEvent;
    }

    public void setAllocator(XMLEventAllocator allocator) {
        if (allocator == null)
            throw new IllegalArgumentException(CommonResourceBundle.getInstance().getString("message.nullXMLEventAllocator"));

        _eventAllocator = allocator;
    }


}
