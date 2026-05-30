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

package stream.XMLOutputFactoryTest;

import org.junit.jupiter.api.Test;

import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.stax.StAXResult;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test
 * @library /javax/xml/jaxp/unittest
 * @run junit/othervm stream.XMLOutputFactoryTest.StreamResultTest
 * @summary Test create XMLWriter with variant Result.
 */
public class StreamResultTest {

    @Test
    public void testStreamResult() throws Exception {
        final String EXPECTED_OUTPUT = "<?xml version=\"1.0\"?><root></root>";

        XMLOutputFactory ofac = XMLOutputFactory.newInstance();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        StreamResult sr = new StreamResult(buffer);
        XMLStreamWriter writer = ofac.createXMLStreamWriter(sr);
        writer.writeStartDocument("1.0");
        writer.writeStartElement("root");
        writer.writeEndElement();
        writer.writeEndDocument();
        writer.close();
        assertEquals(EXPECTED_OUTPUT, buffer.toString());
    }

    @Test
    public void testStreamWriterWithStAXResultNStreamWriter() throws Exception {
        final String EXPECTED_OUTPUT = "<?xml version=\"1.0\"?><root></root>";

        XMLOutputFactory ofac = XMLOutputFactory.newInstance();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        XMLStreamWriter writer = ofac.createXMLStreamWriter(buffer);
        StAXResult res = new StAXResult(writer);
        writer = ofac.createXMLStreamWriter(res);
        writer.writeStartDocument("1.0");
        writer.writeStartElement("root");
        writer.writeEndElement();
        writer.writeEndDocument();
        writer.close();
        assertEquals(EXPECTED_OUTPUT, buffer.toString());
    }

    @Test
    public void testEventWriterWithStAXResultNStreamWriter() throws Exception {
        String encoding = "";
        if (Charset.defaultCharset().equals(StandardCharsets.UTF_8)) {
            encoding = " encoding=\"UTF-8\"";
        }
        final String EXPECTED_OUTPUT = "<?xml version=\"1.0\"" + encoding + "?><root></root>";

        XMLOutputFactory ofac = XMLOutputFactory.newInstance();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        XMLStreamWriter swriter = ofac.createXMLStreamWriter(buffer);
        StAXResult res = new StAXResult(swriter);
        XMLEventWriter writer = ofac.createXMLEventWriter(res);

        XMLEventFactory efac = XMLEventFactory.newInstance();
        writer.add(efac.createStartDocument(null, "1.0"));
        writer.add(efac.createStartElement("", "", "root"));
        writer.add(efac.createEndElement("", "", "root"));
        writer.add(efac.createEndDocument());
        writer.close();

        assertEquals(buffer.toString(), EXPECTED_OUTPUT);
    }

    @Test
    public void testEventWriterWithStAXResultNEventWriter() throws Exception {
        String encoding = "";
        if (Charset.defaultCharset().equals(StandardCharsets.UTF_8)) {
            encoding = " encoding=\"UTF-8\"";
        }
        final String EXPECTED_OUTPUT = "<?xml version=\"1.0\"" + encoding + "?><root></root>";

        XMLOutputFactory ofac = XMLOutputFactory.newInstance();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        XMLEventWriter writer = ofac.createXMLEventWriter(buffer);
        StAXResult res = new StAXResult(writer);
        writer = ofac.createXMLEventWriter(res);

        XMLEventFactory efac = XMLEventFactory.newInstance();
        writer.add(efac.createStartDocument(null, "1.0"));
        writer.add(efac.createStartElement("", "", "root"));
        writer.add(efac.createEndElement("", "", "root"));
        writer.add(efac.createEndDocument());
        writer.close();

        assertEquals(buffer.toString(), EXPECTED_OUTPUT);
    }

    @Test
    public void testStreamWriterWithStAXResultNEventWriter() throws Exception {
        XMLOutputFactory ofac = XMLOutputFactory.newInstance();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        XMLEventWriter writer = ofac.createXMLEventWriter(buffer);
        StAXResult res = new StAXResult(writer);
        assertThrows(UnsupportedOperationException.class, () -> ofac.createXMLStreamWriter(res));
    }
}
