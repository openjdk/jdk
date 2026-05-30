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

import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test
 * @bug 6394074
 * @library /javax/xml/jaxp/unittest
 * @run junit/othervm stream.XMLStreamWriterTest.UnprefixedNameTest
 * @summary Test XMLStreamWriter namespace prefix with writeDefaultNamespace.
 */
public class UnprefixedNameTest {

    @Test
    public void testUnboundPrefix() throws Exception {
        XMLOutputFactory xof = XMLOutputFactory.newInstance();
        XMLStreamWriter w = xof.createXMLStreamWriter(System.out);
        // here I'm trying to write
        // <bar xmlns="foo" />
        w.writeStartDocument();
        assertThrows(XMLStreamException.class, () -> w.writeStartElement("foo", "bar"),
                "Expected: XMLStreamException if the namespace URI has not been bound to a prefix "
                        + "and javax.xml.stream.isPrefixDefaulting has not been " + "set to true");
    }

    @Test
    public void testBoundPrefix() throws Exception {
        XMLOutputFactory xof = XMLOutputFactory.newInstance();
        XMLStreamWriter w = xof.createXMLStreamWriter(System.out);
        // here I'm trying to write
        // <bar xmlns="foo" />
        w.writeStartDocument();
        w.writeStartElement("foo", "bar", "http://namespace");
        w.writeCharacters("---");
        w.writeEndElement();
        w.writeEndDocument();
        w.close();
    }

    @Test
    public void testRepairingPrefix() throws Exception {
        // repair namespaces
        // use new XMLOutputFactory as changing its property settings
        XMLOutputFactory xof = XMLOutputFactory.newInstance();
        xof.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, Boolean.TRUE);
        XMLStreamWriter w = xof.createXMLStreamWriter(System.out);

        // here I'm trying to write
        // <bar xmlns="foo" />
        w.writeStartDocument();
        w.writeStartElement("foo", "bar");
        w.writeDefaultNamespace("foo");
        w.writeCharacters("---");
        w.writeEndElement();
        w.writeEndDocument();
        w.close();

        // Expected success
        System.out.println("Expected success.");
    }
}
