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

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test
 * @library /javax/xml/jaxp/unittest
 * @run junit/othervm stream.XMLStreamReaderTest.DoubleXmlnsTest
 * @summary Test double namespaces and nested namespaces.
 */
public class DoubleXmlnsTest {

    @Test
    public void testDoubleNS() throws Exception {
        final String INVALID_XML = "<foo xmlns:xmli='http://www.w3.org/XML/1998/namespacei' xmlns:xmli='http://www.w3.org/XML/1998/namespacei' />";
        XMLStreamReader xsr = XMLInputFactory.newInstance().createXMLStreamReader(new StringReader(INVALID_XML));
        assertThrows(XMLStreamException.class, () -> consumeAllEvents(xsr));
    }

    @Test
    public void testNestedNS() throws Exception {
        final String VALID_XML = "<foo xmlns:xmli='http://www.w3.org/XML/1998/namespacei'><bar xmlns:xmli='http://www.w3.org/XML/1998/namespaceii'></bar></foo>";
        XMLStreamReader xsr = XMLInputFactory.newInstance().createXMLStreamReader(new StringReader(VALID_XML));
        consumeAllEvents(xsr);
    }

    @Test
    public void testDoubleXmlns() throws Exception {
        final String INVALID_XML = "<foo xmlns:xml='http://www.w3.org/XML/1998/namespace' xmlns:xml='http://www.w3.org/XML/1998/namespace' ></foo>";
        XMLStreamReader xsr = XMLInputFactory.newInstance().createXMLStreamReader(new StringReader(INVALID_XML));
        assertThrows(XMLStreamException.class, () -> consumeAllEvents(xsr));
    }

    @Test
    public void testNestedXmlns() throws Exception {
        final String VALID_XML = "<foo xmlns:xml='http://www.w3.org/XML/1998/namespace'><bar xmlns:xml='http://www.w3.org/XML/1998/namespace'></bar></foo>";
        XMLStreamReader xsr = XMLInputFactory.newInstance().createXMLStreamReader(new StringReader(VALID_XML));
        consumeAllEvents(xsr);
    }

    private static void consumeAllEvents(XMLStreamReader xsr) throws XMLStreamException {
        while (xsr.hasNext()) {
            xsr.next();
        }
    }
}
