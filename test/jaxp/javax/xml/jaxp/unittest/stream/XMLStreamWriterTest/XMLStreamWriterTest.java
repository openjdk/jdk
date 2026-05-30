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
package stream.XMLStreamWriterTest;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.events.XMLEvent;
import javax.xml.transform.dom.DOMResult;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @bug 6347190 8139584 8216408
 * @library /javax/xml/jaxp/unittest
 * @run junit/othervm stream.XMLStreamWriterTest.XMLStreamWriterTest
 * @summary Tests XMLStreamWriter.
 */
public class XMLStreamWriterTest {
    /**
     * @bug 8139584
     * Verifies that the resulting XML contains the standalone setting.
     */
    @Test
    public void testCreateStartDocument() throws XMLStreamException {

        StringWriter stringWriter = new StringWriter();
        XMLOutputFactory out = XMLOutputFactory.newInstance();
        XMLEventFactory eventFactory = XMLEventFactory.newInstance();

        XMLEventWriter eventWriter = out.createXMLEventWriter(stringWriter);

        XMLEvent event = eventFactory.createStartDocument("iso-8859-15", "1.0", true);
        eventWriter.add(event);
        eventWriter.flush();
        assertTrue(stringWriter.toString().contains("encoding=\"iso-8859-15\""));
        assertTrue(stringWriter.toString().contains("version=\"1.0\""));
        assertTrue(stringWriter.toString().contains("standalone=\"yes\""));
    }

    /**
     * @bug 8139584
     * Verifies that the resulting XML contains the standalone setting.
     */
    @Test
    public void testCreateStartDocument_DOMWriter() throws Exception {

        XMLOutputFactory xof = XMLOutputFactory.newInstance();
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.newDocument();
        XMLEventWriter eventWriter = xof.createXMLEventWriter(new DOMResult(doc));
        XMLEventFactory eventFactory = XMLEventFactory.newInstance();
        XMLEvent event = eventFactory.createStartDocument("iso-8859-15", "1.0", true);
        eventWriter.add(event);
        eventWriter.flush();
        assertEquals("iso-8859-15", doc.getXmlEncoding());
        assertEquals("1.0", doc.getXmlVersion());
        assertTrue(doc.getXmlStandalone());
    }

    /**
     * Verifies that the StAX Writer won't insert comment into the element tag.
     */
    @Test
    public void testWriteComment() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<a:html href=\"http://java.sun.com\">"
                + "<!--This is comment-->java.sun.com</a:html>";
        XMLOutputFactory f = XMLOutputFactory.newInstance();
        // f.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES,
        // Boolean.TRUE);
        StringWriter sw = new StringWriter();
        XMLStreamWriter writer = f.createXMLStreamWriter(sw);
        writer.writeStartDocument("UTF-8", "1.0");
        writer.writeStartElement("a", "html", "http://www.w3.org/TR/REC-html40");
        writer.writeAttribute("href", "http://java.sun.com");
        writer.writeComment("This is comment");
        writer.writeCharacters("java.sun.com");
        writer.writeEndElement();
        writer.writeEndDocument();
        writer.flush();
        sw.flush();
        StringBuffer sb = sw.getBuffer();
        System.out.println("sb:" + sb.toString());
        assertEquals(xml, sb.toString());
    }

    /**
     * @bug 8216408
     * Verifies that setDefaultNamespace accepts null.
     */
    @Test
    public void testSetDefaultNamespace() throws Exception {
        XMLOutputFactory f = XMLOutputFactory.newFactory();
        f.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, true);
        StringWriter sw = new StringWriter();
        XMLStreamWriter xsw = f.createXMLStreamWriter(sw);
        xsw.setDefaultNamespace(null);
    }
}
