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

package stream.XMLEventWriterTest;

import org.junit.jupiter.api.Test;

import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/*
 * @test
 * @library /javax/xml/jaxp/unittest
 * @run junit/othervm stream.XMLEventWriterTest.ReaderToWriterTest
 * @summary Test XMLEventWriter.
 */
public class ReaderToWriterTest {

    private static final XMLEventFactory XML_EVENT_FACTORY = XMLEventFactory.newInstance();
    private static final XMLInputFactory XML_INPUT_FACTORY = XMLInputFactory.newInstance();
    private static final XMLOutputFactory XML_OUTPUT_FACTORY = XMLOutputFactory.newInstance();

    private static final String INPUT_FILE = "W2JDLR4002TestService.wsdl.data";
    private static final String OUTPUT_FILE = "Encoded.wsdl";

    /**
     * Unit test for writing namespaces when namespaceURI == null.
     */
    @Test
    public void testWriteNamespace() throws Exception {

        /* Platform default encoding. */
        final String DEFAULT_CHARSET = java.nio.charset.Charset.defaultCharset().name();
        System.out.println("DEFAULT_CHARSET = " + DEFAULT_CHARSET);

        final String EXPECTED_OUTPUT = "<?xml version=\"1.0\" encoding=\"" + DEFAULT_CHARSET + "\"?><prefix:root xmlns=\"\" xmlns:null=\"\"></prefix:root>";

        // new Writer
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        XMLEventWriter xmlEventWriter = XML_OUTPUT_FACTORY.createXMLEventWriter(byteArrayOutputStream);

        // start a valid event stream
        XMLEvent startDocumentEvent = XML_EVENT_FACTORY.createStartDocument(DEFAULT_CHARSET);
        XMLEvent startElementEvent = XML_EVENT_FACTORY.createStartElement("prefix", "http://example.com", "root");
        xmlEventWriter.add(startDocumentEvent);
        xmlEventWriter.add(startElementEvent);

        // try using a null default namespaceURI
        XMLEvent namespaceEvent = XML_EVENT_FACTORY.createNamespace(null);
        xmlEventWriter.add(namespaceEvent);

        // try using a null prefix'd namespaceURI
        XMLEvent namespacePrefixEvent = XML_EVENT_FACTORY.createNamespace("null", null);
        xmlEventWriter.add(namespacePrefixEvent);

        // close event stream
        XMLEvent endElementEvent = XML_EVENT_FACTORY.createEndElement("prefix", "http://example.com", "root");
        XMLEvent endDocumentEvent = XML_EVENT_FACTORY.createEndDocument();
        xmlEventWriter.add(endElementEvent);
        xmlEventWriter.add(endDocumentEvent);
        xmlEventWriter.flush();

        // get XML document as String
        String actualOutput = byteArrayOutputStream.toString();

        // is output as expected?
        assertEquals(EXPECTED_OUTPUT, actualOutput);
    }

    /**
     * Test: 6419687 NPE in XMLEventWriterImpl.
     */
    @Test
    public void testCR6419687() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("ReaderToWriterTest.wsdl");
             OutputStream out = new FileOutputStream("ReaderToWriterTest-out.xml")) {

            XMLEventReader reader = XML_INPUT_FACTORY.createXMLEventReader(in);
            XMLEventWriter writer = XML_OUTPUT_FACTORY.createXMLEventWriter(out, "UTF-8");
            while (reader.hasNext()) {
                XMLEvent event = reader.nextEvent();
                writer.add(event);
            }
            reader.close();
            writer.close();
        }
    }

    /*
     * Reads UTF-16 encoding file and writes it to UTF-8 encoded format.
     */
    @Test
    public void testUTF8Encoding() throws Exception {
        try (InputStream in = util.BOMInputStream.createStream("UTF-16BE", this.getClass().getResourceAsStream(INPUT_FILE));
             OutputStream out = new FileOutputStream(OUTPUT_FILE)) {

            XMLEventReader reader = XML_INPUT_FACTORY.createXMLEventReader(in);
            XMLEventWriter writer = XML_OUTPUT_FACTORY.createXMLEventWriter(out, "UTF-8");

            writeEvents(reader, writer);
            checkOutput(OUTPUT_FILE);
        } finally {
            Files.deleteIfExists(Path.of(OUTPUT_FILE));
        }
    }

    private void writeEvents(XMLEventReader reader, XMLEventWriter writer) throws XMLStreamException {
        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();
            writer.add(event);
        }
        reader.close();
        writer.close();
    }

    private void checkOutput(String output) throws Exception {
        InputStream in = new FileInputStream(output);
        XMLEventReader reader = XML_INPUT_FACTORY.createXMLEventReader(in);
        while (reader.hasNext()) {
            reader.next();
        }
        reader.close();
    }

    /*
     * Reads UTF-16 encoding file and writes it with default encoding.
     */
    @Test
    public void testNoEncoding() throws Exception {
        try (InputStream in = util.BOMInputStream.createStream("UTF-16BE", this.getClass().getResourceAsStream(INPUT_FILE));
             OutputStream out = new FileOutputStream(OUTPUT_FILE)) {

            XMLEventReader reader = XML_INPUT_FACTORY.createXMLEventReader(in);
            XMLEventWriter writer = XML_OUTPUT_FACTORY.createXMLEventWriter(out);

            writeEvents(reader, writer);
            checkOutput(OUTPUT_FILE);
        } finally {
            Files.deleteIfExists(Path.of(OUTPUT_FILE));
        }
    }
}
