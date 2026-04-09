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

package stream;

import org.junit.jupiter.api.Test;

import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.util.StreamReaderDelegate;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @library /javax/xml/jaxp/unittest
 * @run junit/othervm stream.StreamReaderDelegateTest
 * @summary Test StreamReaderDelegate.
 */
public class StreamReaderDelegateTest {

    /**
     * Tested xml file looks as below: <?xml version="1.0" standalone="no" ?>
     * <ns1:foo attr1="defaultAttr1" ns1:attr1="ns1Attr1" ns2:attr1="ns2Attr1"
     * attr2="defaultAttr2" attr3="defaultAttr3" xmlns:ns1="http://ns1.java.com"
     * xmlns:ns2="http://ns2.java.com"> <!--description--> content text
     * <![CDATA[<greeting>Hello</greeting>]]> other content </ns1:foo>
     **/
    @Test
    public void testAttribute() throws Exception {
        XMLInputFactory ifac = XMLInputFactory.newFactory();
        XMLStreamReader reader = ifac.createXMLStreamReader(new FileInputStream(getClass().getResource("testfile1.xml").getFile()));
        StreamReaderDelegate delegate = new StreamReaderDelegate(reader);
        try {

            assertTrue(delegate.standaloneSet());
            assertFalse(delegate.isStandalone());
            while (delegate.hasNext()) {
                delegate.next();
                if (delegate.getEventType() == XMLStreamConstants.START_ELEMENT || delegate.getEventType() == XMLStreamConstants.ATTRIBUTE) {
                    if (delegate.getLocalName().equals("foo")) {
                        assertEquals(5, delegate.getAttributeCount());
                        assertSame("CDATA", delegate.getAttributeType(1));

                        assertEquals("defaultAttr1", delegate.getAttributeValue(0));
                        assertEquals("defaultAttr2", delegate.getAttributeValue(delegate.getAttributeCount() - 2));
                        assertEquals("defaultAttr3", delegate.getAttributeValue(delegate.getAttributeCount() - 1));

                        assertEquals("ns1Attr1", delegate.getAttributeValue("http://ns1.java.com", "attr1"));
                        assertEquals("ns2Attr1", delegate.getAttributeValue("http://ns2.java.com", "attr1"));

                        assertEquals("defaultAttr2", delegate.getAttributeValue(null, "attr2"));
                        assertEquals("defaultAttr3", delegate.getAttributeValue(null, "attr3"));

                        assertNull(delegate.getAttributeNamespace(0));
                        assertEquals("http://ns1.java.com", delegate.getAttributeNamespace(1));
                        assertEquals("ns1", delegate.getAttributePrefix(1));
                        assertEquals(delegate.getAttributeName(1).toString(), "{" + delegate.getAttributeNamespace(1) + "}" + delegate.getAttributeLocalName(1));
                        assertEquals("attr1", delegate.getAttributeLocalName(1));

                        // negative test. Should return null for out of
                        // attribute array index
                        assertNull(delegate.getAttributeNamespace(delegate.getAttributeCount()));
                        assertNull(delegate.getAttributePrefix(delegate.getAttributeCount()));
                        assertNull(delegate.getAttributeName(delegate.getAttributeCount()));
                        assertNull(delegate.getAttributeLocalName(delegate.getAttributeCount()));
                        assertNull(delegate.getAttributeType(delegate.getAttributeCount()));
                    }
                } else {
                    assertThrows(IllegalStateException.class, delegate::getAttributeCount);
                }
            }
        } finally {
            delegate.close();
        }
    }

    /**
     * Tested xml file looks as below: <?xml version="1.0" encoding="UTF-8"?>
     * <ns1:foo xmlns:ns="http://ns1.java.com" xmlns:ns1="http://ns1.java.com"
     * xmlns:ns2="http://ns2.java.com" > <!--description-->content text
     * <![CDATA[<greeting>Hello</greeting>]]> other content </ns1:foo>
     **/
    @Test
    public void testNamespace() throws Exception {
        XMLStreamReader reader = XMLInputFactory.newFactory().createXMLStreamReader(
                new FileInputStream(getClass().getResource("testfile2.xml").getFile()));
        StreamReaderDelegate delegate = new StreamReaderDelegate();
        try {
            delegate.setParent(reader);
            while (delegate.hasNext()) {
                delegate.next();
                if (delegate.getEventType() == XMLStreamConstants.START_ELEMENT || delegate.getEventType() == XMLStreamConstants.ATTRIBUTE) {

                    if (delegate.getName().getLocalPart().equals("foo")) {
                        assertEquals(("{" + delegate.getNamespaceURI(delegate.getPrefix()) + "}" + delegate.getLocalName()), delegate.getName()
                                .toString());
                        System.out.println(delegate.getLocation());

                        assertEquals(3, delegate.getNamespaceCount());
                        assertEquals("http://ns1.java.com", delegate.getNamespaceURI());
                        assertEquals("http://ns2.java.com", delegate.getNamespaceURI(2));
                        assertEquals("http://ns1.java.com", delegate.getNamespaceURI("ns"));

                        assertEquals("ns1", delegate.getNamespacePrefix(1));

                        NamespaceContext nsCtx = delegate.getNamespaceContext();
                        nsCtx.getNamespaceURI("ns");
                        Iterator<String> prefixes = nsCtx.getPrefixes("http://ns1.java.com");
                        boolean hasns = false;
                        boolean hasns1 = false;
                        while (prefixes.hasNext()) {
                            String prefix = (String) prefixes.next();
                            if (prefix.equals("ns")) {
                                hasns = true;
                            } else if (prefix.equals("ns1")) {
                                hasns1 = true;
                            }
                        }
                        assertTrue(hasns && hasns1);
                    }
                }
            }
        } finally {
            delegate.close();
        }
    }

