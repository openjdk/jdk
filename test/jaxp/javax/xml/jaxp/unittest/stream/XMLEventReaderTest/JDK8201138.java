/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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

import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.Comment;
import javax.xml.stream.events.StartDocument;
import javax.xml.stream.events.StartElement;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/*
 * @test
 * @bug 8201138
 * @library /javax/xml/jaxp/unittest
 * @run junit/othervm stream.XMLEventReaderTest.JDK8201138
 * @summary Verifies a fix that set the type and data properly in the loop
 */
public class JDK8201138 {

    @Test
    public void testTypeReset() throws XMLStreamException, FactoryConfigurationError {

        String xmlData = "<?xml version=\"1.0\"?><nextEvent><!-- peeked -->aaa<![CDATA[bbb]]>ccc</nextEvent>";

        XMLEventReader eventReader = XMLInputFactory.newFactory().createXMLEventReader(new StringReader(xmlData));
        assertInstanceOf(StartDocument.class, eventReader.nextEvent(), "shall be StartDocument");
        assertInstanceOf(StartElement.class, eventReader.nextEvent(), "shall be StartElement");
        assertInstanceOf(Comment.class, eventReader.peek(), "shall be Comment");
        // the following returns empty string before the fix
        assertEquals("aaabbbccc", eventReader.getElementText(), "The text shall be \"aaabbbccc\"");

        eventReader.close();
    }

    @Test
    public void testTypeResetAndBufferClear() throws XMLStreamException, FactoryConfigurationError {

        String xmlData = "<?xml version=\"1.0\"?><nextEvent>aaa<!-- comment --></nextEvent>";

        XMLEventReader eventReader = XMLInputFactory.newFactory().createXMLEventReader(new StringReader(xmlData));
        assertInstanceOf(StartDocument.class, eventReader.nextEvent(), "shall be StartDocument");
        assertInstanceOf(StartElement.class, eventReader.nextEvent(), "shall be StartElement");
        assertInstanceOf(Characters.class, eventReader.peek(), "shall be Characters");
        // the following throws ClassCastException before the fix
        assertEquals("aaa", eventReader.getElementText(), "The text shall be \"aaa\"");

        eventReader.close();
    }

}
