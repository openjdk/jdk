/*
 * Copyright (c) 2014, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package stream;

import org.junit.jupiter.api.Test;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;
import javax.xml.stream.util.EventReaderDelegate;
import java.io.FileInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @library /javax/xml/jaxp/unittest
 * @run junit/othervm stream.EventReaderDelegateTest
 * @summary Test EventReaderDelegate.
 */
public class EventReaderDelegateTest {
    @Test
    public void testGetElementText() throws Exception {
        XMLInputFactory ifac = XMLInputFactory.newFactory();
        XMLEventReader reader = ifac.createXMLEventReader(new FileInputStream(getClass().getResource("toys.xml").getFile()));
        EventReaderDelegate delegate = new EventReaderDelegate(reader);
        while (delegate.hasNext()) {
            XMLEvent event = (XMLEvent) delegate.next();
            switch (event.getEventType()) {
                case XMLStreamConstants.START_ELEMENT: {
                    String name = event.asStartElement().getName().toString();
                    if (name.equals("name") || name.equals("price")) {
                        assertNotNull(delegate.getElementText());
                    } else {
                        assertThrows(XMLStreamException.class, delegate::getElementText);
                    }
                }
            }
        }
        delegate.close();
    }

    @Test
    public void testRemove() throws Exception {
        XMLInputFactory ifac = XMLInputFactory.newFactory();
        XMLEventReader reader = ifac.createXMLEventReader(new FileInputStream(getClass().getResource("toys.xml").getFile()));
        EventReaderDelegate delegate = new EventReaderDelegate(reader);
        assertThrows(UnsupportedOperationException.class, delegate::remove);
    }

    @Test
    public void testPeek() throws Exception {
        XMLInputFactory ifac = XMLInputFactory.newFactory();
        XMLEventReader reader = ifac.createXMLEventReader(new FileInputStream(getClass().getResource("toys.xml").getFile()));
        EventReaderDelegate delegate = new EventReaderDelegate();
        delegate.setParent(reader);
        while (delegate.hasNext()) {
            XMLEvent peekevent = delegate.peek();
            XMLEvent event = (XMLEvent) delegate.next();
            assertSame(peekevent, event, "peek() does not return same XMLEvent with next()");
        }
        delegate.close();
    }

    @Test
    public void testNextTag() throws Exception {
        XMLInputFactory ifac = XMLInputFactory.newFactory();
        ifac.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        XMLEventReader reader = ifac.createXMLEventReader(new FileInputStream(getClass().getResource("toys.xml").getFile()));
        EventReaderDelegate delegate = new EventReaderDelegate(reader);
        assertEquals(Boolean.FALSE, delegate.getProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES));
        while (delegate.hasNext()) {
            XMLEvent event = delegate.peek();
            if (event.isEndElement() || event.isStartElement()) {
                XMLEvent nextevent = delegate.nextTag();
                assertTrue(nextevent.getEventType() == XMLStreamConstants.START_ELEMENT || nextevent.getEventType() == XMLStreamConstants.END_ELEMENT);
            } else {
                delegate.next();
            }
        }
        delegate.close();
    }

    @Test
    public void testNextEvent() throws Exception {
        XMLInputFactory ifac = XMLInputFactory.newFactory();
        ifac.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        XMLEventReader reader = ifac.createXMLEventReader(new FileInputStream(getClass().getResource("toys.xml").getFile()));
        EventReaderDelegate delegate = new EventReaderDelegate();
        delegate.setParent(reader);

        assertEquals(Boolean.FALSE, delegate.getParent().getProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES));
        assertEquals(Boolean.FALSE, delegate.getProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES));

        while (delegate.hasNext()) {
            XMLEvent event = delegate.nextEvent();
            switch (event.getEventType()) {
                case XMLStreamConstants.START_ELEMENT: {
                    assertNotNull(event.asStartElement().getName());
                    break;
                }
                case XMLStreamConstants.END_ELEMENT: {
                    assertNotNull(event.asEndElement().getName());
                    break;
                }
                case XMLStreamConstants.END_DOCUMENT: {
                    assertTrue(event.isEndDocument());
                    break;
                }
                case XMLStreamConstants.START_DOCUMENT: {
                    assertTrue(event.isStartDocument());
                    break;
                }
                case XMLStreamConstants.CHARACTERS: {
                    assertNotNull(event.asCharacters().getData());
                    break;
                }
                case XMLStreamConstants.COMMENT: {
                    assertNotNull(event.toString());
                    break;
                }
            }

        }
        delegate.close();
    }
}
