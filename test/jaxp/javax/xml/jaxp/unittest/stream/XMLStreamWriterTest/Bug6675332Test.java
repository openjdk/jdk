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
import util.BaseStAXUT;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;

/*
 * @test
 * @bug 6675332
 * @library /javax/xml/jaxp/unittest
 * @run junit/othervm stream.XMLStreamWriterTest.Bug6675332Test
 * @summary Test XMLStreamWriter writeAttribute when IS_REPAIRING_NAMESPACES is true.
 */
public class Bug6675332Test extends BaseStAXUT {

    private static final XMLOutputFactory XML_OUTPUT_FACTORY = XMLOutputFactory.newInstance();

    @Test
    public void test() throws Exception {
        final String URL_DEF = "urn:default";
        final String ATTR_VALUE = "'value\"";

        XML_OUTPUT_FACTORY.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, Boolean.TRUE);

        final String EXPECTED_OUTPUT = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><root></root>";
        XMLStreamWriter w = null;
        StringWriter strw = new StringWriter();

        w = XML_OUTPUT_FACTORY.createXMLStreamWriter(strw);

        w.writeStartDocument();

        /*
         * Calling this method should be optional; but if we call it,
         * exceptation is that it does properly bind the prefix and URL as
         * the 'preferred' combination. In this case we'll just try to make
         * URL bound as the default namespace
         */
        w.setDefaultNamespace(URL_DEF);
        w.writeStartElement(URL_DEF, "test"); // root

        /*
         * And let's further make element and attribute(s) belong to that
         * same namespace
         */
        w.writeStartElement("", "leaf", URL_DEF); // 1st leaf
        w.writeAttribute("", URL_DEF, "attr", ATTR_VALUE);
        w.writeAttribute(URL_DEF, "attr2", ATTR_VALUE);
        w.writeEndElement();

        // w.writeEmptyElement("", "leaf"); // 2nd leaf; in empty/no
        // namespace!

        w.writeStartElement(URL_DEF, "leaf"); // 3rd leaf
        // w.writeAttribute("", "attr2", ATTR_VALUE2); // in empty/no
        // namespace
        w.writeEndElement();

        w.writeEndElement(); // root elem
        w.writeEndDocument();
        w.close();
        System.out.println("\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\\n");
        System.out.println(strw.toString());

        // And then let's parse and verify it all:
        // System.err.println("testAttributes: doc = '"+strw+"'");

        XMLStreamReader sr = constructNsStreamReader(strw.toString());
        assertTokenType(START_DOCUMENT, sr.getEventType(), sr);

        // root element
        assertTokenType(START_ELEMENT, sr.next(), sr);
        assertEquals("test", sr.getLocalName());
        assertEquals(URL_DEF, sr.getNamespaceURI());

        // first leaf:
        assertTokenType(START_ELEMENT, sr.next(), sr);
        assertEquals("leaf", sr.getLocalName());
        assertEquals(URL_DEF, sr.getNamespaceURI());
        System.out.println(sr.getAttributeLocalName(0));
        System.out.println(sr.getAttributeLocalName(1));
        assertEquals(2, sr.getAttributeCount());
        assertEquals("attr", sr.getAttributeLocalName(0));

        String uri = sr.getAttributeNamespace(0);
        assertEquals(URL_DEF, uri, "Expected attribute 'attr' to have NS '" + URL_DEF + "', was " + valueDesc(uri) + "; input = '" + strw + "'");
        assertEquals(ATTR_VALUE, sr.getAttributeValue(0));
        assertTokenType(END_ELEMENT, sr.next(), sr);
        assertEquals("leaf", sr.getLocalName());
        assertEquals(URL_DEF, sr.getNamespaceURI());

        // 2nd/empty leaf
        /*
         * assertTokenType(START_ELEMENT, sr.next(), sr);
         * assertEquals("leaf", sr.getLocalName()); assertNoNsURI(sr);
         * assertTokenType(END_ELEMENT, sr.next(), sr); assertEquals("leaf",
         * sr.getLocalName()); assertNoNsURI(sr);
         */
        // third leaf
        assertTokenType(START_ELEMENT, sr.next(), sr);
        assertEquals("leaf", sr.getLocalName());
        assertEquals(URL_DEF, sr.getNamespaceURI());

        /*
         * attr in 3rd leaf, in empty/no namespace assertEquals(1,
         * sr.getAttributeCount()); assertEquals("attr2",
         * sr.getAttributeLocalName(0));
         * assertNoAttrNamespace(sr.getAttributeNamespace(0));
         * assertEquals(ATTR_VALUE2, sr.getAttributeValue(0));
         */
        assertTokenType(END_ELEMENT, sr.next(), sr);
        assertEquals("leaf", sr.getLocalName());
        assertEquals(URL_DEF, sr.getNamespaceURI());

        // closing root element
        assertTokenType(END_ELEMENT, sr.next(), sr);
        assertEquals("test", sr.getLocalName());
        assertEquals(URL_DEF, sr.getNamespaceURI());

        assertTokenType(END_DOCUMENT, sr.next(), sr);
    }

}
