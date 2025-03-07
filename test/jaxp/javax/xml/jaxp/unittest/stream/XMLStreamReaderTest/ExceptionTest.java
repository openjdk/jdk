/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test
 * @bug 8327378
 * @summary Verifies exception handling
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest
 * @run junit stream.XMLStreamReaderTest.ExceptionTest
 */
public class ExceptionTest {

    /**
     * Verifies that the XMLStreamReader throws XMLStreamException instead of EOFException.
     * The specification for the XMLStreamReader's next method:
     * Throws: XMLStreamException - if there is an error processing the underlying XML source
     * @throws Exception if the test fails
     */
    @Test
    public void testExpectedException() throws Exception {
        XMLInputFactory xmlInputFactory = XMLInputFactory.newDefaultFactory();
        XMLStreamReader xmlStreamReader = xmlInputFactory.createXMLStreamReader(
                new StringReader("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><!DOCTYPE foo [ <!ELEMENT foo ANY ><!ENTITY"));

        assertThrows(XMLStreamException.class, () -> {
            while (xmlStreamReader.hasNext()) {
                int event = xmlStreamReader.next();
            }
        });
    }
}
