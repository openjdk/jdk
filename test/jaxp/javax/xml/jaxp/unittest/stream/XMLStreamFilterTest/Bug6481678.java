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

package stream.XMLStreamFilterTest;

import org.junit.jupiter.api.Test;

import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.stream.EventFilter;
import javax.xml.stream.StreamFilter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.XMLEvent;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/*
 * @test
 * @bug 6481678
 * @library /javax/xml/jaxp/unittest
 * @run junit/othervm stream.XMLStreamFilterTest.Bug6481678
 * @summary Test Filtered XMLStreamReader parses namespace correctly.
 */
public class Bug6481678 {

    String rootElement = "fruits";
    String childElement = "varieties";
    String prefixApple = "a";
    String namespaceURIApple = "apple.fruit";
    String prefixOrange = "o";
    String namespaceURIOrange = "orange.fruit";
    String namespaceURIBanana = "banana.fruit";

    TypeFilter filter;
    XMLInputFactory factory;
    InputStream is;

    public Bug6481678() {
        factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.TRUE);
        filter = createFilter();
    }

    String getXML() {
        StringBuilder sbuffer = new StringBuilder();
        sbuffer.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        sbuffer.append("<" + rootElement + " state=\"WA\"");
        sbuffer.append(" xmlns:" + prefixApple + "=\"" + namespaceURIApple + "\"");
        sbuffer.append(" xmlns:" + prefixOrange + "=\"" + namespaceURIOrange + "\"");
        sbuffer.append(" xmlns=\"" + namespaceURIBanana + "\">");
        sbuffer.append("<" + prefixApple + ":" + childElement + ">");
        sbuffer.append("<" + prefixApple + ":fuji/>");
        sbuffer.append("<" + prefixApple + ":gala/>");
        sbuffer.append("</" + prefixApple + ":" + childElement + ">");
        sbuffer.append("</" + rootElement + ">");
        // System.out.println("XML = " + sbuffer.toString()) ;
        return sbuffer.toString();
    }

    TypeFilter createFilter() {

        TypeFilter f = new TypeFilter();

        f.addType(XMLEvent.START_ELEMENT);
        f.addType(XMLEvent.END_ELEMENT);
        f.addType(XMLEvent.PROCESSING_INSTRUCTION);
        f.addType(XMLEvent.CHARACTERS);
        f.addType(XMLEvent.COMMENT);
        f.addType(XMLEvent.SPACE);
        f.addType(XMLEvent.START_DOCUMENT);
        f.addType(XMLEvent.END_DOCUMENT);
        return f;
    }

    /*
     * testcase for cr6481678 in our current impl (using cache), the reader
     * would read from cache when getters are called before next() is. refter to
     * testRootElementNamespace.
     */
    @Test
    public void testReadingNamespace() throws Exception {
        is = new java.io.ByteArrayInputStream(getXML().getBytes());
        XMLStreamReader sr = factory.createFilteredReader(factory.createXMLStreamReader(is), filter);

        while (sr.hasNext()) {
            int eventType = sr.getEventType();
            if (eventType == XMLStreamConstants.START_ELEMENT) {
                if (sr.getLocalName().equals(rootElement)) {
                    assertEquals(prefixApple, sr.getNamespacePrefix(0));
                    assertEquals(namespaceURIApple, sr.getNamespaceURI(0));
                }
            }
            sr.next();
        }
    }

    @Test
    public void testRootElementNamespace() throws Exception {
        is = new java.io.ByteArrayInputStream(getXML().getBytes());
        XMLStreamReader sr = factory.createFilteredReader(factory.createXMLStreamReader(is), filter);

        while (sr.hasNext()) {
            int eventType = sr.next();
            if (eventType == XMLStreamConstants.START_ELEMENT) {
                if (sr.getLocalName().equals(rootElement)) {
                    assertEquals(prefixApple, sr.getNamespacePrefix(0));
                    assertEquals(namespaceURIApple, sr.getNamespaceURI(0));
                }
            }
        }
    }

    @Test
    public void testChildElementNamespace() throws Exception {
        is = new java.io.ByteArrayInputStream(getXML().getBytes());
        XMLStreamReader sr = factory.createFilteredReader(factory.createXMLStreamReader(is), filter);
        while (sr.hasNext()) {
            int eventType = sr.next();
            if (eventType == XMLStreamConstants.START_ELEMENT) {
                if (sr.getLocalName().equals(childElement)) {
                    QName qname = sr.getName();
                    assertEquals(prefixApple, qname.getPrefix());
                    assertEquals(namespaceURIApple, qname.getNamespaceURI());
                    assertEquals(childElement, qname.getLocalPart());
                }
            }
        }
    }

    @Test
    public void testNamespaceContext() throws Exception {
        is = new java.io.ByteArrayInputStream(getXML().getBytes());
        XMLStreamReader sr = factory.createFilteredReader(factory.createXMLStreamReader(is), filter);
        while (sr.hasNext()) {
            int eventType = sr.next();
            if (eventType == XMLStreamConstants.START_ELEMENT) {
                if (sr.getLocalName().equals(childElement)) {
                    NamespaceContext context = sr.getNamespaceContext();
                    assertEquals(context.getPrefix(namespaceURIApple), prefixApple);
                }
            }
        }
    }

    @Test
    public void testNamespaceCount() throws Exception {
        is = new java.io.ByteArrayInputStream(getXML().getBytes());
        XMLStreamReader sr = factory.createFilteredReader(factory.createXMLStreamReader(is), filter);
        while (sr.hasNext()) {
            int eventType = sr.next();
            if (eventType == XMLStreamConstants.START_ELEMENT) {
                if (sr.getLocalName().equals(rootElement)) {
                    int count = sr.getNamespaceCount();
                    assertEquals(3, count);
                }
            }
        }
    }

    static class TypeFilter implements EventFilter, StreamFilter {

        protected boolean[] types = new boolean[20];

        public void addType(int type) {
            types[type] = true;
        }

        public boolean accept(XMLEvent e) {
            return types[e.getEventType()];
        }

        public boolean accept(XMLStreamReader r) {
            return types[r.getEventType()];
        }
    }
}
