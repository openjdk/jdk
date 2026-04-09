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

package stream.EventsTest;

import org.junit.jupiter.api.Test;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.events.DTD;
import javax.xml.stream.events.EntityDeclaration;
import javax.xml.stream.events.NotationDeclaration;
import javax.xml.stream.events.XMLEvent;
import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/*
 * @test
 * @bug 6620632
 * @library /javax/xml/jaxp/unittest
 * @run junit/othervm stream.EventsTest.Issue48Test
 * @summary Test XMLEventReader can parse notation and entity information from DTD Event.
 */
public class Issue48Test {
    /**
     * DTDEvent instances constructed via event reader are missing the notation
     * and entity declaration information
     */
    @Test
    public void testDTDEvent() throws Exception {
        String XML = "<?xml version='1.0' ?>" + "<!DOCTYPE root [\n" + "<!ENTITY intEnt 'internal'>\n" + "<!ENTITY extParsedEnt SYSTEM 'url:dummy'>\n"
                + "<!NOTATION notation PUBLIC 'notation-public-id'>\n" + "<!NOTATION notation2 SYSTEM 'url:dummy'>\n"
                + "<!ENTITY extUnparsedEnt SYSTEM 'url:dummy2' NDATA notation>\n" + "]>" + "<root />";

        XMLEventReader er = getReader(XML);
        er.nextEvent(); // StartDocument
        XMLEvent evt = er.nextEvent(); // DTD
        assertEquals(XMLStreamConstants.DTD, evt.getEventType());
        DTD dtd = (DTD) evt;
        List<EntityDeclaration> entities = dtd.getEntities();
        assertNotNull(entities);
        assertEquals(3, entities.size());

        List<NotationDeclaration> notations = dtd.getNotations();
        assertNotNull(notations);
        assertEquals(2, notations.size());
    }

    private XMLEventReader getReader(String XML) throws Exception {
        XMLInputFactory inputFactory = XMLInputFactory.newInstance();

        // Check if event reader returns the correct event
        return inputFactory.createXMLEventReader(new StringReader(XML));
    }
}
