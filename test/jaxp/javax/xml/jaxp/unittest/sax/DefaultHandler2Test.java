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

package sax;

import org.junit.jupiter.api.Test;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.DefaultHandler2;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.ParserAdapter;
import org.xml.sax.helpers.XMLFilterImpl;
import org.xml.sax.helpers.XMLReaderFactory;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest
 * @run junit/othervm sax.DefaultHandler2Test
 * @summary Test DefaultHandler2.
 */
public class DefaultHandler2Test {

    @Test
    public void testParse01() throws Exception {
        DefaultHandler handler = new MyDefaultHandler2();
        SAXParserFactory saxFac = SAXParserFactory.newInstance();
        System.out.println(saxFac.getFeature("http://xml.org/sax/features/use-locator2"));

        // set use-entity-resolver2 as FALSE to use EntityResolver firstly.
        saxFac.setFeature("http://xml.org/sax/features/use-entity-resolver2", false);
        saxFac.setValidating(true);

        SAXParser parser = saxFac.newSAXParser();
        parser.setProperty("http://xml.org/sax/properties/lexical-handler", handler);
        parser.setProperty("http://xml.org/sax/properties/declaration-handler", handler);

        parser.parse(this.getClass().getResource("toys.xml").getFile(), handler);
    }

    @Test
    public void testParse02() throws Exception {
        DefaultHandler handler = new MyDefaultHandler2();
        SAXParserFactory saxFac = SAXParserFactory.newInstance();
        System.out.println(saxFac.getFeature("http://xml.org/sax/features/use-locator2"));

        // Enable namespace parsing
        System.out.println(saxFac.getFeature("http://xml.org/sax/features/namespaces"));
        saxFac.setNamespaceAware(true);

        saxFac.setValidating(true);
        SAXParser parser = saxFac.newSAXParser();
        parser.setProperty("http://xml.org/sax/properties/lexical-handler", handler);
        parser.setProperty("http://xml.org/sax/properties/declaration-handler", handler);

        parser.parse(this.getClass().getResource("toys.xml").getFile(), handler);
    }

    @Test
    public void testParse03() throws Exception {
        DefaultHandler handler = new MyDefaultHandler2();

        XMLReader xmlReader = XMLReaderFactory.createXMLReader();
        xmlReader.setProperty("http://xml.org/sax/properties/declaration-handler", handler);
        System.out.println("XMLReader : " + xmlReader.getProperty("http://xml.org/sax/properties/declaration-handler"));

        SAXParserFactory saxFac = SAXParserFactory.newInstance();
        SAXParser parser = saxFac.newSAXParser();
        parser.setProperty("http://xml.org/sax/properties/declaration-handler", handler);
        System.out.println("SAXParser : " + parser.getProperty("http://xml.org/sax/properties/declaration-handler"));

        // From https://docs.oracle.com/javase/7/docs/api,
        // ParserAdapter.setProperty() and ParserAdapter.getProperty() does
        // not support any property currently.
        ParserAdapter adapter = new ParserAdapter(parser.getParser());
        assertThrows(SAXNotRecognizedException.class, () -> adapter.getProperty("http://xml.org/sax/properties/declaration-handler"));
        assertThrows(SAXNotRecognizedException.class, () -> adapter.setProperty("http://xml.org/sax/properties/declaration-handler", handler));
    }

    @Test
    public void testParse04() throws Exception {
        DefaultHandler handler = new MyDefaultHandler2();
        XMLReader xmlReader = XMLReaderFactory.createXMLReader();
        System.out.println(xmlReader.getFeature("http://xml.org/sax/features/namespaces"));
        xmlReader.setProperty("http://xml.org/sax/properties/declaration-handler", handler);
        xmlReader.setProperty("http://xml.org/sax/properties/lexical-handler", handler);
        xmlReader.setContentHandler(handler);

        xmlReader.parse(this.getClass().getResource("toys.xml").getFile());
    }

    @Test
    public void testParse05() throws Exception {
        DefaultHandler handler = new MyDefaultHandler2();
        XMLReader xmlReader = XMLReaderFactory.createXMLReader();
        XMLFilterImpl filterImpl = new XMLFilterImpl(xmlReader);
        System.out.println(xmlReader.getFeature("http://xml.org/sax/features/namespaces"));
        filterImpl.setProperty("http://xml.org/sax/properties/declaration-handler", handler);
        filterImpl.setProperty("http://xml.org/sax/properties/lexical-handler", handler);
        filterImpl.setContentHandler(handler);

        filterImpl.parse(this.getClass().getResource("toys.xml").getFile());
    }

    @Test
    public void testParse06() throws Exception {
        DefaultHandler handler = new MyDefaultHandler2();
        XMLReader xmlReader = XMLReaderFactory.createXMLReader();
        XMLFilterImpl filterImpl = new XMLFilterImpl(xmlReader);
        System.out.println(xmlReader.getFeature("http://xml.org/sax/features/namespaces"));
        filterImpl.setProperty("http://xml.org/sax/properties/declaration-handler", handler);
        filterImpl.setProperty("http://xml.org/sax/properties/lexical-handler", handler);
        filterImpl.setContentHandler(handler);

        assertInstanceOf(DefaultHandler2.class, filterImpl.getProperty("http://xml.org/sax/properties/declaration-handler"));

        // filterImpl.setFeature("http://xml.org/sax/features/external-general-entities",
        // false) ;
        // filterImpl.setFeature("http://xml.org/sax/features/external-parameter-entities",
        // false) ;
        filterImpl.skippedEntity("name2");

        filterImpl.parse(this.getClass().getResource("toys.xml").getFile());
    }

    @Test
    public void testParse07() throws Exception {
        DefaultHandler handler = new MyDefaultHandler2();
        XMLReader xmlReader = XMLReaderFactory.createXMLReader();
        XMLFilterImpl filterImpl = new XMLFilterImpl(xmlReader);
        System.out.println(xmlReader.getFeature("http://xml.org/sax/features/namespaces"));
        filterImpl.setProperty("http://xml.org/sax/properties/declaration-handler", handler);
        filterImpl.setProperty("http://xml.org/sax/properties/lexical-handler", handler);
        filterImpl.setContentHandler(handler);
        filterImpl.setErrorHandler(handler);
        assertInstanceOf(DefaultHandler2.class, filterImpl.getProperty("http://xml.org/sax/properties/declaration-handler"));

        filterImpl.setFeature("http://apache.org/xml/features/continue-after-fatal-error", true);
        filterImpl.parse(this.getClass().getResource("toys_error.xml").getFile());
    }
}
