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

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test
 * @library /javax/xml/jaxp/unittest
 * @run junit/othervm stream.XMLStreamWriterTest.EncodingTest
 * @summary Test XMLStreamWriter writes a document with encoding setting.
 */
public class EncodingTest {

    private static final XMLOutputFactory XML_OUTPUT_FACTORY = XMLOutputFactory.newInstance();

    /*
     * Tests writing a document with UTF-8 encoding, by setting UTF-8 on writer.
     */
    @Test
    public void testWriteStartDocumentUTF8() throws Exception {

        final String EXPECTED_OUTPUT = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><root></root>";
        XMLStreamWriter writer = null;
        ByteArrayOutputStream byteArrayOutputStream = null;

        byteArrayOutputStream = new ByteArrayOutputStream();
        writer = XML_OUTPUT_FACTORY.createXMLStreamWriter(byteArrayOutputStream, "UTF-8");

        writer.writeStartDocument("UTF-8", "1.0");
        writer.writeStartElement("root");
        writer.writeEndElement();
        writer.writeEndDocument();
        writer.flush();

        String actualOutput = byteArrayOutputStream.toString();
        assertEquals(EXPECTED_OUTPUT, actualOutput);
    }

    /*
     * Tests writing a document with UTF-8 encoding on default enocding writer.
     * This scenario should result in an exception as default encoding is ASCII.
     */
    @Test
    public void testWriteStartDocumentUTF8Fail() throws Exception {
        // pick a different encoding to use v. default encoding
        Charset defaultCharset = Charset.defaultCharset();
        Charset useCharset = defaultCharset.equals(UTF_8) ? US_ASCII : UTF_8;

        System.out.println("defaultCharset = " + defaultCharset + ", useCharset = " + useCharset);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        XMLStreamWriter writer = XML_OUTPUT_FACTORY.createXMLStreamWriter(byteArrayOutputStream);

        assertThrows(XMLStreamException.class, () -> writer.writeStartDocument(useCharset.toString(), "1.0"),
                "Expected XMLStreamException as default underlying stream encoding of " + defaultCharset
                        + " differs from explicitly specified encoding of " + useCharset);
    }
}