    /**
     * <?xml version="1.0" encoding="utf-8" ?> <ns1:foo
     * xmlns:ns1="http://ns1.java.com" xmlns:ns2="http://ns2.java.com">
     * <!--description--> content text <![CDATA[<greeting>Hello</greeting>]]>
     * other content </ns1:foo>
     **/
    @Test
    public void testText() throws Exception {
        String property = "javax.xml.stream.isCoalescing";
        XMLInputFactory ifac = XMLInputFactory.newFactory();
        ifac.setProperty(property, Boolean.TRUE);
        XMLStreamReader reader = ifac.createXMLStreamReader(new FileInputStream(getClass().getResource("testfile3.xml").getFile()), "iso8859-1");
        StreamReaderDelegate delegate = new StreamReaderDelegate();
        try {
            delegate.setParent(reader);

            assertEquals(delegate.getParent(), reader);
            assertEquals(Boolean.TRUE, delegate.getProperty(property));
            assertTrue(delegate.getCharacterEncodingScheme().equalsIgnoreCase("utf-8"));
            assertTrue(delegate.getEncoding().equalsIgnoreCase("iso8859-1"));
            assertEquals("1.0", delegate.getVersion());
            while (delegate.hasNext()) {
                delegate.next();
                if (delegate.getEventType() == XMLStreamConstants.CHARACTERS) {
                    char[] target1 = new char[delegate.getTextLength()];
                    delegate.getTextCharacters(delegate.getTextStart(), target1, 0, target1.length);
                    char[] target2 = delegate.getTextCharacters();

                    assertEquals(delegate.getText().trim(), new String(target1).trim());
                    assertEquals(delegate.getText().trim(), new String(target2).trim());
                }
            }
        } finally {
            delegate.close();
        }
    }

    @Test
    public void testWhiteSpace() throws Exception {
        XMLInputFactory ifac = XMLInputFactory.newFactory();
        ifac.setProperty("javax.xml.stream.isCoalescing", Boolean.TRUE);
        XMLStreamReader reader = ifac.createXMLStreamReader(new FileInputStream(getClass().getResource("testfile4.xml").getFile()));

        StreamReaderDelegate delegate = new StreamReaderDelegate();
        try {
            delegate.setParent(reader);
            while (delegate.hasNext()) {
                int i = delegate.next();
                switch (i) {
                    case XMLStreamConstants.CHARACTERS: {
                        assertTrue(delegate.isCharacters());
                        assertTrue(delegate.hasText());
                        assertTrue(delegate.isWhiteSpace());
                        break;
                    }
                    case XMLStreamConstants.START_ELEMENT: {
                        assertTrue(delegate.isStartElement());
                        assertTrue(delegate.isAttributeSpecified(0));
                        assertTrue(delegate.hasName());
                        delegate.require(XMLStreamConstants.START_ELEMENT, delegate.getNamespaceURI(), delegate.getLocalName());
                        break;
                    }
                    case XMLStreamConstants.END_ELEMENT: {
                        assertTrue(delegate.isEndElement());
                        assertTrue(delegate.hasName());
                        delegate.require(XMLStreamConstants.END_ELEMENT, delegate.getNamespaceURI(), delegate.getLocalName());
                        break;
                    }
                }
            }
        } finally {
            delegate.close();
        }

    }

    @Test
    public void testElementText() throws Exception {
        XMLInputFactory ifac = XMLInputFactory.newFactory();
        XMLStreamReader reader = ifac.createXMLStreamReader(new FileInputStream(getClass().getResource("toys.xml").getFile()));

        StreamReaderDelegate delegate = new StreamReaderDelegate();
        try {
            delegate.setParent(reader);
            while (delegate.hasNext()) {
                if (delegate.getEventType() == XMLStreamConstants.START_ELEMENT) {
                    if (delegate.getLocalName().equals("name") || delegate.getLocalName().equals("price")) {
                        System.out.println(delegate.getElementText());
                    }
                    delegate.nextTag();
                } else {
                    delegate.next();
                }
            }
        } finally {
            delegate.close();
        }
    }

    @Test
    public void testPITargetAndData() throws Exception {
        XMLInputFactory xif = XMLInputFactory.newInstance();
        String PITarget = "soffice";
        String PIData = "WebservicesArchitecture";
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + "<?" + PITarget + " " + PIData + "?>" + "<foo></foo>";
        InputStream is = new java.io.ByteArrayInputStream(xml.getBytes());
        XMLStreamReader sr = xif.createXMLStreamReader(is);
        StreamReaderDelegate delegate = new StreamReaderDelegate(sr);
        try {
            while (delegate.hasNext()) {
                int eventType = delegate.next();
                if (eventType == XMLStreamConstants.PROCESSING_INSTRUCTION) {
                    String target = delegate.getPITarget();
                    String data = delegate.getPIData();
                    assertEquals(PITarget, target);
                    assertEquals(PIData, data);
                }
            }
        } finally {
            delegate.close();
        }
    }
}
