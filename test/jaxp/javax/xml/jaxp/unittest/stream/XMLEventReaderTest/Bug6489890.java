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
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.XMLEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/*
 * @test
 * @bug 6489890
 * @library /javax/xml/jaxp/unittest
 * @run junit/othervm stream.XMLEventReaderTest.Bug6489890
 * @summary Test XMLEventReader's initial state is an undefined state, and nextEvent() is START_DOCUMENT.
 */
public class Bug6489890 {

    @Test
    public void test0() throws Exception {
        XMLInputFactory xif = XMLInputFactory.newInstance();

        XMLStreamReader xsr = xif.createXMLStreamReader(getClass().getResource("sgml.xml").toString(), getClass().getResourceAsStream("sgml.xml"));

        XMLEventReader xer = xif.createXMLEventReader(xsr);

        assertEquals(XMLEvent.START_DOCUMENT, xer.peek().getEventType());
        assertSame(xer.peek(), xer.nextEvent());
        xsr.close();
    }

    @Test
    public void test1() throws Exception {
        XMLInputFactory xif = XMLInputFactory.newInstance();

        XMLStreamReader xsr = xif.createXMLStreamReader(getClass().getResource("sgml.xml").toString(), getClass().getResourceAsStream("sgml.xml"));

        XMLEventReader xer = xif.createXMLEventReader(xsr);

        assertEquals(XMLEvent.START_DOCUMENT, xer.nextEvent().getEventType());
        xsr.close();
    }

}
