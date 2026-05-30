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

package stream.XMLStreamReaderTest;

import org.junit.jupiter.api.Test;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Namespace;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertEquals;

/*
 * @test
 * @library /javax/xml/jaxp/unittest
 * @run junit/othervm stream.XMLStreamReaderTest.DefaultAttributeTest
 * @summary Test StAX parses namespace and attribute.
 */
public class DefaultAttributeTest {

    private static final String INPUT_FILE = "ExternalDTD.xml";

    @Test
    public void testStreamReader() throws Exception {
        XMLInputFactory ifac = XMLInputFactory.newInstance();

        ifac.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, Boolean.FALSE);

        XMLStreamReader re = ifac.createXMLStreamReader(
                this.getClass().getResource(INPUT_FILE).toExternalForm(),
                this.getClass().getResourceAsStream(INPUT_FILE));

        while (re.hasNext()) {
            int event = re.next();
            if (event == XMLStreamConstants.START_ELEMENT && re.getLocalName().equals("bookurn")) {
                assertEquals(0, re.getAttributeCount(), "No attributes are expected for <bookurn> ");
                assertEquals(2, re.getNamespaceCount(), "Two namespaces are expected for <bookurn> ");
            }
        }
    }

    @Test
    public void testEventReader() throws Exception {
        XMLInputFactory ifac = XMLInputFactory.newInstance();
        XMLEventReader read = ifac.createXMLEventReader(this.getClass().getResource(INPUT_FILE).toExternalForm(),
                this.getClass().getResourceAsStream(INPUT_FILE));
        while (read.hasNext()) {
            XMLEvent event = read.nextEvent();
            if (event.isStartElement()) {
                StartElement startElement = event.asStartElement();
                if (startElement.getName().getLocalPart().equals("bookurn")) {
                    Iterator<Namespace> iterator = startElement.getNamespaces();
                    int count = 0;
                    while (iterator.hasNext()) {
                        iterator.next();
                        count++;
                    }
                    assertEquals(2, count, "Two namespaces are expected for <bookurn> ");

                    Iterator<Attribute> attributes = startElement.getAttributes();
                    count = 0;
                    while (attributes.hasNext()) {
                        iterator.next();
                        count++;
                    }
                    assertEquals(0, count, "Zero attributes are expected for <bookurn> ");
                }
            }
        }
    }
}
