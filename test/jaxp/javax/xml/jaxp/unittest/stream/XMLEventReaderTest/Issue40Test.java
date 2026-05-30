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

package stream.XMLEventReaderTest;

import org.junit.jupiter.api.Test;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.events.XMLEvent;
import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;

/*
 * @test
 * @library /javax/xml/jaxp/unittest
 * @run junit/othervm stream.XMLEventReaderTest.Issue40Test
 * @summary Test XMLEventReader.getElementText() works after calling peek().
 */
public class Issue40Test {

    public java.io.File input;
    public final String filesDir = "./";
    protected XMLInputFactory inputFactory;
    protected XMLOutputFactory outputFactory;

    /**
     * test without peek
     */
    @Test
    public void testWOPeek() throws Exception {
        XMLEventReader er = getReader();
        XMLEvent e = er.nextEvent();
        assertEquals(XMLStreamConstants.START_DOCUMENT, e.getEventType());
        // we have two start elements in this file
        assertEquals(XMLStreamConstants.START_ELEMENT, er.nextEvent().getEventType());
        assertEquals(XMLStreamConstants.START_ELEMENT, er.nextEvent().getEventType());
    }

    /**
     * test with peek
     */
    @Test
    public void testWPeek() throws Exception {
        XMLEventReader er = getReader();
        XMLEvent e = er.nextEvent();
        assertEquals(XMLStreamConstants.START_DOCUMENT, e.getEventType());
        // we have two start elements in this file
        while (er.peek().getEventType() == XMLStreamConstants.START_ELEMENT) {
            e = er.nextEvent();
        }
        assertEquals(XMLStreamConstants.START_ELEMENT, e.getEventType());
    }

    private XMLEventReader getReader() throws Exception {
        inputFactory = XMLInputFactory.newInstance();
        input = new File(getClass().getResource("play.xml").getFile());

        // Check if event reader returns the correct event
        return inputFactory.createXMLEventReader(
                inputFactory.createXMLStreamReader(new java.io.FileInputStream(input), "UTF-8"));
    }
}
