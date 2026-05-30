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

import javax.xml.stream.Location;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.events.XMLEvent;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;

/*
 * @test
 * @library /javax/xml/jaxp/unittest
 * @run junit/othervm stream.EventsTest.Issue58Test
 * @summary Test XMLEvent.getLocation() returns a non-volatile Location.
 */
public class Issue58Test {

    @Test
    public void testLocation() throws Exception {
        String XML = "<?xml version='1.0' ?>" + "<!DOCTYPE root [\n" + "<!ENTITY intEnt 'internal'>\n" + "<!ENTITY extParsedEnt SYSTEM 'url:dummy'>\n"
                + "<!NOTATION notation PUBLIC 'notation-public-id'>\n" + "<!NOTATION notation2 SYSTEM 'url:dummy'>\n"
                + "<!ENTITY extUnparsedEnt SYSTEM 'url:dummy2' NDATA notation>\n" + "]>\n" + "<root />";

        XMLEventReader er = getReader(XML);
        XMLEvent evt = er.nextEvent(); // StartDocument
        Location loc1 = evt.getLocation();
        evt = er.nextEvent(); // DTD
        // loc1 should not change so its line number should still be 1
        assertEquals(1, loc1.getLineNumber());

        Location loc2 = evt.getLocation();
        assertEquals(1, loc1.getLineNumber());
        assertEquals(7, loc2.getLineNumber());
    }

    private XMLEventReader getReader(String XML) throws Exception {
        XMLInputFactory inputFactory = XMLInputFactory.newInstance();

        // Check if event reader returns the correct event
        return inputFactory.createXMLEventReader(new StringReader(XML));
    }
}
