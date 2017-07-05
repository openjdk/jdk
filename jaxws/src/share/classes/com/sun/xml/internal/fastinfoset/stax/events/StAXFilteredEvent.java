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
/*
 * StAXFilteredEvent.java
 *
 * Created on January 12, 2005, 4:46 PM
 */

package com.sun.xml.internal.fastinfoset.stax.events;

import javax.xml.stream.events.XMLEvent;
import javax.xml.stream.EventFilter;
import javax.xml.stream.events.Characters;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLEventReader;
import com.sun.xml.internal.fastinfoset.CommonResourceBundle;

public class StAXFilteredEvent implements XMLEventReader {
    private XMLEventReader eventReader;
    private EventFilter _filter;

    /** Creates a new instance of StAXFilteredEvent */
    public StAXFilteredEvent() {
    }

    public StAXFilteredEvent(XMLEventReader reader, EventFilter filter) throws XMLStreamException
    {
        eventReader = reader;
        _filter = filter;
    }

    public void setEventReader(XMLEventReader reader) {
        eventReader = reader;
    }

    public void setFilter(EventFilter filter) {
        _filter = filter;
    }

    public Object next() {
        try {
            return nextEvent();
        } catch (XMLStreamException e) {
            return null;
        }
    }

    public XMLEvent nextEvent() throws XMLStreamException
    {
        if (hasNext())
            return eventReader.nextEvent();
        return null;
    }

    public String getElementText() throws XMLStreamException
    {
        StringBuffer buffer = new StringBuffer();
        XMLEvent e = nextEvent();
        if (!e.isStartElement())
            throw new XMLStreamException(
            CommonResourceBundle.getInstance().getString("message.mustBeOnSTART_ELEMENT"));

        while(hasNext()) {
            e = nextEvent();
            if(e.isStartElement())
                throw new XMLStreamException(
                CommonResourceBundle.getInstance().getString("message.getElementTextExpectTextOnly"));
            if(e.isCharacters())
                buffer.append(((Characters) e).getData());
            if(e.isEndElement())
                return buffer.toString();
        }
        throw new XMLStreamException(CommonResourceBundle.getInstance().getString("message.END_ELEMENTnotFound"));
    }

    public XMLEvent nextTag() throws XMLStreamException {
        while(hasNext()) {
            XMLEvent e = nextEvent();
            if (e.isStartElement() || e.isEndElement())
                return e;
        }
        throw new XMLStreamException(CommonResourceBundle.getInstance().getString("message.startOrEndNotFound"));
    }


    public boolean hasNext()
    {
        try {
            while(eventReader.hasNext()) {
                if (_filter.accept(eventReader.peek())) return true;
                eventReader.nextEvent();
            }
            return false;
        } catch (XMLStreamException e) {
            return false;
        }
    }

    public void remove() {
        throw new UnsupportedOperationException();
    }

    public XMLEvent peek() throws XMLStreamException
    {
        if (hasNext())
            return eventReader.peek();
        return null;
    }

    public void close() throws XMLStreamException
    {
        eventReader.close();
    }

    public Object getProperty(String name) {
        return eventReader.getProperty(name);
    }

}
