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
import javax.xml.stream.XMLStreamWriter;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/*
 * @test
 * @library /javax/xml/jaxp/unittest
 * @run junit/othervm stream.XMLStreamWriterTest.EmptyElementTest
 * @summary Test XMLStreamWriter writes namespace and attribute after writeEmptyElement.
 */
public class EmptyElementTest {

    // expected output
    private static final String EXPECTED_OUTPUT = "<?xml version=\"1.0\" ?>" + "<hello xmlns=\"http://hello\">"
            + "<world xmlns=\"http://world\" prefixes=\"foo bar\"/>" + "</hello>";

    XMLStreamWriter xmlStreamWriter;
    ByteArrayOutputStream byteArrayOutputStream;
    XMLOutputFactory xmlOutputFactory;

    @Test
    public void testWriterOnLinux() throws Exception {

        // setup XMLStreamWriter
        byteArrayOutputStream = new ByteArrayOutputStream();
        xmlOutputFactory = XMLOutputFactory.newInstance();
        xmlOutputFactory.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, true);
        xmlStreamWriter = xmlOutputFactory.createXMLStreamWriter(byteArrayOutputStream);

        // create & write a document
        xmlStreamWriter.writeStartDocument();
        xmlStreamWriter.writeStartElement("hello");
        xmlStreamWriter.writeDefaultNamespace("http://hello");
        xmlStreamWriter.writeEmptyElement("world");
        xmlStreamWriter.writeDefaultNamespace("http://world");
        xmlStreamWriter.writeAttribute("prefixes", "foo bar");
        xmlStreamWriter.writeEndElement();
        xmlStreamWriter.writeEndDocument();
        xmlStreamWriter.flush();
        String actualOutput = byteArrayOutputStream.toString();
        assertEquals(EXPECTED_OUTPUT, actualOutput);
    }
}
